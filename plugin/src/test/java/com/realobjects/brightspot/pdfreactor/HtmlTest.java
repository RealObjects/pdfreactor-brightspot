package com.realobjects.brightspot.pdfreactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTest {

    @Test
    void escapesTheFiveMarkupCharacters() {
        assertThat(Html.escape("a & b < c > d \" e ' f"))
                .isEqualTo("a &amp; b &lt; c &gt; d &quot; e &#39; f");
    }

    @Test
    void escapesAmpersandFirstToAvoidDoubleEscaping() {
        assertThat(Html.escape("&lt;")).isEqualTo("&amp;lt;");
    }

    @Test
    void nullBecomesEmptyString() {
        assertThat(Html.escape(null)).isEmpty();
    }
}
