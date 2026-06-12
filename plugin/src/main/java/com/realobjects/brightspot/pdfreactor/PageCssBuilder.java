package com.realobjects.brightspot.pdfreactor;

/**
 * Builds an {@code @page} CSS rule from page geometry options.
 *
 * <p>PDFreactor controls paper size, margins, and running headers/footers
 * through CSS paged-media rules rather than dedicated configuration
 * properties, so per-view defaults declared via
 * {@link DefaultPdfReactorConfiguration} are translated into an injected
 * inline user stylesheet.</p>
 */
final class PageCssBuilder {

    private PageCssBuilder() {
    }

    /**
     * Builds an {@code @page} rule from the given parts.
     *
     * @param paperSize CSS {@code size} value, e.g. {@code A4} or
     *        {@code A4 landscape}. Nullable.
     * @param margin CSS {@code margin} shorthand, e.g. {@code 20mm}. Nullable.
     * @param headerContent CSS {@code content} expression for the
     *        {@code @top-center} margin box, e.g. {@code "Acme Corp"}. Nullable.
     * @param footerContent CSS {@code content} expression for the
     *        {@code @bottom-center} margin box, e.g.
     *        {@code counter(page) " / " counter(pages)}. Nullable.
     * @return The {@code @page} rule, or {@code null} if all parts are blank.
     */
    static String build(String paperSize, String margin, String headerContent, String footerContent) {
        boolean hasPaperSize = isNotBlank(paperSize);
        boolean hasMargin = isNotBlank(margin);
        boolean hasHeader = isNotBlank(headerContent);
        boolean hasFooter = isNotBlank(footerContent);

        if (!hasPaperSize && !hasMargin && !hasHeader && !hasFooter) {
            return null;
        }

        StringBuilder css = new StringBuilder("@page {");
        if (hasPaperSize) {
            css.append(" size: ").append(paperSize.trim()).append(';');
        }
        if (hasMargin) {
            css.append(" margin: ").append(margin.trim()).append(';');
        }
        if (hasHeader) {
            css.append(" @top-center { content: ").append(headerContent.trim()).append("; }");
        }
        if (hasFooter) {
            css.append(" @bottom-center { content: ").append(footerContent.trim()).append("; }");
        }
        css.append(" }");
        return css.toString();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
