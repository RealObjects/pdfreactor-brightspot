package com.realobjects.brightspot.pdfreactor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Small SHA-256 hex helper shared by the cache key and the config
 * fingerprint.
 */
final class Hashing {

    private Hashing() {
    }

    /**
     * Starts a collision-proof canonical string builder for hashing a set of
     * named values. Each field is one {@code name=<token>} line: the token is
     * the URL-safe, unpadded Base64 of the value (so it can never contain the
     * {@code =} separator, the {@code \n} line break, or the reserved
     * null-token character), and {@code null} is the reserved {@code ~null~}
     * token, which Base64 can never produce. So two distinct value sets can
     * never produce the same canonical string — fixing the old space-joined,
     * {@code "null"}-literal scheme where boundary-shifting inputs (e.g.
     * {@code title="x null", author=null} vs {@code title="x", author="null"})
     * collided.
     */
    static Canonical canonical() {
        return new Canonical();
    }

    /** Builder for {@link #canonical()}. */
    static final class Canonical {

        private final StringBuilder builder = new StringBuilder();

        private Canonical() {
        }

        /** Adds a string field. A {@code null} value is the reserved null token. */
        Canonical add(String name, String value) {
            builder.append(name).append('=').append(encode(value)).append('\n');
            return this;
        }

        /**
         * Adds a byte-valued field by its content hash (or the reserved null
         * token when absent), so large blobs (ICC profiles) bust the key on a
         * byte change without bloating the canonical string.
         */
        Canonical add(String name, byte[] value) {
            return add(name, value == null ? null : "sha256:" + sha256Hex(value));
        }

        /** The SHA-256 hex digest of the canonical string. */
        String digest() {
            return sha256Hex(builder.toString());
        }

        /** The raw canonical string (for collision-regression tests). */
        String canonicalString() {
            return builder.toString();
        }

        private static String encode(String value) {
            return value == null
                    ? "~null~"
                    : Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** @param value Nullable; {@code null} yields the literal {@code "null"}. */
    static String sha256Hex(String value) {
        return hex(digest((value == null ? "null" : value).getBytes(StandardCharsets.UTF_8)));
    }

    /** @param bytes Nullable; {@code null} yields {@code "none"}. */
    static String sha256Hex(byte[] bytes) {
        return bytes == null ? "none" : hex(digest(bytes));
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException error) {
            // SHA-256 is mandatory in every JRE.
            throw new IllegalStateException(error);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
