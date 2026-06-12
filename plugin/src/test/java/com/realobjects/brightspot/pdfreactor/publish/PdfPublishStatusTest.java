package com.realobjects.brightspot.pdfreactor.publish;

import com.realobjects.brightspot.pdfreactor.PdfDiagnostics;
import com.realobjects.brightspot.pdfreactor.PdfResult;
import com.realobjects.pdfreactor.webservice.client.Connection;
import com.realobjects.pdfreactor.webservice.client.MissingResource;
import com.realobjects.pdfreactor.webservice.client.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPublishStatusTest {

    private static PdfResult result(int pages, PdfDiagnostics diagnostics) {
        return new PdfResult("%PDF".getBytes(), "application/pdf", pages, diagnostics);
    }

    @Test
    void publishFailsClosedOnLicenseAndMissingResources() {
        // A stored publish artifact must never carry eval watermarks or
        // broken resources, so both policies fail closed.
        assertThat(PdfPublishAutomation.publishOptions().isFailOnLicenseProblems()).isTrue();
        assertThat(PdfPublishAutomation.publishOptions().isFailOnMissingResources()).isTrue();
    }

    @Test
    void cleanSuccessReportsPageCountAndSize() {
        // No size when it is unknown (legacy rows, 0 bytes)...
        assertThat(PdfPublishAutomation.successStatus(result(1, PdfDiagnostics.empty()), 0L))
                .isEqualTo("Success (1 page)");
        // ...and the human-readable size when known.
        assertThat(PdfPublishAutomation.successStatus(result(3, PdfDiagnostics.empty()), 5 * 1024L))
                .isEqualTo("Success (3 pages; 5 KB)");
    }

    @Test
    void successWithDiagnosticsReportsCounts() {
        Result raw = new Result();
        MissingResource missing = new MissingResource();
        missing.setResource("http://cdn/x.png");
        raw.setMissingResources(new MissingResource[] {missing});
        Connection failed = new Connection();
        failed.setUrl("http://cdn/x.png");
        failed.setStatusCode(404);
        failed.setConnected(true);
        raw.setConnections(new Connection[] {failed});

        String status = PdfPublishAutomation.successStatus(
                result(2, PdfDiagnostics.fromResult(raw)), 2L * 1024 * 1024);

        assertThat(status).isEqualTo("Success (2 pages; 2.0 MB; 1 missing resource; 1 connection issue)");
    }
}
