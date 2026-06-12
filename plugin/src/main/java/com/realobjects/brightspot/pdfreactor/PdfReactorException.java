package com.realobjects.brightspot.pdfreactor;

/**
 * Thrown when a conversion or service interaction fails. Carries the
 * {@link PdfDiagnostics} extracted from the failed conversion's result, if
 * the service returned one.
 */
public class PdfReactorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient PdfDiagnostics diagnostics;

    public PdfReactorException(String message) {
        this(message, null, null);
    }

    public PdfReactorException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public PdfReactorException(String message, Throwable cause, PdfDiagnostics diagnostics) {
        super(message, cause);
        this.diagnostics = diagnostics != null ? diagnostics : PdfDiagnostics.empty();
    }

    /**
     * @return Nonnull.
     */
    public PdfDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
