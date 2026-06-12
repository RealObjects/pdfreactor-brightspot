package com.realobjects.brightspot.pdfreactor;

import java.util.List;
import java.util.Objects;

/**
 * {@link PdfReactorConfig} that layers a site's {@link PdfReactorSiteSettings}
 * over a global delegate (normally {@link SettingsPdfReactorConfig}). For each
 * value, a non-blank / non-empty site override wins; otherwise the global
 * value is used. This is how a single deployment serves multiple brands with
 * different base URLs and print stylesheets. (The service URL and API key are
 * deploy-time/global only and are not layered per site — see
 * {@link PdfReactorSiteSettings}.)
 *
 * @see PdfReactorConfigs
 */
public class SitePdfReactorConfig implements PdfReactorConfig {

    private final PdfReactorSiteSettings site;
    private final PdfReactorConfig global;

    public SitePdfReactorConfig(PdfReactorSiteSettings site, PdfReactorConfig global) {
        this.site = Objects.requireNonNull(site, "site");
        this.global = Objects.requireNonNull(global, "global");
    }

    // Service URL and API key are deploy-time/global only: they are
    // not per-site editable, so there is no site override to layer here.
    @Override
    public String getServiceUrl() {
        return global.getServiceUrl();
    }

    @Override
    public String getApiKey() {
        return global.getApiKey();
    }

    @Override
    public String getLicenseKey() {
        return global.getLicenseKey();
    }

    @Override
    public int getClientTimeoutMillis() {
        return global.getClientTimeoutMillis();
    }

    @Override
    public int getHealthTimeoutMillis() {
        return global.getHealthTimeoutMillis();
    }

    @Override
    public int getConversionTimeoutSeconds() {
        return global.getConversionTimeoutSeconds();
    }

    @Override
    public long getAsyncPollIntervalMillis() {
        return global.getAsyncPollIntervalMillis();
    }

    @Override
    public boolean isAsyncDefault() {
        return global.isAsyncDefault();
    }

    @Override
    public List<String> getDefaultUserStyleSheetUris() {
        List<String> siteUris = site.getDefaultUserStyleSheetUris();
        return siteUris != null && !siteUris.isEmpty()
                ? siteUris
                : global.getDefaultUserStyleSheetUris();
    }

    @Override
    public String getBaseUrl() {
        return override(site.getBaseUrl(), global.getBaseUrl());
    }

    @Override
    public com.realobjects.pdfreactor.webservice.client.Configuration.LogLevel getLogLevel() {
        return global.getLogLevel();
    }

    @Override
    public com.realobjects.pdfreactor.webservice.client.Configuration.Conformance getConformance() {
        PdfReactorSiteSettings.Conformance siteConformance = site.getConformance();
        return siteConformance != null
                ? com.realobjects.pdfreactor.webservice.client.Configuration.Conformance
                        .valueOf(siteConformance.name())
                : global.getConformance();
    }

    @Override
    public String getOutputIntentIdentifier() {
        return override(site.getOutputIntentIdentifier(), global.getOutputIntentIdentifier());
    }

    @Override
    public byte[] getOutputIntentProfileData() {
        byte[] siteData = profileBytes(site.getOutputIntentProfile());
        return siteData != null ? siteData : global.getOutputIntentProfileData();
    }

    @Override
    public byte[] getCmykIccProfileData() {
        byte[] siteData = profileBytes(site.getCmykIccProfile());
        return siteData != null ? siteData : global.getCmykIccProfileData();
    }

    /** Bytes of a referenced reusable {@link IccProfile}, or null. */
    private static byte[] profileBytes(IccProfile profile) {
        return profile != null ? profile.readBytes() : null;
    }

    @Override
    public Boolean getColorConversionEnabled() {
        return site.getColorConversionEnabled() != null
                ? site.getColorConversionEnabled()
                : global.getColorConversionEnabled();
    }

    @Override
    public com.realobjects.pdfreactor.webservice.client.Configuration.ColorConversionIntent getColorConversionIntent() {
        // Not a per-site form field — deploy-time/global only.
        return global.getColorConversionIntent();
    }

    @Override
    public Boolean getJavaScriptEnabled() {
        return site.getJavaScriptEnabled() != null
                ? site.getJavaScriptEnabled()
                : global.getJavaScriptEnabled();
    }

    @Override
    public String getCreator() {
        return override(site.getCreator(), global.getCreator());
    }

    @Override
    public String getSubject() {
        return override(site.getSubject(), global.getSubject());
    }

    @Override
    public String getKeywords() {
        return override(site.getKeywords(), global.getKeywords());
    }

    @Override
    public Boolean getAddBookmarks() {
        return site.getAddBookmarks() != null ? site.getAddBookmarks() : global.getAddBookmarks();
    }

    @Override
    public Boolean getAddLinks() {
        return site.getAddLinks() != null ? site.getAddLinks() : global.getAddLinks();
    }

    @Override
    public Boolean getAddTags() {
        return site.getAddTags() != null ? site.getAddTags() : global.getAddTags();
    }

    @Override
    public Boolean getValidateConformance() {
        // Global-only (no per-site override), like the connection settings.
        return global.getValidateConformance();
    }

    @Override
    public PdfViewerPageLayout getViewerPageLayout() {
        return site.getViewerPageLayout() != null
                ? site.getViewerPageLayout()
                : global.getViewerPageLayout();
    }

    @Override
    public Boolean getViewerFitWindow() {
        return site.getViewerFitWindow() != null ? site.getViewerFitWindow() : global.getViewerFitWindow();
    }

    @Override
    public Boolean getViewerDisplayDocTitle() {
        return site.getViewerDisplayDocTitle() != null
                ? site.getViewerDisplayDocTitle()
                : global.getViewerDisplayDocTitle();
    }

    @Override
    public String getConfigurationJson() {
        // Deep-merge the site JSON over the global JSON (objects merge,
        // arrays/scalars replace) so per-site overrides layer rather than
        // wholesale-replace the global pass-through.
        com.fasterxml.jackson.databind.JsonNode merged = RawConfiguration.deepMerge(
                RawConfiguration.parse(global.getConfigurationJson()),
                RawConfiguration.parse(site.getConfigurationJson()));
        return merged != null ? merged.toString() : null;
    }

    /** Site value if non-null and non-blank, else the global value. */
    private static String override(String siteValue, String globalValue) {
        return siteValue != null && !siteValue.trim().isEmpty() ? siteValue : globalValue;
    }
}
