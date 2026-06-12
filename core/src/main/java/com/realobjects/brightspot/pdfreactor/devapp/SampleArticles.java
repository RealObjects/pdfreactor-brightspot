package com.realobjects.brightspot.pdfreactor.devapp;

import java.util.List;

import com.psddev.cms.db.Content;
import com.psddev.cms.db.Directory;
import com.psddev.cms.db.Site;
import com.psddev.cms.db.ToolUser;
import com.psddev.dari.db.Query;
import com.realobjects.brightspot.pdfreactor.publish.HasPdfRenderingData;

/**
 * Publishes the dev-harness sample articles (idempotent). Shared by
 * {@link SeedSampleDataPage} (manual seeding) and {@link SeedUiTestPage}
 * (Playwright bootstrap).
 */
final class SampleArticles {

    static final String PAGED_WEB_HEADLINE = "The Paged Web: Print-Grade PDF from CMS Content";
    static final String LONG_FORM_HEADLINE = "Sixty Paragraphs of Pagination";
    static final String DIAGNOSTICS_HEADLINE = "Diagnostics by Example";

    // The fail-closed / diagnostics demonstration references a STYLESHEET on a
    // non-resolving host: a load-critical resource PDFreactor reports as missing
    // and aborts on (with the generate path's MISSING_RESOURCE policy) even with
    // JavaScript on. A broken <img> is NOT used as the trigger because a
    // browser-grade converter (JavaScript processing is on by default) renders
    // a broken image as a placeholder rather than failing.
    static final String DIAGNOSTICS_BODY =
            "<p>This article references a stylesheet on a host that does not"
                    + " resolve, so the PDF preview shows the diagnostics banner"
                    + " (missing resource) while still rendering a preview, and"
                    + " on-demand generation fails closed:</p>"
                    + "<link rel=\"stylesheet\" href=\"http://missing.invalid/print.css\">"
                    + "<p>It also embeds a broken image, which a browser-grade"
                    + " converter renders as a placeholder rather than failing:"
                    + " <img src=\"http://missing.invalid/logo.png\" alt=\"intentionally missing\"></p>"
                    + "<p>Remove the missing references to make generation succeed.</p>";

    private SampleArticles() {
    }

    /**
     * Publishes the sample articles if none exist; returns all articles.
     * Always backfills permalink paths: programmatic publishing bypasses
     * the Tool's URLs widget (the only place the platform calls
     * {@code Directory.Item#createPermalink}), and the publish-automation
     * path renders by fetching the permalink.
     */
    static List<Article> ensure(Site site, ToolUser user) {
        if (Query.from(Article.class).count() == 0) {
            publishArticle(site, user,
                    PAGED_WEB_HEADLINE,
                    "Why paged media still matters in a scrolling world",
                    "<p>Print layout is not a degraded form of web layout — it is its own discipline,"
                            + " with pagination, running headers, and typographic control that browsers"
                            + " never needed to master.</p>"
                            + "<h2>Page breaks are content decisions</h2>"
                            + "<p>A heading stranded at the bottom of a page, a table split mid-row, a"
                            + " quotation orphaned from its attribution: these are the failures readers"
                            + " notice. CSS paged media gives authors the vocabulary —"
                            + " <code>page-break-after</code>, <code>page-break-inside</code>, margin"
                            + " boxes — and the converter does the bookkeeping.</p>"
                            + "<blockquote>Print is the debugger of typography: every shortcut taken on"
                            + " screen becomes visible on paper.</blockquote>"
                            + "<h2>Numbers belong in tables</h2>"
                            + "<table><tr><th>Format</th><th>Pagination</th><th>Fidelity</th></tr>"
                            + "<tr><td>HTML</td><td>none</td><td>screen</td></tr>"
                            + "<tr><td>PDF</td><td>full</td><td>print</td></tr></table>"
                            + "<p>Re-publish this article and watch the generated PDF regenerate; leave"
                            + " it untouched and repeat requests serve the cached copy.</p>");

            publishArticle(site, user,
                    LONG_FORM_HEADLINE,
                    "A long-form stress test for page counting",
                    longBody());

            publishArticle(site, user,
                    DIAGNOSTICS_HEADLINE,
                    "This article references a missing resource on purpose",
                    DIAGNOSTICS_BODY);
        }
        List<Article> articles = Query.from(Article.class).selectAll();
        for (Article article : articles) {
            boolean changed = false;

            // Enable the opt-in schedule-date PDF preview control on the
            // showcase article only, so the UI suite can exercise both the
            // opted-in and the default-off states.
            boolean wantSchedule = PAGED_WEB_HEADLINE.equals(article.getHeadline());
            HasPdfRenderingData pdfData = article.as(HasPdfRenderingData.class);
            if (pdfData.isSchedulePreviewEnabled() != wantSchedule) {
                pdfData.setSchedulePreviewEnabled(wantSchedule);
                changed = true;
            }

            // Refresh the diagnostics article's body on existing data so a
            // redeploy picks up the missing-stylesheet demonstration (the seed
            // only *creates* when the table is empty; the DB persists older
            // bodies across runs).
            if (DIAGNOSTICS_HEADLINE.equals(article.getHeadline())
                    && !DIAGNOSTICS_BODY.equals(article.getBody())) {
                article.setBody(DIAGNOSTICS_BODY);
                changed = true;
            }

            // The sample articles demonstrate publish automation, so generation
            // on publish must be on. Persist it explicitly (the checkbox reads
            // the stored value, which is otherwise unset on content created
            // before this field, leaving the box unchecked even though it
            // generates) and clear any stale opt-out from earlier manual
            // testing, so the box reads checked and automation is not gated out.
            if (!Boolean.TRUE.equals(article.getState().get("pdfreactor.generatePdfOnPublish"))) {
                pdfData.setGeneratePdfOnPublish(true);
                changed = true;
            }

            Directory.ObjectModification directoryData =
                    article.as(Directory.ObjectModification.class);
            if (directoryData.getPermalink() == null) {
                directoryData.addSitePath(site, article.createPermalink(site),
                        Directory.PathType.PERMALINK);
                changed = true;
            }

            if (changed) {
                article.save();
            }
        }
        return articles;
    }

    private static void publishArticle(Site site, ToolUser user, String headline, String subheadline, String body) {
        Article article = new Article();
        article.setHeadline(headline);
        article.setSubheadline(subheadline);
        article.setBody(body);
        // Assign the permalink BEFORE the first publish: Content.Static.publish
        // fires the publish automation (afterSave), which renders by fetching
        // the permalink — so without this the very first publish failed with
        // "has no permalink to render" (the loop backfill below only ran after,
        // on a later save). Programmatic publishing bypasses the Tool's URLs
        // widget, the only place the platform calls createPermalink itself.
        article.as(Directory.ObjectModification.class).addSitePath(
                site, article.createPermalink(site), Directory.PathType.PERMALINK);
        Content.Static.publish(article, site, user);
    }

    private static String longBody() {
        StringBuilder body = new StringBuilder("<p>Each section below is identical filler;"
                + " the point is the page count in the footer.</p>");
        for (int i = 1; i <= 60; i++) {
            body.append("<h2>Section ").append(i).append("</h2>")
                    .append("<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do"
                            + " eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim"
                            + " ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut"
                            + " aliquip ex ea commodo consequat.</p>");
        }
        return body.toString();
    }
}
