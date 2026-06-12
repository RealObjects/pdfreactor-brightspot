package com.realobjects.brightspot.pdfreactor.tool;

import java.util.Date;
import java.util.UUID;

import com.psddev.cms.db.Content;
import com.psddev.dari.db.Predicate;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.State;
import com.psddev.dari.util.StorageItem;
import com.psddev.test.db.TestDatabaseExtension;
import com.realobjects.brightspot.pdfreactor.GeneratedPdf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Download-by-id resolution for {@link PdfGeneratePage}: the widget's
 * Download link carries the displayed record's id so the page streams that
 * exact stored PDF — no current-cache-key lookup, no conversion, no write.
 * {@link PdfGeneratePage#findDownloadable} is the resolver that branch uses;
 * because it never touches the conversion service, a download cannot
 * regenerate after a content change.
 */
@ExtendWith(TestDatabaseExtension.class)
class PdfGeneratePageTest {

    private static GeneratedPdf store(UUID contentId, String cacheKey) {
        GeneratedPdf generatedPdf = new GeneratedPdf();
        generatedPdf.setCacheKey(cacheKey);
        generatedPdf.setContentId(contentId);
        generatedPdf.setGenerated(new Date());
        generatedPdf.setNumberOfPages(2);
        generatedPdf.setPdf(StorageItem.Static.createUrl(
                "https://storage.example.com/pdfreactor/" + cacheKey + ".pdf"));
        generatedPdf.save();
        return generatedPdf;
    }

    @Test
    void resolvesRecordForMatchingContent() {
        UUID contentId = UUID.randomUUID();
        GeneratedPdf stored = store(contentId, contentId + ":1:a");

        GeneratedPdf found = PdfGeneratePage.findDownloadable(stored.getId(), contentId);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(stored.getId());
        assertThat(found.getPdf()).isNotNull();
    }

    @Test
    void rejectsRecordBelongingToOtherContent() {
        UUID contentId = UUID.randomUUID();
        GeneratedPdf stored = store(contentId, contentId + ":1:a");

        // A record id that exists but belongs to a different content must not
        // stream (prevents cross-content downloads via a crafted id).
        assertThat(PdfGeneratePage.findDownloadable(stored.getId(), UUID.randomUUID()))
                .isNull();
    }

    @Test
    void returnsNullForMissingOrNullId() {
        assertThat(PdfGeneratePage.findDownloadable(UUID.randomUUID(), UUID.randomUUID()))
                .isNull();
        assertThat(PdfGeneratePage.findDownloadable(null, UUID.randomUUID())).isNull();
    }

    // --- Per-content authorization (site scope + type read permission) ---

    public static class TestContent extends Content {
    }

    private static TestContent newContent() {
        TestContent content = new TestContent();
        content.save();
        return content;
    }

    @Test
    void authorizeContentReturnsContentWhenSiteAndTypePermit() {
        TestContent content = newContent();
        Object found = PdfGeneratePage.authorizeContent(content.getId(), null, type -> true);
        assertThat(found).isNotNull();
        assertThat(State.getInstance(found).getId()).isEqualTo(content.getId());
    }

    @Test
    void authorizeContentDeniedByTypePermission() {
        TestContent content = newContent();
        // The user lacks "type/<id>/read" → not authorized, even though it exists.
        assertThat(PdfGeneratePage.authorizeContent(content.getId(), null, type -> false)).isNull();
    }

    @Test
    void authorizeContentDeniedBySitePredicate() {
        TestContent content = newContent();
        // A site-items predicate the content does not match (foreign-site user):
        // a predicate that only admits some other id, ANDed with this content's id.
        Predicate foreignSite = Query.fromAll().where("_id = ?", UUID.randomUUID()).getPredicate();
        assertThat(PdfGeneratePage.authorizeContent(content.getId(), foreignSite, type -> true)).isNull();
    }

    @Test
    void authorizeContentNullForMissingOrNullId() {
        assertThat(PdfGeneratePage.authorizeContent(null, null, type -> true)).isNull();
        assertThat(PdfGeneratePage.authorizeContent(UUID.randomUUID(), null, type -> true)).isNull();
    }
}
