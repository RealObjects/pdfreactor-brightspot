package com.realobjects.brightspot.pdfreactor.render;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Shields the live Tool response during in-request render capture.
 *
 * <p>{@code PageFilter.renderObject} and the View System mutate the
 * <em>real</em> response — content type, status, headers, cookies, and even
 * {@code sendRedirect}/{@code sendError} (verified in the platform's
 * {@code tryProcessView}/{@code updateResponseWithViewResponse}) — and legacy
 * {@code Renderer} types write to {@code response.getWriter()} rather than the
 * {@code Writer} passed to {@code renderObject}. Used unwrapped, a content
 * ViewModel that redirects or errors would commit the very Tool response about
 * to stream the PDF, and its site cookies/headers would leak into the CMS
 * response; a legacy renderer's markup would be lost (the capture stays empty,
 * producing the misleading "produced no markup" error).</p>
 *
 * <p>This wrapper routes all output into the capture {@link Writer} (so legacy
 * renderers are captured too), swallows every header/status/cookie mutation,
 * and records a redirect/{@code sendError} as a {@linkplain #getDiversion()
 * diagnosable signal} instead of committing the response.</p>
 */
final class CapturingServletResponse extends HttpServletResponseWrapper {

    private final Writer capture;
    private final ByteArrayOutputStream byteCapture = new ByteArrayOutputStream();
    private PrintWriter writer;
    private ServletOutputStream outputStream;
    private String diversion;

    CapturingServletResponse(HttpServletResponse response, Writer capture) {
        super(response);
        this.capture = capture;
    }

    /**
     * A short description of the first redirect/error the rendered view
     * attempted, or {@code null} if it produced markup normally.
     */
    String getDiversion() {
        return diversion;
    }

    /** Bytes written via {@link #getOutputStream()} (legacy byte-writing renderers). */
    byte[] capturedBytes() {
        return byteCapture.toByteArray();
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(capture);
        }
        return writer;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new ServletOutputStream() {
                @Override
                public void write(int b) {
                    byteCapture.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // No async writes during capture.
                }
            };
        }
        return outputStream;
    }

    @Override
    public void flushBuffer() {
        if (writer != null) {
            writer.flush();
        }
    }

    // --- swallow every mutation of the real Tool response ---

    @Override
    public void setContentType(String type) {
    }

    @Override
    public void setContentLength(int len) {
    }

    @Override
    public void setContentLengthLong(long len) {
    }

    @Override
    public void setCharacterEncoding(String charset) {
    }

    @Override
    public void setHeader(String name, String value) {
    }

    @Override
    public void addHeader(String name, String value) {
    }

    @Override
    public void setIntHeader(String name, int value) {
    }

    @Override
    public void addIntHeader(String name, int value) {
    }

    @Override
    public void setDateHeader(String name, long date) {
    }

    @Override
    public void addDateHeader(String name, long date) {
    }

    @Override
    public void addCookie(Cookie cookie) {
    }

    @Override
    public void setStatus(int sc) {
    }

    @Override
    public void setStatus(int sc, String sm) {
    }

    @Override
    public void sendRedirect(String location) {
        if (diversion == null) {
            diversion = "redirect to [" + location + "]";
        }
    }

    @Override
    public void sendError(int sc) {
        if (diversion == null) {
            diversion = "error status " + sc;
        }
    }

    @Override
    public void sendError(int sc, String msg) {
        if (diversion == null) {
            diversion = "error status " + sc + " (" + msg + ")";
        }
    }
}
