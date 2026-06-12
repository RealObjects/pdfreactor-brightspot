package com.realobjects.brightspot.pdfreactor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.psddev.dari.html.RawNode;
import com.psddev.dari.html.metadata.StyleElement;
import com.psddev.dari.html.scripting.ScriptElement;

import static com.psddev.dari.html.Nodes.SCRIPT;

/**
 * Loads the plugin's static web assets (CSS / JS) from the classpath so they
 * live as real {@code .css}/{@code .js} files under
 * {@code com/realobjects/brightspot/pdfreactor/web/} — editable with proper
 * tooling — rather than as concatenated Java string literals.
 *
 * <p>The text is read once and cached (the assets are immutable in a built
 * jar). {@link #styleSheet(String)} / {@link #inlineScript(String)} wrap the
 * loaded text in a dari-html {@code <style>}/{@code <script>} node for
 * inlining into a Tool page: the content is emitted <em>raw</em> (a
 * {@code RawNode} for the stylesheet, since {@link StyleElement}'s default
 * text node would HTML-escape a CSS {@code >} combinator; {@code SCRIPT}'s
 * content is already raw), so the CSS/JS is never entity-mangled.</p>
 */
public final class ToolResources {

    /** Classpath folder holding the plugin's static web assets. */
    public static final String WEB = "com/realobjects/brightspot/pdfreactor/web/";

    private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();

    private ToolResources() {
    }

    /**
     * Reads a UTF-8 classpath resource to a string, cached.
     *
     * @param classpathPath Nonnull. The full classpath path (e.g.
     *        {@code WEB + "preview-frame.css"}).
     * @return Nonnull.
     * @throws IllegalStateException If the resource is missing or unreadable
     *         (a packaging error — these assets ship in the plugin jar).
     */
    public static String text(String classpathPath) {
        return CACHE.computeIfAbsent(classpathPath, ToolResources::read);
    }

    /**
     * The loaded CSS as an inline {@code <style>} node (raw content).
     *
     * @param classpathPath Nonnull.
     * @return Nonnull.
     */
    public static StyleElement styleSheet(String classpathPath) {
        return new StyleElement(
                Collections.emptyMap(),
                Collections.singletonList(new RawNode(text(classpathPath))));
    }

    /**
     * The loaded JavaScript as an inline {@code <script>} node (raw content).
     *
     * @param classpathPath Nonnull.
     * @return Nonnull.
     */
    public static ScriptElement inlineScript(String classpathPath) {
        return SCRIPT.with(text(classpathPath));
    }

    private static String read(String classpathPath) {
        InputStream in = open(classpathPath);
        if (in == null) {
            throw new IllegalStateException(
                    "Missing plugin web asset on the classpath: [" + classpathPath + "].");
        }
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not read the plugin web asset [" + classpathPath + "].", error);
        }
    }

    /**
     * Opens the resource via the context classloader, falling back to the
     * plugin's own classloader whenever the TCCL <em>lookup fails</em> (returns
     * null) — not only when the TCCL itself is null. In some Tool contexts the
     * TCCL is set but does not see the plugin jar's resources.
     */
    private static InputStream open(String classpathPath) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        InputStream in = tccl != null ? tccl.getResourceAsStream(classpathPath) : null;
        if (in == null) {
            in = ToolResources.class.getClassLoader().getResourceAsStream(classpathPath);
        }
        return in;
    }
}
