package com.realobjects.brightspot.pdfreactor;

import java.io.File;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * End-to-end tests of {@link DefaultPdfReactorService} against a real
 * PDFreactor 12.6.0 container.
 *
 * <p>If the dev license ({@code licensekey.xml} in the repo root, passed via
 * the {@code pdfreactor.licensekey.path} system property) is present it is
 * mounted into the container; otherwise the service runs in evaluation mode
 * (watermarked output) and conversions relax the LICENSE error policy.</p>
 */
@Testcontainers
class PdfReactorServiceE2eTest {

    private static final int SERVICE_PORT = 9423;
    private static final boolean LICENSED = licenseFile() != null;

    @Container
    private static final GenericContainer<?> PDFREACTOR = createContainer();

    private static PdfReactorService service;

    private static File licenseFile() {
        String path = System.getProperty("pdfreactor.licensekey.path");
        if (path == null) {
            return null;
        }
        File file = new File(path);
        return file.isFile() ? file : null;
    }

    private static GenericContainer<?> createContainer() {
        GenericContainer<?> container = new GenericContainer<>("realobjects/pdfreactor:12.6.0")
                .withExposedPorts(SERVICE_PORT)
                .withEnv("JAVA_OPTIONS", "-Xmx1g")
                .waitingFor(Wait.forHttp("/service/rest/status").forPort(SERVICE_PORT));
        File license = licenseFile();
        if (license != null) {
            // A bind mount (as in docker-compose), not a copy: /ro/config
            // does not exist before the entrypoint runs, so copied files do
            // not survive to license loading.
            container.withFileSystemBind(
                    license.getAbsolutePath(),
                    "/ro/config/licensekey.txt",
                    BindMode.READ_ONLY);
        }
        return container;
    }

    @BeforeAll
    static void createService() {
        PdfReactorConfig config = new PdfReactorConfig() {

            @Override
            public String getServiceUrl() {
                return "http://" + PDFREACTOR.getHost()
                        + ":" + PDFREACTOR.getMappedPort(SERVICE_PORT)
                        + "/service/rest";
            }

            @Override
            public long getAsyncPollIntervalMillis() {
                return 250L;
            }
        };
        service = new DefaultPdfReactorService(config);
    }

    /** Base options: relax the LICENSE policy when running in eval mode. */
    private static PdfRenderOptions.Builder options() {
        return PdfRenderOptions.builder().failOnLicenseProblems(LICENSED);
    }

    private static void assertIsPdf(PdfResult result) {
        assertThat(result.getDocument()).isNotEmpty();
        assertThat(new String(result.getDocument(), 0, 5)).isEqualTo("%PDF-");
        assertThat(result.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void healthCheckReportsUpWithVersion() {
        PdfServiceHealth health = service.checkHealth();

        assertThat(health.isUp()).isTrue();
        assertThat(health.getVersion()).startsWith("12.6");
    }

    @Test
    void licenseProbeClassifiesServiceLicenseState() {
        // The LICENSE-policy probe detects evaluation mode by attempting a
        // trivial conversion: it aborts unlicensed, succeeds licensed. The
        // container runs in eval mode unless the dev license is mounted, so
        // the expected state tracks the LICENSED flag.
        PdfLicenseState state = service.checkLicense();

        assertThat(state).isEqualTo(LICENSED
                ? PdfLicenseState.LICENSED
                : PdfLicenseState.EVALUATION);
    }

    @Test
    void syncConversionProducesPdf() {
        PdfResult result = service.renderHtml(
                "<html><body><h1>Hello PDFreactor</h1><p>Sync conversion.</p></body></html>",
                options().build());

        assertIsPdf(result);
        assertThat(result.getNumberOfPages()).isEqualTo(1);
        assertThat(result.getDiagnostics().getMissingResources()).isEmpty();
    }

    @Test
    void asyncConversionProducesPdf() {
        PdfResult result = service.renderHtml(
                "<html><body><h1>Hello PDFreactor</h1><p>Async conversion.</p></body></html>",
                options().async(Boolean.TRUE).build());

        assertIsPdf(result);
        assertThat(result.getNumberOfPages()).isEqualTo(1);
    }

    @Test
    void pageGeometryFromOptionsControlsPagination() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            body.append("<p>Paragraph ").append(i).append("</p>");
        }
        String html = "<html><body>" + body + "</body></html>";

        PdfResult onLargePages = service.renderHtml(html, options()
                .paperSize("A4")
                .build());
        PdfResult onTinyPages = service.renderHtml(html, options()
                .paperSize("A7 landscape")
                .margin("5mm")
                .build());

        assertIsPdf(onLargePages);
        assertIsPdf(onTinyPages);
        assertThat(onTinyPages.getNumberOfPages())
                .isGreaterThan(onLargePages.getNumberOfPages());
    }

