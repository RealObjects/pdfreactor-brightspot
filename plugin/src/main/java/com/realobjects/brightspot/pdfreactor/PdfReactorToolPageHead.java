package com.realobjects.brightspot.pdfreactor;

import java.util.List;

import com.psddev.cms.tool.ToolPageHead;
import com.psddev.dari.html.Node;

/**
 * Plugin contributions to every Tool page head via the documented
 * {@link ToolPageHead} extension point (auto-discovered): a small structural
 * stylesheet for the generate widget ({@code PdfWidget-*}; spacing only —
 * visual styling comes from the skin's own component classes) and the
 * PDF-preview problem-banner listener.
 *
 * <p>Both assets are real files loaded by {@link ToolResources} rather than
 * inline Java strings: {@code web/tool-head.css} and
 * {@code web/preview-problems.js}.</p>
 *
 * <p>Editor-facing messages belong in the Tool DOM, where the active theme
 * styles them — the platform itself never renders Tool-styled content inside
 * a preview iframe (iframe content is the previewed site, styled by the
 * site). So {@code PdfPreviewPage} posts its problem report to the parent
 * window, and {@code preview-problems.js} renders it as a themed banner
 * inside the preview pane's {@code PreviewFrame-typeDisplay}, sizing the PDF
 * iframe below it via the {@code --PdfPreview-bannerHeight} variable (consumed
 * by {@code preview-frame.css}). That script also carries the skin's
 * legacy/bridged {@code message}/{@code Message} class contract documented in
 * the SKIN COUPLING INVENTORY on {@code PdfPreviewType}.</p>
 */
public class PdfReactorToolPageHead implements ToolPageHead {

    @Override
    public Object getElement() {
        // A list of head nodes: ToolPageContext renders each (Components
        // .toRawNode joins an Iterable of nodes). getElement() is the current,
        // non-deprecated ToolPageHead contract.
        return List.<Node>of(
                ToolResources.styleSheet(ToolResources.WEB + "tool-head.css"),
                ToolResources.inlineScript(ToolResources.WEB + "preview-problems.js"),
                ToolResources.inlineScript(ToolResources.WEB + "pdf-widget.js"));
    }
}
