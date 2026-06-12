package com.realobjects.brightspot.pdfreactor.tool;

import java.util.UUID;

import com.psddev.cms.tool.ContentEditWidget;
import com.psddev.cms.tool.ContentEditWidgetPlacement;
import com.psddev.cms.ui.ToolLocalization;
import com.psddev.dari.db.State;
import com.psddev.dari.web.UrlBuilder;
import com.realobjects.brightspot.pdfreactor.GeneratedPdf;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfigs;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;

import static com.psddev.dari.html.Nodes.A;
import static com.psddev.dari.html.Nodes.DIV;

/**
 * "PDFreactor" widget on the content edit form (right rail, "Publishing
 * Tools" group): a "Show PDF" action that opens the published content's
 * PDF via {@link PdfGeneratePage} — generating it the first time and then
 * serving the stored PDF while the content is unchanged — plus the most
 * recently stored PDF with download/regenerate actions.
 *
 * <p>Markup follows the Tool conventions: controls carry both class
 * generations explicitly ({@code button Button} / {@code link Link} — the
 * v5 skin hides legacy classes until its runtime bridge upgrades them, so
 * relying on the bridge means hidden/flickering controls), structure uses
 * plugin-namespaced {@code PdfWidget-*} classes (spacing contributed by
 * {@code PdfReactorToolPageHead}), dates render via
 * {@link ToolLocalization#dateTime(long)} like the Revisions widget, and
 * strings are localized with fallbacks.</p>
 */
public class GeneratePdfWidget extends ContentEditWidget {

    @Override
    public boolean shouldDisplay(Object content) {
        if (content == null) {
            return false;
        }
        State state = State.getInstance(content);
        return state.getId() != null && state.getType() != null;
    }

    @Override
    public ContentEditWidgetPlacement getPlacement(Object content) {
        return ContentEditWidgetPlacement.RIGHT;
    }

    @Override
    public String getHeading(Object content) {
        return localize("title", "PDFreactor");
    }

