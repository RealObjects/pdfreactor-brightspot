package com.realobjects.brightspot.pdfreactor;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realobjects.pdfreactor.webservice.client.Configuration;

/**
 * The full-configuration pass-through: a raw JSON escape hatch so
 * the entire PDFreactor {@link Configuration} surface (~100+ properties) is
 * reachable without a plugin release.
 *
 * <p>JSON fragments are merged in layers (global → site → view → call) with a
 * deep merge — objects merge recursively, while arrays and scalars
 * <em>replace</em> — then applied onto the already-assembled
 * {@code Configuration} via Jackson's {@code readerForUpdating}. The client's
 * {@code Configuration} is a plain Jackson bean, so a default {@code JsonMapper}
 * reads JSON into it; unknown properties are ignored so a JSON written for a
 * newer client does not break older deployments.</p>
 *
 * <p>The fields the plugin owns — the document, the content observer, the
 * per-path error policies, conformance, color management, JavaScript, the
 * document title/author, and the user stylesheets — are <strong>re-enforced by
 * the caller after</strong> this merge (see {@link DefaultPdfReactorService}),
 * so a form setting or plugin decision always takes precedence and the
 * pass-through may only set properties the plugin does not own. (The configured
 * user stylesheets are kept and any pass-through sheets appended after them.)</p>
 */
public final class RawConfiguration {

    // Read-only mapper: unknown properties are ignored for forward
    // compatibility (a JSON written for a newer client still applies).
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private RawConfiguration() {
    }

    /**
     * Parses a JSON object string, or returns {@code null} for null/blank
     * input.
     *
     * @throws PdfReactorException If the string is non-blank but not valid
     *         JSON (a configuration error worth surfacing).
     */
    static JsonNode parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new PdfReactorException(
                    "Invalid PDFreactor configuration JSON: " + error.getOriginalMessage(),
                    error);
        }
    }

    /**
     * Deep-merges {@code overlay} onto {@code base}: object nodes merge
     * recursively; arrays and scalars from the overlay replace the base.
     * Either argument may be {@code null}.
     *
     * @return Nullable. {@code null} only if both inputs are {@code null}.
     */
    static JsonNode deepMerge(JsonNode base, JsonNode overlay) {
        if (base == null) {
            return overlay;
        }
        if (overlay == null) {
            return base;
        }
        if (!base.isObject() || !overlay.isObject()) {
            // Arrays and scalars replace wholesale.
            return overlay;
        }
        ObjectNode merged = ((ObjectNode) base).deepCopy();
        Iterator<Map.Entry<String, JsonNode>> fields = overlay.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode mergedValue = deepMerge(merged.get(field.getKey()), field.getValue());
            merged.set(field.getKey(), mergedValue);
        }
        return merged;
    }

    /**
     * Applies the given merged JSON tree onto the assembled configuration.
     * Top-level properties present in the JSON set (or, for objects already
     * present, replace) the corresponding configuration value; properties
     * absent from the JSON are left untouched.
     *
     * @param merged Nullable. {@code null} or an empty object is a no-op.
     */
    static void apply(Configuration configuration, JsonNode merged) {
        if (merged == null || merged.isEmpty()) {
            return;
        }
        try {
            MAPPER.readerForUpdating(configuration).readValue(merged);
        } catch (java.io.IOException error) {
            throw new PdfReactorException(
                    "Could not apply the PDFreactor configuration JSON: " + error.getMessage(),
                    error);
        }
    }

    /**
     * Convenience: deep-merge two JSON layer strings and apply onto the
     * configuration. The lower layer is {@code baseJson}; {@code overlayJson}
     * takes precedence.
     */
    static void merge(Configuration configuration, String baseJson, String overlayJson) {
        apply(configuration, deepMerge(parse(baseJson), parse(overlayJson)));
    }
}
