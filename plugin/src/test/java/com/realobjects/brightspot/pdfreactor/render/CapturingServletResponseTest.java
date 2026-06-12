package com.realobjects.brightspot.pdfreactor.render;

import java.io.StringWriter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CapturingServletResponseTest {

    @Mock
    private HttpServletResponse delegate;

    @Test
    void capturesWriterOutputIntoTheCaptureWriter() {
        StringWriter capture = new StringWriter();
        CapturingServletResponse response = new CapturingServletResponse(delegate, capture);

        response.getWriter().write("<html>legacy</html>");
        response.flushBuffer();

        assertThat(capture.toString()).isEqualTo("<html>legacy</html>");
    }

    @Test
    void swallowsHeaderStatusAndCookieMutations() {
        CapturingServletResponse response = new CapturingServletResponse(delegate, new StringWriter());

        response.setContentType("application/pdf");
        response.setHeader("X-Leak", "1");
        response.addHeader("X-Leak2", "2");
        response.setStatus(503);
        response.addCookie(new Cookie("session", "abc"));

        // None of these must reach the real Tool response.
        verify(delegate, never()).setContentType(org.mockito.ArgumentMatchers.anyString());
        verify(delegate, never()).setHeader(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(delegate, never()).addHeader(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(delegate, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
        verify(delegate, never()).addCookie(org.mockito.ArgumentMatchers.any(Cookie.class));
    }

    @Test
    void recordsRedirectAsADiversion() {
        CapturingServletResponse response = new CapturingServletResponse(delegate, new StringWriter());

        response.sendRedirect("https://elsewhere.example.com/");

        assertThat(response.getDiversion()).contains("redirect").contains("elsewhere.example.com");
    }

    @Test
    void recordsSendErrorAsADiversion() {
        CapturingServletResponse response = new CapturingServletResponse(delegate, new StringWriter());

        response.sendError(404, "missing");

        assertThat(response.getDiversion()).contains("404");
    }

    @Test
    void noDiversionByDefault() {
        CapturingServletResponse response = new CapturingServletResponse(delegate, new StringWriter());
        assertThat(response.getDiversion()).isNull();
    }
}
