package com.realobjects.brightspot.pdfreactor;

import java.util.Objects;

/**
 * The outcome of a successful conversion: the PDF bytes plus
 * {@link PdfDiagnostics} for surfacing non-fatal problems (e.g. missing
 * resources in the editor-preview path, which does not fail closed).
 */
public final class PdfResult {

    private final byte[] document;
    private final String contentType;
    private final int numberOfPages;
    private final PdfDiagnostics diagnostics;

    public PdfResult(byte[] document, String contentType, int numberOfPages, PdfDiagnostics diagnostics) {
        // Defensive copy: this type is immutable, so it must not alias the
        // caller's array (a later mutation would change the "result" bytes).
        this.document = Objects.requireNonNull(document, "document").clone();
        this.contentType = contentType != null ? contentType : "application/pdf";
        this.numberOfPages = numberOfPages;
        this.diagnostics = diagnostics != null ? diagnostics : PdfDiagnostics.empty();
    }

    /**
     * @return Nonnull. The PDF bytes (a defensive copy).
     */
    public byte[] getDocument() {
        return document.clone();
    }

    /**
     * @return Nonnull. Usually {@code application/pdf}.
     */
    public String getContentType() {
        return contentType;
    }

    public int getNumberOfPages() {
        return numberOfPages;
    }

    /**
     * @return Nonnull.
     */
    public PdfDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
