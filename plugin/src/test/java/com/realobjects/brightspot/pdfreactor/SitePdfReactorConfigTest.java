package com.realobjects.brightspot.pdfreactor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.psddev.test.db.TestDatabaseExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layering behavior of {@link SitePdfReactorConfig}: a non-blank site value
 * wins, a blank/unset one inherits the global delegate.
 */
@ExtendWith(TestDatabaseExtension.class)
class SitePdfReactorConfigTest {

    /** Fixed global delegate with distinctive values. */
    private static final PdfReactorConfig GLOBAL = new PdfReactorConfig() {
        @Override
        public String getServiceUrl() {
            return "http://global:9423/service/rest";
        }

        @Override
        public String getApiKey() {
            return "global-key";
        }

        @Override
        public String getBaseUrl() {
            return "http://global/base";
        }

        @Override
        public List<String> getDefaultUserStyleSheetUris() {
            return Collections.singletonList("global.css");
        }
    };

    @Test
    void siteOverridesWhenSet() {
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setBaseUrl("http://site/base");
        site.setDefaultUserStyleSheetUris(Arrays.asList("a.css", "b.css"));

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, GLOBAL);

        assertThat(config.getBaseUrl()).isEqualTo("http://site/base");
        assertThat(config.getDefaultUserStyleSheetUris()).containsExactly("a.css", "b.css");
    }

    @Test
    void inheritsGlobalWhenUnset() {
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, GLOBAL);

        assertThat(config.getBaseUrl()).isEqualTo("http://global/base");
        assertThat(config.getDefaultUserStyleSheetUris()).containsExactly("global.css");
    }

    // Service URL and API key are deploy-time/global only: they are
    // never layered per site, so SitePdfReactorConfig always returns the
    // global value regardless of the site settings.
    @Test
    void serviceUrlAndApiKeyAlwaysComeFromGlobal() {
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, GLOBAL);

        assertThat(config.getServiceUrl()).isEqualTo("http://global:9423/service/rest");
        assertThat(config.getApiKey()).isEqualTo("global-key");
    }

    @Test
    void blankSiteValueInheritsGlobal() {
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setBaseUrl("   ");

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, GLOBAL);

        assertThat(config.getBaseUrl()).isEqualTo("http://global/base");
    }

    @Test
    void layersConformance() {
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setConformance(PdfReactorSiteSettings.Conformance.PDFA2B);
        assertThat(new SitePdfReactorConfig(site, GLOBAL).getConformance())
                .isEqualTo(com.realobjects.pdfreactor.webservice.client.Configuration
                        .Conformance.PDFA2B);
    }

    @Test
    void conformanceInheritsGlobalWhenSiteUnset() {
        PdfReactorConfig globalWithConformance = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://g/service/rest";
            }

            @Override
            public com.realobjects.pdfreactor.webservice.client.Configuration.Conformance getConformance() {
                return com.realobjects.pdfreactor.webservice.client.Configuration.Conformance.PDFUA1;
            }
        };
        assertThat(new SitePdfReactorConfig(new PdfReactorSiteSettings(), globalWithConformance)
                .getConformance())
                .isEqualTo(com.realobjects.pdfreactor.webservice.client.Configuration
                        .Conformance.PDFUA1);
    }

    @Test
    void layersJavaScriptEnabledSiteOverGlobal() {
        PdfReactorConfig globalJsOn = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://global:9423/service/rest";
            }

            @Override
            public Boolean getJavaScriptEnabled() {
                return Boolean.TRUE;
            }
        };

        // Unset on the site → inherit the global value.
        SitePdfReactorConfig inherited =
                new SitePdfReactorConfig(new PdfReactorSiteSettings(), globalJsOn);
        assertThat(inherited.getJavaScriptEnabled()).isTrue();

        // Set on the site → the site value wins (here, turning the global on off).
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setJavaScriptEnabled(Boolean.FALSE);
        SitePdfReactorConfig overridden = new SitePdfReactorConfig(site, globalJsOn);
        assertThat(overridden.getJavaScriptEnabled()).isFalse();
    }

    @Test
    void layersIccIdentifierAndEnabled() {
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setOutputIntentIdentifier("ISO Coated v2");
        site.setColorConversionEnabled(Boolean.TRUE);

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, GLOBAL);

        assertThat(config.getOutputIntentIdentifier()).isEqualTo("ISO Coated v2");
        assertThat(config.getColorConversionEnabled()).isTrue();
    }

    @Test
    void colorConversionIntentComesFromGlobalOnly() {
        // Not a per-site field; it delegates straight to the global config.
        PdfReactorConfig globalWithIntent = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://global:9423/service/rest";
            }

            @Override
            public com.realobjects.pdfreactor.webservice.client.Configuration.ColorConversionIntent
                    getColorConversionIntent() {
                return com.realobjects.pdfreactor.webservice.client.Configuration
                        .ColorConversionIntent.PERCEPTIVE;
            }
        };
        SitePdfReactorConfig config = new SitePdfReactorConfig(new PdfReactorSiteSettings(), globalWithIntent);
        assertThat(config.getColorConversionIntent())
                .isEqualTo(com.realobjects.pdfreactor.webservice.client.Configuration
                        .ColorConversionIntent.PERCEPTIVE);
    }

    @Test
    void referencedProfileBytesWinOverGlobal() {
        // The ICC fields reference a reusable IccProfile record.
        // Its bytes (read server-side) must win over the global delegate, the
        // same way the old inline StorageItem upload did.
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setOutputIntentProfile(profileReturning(new byte[] {1, 2, 3}));
        site.setCmykIccProfile(profileReturning(new byte[] {7, 8}));

        PdfReactorConfig globalWithIcc = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://global:9423/service/rest";
            }

            @Override
            public byte[] getOutputIntentProfileData() {
                return new byte[] {9, 9, 9};
            }

            @Override
            public byte[] getCmykIccProfileData() {
                return new byte[] {9, 9, 9};
            }
        };

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, globalWithIcc);

        assertThat(config.getOutputIntentProfileData()).containsExactly(1, 2, 3);
        assertThat(config.getCmykIccProfileData()).containsExactly(7, 8);
    }

    /** An IccProfile whose server-side byte read yields the given bytes. */
    private static IccProfile profileReturning(byte[] bytes) {
        return new IccProfile() {
            @Override
            public byte[] readBytes() {
                return bytes;
            }
        };
    }

    @Test
    void iccInheritsGlobalWhenSiteUnset() {
        PdfReactorConfig globalWithIcc = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://global:9423/service/rest";
            }

            @Override
            public byte[] getOutputIntentProfileData() {
                return new byte[] {9, 9, 9};
            }

            @Override
            public String getOutputIntentIdentifier() {
                return "FOGRA39";
            }
        };

        SitePdfReactorConfig config = new SitePdfReactorConfig(new PdfReactorSiteSettings(), globalWithIcc);

        assertThat(config.getOutputIntentIdentifier()).isEqualTo("FOGRA39");
        assertThat(config.getOutputIntentProfileData()).containsExactly(9, 9, 9);
    }

    @Test
    void configurationJsonDeepMergesSiteOverGlobal() {
        PdfReactorConfig globalWithJson = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://global:9423/service/rest";
            }

            @Override
            public String getConfigurationJson() {
                return "{\"author\":\"Global\",\"viewerPreferences\":[\"FIT_WINDOW\"]}";
            }
        };
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setConfigurationJson("{\"author\":\"Site\",\"title\":\"Site Title\"}");

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, globalWithJson);
        JsonNode merged = RawConfiguration.parse(config.getConfigurationJson());

        assertThat(merged.get("author").asText()).isEqualTo("Site"); // site overrides
        assertThat(merged.get("title").asText()).isEqualTo("Site Title"); // site adds
        assertThat(merged.get("viewerPreferences").get(0).asText())
                .isEqualTo("FIT_WINDOW"); // global kept
    }

    @Test
    void emptyStyleSheetListInheritsGlobal() {
        PdfReactorSiteSettings site = new PdfReactorSiteSettings();
        site.setDefaultUserStyleSheetUris(Collections.emptyList());

        SitePdfReactorConfig config = new SitePdfReactorConfig(site, GLOBAL);

        assertThat(config.getDefaultUserStyleSheetUris()).containsExactly("global.css");
    }
}
