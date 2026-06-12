package com.realobjects.brightspot.pdfreactor.publish;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.psddev.cms.db.Content;
import com.psddev.cms.db.Site;
import com.psddev.cms.tool.CmsTool;
import com.psddev.dari.db.Singleton;
import com.psddev.dari.db.State;
import com.psddev.dari.util.LocalStorageItem;
import com.psddev.dari.util.Settings;
import com.psddev.dari.util.StorageItem;
import com.psddev.test.db.TestDatabaseExtension;
import com.realobjects.brightspot.pdfreactor.GeneratedPdf;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;
import com.realobjects.brightspot.pdfreactor.PdfDiagnostics;
import com.realobjects.brightspot.pdfreactor.PdfReactorConfig;
import com.realobjects.brightspot.pdfreactor.PdfReactorException;
import com.realobjects.brightspot.pdfreactor.PdfReactorService;
import com.realobjects.brightspot.pdfreactor.PdfResult;
import com.realobjects.brightspot.pdfreactor.SettingsPdfReactorConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Publish-automation behavior on an in-memory H2 Dari database: the gates
 * (editor checkbox, admin site toggle, visibility), the off-thread
 * generation outcome fields, idempotency by attempt key, and the
 * fail-closed failure path. The task runner runs inline and the service is
 * mocked; the real conversion is covered by the e2e/UI suites.
 */
@ExtendWith(TestDatabaseExtension.class)
class PdfPublishAutomationTest {

    /** Dev-harness-free printable type for the tests. */
    public static class TestPrintable extends Content implements HasPdfRendering {

        private String headline;

        public void setHeadline(String headline) {
            this.headline = headline;
        }

        @Override
        public String getLabel() {
            return headline;
        }
    }

    private static final byte[] PDF = "%PDF-test".getBytes();

    private final List<Runnable> deferredJobs = new ArrayList<>();
    private PdfReactorService service;
    private Function<PdfReactorConfig, PdfReactorService> originalServiceFactory;
    private Consumer<Runnable> originalTaskRunner;

    @BeforeAll
    static void configureStorage() throws Exception {
        Path storageRoot = Files.createTempDirectory("pdfreactor-test-storage");
        Settings.setOverride(StorageItem.DEFAULT_STORAGE_SETTING, "test");
        Settings.setOverride(StorageItem.SETTING_PREFIX + "/test/class",
                LocalStorageItem.class.getName());
        Settings.setOverride(StorageItem.SETTING_PREFIX + "/test/" + LocalStorageItem.ROOT_PATH_SETTING,
                storageRoot.toString());
        Settings.setOverride(StorageItem.SETTING_PREFIX + "/test/baseUrl",
                "file://" + storageRoot);
    }

    @BeforeEach
    void install() {
        originalServiceFactory = PdfPublishAutomation.serviceFactory;
        originalTaskRunner = PdfPublishAutomation.taskRunner;
        service = mock(PdfReactorService.class);
        PdfPublishAutomation.serviceFactory = config -> service;
        // Synchronous-but-deferred: collects jobs so the test controls when
        // the "task" runs (mirrors the real off-thread execution order).
        PdfPublishAutomation.taskRunner = deferredJobs::add;
    }

    @AfterEach
    void restore() {
        PdfPublishAutomation.serviceFactory = originalServiceFactory;
        PdfPublishAutomation.taskRunner = originalTaskRunner;
        deferredJobs.clear();
        setSiteToggleDisabled(null);
    }

    private void runPendingJobs() {
        List<Runnable> jobs = new ArrayList<>(deferredJobs);
        deferredJobs.clear();
        jobs.forEach(Runnable::run);
    }

    private static void setSiteToggleDisabled(Boolean disabled) {
        CmsTool cms = Singleton.getInstance(CmsTool.class);
        cms.as(PdfReactorPublishSettings.class).setDisablePublishAutomation(disabled);
        cms.save();
    }

    private TestPrintable publishNew() {
        TestPrintable content = new TestPrintable();
        content.setHeadline("Printable " + java.util.UUID.randomUUID());
        content.save();
        return content;
    }

    private TestPrintable publishOwnedBy(Site site) {
        TestPrintable content = new TestPrintable();
        content.setHeadline("Printable " + java.util.UUID.randomUUID());
        content.as(Site.ObjectModification.class).setOwner(site);
        content.save();
        return content;
    }

