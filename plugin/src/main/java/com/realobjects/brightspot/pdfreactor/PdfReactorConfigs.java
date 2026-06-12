package com.realobjects.brightspot.pdfreactor;

import com.psddev.cms.db.Site;
import com.psddev.dari.db.State;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;

/**
 * Resolves the effective {@link PdfReactorConfig} for a call. Per-site
 * overrides ({@link PdfReactorSiteSettings}) are layered over the global
 * {@link SettingsPdfReactorConfig}; the site is derived from the content
 * itself ({@code Site.ObjectModification#getOwner}), the same way publish
 * automation already derives it.
 */
public final class PdfReactorConfigs {

    private PdfReactorConfigs() {
    }

    /** The global, site-agnostic configuration. */
    public static PdfReactorConfig global() {
        return new SettingsPdfReactorConfig();
    }

    /**
     * Wraps a config so its byte-valued sources (ICC profiles) are read once
     * and reused for the rest of the request/publish. Build it at the
     * request boundary and hand the <em>same</em> instance to both
     * {@link PdfConfigFingerprint#of} and the conversion service, so the ICC
     * bytes are not read twice (or three times — see the publish path's
     * supersede guard). Do not cache the instance across requests.
     */
    public static PdfReactorConfig resolved(PdfReactorConfig config) {
        return new ResolvedPdfReactorConfig(config);
    }

    /**
     * Configuration for a specific site, falling back to global for any unset
     * value. A {@code null} site resolves to the global Sites &amp; Settings
     * overrides layered over the {@code pdfreactor/*} settings.
     */
    public static PdfReactorConfig forSite(Site site) {
        return new SitePdfReactorConfig(PdfReactorSiteSettings.forSite(site), global());
    }

    /**
     * Configuration for the site that owns the given content. Content with no
     * owning site resolves to the global configuration.
     *
     * @param content Nullable.
     */
    public static PdfReactorConfig forContent(Object content) {
        if (content == null) {
            return global();
        }
        Site owner = State.getInstance(content).as(Site.ObjectModification.class).getOwner();
        PdfReactorConfig siteConfig = forSite(owner);
        // Overlay this content's per-article overrides (metadata / document
        // features / viewer preferences) on top of the site/global config, so
        // they flow through buildConfiguration and the cache fingerprint. Only
        // HasPdfRendering content carries the override fields.
        return content instanceof HasPdfRendering
                ? new PerArticlePdfReactorConfig(content, siteConfig)
                : siteConfig;
    }

    /**
     * The administrator gate ("Allow debug/inspectable PDF builds") for a site:
     * the site's {@link PdfReactorSiteSettings#getTroubleshootingEnabled()} when
     * set, else the global Sites &amp; Settings master, else off. This only
     * <em>permits</em> producing diagnostic builds — the actual per-article
     * toggles live on the content (see {@code HasPdfRenderingData}).
     *
     * @param site Nullable. {@code null} resolves the global master directly.
     */
    public static boolean troubleshootingGateEnabled(Site site) {
        Boolean siteValue = PdfReactorSiteSettings.forSite(site).getTroubleshootingEnabled();
        if (siteValue != null) {
            return siteValue;
        }
        return Boolean.TRUE.equals(PdfReactorSiteSettings.forSite(null).getTroubleshootingEnabled());
    }

    /** The administrator gate for the site that owns the given content. */
    public static boolean troubleshootingGateEnabled(Object content) {
        if (content == null) {
            return troubleshootingGateEnabled((Site) null);
        }
        Site owner = State.getInstance(content).as(Site.ObjectModification.class).getOwner();
        return troubleshootingGateEnabled(owner);
    }

    /**
     * Whether a <em>debug</em> build is in effect for the given content: the
     * administrator gate is on for its site <em>and</em> the article's "Debug
     * build" toggle is on. Content-scoped — the toggle is per article, not per
     * site. The preview and on-demand Generate paths read this; the publish
     * automation never does (a published PDF is always the production build).
     */
    public static boolean debugActive(Object content) {
        return perArticleToggle(content, HasPdfRenderingData::isDebugBuild);
    }

    /** Whether an <em>inspectable</em> build is in effect — see {@link #debugActive}. */
    public static boolean inspectableActive(Object content) {
        return perArticleToggle(content, HasPdfRenderingData::isInspectableBuild);
    }

    /**
     * Whether either troubleshooting build (debug or inspectable) is in effect
     * for the content — the condition under which the on-demand Generate path
     * produces a diagnostic, uncached PDF.
     */
    public static boolean troubleshootingActive(Object content) {
        return debugActive(content) || inspectableActive(content);
    }

    private static boolean perArticleToggle(
            Object content,
            java.util.function.Predicate<HasPdfRenderingData> toggle) {
        return content instanceof HasPdfRendering
                && troubleshootingGateEnabled(content)
                && toggle.test(State.getInstance(content).as(HasPdfRenderingData.class));
    }
}