    @Test
    void inlineUserStyleSheetIsApplied() {
        String html = "<html><body><p>break me</p><p class='second'>second</p></body></html>";

        PdfResult unstyled = service.renderHtml(html, options().build());
        PdfResult styled = service.renderHtml(html, options()
                .addStyleSheet(PdfStyleSheet.inline(".second { page-break-before: always; }"))
                .build());

        assertThat(unstyled.getNumberOfPages()).isEqualTo(1);
        assertThat(styled.getNumberOfPages()).isEqualTo(2);
    }

    @Test
    void missingResourceIsReportedInDiagnosticsWithoutFailing() {
        // The container cannot resolve this host, so the image is missing
        // but the conversion succeeds (no MISSING_RESOURCE error policy).
        String html = "<html><body><img src='http://missing.invalid/logo.png'>text</body></html>";

        PdfResult result = service.renderHtml(html, options().build());

        assertIsPdf(result);
        assertThat(result.getDiagnostics().getMissingResources())
                .anySatisfy(missing -> assertThat(missing.getResource())
                        .contains("missing.invalid"));
        assertThat(result.getDiagnostics().hasProblems()).isTrue();
    }

    @Test
    void missingResourceFailsClosedWhenRequested() {
        String html = "<html><body><img src='http://missing.invalid/logo.png'>text</body></html>";

        // On an error-policy abort the service reports the detail in the
        // result's error field, not in the missingResources array.
        assertThatThrownBy(() -> service.renderHtml(html, options()
                .failOnMissingResources(true)
                .build()))
                .isInstanceOf(PdfReactorException.class)
                .satisfies(error -> assertThat(((PdfReactorException) error)
                        .getDiagnostics().getError()).contains("missing.invalid"));
    }

    @Test
    void conformancePdfA3aProducesPdf() {
        PdfResult result = service.renderHtml(
                "<html><body><h1 lang='en'>Conformance</h1></body></html>",
                options()
                        .conformance(com.realobjects.pdfreactor.webservice.client.Configuration.Conformance.PDFA3A)
                        .build());

        assertIsPdf(result);
    }

    @Test
    void outputIntentProfileIsEmbedded() {
        // The JDK's built-in sRGB profile, embedded as the output intent.
        byte[] srgb = java.awt.color.ICC_Profile
                .getInstance(java.awt.color.ColorSpace.CS_sRGB)
                .getData();
        assertThat(srgb).isNotEmpty();

        String html = "<html><body><p>color management</p></body></html>";

        PdfResult plain = service.renderHtml(html, options().build());
        PdfResult withIntent = service.renderHtml(html, options()
                .outputIntentIdentifier("sRGB IEC61966-2.1")
                .outputIntentProfileData(srgb)
                .build());

        assertIsPdf(withIntent);
        // The embedded ICC profile stream makes the document materially
        // larger than the same conversion without an output intent.
        assertThat(withIntent.getDocument().length)
                .isGreaterThan(plain.getDocument().length + 1000);
    }

    @Test
    void unreachableHostHealthIsDownWithinHealthTimeout() {
        // A non-routable address (RFC 5737 TEST-NET-1) black-holes the TCP
        // connect rather than refusing it, so this checks whether the short
        // health timeout bounds a *hanging* connect (the Step 2 open question),
        // not just a fast connection-refused. It also pins that checkHealth now
        // uses getHealthTimeoutMillis(), not getClientTimeoutMillis().
        PdfReactorConfig badConfig = new PdfReactorConfig() {

            @Override
            public String getServiceUrl() {
                return "http://192.0.2.1:9423/service/rest";
            }

            @Override
            public int getHealthTimeoutMillis() {
                return 1_500;
            }
        };

        long start = System.currentTimeMillis();
        PdfServiceHealth health = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> new DefaultPdfReactorService(badConfig).checkHealth());
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[step2] checkHealth against a black-holed host returned in " + elapsed + "ms");

        assertThat(health.isUp()).isFalse();
        assertThat(health.getError()).isNotBlank();
        // The short health timeout must bound the hang well under the OS
        // default connect timeout (~75-130s).
        assertThat(elapsed).isLessThan(15_000L);
    }
}
