package com.realobjects.brightspot.pdfreactor;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import com.psddev.cms.db.Content;
import com.psddev.dari.db.State;

/**
 * Builds the cache key for a generated PDF:
 * {@code contentId:revision:optionsHash:configFingerprint}. The revision
 * component is the content's update date (a new revision means a new update
 * date); the options hash covers every {@link PdfRenderOptions} value that
 * affects the output bytes; and the {@linkplain PdfConfigFingerprint config
 * fingerprint} covers the config-sourced output-affecting values (per-site
 * print CSS, ICC profiles). A re-publish, an options change, or a config
 * change therefore regenerates, while repeat requests hit the cache.
 */
public final class PdfCacheKey {

    private PdfCacheKey() {
    }

    /**
     * @param configFingerprint Nullable. The {@link PdfConfigFingerprint} of
     *        the effective config; {@code null} is treated as empty (for
     *        call sites with no per-site/ICC config to distinguish).
     */
    public static String of(Object content, PdfRenderOptions options, String configFingerprint) {
        Objects.requireNonNull(content, "content");
        State state = State.getInstance(content);
        Date updateDate = state.as(Content.ObjectModification.class).getUpdateDate();

        return state.getId()
                + ":" + (updateDate != null ? updateDate.getTime() : 0L)
                + ":" + optionsHash(options != null ? options : PdfRenderOptions.builder().build())
                + ":" + (configFingerprint != null ? configFingerprint : "");
    }

    /**
     * Storage object key for the generated PDF of a cache entry, unique per
     * entry. Derived from a hash of the <em>whole</em> cache key — not just one
     * segment — so two distinct entries for the same content (a different
     * revision, options, or config) never collide on the same stored object and
     * silently overwrite each other.
     *
     * @param contentId Nonnull.
     * @param cacheKey Nonnull. As built by {@link #of}.
     */
    public static String storagePath(UUID contentId, String cacheKey) {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(cacheKey, "cacheKey");
        return "pdfreactor/" + contentId + "/"
                + Hashing.sha256Hex(cacheKey).substring(0, 12) + ".pdf";
    }

    /**
     * Hashes the options values that influence the output document.
     * (Error policies, timeouts, and sync/async do not change the bytes and
     * are excluded.)
     */
    static String optionsHash(PdfRenderOptions options) {
        Hashing.Canonical canonical = Hashing.canonical()
                .add("baseUrl", options.getBaseUrl())
                .add("paperSize", options.getPaperSize())
                .add("margin", options.getMargin())
                .add("headerContent", options.getHeaderContent())
                .add("footerContent", options.getFooterContent())
                .add("conformance", enumName(options.getConformance()))
                .add("javaScriptEnabled", String.valueOf(options.getJavaScriptEnabled()))
                .add("title", options.getTitle())
                .add("author", options.getAuthor())
                .add("outputIntentIdentifier", options.getOutputIntentIdentifier())
                .add("outputIntentProfile", options.getOutputIntentProfileData())
                .add("cmykIccProfile", options.getCmykIccProfileData())
                .add("colorConversionEnabled", String.valueOf(options.getColorConversionEnabled()))
                .add("colorConversionIntent", enumName(options.getColorConversionIntent()))
                .add("configJson", options.getConfigurationJson());

        // Stylesheets are indexed and length-prefixed so a sheet boundary can
        // never shift to collide with a different sheet split.
        java.util.List<PdfStyleSheet> styleSheets = options.getStyleSheets();
        canonical.add("styleSheetCount", String.valueOf(styleSheets.size()));
        for (int i = 0; i < styleSheets.size(); i++) {
            canonical.add("styleSheet" + i + ".uri", styleSheets.get(i).getUri());
            canonical.add("styleSheet" + i + ".content", styleSheets.get(i).getContent());
        }
        return canonical.digest();
    }

    private static String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }
}
