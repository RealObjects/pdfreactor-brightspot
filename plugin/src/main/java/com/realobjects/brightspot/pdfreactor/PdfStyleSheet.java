package com.realobjects.brightspot.pdfreactor;

import java.util.Objects;

import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * A user stylesheet supplied to a conversion, either by URI (fetched by the
 * PDFreactor service) or as inline CSS content.
 */
public final class PdfStyleSheet {

    private final String uri;
    private final String content;

    private PdfStyleSheet(String uri, String content) {
        this.uri = uri;
        this.content = content;
    }

    /**
     * Creates a stylesheet that the PDFreactor service fetches from the
     * given {@code uri}.
     */
    public static PdfStyleSheet fromUri(String uri) {
        Objects.requireNonNull(uri, "uri");
        return new PdfStyleSheet(uri, null);
    }

    /**
     * Creates a stylesheet from inline CSS {@code content}.
     */
    public static PdfStyleSheet inline(String content) {
        Objects.requireNonNull(content, "content");
        return new PdfStyleSheet(null, content);
    }

    public String getUri() {
        return uri;
    }

    public String getContent() {
        return content;
    }

    Configuration.Resource toResource() {
        Configuration.Resource resource = new Configuration.Resource();
        if (uri != null) {
            resource.setUri(uri);
        } else {
            resource.setContent(content);
        }
        return resource;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PdfStyleSheet)) {
            return false;
        }
        PdfStyleSheet that = (PdfStyleSheet) other;
        return Objects.equals(uri, that.uri) && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, content);
    }

    @Override
    public String toString() {
        return uri != null
                ? "PdfStyleSheet{uri=" + uri + '}'
                : "PdfStyleSheet{inline, " + content.length() + " chars}";
    }
}
