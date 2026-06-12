package com.realobjects.brightspot.pdfreactor;

import com.psddev.dari.util.Settings;
import com.realobjects.pdfreactor.webservice.client.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsPdfReactorConfigTest {

    private final SettingsPdfReactorConfig config = new SettingsPdfReactorConfig();

    @AfterEach
    void clearOverrides() {
        Settings.setOverride(SettingsPdfReactorConfig.SERVICE_URL_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.API_KEY_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.CLIENT_TIMEOUT_MILLIS_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.CONVERSION_TIMEOUT_SECONDS_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.ASYNC_DEFAULT_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.DEFAULT_USER_STYLE_SHEET_URIS_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.BASE_URL_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.LOG_LEVEL_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.OUTPUT_INTENT_IDENTIFIER_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.OUTPUT_INTENT_PROFILE_URI_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.CMYK_ICC_PROFILE_URI_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.COLOR_CONVERSION_ENABLED_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.COLOR_CONVERSION_INTENT_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.CONFIGURATION_JSON_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.CONFORMANCE_SETTING, null);
        Settings.setOverride(SettingsPdfReactorConfig.JAVA_SCRIPT_ENABLED_SETTING, null);
    }

    @Test
    void unconfiguredServiceUrlIsNull() {
        assertThat(config.getServiceUrl()).isNull();
    }

    @Test
    void readsServiceUrl() {
        Settings.setOverride(SettingsPdfReactorConfig.SERVICE_URL_SETTING,
                "http://pdfreactor:9423/service/rest");
        assertThat(config.getServiceUrl()).isEqualTo("http://pdfreactor:9423/service/rest");
    }

    @Test
    void defaultsApplyWhenUnset() {
        assertThat(config.getClientTimeoutMillis()).isEqualTo(60_000);
        assertThat(config.getConversionTimeoutSeconds()).isEqualTo(300);
        assertThat(config.getAsyncPollIntervalMillis()).isEqualTo(1_000L);
        assertThat(config.isAsyncDefault()).isFalse();
        assertThat(config.getDefaultUserStyleSheetUris()).isEmpty();
        assertThat(config.getBaseUrl()).isNull();
        assertThat(config.getLogLevel()).isEqualTo(Configuration.LogLevel.WARN);
    }

    @Test
    void readsNumericAndBooleanOverrides() {
        Settings.setOverride(SettingsPdfReactorConfig.CLIENT_TIMEOUT_MILLIS_SETTING, "120000");
        Settings.setOverride(SettingsPdfReactorConfig.CONVERSION_TIMEOUT_SECONDS_SETTING, "600");
        Settings.setOverride(SettingsPdfReactorConfig.ASYNC_DEFAULT_SETTING, "true");

        assertThat(config.getClientTimeoutMillis()).isEqualTo(120_000);
        assertThat(config.getConversionTimeoutSeconds()).isEqualTo(600);
        assertThat(config.isAsyncDefault()).isTrue();
    }

    @Test
    void splitsStyleSheetUrisOnCommas() {
        Settings.setOverride(SettingsPdfReactorConfig.DEFAULT_USER_STYLE_SHEET_URIS_SETTING,
                "https://cdn.example.com/print.css, https://cdn.example.com/brand.css ,,");

        assertThat(config.getDefaultUserStyleSheetUris()).containsExactly(
                "https://cdn.example.com/print.css",
                "https://cdn.example.com/brand.css");
    }

    @Test
    void javaScriptEnabledDefaultsNullAndReadsOverride() {
        assertThat(config.getJavaScriptEnabled()).isNull();

        Settings.setOverride(SettingsPdfReactorConfig.JAVA_SCRIPT_ENABLED_SETTING, "false");
        assertThat(config.getJavaScriptEnabled()).isFalse();
    }

    @Test
    void parsesLogLevelCaseInsensitively() {
        Settings.setOverride(SettingsPdfReactorConfig.LOG_LEVEL_SETTING, "debug");
        assertThat(config.getLogLevel()).isEqualTo(Configuration.LogLevel.DEBUG);
    }

    @Test
    void invalidLogLevelWarnsAndDefaults() {
        Settings.setOverride(SettingsPdfReactorConfig.LOG_LEVEL_SETTING, "nonsense");
        // The log level does not affect output, so a typo must not abort a
        // conversion: it falls back to the default (and logs a warning).
        assertThat(config.getLogLevel()).isEqualTo(Configuration.LogLevel.WARN);
    }

    @Test
    void invalidConformanceThrowsNamingTheSetting() {
        Settings.setOverride(SettingsPdfReactorConfig.CONFORMANCE_SETTING, "PDFA9Z");
        // Output-affecting: a typo must fail clean (naming the setting and the
        // valid values), not silently produce the wrong print artifact.
        assertThatThrownBy(config::getConformance)
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining(SettingsPdfReactorConfig.CONFORMANCE_SETTING)
                .hasMessageContaining("PDFA3A");
    }

    @Test
    void invalidColorConversionIntentThrowsNamingTheSetting() {
        Settings.setOverride(SettingsPdfReactorConfig.COLOR_CONVERSION_INTENT_SETTING, "BOGUS");
        assertThatThrownBy(config::getColorConversionIntent)
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining(SettingsPdfReactorConfig.COLOR_CONVERSION_INTENT_SETTING)
                .hasMessageContaining("PERCEPTIVE");
    }

    @Test
    void iccDefaultsAreUnset() {
        assertThat(config.getOutputIntentIdentifier()).isNull();
        assertThat(config.getOutputIntentProfileData()).isNull();
        assertThat(config.getCmykIccProfileData()).isNull();
        assertThat(config.getColorConversionEnabled()).isNull();
        assertThat(config.getColorConversionIntent()).isNull();
    }

    @Test
    void readsIccSettings() {
        Settings.setOverride(SettingsPdfReactorConfig.OUTPUT_INTENT_IDENTIFIER_SETTING, "FOGRA39");
        Settings.setOverride(SettingsPdfReactorConfig.OUTPUT_INTENT_PROFILE_URI_SETTING,
                "classpath:icc/test-profile.bin");
        Settings.setOverride(SettingsPdfReactorConfig.COLOR_CONVERSION_ENABLED_SETTING, "true");
        Settings.setOverride(SettingsPdfReactorConfig.COLOR_CONVERSION_INTENT_SETTING, "perceptive");

        assertThat(config.getOutputIntentIdentifier()).isEqualTo("FOGRA39");
        assertThat(new String(config.getOutputIntentProfileData())).startsWith("ICC-TEST");
        assertThat(config.getColorConversionEnabled()).isTrue();
        assertThat(config.getColorConversionIntent())
                .isEqualTo(Configuration.ColorConversionIntent.PERCEPTIVE);
    }

    @Test
    void conformanceDefaultsToNullAndParsesCaseInsensitively() {
        assertThat(config.getConformance()).isNull();
        Settings.setOverride(SettingsPdfReactorConfig.CONFORMANCE_SETTING, "pdfa3a");
        assertThat(config.getConformance()).isEqualTo(Configuration.Conformance.PDFA3A);
    }

    @Test
    void readsConfigurationJson() {
        assertThat(config.getConfigurationJson()).isNull();
        Settings.setOverride(SettingsPdfReactorConfig.CONFIGURATION_JSON_SETTING, "{\"author\":\"X\"}");
        assertThat(config.getConfigurationJson()).isEqualTo("{\"author\":\"X\"}");
    }
}
