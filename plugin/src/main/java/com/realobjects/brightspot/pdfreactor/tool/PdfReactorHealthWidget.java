package com.realobjects.brightspot.pdfreactor.tool;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.psddev.cms.tool.Dashboard;
import com.psddev.cms.tool.DashboardWidget;
import com.psddev.cms.ui.ToolLocalization;
import com.psddev.dari.html.RawNode;
import com.psddev.dari.html.content.FlowContent;
import com.psddev.dari.html.content.PhrasingContent;
import com.psddev.dari.util.Task;
import com.realobjects.brightspot.pdfreactor.DefaultPdfReactorService;
import com.realobjects.brightspot.pdfreactor.PdfLicenseProbe;
import com.realobjects.brightspot.pdfreactor.PdfLicenseState;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfig;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfigs;
import com.realobjects.brightspot.pdfreactor.PdfServiceHealth;
import com.realobjects.brightspot.pdfreactor.ToolResources;

import static com.psddev.dari.html.Nodes.DIV;
import static com.psddev.dari.html.Nodes.H2;
import static com.psddev.dari.html.Nodes.P;
import static com.psddev.dari.html.Nodes.SCRIPT;
import static com.psddev.dari.html.Nodes.SPAN;
import static com.psddev.dari.html.Nodes.text;

/**
 * Dashboard widget reporting PDFreactor <em>service</em> health (distinct
 * from per-conversion resource failures, which surface as diagnostics): UP
 * with the service version, or DOWN with the error. Admins add it to a
 * dashboard (Admin → Dashboards); there is no auto-registration.
 *
 * <p>The render never blocks on the service. It returns the last known
 * health immediately and refreshes it in a background {@link Task} when the
 * cached value is missing or older than {@link #CACHE_TTL_MILLIS}; the very
 * first load (before any probe has completed) shows "checking…". A
 * single-flight guard keeps a burst of dashboard loads after each TTL expiry
 * from each firing its own probe. The probe itself uses the short
 * {@link PdfReactorConfig#getHealthTimeoutMillis()} so a slow service cannot
 * tie up the background thread for the full conversion timeout.</p>
 *
 * <p>The cache is process-local, so each node in a cluster probes
 * independently — intentional: a health indicator does not warrant
 * shared-state coordination.</p>
 *
 * <p><b>Live update.</b> The platform's dashboard auto-refresh
 * ({@code cms/script/v3/dashboard.js}) re-fetches each {@code .dashboard-widget}
 * frame only on an RTC {@code PublishBroadcast} — an event unrelated to
 * service health — so on its own the widget would only repaint when an editor
 * publishes content (or on a full reload). To keep the status current, the
 * widget re-fetches its <em>own</em> dashboard-widget frame every
 * {@link #CLIENT_POLL_MILLIS}, reusing the platform's exact re-fetch mechanism
 * ({@code data-dashboard-widget-url} → AJAX → replace the frame's HTML →
 * re-trigger {@code create}/{@code load}) and honoring the same
 * hover/interaction guards. Each re-fetch re-injects the poll script, so the
 * {@code setTimeout} chain runs continuously until the page is left. The poll
 * interval matches {@link #CACHE_TTL_MILLIS}, so the client never out-paces the
 * server-side cache.</p>
 *
 * <p>Uses the current {@code getWidget(Dashboard)} contract returning a
 * dari-html node ({@code writeHtml} is deprecated-for-removal), mirroring the
 * platform widgets' {@code DashboardWidget} / {@code DashboardWidget-title}
 * chrome.</p>
 */
public class PdfReactorHealthWidget extends DashboardWidget {

    /** A cached probe result with the wall-clock millis it was taken. */
    private static final class CachedHealth {

        private final PdfServiceHealth health;
        private final long timestamp;

        CachedHealth(PdfServiceHealth health, long timestamp) {
            this.health = health;
            this.timestamp = timestamp;
        }
    }

    /** Serve a cached result without re-probing until it is this old. */
    static final long CACHE_TTL_MILLIS = 30_000L;

    /**
     * Steady-state cadence at which the client re-fetches the widget frame once
     * the status has resolved (UP/DOWN). Matched to {@link #CACHE_TTL_MILLIS} so
     * a poll never out-paces the server cache (each poll past the TTL triggers
     * at most one fresh background probe).
     */
    static final long CLIENT_POLL_MILLIS = CACHE_TTL_MILLIS;

    /**
     * Faster re-fetch cadence while the status is still resolving (the
     * "Checking…" render), so the first load displays the already-running
     * probe's result within a second or two instead of after a full
     * {@link #CLIENT_POLL_MILLIS} cycle. The server-side single-flight guard +
     * cache mean these extra frame fetches just serve the in-flight/cached
     * result — no probe storm.
     */
    static final long CLIENT_FAST_POLL_MILLIS = 1_500L;

