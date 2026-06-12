package com.realobjects.brightspot.pdfreactor.devapp;

import java.util.Locale;

import com.psddev.cms.db.Content;
import com.psddev.cms.db.Directory;
import com.psddev.cms.db.Site;
import com.psddev.cms.db.ToolUi;
import com.realobjects.brightspot.pdfreactor.HasPdfRendering;

/**
 * Dev-harness sample content type: just enough of an "article" to exercise
 * the PDFreactor plugin (preview, on-demand generation, permalink fetch,
 * publish automation via {@link HasPdfRendering}).
 * Not part of the distributable plugin.
 */
public class Article extends Content implements Directory.Item, HasPdfRendering {

    @Required
    private String headline;

    private String subheadline;

    @ToolUi.RichText
    private String body;

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSubheadline() {
        return subheadline;
    }

    public void setSubheadline(String subheadline) {
        this.subheadline = subheadline;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String getLabel() {
        return headline;
    }

    @Override
    public String createPermalink(Site site) {
        if (headline == null) {
            return null;
        }
        String slug = headline.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return "/article/" + slug;
    }
}
