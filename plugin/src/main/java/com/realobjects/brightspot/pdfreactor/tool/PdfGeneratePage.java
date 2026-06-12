package com.realobjects.brightspot.pdfreactor.tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.psddev.cms.ui.ToolLocalization;
import com.psddev.cms.ui.ToolPage;
import com.psddev.cms.ui.ToolRequest;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Predicate;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.State;
import com.psddev.dari.db.ValidationException;
import com.psddev.dari.html.content.FlowContent;
import com.psddev.dari.util.StorageItem;
import com.psddev.dari.web.WebRequest;
import com.psddev.dari.web.WebResponseStatus;
import com.psddev.dari.web.annotation.WebParameter;
import com.psddev.dari.web.annotation.WebPath;
import com.realobjects.brightspot.pdfreactor.DefaultPdfReactorService;
import com.realobjects.brightspot.pdfreactor.GeneratedPdf;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;
import com.realobjects.brightspot.pdfreactor.PdfCacheKey;
import com.realobjects.brightspot.pdfreactor.PdfConfigFingerprint;
import com.realobjects.brightspot.pdfreactor.PdfProblemReport;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfig;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfigs;
import com.realobjects.brightspot.pdfreactor.PdfReactorException;
import com.realobjects.brightspot.pdfreactor.PdfReactorService;
import com.realobjects.brightspot.pdfreactor.PdfRenderOptions;
import com.realobjects.brightspot.pdfreactor.PdfResult;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.psddev.dari.html.Nodes.A;
import static com.psddev.dari.html.Nodes.DETAILS;
import static com.psddev.dari.html.Nodes.DIV;
import static com.psddev.dari.html.Nodes.LI;
import static com.psddev.dari.html.Nodes.P;
import static com.psddev.dari.html.Nodes.PRE;
import static com.psddev.dari.html.Nodes.SUMMARY;
import static com.psddev.dari.html.Nodes.UL;
import static com.psddev.dari.html.Nodes.text;

/**
 * Tool endpoint for on-demand generation: converts a content item, stores
 * the result as a {@link StorageItem} referenced by a {@link GeneratedPdf}
 * record, and streams the PDF. Repeat requests for the same
 * {@code (contentId, revision, configHash)} cache key serve the stored PDF
 * without converting again; {@code regenerate=true} forces a fresh
 * conversion.
 *
 * <p>Unlike the preview, this path stores an artifact, so it fails closed on
 * missing resources: a broken resource aborts the generation and is shown with
 * full diagnostics instead. License problems do not block generation — an
 * unlicensed (evaluation-mode) service stores watermarked output, with
 * evaluation mode surfaced by the health widget and the preview banner.</p>
 */
@WebPath("/pdfreactor/generate")
public class PdfGeneratePage extends ToolPage {

