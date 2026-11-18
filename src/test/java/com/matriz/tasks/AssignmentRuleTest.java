package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Assignment request rules as pure logic (INC-10; US-29; FR-22, BR-22; AC-29.3 for the repeat rule only). No Spring context, no database.
 * Expected (openapi assignTask "Rules, checked in this order"): 1 to 20 member ids, else 422 validation_failed on
 * member_ids; the same member twice in one request is 409 conflict on member_ids. Membership lookups, eligibility
 * and check order are not tested here.
 */
class AssignmentRuleTest {

    private static final UUID X = UUID.fromString("a0000000-0000-4000-8000-0000000000a1");
    private static final UUID Y = UUID.fromString("a0000000-0000-4000-8000-0000000000a2");

    private static List<String> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> String.format("a0000000-0000-4000-8000-%012d", i)).toList();
    }

    private static void assertCountRefused(List<String> value) {
        assertThatThrownBy(() -> AssignmentService.requireCount(value))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("member_ids");
                });
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"null list", "empty list"})
    @DisplayName("FR-22, BR-22 · a missing or empty member_ids list is refused 422 validation_failed on member_ids")
    void emptyRefused(String which) {
        assertCountRefused(which.equals("null list") ? null : new ArrayList<>());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20})
    @DisplayName("FR-22, BR-22 · 1 and 20 member ids are accepted")
    void boundsAccepted(int count) {
        assertThatCode(() -> AssignmentService.requireCount(ids(count))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FR-22, BR-22 · 21 member ids are refused 422 validation_failed on member_ids")
    void overMaxRefused() {
        assertCountRefused(ids(21));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"X,X", "X,Y,X"})
    @DisplayName("FR-22, BR-22, AC-29.3 · the same member twice in one request is refused 409 conflict on member_ids")
    void repeatRefused(String which) {
        List<UUID> list = which.equals("X,X") ? List.of(X, X) : List.of(X, Y, X);
        assertThatThrownBy(() -> AssignmentService.requireNoRepeats(list, id -> "Fictitious Member"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.error().code()).isEqualTo("conflict");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("member_ids");
                });
    }

    @Test
    @DisplayName("FR-22, BR-22 · distinct member ids raise no repeat refusal")
    void distinctAccepted() {
        assertThatCode(() -> AssignmentService.requireNoRepeats(List.of(X, Y), id -> "Fictitious Member"))
                .doesNotThrowAnyException();
    }
}
