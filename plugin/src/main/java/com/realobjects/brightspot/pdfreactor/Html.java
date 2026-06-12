package com.realobjects.brightspot.pdfreactor;

/**
 * Minimal HTML escaping for the small hand-built markup fragments the plugin
 * emits (failure banners, the publish-failure notification HTML). Escapes the
 * five characters that can break out of element text or a quoted attribute:
 * {@code &}, {@code <}, {@code >}, {@code "}, {@code '} — replacing the four
 * near-duplicate {@code escape(...)} helpers that previously diverged on which
 * of these they covered.
 *
 * <p>Markup built with dari-html {@code .with(...)} is auto-escaped by the
 * framework; do not double-escape it through this.</p>
 */
public final class Html {

    private Html() {
    }

    /**
     * @param text Nullable.
     * @return Nonnull. Empty string for {@code null} input.
     */
    public static String escape(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