    private static Site newSite(String name) {
        Site site = new Site();
        site.setName(name);
        site.save();
        return site;
    }

    private static PdfResult pdfResult() {
        return new PdfResult(PDF, "application/pdf", 2, PdfDiagnostics.empty());
    }

    @Test
    void publishGeneratesStoresAndRecords() {
        when(service.renderContent(any(), any())).thenReturn(pdfResult());

        TestPrintable content = publishNew();
        assertThat(deferredJobs).hasSize(1);
        runPendingJobs();

        HasPdfRenderingData data = reload(content);
        assertThat(data.getGeneratedPdf()).isNotNull();
        assertThat(data.getGeneratedPdfStatus()).startsWith("Success");
        assertThat(data.getGeneratedPdfDate()).isNotNull();
        assertThat(data.getGeneratedPdfAttemptKey()).isNotBlank();
        assertThat(GeneratedPdf.findLatestForContent(content.getId())).isNotNull();
    }

    @Test
    void postGenerationSaveDoesNotRetrigger() {
        when(service.renderContent(any(), any())).thenReturn(pdfResult());

        publishNew();
        runPendingJobs();

        // The generate() save re-fired afterSave; the attempt key must have
        // made it a no-op.
        assertThat(deferredJobs).isEmpty();
        verify(service, times(1)).renderContent(any(), any());
    }

    @Test
    void unchangedRepublishIsIdempotentButChangeRetries() {
        when(service.renderContent(any(), any())).thenReturn(pdfResult());

        TestPrintable content = publishNew();
        runPendingJobs();

        // Same revision, same options: no new attempt.
        TestPrintable reloaded = (TestPrintable) com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        reloaded.save();
        assertThat(deferredJobs).isEmpty();

        // Content change (publish updates the update date): retries.
        reloaded.setHeadline("Changed " + java.util.UUID.randomUUID());
        com.psddev.cms.db.Content.Static.publish(reloaded, null, null);
        assertThat(deferredJobs).hasSize(1);
        runPendingJobs();
        verify(service, times(2)).renderContent(any(), any());
    }

    @Test
    void publishIgnoresPerArticleTroubleshootingToggles() throws Exception {
        // The per-article Debug/Inspectable build toggles never affect
        // publishing — a published PDF is always the normal production build,
        // never a diagnostic one. So with the admin gate on AND both per-article
        // toggles on, the publish still runs and produces a non-debug PDF.
        when(service.renderContent(any(), any())).thenReturn(
                new PdfResult(PDF, "application/pdf", 1, PdfDiagnostics.fromResult(null)));
        CmsTool cms = Singleton.getInstance(CmsTool.class);
        cms.as(com.realobjects.brightspot.pdfreactor.PdfReactorSiteSettings.class)
                .setTroubleshootingEnabled(true);
        cms.save();
        try {
            TestPrintable content = new TestPrintable();
            content.setHeadline("Debug toggled " + java.util.UUID.randomUUID());
            content.as(HasPdfRenderingData.class).setDebugBuild(true);
            content.as(HasPdfRenderingData.class).setInspectableBuild(true);
            content.save();

            assertThat(deferredJobs).hasSize(1);
            runPendingJobs();

            org.mockito.ArgumentCaptor<com.realobjects.brightspot.pdfreactor.PdfRenderOptions> options =
                    org.mockito.ArgumentCaptor.forClass(
                            com.realobjects.brightspot.pdfreactor.PdfRenderOptions.class);
            verify(service).renderContent(any(), options.capture());
            assertThat(options.getValue().isDebug()).isFalse();
            assertThat(options.getValue().isInspectable()).isFalse();
        } finally {
            cms.as(com.realobjects.brightspot.pdfreactor.PdfReactorSiteSettings.class)
                    .setTroubleshootingEnabled(null);
            cms.save();
        }
    }

    @Test
    void unexpectedRuntimeFailureSurfacesAsFailedStatus() {
        // A non-PdfReactorException, non-IOException failure (here from the
        // service factory) must not escape into Task's generic catch and leave
        // a stale "Success" — the last-resort catch stamps Failed + notifies.
        when(service.renderContent(any(), any())).thenThrow(new IllegalStateException("boom"));

        TestPrintable content = publishNew();
        runPendingJobs();

        TestPrintable reloaded = (TestPrintable) com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        assertThat(reloaded.as(HasPdfRenderingData.class).getGeneratedPdfStatus())
                .startsWith("Failed");
        assertThat(reloaded.as(HasPdfRenderingData.class).getGeneratedPdfAttemptKey()).isNotNull();
    }

