package com.realobjects.brightspot.pdfreactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageCssBuilderTest {

    @Test
    void allBlankYieldsNull() {
        assertThat(PageCssBuilder.build(null, null, null, null)).isNull();
        assertThat(PageCssBuilder.build("", "  ", "", null)).isNull();
    }

    @Test
    void paperSizeOnly() {
        assertThat(PageCssBuilder.build("A4", null, null, null))
                .isEqualTo("@page { size: A4; }");
    }

    @Test
    void paperSizeAndMargin() {
        assertThat(PageCssBuilder.build("A4 landscape", "20mm 15mm", null, null))
                .isEqualTo("@page { size: A4 landscape; margin: 20mm 15mm; }");
    }

    @Test
    void headerAndFooterMarginBoxes() {
        assertThat(PageCssBuilder.build(null, null, "\"Acme Corp\"", "counter(page) \" / \" counter(pages)"))
                .isEqualTo("@page {"
                        + " @top-center { content: \"Acme Corp\"; }"
                        + " @bottom-center { content: counter(page) \" / \" counter(pages); }"
                        + " }");
    }

    @Test
    void everythingCombined() {
        assertThat(PageCssBuilder.build("letter", "1in", "\"H\"", "\"F\""))
                .isEqualTo("@page { size: letter; margin: 1in;"
                        + " @top-center { content: \"H\"; }"
                        + " @bottom-center { content: \"F\"; }"
                        + " }");
    }

    @Test
    void valuesAreTrimmed() {
        assertThat(PageCssBuilder.build(" A4 ", " 2cm ", null, null))
                .isEqualTo("@page { size: A4; margin: 2cm; }");
    }
}
