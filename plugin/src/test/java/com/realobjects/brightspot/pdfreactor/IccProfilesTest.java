package com.realobjects.brightspot.pdfreactor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IccProfilesTest {

    @Test
    void nullOrBlankReferenceYieldsNull() {
        assertThat(IccProfiles.read(null)).isNull();
        assertThat(IccProfiles.read("   ")).isNull();
    }

    @Test
    void readsClasspathResource() {
        // src/test/resources/icc/test-profile.bin (see resources dir).
        byte[] data = IccProfiles.read(IccProfiles.CLASSPATH_PREFIX + "icc/test-profile.bin");
        assertThat(data).isNotEmpty();
        assertThat(new String(data)).startsWith("ICC-TEST");
    }

    @Test
    void missingClasspathResourceThrows() {
        assertThatThrownBy(() -> IccProfiles.read(IccProfiles.CLASSPATH_PREFIX + "icc/does-not-exist.icc"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("does-not-exist.icc");
    }

    @Test
    void readsFileUrl() throws IOException {
        Path file = Files.createTempFile("icc", ".bin");
        Files.write(file, new byte[] {1, 2, 3, 4});
        try {
            byte[] data = IccProfiles.read(file.toUri().toString());
            assertThat(data).containsExactly(1, 2, 3, 4);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void unreadableUrlThrows() {
        assertThatThrownBy(() -> IccProfiles.read("file:/no/such/path/profile.icc"))
                .isInstanceOf(PdfReactorException.class);
    }

    @Test
    void readCappedReturnsSmallStream() throws IOException {
        byte[] data = IccProfiles.readCapped(new ByteArrayInputStream(new byte[] {9, 8, 7}), "test");
        assertThat(data).containsExactly(9, 8, 7);
    }

    @Test
    void readCappedAbortsOversizedStream() {
        // A stream one byte past the cap must abort rather than buffer it all.
        ByteArrayInputStream oversized =
                new ByteArrayInputStream(new byte[IccProfiles.MAX_PROFILE_BYTES + 1]);
        assertThatThrownBy(() -> IccProfiles.readCapped(oversized, "huge.icc"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("exceeds");
    }
}
