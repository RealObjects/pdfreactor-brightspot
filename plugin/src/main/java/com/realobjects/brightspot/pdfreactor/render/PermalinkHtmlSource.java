package com.realobjects.brightspot.pdfreactor.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.psddev.cms.db.Directory;
import com.psddev.dari.db.State;
import com.psddev.dari.util.Settings;
import com.realobjects.brightspot.pdfreactor.PdfReactorException;
import com.realobjects.brightspot.pdfreactor.SettingsPdfReactorConfig;

/**
 * Renders content by fetching its published permalink over internal HTTP —
 * the path for contexts without a suitable request (e.g. the publish
 * {@code Task}), where the View System cannot render in-process.
 *
 * <p>The fetch URL is {@value #INTERNAL_RENDER_BASE_URL_SETTING} (e.g.
 * {@code http://localhost:8080}) plus the content's permalink path; without
 * that setting the content's full permalink (default site URL) is fetched
 * directly. The PDF base URL is always the public full permalink, so
 * relative resource URLs resolve to hosts reachable by PDFreactor.</p>
 */
public class PermalinkHtmlSource implements HtmlSource {

    /**
     * Optional base URL for the internal HTTP fetch of the rendered page,
     * e.g. {@code http://localhost:8080} when the public site URL is not
     * reachable from the application's own network.
     */
    public static final String INTERNAL_RENDER_BASE_URL_SETTING =
            SettingsPdfReactorConfig.SETTING_PREFIX + "/internalRenderBaseUrl";

    private static final int MAX_REDIRECTS = 5;

    /**
     * Upper bound on the fetched page size (the whole body is buffered in
     * memory on the publish thread, so an unbounded or runaway response must
     * not exhaust the heap).
     */
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

    private final int fetchTimeoutMillis;

    public PermalinkHtmlSource() {
        this(60_000);
    }

    public PermalinkHtmlSource(int fetchTimeoutMillis) {
        this.fetchTimeoutMillis = fetchTimeoutMillis;
    }

    @Override
    public RenderedHtml render(Object content) {
        Objects.requireNonNull(content, "content");

        Directory.ObjectModification directoryData = State.getInstance(content)
                .as(Directory.ObjectModification.class);

        String permalinkPath = directoryData.getPermalink();
        if (permalinkPath == null || permalinkPath.trim().isEmpty()) {
            throw new PdfReactorException(
                    "Content [" + State.getInstance(content).getId()
                            + "] has no permalink to render.");
        }

        String fullPermalink = fullPermalinkOrNull(directoryData);
        String internalBaseUrl = Settings.getOrDefault(
                String.class, INTERNAL_RENDER_BASE_URL_SETTING, null);

        String fetchUrl;
        if (internalBaseUrl != null && !internalBaseUrl.trim().isEmpty()) {
            fetchUrl = internalFetchUrl(stripTrailingSlash(internalBaseUrl.trim()), permalinkPath);
        } else if (fullPermalink != null) {
            fetchUrl = fullPermalink;
        } else {
            throw new PdfReactorException(
                    "Cannot build a render URL for [" + permalinkPath + "]: neither the ["
                            + INTERNAL_RENDER_BASE_URL_SETTING
                            + "] setting nor a default site URL is configured.");
        }

        // Allow redirects only to the start host plus the public permalink host
        // (a benign canonical redirect to the public site), never to an
        // arbitrary internal service.
        Set<String> allowedHosts = new HashSet<>();
        addHost(allowedHosts, fullPermalink);
        addHost(allowedHosts, internalBaseUrl);

        String html = fetch(fetchUrl, allowedHosts);
        return new RenderedHtml(html, fullPermalink != null ? fullPermalink : fetchUrl);
    }

    /**
     * Builds the internal fetch URL, URL-encoding the permalink path so a path
     * with spaces or other unsafe characters does not produce a malformed URL.
     */
    private static String internalFetchUrl(String baseUrl, String permalinkPath) {
        try {
            URI base = new URI(baseUrl);
            String path = (base.getRawPath() == null ? "" : base.getRawPath()) + permalinkPath;
            return new URI(base.getScheme(), base.getAuthority(), path, null, null).toString();
        } catch (URISyntaxException error) {
            throw new PdfReactorException(
                    "Invalid internal render base URL [" + baseUrl + "].", error);
        }
    }

    private static void addHost(Set<String> hosts, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        try {
            String host = new URI(url.trim()).getHost();
            if (host != null) {
                hosts.add(host);
            }
        } catch (URISyntaxException ignored) {
            // Not a parseable URL — contributes no allowed host.
        }
    }

