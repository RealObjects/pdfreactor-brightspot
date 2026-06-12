package com.realobjects.brightspot.pdfreactor.uitest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Playwright UI tests (Phase 4b) against the running local CMS stack.
 *
 * <p>Preconditions: {@code docker compose up -d} with the current web.war
 * deployed. The suite bootstraps everything else through the dev harness's
 * {@code /cms/_seed-ui-test} endpoint (test user, PDF preview type in
 * Sites &amp; Settings, sample articles) and logs in through the real
 * login form.</p>
 *
 * <p>Covers the regressions found during the manual Phase 3–4 verification:
 * the preview must load as {@code application/pdf}, the iframe must fill
 * the pane unscaled, the header controls must be visible and equally
 * sized, refresh must re-convert, generate must store/cache and fail
 * closed on broken resources, and the shared preview must work.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PdfToolUiTest {

    private static final String BASE = System.getProperty("uiTest.baseUrl", "http://localhost");
    private static final String PAGED_WEB = "The Paged Web: Print-Grade PDF from CMS Content";
    private static final String LONG_FORM = "Sixty Paragraphs of Pagination";
    private static final String DIAGNOSTICS = "Diagnostics by Example";

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;
    private static final Map<String, JsonNode> ARTICLES = new HashMap<>();
    private static JsonNode settings;

    /**
     * Whether the PDFreactor service behind the stack is licensed; detected
     * once in {@link #bootstrapAndLogIn()}. Unlicensed, the service runs in
     * evaluation mode: previews answer with the watermarked carrier page and
     * the storing paths (Generate / publish) fail closed — several tests
     * branch or skip on this.
     */
    private static boolean licensed;

    private Page page;

    @BeforeAll
    static void bootstrapAndLogIn() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));

        APIResponse seed = context.request().get(BASE + "/cms/_seed-ui-test");
        assertThat(seed.status()).as("seed endpoint must answer (is the stack up?)").isEqualTo(200);
        JsonNode json = new ObjectMapper().readTree(seed.text());
        settings = json;
        json.get("articles").forEach(article ->
                ARTICLES.put(article.get("headline").asText(), article));

        Page loginPage = context.newPage();
        loginPage.navigate(BASE + "/cms/");
        loginPage.fill("input[name=username]", json.get("username").asText());
        loginPage.keyboard().press("Enter");
        loginPage.fill("input[name=password]", json.get("password").asText());
        loginPage.keyboard().press("Enter");
        loginPage.waitForURL(url -> !url.contains("logIn"));
        loginPage.close();

        licensed = detectLicenseState();
    }

    /**
     * Reads the license state from the health widget's own status line. The
     * fragment fetch also warms the background license probe, so by the time
     * the first test runs the preview's evaluation-banner behavior is
     * deterministic (a cold probe would let early previews answer with raw
     * PDF even on an unlicensed service).
     */
    private static boolean detectLicenseState() {
        String url = BASE + "/cms/dashboardWidget/default/"
                + "com.realobjects.brightspot.pdfreactor.tool.PdfReactorHealthWidget/"
                + UUID.randomUUID();
        long deadline = System.currentTimeMillis() + 90_000L;
        String body = "";
        while (System.currentTimeMillis() < deadline) {
            body = context.request().get(url).text();
            if (body.contains("(licensed)")) {
                return true;
            }
            if (body.contains("(evaluation")) {
                return false;
            }
            try {
                Thread.sleep(3_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException(
                "License state did not resolve from the health widget within 90s;"
                        + " last fragment: " + body);
    }

    @AfterAll
    static void tearDown() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @AfterEach
    void closePage() {
        if (page != null) {
            page.close();
            page = null;
        }
    }

    private static String editUrl(String headline) {
        return BASE + ARTICLES.get(headline).get("editUrl").asText();
    }

    private static String globalSettingsEditUrl() {
        return BASE + settings.get("globalSettingsEditUrl").asText();
    }

    /** Opens the edit page and returns the first PDF-preview response. */
    private Response openEditPageAwaitingPreview(String headline) {
        page = context.newPage();
        return page.waitForResponse(
                response -> response.url().contains("/cms/pdfreactor/preview")
                        && response.status() == 200,
                () -> page.navigate(editUrl(headline)));
    }

    @Test
    @Order(1)
    void pdfPreviewStreamsPdfIntoThePane() {
        Response preview = openEditPageAwaitingPreview(PAGED_WEB);

        if (licensed) {
            assertThat(preview.headerValue("content-type")).startsWith("application/pdf");
        } else {
            // Evaluation mode: the preview relaxes license problems and
            // answers with the carrier page — an informational banner in the
            // Tool DOM plus the watermarked PDF embedded in the iframe.
            assertThat(preview.headerValue("content-type")).startsWith("text/html");
            Locator banner = page.locator(".PreviewFrame-typeDisplay .PdfPreview-problems");
            banner.waitFor();
            assertThat(banner.textContent()).contains("Evaluation mode");
            page.frameLocator(".PreviewFrame-typeDisplay iframe[data-container-id]")
                    .locator("object[type='application/pdf']").waitFor();
        }
    }

    @Test
    @Order(2)
    void previewIframeFillsThePaneUnscaled() {
        openEditPageAwaitingPreview(PAGED_WEB);

        Locator display = page.locator(".PreviewFrame-typeDisplay");
        Locator iframe = display.locator("iframe").first();
        iframe.waitFor();

        String transform = (String) iframe.evaluate("el => getComputedStyle(el).transform");
        assertThat(transform).isEqualTo("none");

        BoundingBox displayBox = display.boundingBox();
        BoundingBox iframeBox = iframe.boundingBox();
        // A banner above the viewer (the evaluation notice here; diagnostics
        // on problem documents) offsets the iframe by its height — the iframe
        // fills what the banner leaves.
        Locator banner = display.locator(".PdfPreview-problems");
        double bannerHeight = banner.count() > 0 && banner.first().isVisible()
                ? banner.first().boundingBox().height
                : 0;
        assertThat(iframeBox.width).isGreaterThan(displayBox.width * 0.95);
        assertThat(iframeBox.height).isGreaterThan((displayBox.height - bannerHeight) * 0.95);
    }

    @Test
    @Order(3)
    void headerControlsAreVisibleAndEquallySized() {
        openEditPageAwaitingPreview(PAGED_WEB);

        Locator refresh = page.locator(".PdfPreview-refresh");
        Locator share = page.locator(".PreviewFrame-typeActions .action-share");
        refresh.waitFor();
        share.waitFor();

        // Explicit visibility guard: a v5-skin upgrade that changes the
        // edit-pane typeActions allowlist or the controls grid would hide the
        // refresh control. Fail the suite loudly here rather than only via a
        // null bounding box below. (See the SKIN COUPLING INVENTORY in
        // PdfPreviewType.)
        assertThat(refresh.isVisible()).isTrue();
        assertThat(share.isVisible()).isTrue();

        BoundingBox refreshBox = refresh.boundingBox();
        BoundingBox shareBox = share.boundingBox();
        assertThat(refreshBox.height).isCloseTo(shareBox.height, within(1.0));
        assertThat(refreshBox.width).isCloseTo(shareBox.width, within(1.0));
    }

    @Test
    @Order(10)
    void scheduleDateControlShownWhenOptedIn() {
        // PAGED_WEB has the per-article opt-in enabled by the seed. The
        // schedule-date select (Phase 6.6) submits _date / _scheduleId and
        // renders as a DateTimeInput; PdfPreviewPage turns those into a
        // PreviewDatabase override. It is wrapped with an explanatory tooltip.
        openEditPageAwaitingPreview(PAGED_WEB);

        Locator scheduleControl = page.locator(
                ".PreviewFrame-typeControlsContainer [name='_date'], "
                        + ".PreviewFrame-typeControlsContainer [name='_scheduleId'], "
                        + ".PreviewFrame-typeActions .DateTimeInput");
        assertThat(scheduleControl.count()).isGreaterThan(0);

        Locator wrapper = page.locator(".PdfPreview-scheduleDate");
        wrapper.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        assertThat(wrapper.getAttribute("title")).isNotBlank();
    }

    @Test
    @Order(11)
    void scheduleDateControlHiddenWhenNotOptedIn() {
        // LONG_FORM does not opt in (the control is off by default), so the
        // schedule-date control must not appear in its PDF preview header
        // (refresh and share still do).
        openEditPageAwaitingPreview(LONG_FORM);

        Locator scheduleControl = page.locator(
                ".PreviewFrame-typeControlsContainer [name='_date'], "
                        + ".PreviewFrame-typeControlsContainer [name='_scheduleId'], "
                        + ".PreviewFrame-typeActions .DateTimeInput, "
                        + ".PdfPreview-scheduleDate");
        assertThat(scheduleControl.count()).isZero();
        assertThat(page.locator(".PdfPreview-refresh").count()).isGreaterThan(0);
    }

    @Test
    @Order(16)
    void healthWidgetReportsServiceStatusOnDashboard() {
        // The widget is seeded onto the dashboard. Its probe runs in a
        // background task, so the first render shows "Checking…"; reload until
        // it reaches a terminal status (server-rendered each load).
        page = context.newPage();
        Locator widget = page.locator(".DashboardWidget")
                .filter(new Locator.FilterOptions().setHasText("PDFreactor service"));

        String text = "";
        for (int i = 0; i < 15; i++) {
            page.navigate(BASE + "/cms/");
            widget.first().waitFor();
            text = widget.first().textContent();
            if (text.contains("UP") || text.contains("DOWN")) {
                break;
            }
            page.waitForTimeout(500);
        }

        assertThat(text).containsIgnoringCase("PDFreactor service");
        // The compose stack's PDFreactor service is reachable (generate and
        // publish succeed above), so the probe must report UP, not DOWN.
        assertThat(text).contains("UP");
        assertThat(text).doesNotContain("DOWN");
    }

    @Test
    @Order(12)
    void healthWidgetResolvesQuicklyViaClientPollOnFirstLoad() {
        // First-load latency smoke test: on the suite's first dashboard view the
        // widget must show a resolved status (UP/DOWN) well under the 30 s
        // steady-state cadence WITHOUT any manual reload — exercising the fast
        // first poll on a "Checking…" (pending) render. NOTE: the probe cache is
        // process-local and may already be warm here (an earlier dashboard view
        // warms it), in which case the first render is already resolved and this
        // passes trivially; so it guards "fast first display" end-to-end but is
        // not a strict cold-cache guard. The pending → fast-poll branch is
        // covered at the unit level by currentHealth()'s cold → null (Checking…)
        // path in PdfReactorHealthWidgetTest; the render half (the pending
        // attribute) is verified by code review, since localize() needs a Dari
        // DB and the rendered HTML can't be asserted in a plain unit test.
        page = context.newPage();
        page.navigate(BASE + "/cms/");
        page.locator(".DashboardWidget")
                .filter(new Locator.FilterOptions().setHasText("PDFreactor service"))
                .first()
                .waitFor();

        // Resolved status lines are "UP — PDFreactor …" / "DOWN — …" (the
        // em-dash never appears in the "Checking…" render or the poll script),
        // so this waits for the poll-driven resolution, not a reload. The 20 s
        // timeout is below the 30 s cycle: if the first poll waited a full
        // cycle this would fail.
        page.waitForFunction(
                "() => Array.from(document.querySelectorAll('.DashboardWidget'))"
                        + ".some(e => /UP \\u2014 PDFreactor|DOWN \\u2014/.test(e.textContent))",
                null,
                new Page.WaitForFunctionOptions().setTimeout(20_000));
    }

    @Test
    @Order(13)
    void iccProfileFieldRendersAsReusablePicker() {
        // The ICC profile fields are reusable IccProfile references,
        // so the PDFreactor cluster must render an object-reference picker
        // (select existing / create new), NOT an inline file-upload widget.
        page = context.newPage();
        page.navigate(globalSettingsEditUrl());
        page.waitForLoadState();

        // The settings form prefixes field names with the settings record id,
        // so match the field-name suffix. Both ICC fields must render as the
        // object-reference picker (a ContentSelector: a dropdown of existing
        // IccProfile records + a search/select control + a hidden objectId
        // input) — NOT an inline file-upload widget.
        for (String fieldSuffix : new String[] {
                "/pdfreactor.outputIntentProfile", "/pdfreactor.cmykIccProfile"}) {
            Locator objectId = page.locator("input.objectId[name$='" + fieldSuffix + "']");
            objectId.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED));

            // The reference picker's region (the .inputSmall wrapper) carries
            // the ContentSelector dropdown + search; a StorageItem upload would
            // instead render a file input / image editor here.
            Locator region = page.locator(".inputSmall:has(input.objectId[name$='" + fieldSuffix + "'])");
            assertThat(region.locator("select.ContentSelector-select").count())
                    .as("reference dropdown of existing IccProfiles for " + fieldSuffix)
                    .isGreaterThan(0);
            assertThat(region.locator("a.objectId-select").count())
                    .as("search/select-existing control for " + fieldSuffix)
                    .isGreaterThan(0);
            assertThat(region.locator("input[type=file]").count())
                    .as("no inline upload widget for " + fieldSuffix)
                    .isEqualTo(0);
        }
    }

    @Test
    @Order(17)
    void settingsFieldNoteIsOneNodeInReadingOrder() {
        // The Sites & Settings PDFreactor fields render their explanation and
        // the "currently in effect (inherited)" hint as ONE plugin-owned note
        // node inside ONE platform note wrapper (.CIG-note). Previously each
        // field had two .CIG-note siblings (a static @Note and the dynamic
        // note); the Tool's note-row flex layout (.CIG-row>:not(…,.CIG-note,…)
        // does NOT give notes full width) let them sit side by side at ~50%
        // each on wide viewports, in the wrong order. A single note wrapper
        // makes side-by-side structurally impossible. The suite's context is
        // 1600px wide — the failure mode's viewport.
        page = context.newPage();
        page.navigate(globalSettingsEditUrl());
        page.waitForLoadState();

        // Base URL is blank on the seeded record, so its note shows both the
        // explanation and the inherited line. The CMS tab panel is inactive on
        // load (fields attached but not laid out), so assert on structure/DOM
        // order, which needs no visibility — the count is the real guard.
        Locator row = page.locator(".CIG-row[data-field='pdfreactor.baseUrl']");
        row.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));

        // Exactly one platform note wrapper in the field's row — nothing to
        // share the row with (was two: static @Note + dynamic note).
        assertThat(row.locator(":scope > .CIG-note").count()).isEqualTo(1);

        // One plugin note node, carrying explanation then the inherited line.
        assertThat(row.locator(".PdfFieldNote").count()).isEqualTo(1);
        Locator children = row.locator(".PdfFieldNote > div");
        assertThat(children.count()).isEqualTo(2);
        // Reading order: explanation first, inherited hint second.
        assertThat(children.nth(0).getAttribute("class")).contains("PdfFieldNote-text");
        assertThat(children.nth(1).getAttribute("class")).contains("PdfFieldNote-inherited");
        assertThat(children.nth(1).textContent()).contains("Currently in effect (inherited):");
    }

    @Test
    @Order(18)
    void settingsRenderAsOneClusterWithSectionHeadings() {
        // The PDFreactor settings render as ONE "PDFreactor" cluster (a single
        // collapsible section), not several sibling clusters. The document
        // -shaping groups are introduced inside it by section-heading rows, in a
        // fixed reading order. A heading leads its group's first-field note, and
        // a field note renders above its field, so the heading introduces the
        // fields below it.
        page = context.newPage();
        page.navigate(globalSettingsEditUrl());
        page.waitForLoadState();

        // Every PDFreactor field row carries the SAME cluster name — one cluster.
        Locator rows = page.locator(".CIG-row[data-cluster^='PDFreactor']");
        rows.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        List<String> clusters = new ArrayList<>();
        for (Locator row : rows.all()) {
            String name = row.getAttribute("data-cluster");
            if (name != null && !clusters.contains(name)) {
                clusters.add(name);
            }
        }
        assertThat(clusters).containsExactly("PDFreactor");

        // The section headings appear inside that cluster in reading order
        // (DOM order = field declaration order). Works on the inactive CMS tab —
        // the notes are attached, so text content needs no visibility.
        Locator headings = page.locator(".PdfFieldNote-heading");
        headings.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        List<String> ordered = new ArrayList<>();
        for (Locator heading : headings.all()) {
            ordered.add(heading.textContent().trim());
        }
        assertThat(ordered).containsExactly(
                "Document Metadata",
                "Document Features",
                "Viewer Preferences",
                "Color Management",
                "Advanced");
    }

    @Test
    @Order(19)
    void editPanesResizeWithPreviewOpen() {
        // Drag the content/preview divider with the PDF preview open and assert
        // the preview pane resizes in both directions — including the rightward
        // drag whose cursor passes over the preview iframe (the iframe pin must
        // not let the iframe capture the drag or block the resize handle).
        String anyEdit = BASE + ARTICLES.values().iterator().next().get("editUrl").asText();
        page = context.newPage();
        page.waitForResponse(
                r -> r.url().contains("/cms/pdfreactor/preview") && r.status() == 200,
                () -> page.navigate(anyEdit));
        page.locator(".PreviewFrame-typeDisplay iframe").first().waitFor();

        String widthScript =
                "() => document.querySelector('.PreviewFrame-typeDisplay').getBoundingClientRect().width";

        double startWidth = previewPaneWidth(widthScript);
        dragDivider(-150);
        double widened = previewPaneWidth(widthScript);
        assertThat(widened - startWidth)
                .as("dragging the divider left must widen the preview pane")
                .isGreaterThan(80.0);

        dragDivider(160);
        double narrowed = previewPaneWidth(widthScript);
        assertThat(widened - narrowed)
                .as("dragging the divider right (cursor over the iframe) must shrink the preview pane")
                .isGreaterThan(80.0);
    }

    private double previewPaneWidth(String widthScript) {
        return ((Number) page.evaluate(widthScript)).doubleValue();
    }

    /** Drags the content/preview resize divider by {@code dx} px. */
    private void dragDivider(int dx) {
        BoundingBox h = page.locator(".ContentEdit-resizeWrapper").first().boundingBox();
        double cx = h.x + h.width / 2;
        double cy = h.y + h.height / 2;
        page.mouse().move(cx, cy);
        page.mouse().down();
        page.mouse().move(cx + dx, cy, new com.microsoft.playwright.Mouse.MoveOptions().setSteps(12));
        page.mouse().up();
    }

    @Test
    @Order(4)
    void refreshReconvertsTheDraft() {
        openEditPageAwaitingPreview(PAGED_WEB);

        Response reconverted = page.waitForResponse(
                response -> response.url().contains("/cms/pdfreactor/preview")
                        && response.status() == 200,
                () -> page.click(".PdfPreview-refresh"));

        // Licensed: raw PDF. Evaluation mode: the carrier page wrapping the
        // watermarked PDF (same shape as the initial preview).
        assertThat(reconverted.headerValue("content-type"))
                .startsWith(licensed ? "application/pdf" : "text/html");
    }

    @Test
    @Order(5)
    void diagnosticsBannerShowsMissingResource() {
        Response preview = openEditPageAwaitingPreview(DIAGNOSTICS);

        // Non-fatal problems: the endpoint answers with the carrier page
        // (PDF embedded; report posted to the parent) instead of raw PDF.
        assertThat(preview.headerValue("content-type")).startsWith("text/html");

        // The banner renders in the TOOL DOM (themed), not inside the
        // iframe: platform convention -- editor messages live in the Tool
        // page; iframe content is the previewed document only.
        Locator banner = page.locator(".PreviewFrame-typeDisplay .PdfPreview-problems");
        banner.waitFor();
        assertThat(banner.textContent()).contains("missing.invalid");
        // Themed by the skin's bridged message classes.
        assertThat(banner.getAttribute("class")).contains("is-warning").contains("Message");

        // Only the loader's dynamically created iframe carries
        // data-container-id; the static placeholder (removed asynchronously
        // after load) would otherwise make the locator ambiguous. The frame
        // holds the plugin's minimal carrier document (never Tool chrome),
        // with the PDF and the standalone fallback hidden.
        Locator body = page.frameLocator(".PreviewFrame-typeDisplay iframe[data-container-id]")
                .locator("body");
        assertThat(body.getAttribute("class")).contains("PdfPreviewMessage");
        Locator embeddedPdf = page.frameLocator(".PreviewFrame-typeDisplay iframe[data-container-id]")
                .locator("object[type='application/pdf']");
        embeddedPdf.waitFor();
        Locator fallback = page.frameLocator(".PreviewFrame-typeDisplay iframe[data-container-id]")
                .locator(".PdfPreview-fallback");
        assertThat(fallback.isVisible()).isFalse();

        // Resize fix: the PDF viewer is offset below the banner by
        // --PdfPreview-bannerHeight; that offset must keep matching the
        // banner's real height when the pane is resized and the banner
        // reflows, or the viewer gaps below / overlaps it. A ResizeObserver
        // keeps it synced.
        String offsetMatchesBanner =
                "() => { var b = document.querySelector('.PreviewFrame-typeDisplay .PdfPreview-problems');"
                        + " if (!b) { return false; }"
                        + " var d = b.closest('.PreviewFrame-typeDisplay');"
                        + " var v = parseFloat(d.style.getPropertyValue('--PdfPreview-bannerHeight'));"
                        + " return !isNaN(v) && Math.abs(v - b.offsetHeight) <= 1; }";
        assertThat((Boolean) page.evaluate(offsetMatchesBanner)).isTrue();
        assertThat((Boolean) page.evaluate(
                "() => !!document.querySelector('.PreviewFrame-typeDisplay .PdfPreview-problems')"
                        + ".closest('.PreviewFrame-typeDisplay').pdfBannerSizeObserver")).isTrue();
        page.setViewportSize(820, 1000);
        page.waitForFunction(offsetMatchesBanner);
    }

    /**
     * The "PDFreactor" widget lives in the collapsible right rail
     * ({@code ContentEdit-right}), which starts closed — so the link is in
     * the DOM but not visible. The tests assert the widget's function, not
     * the drawer state: wait for attachment, then drive the link's URL.
     */
    private String generateHref() {
        Locator generateLink = page.locator("a[href*='pdfreactor/generate']").first();
        generateLink.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        return generateLink.getAttribute("href");
    }

    @Test
    @Order(6)
    void generateStoresAndServesCachedPdf() {
        Assumptions.assumeTrue(licensed,
                "needs a licensed service: Generate fails closed on license problems"
                        + " before anything is stored (fail-closed behavior is covered"
                        + " by DefaultPdfReactorServiceTest and the e2e suite)");
        openEditPageAwaitingPreview(PAGED_WEB);
        String href = generateHref();

        // Convention: controls must carry both class generations explicitly
        // (the v5 skin hides legacy classes until its runtime bridge runs).
        Locator generateAction = page.locator(".PdfWidget-generate").first();
        generateAction.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        assertThat(generateAction.getAttribute("class")).contains("button").contains("Button");

        APIResponse generated = context.request().get(BASE + href);
        assertThat(generated.status()).isEqualTo(200);
        assertThat(generated.headers().get("content-type")).startsWith("application/pdf");

        // The widget now offers the stored PDF with a localized timestamp
        // (never the raw Date.toString format, e.g. "EDT 2026")...
        page.reload();
        Locator download = page.locator(".PdfWidget-download").first();
        download.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        assertThat(download.getAttribute("class")).contains("link").contains("Link");

        // Step 6: the Download link carries the shown record's id and streams
        // that exact stored PDF — no regenerate, no content lookup. The href
        // must target the record by id, and fetching it must serve the stored
        // bytes inline (opened in a new tab, the browser decides
        // view-vs-download — never forced as an attachment).
        String downloadHref = download.getAttribute("href");
        assertThat(downloadHref).contains("download=true").contains("generatedPdfId=");
        APIResponse downloaded = context.request().get(BASE + downloadHref);
        assertThat(downloaded.status()).isEqualTo(200);
        assertThat(downloaded.headers().get("content-type")).startsWith("application/pdf");
        assertThat(downloaded.headers().get("content-disposition")).contains("inline");

        Locator meta = page.locator(".PdfWidget-meta").first();
        meta.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        String metaText = meta.textContent();
        assertThat(metaText).isNotBlank();
        assertThat(metaText).doesNotContainPattern("[A-Z]{2,4} 20\\d\\d");

        // ...and the repeat request serves it (same cache key).
        APIResponse cached = context.request().get(BASE + href);
        assertThat(cached.status()).isEqualTo(200);
        assertThat(cached.headers().get("content-type")).startsWith("application/pdf");
    }

    @org.junit.jupiter.api.Disabled("With JavaScript on (the"
            + " default), PDFreactor's browser engine treats a network-unreachable"
            + " resource as a non-fatal connection error, not a MISSING_RESOURCE"
            + " abort — so generate no longer fails closed on the sample's"
            + " missing.invalid references (the preview still REPORTS them — see"
            + " diagnosticsBannerShowsMissingResource). Re-enable with a sample that"
            + " 404s from a reachable host (a deferred sample-data follow-up). The fail-closed"
            + " machinery is covered by DefaultPdfReactorServiceTest + the e2e suite.")
    @Test
    @Order(7)
    void generateFailsClosedOnMissingResources() {
        openEditPageAwaitingPreview(DIAGNOSTICS);
        String href = generateHref();

        APIResponse failed = context.request().get(BASE + href);
        assertThat(failed.status()).isEqualTo(200);
        assertThat(failed.headers().get("content-type")).startsWith("text/html");

        String body = failed.text();
        assertThat(body).contains("could not be generated");
        // Concise parsed detail (the missing resource's URL), a remedy,
        // technical details collapsed, and a way back -- not the raw exception
        // chain. The fail-closed trigger is the missing STYLESHEET (a broken
        // image is non-fatal with JavaScript on), so assert on the URL
        // rather than an image-specific message.
        assertThat(body).contains("missing.invalid");
        assertThat(body).contains("Fix or remove the broken resource");
        assertThat(body).contains("Technical details");
        assertThat(body).contains("Back to the content");
        // No duplicate prefix-chaining of the raw service error outside the
        // technical-details block.
        assertThat(body).doesNotContain("PDFreactor conversion failed:");
    }

    @Test
    @Order(9)
    void publishGeneratesPdfAutomatically() {
        openEditPageAwaitingPreview(LONG_FORM);

        // Make an editorial change so the form is dirty and the CMS JavaScript
        // allows the Publish action to submit. Without a change the platform
        // skips the submit when no modifications are detected, so
        // Content.Static.publish() is never called, updateDate does not change,
        // and the automation idempotency guard (cacheKey == generatedPdfAttemptKey)
        // silently blocks re-generation on every rerun.
        //
        // Brightspot form field names: "{stateId}/{fieldInternalName}".
        // Simple String fields render as <textarea> in Brightspot v5 (verified
        // by inspecting the live edit form DOM).
        String articleId = ARTICLES.get(LONG_FORM).get("id").asText();
        page.locator("textarea[name='" + articleId + "/subheadline']")
                .fill("Sixty Paragraphs — run " + System.currentTimeMillis());

        // Baseline: the stored PDF's identity before publishing. We key on the
        // download link's href, which encodes the GeneratedPdf record id
        // (`generatedPdfId`): each regeneration writes a new cache record with a
        // fresh id, so the href changes. The visible meta line is too coarse —
        // it renders the date to the minute, so a re-publish in the same minute
        // (e.g. right after the seed-time generation) produces a byte-identical
        // string even though a new PDF was genuinely generated.
        String before = storedPdfIdentityOrNull();

        page.click("button[name=action-publish]");

        if (licensed) {
            // The publish hook converts off-thread; poll the widget until the
            // stored PDF's identity appears or changes.
            long deadline = System.currentTimeMillis() + 60_000L;
            String after = before;
            while (System.currentTimeMillis() < deadline) {
                page.waitForTimeout(2_000);
                page.navigate(editUrl(LONG_FORM));
                after = storedPdfIdentityOrNull();
                if (after != null && !after.equals(before)) {
                    break;
                }
            }

            assertThat(after).as("publish must produce/refresh the stored PDF")
                    .isNotNull()
                    .isNotEqualTo(before);
        } else {
            // Evaluation mode: the publish-time conversion fails closed on
            // the license, nothing may be stored, and the failure must
            // surface on the widget as the red publish-failure banner.
            long deadline = System.currentTimeMillis() + 90_000L;
            Locator failureBanner = page.locator(".PdfWidget-publishFailure");
            while (System.currentTimeMillis() < deadline && failureBanner.count() == 0) {
                page.waitForTimeout(2_000);
                page.navigate(editUrl(LONG_FORM));
                failureBanner = page.locator(".PdfWidget-publishFailure");
            }
            assertThat(failureBanner.count())
                    .as("an unlicensed publish must surface the failure banner")
                    .isPositive();
            assertThat(storedPdfIdentityOrNull())
                    .as("an unlicensed publish must not store a PDF")
                    .isEqualTo(before);
        }
    }

    private String storedPdfIdentityOrNull() {
        Locator download = page.locator(".PdfWidget-download").first();
        return download.count() > 0 ? download.getAttribute("href") : null;
    }

    @Test
    @Order(14)
    void convertAgainIsAFrameSafePostNotANavigableLink() {
        Assumptions.assumeTrue(licensed,
                "needs a licensed service: the Convert again control only renders"
                        + " next to a stored PDF, which the licensed Generate path"
                        + " (Order 6) produces");
        // PAGED_WEB has a stored PDF from Order 6, so the widget shows the
        // "Convert again" (regenerate) control.
        page = context.newPage();
        page.navigate(editUrl(PAGED_WEB));
        page.waitForLoadState();

        Locator regenerate = page.locator(".PdfWidget-regenerate").first();
        regenerate.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));

        // It is NOT a navigable <a href> — so the edit-form frame JS does
        // not AJAX-intercept it and inject the response into the widget frame
        // (which destroyed the right-rail layout). The URL rides a data
        // attribute; pdf-widget.js POSTs it.
        assertThat(regenerate.getAttribute("href")).isNull();
        String regenUrl = regenerate.getAttribute("data-pdf-regenerate-url");
        assertThat(regenUrl).contains("regenerate=true");

        // Regeneration is POST-only. A GET (the old CSRF-via-navigation
        // vector) is rejected without converting; a POST performs it.
        APIResponse viaGet = context.request().get(BASE + regenUrl);
        assertThat(viaGet.status()).isEqualTo(200);
        assertThat(viaGet.text()).contains("Convert again"); // the reject message
        // The POST must carry the Brightspot CSRF token (the bsp.csrf cookie),
        // exactly as pdf-widget.js does — without it the platform answers 403.
        String csrf = context.cookies().stream()
                .filter(c -> "bsp.csrf".equals(c.name))
                .map(c -> c.value)
                .findFirst()
                .orElse("");
        APIResponse viaPost = context.request().post(BASE + regenUrl,
                com.microsoft.playwright.options.RequestOptions.create()
                        .setHeader("Brightspot-CSRF", csrf));
        assertThat(viaPost.status()).isEqualTo(200);

        // Clicking triggers the POST handler (proving the control is wired) and
        // must NOT open a new tab or navigate to the broken refresh-type tab —
        // the symptoms a navigable link showed. (dispatchEvent works on the attached-but-collapsed
        // right-rail control; the handler is a delegated document listener.)
        int pagesBefore = context.pages().size();
        page.waitForResponse(
                response -> response.url().contains("/cms/pdfreactor/generate")
                        && "POST".equals(response.request().method())
                        && response.status() == 200,
                () -> regenerate.dispatchEvent("click"));
        assertThat(context.pages().size()).as("Convert again must not open a new tab")
                .isEqualTo(pagesBefore);
        assertThat(page.url()).doesNotContain("refresh-type");
    }

    @Test
    @Order(8)
    void sharedPreviewOpensWorkingPdfPreview() {
        openEditPageAwaitingPreview(PAGED_WEB);

        Page shared = context.waitForPage(() ->
                page.click(".PreviewFrame-typeActions .action-share"));
        try {
            Response preview = shared.waitForResponse(
                    response -> response.url().contains("/cms/pdfreactor/preview")
                            && response.status() == 200,
                    () -> shared.waitForLoadState());
            // Licensed: raw PDF. Evaluation mode: the carrier page wrapping
            // the watermarked PDF.
            assertThat(preview.headerValue("content-type"))
                    .startsWith(licensed ? "application/pdf" : "text/html");

            // Shared previews are frozen snapshots: edit-coupled controls
            // (the refresh button) must not render there (platform
            // convention: content-edit context only).
            assertThat(shared.locator(".PdfPreview-refresh").count()).isZero();
        } finally {
            shared.close();
        }
    }
}
