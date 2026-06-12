package com.realobjects.brightspot.pdfreactor;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.psddev.dari.db.Query;
import com.psddev.dari.db.Record;
import com.psddev.dari.util.Settings;
import com.psddev.dari.util.StorageItem;

/**
 * A stored, generated PDF: the {@link StorageItem} holding the bytes plus
 * the cache key ({@code contentId:revision:configHash}, see
 * {@link PdfCacheKey}) it was generated for. Repeat requests for the same
 * key serve the stored PDF instead of converting again.
 */
public class GeneratedPdf extends Record {

    /** Number of most-recent stored PDFs to keep per content; 0/negative disables pruning. */
    static final String RETENTION_SETTING = "pdfreactor/generatedPdfRetention";
    static final int DEFAULT_RETENTION = 20;

    @Indexed(unique = true)
    private String cacheKey;

    @Indexed
    private UUID contentId;

    private StorageItem pdf;

    @Indexed
    private Date generated;

    private int numberOfPages;

    /** Size of the stored PDF in bytes; 0 for rows written before this was tracked. */
    private long byteSize;

    /**
     * A compact, human-readable rendering of a byte count — e.g. {@code "842 KB"}
     * or {@code "3.7 MB"} — or {@code null} when the size is unknown
     * ({@code bytes <= 0}), so callers can omit it. Uses a fixed
     * (locale-independent) format so the value reads the same everywhere.
     */
    public static String humanReadableSize(long bytes) {
        if (bytes <= 0) {
            return null;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Returns the stored PDF for the given cache key, or {@code null}.
     */
    public static GeneratedPdf findByCacheKey(String cacheKey) {
        return Query.from(GeneratedPdf.class).where("cacheKey = ?", cacheKey).first();
    }

    /**
     * Returns the stored PDF with the given id, or {@code null}. Used to
     * download the exact record currently shown in the edit-form widget,
     * without a content lookup or a fresh conversion.
     */
    public static GeneratedPdf findById(UUID id) {
        return id == null ? null : Query.from(GeneratedPdf.class).where("_id = ?", id).first();
    }

    /**
     * Returns the most recently generated PDF for the given content, or
     * {@code null}.
     */
    public static GeneratedPdf findLatestForContent(UUID contentId) {
        return Query.from(GeneratedPdf.class)
                .where("contentId = ?", contentId)
                .sortDescending("generated")
                .first();
    }

    /**
     * Deletes the oldest stored PDFs for the content beyond the retention
     * count ({@value #RETENTION_SETTING}, default {@value #DEFAULT_RETENTION};
     * {@code 0} or negative disables pruning), so repeated re-publishes do not
     * grow the table without bound. Best-effort: a storage-delete failure is
     * skipped, never propagated.
     *
     * @param contentId Nullable; {@code null} is a no-op.
     * @return The number of records deleted.
     */
    public static int pruneForContent(UUID contentId) {
        if (contentId == null) {
            return 0;
        }
        int keep = Settings.getOrDefault(int.class, RETENTION_SETTING, DEFAULT_RETENTION);
        if (keep <= 0) {
            return 0;
        }
        List<GeneratedPdf> all = Query.from(GeneratedPdf.class)
                .where("contentId = ?", contentId)
                .sortDescending("generated")
                .selectAll();
        int deleted = 0;
        for (int i = keep; i < all.size(); i++) {
            GeneratedPdf old = all.get(i);
            StorageItem item = old.getPdf();
            // Delete the record BEFORE the storage object: a concurrent
            // download-by-id then never resolves a record that points at an
            // already-deleted blob (a dangling record is the longer-lived risk).
            old.delete();
            if (item != null) {
                try {
                    item.delete();
                } catch (IOException | RuntimeException ignored) {
                    // Best effort: a stale storage object is not worth failing on.
                }
            }
            deleted++;
        }
        return deleted;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public StorageItem getPdf() {
        return pdf;
    }

    public void setPdf(StorageItem pdf) {
        this.pdf = pdf;
    }

    public Date getGenerated() {
        return generated;
    }

    public void setGenerated(Date generated) {
        this.generated = generated;
    }

    public int getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public long getByteSize() {
        return byteSize;
    }

    public void setByteSize(long byteSize) {
        this.byteSize = byteSize;
    }

    @Override
    public String getLabel() {
        return "PDF " + (contentId != null ? contentId : "?")
                + (generated != null ? " (" + generated + ")" : "");
    }
}
