package com.realobjects.brightspot.pdfreactor;

/**
 * The PDFreactor service's license state, as observed by a license probe (a
 * trivial conversion run with the {@code LICENSE} error policy on; see
 * {@link PdfReactorService#checkLicense()}).
 *
 * <p>There is no license-status accessor on the 12.6 client — {@code Result},
 * {@code Version}, and the status/version probes carry no licensed/evaluation
 * field. The only documented signal is that the {@code LICENSE} error policy
 * makes a conversion <em>abort</em> when no valid license is installed, so the
 * state is inferred from whether that probe conversion succeeds or aborts.</p>
 */
public enum PdfLicenseState {

    /** A probe conversion with the {@code LICENSE} policy succeeded. */
    LICENSED,

    /**
     * A probe conversion with the {@code LICENSE} policy aborted with a
     * license error — the service runs unlicensed (evaluation mode), so all
     * output is watermarked. Conversions are not blocked: preview, Generate,
     * and publish all relax the license policy and produce watermarked PDFs.
     */
    EVALUATION,

    /**
     * The probe was inconclusive (service down, unreachable, timeout, or any
     * non-license failure). Never reported as licensed or evaluation — callers
     * fall back to the plain service-health state.
     */
    UNKNOWN
}
