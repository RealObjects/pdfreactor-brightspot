package com.realobjects.brightspot.pdfreactor.preview;

import java.util.Optional;

import com.psddev.cms.db.PageFilter;
import com.psddev.cms.db.Preview;
import com.psddev.cms.db.Renderer;
import com.psddev.cms.db.ToolUi;
import com.psddev.cms.db.ToolUser;
import com.psddev.cms.preview.IFramePreviewType;
import com.psddev.cms.preview.PreviewType;
import com.psddev.cms.ui.ToolLocalization;
import com.psddev.cms.ui.ToolRequest;
import com.psddev.cms.ui.form.Note;
import com.psddev.cms.view.PageEntryView;
import com.psddev.cms.view.PreviewEntryView;
import com.psddev.cms.view.ViewModel;
import com.psddev.dari.db.State;
import com.psddev.dari.html.RawNode;
import com.psddev.dari.web.UrlBuilder;
import com.psddev.dari.web.WebRequest;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;
import com.realobjects.brightspot.pdfreactor.ToolResources;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;

import static com.psddev.dari.html.Nodes.BUTTON;
import static com.psddev.dari.html.Nodes.DIV;
import static com.psddev.dari.html.Nodes.FORM;
import static com.psddev.dari.html.Nodes.IFRAME;
import static com.psddev.dari.html.Nodes.INPUT;
import static com.psddev.dari.html.Nodes.SPAN;

/**
 * "PDF" preview option in the editor's preview pane. The iframe form points
 * at {@link PdfPreviewPage}, which streams the converted draft as
 * {@code application/pdf} — the browser renders it natively, so there is no
 * separate preview artifact.
 *
 * <p>Enabled by adding it to the preview types in Sites &amp; Settings
 * (CMS tab → Preview cluster). {@code refreshAsContentChanges} is off:
 * a full conversion per keystroke is too costly.</p>
 *
 * <p><b>SKIN COUPLING INVENTORY</b> (verified against
 * {@code com.psddev:brightspot-bom:5.0.2.4}). There is no public extension
 * point for a custom preview-bar control, so {@link #display(Preview)} and
 * {@link com.realobjects.brightspot.pdfreactor.PdfReactorToolPageHead} depend
 * on these private v5-skin internals. Any {@code brightspot}/BOM bump must
 * re-verify each item and re-run {@code :plugin:uiTest} plus a visual check of
 * the preview bar before merge (see the working-agreement upgrade gate in
 * {@code CLAUDE.md}). The detailed rationale stays at each use site below;
 * this is the single checklist:</p>
 * <ul>
 *   <li><b>Loader contract</b> (a contract, not styling — required by
 *       ViewPreview.js): {@code PreviewFrame-typeControlsContainer} and
 *       {@code PreviewFrame-typeDisplay}.</li>
 *   <li><b>Controls grid placement:</b> {@code grid-area: controls;
 *       grid-column: 8} — the edit pane dissolves preview wrappers to
 *       {@code display: contents}, making controls direct {@code .PreviewFrame}
 *       grid items needing explicit placement.</li>
 *   <li><b>typeActions allowlist:</b> the edit pane hides all
 *       {@code .PreviewFrame-typeActions} children except {@code .DateTimeInput}
 *       and {@code .action-share}; the refresh button is forced visible with
 *       {@code !important}.</li>
 *   <li><b>Button recipe</b> (in {@code web/preview-frame.css}): the
 *       {@code --button-*} custom properties resolved from
 *       {@code oklch(var(--btu-theme-*))}, ported verbatim onto the
 *       plugin-owned {@code PdfPreview-refresh} class (with literal fallbacks so
 *       renamed variables degrade rather than break).</li>
 *   <li><b>Refresh icon</b> (in {@code web/preview-frame.css}): the skin's
 *       lucide private-use glyph {@code \e149}.</li>
 *   <li><b>Banner bridge</b> (in {@code web/preview-problems.js}, contributed
 *       by {@code PdfReactorToolPageHead}): the skin's
 *       {@code .message:not(.Message)} legacy-class rule plus the themed
 *       {@code Message} / {@code is-warning} / {@code is-error} classes. The
 *       skin has <em>no</em> {@code is-info} theme (verified — "is-info" does
 *       not appear in the v5 skin CSS), so the evaluation banner's blue
 *       {@code is-info} look is themed by the plugin's own
 *       {@code preview-frame.css} via the {@code --btu-theme-primary-*}
 *       scale.</li>
 * </ul>
 */
