package com.realobjects.brightspot.pdfreactor;

import com.realobjects.pdfreactor.webservice.client.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfRenderOptionsTest {

    @DefaultPdfReactorConfiguration(
            paperSize = "A4",
            margin = "20mm",
            headerContent = "\"Acme\"",
            footerContent = "counter(page)",
            conformance = Configuration.Conformance.PDFA3A,
            userStyleSheetUris = { "https://cdn.example.com/print.css" })
    private static final class AnnotatedView {
    }

    private static final class PlainView {
    }

    @DefaultPdfReactorConfiguration(
            javaScript = DefaultPdfReactorConfiguration.JavaScript.DISABLED)
    private static final class JavaScriptOffView {
    }

    @Test
    void defaultsFailClosedOnLicenseOnly() {
        PdfRenderOptions options = PdfRenderOptions.builder().build();

        assertThat(options.isFailOnLicenseProblems()).isTrue();
        assertThat(options.isFailOnMissingResources()).isFalse();
        // Tri-state: unset by default (inherits config, then the enabled default).
        assertThat(options.getJavaScriptEnabled()).isNull();
        assertThat(options.getAsync()).isNull();
        assertThat(options.getConformance()).isNull();
        assertThat(options.getStyleSheets()).isEmpty();
        assertThat(options.toPageCss()).isNull();
    }

    @Test
    void troubleshootingFlagsDefaultOffAndBuildAndOverride() {
        assertThat(PdfRenderOptions.builder().build().isDebug()).isFalse();
        assertThat(PdfRenderOptions.builder().build().isInspectable()).isFalse();

        PdfRenderOptions set = PdfRenderOptions.builder()
                .debug(true)
                .inspectable(true)
                .build();
        assertThat(set.isDebug()).isTrue();
        assertThat(set.isInspectable()).isTrue();

        // overrideWith carries the per-call troubleshooting flags (the
        // annotation seed has none).
        PdfRenderOptions effective = PdfRenderOptions.fromAnnotated(AnnotatedView.class)
                .overrideWith(set)
                .build();
        assertThat(effective.isDebug()).isTrue();
        assertThat(effective.isInspectable()).isTrue();
    }

    @Test
    void fromAnnotatedSeedsAllAnnotationValues() {
        PdfRenderOptions options = PdfRenderOptions.fromAnnotated(AnnotatedView.class).build();

        assertThat(options.getPaperSize()).isEqualTo("A4");
        assertThat(options.getMargin()).isEqualTo("20mm");
        assertThat(options.getHeaderContent()).isEqualTo("\"Acme\"");
        assertThat(options.getFooterContent()).isEqualTo("counter(page)");
        assertThat(options.getConformance()).isEqualTo(Configuration.Conformance.PDFA3A);
        assertThat(options.getStyleSheets())
                .containsExactly(PdfStyleSheet.fromUri("https://cdn.example.com/print.css"));
        assertThat(options.toPageCss()).contains("size: A4;").contains("margin: 20mm;");
    }

    @Test
    void javaScriptAnnotationTriStateSeedsTheFlag() {
        // DEFAULT (the AnnotatedView leaves it unset) → unset, so it inherits.
        assertThat(PdfRenderOptions.fromAnnotated(AnnotatedView.class).build()
                .getJavaScriptEnabled()).isNull();
        // DISABLED pins it off for the view.
        assertThat(PdfRenderOptions.fromAnnotated(JavaScriptOffView.class).build()
                .getJavaScriptEnabled()).isFalse();
    }

    @Test
    void fromAnnotatedWithoutAnnotationYieldsPlainDefaults() {
        PdfRenderOptions options = PdfRenderOptions.fromAnnotated(PlainView.class).build();

        assertThat(options.getPaperSize()).isNull();
        assertThat(options.getConformance()).isNull();
        assertThat(options.getStyleSheets()).isEmpty();
    }

    @Test
    void overrideWithLayersPerCallOverAnnotation() {
        PdfRenderOptions perCall = PdfRenderOptions.builder()
                .paperSize("letter")
                .failOnMissingResources(true)
                .addStyleSheet(PdfStyleSheet.inline("p { color: red; }"))
                .build();

        PdfRenderOptions effective = PdfRenderOptions.fromAnnotated(AnnotatedView.class)
                .overrideWith(perCall)
                .build();

        // Per-call wins where set; annotation kept where the per-call is silent.
        assertThat(effective.getPaperSize()).isEqualTo("letter");
        assertThat(effective.getMargin()).isEqualTo("20mm");
        assertThat(effective.getFooterContent()).isEqualTo("counter(page)");
        assertThat(effective.getConformance()).isEqualTo(Configuration.Conformance.PDFA3A);
        // Error policy comes from the per-call options (the annotation has none).
        assertThat(effective.isFailOnMissingResources()).isTrue();
        // Annotation stylesheets are defaults; per-call ones are appended.
        assertThat(effective.getStyleSheets()).containsExactly(
                PdfStyleSheet.fromUri("https://cdn.example.com/print.css"),
                PdfStyleSheet.inline("p { color: red; }"));
        assertThat(effective.toPageCss()).contains("size: letter;").contains("margin: 20mm;");
    }

    @Test
    void builderOverridesAnnotationSeed() {
        PdfRenderOptions options = PdfRenderOptions.fromAnnotated(AnnotatedView.class)
                .paperSize("letter")
                .failOnMissingResources(true)
                .async(Boolean.TRUE)
                .build();

        assertThat(options.getPaperSize()).isEqualTo("letter");
        assertThat(options.getMargin()).isEqualTo("20mm");
        assertThat(options.isFailOnMissingResources()).isTrue();
        assertThat(options.getAsync()).isTrue();
    }
}
