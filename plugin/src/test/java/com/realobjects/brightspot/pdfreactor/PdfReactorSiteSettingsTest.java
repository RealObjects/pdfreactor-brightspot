package com.realobjects.brightspot.pdfreactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Save-time validation of the per-site free-text fields, so a malformed value
 * is reported in the form instead of failing a later conversion.
 */
class PdfReactorSiteSettingsTest {

    @Test
    void blankValuesAreValid() {
        assertThat(PdfReactorSiteSettings.validateConfigurationJson(null)).isNull();
        assertThat(PdfReactorSiteSettings.validateConfigurationJson("  ")).isNull();
    }

    @Test
    void validConfigurationJsonPasses() {
        assertThat(PdfReactorSiteSettings.validateConfigurationJson("{\"author\":\"X\"}")).isNull();
    }

    @Test
    void malformedConfigurationJsonIsReported() {
        assertThat(PdfReactorSiteSettings.validateConfigurationJson("{not json")).isNotNull();
    }
}
