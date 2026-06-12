package com.realobjects.brightspot.pdfreactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Resolves an ICC-profile reference to its raw bytes <em>server-side</em>.
 *
 * <p>ICC profiles are conversion <em>configuration</em> — their bytes are
 * embedded in the conversion request (as an output intent or CMYK profile),
 * not fetched by the PDFreactor service as document resources. So the plugin
 * resolves them where the application runs, never relying on the PDFreactor
 * host having egress to the profile's URL.</p>
 *
 * <p>Supported references:</p>
 * <ul>
 *   <li>{@code classpath:path/to/profile.icc} — a resource on the plugin /
 *       application classpath (the form used by
 *       {@link DefaultPdfReactorConfiguration});</li>
 *   <li>any absolute URL ({@code https:}, {@code file:}, …) read by the
 *       application.</li>
 * </ul>
 */
public final class IccProfiles {

    public static final String CLASSPATH_PREFIX = "classpath:";

    // Bound the fetch: profile resolution runs on conversion AND on the
    // editor's save thread (via PdfConfigFingerprint), so a hung or oversized
    // profile host must not stall saves indefinitely. ICC profiles
    // are small (a few hundred KB; large device-link profiles a few MB), so an
    // 8 MB cap is generous. Mirrors PermalinkHtmlSource's timeout + cap pattern.
    private static final int FETCH_TIMEOUT_MILLIS = 10_000;
    static final int MAX_PROFILE_BYTES = 8 * 1024 * 1024;

    private IccProfiles() {
    }

    /**
     * Reads the profile bytes for the given reference.
     *
     * @param reference Nullable. {@code null} or blank yields {@code null}.
     * @return Nullable. The profile bytes, or {@code null} for no reference.
     * @throws PdfReactorException If the reference is set but cannot be read
     *         (a configuration error worth surfacing rather than silently
     *         dropping the profile and emitting a non-conformant PDF).
     */
    public static byte[] read(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return null;
        }
        String ref = reference.trim();
        try {
            if (ref.startsWith(CLASSPATH_PREFIX)) {
                String path = ref.substring(CLASSPATH_PREFIX.length());
                try (InputStream in = classLoader().getResourceAsStream(path)) {
                    if (in == null) {
                        throw new PdfReactorException(
                                "ICC profile classpath resource not found: [" + path + "].");
                    }
                    return readCapped(in, ref);
                }
            }
            URLConnection connection = new URL(ref).openConnection();
            connection.setConnectTimeout(FETCH_TIMEOUT_MILLIS);
            connection.setReadTimeout(FETCH_TIMEOUT_MILLIS);
            try (InputStream in = connection.getInputStream()) {
                return readCapped(in, ref);
            }
        } catch (IOException error) {
            throw new PdfReactorException(
                    "Could not read the ICC profile [" + ref + "]: " + error.getMessage(),
                    error);
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : IccProfiles.class.getClassLoader();
    }

    /**
     * Reads the stream fully, but aborts past {@link #MAX_PROFILE_BYTES} so a
     * runaway or hostile source cannot exhaust heap. Package-private for tests.
     */
    static byte[] readCapped(InputStream in, String ref) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_PROFILE_BYTES) {
                throw new PdfReactorException("ICC profile [" + ref + "] exceeds the "
                        + MAX_PROFILE_BYTES + "-byte limit.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
