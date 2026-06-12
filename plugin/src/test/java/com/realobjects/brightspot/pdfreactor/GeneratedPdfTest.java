package com.realobjects.brightspot.pdfreactor;

import java.util.Date;
import java.util.UUID;

import com.psddev.dari.db.Query;
import com.psddev.dari.util.Settings;
import com.psddev.dari.util.StorageItem;
import com.psddev.test.db.TestDatabaseExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Record/query behavior of {@link GeneratedPdf} against an in-memory H2
 * Dari database (via {@code com.psddev:test-db}).
 */
@ExtendWith(TestDatabaseExtension.class)
class GeneratedPdfTest {

    private static GeneratedPdf newGeneratedPdf(UUID contentId, String cacheKey, Date generated) {
        GeneratedPdf generatedPdf = new GeneratedPdf();
        generatedPdf.setCacheKey(cacheKey);
        generatedPdf.setContentId(contentId);
        generatedPdf.setGenerated(generated);
        generatedPdf.setNumberOfPages(3);
        generatedPdf.setPdf(StorageItem.Static.createUrl(
                "https://storage.example.com/pdfreactor/" + cacheKey + ".pdf"));
        generatedPdf.save();
        return generatedPdf;
    }

    @Test
    void findByCacheKeyReturnsSavedRecord() {
        UUID contentId = UUID.randomUUID();
        String cacheKey = contentId + ":1:abc";
        newGeneratedPdf(contentId, cacheKey, new Date());

        GeneratedPdf found = GeneratedPdf.findByCacheKey(cacheKey);

        assertThat(found).isNotNull();
        assertThat(found.getContentId()).isEqualTo(contentId);
        assertThat(found.getNumberOfPages()).isEqualTo(3);
        assertThat(found.getPdf()).isNotNull();
        assertThat(GeneratedPdf.findByCacheKey("missing")).isNull();
    }

    @Test
    void findLatestForContentSortsByGeneratedDate() {
        UUID contentId = UUID.randomUUID();
        newGeneratedPdf(contentId, contentId + ":1:a", new Date(1_000L));
        GeneratedPdf newest = newGeneratedPdf(contentId, contentId + ":2:b", new Date(2_000L));

        GeneratedPdf latest = GeneratedPdf.findLatestForContent(contentId);

        assertThat(latest).isNotNull();
        assertThat(latest.getId()).isEqualTo(newest.getId());
        assertThat(GeneratedPdf.findLatestForContent(UUID.randomUUID())).isNull();
    }

    @AfterEach
    void clearRetentionOverride() {
        Settings.setOverride(GeneratedPdf.RETENTION_SETTING, null);
    }

    @Test
    void pruneKeepsOnlyTheMostRecentPerContent() {
        Settings.setOverride(GeneratedPdf.RETENTION_SETTING, 2);
        UUID contentId = UUID.randomUUID();
        newGeneratedPdf(contentId, contentId + ":1:a", new Date(1_000L));
        newGeneratedPdf(contentId, contentId + ":2:b", new Date(2_000L));
        newGeneratedPdf(contentId, contentId + ":3:c", new Date(3_000L));
        newGeneratedPdf(contentId, contentId + ":4:d", new Date(4_000L));

        int deleted = GeneratedPdf.pruneForContent(contentId);

        assertThat(deleted).isEqualTo(2);
        assertThat(Query.from(GeneratedPdf.class).where("contentId = ?", contentId).count())
                .isEqualTo(2L);
        // The two newest survive; the two oldest are gone.
        assertThat(GeneratedPdf.findByCacheKey(contentId + ":4:d")).isNotNull();
        assertThat(GeneratedPdf.findByCacheKey(contentId + ":3:c")).isNotNull();
        assertThat(GeneratedPdf.findByCacheKey(contentId + ":2:b")).isNull();
        assertThat(GeneratedPdf.findByCacheKey(contentId + ":1:a")).isNull();
    }

    @Test
    void pruneDisabledKeepsEverything() {
        Settings.setOverride(GeneratedPdf.RETENTION_SETTING, 0);
        UUID contentId = UUID.randomUUID();
        newGeneratedPdf(contentId, contentId + ":1:a", new Date(1_000L));
        newGeneratedPdf(contentId, contentId + ":2:b", new Date(2_000L));

        assertThat(GeneratedPdf.pruneForContent(contentId)).isZero();
        assertThat(Query.from(GeneratedPdf.class).where("contentId = ?", contentId).count())
                .isEqualTo(2L);
    }

    @Test
    void humanReadableSizeFormatsBytesKbMb() {
        // Unknown size yields null so callers can omit it.
        assertThat(GeneratedPdf.humanReadableSize(0)).isNull();
        assertThat(GeneratedPdf.humanReadableSize(-1)).isNull();
        assertThat(GeneratedPdf.humanReadableSize(512)).isEqualTo("512 B");
        assertThat(GeneratedPdf.humanReadableSize(5 * 1024L)).isEqualTo("5 KB");
        assertThat(GeneratedPdf.humanReadableSize(2L * 1024 * 1024)).isEqualTo("2.0 MB");
        // Locale-independent decimal point (not "3,5 MB").
        assertThat(GeneratedPdf.humanReadableSize(3_670_016L)).isEqualTo("3.5 MB");
    }
}
