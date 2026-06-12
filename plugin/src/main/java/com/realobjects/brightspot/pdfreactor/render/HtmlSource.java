package com.realobjects.brightspot.pdfreactor.render;

import com.realobjects.brightspot.pdfreactor.PdfReactorException;

/**
 * Produces the finished HTML for a content object that is then handed to
 * PDFreactor.
 *
 * <p>Rendering always happens in a request context (no in-process
 * render-to-string API is documented in Brightspot): the preview path
 * renders inside the current tool request ({@link InRequestHtmlSource});
 * paths without a suitable request fetch the published permalink over
 * internal HTTP ({@link PermalinkHtmlSource}).</p>
 */
public interface HtmlSource {

    /**
     * Renders the given content object to finished HTML.
     *
     * @param content Nonnull.
     * @return Nonnull.
     * @throws PdfReactorException If the content cannot be rendered.
     */
    RenderedHtml render(Object content);
}
