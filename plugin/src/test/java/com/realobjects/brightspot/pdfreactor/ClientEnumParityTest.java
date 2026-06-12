package com.realobjects.brightspot.pdfreactor;

import com.realobjects.pdfreactor.webservice.client.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression guard for the by-name enum mapping in {@link SitePdfReactorConfig}
 * ({@code Configuration.Conformance.valueOf(siteEnum.name())}). The plugin
 * mirrors the client's {@code Conformance} constants by name; that mapping is
 * exact for the pinned client (12.6.0). If a future client bump renames or
 * drops a constant, this fails the build rather than letting a per-site
 * override blow up at runtime. (The color-conversion intent has no UI mirror enum —
 * it is deploy-time/JSON-only — so it needs no parity guard.)
 */
class ClientEnumParityTest {

    @Test
    void everyPluginConformanceMapsToAClientConstant() {
        for (PdfReactorSiteSettings.Conformance value : PdfReactorSiteSettings.Conformance.values()) {
            assertThatCode(() -> Configuration.Conformance.valueOf(value.name()))
                    .as("plugin Conformance.%s must map to a client Configuration.Conformance constant",
                            value.name())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void everyViewerPageLayoutMapsToAClientConstant() {
        for (PdfViewerPageLayout value : PdfViewerPageLayout.values()) {
            assertThatCode(value::toClient)
                    .as("PdfViewerPageLayout.%s must map to a client PAGE_LAYOUT_ constant", value.name())
                    .doesNotThrowAnyException();
        }
    }
}
