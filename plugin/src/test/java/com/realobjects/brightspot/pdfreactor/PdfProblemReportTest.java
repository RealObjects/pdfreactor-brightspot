package com.realobjects.brightspot.pdfreactor;

import com.realobjects.pdfreactor.webservice.client.Connection;
import com.realobjects.pdfreactor.webservice.client.MissingResource;
import com.realobjects.pdfreactor.webservice.client.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfProblemReportTest {

    /** The real abort string the 12.6.0 service produces (verified live). */
    private static final String MISSING_ABORT =
            "Missing Resource: Missing resource http://missing.invalid/logo.png (IMAGE):"
                    + " Image \"http://missing.invalid/logo.png\" could not be loaded"
                    + " | unknown host: missing.invalid";

    private static PdfReactorException abortException(String serviceError) {
        Result result = new Result();
        result.setError(serviceError);
        return new PdfReactorException("PDFreactor conversion failed: " + serviceError,
                new com.realobjects.pdfreactor.webservice.client.PDFreactorWebserviceException(serviceError, result),
                PdfDiagnostics.fromResult(result));
    }

    @Test
    void missingResourceAbortIsParsedToOneConciseLine() {
        PdfProblemReport report = PdfProblemReport.of(abortException(MISSING_ABORT));

        assertThat(report.getKind()).isEqualTo(PdfProblemReport.Kind.MISSING_RESOURCE);
        assertThat(report.getDetails()).containsExactly(
                "Missing image: http://missing.invalid/logo.png — unknown host: missing.invalid");
        assertThat(report.getTechnical()).isEqualTo(MISSING_ABORT);
    }

    @Test
    void licenseAbortIsClassified() {
        String error = "License: PDFreactor is in evaluation mode."
                + " Use in production is prohibited!";
        PdfProblemReport report = PdfProblemReport.of(abortException(error));

        assertThat(report.getKind()).isEqualTo(PdfProblemReport.Kind.LICENSE);
        assertThat(report.getDetails()).containsExactly(
                "PDFreactor is in evaluation mode. Use in production is prohibited!");
    }

    @Test
    void unknownErrorIsGenericWithoutDuplication() {
        PdfProblemReport report = PdfProblemReport.of(abortException("Something exploded"));

        assertThat(report.getKind()).isEqualTo(PdfProblemReport.Kind.GENERIC);
        assertThat(report.getDetails()).containsExactly("Something exploded");
        assertThat(report.getTechnical()).isNull();
        // No conversion log on this result: the log surface stays null (the
        // populated case is a capped pass-through of Result#getLog, exercised
        // by the render sites and the e2e suite — the client Log is
        // deserialization-only and cannot be fabricated here).
        assertThat(report.getLogText()).isNull();
    }

    @Test
    void renderSourceFailureIsClassifiedForATailoredRemedy() {
        PdfProblemReport report = PdfProblemReport.of(new PdfReactorException(
                "Fetching [http://localhost:8080/article/x] for PDF rendering returned HTTP 404."));

        assertThat(report.getKind()).isEqualTo(PdfProblemReport.Kind.RENDER_SOURCE);
        assertThat(report.getDetails()).hasSize(1);
    }

    @Test
    void configFailureIsClassifiedForATailoredRemedy() {
        assertThat(PdfProblemReport.of(new PdfReactorException(
                "Could not read the ICC profile [classpath:icc/x.icc]: not found")).getKind())
                .isEqualTo(PdfProblemReport.Kind.CONFIG);
        assertThat(PdfProblemReport.of(new PdfReactorException(
                "Invalid PDFreactor configuration JSON: Unexpected character")).getKind())
                .isEqualTo(PdfProblemReport.Kind.CONFIG);
    }

    @Test
    void nonFatalDiagnosticsDeduplicateConnectionsAgainstMissingResources() {
        MissingResource missing = new MissingResource();
        missing.setResource("http://missing.invalid/logo.png");
        missing.setMessage("could not be loaded");

        Connection sameUrl = new Connection();
        sameUrl.setUrl("http://missing.invalid/logo.png");
        sameUrl.setConnected(false);

        Connection otherUrl = new Connection();
        otherUrl.setUrl("https://cdn.example.com/style.css");
        otherUrl.setStatusCode(403);
        otherUrl.setConnected(true);

        Result result = new Result();
        result.setDocument(new byte[] { 1 });
        result.setMissingResources(new MissingResource[] { missing });
        result.setConnections(new Connection[] { sameUrl, otherUrl });

        PdfProblemReport report = PdfProblemReport.of(PdfDiagnostics.fromResult(result));

        assertThat(report.getKind()).isEqualTo(PdfProblemReport.Kind.NONE);
        assertThat(report.getDetails()).containsExactly(
                "Missing resource: http://missing.invalid/logo.png (could not be loaded)",
                "https://cdn.example.com/style.css -> HTTP 403 (authentication/authorization)");
        // F2a: the warning case now populates the technical disclosure with the
        // verbatim raw diagnostics (every missing resource + failed connection).
        assertThat(report.getTechnical())
                .contains("Missing resource: http://missing.invalid/logo.png")
                .contains("Connection: https://cdn.example.com/style.css -> 403");
    }

    @Test
    void abortReportDeduplicatesDiagnosticsForTheSameResource() {
        MissingResource missing = new MissingResource();
        missing.setResource("http://missing.invalid/logo.png");

        Result result = new Result();
        result.setError(MISSING_ABORT);
        result.setMissingResources(new MissingResource[] { missing });

        PdfReactorException error = new PdfReactorException("failed",
                new com.realobjects.pdfreactor.webservice.client.PDFreactorWebserviceException(MISSING_ABORT, result),
                PdfDiagnostics.fromResult(result));

        PdfProblemReport report = PdfProblemReport.of(error);

        // The resource appears once, not once from the abort string and
        // again from the missingResources array.
        assertThat(report.getDetails()).hasSize(1);
    }
}
