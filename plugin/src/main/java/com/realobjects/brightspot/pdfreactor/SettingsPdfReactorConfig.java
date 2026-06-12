package com.realobjects.brightspot.pdfreactor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.psddev.dari.util.Settings;
import com.realobjects.pdfreactor.webservice.client.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link PdfReactorConfig} backed by Dari {@link Settings}.
 *
 * <p>All keys live under the {@value #SETTING_PREFIX} prefix; the
 * {@code *_SETTING} constants on this class are the authoritative key
 * inventory, and {@code docs/configuration.md} documents every key with its
 * type, default, and meaning. Exactly one key is required:
 * {@code pdfreactor/serviceUrl} — the PDFreactor Web Service URL.</p>
 *
 * <p>The troubleshooting (debug / inspectable) gate is deliberately not a
 * deploy setting: it is the {@code troubleshootingEnabled} field on the
 * global Sites &amp; Settings record (see {@link PdfReactorSiteSettings}).</p>
 */
public class SettingsPdfReactorConfig implements PdfReactorConfig {

    public static final String SETTING_PREFIX = "pdfreactor";

    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsPdfReactorConfig.class);

    public static final String SERVICE_URL_SETTING = SETTING_PREFIX + "/serviceUrl";
    public static final String API_KEY_SETTING = SETTING_PREFIX + "/apiKey";
    public static final String LICENSE_KEY_SETTING = SETTING_PREFIX + "/licenseKey";
    public static final String CLIENT_TIMEOUT_MILLIS_SETTING = SETTING_PREFIX + "/clientTimeoutMillis";
    public static final String HEALTH_TIMEOUT_MILLIS_SETTING = SETTING_PREFIX + "/healthTimeoutMillis";
    public static final String CONVERSION_TIMEOUT_SECONDS_SETTING = SETTING_PREFIX + "/conversionTimeoutSeconds";
    public static final String ASYNC_POLL_INTERVAL_MILLIS_SETTING = SETTING_PREFIX + "/asyncPollIntervalMillis";
    public static final String ASYNC_DEFAULT_SETTING = SETTING_PREFIX + "/asyncDefault";
    public static final String DEFAULT_USER_STYLE_SHEET_URIS_SETTING = SETTING_PREFIX + "/defaultUserStyleSheetUris";
    public static final String BASE_URL_SETTING = SETTING_PREFIX + "/baseUrl";
    public static final String LOG_LEVEL_SETTING = SETTING_PREFIX + "/logLevel";
    public static final String OUTPUT_INTENT_IDENTIFIER_SETTING = SETTING_PREFIX + "/outputIntentIdentifier";
    public static final String OUTPUT_INTENT_PROFILE_URI_SETTING = SETTING_PREFIX + "/outputIntentProfileUri";
    public static final String CMYK_ICC_PROFILE_URI_SETTING = SETTING_PREFIX + "/cmykIccProfileUri";
    public static final String COLOR_CONVERSION_ENABLED_SETTING = SETTING_PREFIX + "/colorConversionEnabled";
    public static final String COLOR_CONVERSION_INTENT_SETTING = SETTING_PREFIX + "/colorConversionIntent";
    public static final String CONFIGURATION_JSON_SETTING = SETTING_PREFIX + "/configurationJson";
    public static final String CONFORMANCE_SETTING = SETTING_PREFIX + "/conformance";
    public static final String JAVA_SCRIPT_ENABLED_SETTING = SETTING_PREFIX + "/javaScriptEnabled";
    /** Max concurrent publish-PDF conversions (default 3). See {@code PdfPublishAutomation}. */
    public static final String PUBLISH_CONCURRENCY_SETTING = SETTING_PREFIX + "/publishConcurrency";
    // Document metadata defaults.
    public static final String CREATOR_SETTING = SETTING_PREFIX + "/creator";
    public static final String SUBJECT_SETTING = SETTING_PREFIX + "/subject";
    public static final String KEYWORDS_SETTING = SETTING_PREFIX + "/keywords";
    // Document features.
    public static final String ADD_BOOKMARKS_SETTING = SETTING_PREFIX + "/addBookmarks";
    public static final String ADD_LINKS_SETTING = SETTING_PREFIX + "/addLinks";
    public static final String ADD_TAGS_SETTING = SETTING_PREFIX + "/addTags";
    /** Validate output conformance — global/deploy-time only. */
    public static final String VALIDATE_CONFORMANCE_SETTING = SETTING_PREFIX + "/validateConformance";
    // Curated viewer preferences.
    public static final String VIEWER_PAGE_LAYOUT_SETTING = SETTING_PREFIX + "/viewerPageLayout";
    public static final String VIEWER_FIT_WINDOW_SETTING = SETTING_PREFIX + "/viewerFitWindow";
    public static final String VIEWER_DISPLAY_DOC_TITLE_SETTING = SETTING_PREFIX + "/viewerDisplayDocTitle";

    @Override
    public String getServiceUrl() {
        return Settings.getOrDefault(String.class, SERVICE_URL_SETTING, null);
    }

    @Override
    public String getApiKey() {
        return Settings.getOrDefault(String.class, API_KEY_SETTING, null);
    }

    @Override
    public String getLicenseKey() {
        return Settings.getOrDefault(String.class, LICENSE_KEY_SETTING, null);
    }

    @Override
    public int getClientTimeoutMillis() {
        return Settings.getOrDefault(int.class, CLIENT_TIMEOUT_MILLIS_SETTING,
                PdfReactorConfig.super.getClientTimeoutMillis());
    }

    @Override
    public int getHealthTimeoutMillis() {
        return Settings.getOrDefault(int.class, HEALTH_TIMEOUT_MILLIS_SETTING,
                PdfReactorConfig.super.getHealthTimeoutMillis());
    }

    @Override
    public int getConversionTimeoutSeconds() {
        return Settings.getOrDefault(int.class, CONVERSION_TIMEOUT_SECONDS_SETTING,
                PdfReactorConfig.super.getConversionTimeoutSeconds());
    }

    @Override
    public long getAsyncPollIntervalMillis() {
        return Settings.getOrDefault(long.class, ASYNC_POLL_INTERVAL_MILLIS_SETTING,
                PdfReactorConfig.super.getAsyncPollIntervalMillis());
    }

    @Override
    public boolean isAsyncDefault() {
        return Settings.getOrDefault(boolean.class, ASYNC_DEFAULT_SETTING,
                PdfReactorConfig.super.isAsyncDefault());
    }

    @Override
    public List<String> getDefaultUserStyleSheetUris() {
        String uris = Settings.getOrDefault(String.class, DEFAULT_USER_STYLE_SHEET_URIS_SETTING, null);
        if (uris == null || uris.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(uris.split(","))
                .map(String::trim)
                .filter(uri -> !uri.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public String getBaseUrl() {
        return Settings.getOrDefault(String.class, BASE_URL_SETTING, null);
    }

    @Override
    public Configuration.LogLevel getLogLevel() {
        String logLevel = Settings.getOrDefault(String.class, LOG_LEVEL_SETTING, null);
        if (logLevel == null) {
            return PdfReactorConfig.super.getLogLevel();
        }
        // The log level does not affect output bytes, so a typo must never
        // abort an otherwise-valid conversion: warn and fall back to the
        // default rather than throwing (unlike the output-affecting enums).
        try {
            return Configuration.LogLevel.valueOf(logLevel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            Configuration.LogLevel fallback = PdfReactorConfig.super.getLogLevel();
            LOGGER.warn("Ignoring invalid [{}] setting \"{}\"; using {}. Valid values: {}.",
                    LOG_LEVEL_SETTING, logLevel, fallback,
                    Arrays.toString(Configuration.LogLevel.values()));
            return fallback;
        }
    }

    @Override
    public String getOutputIntentIdentifier() {
        return trimToNull(Settings.getOrDefault(String.class, OUTPUT_INTENT_IDENTIFIER_SETTING, null));
    }

    @Override
    public byte[] getOutputIntentProfileData() {
        return IccProfiles.read(Settings.getOrDefault(String.class, OUTPUT_INTENT_PROFILE_URI_SETTING, null));
    }

    @Override
    public byte[] getCmykIccProfileData() {
        return IccProfiles.read(Settings.getOrDefault(String.class, CMYK_ICC_PROFILE_URI_SETTING, null));
    }

    @Override
    public Boolean getColorConversionEnabled() {
        return Settings.getOrDefault(Boolean.class, COLOR_CONVERSION_ENABLED_SETTING, null);
    }

    @Override
    public Configuration.ColorConversionIntent getColorConversionIntent() {
        String intent = trimToNull(Settings.getOrDefault(String.class, COLOR_CONVERSION_INTENT_SETTING, null));
        return intent != null
                ? parseEnum(Configuration.ColorConversionIntent.class, intent, COLOR_CONVERSION_INTENT_SETTING)
                : null;
    }

    @Override
    public String getConfigurationJson() {
        return trimToNull(Settings.getOrDefault(String.class, CONFIGURATION_JSON_SETTING, null));
    }

    @Override
    public Configuration.Conformance getConformance() {
        String conformance = trimToNull(Settings.getOrDefault(String.class, CONFORMANCE_SETTING, null));
        return conformance != null
                ? parseEnum(Configuration.Conformance.class, conformance, CONFORMANCE_SETTING)
                : null;
    }

    @Override
    public Boolean getJavaScriptEnabled() {
        return Settings.getOrDefault(Boolean.class, JAVA_SCRIPT_ENABLED_SETTING, null);
    }

    @Override
    public String getCreator() {
        return trimToNull(Settings.getOrDefault(String.class, CREATOR_SETTING, null));
    }

    @Override
    public String getSubject() {
        return trimToNull(Settings.getOrDefault(String.class, SUBJECT_SETTING, null));
    }

    @Override
    public String getKeywords() {
        return trimToNull(Settings.getOrDefault(String.class, KEYWORDS_SETTING, null));
    }

    @Override
    public Boolean getAddBookmarks() {
        return Settings.getOrDefault(Boolean.class, ADD_BOOKMARKS_SETTING, null);
    }

    @Override
    public Boolean getAddLinks() {
        return Settings.getOrDefault(Boolean.class, ADD_LINKS_SETTING, null);
    }

    @Override
    public Boolean getAddTags() {
        return Settings.getOrDefault(Boolean.class, ADD_TAGS_SETTING, null);
    }

    @Override
    public Boolean getValidateConformance() {
        return Settings.getOrDefault(Boolean.class, VALIDATE_CONFORMANCE_SETTING, null);
    }

    @Override
    public PdfViewerPageLayout getViewerPageLayout() {
        String layout = trimToNull(Settings.getOrDefault(String.class, VIEWER_PAGE_LAYOUT_SETTING, null));
        return layout != null
                ? parseEnum(PdfViewerPageLayout.class, layout, VIEWER_PAGE_LAYOUT_SETTING)
                : null;
    }

    @Override
    public Boolean getViewerFitWindow() {
        return Settings.getOrDefault(Boolean.class, VIEWER_FIT_WINDOW_SETTING, null);
    }

    @Override
    public Boolean getViewerDisplayDocTitle() {
        return Settings.getOrDefault(Boolean.class, VIEWER_DISPLAY_DOC_TITLE_SETTING, null);
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * Parses an output-affecting enum setting, failing clean: an invalid
     * value produces a {@link PdfReactorException} naming the setting and the
     * valid values (rather than a raw {@code IllegalArgumentException} that
     * surfaces as a 500). Used for {@code conformance} and
     * {@code colorConversionIntent}, where a wrong value would otherwise
     * silently produce the wrong print artifact.
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String raw, String settingName) {
        try {
            return Enum.valueOf(enumClass, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new PdfReactorException("Invalid [" + settingName + "] setting \"" + raw
                    + "\". Valid values: " + Arrays.toString(enumClass.getEnumConstants()) + ".");
        }
    }
}
