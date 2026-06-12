package com.realobjects.brightspot.pdfreactor;

import com.psddev.cms.db.Content;
import com.psddev.test.db.TestDatabaseExtension;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-article override layer ({@link PerArticlePdfReactorConfig}): a set
 * per-article value wins over the site/global config; an unset one inherits;
 * non-overrideable values delegate straight through.
 */
@ExtendWith(TestDatabaseExtension.class)
class PerArticlePdfReactorConfigTest {

    public static class TestPrintable extends Content implements HasPdfRendering {
    }

    private static PdfReactorConfig siteStub() {
        return new PdfReactorConfig() {
            @Override
            public String getServiceUrl() {
                return "http://svc";
            }

            @Override
            public String getCreator() {
                return "Site Creator";
            }

            @Override
            public String getSubject() {
                return "Site Subject";
            }

            @Override
            public Boolean getAddBookmarks() {
                return Boolean.FALSE;
            }

            @Override
            public PdfViewerPageLayout getViewerPageLayout() {
                return PdfViewerPageLayout.SINGLE_PAGE;
            }
        };
    }

    @Test
    void perArticleValueWinsWhenSet() {
        TestPrintable content = new TestPrintable();
        HasPdfRenderingData data = content.as(HasPdfRenderingData.class);
        data.setCreator("Article Creator");
        data.setAddBookmarks(Boolean.TRUE);
        data.setViewerPageLayout(PdfViewerPageLayout.TWO_COLUMN_LEFT);

        PerArticlePdfReactorConfig config = new PerArticlePdfReactorConfig(content, siteStub());

        assertThat(config.getCreator()).isEqualTo("Article Creator");
        assertThat(config.getAddBookmarks()).isTrue();
        assertThat(config.getViewerPageLayout()).isEqualTo(PdfViewerPageLayout.TWO_COLUMN_LEFT);
    }

    @Test
    void unsetPerArticleInheritsSiteAndDelegatesTheRest() {
        TestPrintable content = new TestPrintable(); // no per-article overrides
        PerArticlePdfReactorConfig config = new PerArticlePdfReactorConfig(content, siteStub());

        assertThat(config.getCreator()).isEqualTo("Site Creator");
        assertThat(config.getSubject()).isEqualTo("Site Subject");
        assertThat(config.getAddBookmarks()).isFalse();
        assertThat(config.getViewerPageLayout()).isEqualTo(PdfViewerPageLayout.SINGLE_PAGE);
        assertThat(config.getServiceUrl()).isEqualTo("http://svc"); // delegated, not overrideable
    }
}
