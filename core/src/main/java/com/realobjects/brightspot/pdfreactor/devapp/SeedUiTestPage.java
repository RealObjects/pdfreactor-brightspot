package com.realobjects.brightspot.pdfreactor.devapp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;

import com.psddev.cms.db.ToolUser;
import com.psddev.cms.preview.PreviewSiteSettings;
import com.psddev.cms.tool.CmsTool;
import com.psddev.cms.tool.Dashboard;
import com.psddev.cms.tool.DashboardColumn;
import com.psddev.cms.tool.DashboardContainer;
import com.psddev.cms.tool.DashboardWidget;
import com.psddev.cms.ui.ToolPage;
import com.psddev.cms.ui.ToolRequest;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.Singleton;
import com.psddev.dari.db.State;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.Password;
import com.psddev.dari.util.Settings;
import com.psddev.dari.web.WebRequest;
import com.psddev.dari.web.annotation.WebPath;
import com.realobjects.brightspot.pdfreactor.PdfReactorSiteSettings;
import com.realobjects.brightspot.pdfreactor.preview.PdfPreviewType;
import com.realobjects.brightspot.pdfreactor.publish.PdfReactorPublishSettings;
import com.realobjects.brightspot.pdfreactor.tool.PdfReactorHealthWidget;

/**
 * Dev-harness bootstrap endpoint for the Playwright UI tests (Phase 4b).
 * Idempotently ensures: a dedicated test user, the PDF preview type in the
 * global Sites &amp; Settings, and the sample articles. Responds with JSON
 * (credentials + article edit URLs) consumed by the test suite.
 *
 * <p><strong>Unauthenticated by design</strong> (the tests have no
 * credentials before it runs), so it is gated twice: it refuses to run in
 * production AND requires an explicit opt-in setting
 * ({@value #UI_TEST_SEED_ENABLED_SETTING}). {@code Settings.isProduction()}
 * defaults to <em>false</em> (verified), so non-production alone is not a
 * real guard — the opt-in is. Each call also rotates the test user's password
 * to a fresh random value (echoed once in the response) rather than a constant
 * baked into a DB snapshot. Dev-harness only — not part of the plugin.</p>
 */
@WebPath("/_seed-ui-test")
public class SeedUiTestPage extends ToolPage {

    static final String USERNAME = "uitest";

    /** Explicit dev opt-in required (in addition to non-production) to seed. */
    static final String UI_TEST_SEED_ENABLED_SETTING = "pdfreactor/uiTestSeedEnabled";

    @Override
    protected boolean shouldRequireUser() {
        return false;
    }