    private static String fullPermalinkOrNull(Directory.ObjectModification directoryData) {
        try {
            return directoryData.getFullPermalink();
        } catch (IllegalStateException noDefaultSiteUrl) {
            return null;
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Fetches the given URL, following redirects manually (including
     * cross-protocol http&harr;https hops, which the JDK auto-follower
     * refuses). The start URL's host is always allowed; redirects may only stay
     * within that host or {@code additionalAllowedHosts}.
     */
    String fetch(String url) {
        return fetch(url, Collections.emptySet());
    }

    String fetch(String url, Set<String> additionalAllowedHosts) {
        Set<String> allowedHosts = new HashSet<>(additionalAllowedHosts);
        URL start;
        try {
            start = new URL(url);
        } catch (MalformedURLException error) {
            throw new PdfReactorException("Invalid render URL [" + url + "].", error);
        }
        allowedHosts.add(start.getHost());

        // Overall deadline across every hop, so up to MAX_REDIRECTS+1 hops each
        // with its own connect+read timeout cannot sum to an unbounded wait.
        long deadline = System.currentTimeMillis() + (long) (MAX_REDIRECTS + 1) * fetchTimeoutMillis;

        String currentUrl = url;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (System.currentTimeMillis() > deadline) {
                throw new PdfReactorException(
                        "Timed out fetching [" + url + "] for PDF rendering.");
            }

            URL target;
            try {
                target = new URL(currentUrl);
            } catch (MalformedURLException error) {
                throw new PdfReactorException("Invalid redirect URL [" + currentUrl + "].", error);
            }
            // Reject non-HTTP(S) BEFORE openConnection (a file:/mailto: target
            // would otherwise blow up as a raw ClassCastException), and refuse
            // hosts outside the allowlist (SSRF: the body lands inside the PDF).
            String scheme = target.getProtocol();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new PdfReactorException(
                        "Refusing non-HTTP(S) URL [" + currentUrl + "] for PDF rendering.");
            }
            if (!allowedHosts.contains(target.getHost())) {
                throw new PdfReactorException("Refusing redirect to disallowed host ["
                        + target.getHost() + "] while fetching [" + url + "] for PDF rendering.");
            }

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) target.openConnection();
                connection.setConnectTimeout(fetchTimeoutMillis);
                connection.setReadTimeout(fetchTimeoutMillis);
                // Auto-follow OFF so a single mechanism — the loop below —
                // handles every redirect, including the http<->https hops the
                // JDK auto-follower silently drops.
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "text/html");

                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new PdfReactorException(
                                "Redirect without Location from [" + currentUrl + "].");
                    }
                    currentUrl = new URL(new URL(currentUrl), location).toString();
                    continue;
                }
                if (status != 200) {
                    throw new PdfReactorException(
                            "Fetching [" + currentUrl + "] for PDF rendering returned HTTP "
                                    + status + ".");
                }

                // Reject non-HTML bodies (JSON/binary would never render as a
                // page), and decode by the response charset with a UTF-8 fallback
                // (so a non-UTF-8 page is not mojibake'd).
                String contentType = connection.getContentType();
                if (contentType != null) {
                    String lower = contentType.toLowerCase(Locale.ROOT);
                    if (!lower.contains("html") && !lower.contains("xml")) {
                        throw new PdfReactorException("Expected HTML from [" + currentUrl
                                + "] for PDF rendering but got Content-Type [" + contentType + "].");
                    }
                }
                Charset charset = charsetOf(contentType);

                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int read;
                    int total = 0;
                    while ((read = input.read(chunk)) != -1) {
                        total += read;
                        if (total > MAX_RESPONSE_BYTES) {
                            throw new PdfReactorException("Rendered page at [" + currentUrl
                                    + "] exceeds the " + MAX_RESPONSE_BYTES
                                    + "-byte limit for PDF rendering.");
                        }
                        buffer.write(chunk, 0, read);
                    }
                    return new String(buffer.toByteArray(), charset);
                }

            } catch (IOException error) {
                throw new PdfReactorException(
                        "Failed to fetch [" + currentUrl + "] for PDF rendering.", error);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        throw new PdfReactorException(
                "Too many redirects fetching [" + url + "] for PDF rendering.");
    }

    /**
     * The charset named in a {@code Content-Type} header, or UTF-8 when absent
     * or unrecognized.
     */
    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String token = part.trim();
                if (token.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    String name = token.substring("charset=".length()).trim()
                            .replace("\"", "");
                    try {
                        return Charset.forName(name);
                    } catch (RuntimeException unsupported) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
