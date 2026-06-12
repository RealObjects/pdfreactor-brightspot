package com.realobjects.brightspot.pdfreactor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache behavior of {@link PdfLicenseProbe#current()}: it never blocks,
 * reports UNKNOWN until the first probe completes, serves a fresh cached value
 * without re-probing, refreshes once the (longer) TTL elapses, single-flights
 * concurrent refreshes, and lets an inconclusive result expire so a probe is
 * retried (no permanent poisoning) — never claiming licensed/eval unproven.
 */
class PdfLicenseProbeTest {

    private final AtomicLong now = new AtomicLong();
    private Supplier<PdfLicenseState> originalProbe;
    private LongSupplier originalClock;
    private Consumer<Runnable> originalRunner;

    @BeforeEach
    void install() {
        originalProbe = PdfLicenseProbe.probe;
        originalClock = PdfLicenseProbe.clock;
        originalRunner = PdfLicenseProbe.refreshRunner;
        now.set(0L);
        PdfLicenseProbe.clock = now::get;
        PdfLicenseProbe.refreshRunner = Runnable::run; // inline by default
        PdfLicenseProbe.clearCacheForTest();
    }

    @AfterEach
    void restore() {
        PdfLicenseProbe.probe = originalProbe;
        PdfLicenseProbe.clock = originalClock;
        PdfLicenseProbe.refreshRunner = originalRunner;
        PdfLicenseProbe.clearCacheForTest();
    }

    @Test
    void firstLoadIsUnknownThenServesFromCache() {
        AtomicInteger probes = new AtomicInteger();
        PdfLicenseProbe.probe = () -> {
            probes.incrementAndGet();
            return PdfLicenseState.LICENSED;
        };

        // First load: nothing cached yet -> schedules the probe, reports UNKNOWN.
        assertThat(PdfLicenseProbe.current()).isEqualTo(PdfLicenseState.UNKNOWN);
        assertThat(probes.get()).isEqualTo(1);

        // Cache now warm and fresh: served without a second probe.
        assertThat(PdfLicenseProbe.current()).isEqualTo(PdfLicenseState.LICENSED);
        assertThat(probes.get()).isEqualTo(1);
    }

    @Test
    void staleCacheRefreshesAfterTtl() {
        AtomicInteger probes = new AtomicInteger();
        PdfLicenseProbe.probe = () -> {
            probes.incrementAndGet();
            return PdfLicenseState.EVALUATION;
        };

        PdfLicenseProbe.current(); // probe #1 at t=0
        assertThat(probes.get()).isEqualTo(1);

        now.set(PdfLicenseProbe.CACHE_TTL_MILLIS - 1);
        PdfLicenseProbe.current(); // within TTL: no re-probe
        assertThat(probes.get()).isEqualTo(1);

        now.set(PdfLicenseProbe.CACHE_TTL_MILLIS);
        PdfLicenseProbe.current(); // TTL elapsed: re-probe
        assertThat(probes.get()).isEqualTo(2);
    }

    @Test
    void unknownResultExpiresAndRetriesRatherThanPoisoning() {
        AtomicReference<PdfLicenseState> next = new AtomicReference<>(PdfLicenseState.UNKNOWN);
        PdfLicenseProbe.probe = next::get;

        PdfLicenseProbe.current(); // probe #1 -> UNKNOWN cached
        assertThat(PdfLicenseProbe.current()).isEqualTo(PdfLicenseState.UNKNOWN);

        // Service becomes probeable; the UNKNOWN entry must expire and be replaced.
        next.set(PdfLicenseState.LICENSED);
        now.set(PdfLicenseProbe.CACHE_TTL_MILLIS);
        PdfLicenseProbe.current(); // re-probe, cache updated
        assertThat(PdfLicenseProbe.current()).isEqualTo(PdfLicenseState.LICENSED);
    }

    @Test
    void latchResetsWhenSubmissionThrows() {
        // If the refresh executor itself throws, the single-flight flag must
        // not latch true forever (which would freeze the state until restart).
        PdfLicenseProbe.refreshRunner = job -> {
            throw new IllegalStateException("executor down");
        };
        AtomicInteger probes = new AtomicInteger();
        PdfLicenseProbe.probe = () -> {
            probes.incrementAndGet();
            return PdfLicenseState.LICENSED;
        };

        try {
            PdfLicenseProbe.current();
        } catch (IllegalStateException expected) {
            // The submission propagated; what matters is the flag was reset.
        }

        // A subsequent call with a working runner must be able to probe again.
        PdfLicenseProbe.refreshRunner = Runnable::run;
        PdfLicenseProbe.current();
        assertThat(probes.get()).isEqualTo(1);
    }

    @Test
    void singleFlightCoalescesConcurrentRefreshes() {
        List<Runnable> deferred = new ArrayList<>();
        PdfLicenseProbe.refreshRunner = deferred::add; // defer, don't run
        AtomicInteger probes = new AtomicInteger();
        PdfLicenseProbe.probe = () -> {
            probes.incrementAndGet();
            return PdfLicenseState.LICENSED;
        };

        PdfLicenseProbe.current(); // schedules one refresh
        PdfLicenseProbe.current(); // in-flight: must not schedule another
        assertThat(deferred).hasSize(1);
        assertThat(probes.get()).isZero();

        deferred.forEach(Runnable::run); // the single probe completes
        assertThat(probes.get()).isEqualTo(1);
        assertThat(PdfLicenseProbe.current()).isEqualTo(PdfLicenseState.LICENSED);
    }
}
