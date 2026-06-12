package com.realobjects.brightspot.pdfreactor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A stable fingerprint of the <em>config-sourced</em> values that affect the
 * output bytes of a conversion (base URL, default print stylesheets, ICC /
 * color management). Once configuration — not just per-call
 * {@link PdfRenderOptions} — can change the output (per-site overrides, ICC
 * profiles, conformance, the raw-config pass-through), the options hash alone
 * no longer identifies a cached PDF, so this fingerprint becomes part of the
 * {@link PdfCacheKey}. Two sites with different print CSS or ICC profiles
 * therefore never share a cache entry for the same content.
 */
public final class PdfConfigFingerprint {

    private PdfConfigFingerprint() {
    }

    /**
     * @param config Nonnull.
     * @return Nonnull. A short hex digest.
     */
    public static String of(PdfReactorConfig config) {
        Hashing.Canonical canonical = Hashing.canonical()
                .add("baseUrl", config.getBaseUrl());

        // KNOWN, DELIBERATE LIMITATION (cache-invalidation asymmetry):
        // default user stylesheets are hashed by URI *list*, not by fetched
        // CSS bytes — unlike ICC profiles below, which are byte-hashed. So
        // editing the CSS served at a stable theme URL does NOT bust the
        // cache; editors keep the old PDF until they click "Convert again"
        // (whose tooltip already names this: stylesheets/images/fonts changed
        // outside the CMS). Byte-hashing the CSS would make the cache key
        // self-invalidating, but the fingerprint is recomputed on every
        // publish-save (on the editor's save thread) and must be identical on
        // the publish and on-demand paths, so it cannot fetch per side. Doing
        // it safely needs a resolved-bytes cache keyed by a cheap source
        // identity (lastModified/etag) — deferred; "Convert again" is the
        // explicit invalidation lever until then.
        List<String> styleSheets = config.getDefaultUserStyleSheetUris();
        int styleSheetCount = styleSheets != null ? styleSheets.size() : 0;
        canonical.add("styleSheetCount", String.valueOf(styleSheetCount));
        for (int i = 0; i < styleSheetCount; i++) {
            canonical.add("styleSheet" + i, styleSheets.get(i));
        }

        canonical.add("conformance", enumName(config.getConformance()))
                .add("javaScriptEnabled", String.valueOf(config.getJavaScriptEnabled()))
                .add("creator", config.getCreator())
                .add("subject", config.getSubject())
                .add("keywords", config.getKeywords())
                .add("addBookmarks", String.valueOf(config.getAddBookmarks()))
                .add("addLinks", String.valueOf(config.getAddLinks()))
                .add("addTags", String.valueOf(config.getAddTags()))
                .add("validateConformance", String.valueOf(config.getValidateConformance()))
                .add("viewerPageLayout", enumName(config.getViewerPageLayout()))
                .add("viewerFitWindow", String.valueOf(config.getViewerFitWindow()))
                .add("viewerDisplayDocTitle", String.valueOf(config.getViewerDisplayDocTitle()))
                .add("outputIntentIdentifier", config.getOutputIntentIdentifier())
                .add("outputIntentProfile", config.getOutputIntentProfileData())
                .add("cmykIccProfile", config.getCmykIccProfileData())
                .add("colorConversionEnabled", String.valueOf(config.getColorConversionEnabled()))
                .add("colorConversionIntent", enumName(config.getColorConversionIntent()))
                .add("configJson", config.getConfigurationJson());

        // Custom document properties: sorted + indexed entries so a map change
        // always busts the key and no two maps collide.
        Map<String, String> customProps = config.getCustomDocumentProperties();
        TreeMap<String, String> sorted = new TreeMap<>(
                customProps != null ? customProps : java.util.Collections.emptyMap());
        canonical.add("customDocumentPropertyCount", String.valueOf(sorted.size()));
        int index = 0;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            canonical.add("customDocumentProperty" + index + ".key", entry.getKey());
            canonical.add("customDocumentProperty" + index + ".value", entry.getValue());
            index++;
        }

        return canonical.digest();
    }

    private static String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }
}
