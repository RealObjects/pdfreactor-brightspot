package com.realobjects.brightspot.pdfreactor.publish;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;

import com.psddev.cms.db.Site;
import com.psddev.cms.notification.ToolNotification;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.State;
import com.psddev.dari.db.ValidationException;
import com.psddev.dari.util.Settings;
import com.psddev.dari.util.StorageItem;
import com.psddev.dari.util.Task;
import com.realobjects.brightspot.pdfreactor.DefaultPdfReactorService;
import com.realobjects.brightspot.pdfreactor.GeneratedPdf;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;
import com.realobjects.brightspot.pdfreactor.PdfCacheKey;
import com.realobjects.brightspot.pdfreactor.PdfConfigFingerprint;
import com.realobjects.brightspot.pdfreactor.PdfProblemReport;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfig;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfigs;
import com.realobjects.brightspot.pdfreactor.PdfReactorException;
import com.realobjects.brightspot.pdfreactor.PdfReactorService;
import com.realobjects.brightspot.pdfreactor.PdfRenderOptions;
import com.realobjects.brightspot.pdfreactor.PdfResult;
import com.realobjects.brightspot.pdfreactor.SettingsPdfReactorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publish automation: triggered by
 * {@link HasPdfRenderingData#afterSave()}, checks the three opt-in gates —
 * developer (the {@link HasPdfRendering} marker got us here), admin
 * ({@link PdfReactorPublishSettings}, per site), editor (the per-article
 * "Generate PDF on publish" toggle) — plus the documented {@code State#isVisible} publish
 * filter (drafts and work-in-progress saves are invisible), then converts
 * <strong>off the save thread</strong> in a Dari {@link Task}.
 *
 * <p>{@code afterSave} fires on the node performing the save, so the
 * one-shot task runs exactly once per publish in a cluster. Idempotency
 * (and the guard against the post-generation save re-triggering the hook)
 * is the attempt cache key recorded on the content: same content revision +
 * same output-affecting options = no second attempt; a re-publish changes
 * the update date and therefore retries.</p>
 *
 * <p>The publish path fails closed on missing resources
 * ({@code MISSING_RESOURCE} error policy): a PDF with broken resources is never
 * archived. License problems do <em>not</em> block publishing — an unlicensed
 * (evaluation-mode) service archives watermarked output rather than failing
 * the publish; the health widget and preview banner warn about evaluation
 * mode. Failures are recorded in {@code generatedPdfStatus}, logged, and
 * published as a Brightspot notification topic.</p>
 */
public final class PdfPublishAutomation {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfPublishAutomation.class);

    private static final int DEFAULT_PUBLISH_CONCURRENCY = 3;

    /**
     * Bounds concurrent publish conversions. Each publish still submits its own
     * Dari {@link Task} (unbounded thread pool), but only this many run the
     * heavy conversion at once — so a bulk re-publish of N marked items does
     * not fire N simultaneous PDFreactor conversions, each holding a full PDF
     * (and ICC bytes) in heap. The supersede guard already makes
     * deferred execution safe. Sized from {@code pdfreactor/publishConcurrency}
     * (default {@value #DEFAULT_PUBLISH_CONCURRENCY}); overridable in tests.
     */
    static Semaphore conversionPermits = new Semaphore(Math.max(1,
            Settings.getOrDefault(int.class, SettingsPdfReactorConfig.PUBLISH_CONCURRENCY_SETTING,
                    DEFAULT_PUBLISH_CONCURRENCY)));

    /** Override points for tests: conversion service and task execution. */
    static Function<PdfReactorConfig, PdfReactorService> serviceFactory =
            config -> new DefaultPdfReactorService(config);
    static Consumer<Runnable> taskRunner = job -> new Task("PDFreactor", "publish-pdf") {

        @Override
        protected void doTask() {
            job.run();
        }
    }.submit();

    private PdfPublishAutomation() {
    }

    /**
     * Publish hook: checks the gates and schedules the conversion.
     */
    static void handle(HasPdfRendering content, HasPdfRenderingData data) {
        if (content == null) {
            return;
        }
        State state = State.getInstance(content);
        if (!shouldGenerate(state, data)) {
            return;
        }

        // Resolve the effective config (reading the ICC bytes) once here on
        // the save thread, then reuse the same instance for the fingerprint,
        // the supersede guard, and the conversion — instead of reading the
        // bytes on three separate config instances/threads. cacheKeyFor also
        // reads the output-affecting enum settings (conformance /
        // colorConversionIntent); a typo there throws. Since this runs on the
        // editor's save thread, a bad setting must degrade to "skip
        // generation, log" rather than risk disrupting the publish. The admin
        // fixes the setting; the next publish retries.
        PdfReactorConfig config;
        String cacheKey;
        try {
            config = PdfReactorConfigs.resolved(PdfReactorConfigs.forContent(content));
            cacheKey = cacheKeyFor(content, config);
        } catch (RuntimeException invalidConfig) {
            // Fail closed but not silently: a typo'd conformance/intent or an
            // unreadable ICC profile would otherwise stop publish PDFs with
            // only a log line. Notify so an admin sees it. Do NOT write+save a
            // status here — this runs in afterSave and the config is still
            // invalid, so a save would re-enter handle and loop (there is no
            // attempt key yet, since computing it is what threw).
            LOGGER.warn("Skipping PDF generation for content [{}]: invalid PDFreactor configuration.",
                    state.getId(), invalidConfig);
            notifyFailure(state, "invalid PDFreactor configuration: " + invalidConfig.getMessage());
            return;
        }
        // The publish automation is never affected by the per-article
        // debug/inspectable toggles: a published PDF is always the normal
        // production build, and the diagnostic builds (debug/inspectable) are
        // produced on the preview / on-demand Generate paths only — converted
        // fresh, streamed, and never stored or stamped. So nothing about
        // troubleshooting is consulted here.

        if (cacheKey.equals(data.getGeneratedPdfAttemptKey())) {
            // Already attempted for this revision + options (also breaks
            // the save loop caused by the post-generation save).
            return;
        }

        UUID contentId = state.getId();
        taskRunner.accept(() -> generate(contentId, cacheKey, config));
    }

    /**
     * The cache key identifying a generation attempt for the given content:
     * its revision (update date) plus the output-affecting options and config
     * fingerprint. Recomputed in {@link #generate} from the reloaded content
     * (reusing the same resolved config) to detect supersession by a newer
     * publish.
     */
    private static String cacheKeyFor(Object content, PdfReactorConfig config) {
        return PdfCacheKey.of(content, publishOptions(), PdfConfigFingerprint.of(config));
    }

    /**
     * Gate decision: published/visible state, editor checkbox, admin
     * per-site toggle. (The developer gate is the marker interface itself.)
     */
    static boolean shouldGenerate(State state, HasPdfRenderingData data) {
        if (state == null || state.getId() == null || !state.isVisible()) {
            return false;
        }
        if (!data.isGeneratePdfOnPublish()) {
            return false;
        }
        Site owner = state.as(Site.ObjectModification.class).getOwner();
        return PdfReactorPublishSettings.isEnabled(owner);
    }

    /**
     * Publish conversions fail closed on missing resources so a PDF with broken
     * resources is never archived; license problems are relaxed so an
     * unlicensed service archives watermarked output rather than blocking the
     * publish.
     */
    static PdfRenderOptions publishOptions() {
        return PdfRenderOptions.builder()
                .failOnMissingResources(true)
                .failOnLicenseProblems(false)
                .build();
    }

    /**
     * Runs in the task. Converts <em>first</em> (permalink fetch — there is
     * no request context here), then reloads the content immediately before
     * writing, so the long conversion window is not part of the read→write
     * gap. If a newer publish changed the content during the conversion, this
     * task is superseded and writes nothing — neither the content fields nor
     * the {@link GeneratedPdf} cache record (which {@code findLatestForContent}
     * sorts by generation time, so a late save here would wrongly become the
     * "latest" shown/downloaded PDF).
     *
     * <p>This shrinks the clobber window from the whole conversion (seconds)
     * to reload→save (milliseconds); it does not make it strictly race-free.
     * Strict race-freedom would need per-field atomic writes or the
     * structural {@code GeneratedPdf}-as-source-of-truth change.</p>
     */
    private static void generate(UUID contentId, String cacheKey, PdfReactorConfig config) {
        try {
            runGenerate(contentId, cacheKey, config);
        } catch (RuntimeException unexpected) {
            // Last-resort: NO failure class may leave the previous "Success (…)"
            // status standing on the edit form for a publish whose PDF failed.
            // The narrow catches below handle conversion (PdfReactorException) and
            // storage (IOException); this catches everything else — a Dari
            // ValidationException, a runtime fault in the service factory or
            // state.save(), etc. — and stamps a generic failure + attempt key +
            // notifies, exactly as those do.
            LOGGER.warn("Unexpected failure generating publish PDF for content [{}].", contentId, unexpected);
            stampUnexpectedFailure(contentId, cacheKey, unexpected);
        }
    }

    private static void runGenerate(UUID contentId, String cacheKey, PdfReactorConfig config) {
        Object content = Query.fromAll().where("_id = ?", contentId).first();
        if (!(content instanceof HasPdfRendering)) {
            return;
        }

        // Convert first, before touching the content record. Reuse the
        // resolved config from handle so the ICC bytes are not read again.
        PdfResult result = null;
        String failureDetail = null;
        try {
            // Throttle concurrent conversions: bound the heavy work
            // and the in-heap PDF bytes across a burst of publishes.
            conversionPermits.acquire();
        } catch (InterruptedException interrupted) {
            // Shutdown / interruption before converting: leave the status
            // untouched (a later publish retries) and propagate the interrupt.
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted before converting publish PDF for content [{}].", contentId, interrupted);
            return;
        }
        try {
            result = serviceFactory.apply(config).renderContent(content, publishOptions());
        } catch (PdfReactorException error) {
            PdfProblemReport report = PdfProblemReport.of(error);
            failureDetail = report.getDetails().isEmpty()
                    ? error.getMessage()
                    : report.getDetails().get(0);
            LOGGER.warn("Publish PDF generation failed for content [{}].", contentId, error);
        } finally {
            conversionPermits.release();
        }

        // Reload immediately before the write and only proceed if this task
        // still owns generation for the revision it was scheduled with.
        Object fresh = Query.fromAll().where("_id = ?", contentId).first();
        if (!(fresh instanceof HasPdfRendering)) {
            return;
        }
        if (!cacheKey.equals(cacheKeyFor(fresh, config))) {
            LOGGER.info("Skipping superseded publish PDF for content [{}]"
                    + " (a newer publish now owns generation).", contentId);
            return;
        }

        State state = State.getInstance(fresh);
        HasPdfRenderingData data = state.as(HasPdfRenderingData.class);
        data.setGeneratedPdfAttemptKey(cacheKey);

        if (result != null) {
            try {
                byte[] document = result.getDocument();
                StorageItem item = storePdf(contentId, cacheKey, document);

                GeneratedPdf cacheEntry = GeneratedPdf.findByCacheKey(cacheKey);
                if (cacheEntry == null) {
                    cacheEntry = new GeneratedPdf();
                    cacheEntry.setCacheKey(cacheKey);
                    cacheEntry.setContentId(contentId);
                }
                cacheEntry.setPdf(item);
                cacheEntry.setGenerated(new Date());
                cacheEntry.setNumberOfPages(result.getNumberOfPages());
                cacheEntry.setByteSize(document.length);
                try {
                    cacheEntry.save();
                } catch (ValidationException duplicate) {
                    // Another generator raced us to the unique cacheKey index
                    // (e.g. the editor clicked "Generate PDF" while this task
                    // was converting). First writer wins: reuse its record
                    // rather than failing the publish.
                    GeneratedPdf winner = GeneratedPdf.findByCacheKey(cacheKey);
                    if (winner == null) {
                        throw duplicate;
                    }
                    LOGGER.info("Reusing concurrently-saved GeneratedPdf for content [{}].", contentId);
                }

                data.setGeneratedPdf(item);
                data.setGeneratedPdfDate(new Date());
                data.setGeneratedPdfStatus(successStatus(result, document.length));
                LOGGER.info("Generated publish PDF for content [{}].", contentId);

                try {
                    GeneratedPdf.pruneForContent(contentId);
                } catch (RuntimeException pruneError) {
                    LOGGER.warn("Pruning old generated PDFs failed for content [{}].",
                            contentId, pruneError);
                }

            } catch (IOException error) {
                data.setGeneratedPdf(null);
                data.setGeneratedPdfDate(new Date());
                data.setGeneratedPdfStatus("Failed: could not store the PDF.");
                LOGGER.warn("Storing the publish PDF failed for content [{}].", contentId, error);
                notifyFailure(state, "could not store the PDF: " + error.getMessage());
            }
        } else {
            String detail = failureDetail(failureDetail, null);
            data.setGeneratedPdf(null);
            data.setGeneratedPdfDate(new Date());
            data.setGeneratedPdfStatus("Failed: " + detail);
            notifyFailure(state, detail);
        }

        // Saving re-fires afterSave; the attempt key recorded above makes
        // that re-entry a no-op.
        state.save();
    }

    /**
     * Records an unexpected failure on the content so a publish whose PDF
     * failed for a non-conversion, non-storage reason never leaves the previous
     * "Success (…)" status standing. Reloads the content, stamps the attempt
     * key + a "Failed: …" status, and notifies. Its own failure is only logged
     * (there is nothing better to do off-thread).
     */
    private static void stampUnexpectedFailure(UUID contentId, String cacheKey, RuntimeException error) {
        try {
            Object fresh = Query.fromAll().where("_id = ?", contentId).first();
            if (!(fresh instanceof HasPdfRendering)) {
                return;
            }
            State state = State.getInstance(fresh);
            HasPdfRenderingData data = state.as(HasPdfRenderingData.class);
            String detail = failureDetail(null, error);
            data.setGeneratedPdfAttemptKey(cacheKey);
            data.setGeneratedPdf(null);
            data.setGeneratedPdfDate(new Date());
            data.setGeneratedPdfStatus("Failed: " + detail);
            notifyFailure(state, detail);
            state.save();
        } catch (RuntimeException stampError) {
            LOGGER.warn("Could not record the publish PDF failure for content [{}].", contentId, stampError);
        }
    }

    /**
     * A non-null, non-empty failure detail: the given detail if present, else
     * the throwable's message, else its class name — never the literal
     * {@code "null"} (which {@code "Failed: " + null} would produce).
     */
    private static String failureDetail(String detail, Throwable error) {
        if (detail != null && !detail.trim().isEmpty()) {
            return detail;
        }
        if (error != null) {
            String message = error.getMessage();
            return message != null && !message.trim().isEmpty()
                    ? message
                    : error.getClass().getSimpleName();
        }
        return "unknown error";
    }

    private static StorageItem storePdf(UUID contentId, String cacheKey, byte[] document)
            throws IOException {

        StorageItem item = StorageItem.Static.create();
        item.setPath(PdfCacheKey.storagePath(contentId, cacheKey));
        item.setContentType("application/pdf");
        item.setData(new ByteArrayInputStream(document));
        item.save();
        return item;
    }

    /**
     * Success status including the output size and the non-fatal diagnostics
     * counts (missing resources / failed connections) instead of ignoring them.
     *
     * @param byteSize Size of the generated PDF in bytes; omitted from the
     *                 status when not known ({@code <= 0}).
     */
    static String successStatus(PdfResult result, long byteSize) {
        int pages = result.getNumberOfPages();
        StringBuilder status = new StringBuilder("Success (")
                .append(pages).append(pages == 1 ? " page" : " pages");
        String size = GeneratedPdf.humanReadableSize(byteSize);
        if (size != null) {
            status.append("; ").append(size);
        }
        int missing = result.getDiagnostics().getMissingResources().size();
        int failedConnections = result.getDiagnostics().getFailedConnections().size();
        if (missing > 0) {
            status.append("; ").append(missing).append(" missing resource")
                    .append(missing == 1 ? "" : "s");
        }
        if (failedConnections > 0) {
            status.append("; ").append(failedConnections).append(" connection issue")
                    .append(failedConnections == 1 ? "" : "s");
        }
        return status.append(")").toString();
    }

    /**
     * Publishes the failure notification, wrapped so a notification problem
     * never breaks the publish task.
     */
    private static void notifyFailure(State state, String detail) {
        try {
            Site owner = state.as(Site.ObjectModification.class).getOwner();
            PdfPublishFailurePayload payload = new PdfPublishFailurePayload();
            payload.setContentId(state.getId());
            payload.setContentLabel(state.getLabel());
            payload.setSiteName(owner != null ? State.getInstance(owner).getLabel() : null);
            payload.setErrorDetail(detail);
            payload.setFailedAt(new Date());
            ToolNotification.publish(PdfPublishFailureTopic.class, payload);
        } catch (RuntimeException notifyError) {
            LOGGER.warn("Could not publish the PDF failure notification for content [{}].",
                    state.getId(), notifyError);
        }
    }
}
