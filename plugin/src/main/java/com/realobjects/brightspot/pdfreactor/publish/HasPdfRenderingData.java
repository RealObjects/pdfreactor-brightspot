package com.realobjects.brightspot.pdfreactor.publish;

import java.util.Date;

import com.psddev.cms.db.ToolUi;
import com.psddev.cms.ui.form.DynamicFieldMethod;
import com.psddev.cms.ui.form.DynamicNoteMethod;
import com.psddev.cms.ui.form.Note;
import com.psddev.dari.db.Modification;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.html.Node;
import com.psddev.dari.util.StorageItem;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfigs;
import com.realobjects.brightspot.pdfreactor.PdfResult;
import com.realobjects.brightspot.pdfreactor.PdfViewerPageLayout;

import static com.psddev.dari.html.Nodes.DIV;

/**
 * Fields and the publish hook for {@link HasPdfRendering} types: the
 * editor-level "Generate PDF on publish" checkbox (on by default) and the
 * generated-PDF output fields, written by {@link PdfPublishAutomation} off
 * the save thread.
 */
@Recordable.FieldInternalNamePrefix("pdfreactor.")
public class HasPdfRenderingData extends Modification<HasPdfRendering> {

    // All per-article PDFreactor fields live in ONE cluster so they read as a
    // single collapsible "PDFreactor" section rather than several sibling
    // sections. The publish/preview toggles and the read-only generation status
    // come first (no heading, directly under the section title); the override
    // groups below are introduced by section headings. A field note renders
    // below its field, so each group's heading is a trailing row on the note of
    // the field that PRECEDES the group, placing it just above the group's first
    // field: Document Metadata, Document Features, Viewer Preferences. Field
    // declaration order is therefore the section order. There are no per-article
    // color/ICC overrides.
    private static final String CLUSTER = "PDFreactor";

    /** Internal name of the legacy inverted opt-out flag this field replaced. */
    static final String LEGACY_SKIP_FIELD = "pdfreactor.skipPdfOnPublish";

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Generate PDF on publish")
    @Note("When checked, publishing this content generates a PDF. On by default.")
    private Boolean generatePdfOnPublish;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Show schedule-date preview control")
    @Note("When checked, the PDF preview shows a schedule-date control so you"
            + " can preview the PDF as it would render at a future scheduled"
            + " date (applying scheduled changes). Off by default — only useful"
            + " when this content participates in scheduled publishing.")
    private Boolean schedulePreviewEnabled;

    // Per-article troubleshooting toggles. They drive the preview and on-demand
    // Generate paths only (diagnostic builds — converted fresh, streamed inline,
    // never cached, stamped, or published). Shown only when an administrator
    // has allowed troubleshooting for this content's site
    // (PdfReactorSiteSettings.troubleshootingEnabled, the gate); excluded from
    // the cache key and untouched by the publish automation.

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Debug build")
    @DynamicFieldMethod("troubleshootingBuildVisibility")
    @Note("Produce a debug PDF (intermediate documents, logs, and resources"
            + " attached) when previewing or generating this content. Diagnostic"
            + " only — never cached or published. Visible only when an"
            + " administrator allows debug/inspectable builds.")
    private Boolean debugBuild;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Inspectable build")
    @DynamicFieldMethod("troubleshootingBuildVisibility")
    @Note("Produce an inspectable PDF (the rendered DOM embedded for the"
            + " PDFreactor Inspector) when previewing or generating this content."
            + " Diagnostic only — never cached or published. Visible only when an"
            + " administrator allows debug/inspectable builds.")
    private Boolean inspectableBuild;

    // A read-only StorageItem still renders the full file-upload + image
    // -preview chrome (a "Keep Existing" dropdown, a crop/focus/text toolbar,
    // and a tiny overflowing PDF thumbnail) -- the wrong control for a
    // generated artifact. Hide it: the right-rail GeneratePdfWidget already
    // shows the stored PDF cleanly (download link + page count) from the
    // GeneratedPdf cache record, which is the source of truth for display.
    // The field is still written by PdfPublishAutomation as the on-content
    // reference to the latest publish PDF; the date and status below remain
    // visible read-only in the cluster.
    @ToolUi.Hidden
    private StorageItem generatedPdf;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Generated PDF Date")
    @ToolUi.ReadOnly
    @Note("When the most recent PDF for this content was generated — on publish"
            + " or on demand. Read-only — set whenever a PDF is generated.")
    private Date generatedPdfDate;

