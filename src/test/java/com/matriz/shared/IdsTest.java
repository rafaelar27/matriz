package com.matriz.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Identifier parsing as pure logic (API-A4; INC-01 deviation: UUID identifiers). No Spring context, no database. */
class IdsTest {

    @Test
    @DisplayName("API-A4 · a valid UUID parses to itself")
    void validUuid() {
        String id = "a0000000-0000-0000-0000-0000000000a1";
        assertThat(Ids.parse(id)).contains(UUID.fromString(id));
    }

    @ParameterizedTest(name = "[{0}] gives empty")
    @ValueSource(strings = {"abc", ""})
    @NullSource
    @DisplayName("API-A4 · text that is not a UUID, empty text and null give empty")
    void invalidGivesEmpty(String value) {
        assertThat(Ids.parse(value)).isEmpty();
    }
}