public class PdfPreviewType extends IFramePreviewType {

    @Note("Optional label for this PDF preview option in the editor's preview"
            + " menu. Leave blank to use the default (\"PDF\").")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDisplayName() {
        return Optional.ofNullable(name).orElse(localize("defaultName", "PDF"));
    }

    private static String localize(String key, String fallback) {
        return ToolLocalization.text(PdfPreviewType.class, key, fallback);
    }

    /**
     * Whether to offer the schedule-date control for this content. It is an
     * editor opt-in per content (default off): the control only date-shifts
     * the preview for content that participates in scheduled publishing, so
     * rather than show an unexplained date picker on every PDF preview, it
     * appears only for {@link HasPdfRendering} content whose
     * {@code schedulePreviewEnabled} flag is set on the edit form.
     */
    private static boolean scheduleControlEnabled(Preview preview) {
        Object content = preview.getObject();
        return content instanceof HasPdfRendering
                && State.getInstance(content).as(HasPdfRenderingData.class).isSchedulePreviewEnabled();
    }

    // NOT PreviewType.isPreviewable(preview): that iterates all configured
    // preview types calling each shouldDisplay -- including this one --
    // which recurses infinitely (StackOverflowError on the content edit
    // page). Mirror DefaultPreviewType's content-based check instead
    // (which uses the same deprecated lookups, with the same suppression).
    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldDisplay(Preview preview) {
        Object object = preview.getObject();
        if (object == null) {
            return false;
        }
        State state = State.getInstance(object);
        return state.getType() != null
                && (object instanceof Renderer
                || ViewModel.findViewModelClass(PageFilter.PAGE_VIEW_TYPE, object) != null
                || ViewModel.findViewModelClass(PageFilter.PREVIEW_VIEW_TYPE, object) != null
                || ViewModel.findViewModelClass(PageEntryView.class, object) != null
                || ViewModel.findViewModelClass(PreviewEntryView.class, object) != null
                || state.getType().as(ToolUi.class).getRenderer() != null);
    }

    @Override
    protected boolean refreshAsContentChanges() {
        return false;
    }

    @Override
    protected String getFormAction(Preview preview) {
        return new UrlBuilder(PdfPreviewPage.class).build();
    }

