package com.realobjects.brightspot.pdfreactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IccProfile}, the reusable ICC-profile record.
 */
class IccProfileTest {

    @Test
    void readBytesNullWhenNoFile() {
        // No file set → no bytes (StorageItemBytes.read(null)); the layering in
        // SitePdfReactorConfig then falls back to the global profile.
        assertThat(new IccProfile().readBytes()).isNull();
    }

    @Test
    void nameAndDescriptionRoundTrip() {
        IccProfile profile = new IccProfile();
        profile.setName("ISO Coated v2 (ECI)");
        profile.setDescription("CMYK, FOGRA39");
        assertThat(profile.getName()).isEqualTo("ISO Coated v2 (ECI)");
        assertThat(profile.getDescription()).isEqualTo("CMYK, FOGRA39");
    }

    @Test
    void acceptsBytesWithIccSignature() {
        // ICC profile header: the "acsp" signature lives at byte offset 36.
        byte[] header = new byte[128];
        header[36] = 'a';
        header[37] = 'c';
        header[38] = 's';
        header[39] = 'p';
        assertThat(IccProfile.hasIccSignature(header)).isTrue();
    }

    @Test
    void rejectsNonIccFiles() {
        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
        assertThat(IccProfile.hasIccSignature(png)).isFalse();

        byte[] zeroedHeader = new byte[128]; // right size, no signature
        assertThat(IccProfile.hasIccSignature(zeroedHeader)).isFalse();
    }

    @Test
    void unreadableHeaderDoesNotBlockSaving() {
        // null header = bytes could not be read → validation must not block.
        assertThat(IccProfile.hasIccSignature(null)).isTrue();
    }
}
