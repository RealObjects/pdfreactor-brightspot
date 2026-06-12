package com.realobjects.brightspot.pdfreactor;

/**
 * Converts HTML to PDF through the PDFreactor Web Service.
 *
 * <p>The default implementation is {@link DefaultPdfReactorService}, which
 * talks to the service via the PDFreactor Java client. The plugin's own
 * paths (preview, Generate, publish) do not block on license problems —
 * an unlicensed service produces watermarked output rather than failing;
 * the builder default in {@link PdfRenderOptions} still fails closed on
 * license for other programmatic callers. See {@link PdfRenderOptions} for
 * the per-path error policy switches.</p>
 */
public interface PdfReactorService {

    /**
     * Converts the given finished HTML into a PDF.
     *
     * @param html Nonnull. A complete HTML document.
     * @param options Nullable. {@code null} means defaults.
     * @return Nonnull.
     * @throws PdfReactorException If the conversion fails or the service is
     *         unreachable or unconfigured.
     */
    PdfResult renderHtml(String html, PdfRenderOptions options);

    /**
     * Renders the given content object to HTML (through the configured
     * {@link com.realobjects.brightspot.pdfreactor.render.HtmlSource}) and
     * converts it into a PDF.
     *
     * @param content Nonnull. A Brightspot content object.
     * @param options Nullable. {@code null} means defaults.
     * @return Nonnull.
     * @throws PdfReactorException If rendering or conversion fails.
     */
    PdfResult renderContent(Object content, PdfRenderOptions options);

    /**
     * Checks whether the PDFreactor Web Service is reachable and licensed.
     *
     * @return Nonnull. Never throws; failures are reported in the result.
     */
    PdfServiceHealth checkHealth();

    /**
     * Probes the service's license state by attempting a trivial conversion
     * with the {@code LICENSE} error policy on: the conversion aborts when no
     * valid license is installed, so an abort means evaluation mode, a success
     * means licensed, and any other failure is inconclusive.
     *
     * <p>This is a real (if minimal) conversion, heavier than
     * {@link #checkHealth()}; callers must cache it and run it in the
     * background — never inline per conversion. See {@link PdfLicenseProbe}.</p>
     *
     * @return Nonnull. Never throws; an inconclusive probe yields
     *         {@link PdfLicenseState#UNKNOWN}. The default implementation
     *         (for services that do not support probing) returns
     *         {@code UNKNOWN}.
     */
    default PdfLicenseState checkLicense() {
        return PdfLicenseState.UNKNOWN;
    }
}
