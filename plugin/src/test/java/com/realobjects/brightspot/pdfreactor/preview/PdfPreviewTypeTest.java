package com.realobjects.brightspot.pdfreactor.preview;

import com.psddev.test.db.TestDatabaseExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

// getDisplayName() resolves a localized label (ToolLocalization), which reads
// the Tool's localization store, so the test needs a Dari database.
@ExtendWith(TestDatabaseExtension.class)
class PdfPreviewTypeTest {

    @Test
    void displayNameDefaultsToPdf() {
        assertThat(new PdfPreviewType().getDisplayName()).isEqualTo("PDF");
    }

    @Test
    void displayNameIsOverridable() {
        PdfPreviewType type = new PdfPreviewType();
        type.setName("Print PDF");
        assertThat(type.getDisplayName()).isEqualTo("Print PDF");
    }

    @Test
    void doesNotRefreshAsContentChanges() {
        assertThat(new PdfPreviewType().refreshAsContentChanges()).isFalse();
    }
}
