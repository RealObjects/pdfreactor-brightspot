package com.realobjects.brightspot.pdfreactor;

/**
 * Result of a PDFreactor Web Service health check. Distinguishes the
 * service being down from per-conversion resource failures, which are
 * reported via {@link PdfDiagnostics} instead.
 */
public final class PdfServiceHealth {

    public enum Status {
        UP,
        DOWN
    }

    private final Status status;
    private final String version;
    private final String error;

    private PdfServiceHealth(Status status, String version, String error) {
        this.status = status;
        this.version = version;
        this.error = error;
    }

    public static PdfServiceHealth up(String version) {
        return new PdfServiceHealth(Status.UP, version, null);
    }

    public static PdfServiceHealth down(String error) {
        return new PdfServiceHealth(Status.DOWN, null, error);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isUp() {
        return status == Status.UP;
    }

    /**
     * @return Nullable. The service version when {@link #isUp()}.
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return Nullable. The failure reason when not {@link #isUp()}.
     */
    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return isUp() ? "UP (PDFreactor " + version + ")" : "DOWN (" + error + ")";
    }
}