    private static volatile CachedHealth cached;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    /** Override points for tests: the probe, the clock, the refresh executor. */
    static Supplier<PdfServiceHealth> healthProbe = PdfReactorHealthWidget::probeGlobalService;
    static LongSupplier clock = System::currentTimeMillis;
    static Consumer<Runnable> refreshRunner = job -> new Task("PDFreactor", "health-probe") {

        @Override
        protected void doTask() {
            job.run();
        }
    }.submit();

    @Override
    public Object getWidget(Dashboard dashboard) {
        // A fresh per-render id lets the poll script locate its own widget
        // container reliably: the platform re-fetches via jQuery .html(), which
        // evaluates injected scripts through a detached node, so
        // document.currentScript cannot be used to walk up to the frame.
        String pollId = "PdfReactorHealth-" + UUID.randomUUID();

        // Resolve the health once here — currentHealth() also kicks off the
        // background probe on a cold cache. "Pending" = configured but no probe
        // result yet (the "Checking…" render); the poll script then re-fetches
        // FAST until it resolves, so the dashboard shows the already-running
        // probe's result in a second or two instead of waiting a full 30 s
        // cache cycle for the first re-fetch.
        String serviceUrl = PdfReactorConfigs.global().getServiceUrl();
        boolean configured = serviceUrl != null && !serviceUrl.trim().isEmpty();
        PdfServiceHealth health = configured ? currentHealth() : null;
        boolean pending = configured && health == null;

        return DIV.className("DashboardWidget").id(pollId)
                .attr("data-pdf-health-pending", pending ? "true" : null)
                .with(
                        H2.className("DashboardWidget-title").with(titleContent()),
                        statusNode(configured, health),
                        SCRIPT.type("text/javascript").with(pollScript(pollId)));
    }

    /**
     * Self-rescheduling poll that re-fetches this widget's dashboard frame via
     * the platform's own mechanism — every {@link #CLIENT_FAST_POLL_MILLIS}
     * while the status is still resolving (the "Checking…" render carries
     * {@code data-pdf-health-pending}), then every {@link #CLIENT_POLL_MILLIS}
     * once UP/DOWN is shown. When
     * the tab is hidden or the user is interacting with the widget it reschedules
     * without fetching (so a hidden tab does not permanently end the loop). After
     * a fetch it does not reschedule here: the replacement HTML re-injects this
     * script, which re-arms the loop — so polling continues for as long as the
     * page is open. Clearing any timer stored on the element keeps a single loop
     * alive across the platform's own RTC-driven refreshes.
     */
    private static String pollScript(String pollId) {
        return "(function(){"
                + "var self=document.getElementById('" + pollId + "');"
                + "var w=self&&self.closest('[data-dashboard-widget-url]');"
                + "if(!w||!window.jQuery)return;"
                + "if(w.__pdfHealthTimer){clearTimeout(w.__pdfHealthTimer);}"
                // While the status is still resolving this render carries
                // data-pdf-health-pending, so poll FAST until it settles; each
                // re-fetch re-injects this script with the new render's flag, so
                // it drops to the normal cadence as soon as UP/DOWN is shown.
                + "var delay=(self.getAttribute('data-pdf-health-pending')==='true')?"
                + CLIENT_FAST_POLL_MILLIS + ":" + CLIENT_POLL_MILLIS + ";"
                + "function guarded(){"
                + "if(document.visibilityState==='hidden')return true;"
                + "var $w=jQuery(w);"
                + "return $w.is(':hover')||$w.data('refresh-disabled')===true||$w.find('.dropDown-list-open').length>0;"
                + "}"
                + "function tick(){"
                + "if(!document.body.contains(w))return;"
                + "if(guarded()){w.__pdfHealthTimer=setTimeout(tick,delay);return;}"
                + "var url=w.getAttribute('data-dashboard-widget-url');"
                + "if(!url){w.__pdfHealthTimer=setTimeout(tick,delay);return;}"
                + "jQuery.ajax({cache:false,type:'get',url:url,complete:function(r){"
                // Only replace the DOM with a genuine widget fragment (status 200
                // and our own marker present). On a network error (empty body),
                // a session-expiry login redirect, or any non-200, keep the
                // current DOM and reschedule — so the loop survives transient
                // outages instead of wiping the widget or injecting the login page.
                + "if(r.status===200&&r.responseText&&r.responseText.indexOf('PdfReactorHealth-')!==-1){"
                + "var $w=jQuery(w);$w.html(r.responseText);"
                + "$w.trigger('create');$w.trigger('load');$w.trigger('frame-load');$w.resize();"
                + "}else{w.__pdfHealthTimer=setTimeout(tick,delay);}"
                + "}});"
                + "}"
                + "w.__pdfHealthTimer=setTimeout(tick,delay);"
                + "})();";
    }

