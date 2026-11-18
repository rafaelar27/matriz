package com.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Project date order rule as pure logic (INC-10; US-06, US-24; FR-06, BR-21). No Spring context, no database.
 * Expected: when both dates are given, the start date is not after the due date; otherwise 422 validation_failed on
 * field due_date. A single date, or none, is accepted.
 */
class ProjectDateOrderRuleTest {

    private static final LocalDate DAY = LocalDate.of(2030, 3, 15);

    @Test
    @DisplayName("BR-21, FR-06 · a start date equal to the due date is accepted")
    void sameDayAccepted() {
        assertThatCode(() -> ProjectService.requireDateOrder(DAY, DAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BR-21, FR-06 · a start date one day after the due date is refused 422 validation_failed on due_date")
    void startAfterDueRefused() {
        assertThatThrownBy(() -> ProjectService.requireDateOrder(DAY.plusDays(1), DAY))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("due_date");
                });
    }

    @Test
    @DisplayName("BR-21, FR-06 · a start date before the due date is accepted")
    void startBeforeDueAccepted() {
        assertThatCode(() -> ProjectService.requireDateOrder(DAY.minusDays(1), DAY)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"only start", "only due", "neither"})
    @DisplayName("BR-21 · with only one date, or none, the order is not checked")
    void singleOrNoDateAccepted(String which) {
        LocalDate start = which.equals("only start") ? DAY : null;
        LocalDate due = which.equals("only due") ? DAY : null;
        assertThatCode(() -> ProjectService.requireDateOrder(start, due)).doesNotThrowAnyException();
    }
}
