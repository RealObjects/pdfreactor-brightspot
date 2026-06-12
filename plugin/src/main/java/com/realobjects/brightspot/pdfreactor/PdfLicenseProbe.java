package com.realobjects.brightspot.pdfreactor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.psddev.dari.util.Task;

/**
 * Process-local, single-flight, background cache of the global service's
 * {@link PdfLicenseState}, shared by the health dashboard widget (the
 * "licensed vs evaluation" line) and the editor preview (the informational
 * evaluation banner).
 *
 * <p>The probe itself ({@link PdfReactorService#checkLicense()}) is a real
 * (if trivial) conversion — heavier than a status check — so it must never
 * run inline on a request. {@link #current()} returns the last known state
 * immediately and refreshes it in a background {@link Task} when the cached
 * value is missing or older than {@link #CACHE_TTL_MILLIS}. The TTL is
 * deliberately longer than the health widget's: license state changes rarely.
 * A single-flight guard coalesces a burst of refresh triggers.</p>
 *
 * <p>Until the first probe completes, and whenever the probe is inconclusive,
 * {@link #current()} reports {@link PdfLicenseState#UNKNOWN} — it never
 * claims licensed or evaluation on an unproven probe. Like the health cache,
 * an inconclusive result is cached and expires after the TTL, so a recovered
 * service is picked up rather than poisoned permanently.</p>
 *
 * <p>Mirrors {@code PdfReactorHealthWidget}'s caching pattern rather than
 * sharing its state, because the two probes have different cost/cadence and
 * this one is consumed outside the {@code tool} package.</p>
 */
public final class PdfLicenseProbe {

    /** Serve a cached state without re-probing until it is this old (5 min). */
    static final long CACHE_TTL_MILLIS = 300_000L;

    /** A cached state with the wall-clock millis it was taken. */
    private static final class Cached {

        private final PdfLicenseState state;
        private final long timestamp;

        Cached(PdfLicenseState state, long timestamp) {
            this.state = state;
            this.timestamp = timestamp;
        }
    }

    private static volatile Cached cached;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    /** Override points for tests: the probe, the clock, the refresh executor. */
    static Supplier<PdfLicenseState> probe = PdfLicenseProbe::probeGlobalService;
    static LongSupplier clock = System::currentTimeMillis;
    static Consumer<Runnable> refreshRunner = job -> new Task("PDFreactor", "license-probe") {

        @Override
        protected void doTask() {
            job.run();
        }
    }.submit();

    private PdfLicenseProbe() {
    }

    /**
     * Returns the last known license state immediately, kicking off a single
     * background refresh when the cache is missing or stale. Never blocks on
     * the service; {@link PdfLicenseState#UNKNOWN} until a probe has completed.
     *
     * @return Nonnull.
     */
    public static PdfLicenseState current() {
        Cached snapshot = cached;
        long now = clock.getAsLong();
        boolean stale = snapshot == null || now - snapshot.timestamp >= CACHE_TTL_MILLIS;
        if (stale && REFRESHING.compareAndSet(false, true)) {
            // Reset the single-flight flag if the submission itself throws (or
            // the task never starts) — otherwise it latches true forever.
            boolean submitted = false;
            try {
                refreshRunner.accept(() -> {
                    try {
                        cached = new Cached(probe.get(), clock.getAsLong());
                    } finally {
                        REFRESHING.set(false);
                    }
                });
                submitted = true;
            } finally {
                if (!submitted) {
                    REFRESHING.set(false);
                }
            }
        }
        return snapshot != null ? snapshot.state : PdfLicenseState.UNKNOWN;
    }

    /**
     * Probes the global service, failing gracefully to
     * {@link PdfLicenseState#UNKNOWN}: an unconfigured service URL (the
     * constructor throws) or any runtime error is inconclusive, never a false
     * licensed/evaluation claim.
     */
    private static PdfLicenseState probeGlobalService() {
        PdfReactorConfig config = PdfReactorConfigs.global();
        String serviceUrl = config.getServiceUrl();
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            return PdfLicenseState.UNKNOWN;
        }
        try {
            return new DefaultPdfReactorService(config).checkLicense();
        } catch (RuntimeException error) {
            return PdfLicenseState.UNKNOWN;
        }
    }

    /** Test hook: clears the process-local cache and the single-flight guard. */
    static void clearCacheForTest() {
        cached = null;
        REFRESHING.set(false);
    }
}
