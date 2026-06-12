package com.realobjects.brightspot.pdfreactor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * A {@link PdfReactorConfig} decorator that resolves the expensive
 * byte-valued sources — the ICC profiles — <em>once</em>, so a single
 * conversion does not read them twice (once for the {@link PdfConfigFingerprint}
 * that builds the {@link PdfCacheKey}, and again for the actual conversion in
 * {@link DefaultPdfReactorService}).
 *
 * <p>The bytes are read eagerly in the constructor into {@code final} fields:
 * this both fixes the read count and makes the snapshot safe to publish to a
 * background task thread (the publish path builds it on the editor's save
 * thread and hands the same instance to the off-thread conversion). Every
 * other value is delegated live to the wrapped config.</p>
 *
 * <p>Use it at the request/publish boundary via
 * {@link PdfReactorConfigs#resolved(PdfReactorConfig)}; do not cache an
 * instance across requests, so that a re-uploaded ICC profile is picked up on
 * the next conversion. The fingerprint stays byte-accurate: re-uploading a
 * different profile to the same path changes the resolved bytes and therefore
 * the cache key.</p>
 */
final class ResolvedPdfReactorConfig implements PdfReactorConfig {

    private final PdfReactorConfig delegate;

    // Snapshot of EVERY output-affecting value that feeds PdfConfigFingerprint,
    // taken once in the constructor. The publish path builds this on the save
    // thread and hands the same instance to the off-thread conversion AND the
    // supersede guard, so the recomputed cache key compares against the config
    // the task started with — an admin changing a setting mid-conversion no
    // longer mismatches the key and gets the publish silently dropped as
    // "superseded". Non-output values (URLs, timeouts, log level)
    // still delegate live.
    private final byte[] outputIntentProfileData;
    private final byte[] cmykIccProfileData;
    private final String baseUrl;
    private final List<String> defaultUserStyleSheetUris;
    private final Configuration.Conformance conformance;
    private final Boolean javaScriptEnabled;
    private final String outputIntentIdentifier;
    private final Boolean colorConversionEnabled;
    private final Configuration.ColorConversionIntent colorConversionIntent;
    private final String configurationJson;
    private final String creator;
    private final String subject;
    private final String keywords;
    private final Boolean addBookmarks;
    private final Boolean addLinks;
    private final Boolean addTags;
    private final Boolean validateConformance;
    private final PdfViewerPageLayout viewerPageLayout;
    private final Boolean viewerFitWindow;
    private final Boolean viewerDisplayDocTitle;
    private final Map<String, String> customDocumentProperties;

    ResolvedPdfReactorConfig(PdfReactorConfig delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.outputIntentProfileData = delegate.getOutputIntentProfileData();
        this.cmykIccProfileData = delegate.getCmykIccProfileData();
        this.baseUrl = delegate.getBaseUrl();
        List<String> uris = delegate.getDefaultUserStyleSheetUris();
        this.defaultUserStyleSheetUris = uris != null
                ? java.util.Collections.unmodifiableList(new java.util.ArrayList<>(uris))
                : null;
        this.conformance = delegate.getConformance();
        this.javaScriptEnabled = delegate.getJavaScriptEnabled();
        this.outputIntentIdentifier = delegate.getOutputIntentIdentifier();
        this.colorConversionEnabled = delegate.getColorConversionEnabled();
        this.colorConversionIntent = delegate.getColorConversionIntent();
        this.configurationJson = delegate.getConfigurationJson();
        this.creator = delegate.getCreator();
        this.subject = delegate.getSubject();
        this.keywords = delegate.getKeywords();
        this.addBookmarks = delegate.getAddBookmarks();
        this.addLinks = delegate.getAddLinks();
        this.addTags = delegate.getAddTags();
        this.validateConformance = delegate.getValidateConformance();
        this.viewerPageLayout = delegate.getViewerPageLayout();
        this.viewerFitWindow = delegate.getViewerFitWindow();
        this.viewerDisplayDocTitle = delegate.getViewerDisplayDocTitle();
        Map<String, String> props = delegate.getCustomDocumentProperties();
        this.customDocumentProperties = props != null && !props.isEmpty()
                ? Collections.unmodifiableMap(new LinkedHashMap<>(props))
                : Collections.emptyMap();
    }

    @Override
    public Map<String, String> getCustomDocumentProperties() {
        return customDocumentProperties;
    }

    @Override
    public Boolean getValidateConformance() {
        return validateConformance;
    }

    @Override
    public PdfViewerPageLayout getViewerPageLayout() {
        return viewerPageLayout;
    }

    @Override
    public Boolean getViewerFitWindow() {
        return viewerFitWindow;
    }

    @Override
    public Boolean getViewerDisplayDocTitle() {
        return viewerDisplayDocTitle;
    }

    @Override
    public String getCreator() {
        return creator;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public String getKeywords() {
        return keywords;
    }

    @Override
    public Boolean getAddBookmarks() {
        return addBookmarks;
    }

    @Override
    public Boolean getAddLinks() {
        return addLinks;
    }

    @Override
    public Boolean getAddTags() {
        return addTags;
    }

    @Override
    public byte[] getOutputIntentProfileData() {
        return outputIntentProfileData;
    }

    @Override
    public byte[] getCmykIccProfileData() {
        return cmykIccProfileData;
    }

    @Override
    public String getServiceUrl() {
        return delegate.getServiceUrl();
    }

    @Override
    public String getApiKey() {
        return delegate.getApiKey();
    }

    @Override
    public String getLicenseKey() {
        return delegate.getLicenseKey();
    }

    @Override
    public int getClientTimeoutMillis() {
        return delegate.getClientTimeoutMillis();
    }

    @Override
    public int getHealthTimeoutMillis() {
        return delegate.getHealthTimeoutMillis();
    }

    @Override
    public int getConversionTimeoutSeconds() {
        return delegate.getConversionTimeoutSeconds();
    }

    @Override
    public long getAsyncPollIntervalMillis() {
        return delegate.getAsyncPollIntervalMillis();
    }

    @Override
    public boolean isAsyncDefault() {
        return delegate.isAsyncDefault();
    }

    @Override
    public List<String> getDefaultUserStyleSheetUris() {
        return defaultUserStyleSheetUris != null
                ? defaultUserStyleSheetUris
                : java.util.Collections.emptyList();
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public Configuration.LogLevel getLogLevel() {
        return delegate.getLogLevel();
    }

    @Override
    public String getOutputIntentIdentifier() {
        return outputIntentIdentifier;
    }

    @Override
    public Boolean getColorConversionEnabled() {
        return colorConversionEnabled;
    }

    @Override
    public Configuration.ColorConversionIntent getColorConversionIntent() {
        return colorConversionIntent;
    }

    @Override
    public String getConfigurationJson() {
        return configurationJson;
    }

    @Override
    public Configuration.Conformance getConformance() {
        return conformance;
    }

    @Override
    public Boolean getJavaScriptEnabled() {
        return javaScriptEnabled;
    }

}