    // Operational/log-style field, intentionally English: it is written from
    // a background Dari Task with no request/locale, and localizing at display
    // time would need a computed, non-persisted getter rendered read-only in
    // this cluster — a Dari capability not verified to render, so this uses a
    // documented-English fallback rather than an unverified refactor.
    // It is also the only queryable record of a publish *failure* (a failed
    // task stores no GeneratedPdf row).
    // The note carries a trailing "Document Metadata" heading: this is the last
    // field before the metadata override group, and a field note renders below
    // its field, so the heading lands just above the group's first field.
    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Generated PDF Status")
    @ToolUi.ReadOnly
    @DynamicNoteMethod("generatedPdfStatusNote")
    private String generatedPdfStatus;

    // Per-article overrides for the first-class settings: each is null =
    // inherit the site/global default, a set value wins for this article.
    // PerArticlePdfReactorConfig folds these into the effective config, so they
    // flow through buildConfiguration and the cache fingerprint unchanged. The
    // first field of each override group carries that group's section heading in
    // its note (see the *Note methods); a field note renders above its field, so
    // the heading introduces the fields that follow it.

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Document Creator")
    @Note("Override the document Creator for this article. Leave blank to inherit.")
    private String creator;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Document Subject")
    @Note("Override the document Subject for this article. Leave blank to inherit.")
    private String subject;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Document Keywords")
    @DynamicNoteMethod("keywordsNote")
    private String keywords;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Add Bookmarks")
    @Note("Override the bookmark-outline setting for this article. Leave unset to inherit.")
    private Boolean addBookmarks;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Add Links")
    @Note("Override the hyperlinks setting for this article. Leave unset to inherit.")
    private Boolean addLinks;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Add Tags")
    @DynamicNoteMethod("addTagsNote")
    private Boolean addTags;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Viewer Page Layout")
    @Note("Override the initial viewer page layout for this article. Leave blank to inherit.")
    private PdfViewerPageLayout viewerPageLayout;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Viewer: Fit Window")
    @Note("Override the fit-window viewer preference for this article. Leave unset to inherit.")
    private Boolean viewerFitWindow;

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.DisplayName("Viewer: Display Document Title")
    @Note("Override the display-document-title viewer preference. Leave unset to inherit.")
    private Boolean viewerDisplayDocTitle;

    /**
     * Cache key of the last generation attempt (success or failure);
     * the idempotency guard that also keeps the post-generation save from
     * re-triggering the hook. Hidden: not editor-relevant.
     */
    @ToolUi.Hidden
    private String generatedPdfAttemptKey;

    /**
     * Whether publishing this content generates a PDF. On unless explicitly
     * turned off. Content saved before this field existed has no value here;
     * for it, the legacy inverted "skip" flag is honored so an editor's earlier
     * opt-out is preserved (absent both, the default is to generate).
     */
    public boolean isGeneratePdfOnPublish() {
        if (generatePdfOnPublish != null) {
            return generatePdfOnPublish;
        }
        return !Boolean.TRUE.equals(getState().get(LEGACY_SKIP_FIELD));
    }

    public void setGeneratePdfOnPublish(Boolean generatePdfOnPublish) {
        this.generatePdfOnPublish = generatePdfOnPublish;
    }

    public boolean isSchedulePreviewEnabled() {
        return Boolean.TRUE.equals(schedulePreviewEnabled);
    }

    public void setSchedulePreviewEnabled(Boolean schedulePreviewEnabled) {
        this.schedulePreviewEnabled = schedulePreviewEnabled;
    }

    public boolean isDebugBuild() {
        return Boolean.TRUE.equals(debugBuild);
    }

    public void setDebugBuild(Boolean debugBuild) {
        this.debugBuild = debugBuild;
    }

    public boolean isInspectableBuild() {
        return Boolean.TRUE.equals(inspectableBuild);
    }

    public void setInspectableBuild(Boolean inspectableBuild) {
        this.inspectableBuild = inspectableBuild;
    }

    // Per-article override accessors (raw nullable: null = inherit).

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

    // Section headings that group the override fields inside the single
    // PDFreactor cluster. A field note renders below its field, so each group's
    // heading is a trailing row on the note of the field that PRECEDES the
    // group, placing it just above the group's first field. The other fields
    // keep a plain @Note. Styled by .PdfFieldNote / .PdfFieldNote-heading in
    // tool-head.css, matching the Sites & Settings PDFreactor form.

