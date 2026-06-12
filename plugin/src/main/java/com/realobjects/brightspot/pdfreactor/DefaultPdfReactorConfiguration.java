package com.realobjects.brightspot.pdfreactor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * Declares per-view PDF conversion defaults on a ViewModel (or any class
 * passed to {@link PdfRenderOptions#fromAnnotated(Class)}).
 *
 * <p>Page geometry (paper size, margins, header/footer) is expressed in CSS
 * paged-media terms and injected as an inline {@code @page} user stylesheet;
 * PDFreactor has no dedicated configuration properties for page geometry.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DefaultPdfReactorConfiguration {

    /**
     * CSS {@code @page size} value, e.g. {@code "A4"} or
     * {@code "A4 landscape"}. Empty means unspecified.
     */
    String paperSize() default "";

    /**
     * CSS {@code @page margin} shorthand, e.g. {@code "20mm"} or
     * {@code "20mm 15mm"}. Empty means unspecified.
     */
    String margin() default "";

    /**
     * CSS {@code content} expression for the {@code @top-center} margin box,
     * e.g. {@code "\"Acme Corp\""}. Empty means no header.
     */
    String headerContent() default "";

    /**
     * CSS {@code content} expression for the {@code @bottom-center} margin
     * box, e.g. {@code "counter(page) \" / \" counter(pages)"}. Empty means
     * no footer.
     */
    String footerContent() default "";

    /**
     * Conformance profile of the output PDF. PDF/A and PDF/UA are opt-in.
     *
     * <p><strong>Limitation (annotation-default lesson):</strong> the default
     * {@link Configuration.Conformance#PDF} is treated as "unset" — a view
     * annotated with the default does <em>not</em> force plain PDF over a
     * site/global {@code conformance} setting; it inherits it. To pin a
     * specific conformance for a view, set a non-{@code PDF} value here. There
     * is currently no way for the annotation to force plain PDF over a
     * configured conformance (it would need a tri-state, as
     * {@link #javaScript()} uses); set the deploy/site conformance to plain PDF
     * instead.</p>
     */
    Configuration.Conformance conformance() default Configuration.Conformance.PDF;

    /**
     * URIs of user stylesheets injected for this view in addition to the
     * globally configured defaults (typically the view's print CSS).
     */
    String[] userStyleSheetUris() default { };

    /**
     * Output-intent identifier (e.g. a standard profile name) embedded into
     * PDF/A or PDF/X output. Empty means unspecified.
     */
    String outputIntentIdentifier() default "";

    /**
     * Classpath location of the output-intent ICC profile (e.g.
     * {@code "icc/FOGRA39.icc"}). Its bytes are read server-side and embedded
     * into the PDF as the output intent. Empty means none.
     */
    String outputIntentProfileClasspath() default "";

    /**
     * Classpath location of the CMYK ICC profile used for color conversion.
     * Read server-side; its bytes travel in the conversion request. Empty
     * means none.
     */
    String cmykIccProfileClasspath() default "";

    /**
     * Raw PDFreactor {@code Configuration} JSON for this view, deep-merged
     * over the global/per-site pass-through and under any per-call JSON.
     * Behavior-critical fields the plugin owns are not overridable. Empty
     * means none.
     */
    String configurationJson() default "";

    /**
     * Whether page JavaScript runs for this view. {@link JavaScript#DEFAULT}
     * (the default) means "unset" — the effective value is inherited from the
     * site/global {@code javaScriptEnabled} setting, which itself defaults to
     * <strong>enabled</strong> (matching normal PDFreactor behavior). Set
     * {@link JavaScript#ENABLED}/{@link JavaScript#DISABLED} to pin it for this
     * view regardless of the site setting (a per-call option still wins).
     */
    JavaScript javaScript() default JavaScript.DEFAULT;

    /**
     * Tri-state for the {@link #javaScript()} attribute: an enum is used (not a
     * {@code boolean}) so the annotation can express "unset" distinctly from
     * "off" — a {@code boolean false} default would be indistinguishable from a
     * deliberate disable and could never inherit the site setting.
     */
    enum JavaScript { DEFAULT, ENABLED, DISABLED }
}
