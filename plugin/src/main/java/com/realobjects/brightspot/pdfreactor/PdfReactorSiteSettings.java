package com.realobjects.brightspot.pdfreactor;

import java.util.ArrayList;
import java.util.List;

import com.psddev.cms.db.Site;
import com.psddev.cms.db.SiteSettings;
import com.psddev.cms.db.ToolUi;
import com.psddev.cms.ui.form.DynamicNoteMethod;
import com.psddev.dari.db.Modification;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.db.State;
import com.psddev.dari.html.Node;
import com.psddev.dari.util.Settings;

import static com.psddev.dari.html.Nodes.DIV;

/**
 * Per-site PDFreactor configuration (Sites &amp; Settings → CMS tab →
 * PDFreactor cluster), following the {@link
 * com.realobjects.brightspot.pdfreactor.publish.PdfReactorPublishSettings}
 * pattern. {@code SiteSettings.get(site, …)} resolves with a {@code null}
 * site meaning the global Sites &amp; Settings record, so the same fields
 * serve both per-site and global overrides.
 *
 * <p>Every field is nullable: an unset value <em>inherits</em> the global
 * {@link SettingsPdfReactorConfig} ({@code pdfreactor/*}) value.
 * {@link SitePdfReactorConfig} layers these over that global config, and the
 * factory {@link PdfReactorConfigs} resolves the right config for a piece of
 * content.</p>
 */
@Recordable.FieldInternalNamePrefix("pdfreactor.")
public class PdfReactorSiteSettings extends Modification<SiteSettings> {

    // All PDFreactor settings live in ONE cluster so they read as a single
    // collapsible "PDFreactor" section rather than several sibling sections.
    // The document-shaping settings are grouped inside it by section headings
    // (see fieldNote): a field note renders below its field, so a group's
    // heading is carried as a trailing row on the note of the field that
    // PRECEDES the group, placing it just above the group's first field. Field
    // declaration order is therefore the section order — engine/connection
    // -adjacent fields first (no heading, directly under the section title),
    // then the headed groups: Document Metadata, Document Features, Viewer
    // Preferences, Color Management, Advanced.
    static final String CLUSTER = "PDFreactor";
    static final String TAB = "CMS";

    // The service URL and API key are deliberately NOT editable here:
    // connection/secret config is deploy-time only, sourced from the global
    // Dari Settings (pdfreactor/serviceUrl, pdfreactor/apiKey). A frontend-
    // editable API key is a credential-exposure risk, and a runtime-editable
    // service URL lets a Tool user repoint conversions at an arbitrary host;
    // neither belongs in the per-site CMS form. SitePdfReactorConfig therefore
    // reads both from the global config only (no per-site override).

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @DynamicNoteMethod("baseUrlNote")
    private String baseUrl;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @DynamicNoteMethod("defaultUserStyleSheetUrisNote")
    private List<String> defaultUserStyleSheetUris;

    // --- Conformance ---

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @DynamicNoteMethod("conformanceNote")
    private Conformance conformance;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Enable JavaScript Processing")
    @DynamicNoteMethod("javaScriptEnabledNote")
    private Boolean javaScriptEnabled;

    // --- Document metadata defaults ---

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @DynamicNoteMethod("creatorNote")
    private String creator;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @DynamicNoteMethod("subjectNote")
    private String subject;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @DynamicNoteMethod("keywordsNote")
    private String keywords;

    // --- Document features ---

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Add Bookmarks")
    @DynamicNoteMethod("addBookmarksNote")
    private Boolean addBookmarks;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Add Links")
    @DynamicNoteMethod("addLinksNote")
    private Boolean addLinks;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Add Tags")
    @DynamicNoteMethod("addTagsNote")
    private Boolean addTags;