    /**
     * The widget title: the PDFreactor circle mark next to the title-case
     * "PDFreactor Service" heading. The logo is the plugin's own SVG asset
     * inlined into the Tool DOM (the plugin serves no static-file URLs); the
     * XML prolog/comment is stripped so the inlined fragment is valid HTML.
     * Sizing/alignment come from the {@code PdfWidget-title*} rules in
     * {@code tool-head.css}.
     */
    private static PhrasingContent titleContent() {
        String svg = ToolResources.text(ToolResources.WEB + "pdfreactor-logo.svg");
        int start = svg.indexOf("<svg");
        String inlineSvg = start >= 0 ? svg.substring(start) : svg;
        return SPAN.className("PdfWidget-title").with(
                SPAN.className("PdfWidget-titleLogo").with(new RawNode(inlineSvg)),
                text(localize("title", "PDFreactor Service")));
    }

    private FlowContent statusNode(boolean configured, PdfServiceHealth health) {
        // "Not configured" is resolved/localized in the request (getWidget reads
        // the setting) — the background probe (which produces the other DOWN
        // detail) has no locale.
        if (!configured) {
            return down(localize("status.notConfigured", "not configured (set pdfreactor/serviceUrl)"));
        }
        if (health == null) {
            return P.with(localize("status.checking", "Checking…"));
        }
        if (health.isUp()) {
            String version = health.getVersion();
            String line = localize("status.up", "UP")
                    + (version != null ? " — PDFreactor " + version : "");
            // Append the cached license state when the probe has concluded
            // (UNKNOWN — not yet probed or inconclusive — adds nothing). The
            // probe is background-cached, never run inline here; calling
            // current() also kicks off the background license probe, so the
            // resolved line appears on a later poll.
            PdfLicenseState license = PdfLicenseProbe.current();
            if (license == PdfLicenseState.LICENSED) {
                line += " " + localize("status.licensed", "(licensed)");
            } else if (license == PdfLicenseState.EVALUATION) {
                line += " " + localize("status.evaluation",
                        "(evaluation — output is watermarked)");
            }
            return P.with(line);
        }
        // A DOWN with a configured URL is a connection/communication failure.
        // Show a short, friendly line only — the full client error (host,
        // java.net cause, stack) is logged at WARN by checkHealth for whoever
        // is debugging it; dumping it into the dashboard widget is noise.
        return down(localize("status.connectionError",
                "Error connecting to PDFreactor Web Service"));
    }

    /**
     * A DOWN / not-configured line, rendered as the same plain {@code <p>} the
     * UP line uses so both statuses share one layout and DOM. The strong signal
     * is purely ours — a bold, red {@code PdfWidget-statusDown} (skin red token
     * with a literal fallback) plus a leading warning glyph baked into the text.
     * It deliberately does NOT reuse the skin's themed {@code .Message} recipe:
     * that recipe's {@code onDomInsert} appends and positions its own
     * {@code .Message-icon}, which never sat right in the dashboard-widget
     * context and resisted several alignment fixes.
     */
    private static FlowContent down(String detail) {
        return P.className("PdfWidget-statusDown")
                .with("⚠ " + localize("status.down", "DOWN") + " — " + detail);
    }

    private static String localize(String key, String fallback) {
        return ToolLocalization.text(PdfReactorHealthWidget.class, key, fallback);
    }

    /**
     * Returns the last known health immediately, kicking off a single
     * background refresh when the cache is missing or stale. Never blocks on
     * the service. {@code null} means "no probe has completed yet".
     */
    static PdfServiceHealth currentHealth() {
        CachedHealth snapshot = cached;
        long now = clock.getAsLong();
        boolean stale = snapshot == null || now - snapshot.timestamp >= CACHE_TTL_MILLIS;
        if (stale && REFRESHING.compareAndSet(false, true)) {
            // Reset the single-flight flag if the submission itself throws (or
            // the task never starts) — otherwise it latches true forever and
            // the state freezes until a JVM restart.
            boolean submitted = false;
            try {
                refreshRunner.accept(() -> {
                    try {
                        cached = new CachedHealth(healthProbe.get(), clock.getAsLong());
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
        return snapshot != null ? snapshot.health : null;
    }

    /**
     * Probes the global service, failing gracefully: an unconfigured service
     * URL (the constructor throws) or any runtime error reports DOWN rather
     * than breaking the dashboard. A DOWN result is cached like any other and
     * expires after the TTL, so the probe retries — a transient outage never
     * poisons the cache permanently.
     */
    private static PdfServiceHealth probeGlobalService() {
        PdfReactorConfig config = PdfReactorConfigs.global();
        String serviceUrl = config.getServiceUrl();
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            return PdfServiceHealth.down("not configured (set pdfreactor/serviceUrl)");
        }
        try {
            return new DefaultPdfReactorService(config).checkHealth();
        } catch (RuntimeException error) {
            return PdfServiceHealth.down(error.getMessage());
        }
    }

    /** Test hook: clears the process-local cache and the single-flight guard. */
    static void clearCacheForTest() {
        cached = null;
        REFRESHING.set(false);
    }
}
