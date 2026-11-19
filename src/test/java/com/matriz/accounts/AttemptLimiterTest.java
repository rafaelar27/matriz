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

    private void fillWithLiveKeys(String prefix, int count) {
        for (int i = 0; i < count; i++) {
            limiter.record(prefix + i, HOUR);
        }
    }

    @Test
    @DisplayName("NFR-11 · SP-08 · when MAX_KEYS live windows exist, a new key is refused with a wait of at least 1 second and nothing is created")
    void newKeyIsRefusedWhenFull() {
        fillWithLiveKeys("flood-", AttemptLimiter.MAX_KEYS);

        long wait = limiter.acquire("newcomer", 5, HOUR);
        assertThat(wait).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(3600);
        // Nothing was created for it: it is refused again, and a lower-level check sees no counter.
        assertThat(limiter.acquire("newcomer", 5, HOUR)).isGreaterThanOrEqualTo(1);
        assertThat(limiter.retryAfter("newcomer", 1, HOUR)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · when full, the wait for a new key is the time until the earliest window ends")
    void waitIsUntilTheEarliestWindowEnds() {
        limiter.record("early", Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(4));
        fillWithLiveKeys("flood-", AttemptLimiter.MAX_KEYS - 1);

        assertThat(limiter.acquire("newcomer", 5, HOUR)).isEqualTo(6 * 60);
        // Once the earliest window has ended it is dropped, and the new key is accepted.
        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.acquire("newcomer", 5, HOUR)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · audit S-01 · a flood of MAX_KEYS other keys does not reset an existing lockout")
    void floodDoesNotResetALockout() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire("victim", 5, HOUR);
        }
        assertThat(limiter.retryAfter("victim", 5, HOUR)).isGreaterThanOrEqualTo(1);

        fillWithLiveKeys("flood-", AttemptLimiter.MAX_KEYS + 50);

        assertThat(limiter.retryAfter("victim", 5, HOUR)).isGreaterThanOrEqualTo(1);
        assertThat(limiter.acquire("victim", 5, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · when full, existing keys keep counting and a new failure for them is still recorded")
    void existingKeysKeepCountingWhenFull() {
        limiter.record("known", HOUR);
        fillWithLiveKeys("flood-", AttemptLimiter.MAX_KEYS - 1);

        assertThat(limiter.acquire("known", 3, HOUR)).isZero();
        assertThat(limiter.acquire("known", 3, HOUR)).isZero();
        assertThat(limiter.acquire("known", 3, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · when full, record for a new key is ignored")
    void recordForNewKeyIsIgnoredWhenFull() {
        fillWithLiveKeys("flood-", AttemptLimiter.MAX_KEYS);

        limiter.record("newcomer", HOUR);
        assertThat(limiter.retryAfter("newcomer", 1, HOUR)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · when full, expired windows are dropped first so a new key fits")
    void expiredWindowsMakeRoomWhenFull() {
        for (int i = 0; i < AttemptLimiter.MAX_KEYS; i++) {
            limiter.record("old-" + i, Duration.ofMinutes(15));
        }
        clock.advance(Duration.ofMinutes(15));

        assertThat(limiter.acquire("newcomer", 5, HOUR)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · release gives one attempt back and removes the key at 0")
    void releaseGivesAnAttemptBack() {
        limiter.acquire("k", 2, HOUR);
        limiter.acquire("k", 2, HOUR);
        assertThat(limiter.retryAfter("k", 2, HOUR)).isGreaterThanOrEqualTo(1);

        limiter.release("k");
        assertThat(limiter.retryAfter("k", 2, HOUR)).isZero();
        assertThat(limiter.acquire("k", 2, HOUR)).isZero();
        assertThat(limiter.acquire("k", 2, HOUR)).isGreaterThanOrEqualTo(1);

        limiter.release("k");
        limiter.release("k");
        // At 0 the key is gone: with max 1 a fresh key is allowed, and a further release changes nothing.
        assertThat(limiter.retryAfter("k", 1, HOUR)).isZero();
        limiter.release("k");
        limiter.release("never-seen");
        assertThat(limiter.acquire("k", 1, HOUR)).isZero();
        assertThat(limiter.acquire("k", 1, HOUR)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · release of the only attempt frees its slot when the table is full")
    void releaseFreesASlotWhenFull() {
        limiter.acquire("holder", 5, HOUR);
        fillWithLiveKeys("flood-", AttemptLimiter.MAX_KEYS - 1);
        assertThat(limiter.acquire("newcomer", 5, HOUR)).isGreaterThanOrEqualTo(1);

        limiter.release("holder");
        assertThat(limiter.acquire("newcomer", 5, HOUR)).isZero();
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

    // --- Tiers (audit S-12) ---------------------------------------------------------------------------------------

    private void fillBestEffort(String prefix, int count) {
        for (int i = 0; i < count; i++) {
            limiter.acquire(prefix + i, 5, HOUR, AttemptLimiter.Tier.BEST_EFFORT);
        }
    }

    @Test
    @DisplayName("NFR-11 · audit S-12 · MAX_KEYS is 200000")
    void maxKeysValue() {
        assertThat(AttemptLimiter.MAX_KEYS).isEqualTo(200_000);
    }

    @Test
    @DisplayName("NFR-11 · audit S-12 · BEST_EFFORT: when full, a new key is not stored and acquire returns 0 every time")
    void bestEffortNewKeyIsNotStoredWhenFull() {
        fillBestEffort("flood-", AttemptLimiter.MAX_KEYS);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.acquire("newcomer", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isZero();
        }
        assertThat(limiter.retryAfter("newcomer", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · audit S-12 · BEST_EFFORT: when full, existing keys keep counting and stay locked")
    void bestEffortExistingKeysKeepCounting() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("known", 3, HOUR, AttemptLimiter.Tier.BEST_EFFORT);
        }
        fillBestEffort("flood-", AttemptLimiter.MAX_KEYS - 1);

        assertThat(limiter.acquire("known", 3, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isGreaterThanOrEqualTo(1);
        limiter.release("known", AttemptLimiter.Tier.BEST_EFFORT);
        assertThat(limiter.acquire("known", 3, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · audit S-12 · BEST_EFFORT: when full, expired windows are dropped so a new key is stored again")
    void bestEffortExpiredWindowsMakeRoom() {
        for (int i = 0; i < AttemptLimiter.MAX_KEYS; i++) {
            limiter.acquire("old-" + i, 5, Duration.ofMinutes(15), AttemptLimiter.Tier.BEST_EFFORT);
        }
        clock.advance(Duration.ofMinutes(15));

        assertThat(limiter.acquire("newcomer", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isZero();
        assertThat(limiter.retryAfter("newcomer", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · audit S-12 · tier isolation: a full BEST_EFFORT table does not block STRICT keys")
    void bestEffortFloodDoesNotBlockStrictKeys() {
        fillBestEffort("flood-", AttemptLimiter.MAX_KEYS);

        assertThat(limiter.acquire("sign-in:10.0.0.1", 50, HOUR)).isZero();
        assertThat(limiter.acquire("sign-up:10.0.0.1", 5, HOUR)).isZero();
    }

    @Test
    @DisplayName("NFR-11 · audit S-12 · tier isolation: the same key name in the two tiers is counted separately; the default overloads are STRICT")
    void tiersAreSeparateTables() {
        limiter.acquire("k", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT);
        assertThat(limiter.retryAfter("k", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isGreaterThanOrEqualTo(1);
        assertThat(limiter.retryAfter("k", 1, HOUR)).isZero();
        assertThat(limiter.acquire("k", 1, HOUR)).isZero();
        assertThat(limiter.acquire("k", 1, HOUR, AttemptLimiter.Tier.STRICT)).isGreaterThanOrEqualTo(1);
        limiter.release("k");
        assertThat(limiter.retryAfter("k", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11 · audit S-12 · a full STRICT table does not stop BEST_EFFORT keys, and clear empties both tables")
    void strictFloodDoesNotAffectBestEffortAndClearEmptiesBoth() {
        fillWithLiveKeys("flood-", AttemptLimiter.MAX_KEYS);
        limiter.acquire("k", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT);
        assertThat(limiter.retryAfter("k", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isGreaterThanOrEqualTo(1);

        limiter.clear();
        assertThat(limiter.retryAfter("k", 1, HOUR, AttemptLimiter.Tier.BEST_EFFORT)).isZero();
        assertThat(limiter.acquire("newcomer", 1, HOUR)).isZero();
    }
}
