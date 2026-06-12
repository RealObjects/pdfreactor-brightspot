package com.realobjects.brightspot.pdfreactor.render;

import java.util.Objects;

/**
 * Finished HTML produced by an {@link HtmlSource}, together with the base
 * URL against which the document's relative resource URLs should be
 * resolved (if the source knows one).
 */
public final class RenderedHtml {

    private final String html;
    private final String baseUrl;

    public RenderedHtml(String html, String baseUrl) {
        this.html = Objects.requireNonNull(html, "html");
        this.baseUrl = baseUrl;
    }

    /**
     * @return Nonnull.
     */
    public String getHtml() {
        return html;
    }

    /**
     * @return Nullable. {@code null} means the configured default applies.
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