    @Test
    void midFlightConfigChangeDoesNotSupersedeGeneration() throws Exception {
        // The config snapshot (ResolvedPdfReactorConfig) is taken when the task
        // is scheduled; an admin changing a config setting during the conversion
        // window must not flip the recomputed cache key and drop the publish as
        // "superseded".
        when(service.renderContent(any(), any())).thenReturn(
                new PdfResult(PDF, "application/pdf", 1, PdfDiagnostics.fromResult(null)));

        TestPrintable content = publishNew(); // schedules the task; snapshot taken now
        Settings.setOverride(SettingsPdfReactorConfig.BASE_URL_SETTING, "https://changed.example.com/");
        try {
            runPendingJobs();

            verify(service).renderContent(any(), any());
            TestPrintable reloaded = (TestPrintable) com.psddev.dari.db.Query.fromAll()
                    .where("_id = ?", content.getId()).first();
            assertThat(reloaded.as(HasPdfRenderingData.class).getGeneratedPdfStatus())
                    .doesNotStartWith("Failed");
        } finally {
            Settings.setOverride(SettingsPdfReactorConfig.BASE_URL_SETTING, null);
        }
    }

    @Test
    void conversionReleasesItsPermit() {
        // The throttle permit is acquired around the conversion and released in
        // a finally, so a completed publish returns the semaphore to full.
        when(service.renderContent(any(), any())).thenReturn(
                new PdfResult(PDF, "application/pdf", 1, PdfDiagnostics.fromResult(null)));
        java.util.concurrent.Semaphore original = PdfPublishAutomation.conversionPermits;
        PdfPublishAutomation.conversionPermits = new java.util.concurrent.Semaphore(2);
        try {
            publishNew();
            runPendingJobs();
            assertThat(PdfPublishAutomation.conversionPermits.availablePermits()).isEqualTo(2);
        } finally {
            PdfPublishAutomation.conversionPermits = original;
        }
    }

    @Test
    void conversionReleasesItsPermitOnFailure() {
        when(service.renderContent(any(), any())).thenThrow(new IllegalStateException("boom"));
        java.util.concurrent.Semaphore original = PdfPublishAutomation.conversionPermits;
        PdfPublishAutomation.conversionPermits = new java.util.concurrent.Semaphore(2);
        try {
            publishNew();
            runPendingJobs();
            assertThat(PdfPublishAutomation.conversionPermits.availablePermits()).isEqualTo(2);
        } finally {
            PdfPublishAutomation.conversionPermits = original;
        }
    }

    @Test
    void editorOptOutBlocksGeneration() {
        TestPrintable content = new TestPrintable();
        content.setHeadline("Skipped");
        content.as(HasPdfRenderingData.class).setGeneratePdfOnPublish(false);
        content.save();

        assertThat(deferredJobs).isEmpty();
        verify(service, never()).renderContent(any(), any());
    }

    @Test
    void newContentGeneratesByDefault() {
        // afterCreate seeds the positive toggle on for new content, so the
        // checkbox renders (and persists) checked and the default = generate.
        TestPrintable content = new TestPrintable();
        assertThat(State.getInstance(content).get("pdfreactor.generatePdfOnPublish"))
                .as("the toggle is seeded on for new content (checkbox renders checked)")
                .isEqualTo(true);
        assertThat(content.as(HasPdfRenderingData.class).isGeneratePdfOnPublish()).isTrue();
    }

    @Test
    void legacyOptOutIsPreservedAfterReload() {
        // Content saved before the positive field existed carries only the
        // legacy inverted "skip" flag. An opt-out (skip=true) must stay opted
        // out — generation must not silently turn on for it.
        TestPrintable content = new TestPrintable();
        content.setHeadline("Legacy opt-out");
        State state = State.getInstance(content);
        state.getValues().remove("pdfreactor.generatePdfOnPublish");
        state.putByPath(HasPdfRenderingData.LEGACY_SKIP_FIELD, true);
        state.save();

        TestPrintable reloaded = (TestPrintable) com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        assertThat(reloaded.as(HasPdfRenderingData.class).isGeneratePdfOnPublish())
                .as("a legacy opt-out must keep generation off")
                .isFalse();
    }

