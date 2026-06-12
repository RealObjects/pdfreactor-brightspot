package com.realobjects.brightspot.pdfreactor.devapp;

import java.util.Collections;
import java.util.List;

import com.psddev.cms.view.PageEntryView;
import com.psddev.cms.view.RawView;
import com.psddev.cms.view.ViewModel;
import com.realobjects.brightspot.pdfreactor.DefaultPdfReactorConfiguration;
import com.realobjects.brightspot.pdfreactor.Html;

/**
 * Theme-free page view for {@link Article}: implements {@link RawView}, so
 * the View System's {@code RawViewRenderer} emits the returned string as-is
 * — a complete HTML document with embedded screen and print CSS. This keeps
 * the dev harness independent of a Styleguide theme while still exercising
 * the real ViewModel rendering path end-to-end.
 */
@DefaultPdfReactorConfiguration(
        paperSize = "A4",
        margin = "20mm",
        footerContent = "counter(page) \" / \" counter(pages)")
public class ArticleViewModel extends ViewModel<Article> implements RawView, PageEntryView {

    @Override
    public List<?> getItems() {
        return Collections.singletonList(buildHtml());
    }

    private String buildHtml() {
        String headline = Html.escape(model.getHeadline());
        String subheadline = model.getSubheadline() != null ? Html.escape(model.getSubheadline()) : null;
        String body = model.getBody() != null ? model.getBody() : "";

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<title>" + headline + "</title>\n"
                + "<style>\n"
                + "body { font-family: Georgia, 'Times New Roman', serif; margin: 2rem auto;"
                + " max-width: 42rem; line-height: 1.6; color: #222; }\n"
                + "h1 { font-size: 2.2rem; line-height: 1.2; margin-bottom: 0.3rem; }\n"
                + ".subheadline { font-size: 1.2rem; color: #555; font-style: italic; margin-top: 0; }\n"
                + "blockquote { border-left: 4px solid #999; margin-left: 0; padding-left: 1rem; color: #444; }\n"
                + "table { border-collapse: collapse; width: 100%; }\n"
                + "th, td { border: 1px solid #999; padding: 0.4rem 0.6rem; text-align: left; }\n"
                + "@media print {\n"
                + "  body { margin: 0; max-width: none; }\n"
                + "  h2 { page-break-after: avoid; }\n"
                + "  table, blockquote { page-break-inside: avoid; }\n"
                + "}\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<article>\n"
                + "<h1>" + headline + "</h1>\n"
                + (subheadline != null ? "<p class=\"subheadline\">" + subheadline + "</p>\n" : "")
                + "<div class=\"body\">" + body + "</div>\n"
                + "</article>\n"
                + "</body>\n"
                + "</html>\n";
    }
}
