package com.realobjects.brightspot.pdfreactor.preview;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.psddev.cms.db.Preview;
import com.psddev.cms.db.PreviewDatabase;
import com.psddev.cms.db.Schedule;
import com.psddev.cms.ui.ToolLocalization;
import com.psddev.cms.ui.ToolPage;
import com.psddev.dari.db.Database;
import com.psddev.dari.db.Query;
import com.psddev.dari.html.content.FlowContent;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.web.WebRequest;
import com.psddev.dari.web.annotation.WebParameter;
import com.psddev.dari.web.annotation.WebPath;
import com.realobjects.brightspot.pdfreactor.DefaultPdfReactorService;
import com.realobjects.brightspot.pdfreactor.PdfDiagnostics;
import com.realobjects.brightspot.pdfreactor.PdfLicenseProbe;
import com.realobjects.brightspot.pdfreactor.PdfLicenseState;
import com.realobjects.brightspot.pdfreactor.PdfProblemReport;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfigs;
import com.realobjects.brightspot.pdfreactor.PdfReactorException;
import com.realobjects.brightspot.pdfreactor.PdfReactorService;
import com.realobjects.brightspot.pdfreactor.PdfRenderOptions;
import com.realobjects.brightspot.pdfreactor.PdfResult;
import com.realobjects.brightspot.pdfreactor.ToolResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.psddev.dari.html.Nodes.BODY;
import static com.psddev.dari.html.Nodes.DETAILS;
import static com.psddev.dari.html.Nodes.DIV;
import static com.psddev.dari.html.Nodes.HEAD;
import static com.psddev.dari.html.Nodes.HTML;
import static com.psddev.dari.html.Nodes.LI;
import static com.psddev.dari.html.Nodes.META;
import static com.psddev.dari.html.Nodes.OBJECT;
import static com.psddev.dari.html.Nodes.P;
import static com.psddev.dari.html.Nodes.PRE;
import static com.psddev.dari.html.Nodes.SCRIPT;
import static com.psddev.dari.html.Nodes.STRONG;
import static com.psddev.dari.html.Nodes.SUMMARY;
import static com.psddev.dari.html.Nodes.UL;

/**
 * Tool endpoint that converts the draft carried by a {@link Preview} into a
 * PDF and streams it into the preview iframe ({@code application/pdf};
 * browsers render it natively, so the finished PDF is the preview).
 *
 * <p>The preview path never fails closed: license problems (evaluation
 * watermarks) and missing resources still produce a PDF, and diagnostics
 * are surfaced as a themed banner <em>in the Tool DOM</em> — the iframe
 * only ever shows the PDF. Editor-facing messages belong to the Tool page
 * (the platform never renders Tool-styled content inside a preview
 * iframe), so problem responses post their report to the parent window
 * ({@link com.realobjects.brightspot.pdfreactor.PdfReactorToolPageHead}
 * renders the banner) and fall back to a
 * self-contained inline banner only when opened standalone.</p>
 */
@WebPath("/pdfreactor/preview")
public class PdfPreviewPage extends ToolPage {

    public static final String PREVIEW_ID_PARAMETER = "previewId";

