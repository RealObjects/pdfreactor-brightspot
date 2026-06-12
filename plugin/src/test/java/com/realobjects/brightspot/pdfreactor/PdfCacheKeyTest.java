package com.realobjects.brightspot.pdfreactor;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfCacheKeyTest {

    @Test
    void storagePathIsStableForTheSameCacheKey() {
        UUID contentId = UUID.randomUUID();
        String cacheKey = contentId + ":1700000000000:optsHash:cfgFingerprint";

        assertThat(PdfCacheKey.storagePath(contentId, cacheKey))
                .isEqualTo(PdfCacheKey.storagePath(contentId, cacheKey))
                .startsWith("pdfreactor/" + contentId + "/")
                .endsWith(".pdf");
    }

    @Test
    void storagePathDiffersForDistinctCacheEntriesOfTheSameContent() {
        UUID contentId = UUID.randomUUID();
        // Same content + same config fingerprint, different revision: the key
        // must not collide on the stored object (the previous scheme hashed
        // only the trailing config-fingerprint segment, so it did).
        String revisionOne = contentId + ":1700000000000:optsHash:sameFingerprint";
        String revisionTwo = contentId + ":1700000999999:optsHash:sameFingerprint";

        assertThat(PdfCacheKey.storagePath(contentId, revisionOne))
                .isNotEqualTo(PdfCacheKey.storagePath(contentId, revisionTwo));

        // Same content + same revision, different options: also distinct.
        String optionsOne = contentId + ":1700000000000:optsA:sameFingerprint";
        String optionsTwo = contentId + ":1700000000000:optsB:sameFingerprint";
        assertThat(PdfCacheKey.storagePath(contentId, optionsOne))
                .isNotEqualTo(PdfCacheKey.storagePath(contentId, optionsTwo));
    }

    @Test
    void hashIsDeterministic() {
        PdfRenderOptions options = PdfRenderOptions.builder()
                .paperSize("A4")
                .margin("20mm")
                .addStyleSheet(PdfStyleSheet.fromUri("https://cdn.example.com/print.css"))
                .build();

        assertThat(PdfCacheKey.optionsHash(options))
                .isEqualTo(PdfCacheKey.optionsHash(options))
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    @Test
    void outputAffectingOptionsChangeTheHash() {
        PdfRenderOptions base = PdfRenderOptions.builder().paperSize("A4").build();

        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder().paperSize("letter").build()));
        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4")
                        .addStyleSheet(PdfStyleSheet.inline("p { color: red; }"))
                        .build()));
        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4")
                        .conformance(com.realobjects.pdfreactor.webservice.client.Configuration.Conformance.PDFA3A)
                        .build()));
        // JavaScript tri-state: unset, on, and off must all hash distinctly.
        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4").javaScriptEnabled(true).build()));
        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4").javaScriptEnabled(false).build()));
        assertThat(PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4").javaScriptEnabled(true).build()))
                .isNotEqualTo(PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4").javaScriptEnabled(false).build()));
    }

    @Test
    void iccOptionsChangeTheHash() {
        PdfRenderOptions base = PdfRenderOptions.builder().paperSize("A4").build();

        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4")
                        .outputIntentIdentifier("ISO Coated v2")
                        .build()));
        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4")
                        .outputIntentProfileData(new byte[] {1, 2, 3})
                        .build()));
        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4")
                        .colorConversionIntent(
                                com.realobjects.pdfreactor.webservice.client.Configuration
                                        .ColorConversionIntent.PERCEPTIVE)
                        .build()));
    }

    @Test
    void configurationJsonChangesTheHash() {
        PdfRenderOptions base = PdfRenderOptions.builder().paperSize("A4").build();
        assertThat(PdfCacheKey.optionsHash(base)).isNotEqualTo(
                PdfCacheKey.optionsHash(PdfRenderOptions.builder()
                        .paperSize("A4")
                        .configurationJson("{\"author\":\"X\"}")
                        .build()));
    }

    @Test
    void outputNeutralOptionsDoNotChangeTheHash() {
        PdfRenderOptions base = PdfRenderOptions.builder().paperSize("A4").build();
        PdfRenderOptions tweaked = PdfRenderOptions.builder()
                .paperSize("A4")
                .failOnMissingResources(true)
                .failOnLicenseProblems(false)
                .async(Boolean.TRUE)
                .conversionTimeoutSeconds(900)
                .build();

        assertThat(PdfCacheKey.optionsHash(base))
                .isEqualTo(PdfCacheKey.optionsHash(tweaked));
    }
}
