package com.realobjects.brightspot.pdfreactor.render;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.psddev.cms.db.PageFilter;
import com.psddev.dari.web.WebRequest;
import com.psddev.dari.web.servlet.ServletWebRequest;
import com.psddev.dari.web.servlet.ServletWebResponse;
import com.realobjects.brightspot.pdfreactor.PdfReactorException;

/**
 * Renders content through the Brightspot View System within the current
 * servlet request via {@link PageFilter#renderObject}, capturing the markup
 * into a string. This is the editor-preview path: the draft object renders
 * with full view processing without any extra HTTP round trip, and
 * PDFreactor never needs to fetch draft URLs.
 */
public class InRequestHtmlSource implements HtmlSource {

    @Override
    public RenderedHtml render(Object content) {
        Objects.requireNonNull(content, "content");

        WebRequest current = WebRequest.getCurrent();
        if (!(current instanceof ServletWebRequest)) {
            throw new PdfReactorException(
                    "No current servlet request; in-request rendering is only"
                            + " available within a web request.");
        }

        ServletWebRequest servletRequest = (ServletWebRequest) current;
        HttpServletRequest request = servletRequest.getOriginal();
        HttpServletResponse response = ((ServletWebResponse) servletRequest.getResponse()).getOriginal();

        // Shield the live Tool response: the View System would otherwise set
        // its content type / status / headers / cookies on the real response,
        // and a view that redirects or errors would commit it. The wrapper also
        // captures legacy Renderer output (which goes to response.getWriter(),
        // not the passed Writer).
        StringWriter writer = new StringWriter();
        CapturingServletResponse capturing = new CapturingServletResponse(response, writer);
        try {
            PageFilter.renderObject(request, capturing, writer, content);
            capturing.flushBuffer();

        } catch (IOException | ServletException | RuntimeException error) {
            throw new PdfReactorException(
                    "Failed to render [" + content.getClass().getName() + "] to HTML in-request.",
                    error);
        }

        // A view that redirected or errored produced no usable markup — surface
        // that as a diagnosable failure rather than streaming an empty PDF.
        if (capturing.getDiversion() != null) {
            throw new PdfReactorException("Rendering [" + content.getClass().getName()
                    + "] attempted to " + capturing.getDiversion()
                    + " instead of producing markup.");
        }

        String html = writer.toString();
        if (html.trim().isEmpty()) {
            // A legacy Renderer may have written bytes via the output stream.
            byte[] bytes = capturing.capturedBytes();
            if (bytes.length > 0) {
                html = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        if (html.trim().isEmpty()) {
            throw new PdfReactorException(
                    "Rendering [" + content.getClass().getName() + "] produced no markup;"
                            + " the type has no page/preview ViewModel or renderer.");
        }
        return new RenderedHtml(html, null);
    }
}