    // --- Viewer preferences ---

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Viewer Page Layout")
    @DynamicNoteMethod("viewerPageLayoutNote")
    private PdfViewerPageLayout viewerPageLayout;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Viewer: Fit Window")
    @DynamicNoteMethod("viewerFitWindowNote")
    private Boolean viewerFitWindow;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Viewer: Display Document Title")
    @DynamicNoteMethod("viewerDisplayDocTitleNote")
    private Boolean viewerDisplayDocTitle;

    // --- Color management / ICC ---

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @DynamicNoteMethod("outputIntentIdentifierNote")
    private String outputIntentIdentifier;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Output Intent ICC Profile")
    @DynamicNoteMethod("outputIntentProfileNote")
    private IccProfile outputIntentProfile;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("CMYK ICC Profile")
    @DynamicNoteMethod("cmykIccProfileNote")
    private IccProfile cmykIccProfile;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Enable Automatic Color Conversion")
    @DynamicNoteMethod("colorConversionEnabledNote")
    private Boolean colorConversionEnabled;

    // The color-conversion rendering intent is intentionally NOT a form field:
    // it is rarely changed per site and clutters the cluster. It remains
    // settable deploy-time via pdfreactor/colorConversionIntent and through the
    // configuration-JSON pass-through, and stays part of PdfConfigFingerprint.

    // --- Full-configuration pass-through ---

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.CodeType("application/json")
    @DynamicNoteMethod("configurationJsonNote")
    private String configurationJson;

    // --- Troubleshooting gate ---
    //
    // The master control that allows debug/inspectable PDF builds. Set on the
    // GLOBAL Sites & Settings record it is the master for every site; a site
    // may override it. It only *permits* producing diagnostic builds — the
    // actual per-article toggles live on the content edit form (see
    // HasPdfRenderingData) and appear only when this gate is on. The gate never
    // affects the publish automation (a published PDF is always the normal
    // production build).

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab(TAB)
    @ToolUi.DisplayName("Allow debug/inspectable PDF builds")
    @DynamicNoteMethod("troubleshootingEnabledNote")
    private Boolean troubleshootingEnabled;

    /**
     * Conformance choices mirroring the client's
     * {@code Configuration.Conformance} (mapped by name in
     * {@link SitePdfReactorConfig}), so the editor gets a dropdown without the
     * client type entering the stored data model.
     */
    public enum Conformance {
        PDF, PDFA1A, PDFA1A_PDFUA1, PDFA1B, PDFA2A, PDFA2A_PDFUA1, PDFA2B,
        PDFA2U, PDFA3A, PDFA3A_PDFUA1, PDFA3B, PDFA3U, PDFUA1,
        PDFX1A_2001, PDFX1A_2003, PDFX3_2002, PDFX3_2003, PDFX4, PDFX4P
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<String> getDefaultUserStyleSheetUris() {
        if (defaultUserStyleSheetUris == null) {
            defaultUserStyleSheetUris = new ArrayList<>();
        }
        return defaultUserStyleSheetUris;
    }

    public void setDefaultUserStyleSheetUris(List<String> defaultUserStyleSheetUris) {
        this.defaultUserStyleSheetUris = defaultUserStyleSheetUris;
    }

    public Conformance getConformance() {
        return conformance;
    }

    public void setConformance(Conformance conformance) {
        this.conformance = conformance;
    }

    public Boolean getJavaScriptEnabled() {
        return javaScriptEnabled;
    }

    public void setJavaScriptEnabled(Boolean javaScriptEnabled) {
        this.javaScriptEnabled = javaScriptEnabled;
    }

    public String getOutputIntentIdentifier() {
        return outputIntentIdentifier;
    }

    public void setOutputIntentIdentifier(String outputIntentIdentifier) {
        this.outputIntentIdentifier = outputIntentIdentifier;
    }

    public IccProfile getOutputIntentProfile() {
        return outputIntentProfile;
    }

    public void setOutputIntentProfile(IccProfile outputIntentProfile) {
        this.outputIntentProfile = outputIntentProfile;
    }

