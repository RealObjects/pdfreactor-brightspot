package com.realobjects.brightspot.pdfreactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.realobjects.pdfreactor.webservice.client.ClientTimeoutException;
import com.realobjects.pdfreactor.webservice.client.ServiceUnavailableException;
import com.realobjects.pdfreactor.webservice.client.UnreachableServiceException;

/**
 * Editor-facing view of a conversion problem: a failure {@link Kind} (so
 * callers can localize a remedy), concise de-duplicated detail lines, and
 * the raw technical text for a collapsible details section.
 *
 * <p>Exists because the raw error chain is hostile to editors: PDFreactor's
 * abort strings repeat the resource URL and reason several times
 * ({@code Missing Resource: Missing resource X (IMAGE): Image "X" could not
 * be loaded | unknown host}), the service wrapper prefixes them again, and
 * on an abort the diagnostics' error field carries the same text as the
 * exception message. This class parses the known shapes once and never
 * repeats a fact.</p>
 */
public final class PdfProblemReport {

    /** Failure classification; callers map this to a localized remedy. */
    public enum Kind {
        MISSING_RESOURCE,
        LICENSE,
        SERVICE,
        /** The plugin could not fetch/render the content's HTML (permalink). */
        RENDER_SOURCE,
        /** A configuration input was unreadable/invalid (ICC profile, raw JSON). */
        CONFIG,
        GENERIC,
        /** Non-fatal diagnostics only (preview warning banner). */
        NONE
    }

    // "Missing Resource: Missing resource <res> (<TYPE>): <msg> | <reason>"
    private static final Pattern MISSING_RESOURCE_ABORT = Pattern.compile(
            "Missing Resource: Missing resource (.+?) \\(([A-Z_]+)\\): .*?(?:\\| (.+))?$");

    /** Cap on the conversion log surfaced in the UI; the full log is in the server logs. */
    private static final int MAX_LOG_CHARS = 10_000;

    private final Kind kind;
    private final List<String> details;
    private final String technical;
    private final String logText;

    private PdfProblemReport(Kind kind, List<String> details, String technical, String logText) {
        this.kind = kind;
        this.details = Collections.unmodifiableList(details);
        this.technical = technical;
        this.logText = logText;
    }

    /** Truncates the conversion log for display; {@code null}/blank stays {@code null}. */
    private static String capLog(String log) {
        if (log == null || log.trim().isEmpty()) {
            return null;
        }
        return log.length() <= MAX_LOG_CHARS
                ? log
                : log.substring(0, MAX_LOG_CHARS) + "\n… (truncated; full log in the server logs)";
    }

    /**
     * Builds the report for a failed conversion.
     *
     * @param error Nonnull.
     */
    public static PdfProblemReport of(PdfReactorException error) {
        Objects.requireNonNull(error, "error");
        PdfDiagnostics diagnostics = error.getDiagnostics();
        String log = capLog(diagnostics.getLogText());

        Throwable cause = error.getCause();
        if (cause instanceof UnreachableServiceException
                || cause instanceof ServiceUnavailableException
                || cause instanceof ClientTimeoutException) {
            return new PdfProblemReport(
                    Kind.SERVICE,
                    Collections.singletonList("The PDFreactor service did not answer."),
                    cause.getMessage(),
                    log);
        }

        // Plugin-owned failure messages (we control these exact phrases) get a
        // tailored remedy instead of falling through to GENERIC.
        String message = error.getMessage();
        if (message != null) {
            if (isRenderSourceError(message)) {
                return new PdfProblemReport(Kind.RENDER_SOURCE, Collections.singletonList(message), null, log);
            }
            if (message.contains("ICC profile") || message.contains("configuration JSON")) {
                return new PdfProblemReport(Kind.CONFIG, Collections.singletonList(message), null, log);
            }
        }

        String serviceError = diagnostics.getError() != null
                ? diagnostics.getError()
                : cause != null ? cause.getMessage() : error.getMessage();

        if (serviceError != null) {
            Matcher missing = MISSING_RESOURCE_ABORT.matcher(serviceError);
            if (missing.matches()) {
                StringBuilder line = new StringBuilder("Missing ")
                        .append(missing.group(2).toLowerCase(Locale.ROOT))
                        .append(": ")
                        .append(missing.group(1));
                if (missing.group(3) != null) {
                    line.append(" — ").append(missing.group(3).trim());
                }
                List<String> details = new ArrayList<>();
                details.add(line.toString());
                appendDiagnosticLines(details, diagnostics, missing.group(1));
                return new PdfProblemReport(Kind.MISSING_RESOURCE, details, serviceError, log);
            }

            if (serviceError.startsWith("License:")) {
                return new PdfProblemReport(
                        Kind.LICENSE,
                        Collections.singletonList(serviceError.substring("License:".length()).trim()),
                        serviceError,
                        log);
            }
        }

        List<String> details = new ArrayList<>();
        if (serviceError != null) {
            details.add(serviceError);
        }
        appendDiagnosticLines(details, diagnostics, null);
        if (details.isEmpty()) {
            details.add(error.getMessage());
        }
        return new PdfProblemReport(Kind.GENERIC, details,
                serviceError != null && !details.contains(serviceError) ? serviceError : null,
                log);
    }

