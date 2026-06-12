package com.realobjects.brightspot.pdfreactor;

import com.fasterxml.jackson.databind.JsonNode;
import com.realobjects.pdfreactor.webservice.client.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawConfigurationTest {

    @Test
    void parseNullOrBlankYieldsNull() {
        assertThat(RawConfiguration.parse(null)).isNull();
        assertThat(RawConfiguration.parse("   ")).isNull();
    }

    @Test
    void parseInvalidJsonThrows() {
        assertThatThrownBy(() -> RawConfiguration.parse("{not json"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("Invalid PDFreactor configuration JSON");
    }

    @Test
    void deepMergeRecursesObjectsAndReplacesArraysAndScalars() {
        JsonNode base = RawConfiguration.parse(
                "{\"a\":1,\"obj\":{\"x\":1,\"y\":2},\"arr\":[1,2]}");
        JsonNode overlay = RawConfiguration.parse(
                "{\"a\":9,\"obj\":{\"y\":20,\"z\":30},\"arr\":[9]}");

        JsonNode merged = RawConfiguration.deepMerge(base, overlay);

        assertThat(merged.get("a").asInt()).isEqualTo(9); // scalar replaced
        assertThat(merged.get("obj").get("x").asInt()).isEqualTo(1); // kept
        assertThat(merged.get("obj").get("y").asInt()).isEqualTo(20); // overridden
        assertThat(merged.get("obj").get("z").asInt()).isEqualTo(30); // added
        assertThat(merged.get("arr")).hasSize(1); // array replaced
        assertThat(merged.get("arr").get(0).asInt()).isEqualTo(9);
    }

    @Test
    void deepMergeHandlesNullOperands() {
        JsonNode node = RawConfiguration.parse("{\"a\":1}");
        assertThat(RawConfiguration.deepMerge(null, node)).isSameAs(node);
        assertThat(RawConfiguration.deepMerge(node, null)).isSameAs(node);
        assertThat(RawConfiguration.deepMerge(null, null)).isNull();
    }

    @Test
    void applySetsTopLevelAndNestedProperties() {
        Configuration configuration = new Configuration();
        RawConfiguration.apply(configuration, RawConfiguration.parse(
                "{\"author\":\"Jane\",\"title\":\"Report\","
                        + "\"javaScriptSettings\":{\"disabled\":true}}"));

        assertThat(configuration.getAuthor()).isEqualTo("Jane");
        assertThat(configuration.getTitle()).isEqualTo("Report");
        assertThat(configuration.getJavaScriptSettings().getDisabled()).isTrue();
    }

    @Test
    void applyOverwritesExistingValue() {
        Configuration configuration = new Configuration().setAuthor("Original");
        RawConfiguration.apply(configuration, RawConfiguration.parse("{\"author\":\"Override\"}"));
        assertThat(configuration.getAuthor()).isEqualTo("Override");
    }

    @Test
    void applyNullOrEmptyIsNoOp() {
        Configuration configuration = new Configuration().setAuthor("Keep");
        RawConfiguration.apply(configuration, null);
        RawConfiguration.apply(configuration, RawConfiguration.parse("{}"));
        assertThat(configuration.getAuthor()).isEqualTo("Keep");
    }
}
