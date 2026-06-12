package com.realobjects.brightspot.pdfreactor;

import java.util.List;
import java.util.Objects;

import com.psddev.dari.db.State;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;
import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * A {@link PdfReactorConfig} decorator that overlays one piece of content's
 * per-article overrides (from {@link HasPdfRenderingData}) on top of the
 * site/global config: for each overrideable value the per-article value wins
 * when set, else the delegate's is used. Everything else delegates straight
 * through.
 *
 * <p>Because the overrides flow through the resolved config, they reach
 * {@link DefaultPdfReactorService}'s {@code buildConfiguration} and the
 * {@link PdfConfigFingerprint} with no other change — the per-article layer sits
 * between the per-call options and the site/global config in the documented
 * precedence chain (per-call → per-article → site → global → built-in default).
 * {@code validateConformance} is global-only and is not overridden here.</p>
 */
final class PerArticlePdfReactorConfig implements PdfReactorConfig {

    private final HasPdfRenderingData data;
    private final PdfReactorConfig delegate;

    PerArticlePdfReactorConfig(Object content, PdfReactorConfig delegate) {
        this.data = State.getInstance(Objects.requireNonNull(content, "content"))
                .as(HasPdfRenderingData.class);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    // --- Per-article overrides: per-article value wins when set. ---

    @Override
    public String getCreator() {
        return firstNonBlank(data.getCreator(), delegate.getCreator());
    }

    @Override
    public String getSubject() {
        return firstNonBlank(data.getSubject(), delegate.getSubject());
    }

    @Override
    public String getKeywords() {
        return firstNonBlank(data.getKeywords(), delegate.getKeywords());
    }

    @Override
    public Boolean getAddBookmarks() {
        return data.getAddBookmarks() != null ? data.getAddBookmarks() : delegate.getAddBookmarks();
    }

    @Override
    public Boolean getAddLinks() {
        return data.getAddLinks() != null ? data.getAddLinks() : delegate.getAddLinks();
    }

    @Override
    public Boolean getAddTags() {
        return data.getAddTags() != null ? data.getAddTags() : delegate.getAddTags();
    }

    @Override
    public PdfViewerPageLayout getViewerPageLayout() {
        return data.getViewerPageLayout() != null
                ? data.getViewerPageLayout()
                : delegate.getViewerPageLayout();
    }

    @Override
    public Boolean getViewerFitWindow() {
        return data.getViewerFitWindow() != null
                ? data.getViewerFitWindow()
                : delegate.getViewerFitWindow();
    }

    @Override
    public Boolean getViewerDisplayDocTitle() {
        return data.getViewerDisplayDocTitle() != null
                ? data.getViewerDisplayDocTitle()
                : delegate.getViewerDisplayDocTitle();
    }

    private static String firstNonBlank(String perArticle, String inherited) {
        return perArticle != null && !perArticle.trim().isEmpty() ? perArticle : inherited;
    }

    // --- Everything else delegates straight through. ---

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
        return delegate.getDefaultUserStyleSheetUris();
    }

    @Override
    public String getBaseUrl() {
        return delegate.getBaseUrl();
    }

    @Override
    public Configuration.LogLevel getLogLevel() {
        return delegate.getLogLevel();
    }

    @Override
    public String getOutputIntentIdentifier() {
        return delegate.getOutputIntentIdentifier();
    }

    @Override
    public byte[] getOutputIntentProfileData() {
        return delegate.getOutputIntentProfileData();
    }

    @Override
    public byte[] getCmykIccProfileData() {
        return delegate.getCmykIccProfileData();
    }

    @Override
    public Boolean getColorConversionEnabled() {
        return delegate.getColorConversionEnabled();
    }

    @Override
    public Configuration.ColorConversionIntent getColorConversionIntent() {
        return delegate.getColorConversionIntent();
    }

    @Override
    public Configuration.Conformance getConformance() {
        return delegate.getConformance();
    }

    @Override
    public Boolean getJavaScriptEnabled() {
        return delegate.getJavaScriptEnabled();
    }

    @Override
    public String getConfigurationJson() {
        return delegate.getConfigurationJson();
    }

    @Override
    public Boolean getValidateConformance() {
        return delegate.getValidateConformance();
    }
}