    /**
     * Builds the report for non-fatal diagnostics (the preview warning
     * banner: the conversion succeeded with problems).
     *
     * @param diagnostics Nonnull.
     */
    public static PdfProblemReport of(PdfDiagnostics diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        List<String> details = new ArrayList<>();
        if (diagnostics.getError() != null) {
            details.add(diagnostics.getError());
        }
        appendDiagnosticLines(details, diagnostics, null);
        // Populate the technical disclosure for the warning case too. The
        // detail bullets above are reformatted/de-duplicated summaries; the
        // technical dump is the verbatim raw diagnostics (resource type,
        // status message, connection error) for whoever needs the full picture.
        return new PdfProblemReport(Kind.NONE, details, rawDiagnostics(diagnostics),
                capLog(diagnostics.getLogText()));
    }

    /**
     * A verbatim dump of the raw diagnostics (service error, every missing
     * resource with its type/message, every failed connection with its status
     * and error) for the collapsible technical section. {@code null} when the
     * diagnostics carry nothing dumpable.
     */
    private static String rawDiagnostics(PdfDiagnostics diagnostics) {
        StringBuilder raw = new StringBuilder();
        if (diagnostics.getError() != null) {
            raw.append(diagnostics.getError());
        }
        for (PdfDiagnostics.MissingResourceInfo missing : diagnostics.getMissingResources()) {
            appendLine(raw, "Missing resource: " + missing);
        }
        for (PdfDiagnostics.ConnectionInfo connection : diagnostics.getFailedConnections()) {
            appendLine(raw, "Connection: " + connection);
        }
        return raw.length() == 0 ? null : raw.toString();
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    /**
     * Whether {@code message} is one of {@code PermalinkHtmlSource}'s own
     * render-source failures (the publish path fetching the content's
     * permalink). Matches phrases the plugin controls, so it is stable.
     */
    private static boolean isRenderSourceError(String message) {
        return message.contains("for PDF rendering")
                || message.contains("permalink to render")
                || message.contains("build a render URL");
    }

    /**
     * Adds missing-resource and failed-connection lines, skipping facts
     * already covered: a connection whose URL appears in a missing-resource
     * line (or in {@code alreadyReported}) would repeat it.
     */
    private static void appendDiagnosticLines(
            List<String> details,
            PdfDiagnostics diagnostics,
            String alreadyReported) {

        List<String> reportedUrls = new ArrayList<>();
        if (alreadyReported != null) {
            reportedUrls.add(alreadyReported);
        }

        for (PdfDiagnostics.MissingResourceInfo missing : diagnostics.getMissingResources()) {
            if (missing.getResource() != null && reportedUrls.contains(missing.getResource())) {
                continue;
            }
            StringBuilder line = new StringBuilder("Missing ");
            line.append(missing.getType() != null
                    ? missing.getType().toLowerCase(Locale.ROOT)
                    : "resource");
            line.append(": ").append(missing.getResource());
            if (missing.getMessage() != null) {
                line.append(" (").append(missing.getMessage()).append(')');
            }
            details.add(line.toString());
            if (missing.getResource() != null) {
                reportedUrls.add(missing.getResource());
            }
        }

        for (PdfDiagnostics.ConnectionInfo connection : diagnostics.getFailedConnections()) {
            if (connection.getUrl() != null && reportedUrls.contains(connection.getUrl())) {
                continue;
            }
            StringBuilder line = new StringBuilder(String.valueOf(connection.getUrl())).append(" -> ");
            if (!connection.isConnected()) {
                line.append("unreachable");
            } else if (connection.getStatusCode() == 401 || connection.getStatusCode() == 403) {
                line.append("HTTP ").append(connection.getStatusCode())
                        .append(" (authentication/authorization)");
            } else {
                line.append("HTTP ").append(connection.getStatusCode());
            }
            if (connection.getError() != null) {
                line.append(" (").append(connection.getError()).append(')');
            }
            details.add(line.toString());
        }
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * @return Nonnull. Concise, de-duplicated, editor-readable lines.
     */
    public List<String> getDetails() {
        return details;
    }

    /**
     * @return Nullable. The raw service error for a collapsible technical
     *         section; {@code null} when it would only repeat the details.
     */
    public String getTechnical() {
        return technical;
    }

    /**
     * @return Nullable. The (capped) PDFreactor conversion log, for a
     *         collapsible diagnostics section; {@code null} when the service
     *         returned no log.
     */
    public String getLogText() {
        return logText;
    }
}
