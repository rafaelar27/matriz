package com.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The in-memory attempt counter as pure logic with a controllable clock (INC-12; NFR-11, EC-16; US-22, US-01;
 * AC-22.6). No Spring context and no database.
 */
class AttemptLimiterTest {

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private long millis = 1_000_000L;

        void advance(Duration d) {
            millis += d.toMillis();
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    private static final Duration HOUR = Duration.ofHours(1);

    private MutableClock clock;
    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new AttemptLimiter(clock);
    }

    @Test
    @DisplayName("AC-22.6 · the first max attempts are allowed and the next one is refused with a wait of at least 1 second")
    void refusesAfterMax() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.acquire("k", 5, HOUR)).isZero();
        }
        assertThat(limiter.acquire("k", 5, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("EC-16 · a refused attempt is not counted as free: it keeps being refused until the window ends")
    void keepsRefusingInsideTheWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire("k", 5, HOUR);
        }
        clock.advance(Duration.ofMinutes(59));
        assertThat(limiter.acquire("k", 5, HOUR)).isGreaterThanOrEqualTo(1);
        assertThat(limiter.acquire("k", 5, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · retryAfter is the seconds left in the window, rounded up, and never below 1")
    void retryAfterCountsDownAndRoundsUp() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire("k", 5, HOUR);
        }
        assertThat(limiter.retryAfter("k", 5, HOUR)).isEqualTo(3600);
        clock.advance(Duration.ofMinutes(30));
        assertThat(limiter.retryAfter("k", 5, HOUR)).isEqualTo(1800);
        clock.advance(Duration.ofMillis(1_799_500));
        assertThat(limiter.retryAfter("k", 5, HOUR)).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · the window expires: the same key may try again once the window length has passed")
    void windowExpiry() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire("k", 5, HOUR);
        }
        clock.advance(HOUR);
        assertThat(limiter.retryAfter("k", 5, HOUR)).isZero();
        assertThat(limiter.acquire("k", 5, HOUR)).isZero();
        // The new window counts from 1 again.
        for (int i = 0; i < 4; i++) {
            assertThat(limiter.acquire("k", 5, HOUR)).isZero();
        }
        assertThat(limiter.acquire("k", 5, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · the window is fixed: attempts spread over the window do not extend it")
    void windowDoesNotSlide() {
        limiter.acquire("k", 2, HOUR);
        clock.advance(Duration.ofMinutes(40));
        limiter.acquire("k", 2, HOUR);
        assertThat(limiter.retryAfter("k", 2, HOUR)).isEqualTo(1200);
        clock.advance(Duration.ofMinutes(20));
        assertThat(limiter.retryAfter("k", 2, HOUR)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · counters of different keys are independent")
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire("a", 5, HOUR);
        }
        assertThat(limiter.acquire("a", 5, HOUR)).isGreaterThanOrEqualTo(1);
        assertThat(limiter.acquire("b", 5, HOUR)).isZero();
    }

    @Test
    @DisplayName("AC-22.6 · record counts a failure without checking; retryAfter does not count")
    void recordCountsAndRetryAfterDoesNot() {
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.retryAfter("k", 10, HOUR)).isZero();
        }
        for (int i = 0; i < 9; i++) {
            limiter.record("k", HOUR);
        }
        assertThat(limiter.retryAfter("k", 10, HOUR)).isZero();
        limiter.record("k", HOUR);
        assertThat(limiter.retryAfter("k", 10, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · clear forgets every counter")
    void clearForgetsCounters() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire("k", 5, HOUR);
        }
        limiter.clear();
        assertThat(limiter.acquire("k", 5, HOUR)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · the number of keys is bounded: a flood of keys never grows the map past MAX_KEYS")
    void keyCountIsBounded() {
        // Fill to the bound with one live key at the front, then add keys until well past the bound. If the bound
        // held, the counter of the first key may have been dropped, but memory use did not grow without limit.
        for (int i = 0; i < AttemptLimiter.MAX_KEYS + 5; i++) {
            limiter.record("flood-" + i, HOUR);
        }
        // Observable effect: the clear made room, so an early key lost its counter and is allowed again.
        assertThat(limiter.retryAfter("flood-0", 1, HOUR)).isZero();
        // And the most recent key is still counted.
        assertThat(limiter.retryAfter("flood-" + (AttemptLimiter.MAX_KEYS + 4), 1, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · when the bound is reached, expired windows are dropped first and live counters are kept")
    void expiredWindowsAreDroppedBeforeLiveOnes() {
        for (int i = 0; i < AttemptLimiter.MAX_KEYS - 1; i++) {
            limiter.record("old-" + i, Duration.ofMinutes(15));
        }
        clock.advance(Duration.ofMinutes(16));
        limiter.record("live", HOUR);
        assertThat(limiter.retryAfter("live", 1, HOUR)).isGreaterThanOrEqualTo(1);
        // The map is now full; a new key forces room to be made. Only the expired windows go.
        limiter.record("another", HOUR);
        assertThat(limiter.retryAfter("live", 1, HOUR)).isGreaterThanOrEqualTo(1);
        assertThat(limiter.retryAfter("another", 1, HOUR)).isGreaterThanOrEqualTo(1);
    }
}
