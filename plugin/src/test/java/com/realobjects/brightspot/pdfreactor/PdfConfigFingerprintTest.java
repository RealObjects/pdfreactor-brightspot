package com.realobjects.brightspot.pdfreactor;

import java.util.Collections;
import java.util.List;

import com.realobjects.pdfreactor.webservice.client.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfConfigFingerprintTest {

    /** Mutable stub so each case tweaks one config-sourced value. */
    private static final class StubConfig implements PdfReactorConfig {
        String baseUrl;
        List<String> styleSheets = Collections.emptyList();
        String outputIntentIdentifier;
        byte[] outputIntentProfileData;
        byte[] cmykIccProfileData;
        Boolean colorConversionEnabled;
        Configuration.ColorConversionIntent colorConversionIntent;
        String configurationJson;
        Configuration.Conformance conformance;
        Boolean javaScriptEnabled;
        String creator;
        Boolean addBookmarks;
        java.util.Map<String, String> customDocumentProperties = Collections.emptyMap();

        @Override
        public String getServiceUrl() {
            return "http://svc";
        }

        @Override
        public Configuration.Conformance getConformance() {
            return conformance;
        }

        @Override
        public Boolean getJavaScriptEnabled() {
            return javaScriptEnabled;
        }

        @Override
        public String getCreator() {
            return creator;
        }

        @Override
        public Boolean getAddBookmarks() {
            return addBookmarks;
        }

        @Override
        public java.util.Map<String, String> getCustomDocumentProperties() {
            return customDocumentProperties;
        }

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        @Override
        public List<String> getDefaultUserStyleSheetUris() {
            return styleSheets;
        }

        @Override
        public String getOutputIntentIdentifier() {
            return outputIntentIdentifier;
        }

        @Override
        public byte[] getOutputIntentProfileData() {
            return outputIntentProfileData;
        }

        @Override
        public byte[] getCmykIccProfileData() {
            return cmykIccProfileData;
        }

        @Override
        public Boolean getColorConversionEnabled() {
            return colorConversionEnabled;
        }

        @Override
        public Configuration.ColorConversionIntent getColorConversionIntent() {
            return colorConversionIntent;
        }

        @Override
        public String getConfigurationJson() {
            return configurationJson;
        }
    }

    @Test
    void deterministicAndHex() {
        StubConfig config = new StubConfig();
        assertThat(PdfConfigFingerprint.of(config))
                .isEqualTo(PdfConfigFingerprint.of(config))
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    @Test
    void baseUrlChangesFingerprint() {
        StubConfig a = new StubConfig();
        StubConfig b = new StubConfig();
        b.baseUrl = "https://site-b/";
        assertThat(PdfConfigFingerprint.of(a)).isNotEqualTo(PdfConfigFingerprint.of(b));
    }

    @Test
    void styleSheetsChangeFingerprint() {
        StubConfig a = new StubConfig();
        StubConfig b = new StubConfig();
        b.styleSheets = Collections.singletonList("print.css");
        assertThat(PdfConfigFingerprint.of(a)).isNotEqualTo(PdfConfigFingerprint.of(b));
    }

    @Test
    void iccProfileBytesChangeFingerprint() {
        StubConfig a = new StubConfig();
        a.outputIntentProfileData = new byte[] {1, 2, 3};
        StubConfig b = new StubConfig();
        b.outputIntentProfileData = new byte[] {4, 5, 6};
        assertThat(PdfConfigFingerprint.of(a)).isNotEqualTo(PdfConfigFingerprint.of(b));
    }

    @Test
    void conformanceChangesFingerprint() {
        StubConfig a = new StubConfig();
        StubConfig b = new StubConfig();
        b.conformance = Configuration.Conformance.PDFA3A;
        assertThat(PdfConfigFingerprint.of(a)).isNotEqualTo(PdfConfigFingerprint.of(b));
    }

    @Test
    void javaScriptEnabledChangesFingerprint() {
        StubConfig a = new StubConfig();
        StubConfig b = new StubConfig();
        b.javaScriptEnabled = Boolean.FALSE;
        assertThat(PdfConfigFingerprint.of(a)).isNotEqualTo(PdfConfigFingerprint.of(b));
    }

    @Test
    void documentMetadataAndFeaturesChangeFingerprint() {
        StubConfig base = new StubConfig();
        StubConfig withCreator = new StubConfig();
        withCreator.creator = "Acme";
        StubConfig withBookmarks = new StubConfig();
        withBookmarks.addBookmarks = Boolean.TRUE;
        assertThat(PdfConfigFingerprint.of(base)).isNotEqualTo(PdfConfigFingerprint.of(withCreator));
        assertThat(PdfConfigFingerprint.of(base)).isNotEqualTo(PdfConfigFingerprint.of(withBookmarks));
    }

    @Test
    void customDocumentPropertiesChangeFingerprint() {
        StubConfig base = new StubConfig();
        StubConfig withProps = new StubConfig();
        withProps.customDocumentProperties = java.util.Collections.singletonMap("k", "v");
        StubConfig otherValue = new StubConfig();
        otherValue.customDocumentProperties = java.util.Collections.singletonMap("k", "different");
        assertThat(PdfConfigFingerprint.of(base)).isNotEqualTo(PdfConfigFingerprint.of(withProps));
        assertThat(PdfConfigFingerprint.of(withProps)).isNotEqualTo(PdfConfigFingerprint.of(otherValue));
    }

    @Test
    void configurationJsonChangesFingerprint() {
        StubConfig a = new StubConfig();
        StubConfig b = new StubConfig();
        b.configurationJson = "{\"author\":\"X\"}";
        assertThat(PdfConfigFingerprint.of(a)).isNotEqualTo(PdfConfigFingerprint.of(b));
    }

    @Test
    void identifierAndIntentChangeFingerprint() {
        StubConfig base = new StubConfig();
        StubConfig withId = new StubConfig();
        withId.outputIntentIdentifier = "ISO Coated v2";
        StubConfig withIntent = new StubConfig();
        withIntent.colorConversionIntent = Configuration.ColorConversionIntent.PERCEPTIVE;

        assertThat(PdfConfigFingerprint.of(base)).isNotEqualTo(PdfConfigFingerprint.of(withId));
        assertThat(PdfConfigFingerprint.of(base)).isNotEqualTo(PdfConfigFingerprint.of(withIntent));
    }
}
