package com.realobjects.brightspot.pdfreactor;

import java.util.Collections;
import java.util.List;

import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * Configuration provider for the PDFreactor integration.
 *
 * <p>The default implementation, {@link SettingsPdfReactorConfig}, reads all
 * values from Dari {@code Settings} (context.xml, settings.properties, or
 * environment variables). Per-site overrides can be layered on top by
 * supplying a different implementation.</p>
 */
public interface PdfReactorConfig {

    /**
     * Returns the PDFreactor Web Service base URL,
     * e.g. {@code http://pdfreactor:9423/service/rest}.
     *
     * @return Nullable. {@code null} or blank means the integration is not
     *         configured; conversions fail closed.
     */
    String getServiceUrl();

    /**
     * Returns the API key required by the PDFreactor Web Service, if the
     * service is configured to require one.
     *
     * @return Nullable.
     */
    default String getApiKey() {
        return null;
    }

    /**
     * Returns the PDFreactor license key content to send with each
     * conversion. Usually {@code null} because the license is installed on
     * the PDFreactor service itself.
     *
     * @return Nullable.
     */
    default String getLicenseKey() {
        return null;
    }

    /**
     * Returns the HTTP timeout in milliseconds that the Java client waits
     * for a reply from the PDFreactor Web Service.
     */
    default int getClientTimeoutMillis() {
        return 60_000;
    }

    /**
     * Returns the short HTTP timeout in milliseconds used by the dashboard
     * health probe, distinct from the conversion-grade
     * {@link #getClientTimeoutMillis()}: a slow or unreachable service must
     * not tie up the probe for the full conversion timeout.
     */
    default int getHealthTimeoutMillis() {
        return 3_000;
    }

    /**
     * Returns the timeout in seconds for a whole document conversion.
     * Zero or negative means no timeout.
     */
    default int getConversionTimeoutSeconds() {
        return 300;
    }

    /**
     * Returns the polling interval in milliseconds used while waiting for an
     * asynchronous conversion to finish.
     */
    default long getAsyncPollIntervalMillis() {
        return 1_000L;
    }

    /**
     * Returns whether conversions run asynchronously
     * ({@code convertAsync} + progress polling) by default.
     */
    default boolean isAsyncDefault() {
        return false;
    }

    /**
     * Returns URIs of user stylesheets injected into every conversion
     * (typically the developer-authored print CSS).
     *
     * @return Nonnull. May be empty.
     */
    default List<String> getDefaultUserStyleSheetUris() {
        return Collections.emptyList();
    }

    /**
     * Returns the base URL used to resolve relative resource URLs in
     * converted documents.
     *
     * @return Nullable.
     */
    default String getBaseUrl() {
        return null;
    }

    /**
     * Returns the PDFreactor log level recorded with each conversion result.
     */
    default Configuration.LogLevel getLogLevel() {
        return Configuration.LogLevel.WARN;
    }

    /**
     * Returns the output-intent identifier (e.g. a standard profile name)
     * embedded into PDF/A or PDF/X output.
     *
     * @return Nullable.
     */
    default String getOutputIntentIdentifier() {
        return null;
    }

    /**
     * Returns the output-intent ICC profile bytes, already resolved
     * server-side (so the PDFreactor host needs no egress to fetch them).
     *
     * @return Nullable.
     */
    default byte[] getOutputIntentProfileData() {
        return null;
    }

    /**
     * Returns the CMYK ICC profile bytes used for color conversion, resolved
     * server-side.
     *
     * @return Nullable.
     */
    default byte[] getCmykIccProfileData() {
        return null;
    }

    /**
     * Returns whether PDFreactor color conversion is enabled.
     *
     * @return Nullable. {@code null} means leave the service default.
     */
    default Boolean getColorConversionEnabled() {
        return null;
    }

    /**
     * Returns the color-conversion rendering intent.
     *
     * @return Nullable.
     */
    default Configuration.ColorConversionIntent getColorConversionIntent() {
        return null;
    }

