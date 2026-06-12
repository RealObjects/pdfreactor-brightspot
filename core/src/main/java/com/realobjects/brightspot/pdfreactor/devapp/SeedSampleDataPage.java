package com.realobjects.brightspot.pdfreactor.devapp;

import java.util.List;

import com.psddev.cms.ui.ToolPage;
import com.psddev.dari.db.Query;
import com.psddev.dari.web.annotation.WebPath;

import static com.psddev.dari.html.Nodes.A;
import static com.psddev.dari.html.Nodes.DIV;

/**
 * Dev-harness endpoint that publishes a small set of sample {@link Article}s
 * for manual testing of the PDFreactor plugin. Idempotent: does nothing if
 * articles already exist. Open {@code /cms/seed-sample-data} while logged in.
 */
@WebPath("/seed-sample-data")
public class SeedSampleDataPage extends ToolPage {

    @Override
    protected void onGet() {
        long existing = Query.from(Article.class).count();
        if (existing > 0) {
            writeDone("Sample data already present (" + existing + " articles). Nothing to do.");
            return;
        }

        SampleArticles.ensure(tool.getCurrentSite(), tool.getCurrentUser());
        writeDone("Published 3 sample articles.");
    }

    private void writeDone(String message) {
        writeHtmlToResponse(
                List.of(
                        DIV.classList("message", "message-success").with(message),
                        DIV.with(A.href("/cms/").with("Back to the CMS dashboard"))),
                "Seed Sample Data");
    }
}