    @Test
    void legacyContentWithoutOptOutGenerates() {
        // Legacy content that never opted out (no skip flag) generates.
        TestPrintable content = new TestPrintable();
        content.setHeadline("Legacy generate");
        State state = State.getInstance(content);
        state.getValues().remove("pdfreactor.generatePdfOnPublish");
        state.save();

        TestPrintable reloaded = (TestPrintable) com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        assertThat(reloaded.as(HasPdfRenderingData.class).isGeneratePdfOnPublish())
                .as("legacy content without an opt-out generates")
                .isTrue();
    }

    @Test
    void adminSiteToggleBlocksGeneration() {
        setSiteToggleDisabled(true);
        // The settings save itself must not enqueue anything.
        deferredJobs.clear();

        publishNew();

        assertThat(deferredJobs).isEmpty();
        verify(service, never()).renderContent(any(), any());
    }

    // Regression guard: the GLOBAL "disable" toggle must apply to content owned by
    // a site even when that site's per-site toggle is unset. Before the fix,
    // isEnabled read a primitive-boolean getter; SiteSettings.get returned the
    // site's non-blank `false` and never fell back to the global toggle, so a
    // global disable was silently ignored for any site-owned content.
    @Test
    void globalToggleBlocksContentOwnedByASiteWithUnsetPerSiteToggle() {
        Site site = newSite("Brand A");
        setSiteToggleDisabled(true); // global disable
        deferredJobs.clear();

        publishOwnedBy(site);

        assertThat(deferredJobs).isEmpty();
        verify(service, never()).renderContent(any(), any());
    }

    @Test
    void perSiteToggleBlocksContentOwnedByThatSite() {
        Site site = newSite("Brand B");
        site.as(PdfReactorPublishSettings.class).setDisablePublishAutomation(true);
        site.save();
        deferredJobs.clear();

        publishOwnedBy(site);

        assertThat(deferredJobs).isEmpty();
        verify(service, never()).renderContent(any(), any());
    }

    // Owner-resolution behavior: a per-site
    // toggle only governs content OWNED by that site. Content with no owner
    // resolves against the global record, so a per-site disable does not block
    // it.
    @Test
    void perSiteToggleDoesNotBlockUnownedContent() {
        Site site = newSite("Brand C");
        site.as(PdfReactorPublishSettings.class).setDisablePublishAutomation(true);
        site.save();
        when(service.renderContent(any(), any())).thenReturn(pdfResult());
        deferredJobs.clear();

        publishNew(); // no owner -> global (enabled)

        assertThat(deferredJobs).hasSize(1);
    }

    // The editor opt-out must still block on a re-publish AFTER the
    // content has already published a PDF once (the scenario from the manual
    // walkthrough), not only on a never-published item.
    @Test
    void editorSkipBlocksGenerationOnRepublishAfterAPreviousPdf() {
        when(service.renderContent(any(), any())).thenReturn(pdfResult());

        TestPrintable content = publishNew(); // generates PDF #1
        runPendingJobs();
        verify(service, times(1)).renderContent(any(), any());

        // Editor opts out, then re-publishes (a new revision / update date).
        TestPrintable reloaded = (TestPrintable) com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        reloaded.as(HasPdfRenderingData.class).setGeneratePdfOnPublish(false);
        reloaded.setHeadline("Changed " + java.util.UUID.randomUUID());
        com.psddev.cms.db.Content.Static.publish(reloaded, null, null);

        assertThat(deferredJobs).isEmpty();
        verify(service, times(1)).renderContent(any(), any()); // no second conversion
    }

