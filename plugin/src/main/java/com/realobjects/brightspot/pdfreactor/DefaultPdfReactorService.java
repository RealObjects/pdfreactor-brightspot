package com.realobjects.brightspot.pdfreactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.psddev.cms.db.PageFilter;
import com.psddev.cms.view.PageEntryView;
import com.psddev.cms.view.PreviewEntryView;
import com.psddev.cms.view.ViewModel;
import com.psddev.dari.web.WebRequest;
import com.realobjects.brightspot.pdfreactor.render.HtmlSource;
import com.realobjects.brightspot.pdfreactor.render.InRequestHtmlSource;
import com.realobjects.brightspot.pdfreactor.render.PermalinkHtmlSource;
import com.realobjects.brightspot.pdfreactor.render.RenderedHtml;
import com.realobjects.pdfreactor.webservice.client.Configuration;
import com.realobjects.pdfreactor.webservice.client.PDFreactor;
import com.realobjects.pdfreactor.webservice.client.PDFreactorWebserviceException;
import com.realobjects.pdfreactor.webservice.client.Progress;
import com.realobjects.pdfreactor.webservice.client.Result;
import com.realobjects.pdfreactor.webservice.client.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link PdfReactorService} talking to the PDFreactor Web Service
 * through the Java client.
 *
 * <p>Synchronous conversions use {@link PDFreactor#convert(Configuration)};
 * asynchronous conversions use {@link PDFreactor#convertAsync(Configuration)}
 * followed by progress polling and document retrieval. Every conversion
 * attaches a content observer so missing resources and resource connections
 * are reported in the {@link PdfDiagnostics}.</p>
 */
public class DefaultPdfReactorService implements PdfReactorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPdfReactorService.class);

    private final PdfReactorConfig config;
    private final PDFreactor client;
    private final PDFreactor probeClient;
    private final HtmlSource requestHtmlSource;
    private final HtmlSource permalinkHtmlSource;

    /**
     * Creates a service reading configuration from Dari {@code Settings}.
     */
    public DefaultPdfReactorService() {
        this(new SettingsPdfReactorConfig());
    }

    /**
     * Creates a service with the given configuration provider. The
     * conversion client and the short-timeout health/license probe client are
     * separate instances, so a probe never mutates the timeout of an in-flight
     * conversion.
     */
    public DefaultPdfReactorService(PdfReactorConfig config) {
        this(config,
                createClient(config, configOrThrow(config).getClientTimeoutMillis()),
                createClient(config, config.getHealthTimeoutMillis()),
                new InRequestHtmlSource(), new PermalinkHtmlSource());
    }

    /**
     * Creates a service with an externally supplied client (used by tests).
     * The same client serves as both the conversion and the probe client.
     */
    public DefaultPdfReactorService(PdfReactorConfig config, PDFreactor client) {
        this(config, client, client, new InRequestHtmlSource(), new PermalinkHtmlSource());
    }

    /**
     * Creates a service with externally supplied client and HTML sources
     * (used by tests). The same client serves as both conversion and probe.
     *
     * @param requestHtmlSource Used by {@link #renderContent} within a web
     *        request (editor preview / tool pages).
     * @param permalinkHtmlSource Used by {@link #renderContent} outside a
     *        web request (background tasks).
     */
    public DefaultPdfReactorService(
            PdfReactorConfig config,
            PDFreactor client,
            HtmlSource requestHtmlSource,
            HtmlSource permalinkHtmlSource) {

        this(config, client, client, requestHtmlSource, permalinkHtmlSource);
    }

    private DefaultPdfReactorService(
            PdfReactorConfig config,
            PDFreactor client,
            PDFreactor probeClient,
            HtmlSource requestHtmlSource,
            HtmlSource permalinkHtmlSource) {

        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
        this.probeClient = Objects.requireNonNull(probeClient, "probeClient");
        this.requestHtmlSource = Objects.requireNonNull(requestHtmlSource, "requestHtmlSource");
        this.permalinkHtmlSource = Objects.requireNonNull(permalinkHtmlSource, "permalinkHtmlSource");
    }

    private static PdfReactorConfig configOrThrow(PdfReactorConfig config) {
        return Objects.requireNonNull(config, "config");
    }

    private static PDFreactor createClient(PdfReactorConfig config, int timeoutMillis) {
        Objects.requireNonNull(config, "config");
        String serviceUrl = config.getServiceUrl();
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            throw new PdfReactorException(
                    "PDFreactor is not configured: missing the ["
                            + SettingsPdfReactorConfig.SERVICE_URL_SETTING
                            + "] setting.");
        }

        PDFreactor client = new PDFreactor(serviceUrl.trim());
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            client.setApiKey(apiKey.trim());
        }
        client.setTimeout(timeoutMillis);
        return client;
    }

    @Override
    public PdfResult renderHtml(String html, PdfRenderOptions options) {
        return renderHtml(html, options, null);
    }

    @Override
    public PdfResult renderContent(Object content, PdfRenderOptions options) {
        Objects.requireNonNull(content, "content");

        // Seed the conversion options from the content's view
        // @DefaultPdfReactorConfiguration (page geometry, conformance,
        // print stylesheets, ICC, raw JSON), then overlay the explicit
        // per-call options so per-call wins. Without this the annotation is
        // inert — the only thing that translates paper size/margins/
        // header-footer into the conversion.
        PdfRenderOptions effectiveOptions = options != null
                ? options
                : PdfRenderOptions.builder().build();
        Class<?> viewModelClass = resolveViewModelClass(content);
        if (viewModelClass != null
                && viewModelClass.getAnnotation(DefaultPdfReactorConfiguration.class) != null) {
            effectiveOptions = PdfRenderOptions.fromAnnotated(viewModelClass)
                    .overrideWith(effectiveOptions)
                    .build();
        }

        // In a web request (editor preview, tool pages) render in-request
        // through the View System; otherwise (background tasks) fetch the
        // published permalink over internal HTTP.
        HtmlSource source = WebRequest.isAvailable()
                ? requestHtmlSource
                : permalinkHtmlSource;
        RenderedHtml rendered = source.render(content);
        return renderHtml(rendered.getHtml(), effectiveOptions, rendered.getBaseUrl());
    }

    /**
     * Resolves the View System ViewModel class that renders the given content,
     * so its {@link DefaultPdfReactorConfiguration} (if any) can seed the
     * conversion options. Mirrors the page/preview resolution order used by the
     * preview type's {@code shouldDisplay}. Returns {@code null} when no
     * ViewModel is bound or the lookup is unavailable (e.g. outside a fully
     * initialized View System) — annotation seeding is then skipped and the
     * conversion proceeds with the per-call options. Overridable in tests.
     */
    // Same deprecated-but-only view lookup the preview type uses in
    // shouldDisplay (no non-deprecated replacement on this 5.0 BOM).
    @SuppressWarnings("deprecation")
    protected Class<?> resolveViewModelClass(Object content) {
        try {
            Class<?> viewModelClass = ViewModel.findViewModelClass(PageFilter.PAGE_VIEW_TYPE, content);
            if (viewModelClass == null) {
                viewModelClass = ViewModel.findViewModelClass(PageFilter.PREVIEW_VIEW_TYPE, content);
            }
            if (viewModelClass == null) {
                viewModelClass = ViewModel.findViewModelClass(PageEntryView.class, content);
            }
            if (viewModelClass == null) {
                viewModelClass = ViewModel.findViewModelClass(PreviewEntryView.class, content);
            }
            return viewModelClass;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private PdfResult renderHtml(String html, PdfRenderOptions options, String fallbackBaseUrl) {
        Objects.requireNonNull(html, "html");
        PdfRenderOptions effectiveOptions = options != null ? options : PdfRenderOptions.builder().build();
        Configuration configuration = buildConfiguration(html, effectiveOptions, fallbackBaseUrl);
        boolean async = effectiveOptions.getAsync() != null
                ? effectiveOptions.getAsync()
                : config.isAsyncDefault();

        try {
            Result result = async
                    ? convertAsync(configuration, effectiveOptions)
                    : client.convert(configuration);
            return toPdfResult(result);

        } catch (PDFreactorWebserviceException error) {
            throw new PdfReactorException(
                    "PDFreactor conversion failed: " + error.getMessage(),
                    error,
                    PdfDiagnostics.fromResult(error.getResult()));
        }
    }

    @Override
    public PdfServiceHealth checkHealth() {
        // Use the dedicated probe client, already pinned to the short health
        // timeout at construction — so this never mutates the conversion
        // client's timeout and a concurrent conversion is
        // unaffected. Verified on the e2e stack to bound a black-holed TCP
        // connect, not just a slow reply (a 1.5s timeout returns in ~1.5s
        // against a non-routable host); the widget's background refresh is a
        // further backstop.
        try {
            probeClient.getStatus();
            Version version = probeClient.getVersion();
            return PdfServiceHealth.up(marketingVersion(version));

        } catch (PDFreactorWebserviceException | RuntimeException error) {
            // The widget shows only a short, friendly line; the full
            // client message (host, stack, java.net cause) stays here in the
            // server log for whoever is actually debugging the connection.
            LOGGER.warn("PDFreactor service health check failed.", error);
            return PdfServiceHealth.down(error.getMessage());
        }
    }

    /**
     * The marketing version {@code "major.minor.micro"} (e.g. {@code "12.6.0"}),
     * deliberately without the build number that {@link Version#getText()}
     * carries (e.g. {@code "12.6.0.18136"}): the health widget shows the release
     * version, not the build. Falls back to the trimmed full text when the
     * component fields are unpopulated (all zero), and to {@code null} when no
     * version is available.
     */
    static String marketingVersion(Version version) {
        if (version == null) {
            return null;
        }
        if (version.getMajor() > 0 || version.getMinor() > 0 || version.getMicro() > 0) {
            return version.getMajor() + "." + version.getMinor() + "." + version.getMicro();
        }
        String text = version.getText();
        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    /** Minimal document for the license probe — no resources, no scripting. */
    private static final String LICENSE_PROBE_DOCUMENT = "<html><body></body></html>";

    @Override
    public PdfLicenseState checkLicense() {
        // Detect evaluation mode without a license-status accessor (the 12.6
        // client exposes none): convert a trivial document with only the
        // LICENSE error policy on. PDFreactor aborts the conversion when no
        // valid license is installed, so a LICENSE-classified abort means
        // evaluation, a success means licensed, and any other failure is
        // inconclusive. Reuses PdfProblemReport's verified classification so
        // the "License:"-prefixed abort string is recognized the same way the
        // conversion error path recognizes it.
        //
        // Uses the dedicated probe client (pinned to the short health timeout at
        // construction), so it never mutates the conversion client's timeout
        // A license problem aborts fast.
        try {
            Configuration configuration = new Configuration()
                    .setDocument(LICENSE_PROBE_DOCUMENT)
                    .setLogLevel(config.getLogLevel())
                    .setJavaScriptSettings(new Configuration.JavaScriptSettings().setDisabled(true))
                    .setErrorPolicies(Configuration.ErrorPolicy.LICENSE);
            String licenseKey = config.getLicenseKey();
            if (licenseKey != null && !licenseKey.trim().isEmpty()) {
                configuration.setLicenseKey(licenseKey);
            }
            probeClient.convert(configuration);
            return PdfLicenseState.LICENSED;

        } catch (PDFreactorWebserviceException error) {
            PdfReactorException classified = new PdfReactorException(
                    "PDFreactor conversion failed: " + error.getMessage(),
                    error,
                    PdfDiagnostics.fromResult(error.getResult()));
            return PdfProblemReport.of(classified).getKind() == PdfProblemReport.Kind.LICENSE
                    ? PdfLicenseState.EVALUATION
                    : PdfLicenseState.UNKNOWN;

        } catch (RuntimeException error) {
            return PdfLicenseState.UNKNOWN;
        }
    }

    /**
     * Assembles the PDFreactor {@link Configuration} for one conversion.
     */
    // Configuration#setBaseUrl is marked deprecated since PDFreactor 11, but
    // the 12.6 web-service client offers no replacement (its own javadoc
    // points back to setBaseUrl); it remains the only way to set the
    // document base URL when the document is passed as source code.
    @SuppressWarnings("deprecation")
    Configuration buildConfiguration(String html, PdfRenderOptions options, String fallbackBaseUrl) {
        Configuration.ContentObserver observer = new Configuration.ContentObserver()
                .setMissingResources(true)
                .setConnections(true);
        Configuration configuration = new Configuration()
                .setDocument(html)
                .setLogLevel(config.getLogLevel())
                .setContentObserver(observer);

        // Values the JSON pass-through MAY override are set BEFORE the merge
        // (base URL, license key, conversion timeout): they are not owned by a
        // form setting, so an advanced pass-through is allowed to change them.
        String baseUrl = options.getBaseUrl() != null ? options.getBaseUrl()
                : fallbackBaseUrl != null ? fallbackBaseUrl
                : config.getBaseUrl();
        if (baseUrl != null) {
            configuration.setBaseUrl(baseUrl);
        }

        String licenseKey = config.getLicenseKey();
        if (licenseKey != null && !licenseKey.trim().isEmpty()) {
            configuration.setLicenseKey(licenseKey);
        }

        int conversionTimeout = options.getConversionTimeoutSeconds() != null
                ? options.getConversionTimeoutSeconds()
                : config.getConversionTimeoutSeconds();
        if (conversionTimeout > 0) {
            configuration.setConversionTimeout(conversionTimeout);
        }

        // Full-configuration pass-through: deep-merge global+site then
        // view+call raw JSON and apply onto the assembled configuration, so
        // any client Configuration property is reachable without a release.
        // Applied HERE — before the owned fields below — so that every value a
        // form setting or plugin decision owns wins over it: the pass-through
        // may only set Configuration properties the plugin does not own.
        RawConfiguration.merge(configuration, config.getConfigurationJson(), options.getConfigurationJson());

        // UI/plugin-owned fields, re-enforced AFTER the merge so a CMS-editable
        // JSON pass-through can never override them — the document being
        // converted, the diagnostics content observer, the per-path error
        // policies, conformance, color management, JavaScript, the document
        // title/author, and the user stylesheets. (Earlier the JS-off
        // guarantee and these settings were applied before the merge, so the
        // pass-through silently won — the opposite of the intended precedence.)
        configuration.setDocument(html);
        configuration.setContentObserver(observer);

        // An empty array clears any error policies the JSON tried to set.
        List<Configuration.ErrorPolicy> errorPolicies = new ArrayList<>();
        if (options.isFailOnLicenseProblems()) {
            errorPolicies.add(Configuration.ErrorPolicy.LICENSE);
        }
        if (options.isFailOnMissingResources()) {
            errorPolicies.add(Configuration.ErrorPolicy.MISSING_RESOURCE);
        }
        configuration.setErrorPolicies(errorPolicies.toArray(new Configuration.ErrorPolicy[0]));

        // Effective conformance: per-call options first, then config
        // (site over global). When nothing is set anywhere, leave whatever the
        // pass-through configured (else plain PDF, the client default).
        Configuration.Conformance conformance = options.getConformance() != null
                ? options.getConformance()
                : config.getConformance();
        if (conformance != null) {
            configuration.setConformance(conformance);
        }

        applyColorManagement(configuration, options);

        // JavaScript: per-call options first, then config (site over global),
        // else the built-in default — ON, matching normal PDFreactor behavior.
        // Re-enforced after the merge and the pass-through, so a pass-through
        // {@code javaScriptSettings} cannot silently change the resolved value.
        boolean javaScriptEnabled = options.getJavaScriptEnabled() != null
                ? options.getJavaScriptEnabled()
                : config.getJavaScriptEnabled() != null
                        ? config.getJavaScriptEnabled()
                        : true;
        configuration.setJavaScriptSettings(new Configuration.JavaScriptSettings()
                .setDisabled(!javaScriptEnabled));

        if (options.getTitle() != null) {
            configuration.setTitle(options.getTitle());
        }
        if (options.getAuthor() != null) {
            configuration.setAuthor(options.getAuthor());
        }

        // Document metadata defaults and features, from the resolved
        // config (site over global). Applied after the merge so a form setting
        // wins over the JSON pass-through; left untouched when unset.
        if (config.getCreator() != null) {
            configuration.setCreator(config.getCreator());
        }
        if (config.getSubject() != null) {
            configuration.setSubject(config.getSubject());
        }
        if (config.getKeywords() != null) {
            configuration.setKeywords(config.getKeywords());
        }
        Map<String, String> customProps = config.getCustomDocumentProperties();
        if (customProps != null && !customProps.isEmpty()) {
            List<Configuration.KeyValuePair> pairs = new ArrayList<>();
            // Sorted by key for a deterministic order matching the fingerprint.
            new TreeMap<>(customProps).forEach((key, value) ->
                    pairs.add(new Configuration.KeyValuePair(key, value)));
            configuration.setCustomDocumentProperties(
                    pairs.toArray(new Configuration.KeyValuePair[0]));
        }
        if (config.getAddBookmarks() != null) {
            configuration.setAddBookmarks(config.getAddBookmarks());
        }
        if (config.getAddLinks() != null) {
            configuration.setAddLinks(config.getAddLinks());
        }
        if (config.getAddTags() != null) {
            configuration.setAddTags(config.getAddTags());
        }
        if (config.getValidateConformance() != null) {
            configuration.setValidateConformance(config.getValidateConformance());
        }

        // Curated viewer preferences: assemble the array from the resolved
        // config (page layout + fit-window + display-doc-title). Set only when
        // any is configured, leaving the client default otherwise.
        List<Configuration.ViewerPreferences> viewerPrefs = new ArrayList<>();
        if (config.getViewerPageLayout() != null) {
            viewerPrefs.add(config.getViewerPageLayout().toClient());
        }
        if (Boolean.TRUE.equals(config.getViewerFitWindow())) {
            viewerPrefs.add(Configuration.ViewerPreferences.FIT_WINDOW);
        }
        if (Boolean.TRUE.equals(config.getViewerDisplayDocTitle())) {
            viewerPrefs.add(Configuration.ViewerPreferences.DISPLAY_DOC_TITLE);
        }
        if (!viewerPrefs.isEmpty()) {
            configuration.setViewerPreferences(
                    viewerPrefs.toArray(new Configuration.ViewerPreferences[0]));
        }

        // User stylesheets: the configured (plugin/UI-owned) sheets always
        // apply; any the JSON pass-through supplied are appended AFTER them, so
        // the escape hatch may add sheets but never drop the configured ones.
        // Later sheets win in the CSS cascade, so an appended sheet can still
        // override a configured rule.
        List<Configuration.Resource> styleSheets = new ArrayList<>();
        for (String uri : config.getDefaultUserStyleSheetUris()) {
            styleSheets.add(PdfStyleSheet.fromUri(uri).toResource());
        }
        for (PdfStyleSheet styleSheet : options.getStyleSheets()) {
            styleSheets.add(styleSheet.toResource());
        }
        String pageCss = options.toPageCss();
        if (pageCss != null) {
            styleSheets.add(PdfStyleSheet.inline(pageCss).toResource());
        }
        List<Configuration.Resource> jsonStyleSheets = configuration.getUserStyleSheets();
        if (jsonStyleSheets != null) {
            styleSheets.addAll(jsonStyleSheets);
        }
        if (!styleSheets.isEmpty()) {
            configuration.setUserStyleSheets(styleSheets.toArray(new Configuration.Resource[0]));
        }

        // Troubleshooting builds, set after the pass-through (and the owned
        // fields) so the explicit per-conversion request wins and only when
        // asked. debug attaches the intermediate documents, logs, and resources
        // to the PDF; inspectable embeds the rendered DOM for the PDFreactor
        // Inspector. The preview and publish paths never set these (their
        // options leave both off), and a JSON pass-through cannot turn them on
        // past the administrator gate (see PdfReactorConfigs.troubleshootingActive):
        // when not requested they are explicitly cleared, so a pass-through
        // {@code debugSettings}/{@code inspectableSettings} is overridden too.
        configuration.setDebugSettings(options.isDebug()
                ? new Configuration.DebugSettings().setAll(true)
                : null);
        configuration.setInspectableSettings(options.isInspectable()
                ? new Configuration.InspectableSettings().setEnabled(true)
                : null);

        // Echo the output-affecting effective config at DEBUG so a developer
        // using the raw-config pass-through can confirm what actually applied,
        // without trial and error. Deliberately omits the document and ICC
        // bytes (large blobs); a {@code licenseKey} placed in the pass-through
        // JSON (an ordinary Configuration property) is redacted, so the echo
        // carries no secrets.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Effective PDFreactor conversion config: conformance={}, baseUrl={},"
                            + " userStyleSheets={}, javaScriptDisabled={}, errorPolicies={},"
                            + " rawConfigJson(global+site)={}, rawConfigJson(view+call)={}",
                    configuration.getConformance(), baseUrl, styleSheets.size(),
                    !javaScriptEnabled, errorPolicies,
                    redactLicenseKey(config.getConfigurationJson()),
                    redactLicenseKey(options.getConfigurationJson()));
        }

        return configuration;
    }

    /**
     * Removes a top-level {@code licenseKey} from a pass-through JSON string for
     * the DEBUG echo, so a license key set via the pass-through is not logged.
     */
    private static Object redactLicenseKey(String json) {
        if (json == null) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = RawConfiguration.parse(json);
            if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("licenseKey");
                return node;
            }
            return json;
        } catch (RuntimeException unparseable) {
            return "[unparseable]";
        }
    }

    /**
     * Applies ICC / color-management settings with per-call options taking
     * precedence over the (already site-over-global) config. ICC profile
     * bytes are resolved server-side and embedded in the request, so the
     * service never fetches them.
     */
    private void applyColorManagement(Configuration configuration, PdfRenderOptions options) {
        String outputIntentIdentifier = options.getOutputIntentIdentifier() != null
                ? options.getOutputIntentIdentifier()
                : config.getOutputIntentIdentifier();
        byte[] outputIntentData = options.getOutputIntentProfileData() != null
                ? options.getOutputIntentProfileData()
                : config.getOutputIntentProfileData();
        if (outputIntentIdentifier != null || outputIntentData != null) {
            Configuration.OutputIntent outputIntent = new Configuration.OutputIntent();
            if (outputIntentIdentifier != null) {
                outputIntent.setIdentifier(outputIntentIdentifier);
            }
            if (outputIntentData != null) {
                outputIntent.setData(outputIntentData);
            }
            configuration.setOutputIntent(outputIntent);
        }

        byte[] cmykData = options.getCmykIccProfileData() != null
                ? options.getCmykIccProfileData()
                : config.getCmykIccProfileData();
        Boolean conversionEnabled = options.getColorConversionEnabled() != null
                ? options.getColorConversionEnabled()
                : config.getColorConversionEnabled();
        Configuration.ColorConversionIntent intent = options.getColorConversionIntent() != null
                ? options.getColorConversionIntent()
                : config.getColorConversionIntent();
        if (cmykData != null || conversionEnabled != null || intent != null) {
            Configuration.ColorSpaceSettings colorSpace = new Configuration.ColorSpaceSettings();
            if (cmykData != null) {
                colorSpace.setCmykIccProfile(new Configuration.Resource().setData(cmykData));
            }
            if (conversionEnabled != null) {
                colorSpace.setConversionEnabled(conversionEnabled);
            }
            if (intent != null) {
                colorSpace.setColorConversionIntent(intent);
            }
            configuration.setColorSpaceSettings(colorSpace);
        }
    }

    private Result convertAsync(Configuration configuration, PdfRenderOptions options)
            throws PDFreactorWebserviceException {

        String documentId = client.convertAsync(configuration);

        int conversionTimeout = options.getConversionTimeoutSeconds() != null
                ? options.getConversionTimeoutSeconds()
                : config.getConversionTimeoutSeconds();

        // Allow some slack beyond the service-side conversion timeout so the
        // service can abort first and report a proper error.
        long pollBudgetMillis = conversionTimeout > 0
                ? (conversionTimeout + 60L) * 1_000L
                : 30L * 60L * 1_000L;
        long deadline = System.currentTimeMillis() + pollBudgetMillis;
        long pollInterval = Math.max(50L, config.getAsyncPollIntervalMillis());

        while (System.currentTimeMillis() < deadline) {
            Progress progress = client.getProgress(documentId);
            if (progress != null && progress.isFinished()) {
                Result result = client.getDocument(documentId);
                // Delete the converted document on success too (not only on the
                // timeout path) so documents do not accumulate on the service
                // until expiry. Best effort: it expires server-side anyway.
                try {
                    client.deleteDocument(documentId);
                } catch (PDFreactorWebserviceException | RuntimeException suppressed) {
                    // Ignore — the document expires on the service side.
                }
                return result;
            }
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new PdfReactorException(
                        "Interrupted while waiting for asynchronous conversion [" + documentId + "].",
                        interrupted);
            }
        }

        try {
            client.deleteDocument(documentId);
        } catch (PDFreactorWebserviceException | RuntimeException suppressed) {
            // Best effort: the document expires on the service side anyway.
        }
        throw new PdfReactorException(
                "Asynchronous conversion [" + documentId + "] did not finish within "
                        + pollBudgetMillis + "ms.");
    }

    private PdfResult toPdfResult(Result result) {
        if (result == null || result.getDocument() == null) {
            throw new PdfReactorException(
                    "PDFreactor returned no document.",
                    null,
                    PdfDiagnostics.fromResult(result));
        }
        return new PdfResult(
                result.getDocument(),
                result.getContentType(),
                result.getNumberOfPages(),
                PdfDiagnostics.fromResult(result));
    }
}
