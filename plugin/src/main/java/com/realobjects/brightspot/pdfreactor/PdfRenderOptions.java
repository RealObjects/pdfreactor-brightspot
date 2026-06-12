package com.realobjects.brightspot.pdfreactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * Immutable per-conversion options, built via {@link #builder()} or seeded
 * from a {@link DefaultPdfReactorConfiguration} annotation via
 * {@link #fromAnnotated(Class)}.
 *
 * <p>Error policy defaults follow the per-path branching from the
 * architecture: license problems always fail closed unless explicitly
 * relaxed, while missing resources fail closed only where a broken PDF must
 * never be archived (publish/automation); the editor preview leaves
 * {@link #isFailOnMissingResources()} off and surfaces diagnostics
 * instead.</p>
 */
public final class PdfRenderOptions {

    private final String baseUrl;
    private final List<PdfStyleSheet> styleSheets;
    private final String paperSize;
    private final String margin;
    private final String headerContent;
    private final String footerContent;
    private final Configuration.Conformance conformance;
    private final boolean failOnMissingResources;
    private final boolean failOnLicenseProblems;
    private final Boolean javaScriptEnabled;
    private final Boolean async;
    private final Integer conversionTimeoutSeconds;
    private final String title;
    private final String author;
    private final String outputIntentIdentifier;
    private final byte[] outputIntentProfileData;
    private final byte[] cmykIccProfileData;
    private final Boolean colorConversionEnabled;
    private final Configuration.ColorConversionIntent colorConversionIntent;
    private final String configurationJson;
    private final boolean debug;
    private final boolean inspectable;

    private PdfRenderOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.styleSheets = Collections.unmodifiableList(new ArrayList<>(builder.styleSheets));
        this.paperSize = builder.paperSize;
        this.margin = builder.margin;
        this.headerContent = builder.headerContent;
        this.footerContent = builder.footerContent;
        this.conformance = builder.conformance;
        this.failOnMissingResources = builder.failOnMissingResources;
        this.failOnLicenseProblems = builder.failOnLicenseProblems;
        this.javaScriptEnabled = builder.javaScriptEnabled;
        this.async = builder.async;
        this.conversionTimeoutSeconds = builder.conversionTimeoutSeconds;
        this.title = builder.title;
        this.author = builder.author;
        this.outputIntentIdentifier = builder.outputIntentIdentifier;
        this.outputIntentProfileData = builder.outputIntentProfileData;
        this.cmykIccProfileData = builder.cmykIccProfileData;
        this.colorConversionEnabled = builder.colorConversionEnabled;
        this.colorConversionIntent = builder.colorConversionIntent;
        this.configurationJson = builder.configurationJson;
        this.debug = builder.debug;
        this.inspectable = builder.inspectable;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder seeded from the {@link DefaultPdfReactorConfiguration}
     * annotation on the given class, if present.
     *
     * @param annotated Nonnull. Typically a ViewModel class.
     */
    public static Builder fromAnnotated(Class<?> annotated) {
        Objects.requireNonNull(annotated, "annotated");
        Builder builder = new Builder();
        DefaultPdfReactorConfiguration annotation =
                annotated.getAnnotation(DefaultPdfReactorConfiguration.class);
        if (annotation != null) {
            builder.paperSize(emptyToNull(annotation.paperSize()))
                    .margin(emptyToNull(annotation.margin()))
                    .headerContent(emptyToNull(annotation.headerContent()))
                    .footerContent(emptyToNull(annotation.footerContent()));
            if (annotation.conformance() != Configuration.Conformance.PDF) {
                builder.conformance(annotation.conformance());
            }
            for (String uri : annotation.userStyleSheetUris()) {
                builder.addStyleSheet(PdfStyleSheet.fromUri(uri));
            }
            builder.outputIntentIdentifier(emptyToNull(annotation.outputIntentIdentifier()));
            String outputIntentProfile = emptyToNull(annotation.outputIntentProfileClasspath());
            if (outputIntentProfile != null) {
                builder.outputIntentProfileData(
                        IccProfiles.read(IccProfiles.CLASSPATH_PREFIX + outputIntentProfile));
            }
            String cmykProfile = emptyToNull(annotation.cmykIccProfileClasspath());
            if (cmykProfile != null) {
                builder.cmykIccProfileData(
                        IccProfiles.read(IccProfiles.CLASSPATH_PREFIX + cmykProfile));
            }
            builder.configurationJson(emptyToNull(annotation.configurationJson()));
            switch (annotation.javaScript()) {
                case ENABLED:
                    builder.javaScriptEnabled(true);
                    break;
                case DISABLED:
                    builder.javaScriptEnabled(false);
                    break;
                default:
                    // DEFAULT — leave unset so the site/global setting (then the
                    // built-in enabled default) is inherited at conversion time.
                    break;
            }
        }
        return builder;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public List<PdfStyleSheet> getStyleSheets() {
        return styleSheets;
    }

    public String getPaperSize() {
        return paperSize;
    }

    public String getMargin() {
        return margin;
    }

    public String getHeaderContent() {
        return headerContent;
    }

    public String getFooterContent() {
        return footerContent;
    }

    /**
     * @return Nullable. {@code null} means plain PDF output.
     */
    public Configuration.Conformance getConformance() {
        return conformance;
    }

    public boolean isFailOnMissingResources() {
        return failOnMissingResources;
    }

    public boolean isFailOnLicenseProblems() {
        return failOnLicenseProblems;
    }

    /**
     * @return Nullable tri-state. {@code null} means "unset" — the effective
     *         value is then inherited from the config
     *         ({@link PdfReactorConfig#getJavaScriptEnabled()}) and, when that
     *         is also unset, defaults to <strong>enabled</strong> (matching
     *         normal PDFreactor behavior). The resolution lives in
     *         {@link DefaultPdfReactorService}.
     */
    public Boolean getJavaScriptEnabled() {
        return javaScriptEnabled;
    }

    /**
     * @return Nullable. {@code null} means use the configured default.
     */
    public Boolean getAsync() {
        return async;
    }

    /**
     * @return Nullable. {@code null} means use the configured default.
     */
    public Integer getConversionTimeoutSeconds() {
        return conversionTimeoutSeconds;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    /**
     * @return Nullable. The output-intent identifier for this conversion.
     */
    public String getOutputIntentIdentifier() {
        return outputIntentIdentifier;
    }

    /**
     * @return Nullable. Output-intent ICC profile bytes for this conversion
     *         (a defensive copy — these bytes feed the cache-key fingerprint).
     */
    public byte[] getOutputIntentProfileData() {
        return outputIntentProfileData != null ? outputIntentProfileData.clone() : null;
    }

    /**
     * @return Nullable. CMYK ICC profile bytes for this conversion (a defensive
     *         copy — these bytes feed the cache-key fingerprint).
     */
    public byte[] getCmykIccProfileData() {
        return cmykIccProfileData != null ? cmykIccProfileData.clone() : null;
    }

    /**
     * @return Nullable. {@code null} leaves the service default.
     */
    public Boolean getColorConversionEnabled() {
        return colorConversionEnabled;
    }

    /**
     * @return Nullable. Color-conversion rendering intent for this conversion.
     */
    public Configuration.ColorConversionIntent getColorConversionIntent() {
        return colorConversionIntent;
    }

    /**
     * @return Nullable. The per-view/per-call raw configuration JSON (already
     *         deep-merged), applied over the global/per-site pass-through.
     */
    public String getConfigurationJson() {
        return configurationJson;
    }

    /**
     * @return Whether to produce a debug build (intermediate documents, logs,
     *         and resources attached to the PDF) for troubleshooting. Diagnostic
     *         output — never used by the preview or publish paths.
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * @return Whether to produce an inspectable build (the rendered DOM is
     *         embedded so the document can be opened in the PDFreactor Inspector)
     *         for troubleshooting layout. Diagnostic output — never used by the
     *         preview or publish paths.
     */
    public boolean isInspectable() {
        return inspectable;
    }

    /**
     * Returns the {@code @page} CSS generated from the page geometry options,
     * or {@code null} if none are set.
     */
    String toPageCss() {
        return PageCssBuilder.build(paperSize, margin, headerContent, footerContent);
    }

    public static final class Builder {

        private String baseUrl;
        private final List<PdfStyleSheet> styleSheets = new ArrayList<>();
        private String paperSize;
        private String margin;
        private String headerContent;
        private String footerContent;
        private Configuration.Conformance conformance;
        private boolean failOnMissingResources;
        private boolean failOnLicenseProblems = true;
        private Boolean javaScriptEnabled;
        private Boolean async;
        private Integer conversionTimeoutSeconds;
        private String title;
        private String author;
        private String outputIntentIdentifier;
        private byte[] outputIntentProfileData;
        private byte[] cmykIccProfileData;
        private Boolean colorConversionEnabled;
        private Configuration.ColorConversionIntent colorConversionIntent;
        private String configurationJson;
        private boolean debug;
        private boolean inspectable;

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder addStyleSheet(PdfStyleSheet styleSheet) {
            this.styleSheets.add(Objects.requireNonNull(styleSheet, "styleSheet"));
            return this;
        }

        public Builder paperSize(String paperSize) {
            this.paperSize = paperSize;
            return this;
        }

        public Builder margin(String margin) {
            this.margin = margin;
            return this;
        }

        public Builder headerContent(String headerContent) {
            this.headerContent = headerContent;
            return this;
        }

        public Builder footerContent(String footerContent) {
            this.footerContent = footerContent;
            return this;
        }

        public Builder conformance(Configuration.Conformance conformance) {
            this.conformance = conformance;
            return this;
        }

        public Builder failOnMissingResources(boolean failOnMissingResources) {
            this.failOnMissingResources = failOnMissingResources;
            return this;
        }

        public Builder failOnLicenseProblems(boolean failOnLicenseProblems) {
            this.failOnLicenseProblems = failOnLicenseProblems;
            return this;
        }

        public Builder javaScriptEnabled(boolean javaScriptEnabled) {
            this.javaScriptEnabled = javaScriptEnabled;
            return this;
        }

        public Builder async(Boolean async) {
            this.async = async;
            return this;
        }

        public Builder conversionTimeoutSeconds(Integer conversionTimeoutSeconds) {
            this.conversionTimeoutSeconds = conversionTimeoutSeconds;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder outputIntentIdentifier(String outputIntentIdentifier) {
            this.outputIntentIdentifier = outputIntentIdentifier;
            return this;
        }

        public Builder outputIntentProfileData(byte[] outputIntentProfileData) {
            // Defensive copy in: the built options are immutable.
            this.outputIntentProfileData =
                    outputIntentProfileData != null ? outputIntentProfileData.clone() : null;
            return this;
        }

        public Builder cmykIccProfileData(byte[] cmykIccProfileData) {
            this.cmykIccProfileData = cmykIccProfileData != null ? cmykIccProfileData.clone() : null;
            return this;
        }

        public Builder colorConversionEnabled(Boolean colorConversionEnabled) {
            this.colorConversionEnabled = colorConversionEnabled;
            return this;
        }

        public Builder colorConversionIntent(Configuration.ColorConversionIntent colorConversionIntent) {
            this.colorConversionIntent = colorConversionIntent;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder inspectable(boolean inspectable) {
            this.inspectable = inspectable;
            return this;
        }

        /**
         * Deep-merges the given raw configuration JSON over any already set on
         * this builder (so an annotation-seeded "view" layer is merged under a
         * later per-call layer). A {@code null} or blank argument is a no-op.
         */
        public Builder configurationJson(String configurationJson) {
            com.fasterxml.jackson.databind.JsonNode merged = RawConfiguration.deepMerge(
                    RawConfiguration.parse(this.configurationJson),
                    RawConfiguration.parse(configurationJson));
            this.configurationJson = merged != null ? merged.toString() : null;
            return this;
        }

        /**
         * Overlays the explicit per-call {@code overrides} onto this builder
         * (used to seed a builder from a view's {@link
         * DefaultPdfReactorConfiguration} and then apply the per-call options on
         * top, so per-call wins). Nullable fields override only when the
         * override is non-null; the per-call stylesheets are appended to the
         * (annotation-default) ones; the per-call configuration JSON is
         * deep-merged over any already set. The error-policy flags are taken
         * from the overrides unconditionally (the annotation has none); the
         * tri-state JavaScript flag is taken only when the per-call value is
         * set, so an annotation-seeded JavaScript choice survives a per-call
         * options object that left it unset.
         */
        Builder overrideWith(PdfRenderOptions overrides) {
            Objects.requireNonNull(overrides, "overrides");
            if (overrides.getBaseUrl() != null) {
                this.baseUrl = overrides.getBaseUrl();
            }
            this.styleSheets.addAll(overrides.getStyleSheets());
            if (overrides.getPaperSize() != null) {
                this.paperSize = overrides.getPaperSize();
            }
            if (overrides.getMargin() != null) {
                this.margin = overrides.getMargin();
            }
            if (overrides.getHeaderContent() != null) {
                this.headerContent = overrides.getHeaderContent();
            }
            if (overrides.getFooterContent() != null) {
                this.footerContent = overrides.getFooterContent();
            }
            if (overrides.getConformance() != null) {
                this.conformance = overrides.getConformance();
            }
            this.failOnMissingResources = overrides.isFailOnMissingResources();
            this.failOnLicenseProblems = overrides.isFailOnLicenseProblems();
            if (overrides.getJavaScriptEnabled() != null) {
                this.javaScriptEnabled = overrides.getJavaScriptEnabled();
            }
            this.debug = overrides.isDebug();
            this.inspectable = overrides.isInspectable();
            if (overrides.getAsync() != null) {
                this.async = overrides.getAsync();
            }
            if (overrides.getConversionTimeoutSeconds() != null) {
                this.conversionTimeoutSeconds = overrides.getConversionTimeoutSeconds();
            }
            if (overrides.getTitle() != null) {
                this.title = overrides.getTitle();
            }
            if (overrides.getAuthor() != null) {
                this.author = overrides.getAuthor();
            }
            if (overrides.getOutputIntentIdentifier() != null) {
                this.outputIntentIdentifier = overrides.getOutputIntentIdentifier();
            }
            if (overrides.getOutputIntentProfileData() != null) {
                this.outputIntentProfileData = overrides.getOutputIntentProfileData();
            }
            if (overrides.getCmykIccProfileData() != null) {
                this.cmykIccProfileData = overrides.getCmykIccProfileData();
            }
            if (overrides.getColorConversionEnabled() != null) {
                this.colorConversionEnabled = overrides.getColorConversionEnabled();
            }
            if (overrides.getColorConversionIntent() != null) {
                this.colorConversionIntent = overrides.getColorConversionIntent();
            }
            if (overrides.getConfigurationJson() != null) {
                configurationJson(overrides.getConfigurationJson());
            }
            return this;
        }

        public PdfRenderOptions build() {
            return new PdfRenderOptions(this);
        }
    }
}
