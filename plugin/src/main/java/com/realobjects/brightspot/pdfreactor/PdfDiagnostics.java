package com.realobjects.brightspot.pdfreactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.realobjects.pdfreactor.webservice.client.Connection;
import com.realobjects.pdfreactor.webservice.client.MissingResource;
import com.realobjects.pdfreactor.webservice.client.Result;

/**
 * Machine-readable diagnostics extracted from a PDFreactor {@link Result}:
 * missing resources, per-resource HTTP connections (distinguishing auth
 * failures from unreachability), the service error message, and the
 * conversion log.
 */
public final class PdfDiagnostics {

    private static final PdfDiagnostics EMPTY =
            new PdfDiagnostics(null, Collections.emptyList(), Collections.emptyList(), null);

    private final String error;
    private final List<MissingResourceInfo> missingResources;
    private final List<ConnectionInfo> connections;
    private final String logText;

    private PdfDiagnostics(
            String error,
            List<MissingResourceInfo> missingResources,
            List<ConnectionInfo> connections,
            String logText) {

        this.error = error;
        this.missingResources = missingResources;
        this.connections = connections;
        this.logText = logText;
    }

    /**
     * Extracts diagnostics from the given {@code result}.
     *
     * @param result Nullable.
     * @return Nonnull. Empty diagnostics if {@code result} is {@code null}.
     */
    public static PdfDiagnostics fromResult(Result result) {
        if (result == null) {
            return EMPTY;
        }

        List<MissingResourceInfo> missing = new ArrayList<>();
        MissingResource[] missingResources = result.getMissingResources();
        if (missingResources != null) {
            for (MissingResource resource : missingResources) {
                missing.add(new MissingResourceInfo(
                        resource.getResource(),
                        resource.getType() != null ? resource.getType().name() : null,
                        resource.getMessage()));
            }
        }

        List<ConnectionInfo> connections = new ArrayList<>();
        Connection[] resultConnections = result.getConnections();
        if (resultConnections != null) {
            for (Connection connection : resultConnections) {
                connections.add(new ConnectionInfo(
                        connection.getUrl(),
                        connection.getStatusCode(),
                        connection.getStatusMessage(),
                        connection.isConnected(),
                        connection.getError()));
            }
        }

        return new PdfDiagnostics(
                result.getError(),
                Collections.unmodifiableList(missing),
                Collections.unmodifiableList(connections),
                result.getLog() != null ? result.getLog().toString() : null);
    }

    public static PdfDiagnostics empty() {
        return EMPTY;
    }

    /**
     * @return Nullable.
     */
    public String getError() {
        return error;
    }

    /**
     * @return Nonnull.
     */
    public List<MissingResourceInfo> getMissingResources() {
        return missingResources;
    }

    /**
     * @return Nonnull. All connections observed during the conversion.
     */
    public List<ConnectionInfo> getConnections() {
        return connections;
    }

    /**
     * @return Nonnull. Connections that failed (unreachable or HTTP error
     *         status such as {@code 401}/{@code 403}/{@code 5xx}).
     */
    public List<ConnectionInfo> getFailedConnections() {
        return connections.stream()
                .filter(ConnectionInfo::isFailure)
                .collect(Collectors.toList());
    }

    /**
     * @return Nullable.
     */
    public String getLogText() {
        return logText;
    }

    public boolean hasProblems() {
        return error != null || !missingResources.isEmpty() || !getFailedConnections().isEmpty();
    }

    /**
     * A resource that could not be loaded during conversion.
     */
    public static final class MissingResourceInfo {

        private final String resource;
        private final String type;
        private final String message;

        public MissingResourceInfo(String resource, String type, String message) {
            this.resource = resource;
            this.type = type;
            this.message = message;
        }

        public String getResource() {
            return resource;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return resource + " (" + type + "): " + message;
        }
    }

    /**
     * An HTTP connection made by the PDFreactor service while fetching
     * document resources.
     */
    public static final class ConnectionInfo {

        private final String url;
        private final int statusCode;
        private final String statusMessage;
        private final boolean connected;
        private final String error;

        public ConnectionInfo(String url, int statusCode, String statusMessage, boolean connected, String error) {
            this.url = url;
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.connected = connected;
            this.error = error;
        }

        public String getUrl() {
            return url;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public boolean isConnected() {
            return connected;
        }

        public String getError() {
            return error;
        }

        public boolean isFailure() {
            return !connected || statusCode >= 400 || error != null;
        }

        @Override
        public String toString() {
            return url + " -> "
                    + (connected ? statusCode + " " + Objects.toString(statusMessage, "") : "unreachable")
                    + (error != null ? " (" + error + ")" : "");
        }
    }
}
