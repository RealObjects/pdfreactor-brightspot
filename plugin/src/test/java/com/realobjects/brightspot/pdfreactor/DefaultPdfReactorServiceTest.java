package com.realobjects.brightspot.pdfreactor;

import java.util.List;

import com.realobjects.pdfreactor.webservice.client.Configuration;
import com.realobjects.pdfreactor.webservice.client.Connection;
import com.realobjects.pdfreactor.webservice.client.MissingResource;
import com.realobjects.pdfreactor.webservice.client.PDFreactor;
import com.realobjects.pdfreactor.webservice.client.PDFreactorWebserviceException;
import com.realobjects.pdfreactor.webservice.client.Progress;
import com.realobjects.pdfreactor.webservice.client.Result;
import com.realobjects.pdfreactor.webservice.client.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPdfReactorServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 fake".getBytes();

    private final PdfReactorConfig config = new PdfReactorConfig() {

        @Override
        public String getServiceUrl() {
            return "http://localhost:9423/service/rest";
        }

        @Override
        public long getAsyncPollIntervalMillis() {
            return 50L;
        }
    };

    @Mock
    private PDFreactor client;

    private DefaultPdfReactorService newService() {
        return new DefaultPdfReactorService(config, client);
    }

    private static Result resultWithDocument() {
        Result result = new Result();
        result.setDocument(PDF_BYTES);
        result.setContentType("application/pdf");
        result.setNumberOfPages(3);
        return result;
    }

    @Test
    void syncConversionReturnsPdfResult() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfResult result = newService().renderHtml("<html><body>x</body></html>", null);

        assertThat(result.getDocument()).isEqualTo(PDF_BYTES);
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getNumberOfPages()).isEqualTo(3);
        assertThat(result.getDiagnostics().hasProblems()).isFalse();
    }

    @Test
    void configurationAssembly() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfRenderOptions options = PdfRenderOptions.builder()
                .baseUrl("https://www.example.com/")
                .addStyleSheet(PdfStyleSheet.fromUri("https://cdn.example.com/print.css"))
                .paperSize("A4")
                .margin("20mm")
                .conformance(Configuration.Conformance.PDFUA1)
                .failOnMissingResources(true)
                .conversionTimeoutSeconds(120)
                .title("Doc Title")
                .author("Doc Author")
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();

        assertThat(configuration.getDocument()).isEqualTo("<html/>");
        @SuppressWarnings("deprecation")
        String baseUrl = configuration.getBaseUrl();
        assertThat(baseUrl).isEqualTo("https://www.example.com/");
        assertThat(configuration.getErrorPolicies()).containsExactlyInAnyOrder(
                Configuration.ErrorPolicy.LICENSE,
                Configuration.ErrorPolicy.MISSING_RESOURCE);
        assertThat(configuration.getConformance()).isEqualTo(Configuration.Conformance.PDFUA1);
        assertThat(configuration.getConversionTimeout()).isEqualTo(120);
        assertThat(configuration.getTitle()).isEqualTo("Doc Title");
        assertThat(configuration.getAuthor()).isEqualTo("Doc Author");
        assertThat(configuration.getContentObserver().getMissingResources()).isTrue();
        assertThat(configuration.getContentObserver().getConnections()).isTrue();
        // No JavaScript option set anywhere -> the built-in default is ON.
        assertThat(configuration.getJavaScriptSettings().getDisabled()).isFalse();

        List<Configuration.Resource> styleSheets = configuration.getUserStyleSheets();
        assertThat(styleSheets).hasSize(2);
        assertThat(styleSheets.get(0).getUri()).isEqualTo("https://cdn.example.com/print.css");
        assertThat(styleSheets.get(1).getContent()).startsWith("@page {")
                .contains("size: A4;")
                .contains("margin: 20mm;");
    }

    @Test
    void colorManagementFromOptions() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        byte[] outputIntentBytes = {10, 20, 30};
        byte[] cmykBytes = {40, 50};
        PdfRenderOptions options = PdfRenderOptions.builder()
                .outputIntentIdentifier("ISO Coated v2 300% (ECI)")
                .outputIntentProfileData(outputIntentBytes)
                .cmykIccProfileData(cmykBytes)
                .colorConversionEnabled(Boolean.TRUE)
                .colorConversionIntent(Configuration.ColorConversionIntent.RELATIVE_COLORIMETRIC)
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();

        assertThat(configuration.getOutputIntent().getIdentifier()).isEqualTo("ISO Coated v2 300% (ECI)");
        assertThat(configuration.getOutputIntent().getData()).isEqualTo(outputIntentBytes);
        assertThat(configuration.getColorSpaceSettings().getCmykIccProfile().getData()).isEqualTo(cmykBytes);
        assertThat(configuration.getColorSpaceSettings().getConversionEnabled()).isTrue();
        assertThat(configuration.getColorSpaceSettings().getColorConversionIntent())
                .isEqualTo(Configuration.ColorConversionIntent.RELATIVE_COLORIMETRIC);
    }

    @Test
    void colorManagementFromConfigWhenOptionsUnset() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        byte[] configOutputIntent = {7, 7, 7};
        PdfReactorConfig iccConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public String getOutputIntentIdentifier() {
                return "FOGRA39";
            }

            @Override
            public byte[] getOutputIntentProfileData() {
                return configOutputIntent;
            }
        };

        new DefaultPdfReactorService(iccConfig, client).renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();

        assertThat(configuration.getOutputIntent().getIdentifier()).isEqualTo("FOGRA39");
        assertThat(configuration.getOutputIntent().getData()).isEqualTo(configOutputIntent);
        // No CMYK / conversion settings configured -> no color-space settings.
        assertThat(configuration.getColorSpaceSettings()).isNull();
    }

    @Test
    void noColorManagementWhenUnset() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        newService().renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getOutputIntent()).isNull();
        assertThat(captor.getValue().getColorSpaceSettings()).isNull();
    }

    @Test
    void troubleshootingBuildsFromOptions() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfRenderOptions options = PdfRenderOptions.builder()
                .debug(true)
                .inspectable(true)
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();
        assertThat(configuration.getDebugSettings()).isNotNull();
        assertThat(configuration.getDebugSettings().getAll()).isTrue();
        assertThat(configuration.getInspectableSettings()).isNotNull();
        assertThat(configuration.getInspectableSettings().getEnabled()).isTrue();
    }

    @Test
    void noTroubleshootingBuildsByDefault() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        newService().renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getDebugSettings()).isNull();
        assertThat(captor.getValue().getInspectableSettings()).isNull();
    }

    @Test
    void conformanceFromConfigAppliesWhenOptionsUnset() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig conformanceConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public Configuration.Conformance getConformance() {
                return Configuration.Conformance.PDFA2B;
            }
        };

        new DefaultPdfReactorService(conformanceConfig, client).renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getConformance()).isEqualTo(Configuration.Conformance.PDFA2B);
    }

    @Test
    void optionsConformanceOverridesConfig() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig conformanceConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public Configuration.Conformance getConformance() {
                return Configuration.Conformance.PDFA2B;
            }
        };
        PdfRenderOptions options = PdfRenderOptions.builder()
                .conformance(Configuration.Conformance.PDFUA1)
                .build();

        new DefaultPdfReactorService(conformanceConfig, client).renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getConformance()).isEqualTo(Configuration.Conformance.PDFUA1);
    }

    @Test
    void configurationJsonPassThroughAppliesAndLayers() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        // config-level (global+site) JSON sets author + a viewer preference;
        // options-level (view+call) JSON overrides author and adds a title.
        PdfReactorConfig jsonConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public String getConfigurationJson() {
                return "{\"author\":\"Global Author\",\"keywords\":\"a,b\"}";
            }
        };
        PdfRenderOptions options = PdfRenderOptions.builder()
                .configurationJson("{\"author\":\"Call Author\",\"title\":\"Call Title\"}")
                .build();

        new DefaultPdfReactorService(jsonConfig, client).renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();

        assertThat(configuration.getAuthor()).isEqualTo("Call Author"); // call overrides global
        assertThat(configuration.getKeywords()).isEqualTo("a,b"); // global kept
        assertThat(configuration.getTitle()).isEqualTo("Call Title"); // call added
    }

    @Test
    void configurationJsonCannotOverrideOwnedFields() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        // A malicious / mistaken pass-through tries to swap the document,
        // disable the content observer, and set its own error policies.
        PdfRenderOptions options = PdfRenderOptions.builder()
                .failOnMissingResources(true)
                .configurationJson("{\"document\":\"<html>HACKED</html>\","
                        + "\"contentObserver\":{\"missingResources\":false,\"connections\":false},"
                        + "\"errorPolicies\":[]}")
                .build();

        newService().renderHtml("<html>REAL</html>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();

        assertThat(configuration.getDocument()).isEqualTo("<html>REAL</html>");
        assertThat(configuration.getContentObserver().getMissingResources()).isTrue();
        assertThat(configuration.getContentObserver().getConnections()).isTrue();
        assertThat(configuration.getErrorPolicies()).containsExactlyInAnyOrder(
                Configuration.ErrorPolicy.LICENSE,
                Configuration.ErrorPolicy.MISSING_RESOURCE);
    }

    // --- Precedence matrix: a UI/plugin-owned value always wins over the
    //     JSON pass-through; the pass-through may only set what is not owned. ---

    @Test
    void configurationJsonCannotOverrideConformance() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfRenderOptions options = PdfRenderOptions.builder()
                .conformance(Configuration.Conformance.PDFUA1)
                .configurationJson("{\"conformance\":\"PDFA2B\"}")
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getConformance()).isEqualTo(Configuration.Conformance.PDFUA1);
    }

    @Test
    void configurationJsonCannotOverrideJavaScriptDecision() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        // JavaScript is explicitly OFF for this conversion; a pass-through that
        // flips javaScriptSettings.disabled must not silently re-enable it.
        PdfRenderOptions options = PdfRenderOptions.builder()
                .javaScriptEnabled(false)
                .configurationJson("{\"javaScriptSettings\":{\"disabled\":false}}")
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getJavaScriptSettings().getDisabled()).isTrue();
    }

    @Test
    void javaScriptDefaultsToEnabledWhenUnset() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        newService().renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getJavaScriptSettings().getDisabled()).isFalse();
    }

    @Test
    void javaScriptFromConfigAppliesWhenOptionUnset() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig jsConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public Boolean getJavaScriptEnabled() {
                return Boolean.FALSE;
            }
        };

        new DefaultPdfReactorService(jsConfig, client).renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getJavaScriptSettings().getDisabled()).isTrue();
    }

    @Test
    void javaScriptOptionOverridesConfig() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig jsConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public Boolean getJavaScriptEnabled() {
                return Boolean.FALSE;
            }
        };
        PdfRenderOptions options = PdfRenderOptions.builder()
                .javaScriptEnabled(true)
                .build();

        new DefaultPdfReactorService(jsConfig, client).renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getJavaScriptSettings().getDisabled()).isFalse();
    }

    @Test
    void configurationJsonCannotOverrideColorManagement() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfRenderOptions options = PdfRenderOptions.builder()
                .colorConversionEnabled(Boolean.TRUE)
                .configurationJson("{\"colorSpaceSettings\":{\"conversionEnabled\":false}}")
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getColorSpaceSettings().getConversionEnabled()).isTrue();
    }

    @Test
    void configurationJsonCannotOverrideTitleAndAuthor() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfRenderOptions options = PdfRenderOptions.builder()
                .title("UI Title")
                .author("UI Author")
                .configurationJson("{\"title\":\"JSON Title\",\"author\":\"JSON Author\"}")
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("UI Title");
        assertThat(captor.getValue().getAuthor()).isEqualTo("UI Author");
    }

    @Test
    void configurationJsonStyleSheetsAreAppendedAfterConfiguredOnes() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        // The configured sheet stays first; the pass-through sheet is appended
        // after it (so it may add but not replace the configured stylesheets).
        PdfRenderOptions options = PdfRenderOptions.builder()
                .addStyleSheet(PdfStyleSheet.fromUri("https://cfg.example.com/print.css"))
                .configurationJson("{\"userStyleSheets\":[{\"uri\":\"https://json.example.com/extra.css\"}]}")
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        List<Configuration.Resource> styleSheets = captor.getValue().getUserStyleSheets();
        assertThat(styleSheets).hasSize(2);
        assertThat(styleSheets.get(0).getUri()).isEqualTo("https://cfg.example.com/print.css");
        assertThat(styleSheets.get(1).getUri()).isEqualTo("https://json.example.com/extra.css");
    }

    @Test
    void configurationJsonCannotEnableDebugBuilds() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        // Debug/inspectable are gated by the administrator + per-article toggle;
        // a pass-through must not produce a debug build past that gate.
        PdfRenderOptions options = PdfRenderOptions.builder()
                .configurationJson("{\"debugSettings\":{\"all\":true},"
                        + "\"inspectableSettings\":{\"enabled\":true}}")
                .build();

        newService().renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getDebugSettings()).isNull();
        assertThat(captor.getValue().getInspectableSettings()).isNull();
    }

    @Test
    void documentMetadataAndFeaturesFromConfigApply() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig metadataConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public String getCreator() {
                return "Acme CMS";
            }

            @Override
            public String getSubject() {
                return "Quarterly report";
            }

            @Override
            public String getKeywords() {
                return "print, pdf";
            }

            @Override
            public Boolean getAddBookmarks() {
                return Boolean.TRUE;
            }

            @Override
            public Boolean getAddTags() {
                return Boolean.FALSE;
            }
        };

        new DefaultPdfReactorService(metadataConfig, client).renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();
        assertThat(configuration.getCreator()).isEqualTo("Acme CMS");
        assertThat(configuration.getSubject()).isEqualTo("Quarterly report");
        assertThat(configuration.getKeywords()).isEqualTo("print, pdf");
        assertThat(configuration.getAddBookmarks()).isTrue();
        assertThat(configuration.getAddTags()).isFalse();
        assertThat(configuration.getAddLinks()).isNull(); // unset -> client default
    }

    @Test
    void configurationJsonCannotOverrideDocumentMetadata() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig creatorConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public String getCreator() {
                return "Config Creator";
            }
        };
        PdfRenderOptions options = PdfRenderOptions.builder()
                .configurationJson("{\"creator\":\"JSON Creator\"}")
                .build();

        new DefaultPdfReactorService(creatorConfig, client).renderHtml("<html/>", options);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getCreator()).isEqualTo("Config Creator");
    }

    @Test
    void customDocumentPropertiesFromConfigApply() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig propsConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public java.util.Map<String, String> getCustomDocumentProperties() {
                java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
                props.put("Department", "Editorial");
                props.put("DocId", "Q3-2026");
                return props;
            }
        };

        new DefaultPdfReactorService(propsConfig, client).renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        java.util.List<Configuration.KeyValuePair> props =
                captor.getValue().getCustomDocumentProperties();
        assertThat(props).hasSize(2);
        // Applied in sorted-by-key order (matches the fingerprint).
        assertThat(props.get(0).getKey()).isEqualTo("Department");
        assertThat(props.get(0).getValue()).isEqualTo("Editorial");
        assertThat(props.get(1).getKey()).isEqualTo("DocId");
    }

    @Test
    void viewerPreferencesAndValidateConformanceFromConfigApply() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        PdfReactorConfig viewerConfig = new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://localhost:9423/service/rest";
            }

            @Override
            public PdfViewerPageLayout getViewerPageLayout() {
                return PdfViewerPageLayout.TWO_COLUMN_LEFT;
            }

            @Override
            public Boolean getViewerFitWindow() {
                return Boolean.TRUE;
            }

            @Override
            public Boolean getValidateConformance() {
                return Boolean.TRUE;
            }
        };

        new DefaultPdfReactorService(viewerConfig, client).renderHtml("<html/>", null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        Configuration configuration = captor.getValue();
        assertThat(configuration.getValidateConformance()).isTrue();
        assertThat(configuration.getViewerPreferences()).containsExactlyInAnyOrder(
                Configuration.ViewerPreferences.PAGE_LAYOUT_TWO_COLUMN_LEFT,
                Configuration.ViewerPreferences.FIT_WINDOW);
    }

    @Test
    void licensePolicyCanBeRelaxedAndJavaScriptEnabled() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        newService().renderHtml("<html/>", PdfRenderOptions.builder()
                .failOnLicenseProblems(false)
                .javaScriptEnabled(true)
                .build());

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());

        assertThat(captor.getValue().getErrorPolicies()).isEmpty();
        assertThat(captor.getValue().getJavaScriptSettings().getDisabled()).isFalse();
    }

    @Test
    void conversionFailureCarriesDiagnostics() throws Exception {
        Result failed = new Result();
        failed.setError("Missing resource");

        MissingResource missing = new MissingResource();
        missing.setResource("https://cdn.example.com/logo.png");
        missing.setMessage("404");
        failed.setMissingResources(new MissingResource[] { missing });

        Connection connection = new Connection();
        connection.setUrl("https://cdn.example.com/logo.png");
        connection.setStatusCode(404);
        connection.setConnected(true);
        failed.setConnections(new Connection[] { connection });

        when(client.convert(any(Configuration.class)))
                .thenThrow(new PDFreactorWebserviceException("conversion failed", failed));

        assertThatThrownBy(() -> newService().renderHtml("<html/>", null))
                .isInstanceOf(PdfReactorException.class)
                .satisfies(error -> {
                    PdfDiagnostics diagnostics = ((PdfReactorException) error).getDiagnostics();
                    assertThat(diagnostics.getError()).isEqualTo("Missing resource");
                    assertThat(diagnostics.getMissingResources()).hasSize(1);
                    assertThat(diagnostics.getMissingResources().get(0).getResource())
                            .isEqualTo("https://cdn.example.com/logo.png");
                    assertThat(diagnostics.getFailedConnections()).hasSize(1);
                    assertThat(diagnostics.hasProblems()).isTrue();
                });
    }

    @Test
    void emptyResultDocumentFails() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(new Result());

        assertThatThrownBy(() -> newService().renderHtml("<html/>", null))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("no document");
    }

    @Test
    void asyncConversionPollsUntilFinished() throws Exception {
        Progress running = new Progress();
        running.setFinished(false);
        Progress finished = new Progress();
        finished.setFinished(true);

        when(client.convertAsync(any(Configuration.class))).thenReturn("doc-1");
        when(client.getProgress("doc-1")).thenReturn(running, running, finished);
        when(client.getDocument("doc-1")).thenReturn(resultWithDocument());

        PdfResult result = newService().renderHtml("<html/>", PdfRenderOptions.builder()
                .async(Boolean.TRUE)
                .build());

        assertThat(result.getDocument()).isEqualTo(PDF_BYTES);
        verify(client, never()).convert(any(Configuration.class));
    }

    @Test
    void healthCheckUpReportsVersion() throws Exception {
        Version version = new Version();
        version.setText("12.6.0");
        when(client.getVersion()).thenReturn(version);

        PdfServiceHealth health = newService().checkHealth();

        assertThat(health.isUp()).isTrue();
        assertThat(health.getVersion()).isEqualTo("12.6.0");
    }

    @Test
    void healthCheckDownOnServiceError() throws Exception {
        org.mockito.Mockito.doThrow(new PDFreactorWebserviceException("unreachable"))
                .when(client).getStatus();

        PdfServiceHealth health = newService().checkHealth();

        assertThat(health.isUp()).isFalse();
        assertThat(health.getError()).contains("unreachable");
    }

    @Test
    void licenseProbeReportsLicensedOnSuccess() throws Exception {
        when(client.convert(any(Configuration.class))).thenReturn(resultWithDocument());

        assertThat(newService().checkLicense()).isEqualTo(PdfLicenseState.LICENSED);

        // The probe runs only the LICENSE error policy on a trivial document.
        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getErrorPolicies())
                .containsExactly(Configuration.ErrorPolicy.LICENSE);
        assertThat(captor.getValue().getJavaScriptSettings().getDisabled()).isTrue();
    }

    @Test
    void licenseProbeReportsEvaluationOnLicenseAbort() throws Exception {
        Result aborted = new Result();
        aborted.setError("License: No valid license key found, running in evaluation mode.");
        when(client.convert(any(Configuration.class)))
                .thenThrow(new PDFreactorWebserviceException("License error", aborted));

        assertThat(newService().checkLicense()).isEqualTo(PdfLicenseState.EVALUATION);
    }

    @Test
    void licenseProbeReportsUnknownOnNonLicenseFailure() throws Exception {
        when(client.convert(any(Configuration.class)))
                .thenThrow(new PDFreactorWebserviceException("unreachable"));

        assertThat(newService().checkLicense()).isEqualTo(PdfLicenseState.UNKNOWN);
    }

    @Test
    void missingServiceUrlFailsClosed() {
        PdfReactorConfig unconfigured = () -> null;

        assertThatThrownBy(() -> new DefaultPdfReactorService(unconfigured))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("pdfreactor/serviceUrl");
    }

    @Test
    void diagnosticsFromNullResultIsEmpty() {
        PdfDiagnostics diagnostics = PdfDiagnostics.fromResult(null);

        assertThat(diagnostics.hasProblems()).isFalse();
        assertThat(diagnostics.getMissingResources()).isEmpty();
        assertThat(diagnostics.getConnections()).isEmpty();
        assertThat(diagnostics.getError()).isNull();
    }

    @Test
    void unreachableConnectionCountsAsFailure() {
        Connection unreachable = new Connection();
        unreachable.setUrl("https://cdn.example.com/font.woff2");
        unreachable.setConnected(false);

        Result result = resultWithDocument();
        result.setConnections(new Connection[] { unreachable });

        PdfDiagnostics diagnostics = PdfDiagnostics.fromResult(result);
        assertThat(diagnostics.getFailedConnections()).hasSize(1);
        assertThat(diagnostics.getFailedConnections().get(0).toString()).contains("unreachable");
    }

    @Test
    void marketingVersionDropsTheBuildNumber() {
        Version version = new Version();
        version.setMajor(12);
        version.setMinor(6);
        version.setMicro(0);
        version.setBuild(18136);
        version.setText("12.6.0.18136");

        assertThat(DefaultPdfReactorService.marketingVersion(version)).isEqualTo("12.6.0");
    }

    @Test
    void marketingVersionFallsBackToTextWhenComponentsUnset() {
        Version version = new Version();
        version.setText("12.6.0");

        assertThat(DefaultPdfReactorService.marketingVersion(version)).isEqualTo("12.6.0");
        assertThat(DefaultPdfReactorService.marketingVersion(null)).isNull();
    }
}
