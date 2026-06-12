package com.realobjects.brightspot.pdfreactor.publish;

import com.psddev.cms.db.Site;
import com.psddev.cms.db.SiteSettings;
import com.psddev.cms.db.ToolUi;
import com.psddev.cms.ui.form.Note;
import com.psddev.dari.db.Modification;
import com.psddev.dari.db.Recordable;

/**
 * Admin-level gate for publish automation, per site (Sites &amp; Settings →
 * CMS tab → PDFreactor cluster). Stored inverted ({@code disable…}) so the
 * unset default is <em>enabled</em>: the developer already opted the type
 * in deliberately via {@code HasPdfRendering}; this switch lets admins turn
 * automation off per site (or globally) without a redeploy.
 */
@Recordable.FieldInternalNamePrefix("pdfreactor.")
public class PdfReactorPublishSettings extends Modification<SiteSettings> {

    private static final String CLUSTER = "PDFreactor";

    @ToolUi.Cluster(CLUSTER)
    @ToolUi.Tab("CMS")
    @Note("When checked, publishing content marked for PDF rendering"
            + " does not generate PDFs on this site.")
    private Boolean disablePublishAutomation;

    public boolean isPublishAutomationDisabled() {
        return Boolean.TRUE.equals(disablePublishAutomation);
    }

    public void setDisablePublishAutomation(Boolean disablePublishAutomation) {
        this.disablePublishAutomation = disablePublishAutomation;
    }

    /**
     * Returns whether publish automation is enabled for the given site
     * ({@code null}, or an unset per-site toggle, falls back to the global
     * settings).
     *
     * <p>Reads the raw nullable {@code Boolean} field — <strong>not</strong>
     * {@link #isPublishAutomationDisabled()}. {@code SiteSettings.get}
     * returns the per-site value only when {@code !ObjectUtils.isBlank(value)}
     * and otherwise falls back to the global {@code CmsTool} record. A primitive
     * {@code boolean} getter returns {@code false} for an unset per-site toggle,
     * and {@code ObjectUtils.isBlank(Boolean.FALSE)} is {@code false}, so the
     * site's {@code false} would be returned and the <em>global</em> toggle
     * never consulted — meaning a global "disable" was silently ignored for any
     * content owned by a site. The nullable field is {@code null} when unset
     * ({@code isBlank(null) == true}), so the fallback works.</p>
     */
    public static boolean isEnabled(Site site) {
        Boolean disabled = SiteSettings.get(site,
                settings -> settings.as(PdfReactorPublishSettings.class).disablePublishAutomation);
        return !Boolean.TRUE.equals(disabled);
    }
}
