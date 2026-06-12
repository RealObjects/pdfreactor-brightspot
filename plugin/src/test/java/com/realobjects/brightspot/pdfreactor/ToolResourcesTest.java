package com.realobjects.brightspot.pdfreactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ToolResources} loads the plugin's static web assets from the
 * classpath and wraps them in raw {@code <style>}/{@code <script>} nodes
 * (no entity-escaping of CSS combinators or JS operators).
 */
class ToolResourcesTest {

    @Test
    void loadsCssAssetText() {
        String css = ToolResources.text(ToolResources.WEB + "tool-head.css");
        assertThat(css).contains(".PdfWidget-actions");
    }

    @Test
    void styleSheetEmitsRawStyleTag() {
        String html = ToolResources.styleSheet(ToolResources.WEB + "preview-frame.css").toString();
        assertThat(html).startsWith("<style>").endsWith("</style>");
        assertThat(html).contains(".PdfPreviewFrame");
        // Raw: the :not() selector and quoted glyph survive un-escaped.
        assertThat(html).contains("iframe:not(.Preview-overlayFrame)")
                .contains("content: '\\e149'")
                .doesNotContain("&gt;").doesNotContain("&lt;").doesNotContain("&amp;");
    }

    @Test
    void inlineScriptEmitsRawScriptTag() {
        String html = ToolResources.inlineScript(ToolResources.WEB + "preview-problems.js").toString();
        assertThat(html).startsWith("<script>").endsWith("</script>");
        // Raw: JS comparison operators survive (would break if HTML-escaped).
        assertThat(html).contains("pdfreactor-preview-problems")
                .contains("i < displays.length")
                .doesNotContain("&lt;");
    }

    @Test
    void loadsCircleLogoAsset() {
        // The health-widget circle mark. It must be a real <svg> fragment
        // (the widget strips the XML prolog and inlines it) carrying the brand
        // colors, and must NOT include the dropped "RealObjects PDFreactor"
        // wordmark group.
        String svg = ToolResources.text(ToolResources.WEB + "pdfreactor-logo.svg");
        assertThat(svg).contains("<svg").contains("viewBox").contains("#0b379f");
        assertThat(svg).doesNotContain("id=\"text\"").doesNotContain("realobjects");
    }

    @Test
    void missingAssetFailsClearly() {
        assertThatThrownBy(() -> ToolResources.text(ToolResources.WEB + "does-not-exist.css"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist.css");
    }
}
