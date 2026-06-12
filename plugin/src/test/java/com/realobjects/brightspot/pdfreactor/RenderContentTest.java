package com.realobjects.brightspot.pdfreactor;

import com.realobjects.brightspot.pdfreactor.render.HtmlSource;
import com.realobjects.brightspot.pdfreactor.render.RenderedHtml;
import com.realobjects.pdfreactor.webservice.client.Configuration;
import com.realobjects.pdfreactor.webservice.client.PDFreactor;
import com.realobjects.pdfreactor.webservice.client.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DefaultPdfReactorService#renderContent} wiring with mocked
 * {@link HtmlSource}s. Outside a web request the permalink source is used
 * and its base URL feeds the conversion unless overridden.
 */
@ExtendWith(MockitoExtension.class)
class RenderContentTest {

    private static final Object CONTENT = new Object();

    @DefaultPdfReactorConfiguration(
            paperSize = "A4",
            footerContent = "counter(page)",
            conformance = Configuration.Conformance.PDFA3A)
    private static final class AnnotatedView {
    }

    @Mock
    private PDFreactor client;

    @Mock
    private HtmlSource requestSource;

    @Mock
    private HtmlSource permalinkSource;

    private DefaultPdfReactorService newService() {
        PdfReactorConfig config = () -> "http://localhost:9423/service/rest";
        return new DefaultPdfReactorService(config, client, requestSource, permalinkSource);
    }

    private static Result pdfResult() {
        Result result = new Result();
        result.setDocument("%PDF-".getBytes());
        return result;
    }

    @Test
    @SuppressWarnings("deprecation")
    void outsideRequestUsesPermalinkSourceAndItsBaseUrl() throws Exception {
        when(permalinkSource.render(CONTENT)).thenReturn(
                new RenderedHtml("<html>rendered</html>", "https://www.example.com/article"));
        when(client.convert(any(Configuration.class))).thenReturn(pdfResult());

        newService().renderContent(CONTENT, null);

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getDocument()).isEqualTo("<html>rendered</html>");
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://www.example.com/article");
    }

    @Test
    @SuppressWarnings("deprecation")
    void explicitBaseUrlOptionWinsOverSourceBaseUrl() throws Exception {
        when(permalinkSource.render(CONTENT)).thenReturn(
                new RenderedHtml("<html/>", "https://www.example.com/article"));
        when(client.convert(any(Configuration.class))).thenReturn(pdfResult());

        newService().renderContent(CONTENT, PdfRenderOptions.builder()
                .baseUrl("https://override.example.com/")
                .build());

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://override.example.com/");
    }

    @Test
    @SuppressWarnings("deprecation")
    void annotatedViewModelSeedsConversionOptions() throws Exception {
        when(permalinkSource.render(CONTENT)).thenReturn(new RenderedHtml("<html/>", null));
        when(client.convert(any(Configuration.class))).thenReturn(pdfResult());

        // Stub the ViewModel resolution to a class carrying the annotation;
        // the wiring under test seeds the conversion options from it (the gap
        // that previously left @DefaultPdfReactorConfiguration inert).
        PdfReactorConfig config = () -> "http://localhost:9423/service/rest";
        DefaultPdfReactorService service = new DefaultPdfReactorService(
                config, client, requestSource, permalinkSource) {
            @Override
            protected Class<?> resolveViewModelClass(Object content) {
                return AnnotatedView.class;
            }
        };

        service.renderContent(CONTENT, PdfRenderOptions.builder().failOnMissingResources(true).build());

        ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
        verify(client).convert(captor.capture());

        java.util.List<Configuration.Resource> styleSheets = captor.getValue().getUserStyleSheets();
        assertThat(styleSheets).isNotNull();
        boolean pageCssInjected = styleSheets.stream()
                .map(Configuration.Resource::getContent)
                .filter(java.util.Objects::nonNull)
                .anyMatch(css -> css.contains("@page")
                        && css.contains("size: A4;")
                        && css.contains("counter(page)"));
        assertThat(pageCssInjected).isTrue();
        assertThat(captor.getValue().getConformance()).isEqualTo(Configuration.Conformance.PDFA3A);
    }
}