    @Override
    protected void onGet() throws Exception {
        if (Settings.isProduction()
                || !Settings.getOrDefault(boolean.class, UI_TEST_SEED_ENABLED_SETTING, false)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Fresh random password per seed (echoed once below), rotated onto the
        // user so a known constant never persists in a DB snapshot.
        String password = java.util.UUID.randomUUID().toString().replace("-", "");
        ToolUser user = ensureUser(password);
        ensurePdfPreviewType();
        ensurePublishAutomationEnabled();
        ensureTroubleshootingEnabled();
        ensureHealthWidgetOnDashboard(user);
        List<Article> articles = SampleArticles.ensure(null, user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", USERNAME);
        result.put("password", password);
        // The global Sites & Settings (CmsTool singleton) edit page, where the
        // PDFreactor cluster — including the reusable IccProfile pickers — is
        // rendered. Exposed for the UI suite.
        result.put("globalSettingsEditUrl", WebRequest.getCurrent().as(ToolRequest.class)
                .getPathBuilder("/content/edit.jsp")
                .setParameter("id", State.getInstance(Singleton.getInstance(CmsTool.class)).getId())
                .build());
        result.put("articles", articles.stream()
                .map(article -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("headline", article.getHeadline());
                    entry.put("id", article.getId().toString());
                    entry.put("editUrl", WebRequest.getCurrent().as(ToolRequest.class)
                            .getPathBuilder("/content/edit.jsp")
                            .setParameter("id", article.getId())
                            .build());
                    return entry;
                })
                .collect(Collectors.toList()));

        response.setHeader("Content-Type", "application/json");
        response.toBody().write(ObjectUtils.toJson(result));
    }

    private ToolUser ensureUser(String password) {
        ToolUser user = Query.from(ToolUser.class).where("username = ?", USERNAME).first();
        if (user == null) {
            user = new ToolUser();
            user.setName("UI Test");
            user.setUsername(USERNAME);
        }
        // Rotate the password every seed so the returned credential is always
        // valid and no constant lingers if the user already existed.
        user.setPassword(Password.create(password));
        user.save();
        return user;
    }

    /**
     * Turns on the troubleshooting gate ({@code troubleshootingEnabled} on the
     * global Sites &amp; Settings record) so the dev stack can exercise the
     * per-article Debug/Inspectable build toggles. The gate is an
     * administrator setting; there is no deploy-time switch for it.
     * Idempotent; dev-harness only.
     */
    private void ensureTroubleshootingEnabled() {
        CmsTool cms = Singleton.getInstance(CmsTool.class);
        PdfReactorSiteSettings settings = cms.as(PdfReactorSiteSettings.class);
        if (!Boolean.TRUE.equals(settings.getTroubleshootingEnabled())) {
            settings.setTroubleshootingEnabled(true);
            cms.save();
        }
    }

    /**
     * Places the {@link PdfReactorHealthWidget} on the test user's own
     * dashboard so the UI suite can verify the widget renders. Per-user via
     * the non-deprecated {@code DashboardContainer} ({@code OneOff} holds an
     * inline dashboard), so it neither disturbs the global default nor uses
     * the deprecated {@code setDashboard}. Idempotent.
     */
    private void ensureHealthWidgetOnDashboard(ToolUser user) {
        DashboardContainer container = user.getDashboardContainer();
        Dashboard dashboard = container != null ? container.getDashboard() : null;
        boolean present = dashboard != null && dashboard.getColumns().stream()
                .flatMap(column -> column.getWidgets().stream())
                .anyMatch(widget -> widget instanceof PdfReactorHealthWidget);
        if (present) {
            return;
        }
        if (dashboard == null) {
            dashboard = new Dashboard();
        }

        List<DashboardWidget> widgets = new ArrayList<>();
        widgets.add(new PdfReactorHealthWidget());
        DashboardColumn column = new DashboardColumn();
        column.setWidgets(widgets);

        List<DashboardColumn> columns = new ArrayList<>(dashboard.getColumns());
        columns.add(0, column);
        dashboard.setColumns(columns);

        DashboardContainer.OneOff oneOff = new DashboardContainer.OneOff();
        oneOff.setDashboard(dashboard);
        user.setDashboardContainer(oneOff);
        user.save();
    }

    /**
     * Clears any global "disable publish automation" toggle so the UI suite's
     * publish-automation test runs against a known-enabled state. The toggle
     * is stored inverted on the global {@link CmsTool} (unset == enabled), but
     * the DB persists across runs, so a stale {@code disablePublishAutomation
     * = true} would silently gate out automation. Idempotent.
     */
    private void ensurePublishAutomationEnabled() {
        CmsTool cms = Singleton.getInstance(CmsTool.class);
        PdfReactorPublishSettings settings = cms.as(PdfReactorPublishSettings.class);
        if (settings.isPublishAutomationDisabled()) {
            settings.setDisablePublishAutomation(false);
            cms.save();
        }
    }

    private void ensurePdfPreviewType() {
        CmsTool cms = Singleton.getInstance(CmsTool.class);
        PreviewSiteSettings previewSettings = cms.as(PreviewSiteSettings.class);
        boolean present = previewSettings.getPreviewTypes().stream()
                .anyMatch(type -> State.getInstance(type).getType() != null
                        && PdfPreviewType.class.equals(State.getInstance(type).getType().getObjectClass()));
        if (!present) {
            previewSettings.getPreviewTypes().add(new PdfPreviewType());
            cms.save();
        }
    }
}