    private Node generatedPdfStatusNote() {
        return sectionNote(
                "Result of the most recent PDF generation (on publish or on demand):"
                        + " success (with page count and any non-fatal diagnostics) or the"
                        + " failure reason. Read-only — set whenever a PDF is generated.",
                "Document Metadata");
    }

    private Node keywordsNote() {
        return sectionNote(
                "Override the document Keywords for this article. Leave blank to inherit.",
                "Document Features");
    }

    private Node addTagsNote() {
        return sectionNote(
                "Override the accessibility-tags setting for this article. Leave unset to inherit.",
                "Viewer Preferences");
    }

    private static Node sectionNote(String explanation, String trailingSectionHeading) {
        return DIV.className("PdfFieldNote").with(note -> {
            note.add(DIV.className("PdfFieldNote-text").with(explanation));
            note.add(DIV.className("PdfFieldNote-heading").with(trailingSectionHeading));
        });
    }

    /**
     * Hides the per-article troubleshooting toggles unless an administrator has
     * allowed debug/inspectable builds for this content's site (the
     * {@code troubleshootingEnabled} gate). Invoked per field at form render via
     * {@link DynamicFieldMethod}; {@code field.as(ToolUi.class).setHidden(true)}
     * is the documented way to drive a field's edit-form visibility dynamically.
     */
    @SuppressWarnings("unused") // referenced by name from @DynamicFieldMethod
    private void troubleshootingBuildVisibility(ObjectField field) {
        field.as(ToolUi.class).setHidden(
                !PdfReactorConfigs.troubleshootingGateEnabled(getOriginalObject()));
    }

    public StorageItem getGeneratedPdf() {
        return generatedPdf;
    }

    public void setGeneratedPdf(StorageItem generatedPdf) {
        this.generatedPdf = generatedPdf;
    }

    public Date getGeneratedPdfDate() {
        return generatedPdfDate;
    }

    public void setGeneratedPdfDate(Date generatedPdfDate) {
        this.generatedPdfDate = generatedPdfDate;
    }

    public String getGeneratedPdfStatus() {
        return generatedPdfStatus;
    }

    public void setGeneratedPdfStatus(String generatedPdfStatus) {
        this.generatedPdfStatus = generatedPdfStatus;
    }

    public String getGeneratedPdfAttemptKey() {
        return generatedPdfAttemptKey;
    }

    public void setGeneratedPdfAttemptKey(String generatedPdfAttemptKey) {
        this.generatedPdfAttemptKey = generatedPdfAttemptKey;
    }

    /**
     * Records a successful PDF generation onto the content fields: the
     * on-content reference, the date, the success status, and the idempotency
     * attempt key. Used by both the publish automation and the on-demand
     * Generate action so the read-only Generated Pdf Date/Status reflect
     * either path. Setting the attempt key to the generation's cache key makes
     * the resulting {@code afterSave} a no-op — {@link PdfPublishAutomation#handle}
     * short-circuits a matching key — so an on-demand stamp does not re-fire
     * publish automation (the on-demand cache key equals what the publish hook
     * computes for the same revision + config, and a plain save does not change
     * the update date the key uses).
     *
     * @param pdf Nonnull. The stored PDF storage item.
     * @param result Nonnull. The conversion result (for the status counts).
     * @param cacheKey Nonnull. The generation's cache key.
     * @param byteSize Size of the generated PDF in bytes (for the status).
     */
    public void recordSuccessfulGeneration(StorageItem pdf, PdfResult result, String cacheKey, long byteSize) {
        setGeneratedPdf(pdf);
        setGeneratedPdfDate(new Date());
        setGeneratedPdfStatus(PdfPublishAutomation.successStatus(result, byteSize));
        setGeneratedPdfAttemptKey(cacheKey);
    }

    @Override
    protected void afterCreate() {
        // New content generates a PDF on publish by default. afterCreate fires
        // when the content is first created (before the edit form renders), so
        // the toggle shows checked and persists on through the first save — the
        // Tool otherwise renders a modification's boolean unchecked, which would
        // persist "off". On load of existing content the stored value overlays
        // this seed, so an earlier opt-out is preserved.
        setGeneratePdfOnPublish(true);
    }

    @Override
    protected void afterSave() {
        PdfPublishAutomation.handle(getOriginalObject(), this);
    }
}