    public static final String CONTENT_ID_PARAMETER = "contentId";

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfGeneratePage.class);

    @WebParameter
    private UUID contentId;

    @WebParameter
    private boolean regenerate;

    @WebParameter
    private boolean download;

    @WebParameter
    private UUID generatedPdfId;

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public void setRegenerate(boolean regenerate) {
        this.regenerate = regenerate;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }

    public void setGeneratedPdfId(UUID generatedPdfId) {
        this.generatedPdfId = generatedPdfId;
    }

    @Override
    protected void onGet() throws Exception {
        // Download-by-id: stream the exact stored record the widget is
        // showing, with no content lookup, no conversion, and no write
        // side-effect. A regenerate after a content change must never be
        // triggered by a download link (it could fail on a now-broken
        // resource, or silently create a new record).
        if (download && generatedPdfId != null) {
            // Authorize the underlying content for the current tool user before
            // streaming its stored PDF (a record id is a request parameter — the
            // by-content check below is consistency, not access control).
            if (authorizedContent(contentId) == null) {
                writeMessage("message-warning",
                        localize("error.contentMissing",
                                "The content does not exist or you do not have access to it."));
                return;
            }
            GeneratedPdf stored = findDownloadable(generatedPdfId, contentId);
            if (stored == null) {
                writeMessage("message-warning",
                        localize("error.storedPdfMissing", "The stored PDF is missing."));
                return;
            }
            streamPdf(stored);
            return;
        }

        Object content = authorizedContent(contentId);
        if (content == null) {
            writeMessage("message-warning",
                    localize("error.contentMissing",
                            "The content does not exist or you do not have access to it."));
            return;
        }

        // Only generate a stored artifact from a published/visible state;
        // Query.fromAll() would otherwise render a draft or hidden revision.
        if (!com.psddev.dari.db.State.getInstance(content).isVisible()) {
            writeMessage("message-warning",
                    localize("error.notPublished",
                            "Generate a PDF only after the content is published."));
            return;
        }

        // Resolve ICC bytes once for both the fingerprint and the conversion
        // (same instance) instead of reading them twice on a cache miss.
        PdfReactorConfig config = PdfReactorConfigs.resolved(PdfReactorConfigs.forContent(content));

        // Troubleshooting builds: when an administrator allows it for this
        // content's site AND this article's debug/inspectable toggle is on, the
        // Generate path produces a diagnostic build — converted fresh, streamed
        // inline, never stored as the canonical cache record and never stamped
        // onto the content, so it can never become the published PDF. Debug
        // additionally relaxes the missing-resource guard. The publish
        // automation never produces a troubleshooting build (it ignores the
        // per-article toggles entirely).
        if (PdfReactorConfigs.troubleshootingActive(content)) {
            renderTroubleshooting(content, config);
            return;
        }

        // Regeneration is a state-changing action and is POST-only: the
        // widget posts it via pdf-widget.js. A GET here (a stale link, or a
        // cross-site navigation that would carry the SameSite=Lax cookie) must
        // not convert or save — reject it.
        if (regenerate) {
            writeMessage("message-warning", localize("error.regeneratePost",
                    "Use the \"Convert again\" button to regenerate."));
            return;
        }

        // Config resolution and fingerprinting read the output-affecting enum
        // settings and ICC bytes, any of which can throw on a misconfiguration
        // (a typo'd conformance/intent, an unreadable profile). Route those
        // through the same failure page as a conversion error instead of
        // letting them escape onGet as a raw Tool error.
        PdfRenderOptions options;
        String cacheKey;
        try {
            options = productionOptions();
            cacheKey = PdfCacheKey.of(content, options, PdfConfigFingerprint.of(config));
        } catch (PdfReactorException error) {
            writeFailure(error);
            return;
        }

        GeneratedPdf generated = GeneratedPdf.findByCacheKey(cacheKey);
        if (generated == null) {
            try {
                generated = convertStoreStamp(content, config, options, cacheKey);
            } catch (PdfReactorException error) {
                writeFailure(error);
                return;
            }
        }
        streamPdf(generated);
    }

    /**
     * Handles the state-changing regenerate as a POST: converts fresh,
     * stores, and stamps the content, then responds 200 with no body —
     * pdf-widget.js reloads the edit page in place on success, never opening a
     * new tab or injecting into the widget frame (the breakage of the plain
     * anchor). Reads (download / first-time stream / cache serve) stay on GET.
     */
    @Override
    protected void onPost() throws Exception {
        if (!regenerate) {
            response.setStatus(WebResponseStatus.BAD_REQUEST);
            writePlain("Only regeneration is supported here.");
            return;
        }
        Object content = authorizedContent(contentId);
        if (content == null) {
            response.setStatus(WebResponseStatus.NOT_FOUND);
            writePlain(localize("error.contentMissing",
                    "The content does not exist or you do not have access to it."));
            return;
        }
        if (!State.getInstance(content).isVisible()) {
            response.setStatus(WebResponseStatus.BAD_REQUEST);
            writePlain(localize("error.notPublished",
                    "Generate a PDF only after the content is published."));
            return;
        }
        PdfReactorConfig config = PdfReactorConfigs.resolved(PdfReactorConfigs.forContent(content));
        try {
            PdfRenderOptions options = productionOptions();
            String cacheKey = PdfCacheKey.of(content, options, PdfConfigFingerprint.of(config));
            convertStoreStamp(content, config, options, cacheKey);
        } catch (PdfReactorException error) {
            LOGGER.warn("On-demand PDF regeneration failed for content [{}].", contentId, error);
            response.setStatus(WebResponseStatus.INTERNAL_SERVER_ERROR);
            PdfProblemReport report = PdfProblemReport.of(error);
            writePlain(report.getDetails().isEmpty()
                    ? localize("error.headline", "The PDF could not be generated.")
                    : report.getDetails().get(0));
            return;
        }
        // Success: the client reloads the edit page; no body needed.
        response.setStatus(200);
    }

    /**
     * Production (non-troubleshooting) generate options: fail closed on missing
     * resources, but relax license problems so an unlicensed service stores
     * watermarked output instead of blocking generation.
     */
    private static PdfRenderOptions productionOptions() {
        return PdfRenderOptions.builder()
                .failOnMissingResources(true)
                .failOnLicenseProblems(false)
                .build();
    }

    private void writePlain(String text) throws IOException {
        response.setHeader("Content-Type", "text/plain; charset=utf-8");
        response.toBody().write(text);
    }

    /**
     * Converts the content, stores the PDF as a {@link GeneratedPdf} cache
     * record, prunes old records, and stamps the content's read-only PDF
     * fields. Shared by the first-time GET generate and the POST regenerate.
     *
     * @throws PdfReactorException If the conversion fails.
     */
    private GeneratedPdf convertStoreStamp(
            Object content, PdfReactorConfig config, PdfRenderOptions options, String cacheKey)
            throws IOException {

        PdfResult result = createService(config).renderContent(content, options);

        byte[] document = result.getDocument();
        StorageItem item = StorageItem.Static.create();
        item.setPath(PdfCacheKey.storagePath(contentId, cacheKey));
        item.setContentType("application/pdf");
        item.setData(new ByteArrayInputStream(document));
        item.save();

        GeneratedPdf generated = GeneratedPdf.findByCacheKey(cacheKey);
        if (generated == null) {
            generated = new GeneratedPdf();
            generated.setCacheKey(cacheKey);
            generated.setContentId(contentId);
        }
        generated.setPdf(item);
        generated.setGenerated(new Date());
        generated.setNumberOfPages(result.getNumberOfPages());
        generated.setByteSize(document.length);
        try {
            generated.save();
        } catch (ValidationException duplicate) {
            // A concurrent generate (or the publish task) raced us to the unique
            // cacheKey index; first writer wins.
            GeneratedPdf winner = GeneratedPdf.findByCacheKey(cacheKey);
            if (winner == null) {
                throw duplicate;
            }
            generated = winner;
        }

        // Prune so content where on-demand generation is the only PDF path
        // (publish automation disabled, or generate-on-publish turned off) does
        // not grow the table without bound.
        try {
            GeneratedPdf.pruneForContent(contentId);
        } catch (RuntimeException pruneError) {
            LOGGER.warn("Pruning old generated PDFs failed for content [{}].", contentId, pruneError);
        }

        // Stamp the content's read-only Generated Pdf Date/Status. Re-fetch the
        // content immediately before the write so a concurrent edit during the
        // (slow) conversion is not clobbered (lost-update window). The
        // attempt-key idempotency keeps the afterSave from re-firing publish.
        if (content instanceof HasPdfRendering) {
            Object fresh = Query.fromAll().where("_id = ?", contentId).first();
            if (fresh instanceof HasPdfRendering) {
                State state = State.getInstance(fresh);
                state.as(HasPdfRenderingData.class)
                        .recordSuccessfulGeneration(item, result, cacheKey, document.length);
                state.save();
            }
        }
        return generated;
    }

    /** The Tool edit-form URL for the current content (configurable CMS context path). */
    private String contentEditUrl() {
        return WebRequest.getCurrent().as(ToolRequest.class)
                .getPathBuilder("/content/edit.jsp")
                .setParameter("id", contentId)
                .build();
    }

    /**
     * Creates the conversion service for the given content, layering the
     * owning site's overrides over the global config. Override point for
     * tests.
     */
    protected PdfReactorService createService(PdfReactorConfig config) {
        return new DefaultPdfReactorService(config);
    }

    /**
     * Resolves the requested content <em>scoped to the current tool user's
     * access</em>: the content must be visible to the user's current site
     * (the platform {@code siteItemsPredicate}) and the user must hold the read
     * permission for its type. Authentication alone (the {@link ToolPage} base)
     * does not authorize a specific content item, so without this any tool user
     * could render/download any content by guessing its id.
     *
     * @return The content, or {@code null} when it is absent or the user is not
     *         authorized for it (the two are deliberately indistinguishable to
     *         the caller, so existence is not leaked).
     */
    private Object authorizedContent(UUID id) {
        Predicate siteItems = tool.siteItemsPredicate();
        return authorizeContent(id, siteItems,
                type -> tool.hasPermission("type/" + type.getId() + "/read"));
    }

    /**
     * The access check behind {@link #authorizedContent}, factored out for unit
     * testing: load {@code id} filtered by the site-items predicate (when the
     * user has a current site), then require the type-read permission. Returns
     * {@code null} if the content is missing, outside the user's site, or of a
     * type the user may not read.
     *
     * @param siteItems Nullable. {@code null} (no current site) applies no site
     *        filter, matching the platform's single-site / global behavior.
     */
    static Object authorizeContent(
            UUID id,
            Predicate siteItems,
            java.util.function.Predicate<ObjectType> typeReadable) {
        if (id == null) {
            return null;
        }
        Query<Object> query = Query.fromAll().where("_id = ?", id);
        if (siteItems != null) {
            query = query.and(siteItems);
        }
        Object content = query.first();
        if (content == null) {
            return null;
        }
        ObjectType type = State.getInstance(content).getType();
        if (type != null && !typeReadable.test(type)) {
            return null;
        }
        return content;
    }

    /**
     * Resolves the stored record for a by-id download, validating it belongs
     * to the requested content (defense-in-depth: this is an authenticated
     * tool page, but the id is a request parameter — a mismatch must not
     * stream another content's PDF). Returns {@code null} if the record is
     * absent or does not belong to {@code contentId}; a by-id download never
     * converts.
     */
    static GeneratedPdf findDownloadable(UUID generatedPdfId, UUID contentId) {
        GeneratedPdf stored = GeneratedPdf.findById(generatedPdfId);
        if (stored == null || !Objects.equals(contentId, stored.getContentId())) {
            return null;
        }
        return stored;
    }

    /**
     * Runs a one-off troubleshooting conversion (debug and/or inspectable) and
     * streams it inline, without storing a cache record or stamping the content
     * fields — this output is non-production and must never become the canonical
     * generated PDF or be picked up by the publish path.
     */
    private void renderTroubleshooting(Object content, PdfReactorConfig config) throws IOException {
        boolean debug = PdfReactorConfigs.debugActive(content);
        boolean inspectable = PdfReactorConfigs.inspectableActive(content);
        PdfRenderOptions options = PdfRenderOptions.builder()
                // Debug relaxes the missing-resource guard so a diagnostic PDF
                // is produced even when resources are broken; inspectable-only
                // keeps the guard (it is not a "render despite errors" mode).
                .failOnMissingResources(!debug)
                .debug(debug)
                .inspectable(inspectable)
                .build();
        PdfResult result;
        try {
            result = createService(config).renderContent(content, options);
        } catch (PdfReactorException error) {
            writeFailure(error);
            return;
        }
        streamBytes(result.getDocument());
    }

    /** Streams raw PDF bytes inline, with no stored record (the troubleshooting path). */
    private void streamBytes(byte[] document) throws IOException {
        response.setHeader("Content-Type", "application/pdf");
        response.setHeader("Content-Disposition",
                "inline; filename=\"" + contentId + ".pdf\"");
        response.setBinaryBodyType(true);
        response.toBody().write(new ByteArrayInputStream(document));
    }

    private void streamPdf(GeneratedPdf generated) throws IOException {
        StorageItem item = generated.getPdf();
        if (item == null) {
            writeMessage("message-error",
                    localize("error.storedPdfMissing", "The stored PDF is missing."));
            return;
        }

        response.setHeader("Content-Type", "application/pdf");
        // Always inline: the widget link opens this in a new tab with no
        // download attribute, so an inline disposition lets the browser's own
        // PDF setting govern view-vs-download instead of forcing a file
        // download the OS auto-opens. The `download` request flag now only
        // routes the by-id stream in onGet — it no longer toggles disposition.
        response.setHeader("Content-Disposition",
                "inline; filename=\"" + contentId + ".pdf\"");
        response.setBinaryBodyType(true);
        response.toBody().write(item.getData());
    }

    private void writeMessage(String messageClass, String message) {
        writeHtmlToResponse(
                List.of(DIV.classList("message", messageClass).with(message)),
                "Generate PDF");
    }

    /**
     * Failure page: a short headline, concise de-duplicated details, a
     * localized remedy, the raw service error behind a collapsible
     * "Technical details" section, and a way back to the content. (Full
     * Tool chrome is appropriate here — this endpoint opens as its own
     * tab, unlike the preview iframe.)
     */
    private void writeFailure(PdfReactorException error) {
        LOGGER.warn("On-demand PDF generation failed for content [{}].", contentId, error);

        PdfProblemReport report = PdfProblemReport.of(error);
        List<FlowContent> elements = new ArrayList<>();

        // PdfGenerate-problems forces the headline / details / remedy / the two
        // disclosures to stack vertically: the skin bridges "message message-error"
        // to .Message, which is display:flex row, so without this the "Technical
        // details" and "Conversion log" disclosures sit side by side on one line
        // (mirrors the preview banner's .PdfPreview-problems column rule).
        elements.add(DIV.classList("message", "message-error", "PdfGenerate-problems").with(div -> {
            div.add(text(localize("error.headline", "The PDF could not be generated.")));
            if (!report.getDetails().isEmpty()) {
                div.add(UL.with(ul -> report.getDetails().forEach(detail -> ul.add(LI.with(detail)))));
            }
            String remedy = remedyFor(report.getKind());
            if (remedy != null) {
                div.add(P.with(remedy));
            }
            if (report.getTechnical() != null) {
                div.add(disclosure(localize("label.technicalDetails", "Technical details"),
                        report.getTechnical()));
            }
            if (report.getLogText() != null) {
                div.add(disclosure(localize("label.conversionLog", "Conversion log"),
                        report.getLogText()));
            }
        }));
        elements.add(DIV.with(A.href(contentEditUrl())
                .with(localize("link.backToContent", "Back to the content"))));

        writeHtmlToResponse(elements, "Generate PDF");
    }

    /** A {@code <details>} disclosure with a summary label and a pre-wrapped {@code <pre>} body. */
    private static FlowContent disclosure(String summary, String content) {
        return DETAILS.with(
                SUMMARY.with(summary),
                PRE.attr("style", "white-space: pre-wrap;").with(content));
    }

    private String remedyFor(PdfProblemReport.Kind kind) {
        switch (kind) {
            case MISSING_RESOURCE:
                return localize("remedy.missingResource",
                        "Fix or remove the broken resource, then try again.");
            case LICENSE:
                return localize("remedy.license",
                        "Check the PDFreactor license configuration.");
            case SERVICE:
                return localize("remedy.service",
                        "Check that the PDFreactor service is running and reachable,"
                                + " or contact an administrator.");
            case RENDER_SOURCE:
                return localize("remedy.renderSource",
                        "Check that the content is published and reachable at its URL"
                                + " (and the pdfreactor/internalRenderBaseUrl setting, if used).");
            case CONFIG:
                return localize("remedy.config",
                        "Check the PDFreactor configuration: the ICC profile references"
                                + " and the configuration JSON.");
            default:
                return null;
        }
    }

    private String localize(String key, String fallback) {
        return ToolLocalization.text(PdfGeneratePage.class, key, fallback);
    }
}