    /**
     * Returns the output conformance profile (PDF/A, PDF/UA, PDF/X). Applied
     * when a per-call {@link PdfRenderOptions#getConformance()} is not set.
     *
     * @return Nullable. {@code null} means plain PDF.
     */
    default Configuration.Conformance getConformance() {
        return null;
    }

    /**
     * Returns whether page JavaScript runs during conversion. Applied when a
     * per-call {@link PdfRenderOptions#getJavaScriptEnabled()} is not set.
     *
     * @return Nullable tri-state. {@code null} means "unset" — the effective
     *         value then defaults to <strong>enabled</strong>, matching normal
     *         PDFreactor behavior.
     */
    default Boolean getJavaScriptEnabled() {
        return null;
    }

    /**
     * Returns the document-metadata defaults written into the PDF info
     * dictionary, applied when the per-call/per-article value is unset.
     *
     * @return Nullable. {@code null} means unset.
     */
    default String getCreator() {
        return null;
    }

    /** @return Nullable. The default document subject. */
    default String getSubject() {
        return null;
    }

    /** @return Nullable. The default document keywords (comma-separated). */
    default String getKeywords() {
        return null;
    }

    /**
     * Returns custom document properties (extra PDF info-dictionary entries)
     * written into every conversion.
     *
     * @return Nonnull. May be empty.
     */
    default java.util.Map<String, String> getCustomDocumentProperties() {
        return java.util.Collections.emptyMap();
    }

    /**
     * Returns whether the PDF includes a bookmark outline / hyperlinks / tags
     * (accessibility structure). Applied when the per-call/per-article value is
     * unset; {@code null} leaves the client default.
     *
     * @return Nullable.
     */
    default Boolean getAddBookmarks() {
        return null;
    }

    /** @return Nullable. Whether to add hyperlinks. */
    default Boolean getAddLinks() {
        return null;
    }

    /** @return Nullable. Whether to add accessibility tags. */
    default Boolean getAddTags() {
        return null;
    }

    /**
     * Returns whether PDFreactor validates the output against the configured
     * conformance ({@code validateConformance}) and reports violations. A
     * global-only operational setting (no per-site override); a validation
     * failure surfaces through the normal conversion-failure path.
     *
     * @return Nullable. {@code null} leaves the client default.
     */
    default Boolean getValidateConformance() {
        return null;
    }

    /**
     * Returns the initial page-layout viewer preference, or {@code null} to
     * leave the client default.
     */
    default PdfViewerPageLayout getViewerPageLayout() {
        return null;
    }

    /** @return Nullable. {@code TRUE} sets the FIT_WINDOW viewer preference. */
    default Boolean getViewerFitWindow() {
        return null;
    }

    /** @return Nullable. {@code TRUE} sets the DISPLAY_DOC_TITLE viewer preference. */
    default Boolean getViewerDisplayDocTitle() {
        return null;
    }

    /**
     * Returns raw PDFreactor {@code Configuration} JSON merged onto every
     * assembled configuration — the full-configuration pass-through escape
     * hatch (global merged with per-site). It may only set configuration
     * properties the plugin does not own: every value sourced from a form
     * setting or a plugin decision (the document, content observer, error
     * policies, conformance, color management, JavaScript, document
     * title/author, and the user stylesheets) is re-enforced <em>after</em>
     * this merge and therefore takes precedence. The configured user
     * stylesheets are the exception by design: a pass-through {@code
     * userStyleSheets} list is appended after them rather than dropping them.
     *
     * @return Nullable. {@code null} or blank means no pass-through.
     */
    default String getConfigurationJson() {
        return null;
    }

    // Troubleshooting (debug / inspectable) is no longer carried on this config
    // interface. The administrator gate lives on the Sites & Settings record
    // (PdfReactorSiteSettings.troubleshootingEnabled) and the actual per-article
    // toggles on the content; both are resolved content-scoped in
    // PdfReactorConfigs.debugActive/inspectableActive/troubleshootingActive.
}
