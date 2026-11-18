package com.matriz.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Page and size query parameters as pure logic (R-01; FR-08, FR-13; openapi.yaml listProjects, listTasks). No Spring
 * context and no database. Expected: page is a whole number of at least 0 (default 0), size a whole number from 1 to
 * 100 (default 20). A refusal is a 422 {@code validation_failed} on the field, with the caller's top-level message.
 */
class PageParamsTest {

    private static final String MESSAGE = "The list could not be loaded.";

    private static void assertRefused(Runnable call, String field) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(e.error().code()).isEqualTo("validation_failed");
            assertThat(e.error().message()).isEqualTo(MESSAGE);
            assertThat(e.error().details()).extracting(d -> d.field()).contains(field);
        });
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · a missing or empty page or size gives the defaults 0 and 20")
    void defaultsApplyWhenMissingOrEmpty() {
        assertThat(PageParams.page(null, MESSAGE)).isZero();
        assertThat(PageParams.page("", MESSAGE)).isZero();
        assertThat(PageParams.size(null, MESSAGE)).isEqualTo(20);
        assertThat(PageParams.size("", MESSAGE)).isEqualTo(20);
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · size 1 and 100 are accepted")
    void sizeBoundsAreAccepted() {
        assertThat(PageParams.size("1", MESSAGE)).isEqualTo(1);
        assertThat(PageParams.size("100", MESSAGE)).isEqualTo(100);
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · size 0, 101, -1, text and a number beyond int are refused on field size")
    void invalidSizesAreRefused() {
        for (String bad : new String[] {"0", "101", "-1", "abc", "99999999999"}) {
            assertRefused(() -> PageParams.size(bad, MESSAGE), "size");
        }
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · page 0 is accepted")
    void pageZeroIsAccepted() {
        assertThat(PageParams.page("0", MESSAGE)).isZero();
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · page -1, text and a number beyond int are refused on field page")
    void invalidPagesAreRefused() {
        for (String bad : new String[] {"-1", "abc", "99999999999"}) {
            assertRefused(() -> PageParams.page(bad, MESSAGE), "page");
        }
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · page * size up to 2147483647 is accepted (computed as long)")
    void pageTimesSizeAtTheLimitIsAccepted() {
        assertThat(PageParams.page("2147483647", 1, MESSAGE)).isEqualTo(2147483647);
        assertThat(PageParams.page("21474836", 100, MESSAGE)).isEqualTo(21474836);
        assertThat(PageParams.page(null, 100, MESSAGE)).isZero();
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · page * size above 2147483647 is refused on field page with the caller's message")
    void pageTimesSizeBeyondTheLimitIsRefused() {
        assertRefused(() -> PageParams.page("21474837", 100, MESSAGE), "page");
        assertRefused(() -> PageParams.page("2147483647", 2, MESSAGE), "page");
    }

    @Test
    @DisplayName("R-01 · FR-08, FR-13 · with a known size, invalid page strings are still refused on field page")
    void invalidPagesAreRefusedWithKnownSize() {
        for (String bad : new String[] {"-1", "abc", "99999999999"}) {
            assertRefused(() -> PageParams.page(bad, 20, MESSAGE), "page");
        }
    }
}
