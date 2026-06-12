package com.realobjects.brightspot.pdfreactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PdfReactorConfigs#resolved} must read each byte-valued source (the
 * ICC profiles) exactly once and reuse it, so a conversion does not read the
 * bytes again for the fingerprint, the supersede guard, and the conversion
 * itself. Non-byte values pass through to the delegate live.
 */
class ResolvedPdfReactorConfigTest {

    /** Counts how often each byte source is read from the delegate. */
    private static final class CountingConfig implements PdfReactorConfig {

        int outputIntentReads;
        int cmykReads;

        @Override
        public String getServiceUrl() {
            return "http://pdfreactor:9423/service/rest";
        }

        @Override
        public byte[] getOutputIntentProfileData() {
            outputIntentReads++;
            return new byte[] {1, 2, 3};
        }

        @Override
        public byte[] getCmykIccProfileData() {
            cmykReads++;
            return new byte[] {4, 5};
        }
    }

    @Test
    void readsEachByteSourceOnceAcrossRepeatedReads() {
        CountingConfig delegate = new CountingConfig();
        PdfReactorConfig resolved = PdfReactorConfigs.resolved(delegate);

        // Resolved eagerly in the constructor, then served from the snapshot.
        for (int i = 0; i < 3; i++) {
            resolved.getOutputIntentProfileData();
            resolved.getCmykIccProfileData();
        }

        assertThat(delegate.outputIntentReads).isEqualTo(1);
        assertThat(delegate.cmykReads).isEqualTo(1);
        assertThat(resolved.getOutputIntentProfileData()).isEqualTo(new byte[] {1, 2, 3});
        assertThat(resolved.getCmykIccProfileData()).isEqualTo(new byte[] {4, 5});
        assertThat(resolved.getServiceUrl()).isEqualTo("http://pdfreactor:9423/service/rest");
    }

    @Test
    void publishPathPatternReadsEachSourceOnce() {
        CountingConfig delegate = new CountingConfig();
        PdfReactorConfig resolved = PdfReactorConfigs.resolved(delegate);

        // The publish path's three would-be reads, all against one snapshot:
        PdfConfigFingerprint.of(resolved); // handle: cache key
        PdfConfigFingerprint.of(resolved); // generate: supersede guard
        resolved.getOutputIntentProfileData(); // conversion: applyColorManagement
        resolved.getCmykIccProfileData();

        assertThat(delegate.outputIntentReads).isEqualTo(1);
        assertThat(delegate.cmykReads).isEqualTo(1);
    }
}