    public IccProfile getCmykIccProfile() {
        return cmykIccProfile;
    }

    public void setCmykIccProfile(IccProfile cmykIccProfile) {
        this.cmykIccProfile = cmykIccProfile;
    }

    public Boolean getColorConversionEnabled() {
        return colorConversionEnabled;
    }

    public void setColorConversionEnabled(Boolean colorConversionEnabled) {
        this.colorConversionEnabled = colorConversionEnabled;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Boolean getAddBookmarks() {
        return addBookmarks;
    }

    public void setAddBookmarks(Boolean addBookmarks) {
        this.addBookmarks = addBookmarks;
    }

    public Boolean getAddLinks() {
        return addLinks;
    }

    public void setAddLinks(Boolean addLinks) {
        this.addLinks = addLinks;
    }

    public Boolean getAddTags() {
        return addTags;
    }

    public void setAddTags(Boolean addTags) {
        this.addTags = addTags;
    }

    public PdfViewerPageLayout getViewerPageLayout() {
        return viewerPageLayout;
    }

    public void setViewerPageLayout(PdfViewerPageLayout viewerPageLayout) {
        this.viewerPageLayout = viewerPageLayout;
    }

    public Boolean getViewerFitWindow() {
        return viewerFitWindow;
    }

    public void setViewerFitWindow(Boolean viewerFitWindow) {
        this.viewerFitWindow = viewerFitWindow;
    }

    public Boolean getViewerDisplayDocTitle() {
        return viewerDisplayDocTitle;
    }

    public void setViewerDisplayDocTitle(Boolean viewerDisplayDocTitle) {
        this.viewerDisplayDocTitle = viewerDisplayDocTitle;
    }

    public String getConfigurationJson() {
        return configurationJson;
    }

    public void setConfigurationJson(String configurationJson) {
        this.configurationJson = configurationJson;
    }

    public Boolean getTroubleshootingEnabled() {
        return troubleshootingEnabled;
    }

    public void setTroubleshootingEnabled(Boolean troubleshootingEnabled) {
        this.troubleshootingEnabled = troubleshootingEnabled;
    }

    // --- Field notes ------------------------------------------------------
    //
    // Each field renders a SINGLE plugin-owned note node that the platform
    // places on its own row: the always-shown explanation, followed — only
    // when the field is left blank — by the value currently in effect
    // (inherited). One node, because the Tool lays a field's note siblings out
    // in a flex row that does not give each note the full row width, so a
    // separate static note and dynamic note shared a row on wide viewports;
    // collapsing both texts into one node we own removes that dependency and
    // lets us control their order (explanation first, inherited hint second).
    //
    // The inherited value is the global effective config (the global Sites &
    // Settings record layered over the deploy-time settings, i.e.
    // PdfReactorConfigs.forSite(null)); for a site field left blank, that is
    // exactly what applies. These read no ICC bytes, and inherited-value
    // resolution is wrapped so a global-config problem (e.g. a typo'd enum
    // setting) degrades to "explanation only" rather than breaking the form.

    private Node baseUrlNote() {
        return fieldNote(
                "Overrides the base URL this site's PDFs resolve relative links and"
                        + " assets against. Leave blank to inherit.",
                isBlank(baseUrl) ? inheritedShown(PdfReactorConfig::getBaseUrl) : null);
    }

    private Node defaultUserStyleSheetUrisNote() {
        boolean blank = defaultUserStyleSheetUris == null || defaultUserStyleSheetUris.isEmpty();
        return fieldNote(
                "User stylesheet URIs (typically the print CSS) injected into every"
                        + " conversion for this site. When non-empty, replaces the inherited list.",
                blank ? inheritedShown(c -> String.join(", ", c.getDefaultUserStyleSheetUris())) : null);
    }

    private Node conformanceNote() {
        return fieldNote(
                "Output conformance profile (PDF/A, PDF/UA, PDF/X). Leave blank for"
                        + " plain PDF or to inherit.",
                conformance != null ? null : inheritedShown(c -> {
                    com.realobjects.pdfreactor.webservice.client.Configuration.Conformance v = c.getConformance();
                    return v != null ? v.name() : null;
                }));
    }

    private Node javaScriptEnabledNote() {
        return fieldNote(
                "Whether page JavaScript runs during PDF conversion for this site."
                        + " Leave unset to inherit; the default is on, matching normal"
                        + " PDFreactor behavior.",
                javaScriptEnabled != null ? null : inheritedShown(c -> {
                    Boolean v = c.getJavaScriptEnabled();
                    // Unset everywhere resolves to the built-in default (on); name that
                    // rather than the misleading "(none — plain default)".
                    return v != null ? v.toString() : "on (default)";
                }),
                // Trailing heading: introduces the Document Metadata group below.
                "Document Metadata");
    }

    private Node creatorNote() {
        return fieldNote("Default document Creator (PDF metadata). Leave blank to inherit.",
                isBlank(creator) ? inheritedShown(PdfReactorConfig::getCreator) : null);
    }

    private Node subjectNote() {
        return fieldNote("Default document Subject (PDF metadata). Leave blank to inherit.",
                isBlank(subject) ? inheritedShown(PdfReactorConfig::getSubject) : null);
    }

    private Node keywordsNote() {
        return fieldNote("Default document Keywords (comma-separated PDF metadata). Leave blank to inherit.",
                isBlank(keywords) ? inheritedShown(PdfReactorConfig::getKeywords) : null,
                // Trailing heading: introduces the Document Features group below.
                "Document Features");
    }

    private Node addBookmarksNote() {
        return fieldNote("Include a bookmark outline in the PDF. Leave unset to inherit.",
                addBookmarks != null ? null : inheritedShown(c -> booleanText(c.getAddBookmarks())));
    }

    private Node addLinksNote() {
        return fieldNote("Include hyperlinks in the PDF. Leave unset to inherit.",
                addLinks != null ? null : inheritedShown(c -> booleanText(c.getAddLinks())));
    }

    private Node addTagsNote() {
        return fieldNote("Include accessibility tags (tagged PDF). Leave unset to inherit.",
                addTags != null ? null : inheritedShown(c -> booleanText(c.getAddTags())),
                // Trailing heading: introduces the Viewer Preferences group below.
                "Viewer Preferences");
    }

    private static String booleanText(Boolean value) {
        return value != null ? value.toString() : null;
    }

    private Node viewerPageLayoutNote() {
        return fieldNote("Initial page layout when the PDF opens in a viewer. Leave blank to inherit.",
                viewerPageLayout != null ? null : inheritedShown(c -> {
                    PdfViewerPageLayout v = c.getViewerPageLayout();
                    return v != null ? v.name() : null;
                }));
    }

    private Node viewerFitWindowNote() {
        return fieldNote("Resize the viewer window to fit the first page. Leave unset to inherit.",
                viewerFitWindow != null ? null : inheritedShown(c -> booleanText(c.getViewerFitWindow())));
    }

    private Node viewerDisplayDocTitleNote() {
        return fieldNote(
                "Show the document title (not the file name) in the viewer's title bar."
                        + " Leave unset to inherit.",
                viewerDisplayDocTitle != null ? null
                        : inheritedShown(c -> booleanText(c.getViewerDisplayDocTitle())),
                // Trailing heading: introduces the Color Management group below.
                "Color Management");
    }

    private Node outputIntentIdentifierNote() {
        return fieldNote(
                "Output-intent identifier embedded into PDF/A or PDF/X output. Leave blank to inherit.",
                isBlank(outputIntentIdentifier)
                        ? inheritedShown(PdfReactorConfig::getOutputIntentIdentifier) : null);
    }

    private Node colorConversionEnabledNote() {
        return fieldNote(
                "When set, enables (true) or disables (false) PDFreactor color conversion."
                        + " Leave unset to inherit.",
                colorConversionEnabled != null ? null : inheritedShown(c -> {
                    Boolean v = c.getColorConversionEnabled();
                    return v != null ? v.toString() : null;
                }),
                // Trailing heading: introduces the Advanced group (configuration
                // JSON, troubleshooting gate) below.
                "Advanced");
    }

    private Node configurationJsonNote() {
        return fieldNote(
                "Advanced escape hatch: raw PDFreactor configuration as JSON, applied"
                        + " to every conversion for this site, for any configuration property"
                        + " this form does not expose. When the same property appears in more"
                        + " than one JSON source, the sources layer in this order, each"
                        + " overriding the one before: global, then this site, then any"
                        + " per-view or per-call JSON. A value set through any PDFreactor form"
                        + " always wins over the JSON — whether set here, on another site, or"
                        + " on an individual document — so the JSON only fills in properties"
                        + " that no form field controls.",
                null);
    }

    private Node troubleshootingEnabledNote() {
        // The inherited master is the GLOBAL Sites & Settings record's value
        // (not a deploy setting and not on the PdfReactorConfig interface).
        String inherited = null;
        if (troubleshootingEnabled == null) {
            try {
                Boolean master = forSite(null).getTroubleshootingEnabled();
                inherited = master != null ? master.toString() : "false (default)";
            } catch (RuntimeException unresolvable) {
                inherited = null;
            }
        }
        return fieldNote(
                "When on, editors can produce diagnostic debug/inspectable PDFs from"
                        + " the article editor (preview and Generate only — never published)."
                        + " Set this on the global record to allow it everywhere, or per site"
                        + " to override. Leave unset to inherit.",
                inherited);
    }

    private Node outputIntentProfileNote() {
        return fieldNote(
                "Output-intent ICC profile. Its bytes are embedded into the PDF as"
                        + " the output intent (the PDF/A / PDF/X path). Pick an existing"
                        + " profile, or create one and then select it. The same profile can"
                        + " be reused as the CMYK profile and on other sites. Leave blank to"
                        + " inherit.",
                outputIntentProfile != null ? null
                        : inheritedProfileShown(PdfReactorSiteSettings::getOutputIntentProfile,
                                SettingsPdfReactorConfig.OUTPUT_INTENT_PROFILE_URI_SETTING));
    }

    private Node cmykIccProfileNote() {
        return fieldNote(
                "CMYK ICC profile used for color conversion. Its bytes travel inside"
                        + " the conversion request, so the PDFreactor host needs no egress."
                        + " Pick an existing profile, or create one and then select it."
                        + " Leave blank to inherit.",
                cmykIccProfile != null ? null
                        : inheritedProfileShown(PdfReactorSiteSettings::getCmykIccProfile,
                                SettingsPdfReactorConfig.CMYK_ICC_PROFILE_URI_SETTING));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * The inherited (currently-in-effect) value for a blank ICC profile field:
     * the global Sites &amp; Settings record's referenced {@link IccProfile}
     * name when one is picked (nameable because the field is a named
     * reference), else — e.g. on the <strong>global</strong> record itself,
     * where "inherit" means the deploy-time profile-URI setting that has no
     * record to name — the URI string, mirroring the text-field
     * {@link #inheritedShown} "(none — plain default)" behavior. No ICC bytes
     * are read.
     *
     * @return The value to show, or {@code null} when resolution fails (the
     *         note then shows the explanation only).
     */
    private static String inheritedProfileShown(
            java.util.function.Function<PdfReactorSiteSettings, IccProfile> reader,
            String deployTimeUriSetting) {
        IccProfile inherited;
        try {
            inherited = reader.apply(forSite(null));
        } catch (RuntimeException unresolvable) {
            return null;
        }
        if (inherited != null && inherited.getName() != null) {
            return inherited.getName();
        }
        String uri = Settings.getOrDefault(String.class, deployTimeUriSetting, null);
        return !isBlank(uri) ? uri.trim() : "(none — plain default)";
    }

    /**
     * The inherited (currently-in-effect) value from the global effective
     * config, swallowing any config-resolution error.
     *
     * @return The value to show ("(none — plain default)" for an empty
     *         effective value), or {@code null} when resolution fails.
     */
    private static String inheritedShown(java.util.function.Function<PdfReactorConfig, String> reader) {
        String value;
        try {
            value = reader.apply(PdfReactorConfigs.forSite(null));
        } catch (RuntimeException unresolvable) {
            return null;
        }
        return value == null || value.isEmpty() ? "(none — plain default)" : value;
    }

    /**
     * Builds a field's note as one block node: the always-shown explanation,
     * then — only when {@code inheritedShown} is non-null (i.e. the field is
     * blank and the inherited value resolved) — a muted "Currently in effect
     * (inherited): …" line.
     *
     * <p>One node on purpose: the Tool renders a field's note siblings in a
     * flex row that does not give each note the full row width, so a separate
     * static explanation and a separate dynamic hint sat side by side on wide
     * viewports. A single node has no sibling to share the row with, and we
     * own the internal order. Styled by {@code .PdfFieldNote} in
     * {@code tool-head.css}.</p>
     */
    private static Node fieldNote(String explanation, String inheritedShown) {
        return fieldNote(explanation, inheritedShown, null);
    }

    /**
     * As {@link #fieldNote(String, String)} but with a trailing section-heading
     * row that introduces the <em>next</em> settings group. A field note renders
     * below its field, so a group's heading is carried by the note of the field
     * that <strong>precedes</strong> the group; it then sits just above the
     * group's first field, giving visible sub-sections inside the single
     * PDFreactor cluster. Styled by {@code .PdfFieldNote-heading} in
     * {@code tool-head.css}.
     */
    private static Node fieldNote(String explanation, String inheritedShown, String trailingSectionHeading) {
        return DIV.className("PdfFieldNote").with(note -> {
            note.add(DIV.className("PdfFieldNote-text").with(explanation));
            if (inheritedShown != null) {
                note.add(DIV.className("PdfFieldNote-inherited")
                        .with("Currently in effect (inherited): " + inheritedShown));
            }
            if (trailingSectionHeading != null) {
                note.add(DIV.className("PdfFieldNote-heading").with(trailingSectionHeading));
            }
        });
    }

    /**
     * Validates the free-text fields at save time, so a malformed value is
     * reported in the form (with the field highlighted) instead of surfacing
     * only as a {@link PdfReactorException} during a later conversion. The enum
     * fields are dropdowns and need no validation; global {@code pdfreactor/*}
     * settings have no save UI and are validated at use time.
     */
    @Override
    protected void onValidate() {
        State state = getState();
        addFieldError(state, "pdfreactor.configurationJson", validateConfigurationJson(configurationJson));
    }

    private static void addFieldError(State state, String fieldInternalName, String message) {
        if (message == null) {
            return;
        }
        ObjectField field = state.getField(fieldInternalName);
        if (field != null) {
            // The non-deprecated overload takes a Throwable (errors are stored
            // as Throwables); the message is what the form shows on the field.
            state.addError(field, new IllegalArgumentException(message));
        }
    }

    /**
     * @return An error message if {@code json} is non-blank but not valid
     *         PDFreactor configuration JSON, else {@code null}.
     */
    static String validateConfigurationJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            RawConfiguration.parse(json);
            return null;
        } catch (PdfReactorException invalid) {
            return invalid.getMessage();
        }
    }

    /**
     * Loads the PDFreactor settings for the given site, or the global
     * settings when {@code site} is {@code null}.
     *
     * @return Nonnull.
     */
    public static PdfReactorSiteSettings forSite(Site site) {
        return SiteSettings.get(site, settings -> settings.as(PdfReactorSiteSettings.class));
    }
}
