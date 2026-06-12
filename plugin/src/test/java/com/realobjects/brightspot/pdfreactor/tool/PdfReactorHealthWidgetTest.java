package com.realobjects.brightspot.pdfreactor.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.realobjects.brightspot.pdfreactor.PdfServiceHealth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache behavior of {@link PdfReactorHealthWidget#currentHealth()}: it never
 * blocks, serves a fresh cached value without re-probing, refreshes once the
 * TTL elapses, single-flights concurrent refreshes, and lets a DOWN result
 * expire so a recovered service is picked up (no permanent poisoning).
 */
class PdfReactorHealthWidgetTest {

    private final AtomicLong now = new AtomicLong();
    private Supplier<PdfServiceHealth> originalProbe;
    private LongSupplier originalClock;
    private Consumer<Runnable> originalRunner;

    @BeforeEach
    void install() {
        originalProbe = PdfReactorHealthWidget.healthProbe;
        originalClock = PdfReactorHealthWidget.clock;
        originalRunner = PdfReactorHealthWidget.refreshRunner;
        now.set(0L);
        PdfReactorHealthWidget.clock = now::get;
        PdfReactorHealthWidget.refreshRunner = Runnable::run; // inline by default
        PdfReactorHealthWidget.clearCacheForTest();
    }

    @AfterEach
    void restore() {
        PdfReactorHealthWidget.healthProbe = originalProbe;
        PdfReactorHealthWidget.clock = originalClock;
        PdfReactorHealthWidget.refreshRunner = originalRunner;
        PdfReactorHealthWidget.clearCacheForTest();
    }

    @Test
    void firstLoadProbesThenServesFromCache() {
        AtomicInteger probes = new AtomicInteger();
        PdfReactorHealthWidget.healthProbe = () -> {
            probes.incrementAndGet();
            return PdfServiceHealth.up("12.6.0");
        };

        // First load: nothing cached yet → schedules the probe, renders "checking…".
        assertThat(PdfReactorHealthWidget.currentHealth()).isNull();
        assertThat(probes.get()).isEqualTo(1);

        // Cache now warm and fresh: served without a second probe.
        PdfServiceHealth served = PdfReactorHealthWidget.currentHealth();
        assertThat(served).isNotNull();
        assertThat(served.isUp()).isTrue();
        assertThat(probes.get()).isEqualTo(1);
    }

    @Test
    void staleCacheRefreshesAfterTtl() {
        AtomicInteger probes = new AtomicInteger();
        PdfReactorHealthWidget.healthProbe = () -> {
            probes.incrementAndGet();
            return PdfServiceHealth.up("12.6.0");
        };

        PdfReactorHealthWidget.currentHealth(); // probe #1 at t=0
        assertThat(probes.get()).isEqualTo(1);

        now.set(PdfReactorHealthWidget.CACHE_TTL_MILLIS - 1);
        PdfReactorHealthWidget.currentHealth(); // within TTL: no re-probe
        assertThat(probes.get()).isEqualTo(1);

        now.set(PdfReactorHealthWidget.CACHE_TTL_MILLIS);
        PdfReactorHealthWidget.currentHealth(); // TTL elapsed: re-probe
        assertThat(probes.get()).isEqualTo(2);
    }

    @Test
    void downResultExpiresAndRetriesRatherThanPoisoning() {
        AtomicReference<PdfServiceHealth> next = new AtomicReference<>(PdfServiceHealth.down("boom"));
        PdfReactorHealthWidget.healthProbe = next::get;

        PdfReactorHealthWidget.currentHealth(); // probe #1 → DOWN cached
        assertThat(PdfReactorHealthWidget.currentHealth().isUp()).isFalse();

        // Service recovers; the DOWN entry must expire and be replaced.
        next.set(PdfServiceHealth.up("12.6.0"));
        now.set(PdfReactorHealthWidget.CACHE_TTL_MILLIS);
        PdfReactorHealthWidget.currentHealth(); // re-probe, cache updated to UP
        assertThat(PdfReactorHealthWidget.currentHealth().isUp()).isTrue();
    }

    @Test
    void singleFlightCoalescesConcurrentRefreshes() {
        List<Runnable> deferred = new ArrayList<>();
        PdfReactorHealthWidget.refreshRunner = deferred::add; // defer, don't run
        AtomicInteger probes = new AtomicInteger();
        PdfReactorHealthWidget.healthProbe = () -> {
            probes.incrementAndGet();
            return PdfServiceHealth.up("12.6.0");
        };

        PdfReactorHealthWidget.currentHealth(); // schedules one refresh
        PdfReactorHealthWidget.currentHealth(); // in-flight: must not schedule another
        assertThat(deferred).hasSize(1);
        assertThat(probes.get()).isZero();

        deferred.forEach(Runnable::run); // the single probe completes
        assertThat(probes.get()).isEqualTo(1);
        assertThat(PdfReactorHealthWidget.currentHealth().isUp()).isTrue();
    }
}
