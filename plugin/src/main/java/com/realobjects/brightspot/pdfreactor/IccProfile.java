package com.realobjects.brightspot.pdfreactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.psddev.cms.db.Content;
import com.psddev.cms.db.ToolUi;
import com.psddev.cms.ui.form.Note;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.db.State;
import com.psddev.dari.util.StorageItem;

/**
 * A reusable ICC color profile: a first-class, pickable record so a
 * profile is uploaded <em>once</em> and referenced everywhere — as a site's
 * output-intent profile, its CMYK conversion profile, or on another site —
 * instead of re-uploading the same {@code .icc} file per field.
 *
 * <p>Referenced from {@link PdfReactorSiteSettings#getOutputIntentProfile()}
 * and {@link PdfReactorSiteSettings#getCmykIccProfile()}. The bytes are read
 * <strong>server-side</strong> ({@link #readBytes()} → {@link StorageItemBytes})
 * and embedded in the conversion request, exactly as the previous inline
 * {@code StorageItem} uploads were — so the bytes / fingerprint / cache path
 * downstream ({@link SitePdfReactorConfig}, {@link PdfConfigFingerprint}) is
 * unchanged; only the data model (inline upload → shared reference) changes.</p>
 *
 * <p>The same profile may be selected as both an output intent and a CMYK
 * profile and on any number of sites: it carries no role of its own, so it is
 * not filtered per field — that is the reuse the feature is for.</p>
 */
// A reusable config asset, not editorial content — suppress the
// publish/workflow tools the inline create/edit dialog would otherwise inherit
// from Content (publish button, workflow, revisions). Content is annotated
// @ToolUi.Publishable (and the annotation is @Inherited), so subclasses are
// publishable by default; @ToolUi.Publishable(false) overrides that to a plain
// Save flow. It stays a queryable, pickable Content reference with a stable id
// (the ContentSelector picker, @Recordable.LabelFields, indexed name, inline
// create, and readBytes() are all unaffected) — a pure UI change, no data
// migration.
@Recordable.LabelFields("name")
@ToolUi.IconName("palette")
@ToolUi.DisplayName("ICC Profile")
@ToolUi.Publishable(false)
public class IccProfile extends Content {

    @Required
    @Indexed
    @Note("A descriptive name for this profile (e.g. \"ISO Coated v2 (ECI)\")."
            + " Shown when selecting the profile in PDFreactor settings.")
    private String name;

    @Required
    @Note("The ICC profile file (.icc / .icm). Its bytes are embedded in the"
            + " conversion request, so the PDFreactor host needs no egress.")
    private StorageItem file;

    @Note("Optional notes about this profile (color space, source, intended use).")
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StorageItem getFile() {
        return file;
    }

    public void setFile(StorageItem file) {
        this.file = file;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Reads the profile's bytes server-side.
     *
     * @return Nullable. {@code null} when no file is set; the profile bytes
     *         otherwise.
     * @throws PdfReactorException If the file is set but its data cannot be
     *         read (a configuration error worth surfacing).
     */
    public byte[] readBytes() {
        return StorageItemBytes.read(file);
    }

    // An ICC profile's 128-byte header carries the ASCII signature "acsp" at
    // byte offset 36 (the profile-signature field); this is the reliable way to
    // tell an actual color profile from an arbitrary uploaded file. The
    // upload field accepts any StorageItem (the platform has no MIME-type
    // restriction annotation in this BOM), so the bytes are the only honest
    // gate — a browser sends most .icc/.icm files as application/octet-stream
    // anyway, so a content-type check would not be trustworthy.
    private static final byte[] ICC_SIGNATURE = { 'a', 'c', 's', 'p' };
    private static final int ICC_SIGNATURE_OFFSET = 36;

    /**
     * Rejects a non-ICC upload at save time so the bad file is flagged on the
     * form instead of failing opaquely inside a later conversion. ({@code
     * @Required} already covers the missing-file case.)
     */
    @Override
    protected void onValidate() {
        if (file == null) {
            return;
        }
        if (!hasIccSignature(readHeader(file, ICC_SIGNATURE_OFFSET + ICC_SIGNATURE.length))) {
            State state = getState();
            ObjectField field = state.getField("file");
            if (field != null) {
                state.addError(field, new IllegalArgumentException(
                        "This file is not an ICC color profile (.icc / .icm)."
                                + " Upload a valid ICC profile."));
            }
        }
    }

    /**
     * @return {@code true} if {@code header} carries the ICC profile signature
     *         {@code "acsp"} at byte offset 36. A {@code null} header (the bytes
     *         could not be read) passes, so a transient storage read problem
     *         does not block saving — an unreadable profile surfaces separately
     *         on the conversion path. A header too short to contain the
     *         signature is rejected.
     */
    static boolean hasIccSignature(byte[] header) {
        if (header == null) {
            return true;
        }
        if (header.length < ICC_SIGNATURE_OFFSET + ICC_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < ICC_SIGNATURE.length; i++) {
            if (header[ICC_SIGNATURE_OFFSET + i] != ICC_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads up to {@code maxBytes} from the start of the item, or {@code null}
     * if the item has no readable data (so validation does not block on a
     * transient read problem). Reads only the header, not the whole profile.
     */
    private static byte[] readHeader(StorageItem item, int maxBytes) {
        try (InputStream in = item.getData()) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[maxBytes];
            int total = 0;
            int read;
            while (total < maxBytes && (read = in.read(buffer, 0, maxBytes - total)) != -1) {
                out.write(buffer, 0, read);
                total += read;
            }
            return out.toByteArray();

        } catch (IOException error) {
            return null;
        }
    }
}
