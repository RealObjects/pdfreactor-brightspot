package com.realobjects.brightspot.pdfreactor;

import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * The curated initial page-layout viewer preference: a small, editor-
 * friendly subset of the client's {@code Configuration.ViewerPreferences}
 * {@code PAGE_LAYOUT_*} constants, mirrored <em>by name</em> (each constant maps
 * to {@code PAGE_LAYOUT_<name>}). Used directly as both the config value and the
 * settings-form dropdown, so the client type does not enter the stored data
 * model. {@code ClientEnumParityTest} guards the by-name mapping against a
 * client bump.
 */
public enum PdfViewerPageLayout {

    SINGLE_PAGE,
    ONE_COLUMN,
    TWO_COLUMN_LEFT,
    TWO_COLUMN_RIGHT,
    TWO_PAGE_LEFT,
    TWO_PAGE_RIGHT;

    /** The client viewer-preference constant for this layout ({@code PAGE_LAYOUT_<name>}). */
    public Configuration.ViewerPreferences toClient() {
        return Configuration.ViewerPreferences.valueOf("PAGE_LAYOUT_" + name());
    }
}