    /**
     * Emits the hidden {@code previewId} plus the header controls: a
     * refresh button (the PDF preview intentionally does not re-render as
     * content changes, so editors re-render the current draft manually) and
     * the standard share-preview link, plus the standard schedule-date select
     * ({@code PreviewType.createScheduleDateSelect}, emitting {@code _date} /
     * {@code _scheduleId}). The schedule-date select is now wired:
     * {@link PdfPreviewPage} installs a {@code PreviewDatabase} override in
     * the request, because the platform's
     * {@code PreviewDatabaseFilter} only fires on {@code /_preview/} paths,
     * never on this {@code /cms/} ToolPage endpoint. It renders as a
     * {@code .DateTimeInput}, which the edit pane's typeActions allowlist
     * already shows.
     */
    @Override
    protected String createFormInputs(Preview preview) {
        ToolUser user = WebRequest.getCurrent().as(ToolRequest.class).getCurrentUser();

        // Platform convention (DefaultPreviewType, also noted in the
        // custom-preview-type docs): interactive, edit-coupled controls are
        // gated on the content-edit context. Shared previews are frozen
        // snapshots -- a refresh there could never reflect content changes,
        // so it renders only while editing.
        boolean contentEdit = Preview.CONTENT_EDIT_PREVIEW_CONTEXT.equals(preview.getContext());

        // The refresh button sits in PreviewFrame-typeActions next to the
        // share link -- that container is the visible control bar in the
        // edit pane. Its stylesheet allowlists children (display: none for
        // everything except .DateTimeInput and .action-share), so the
        // injected stylesheet re-shows the button with !important; it is
        // fully self-styled because the skin has no styling for custom
        // controls (the typeControls area renders outside the bar).
        return DIV.className("PreviewFrame-typeControls")
                .with(INPUT.typeHidden()
                        .name(PdfPreviewPage.PREVIEW_ID_PARAMETER)
                        .value(preview.getId().toString()))
                .toString()
                + DIV.classList("PreviewFrame-typeActions", "PdfPreview-actions")
                .with(
                        // Submit button: re-runs the controls form against the
                        // current preview iframe, re-converting the draft.
                        // Plugin-owned class; the injected stylesheet carries
                        // a verbatim port of the skin's icon-button recipe
                        // (the skin itself replicates that block per action
                        // class rather than sharing one), so the look matches
                        // the share link without inheriting its JS bindings.
                        contentEdit
                                ? BUTTON.className("PdfPreview-refresh")
                                .attr("type", "submit")
                                .attr("title", localize("action.refresh", "Refresh PDF preview"))
                                .attr("aria-label", localize("action.refresh", "Refresh PDF preview"))
                                : null,
                        user != null && contentEdit
                                ? PreviewType.createSharePreviewLink(preview)
                                : null,
                        // Standard schedule-date select: renders as a
                        // .DateTimeInput (allowlisted by the edit pane) and
                        // submits _date / _scheduleId, which PdfPreviewPage
                        // turns into a PreviewDatabase override. Editor opt-in
                        // per content (default off — see scheduleControlEnabled)
                        // and wrapped with an explanatory tooltip, since the bare
                        // date control is otherwise unexplained next to refresh
                        // and share.
                        user != null && contentEdit && scheduleControlEnabled(preview)
                                ? SPAN.className("PdfPreview-scheduleDate")
                                        .attr("title", localize("action.scheduleDate.title",
                                                "Preview the PDF as it would render at a future"
                                                        + " scheduled date."))
                                        .with(PreviewType.createScheduleDateSelect(preview, user))
                                : null)
                .toString();
    }

    /**
     * Same as {@link IFramePreviewType#display} — both the
     * {@code PreviewFrame-typeControlsContainer} and
     * {@code PreviewFrame-typeDisplay} classes are required by the preview
     * loader (ViewPreview.js inserts a dynamically created iframe into the
     * type display and submits the controls form at it) — plus a scoped
     * stylesheet that pins the iframe to the pane at scale&nbsp;1.
     *
     * <p>Without it, the content-edit resize script scales preview iframes
     * (a virtual width transformed down to the pane), which is right for
     * web pages but shrinks the browser's PDF viewer <em>including its
     * controls</em> to a fraction of their size. Stylesheet
     * {@code !important} rules beat the script's inline styles, the rules
     * are scoped to this type's own {@code PdfPreviewFrame} marker class,
     * and the PDF viewer does its own fit-to-width — no scaling needed.</p>
     */
    @Override
    public String display(Preview preview) {
        // The scoped stylesheet (iframe scale-1 pin + problem-banner layout +
        // the verbatim skin button recipe) lives in web/preview-frame.css,
        // loaded by ToolResources rather than concatenated here. Its skin
        // dependencies are tracked in the SKIN COUPLING INVENTORY above.
        return DIV.className("PreviewFrame-typeControlsContainer")
                .attr("data-refresh-on-change", Boolean.toString(refreshAsContentChanges()))
                .with(FORM
                        .className("PreviewFrame-iFrameControlsForm")
                        .method(getFormMethod(preview))
                        .action(getFormAction(preview))
                        .target("PreviewFrame-preview")
                        .with(new RawNode(createFormInputs(preview))))
                .toString()
                + DIV.classList("PreviewFrame-typeDisplay", "PdfPreviewFrame")
                .with(ToolResources.styleSheet(ToolResources.WEB + "preview-frame.css"),
                        IFRAME.name("PreviewFrame-preview"))
                .toString();
    }
}