    // The on-demand Generate action stamps the same content fields via
    // HasPdfRenderingData.recordSuccessfulGeneration, reusing the cache key the
    // publish hook computes for the revision. The resulting save must be a
    // no-op (the attempt key matches), so an on-demand stamp never re-fires
    // publish automation into a loop or a spurious second conversion.
    @Test
    void onDemandStampDoesNotRetriggerPublishAutomation() throws Exception {
        when(service.renderContent(any(), any())).thenReturn(pdfResult());

        TestPrintable content = publishNew();
        runPendingJobs(); // publish stamps the attempt key K for this revision
        deferredJobs.clear();

        // Simulate the on-demand Generate stamp on the same revision: record a
        // fresh generation with the key handle will recompute (the stored
        // attempt key), then save.
        Object reloaded = com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        State state = State.getInstance(reloaded);
        HasPdfRenderingData data = state.as(HasPdfRenderingData.class);
        String key = data.getGeneratedPdfAttemptKey();
        assertThat(key).isNotBlank();

        StorageItem item = StorageItem.Static.create();
        item.setPath("pdfreactor/on-demand-test.pdf");
        item.setContentType("application/pdf");
        item.setData(new java.io.ByteArrayInputStream(PDF));
        item.save();

        data.recordSuccessfulGeneration(item, pdfResult(), key, PDF.length);
        state.save();

        assertThat(deferredJobs).isEmpty();
        verify(service, times(1)).renderContent(any(), any());
    }

    // The publish path must never produce troubleshooting (debug/inspectable)
    // output — that is non-production and would be archived as the canonical
    // PDF. The publish options leave both off.
    @Test
    void publishOptionsNeverEnableTroubleshootingBuilds() {
        assertThat(PdfPublishAutomation.publishOptions().isDebug()).isFalse();
        assertThat(PdfPublishAutomation.publishOptions().isInspectable()).isFalse();
    }

    @Test
    void failureRecordsStatusAndStoresNothing() {
        when(service.renderContent(any(), any())).thenThrow(
                new PdfReactorException("PDFreactor conversion failed: boom",
                        null, PdfDiagnostics.empty()));

        TestPrintable content = publishNew();
        runPendingJobs();

        HasPdfRenderingData data = reload(content);
        assertThat(data.getGeneratedPdf()).isNull();
        assertThat(data.getGeneratedPdfStatus()).startsWith("Failed:");
        // Failed attempt recorded: no retry loop on the status save.
        assertThat(deferredJobs).isEmpty();
        Mockito.verify(service, times(1)).renderContent(any(), any());
    }

    @Test
    void invalidConfigSkipsGenerationWithoutLooping() {
        // A typo'd output-affecting setting makes config resolution throw on
        // the save thread; the publish must complete, schedule no task, and
        // not loop (the branch notifies but never re-saves the content).
        Settings.setOverride(com.realobjects.brightspot.pdfreactor.SettingsPdfReactorConfig
                .CONFORMANCE_SETTING, "NOT_A_REAL_CONFORMANCE");
        try {
            publishNew();
            assertThat(deferredJobs).isEmpty();
            verify(service, never()).renderContent(any(), any());
        } finally {
            Settings.setOverride(com.realobjects.brightspot.pdfreactor.SettingsPdfReactorConfig
                    .CONFORMANCE_SETTING, null);
        }
    }

    @Test
    void supersededTaskWritesNothing() {
        when(service.renderContent(any(), any())).thenReturn(pdfResult());

        TestPrintable content = publishNew(); // schedules the task for revision R1
        assertThat(deferredJobs).hasSize(1);
        Runnable supersededJob = deferredJobs.get(0);
        deferredJobs.clear();

        // A newer publish lands before the first task writes back: it changes
        // the update date (a new cache key) and schedules its own task, which
        // we drop here to isolate the superseded task's behavior.
        TestPrintable reloaded = (TestPrintable) com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        reloaded.setHeadline("Edited " + java.util.UUID.randomUUID());
        com.psddev.cms.db.Content.Static.publish(reloaded, null, null);
        deferredJobs.clear();

        // The original task converts, then on reload finds it no longer owns
        // generation for its revision and writes nothing — no content fields,
        // no cache record, no clobbering save, no exception.
        supersededJob.run();

        HasPdfRenderingData data = reload(content);
        assertThat(data.getGeneratedPdf()).isNull();
        assertThat(data.getGeneratedPdfStatus()).isNull();
        assertThat(GeneratedPdf.findLatestForContent(content.getId())).isNull();
        assertThat(deferredJobs).isEmpty();
        verify(service, times(1)).renderContent(any(), any());
    }

    private HasPdfRenderingData reload(TestPrintable content) {
        Object reloaded = com.psddev.dari.db.Query.fromAll()
                .where("_id = ?", content.getId()).first();
        return State.getInstance(reloaded).as(HasPdfRenderingData.class);
    }
}