    // Standard schedule-date control parameters (bare literals, matching
    // PreviewDatabaseFilter / createScheduleDateSelect — no platform constant).
    public static final String DATE_PARAMETER = "_date";
    public static final String SCHEDULE_ID_PARAMETER = "_scheduleId";

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfPreviewPage.class);

    @WebParameter
    private UUID previewId;

    public void setPreviewId(UUID previewId) {
        this.previewId = previewId;
    }

    @Override
    protected void onGet() throws Exception {
        WebRequest request = WebRequest.getCurrent();
        Date date = request.getParameter(Date.class, DATE_PARAMETER);
        UUID scheduleId = request.getParameter(UUID.class, SCHEDULE_ID_PARAMETER);

        if (date == null && scheduleId == null) {
            renderPreview();
            return;
        }

        // Date-shifted preview. The platform's
        // PreviewDatabaseFilter only activates on /_preview/ paths, never on
        // this /cms/ ToolPage, so install the PreviewDatabase override
        // in-page exactly as that filter does (delegate to the default DB,
        // apply the schedule's changes, set the as-of date), then restore.
        PreviewDatabase previewDatabase = new PreviewDatabase();
        previewDatabase.setDelegate(Database.Static.getDefault());
        if (date != null) {
            previewDatabase.setDate(date);
        }
        if (scheduleId != null) {
            Schedule schedule = Query.from(Schedule.class).where("_id = ?", scheduleId).first();
            if (schedule != null) {
                previewDatabase.addChanges(schedule);
            }
        }
        Database.Static.overrideDefault(previewDatabase);
        try {
            renderPreview();
        } finally {
            Database.Static.restoreDefault();
        }
    }

    private void renderPreview() throws Exception {
        Preview preview = Query.from(Preview.class).where("_id = ?", previewId).first();
        if (preview == null) {
            writeProblemDocument("warning",
                    localize("error.previewMissing", "The preview has expired or does not exist."),
                    null, null);
            return;
        }

        Object draft = preview.getObject();
        if (draft == null) {
            writeProblemDocument("warning",
                    localize("error.contentMissing", "The previewed content no longer exists."),
                    null, null);
            return;
        }

        // Apply this article's troubleshooting toggles (debug / inspectable) to
        // the preview when an administrator allows them for the content's site,
        // so the preview reflects exactly what those settings produce. The
        // preview already never fails on missing resources, so debug's
        // guard-relaxation is moot here; it matters on the Generate path.
        PdfRenderOptions options = PdfRenderOptions.builder()
                .failOnLicenseProblems(false)
                .failOnMissingResources(false)
                .debug(PdfReactorConfigs.debugActive(draft))
                .inspectable(PdfReactorConfigs.inspectableActive(draft))
                .build();

        PdfResult result;
        try {
            result = createService(draft).renderContent(draft, options);

        } catch (PdfReactorException error) {
            LOGGER.warn("PDF preview conversion failed for preview [{}].", previewId, error);
            writeProblemDocument("error",
                    localize("error.headline", "The PDF could not be generated."),
                    PdfProblemReport.of(error), null);
            return;
        }

        PdfDiagnostics diagnostics = result.getDiagnostics();
        if (diagnostics.hasProblems()) {
            writeProblemDocument("warning",
                    localize("warning.headline", "The PDF was generated with problems:"),
                    PdfProblemReport.of(diagnostics), result.getDocument());
            return;
        }

        // Clean success. The preview relaxes failOnLicenseProblems so the
        // editor always sees output, which means an evaluation watermark
        // carries no diagnostics problem and would display silently. Post an
        // informational banner explaining the watermark (and the otherwise
        // confusing "preview shows a PDF but Generate fails" asymmetry). The
        // license state comes from the background-cached global probe — never
        // probed inline here; a per-site refinement is deferred (Part G.1).
        if (PdfLicenseProbe.current() == PdfLicenseState.EVALUATION) {
            writeProblemDocument("info",
                    localize("eval.headline",
                            "Evaluation mode — this PDF is watermarked. Generated and published PDFs"
                                    + " fail closed until a valid PDFreactor license is configured."),
                    null, result.getDocument());
            return;
        }

        response.setHeader("Content-Type", "application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"preview.pdf\"");
        response.setBinaryBodyType(true);
        response.toBody().write(result.getDocument());
    }

    @Override
    protected void onPost() throws Exception {
        // The preview pane form may submit via POST.
        onGet();
    }

    /**
     * Creates the conversion service for the given content, layering the
     * owning site's overrides over the global config. Override point for
     * tests.
     */
    protected PdfReactorService createService(Object content) {
        return new DefaultPdfReactorService(PdfReactorConfigs.forContent(content));
    }

    /**
     * Writes a minimal document that posts the problem report to the parent
     * Tool page (which renders the themed banner) and shows the PDF — and
     * nothing else — in the frame. Opened standalone (no parent), a
     * self-contained fallback banner becomes visible instead.
     *
     * @param pdf Nullable. Embedded below when the conversion still
     *        produced a document (warning case).
     */
    private void writeProblemDocument(
            String severity,
            String headline,
            PdfProblemReport report,
            byte[] pdf) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "pdfreactor-preview-problems");
        payload.put("severity", severity);
        payload.put("headline", headline);
        if (report != null) {
            payload.put("details", report.getDetails());
            payload.put("technical", report.getTechnical());
            if (report.getLogText() != null) {
                payload.put("log", report.getLogText());
                payload.put("logLabel", localize("label.conversionLog", "Conversion log"));
            }
            String remedy = remedyFor(report.getKind());
            if (remedy != null) {
                payload.put("remedy", remedy);
            }
        }
        payload.put("technicalLabel", localize("label.technicalDetails", "Technical details"));

        // Neither "</" (which would terminate the surrounding <script>) nor
        // "<!--" (which would open the script-data double-escaped state and
        // break parsing) may appear raw in the inlined JSON.
        String json = ObjectUtils.toJson(payload)
                .replace("</", "<\\/")
                .replace("<!--", "<\\!--");
        String bootstrap = "(function () {"
                + "var payload = " + json + ";"
                + "if (window.parent !== window) {"
                + "window.parent.postMessage(payload, window.location.origin);"
                + "} else {"
                + "document.body.classList.add('standalone');"
                + "}"
                + "}());";

        // Built as a dari-html node tree (auto-escaped) rather than a string
        // builder; only the per-request bootstrap script and the embedded-PDF
        // data: URI remain raw text. The fallback styles live in
        // web/preview-fallback.css.
        String html = "<!DOCTYPE html>\n" + HTML.with(
                HEAD.with(
                        META.charset(StandardCharsets.UTF_8),
                        ToolResources.styleSheet(ToolResources.WEB + "preview-fallback.css")),
                BODY.className("PdfPreviewMessage").with(body -> {
                    body.add(fallbackBanner(severity, headline, report));
                    if (pdf != null) {
                        // Embed the already-converted PDF as a data: URI so the
                        // warning case shows the document without a second
                        // conversion. NOTE: relies on the Tool not enforcing a
                        // Content-Security-Policy that forbids data: in
                        // object-src; the success path streams application/pdf
                        // directly and is unaffected. A CSP-safe warning-case
                        // stream would need a re-conversion or a server-side
                        // bytes cache keyed for this request — deferred.
                        body.add(OBJECT.type("application/pdf")
                                .data("data:application/pdf;base64,"
                                        + Base64.getEncoder().encodeToString(pdf)));
                    }
                    body.add(SCRIPT.with(bootstrap));
                }));

        response.setHeader("Content-Type", "text/html; charset=utf-8");
        response.toBody().write(html);
    }

    /**
     * The self-contained fallback banner (visible only when the document is
     * opened standalone — see {@code preview-fallback.css}). Built as nodes,
     * so the text is auto-escaped.
     */
    private FlowContent fallbackBanner(String severity, String headline, PdfProblemReport report) {
        return DIV.classList("PdfPreview-fallback", "PdfPreview-fallback--" + severity)
                .with(banner -> {
                    banner.add(STRONG.with(headline));
                    if (report != null) {
                        if (!report.getDetails().isEmpty()) {
                            banner.add(UL.with(ul ->
                                    report.getDetails().forEach(detail -> ul.add(LI.with(detail)))));
                        }
                        String remedy = remedyFor(report.getKind());
                        if (remedy != null) {
                            banner.add(P.with(remedy));
                        }
                        if (report.getTechnical() != null) {
                            banner.add(disclosure(
                                    localize("label.technicalDetails", "Technical details"),
                                    report.getTechnical()));
                        }
                        if (report.getLogText() != null) {
                            banner.add(disclosure(
                                    localize("label.conversionLog", "Conversion log"),
                                    report.getLogText()));
                        }
                    }
                });
    }

    /** A {@code <details>} disclosure with a summary label and a {@code <pre>} body. */
    private static FlowContent disclosure(String summary, String content) {
        return DETAILS.with(SUMMARY.with(summary), PRE.with(content));
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
        return ToolLocalization.text(PdfPreviewPage.class, key, fallback);
    }
}
