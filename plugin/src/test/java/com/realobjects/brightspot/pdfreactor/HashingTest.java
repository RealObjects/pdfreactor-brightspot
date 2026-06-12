package com.realobjects.brightspot.pdfreactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The collision-proof canonical builder: distinct value sets must never
 * produce the same digest, where the old space-joined, {@code "null"}-literal
 * scheme could.
 */
class HashingTest {

    @Test
    void nullAndEmptyStringAreDistinct() {
        // The old scheme rendered both as effectively nothing / the "null"
        // literal; they must hash differently now.
        assertThat(Hashing.canonical().add("a", (String) null).digest())
                .isNotEqualTo(Hashing.canonical().add("a", "").digest());
    }

    @Test
    void aValueCannotForgeAFieldBoundary() {
        // A value that textually contains a separator + another field name must
        // not collide with two genuine fields (Base64 of the value can produce
        // neither the '=' separator nor the '\n' line break).
        String oneFieldWithTrickyValue = Hashing.canonical().add("a", "x\nb=y").digest();
        String twoFields = Hashing.canonical().add("a", "x").add("b", "y").digest();
        assertThat(oneFieldWithTrickyValue).isNotEqualTo(twoFields);
    }

    @Test
    void boundaryShiftingValuesDoNotCollide() {
        // The review's example: ("x null", null) vs ("x", "null") collided
        // under the space-joined scheme.
        String a = Hashing.canonical().add("title", "x null").add("author", (String) null).digest();
        String b = Hashing.canonical().add("title", "x").add("author", "null").digest();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void isDeterministicAndHex() {
        String first = Hashing.canonical().add("a", "1").add("b", "2").digest();
        String second = Hashing.canonical().add("a", "1").add("b", "2").digest();
        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void byteValuesBustOnContentChangeAndDistinguishAbsent() {
        String none = Hashing.canonical().add("p", (byte[]) null).digest();
        String some = Hashing.canonical().add("p", new byte[] {1, 2, 3}).digest();
        String other = Hashing.canonical().add("p", new byte[] {4, 5, 6}).digest();
        assertThat(none).isNotEqualTo(some);
        assertThat(some).isNotEqualTo(other);
    }
}
