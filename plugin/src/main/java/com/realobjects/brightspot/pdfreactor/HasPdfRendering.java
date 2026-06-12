package com.realobjects.brightspot.pdfreactor;

import com.psddev.dari.db.Recordable;

/**
 * Marker interface: a content type that implements this opts into automatic
 * PDF generation on publish (developer-level gate; see
 * {@code com.realobjects.brightspot.pdfreactor.publish} for the admin and
 * editor gates). Marked types receive the
 * {@code HasPdfRenderingData} modification: the per-article
 * "Generate PDF on publish" checkbox (on by default), the per-article
 * setting overrides, and the generated-PDF output fields.
 */
public interface HasPdfRendering extends Recordable {
}
