package com.realobjects.brightspot.pdfreactor;

import com.psddev.cms.db.Content;
import com.psddev.cms.tool.CmsTool;
import com.psddev.dari.db.Singleton;
import com.psddev.test.db.TestDatabaseExtension;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The content-scoped {@code debugActive} / {@code inspectableActive} /
 * {@code troubleshootingActive} helpers: a per-article Debug/Inspectable build
 * toggle takes effect ONLY when an administrator has allowed troubleshooting
 * for the content's site (the {@code troubleshootingEnabled} gate on the global
 * Sites &amp; Settings record).
 */
@ExtendWith(TestDatabaseExtension.class)
class PdfReactorConfigsTest {

    /** Minimal printable type carrying the per-article toggles. */
    public static class TestPrintable extends Content implements HasPdfRendering {
    }

    @AfterEach
    void resetGate() {
        setGate(null);
    }

    /** The master gate lives on the global Sites & Settings record (CmsTool). */
    private static void setGate(Boolean enabled) {
        CmsTool cms = Singleton.getInstance(CmsTool.class);
        cms.as(PdfReactorSiteSettings.class).setTroubleshootingEnabled(enabled);
        cms.save();
    }

    private static TestPrintable contentWith(boolean debug, boolean inspectable) {
        TestPrintable content = new TestPrintable();
        content.as(HasPdfRenderingData.class).setDebugBuild(debug);
        content.as(HasPdfRenderingData.class).setInspectableBuild(inspectable);
        content.save();
        return content;
    }

    @Test
    void perArticleToggleHasNoEffectWhileGateOff() {
        setGate(false);
        TestPrintable content = contentWith(true, true);
        assertThat(PdfReactorConfigs.debugActive(content)).isFalse();
        assertThat(PdfReactorConfigs.inspectableActive(content)).isFalse();
        assertThat(PdfReactorConfigs.troubleshootingActive(content)).isFalse();
    }

    @Test
    void perArticleToggleTakesEffectWhenGateOn() {
        setGate(true);
        assertThat(PdfReactorConfigs.debugActive(contentWith(true, false))).isTrue();
        assertThat(PdfReactorConfigs.inspectableActive(contentWith(false, true))).isTrue();
        assertThat(PdfReactorConfigs.troubleshootingActive(contentWith(false, true))).isTrue();
    }

    @Test
    void gateOnButTogglesOffIsInactive() {
        setGate(true);
        TestPrintable content = contentWith(false, false);
        assertThat(PdfReactorConfigs.debugActive(content)).isFalse();
        assertThat(PdfReactorConfigs.inspectableActive(content)).isFalse();
        assertThat(PdfReactorConfigs.troubleshootingActive(content)).isFalse();
    }

    @Test
    void gateEnabledReflectsTheGlobalMaster() {
        setGate(true);
        assertThat(PdfReactorConfigs.troubleshootingGateEnabled((com.psddev.cms.db.Site) null)).isTrue();
        setGate(false);
        assertThat(PdfReactorConfigs.troubleshootingGateEnabled((com.psddev.cms.db.Site) null)).isFalse();
    }
}
