package com.realobjects.brightspot.pdfreactor.tool;

import com.psddev.cms.db.Content;
import com.psddev.test.db.TestDatabaseExtension;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The widget surfaces a publish-automation failure on the edit form. The
 * detail is read from the {@link HasPdfRenderingData} status field that the
 * publish task writes; a later success ("Success …") clears it.
 */
@ExtendWith(TestDatabaseExtension.class)
class GeneratePdfWidgetTest {

    /** Dev-harness-free printable type for the test. */
    public static class TestPrintable extends Content implements HasPdfRendering {

        @Override
        public String getLabel() {
            return "test";
        }
    }

    private static TestPrintable withStatus(String status) {
        TestPrintable content = new TestPrintable();
        content.as(HasPdfRenderingData.class).setGeneratedPdfStatus(status);
        return content;
    }

    @Test
    void surfacesTheLastPublishFailureDetail() {
        assertThat(GeneratePdfWidget.publishFailureDetail(withStatus("Failed: boom")))
                .isEqualTo("boom");
    }

    @Test
    void noBannerWhenTheLastPublishSucceeded() {
        assertThat(GeneratePdfWidget.publishFailureDetail(withStatus("Success (2 pages)")))
                .isNull();
        assertThat(GeneratePdfWidget.publishFailureDetail(withStatus(null))).isNull();
    }

    @Test
    void noBannerForContentWithoutPublishRendering() {
        assertThat(GeneratePdfWidget.publishFailureDetail(new Object())).isNull();
    }
}