    @Override
    public Object display(Object content, ContentEditWidgetPlacement placement) {
        State state = State.getInstance(content);
        UUID contentId = state.getId();
        // Generation refuses a draft/hidden state (PdfGeneratePage gates on
        // State#isVisible). Offer the action only when it can succeed; on an
        // unpublished item show why instead of a button that always dead-ends
        // in a new tab with the "publish first" message.
        boolean visible = state.isVisible();

        GeneratedPdf latest = GeneratedPdf.findLatestForContent(contentId);
        String publishFailure = publishFailureDetail(content);

        return DIV.className("PdfWidget").with(div -> {
            // Surface a publish-automation failure on the edit form itself: it
            // otherwise lives only in a read-only status field and an opt-in
            // notification, so a silently-failed publish PDF is easy to miss
            // (and findLatestForContent still shows the older, succeeded PDF).
            if (publishFailure != null) {
                // Carry both the legacy and the bridged v5-skin classes: the
                // skin themes the bridged Message/is-error (and hides legacy-only
                // .message:not(.Message)), so legacy-only classes render unthemed
                // (the same recipe as the health DOWN banner and preview-problems).
                div.add(DIV.classList("message", "Message", "message-error", "is-error",
                                "PdfWidget-publishFailure")
                        .with(localize("status.publishFailed", "PDF generation on publish failed:")
                                + " " + publishFailure));
            }
            if (visible) {
                String generateUrl = new UrlBuilder(PdfGeneratePage.class,
                        page -> page.setContentId(contentId)).build();
                div.add(A.classList("button", "Button", "PdfWidget-generate")
                        .href(generateUrl)
                        .attr("target", "_blank")
                        .attr("title", localize("action.generate.title",
                                "Opens the PDF for the published content in a new tab —"
                                        + " generating it the first time, then serving the"
                                        + " stored PDF while the content is unchanged."))
                        .with(localize("action.generate", "Show PDF")));
            } else {
                div.add(DIV.className("PdfWidget-note").with(
                        localize("note.publishFirst",
                                "Publish the content to generate its PDF.")));
            }

            // Troubleshooting mode: when this article's debug/inspectable toggle
            // is on (and an administrator allows it), the Generate action above
            // produces a diagnostic build (streamed, never stored, never
            // published). Publishing is unaffected — it always produces the
            // normal production PDF. Surface that on the widget so the editor
            // understands the Generate output is a diagnostic build.
            if (visible && PdfReactorConfigs.troubleshootingActive(content)) {
                div.add(DIV.className("PdfWidget-debug").with(
                        DIV.className("PdfWidget-note").with(
                                localize("note.troubleshooting",
                                        "Debug build is on for this article — Generate produces a"
                                                + " diagnostic PDF that is never stored or published."))));
            }

            if (latest != null && latest.getPdf() != null && latest.getGenerated() != null) {
                String downloadUrl = new UrlBuilder(PdfGeneratePage.class, page -> {
                    page.setContentId(contentId);
                    page.setDownload(true);
                    // Stream this exact stored record by id — never re-look-up
                    // by the current cache key (which would miss and regenerate
                    // after a content change).
                    page.setGeneratedPdfId(latest.getId());
                }).build();
                String regenerateUrl = new UrlBuilder(PdfGeneratePage.class, page -> {
                    page.setContentId(contentId);
                    page.setRegenerate(true);
                }).build();

                int pages = latest.getNumberOfPages();
                String meta = localize("label.stored", "Stored PDF:")
                        + " " + ToolLocalization.dateTime(latest.getGenerated().getTime())
                        + " · " + pages + " " + (pages == 1
                        ? localize("label.page", "page")
                        : localize("label.pages", "pages"));
                // Append the file size when known. Rows generated before the
                // size was tracked have none, so they show pages only.
                String size = GeneratedPdf.humanReadableSize(latest.getByteSize());
                if (size != null) {
                    meta += " · " + size;
                }

                div.add(DIV.className("PdfWidget-stored").with(
                        DIV.className("PdfWidget-meta").with(meta),
                        DIV.className("PdfWidget-actions").with(
                                // target=_blank makes this a real navigation to
                                // the /cms/ endpoint (a new tab) rather than an
                                // in-frame SPA click that would paint the raw PDF
                                // bytes into the edit page. No download attribute
                                // and an inline Content-Disposition: the
                                // PDF opens in a new tab and the browser's own
                                // PDF setting governs view-vs-download, instead
                                // of forcing a file the OS auto-opens in Acrobat.
                                A.classList("link", "Link", "PdfWidget-download")
                                        .href(downloadUrl)
                                        .attr("target", "_blank")
                                        .attr("title", localize("action.download.title",
                                                "Opens the stored PDF in a new tab."))
                                        .with(localize("action.download", "Download")),
                                // NOT a navigable link: a plain widget anchor is
                                // AJAX-intercepted by the Tool's edit-form frame
                                // JS, which loaded the regenerate href into the
                                // widget frame and destroyed the right-rail layout.
                                // It carries the URL in a data attribute;
                                // pdf-widget.js POSTs it (state-changing, so POST)
                                // and reloads the edit page in place on success.
                                // No href, so the frame JS ignores it.
                                A.classList("link", "Link", "PdfWidget-regenerate")
                                        .attr("data-pdf-regenerate-url", regenerateUrl)
                                        .attr("data-pdf-regenerating-label",
                                                localize("action.regenerate.busy", "Converting…"))
                                        .attr("role", "button")
                                        .attr("tabindex", "0")
                                        .attr("title", localize("action.regenerate.title",
                                                "Re-runs the conversion and updates the stored PDF,"
                                                        + " even if the content is unchanged — use when"
                                                        + " stylesheets, images, or fonts changed outside"
                                                        + " the CMS."))
                                        .with(localize("action.regenerate", "Convert again")))));
            }
        });
    }

    /**
     * The editor-facing detail of the last publish-automation failure for this
     * content, or {@code null} when the last publish did not fail (or the type
     * has no publish PDF rendering). {@link HasPdfRenderingData} stores the
     * status as {@code "Failed: <detail>"} on failure and {@code "Success …"}
     * on success, so a later success clears the banner.
     */
    static String publishFailureDetail(Object content) {
        if (!(content instanceof HasPdfRendering)) {
            return null;
        }
        String status = State.getInstance(content).as(HasPdfRenderingData.class).getGeneratedPdfStatus();
        if (status == null || !status.startsWith("Failed")) {
            return null;
        }
        return status.startsWith("Failed:")
                ? status.substring("Failed:".length()).trim()
                : status;
    }

    private String localize(String key, String fallback) {
        return ToolLocalization.text(GeneratePdfWidget.class, key, fallback);
    }
}
