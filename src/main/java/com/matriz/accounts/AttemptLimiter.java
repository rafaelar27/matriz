package com.matriz.accounts;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Counts attempts per key in fixed windows, in memory (NFR-11, SP-08; decided 2026-11-18, no new dependency).
 *
 * <p>The counters start again when the application restarts, and they are only correct with one instance. Keys hold
 * an IP address and, for sign-in, a normalized email address; they are never logged.
 *
 * <p>The number of keys is bounded, in two tables with different rules when they are full (security audit S-01, S-12):
 * <ul>
 *   <li>{@link Tier#STRICT} keys (one per client address and purpose): expired windows are dropped first and, if the
 *   table is still full, a new key is refused (429) until a window ends. A live counter is never forgotten, so a flood
 *   of addresses cannot lift an existing lockout; it needs {@link #MAX_KEYS} distinct addresses to block new ones.</li>
 *   <li>{@link Tier#BEST_EFFORT} keys (address and email, up to about 50 per address): when the table is full a new key
 *   is not stored and not enforced, so the per-address limit still applies but a flood cannot block everyone.</li>
 * </ul>
 */
@Component
public class AttemptLimiter {

    /** Which table a key lives in, and what happens to a new key when that table is full. */
    public enum Tier { STRICT, BEST_EFFORT }

    static final int MAX_KEYS = 200_000;

    private record Window(long startMillis, int count, long lengthMillis) {
    }

    private final Clock clock;
    private final Map<String, Window> strict = new HashMap<>();
    private final Map<String, Window> bestEffort = new HashMap<>();

    public AttemptLimiter() {
        this(Clock.systemUTC());
    }

    AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    private Map<String, Window> table(Tier tier) {
        return tier == Tier.STRICT ? strict : bestEffort;
    }

    private static boolean expired(Window w, long now) {
        return now - w.startMillis() >= w.lengthMillis();
    }

    /**
     * Counts one attempt for {@code key} and says whether it may go on.
     *
     * @return 0 if the attempt is within the limit, otherwise the seconds until the window ends
     */
    public long acquire(String key, int max, Duration window) {
        return acquire(key, max, window, Tier.STRICT);
    }

    public synchronized long acquire(String key, int max, Duration window, Tier tier) {
        long retry = retryAfter(key, max, window, tier);
        if (retry > 0) {
            return retry;
        }
        Map<String, Window> map = table(tier);
        long now = clock.millis();
        Window w = map.get(key);
        if (w == null && map.size() >= MAX_KEYS) {
            map.values().removeIf(x -> expired(x, now));
            if (map.size() >= MAX_KEYS) {
                if (tier == Tier.BEST_EFFORT) {
                    return 0; // not stored, not enforced
                }
                // Full of live windows: refuse new keys until the earliest one ends.
                return map.values().stream().mapToLong(x -> x.startMillis() + x.lengthMillis() - now).min().stream()
                        .map(ms -> Math.max(1, (ms + 999) / 1000)).findFirst().orElse(1);
            }
        }
        add(map, key, window, now);
        return 0;
    }

    /** Whether more attempts are allowed for {@code key}, without counting one. 0 if allowed, else seconds to wait. */
    public long retryAfter(String key, int max, Duration window) {
        return retryAfter(key, max, window, Tier.STRICT);
    }

    public synchronized long retryAfter(String key, int max, Duration window, Tier tier) {
        Window w = table(tier).get(key);
        long now = clock.millis();
        if (w == null || expired(w, now) || w.count() < max) {
            return 0;
        }
        return Math.max(1, (w.startMillis() + window.toMillis() - now + 999) / 1000);
    }

    /** Counts one (failed) attempt for {@code key}. Ignored when the table is full of live windows. */
    public synchronized void record(String key, Duration window) {
        Map<String, Window> map = strict;
        long now = clock.millis();
        if (!map.containsKey(key) && map.size() >= MAX_KEYS) {
            map.values().removeIf(x -> expired(x, now));
            if (map.size() >= MAX_KEYS) {
                return;
            }
        }
        add(map, key, window, now);
    }

    /** Gives back one attempt counted earlier for {@code key} (for example a sign-in that turned out to succeed). */
    public void release(String key) {
        release(key, Tier.STRICT);
    }

    public synchronized void release(String key, Tier tier) {
        Map<String, Window> map = table(tier);
        Window w = map.get(key);
        if (w == null) {
            return;
        }
        if (w.count() <= 1) {
            map.remove(key);
        } else {
            map.put(key, new Window(w.startMillis(), w.count() - 1, w.lengthMillis()));
        }
    }

    /** Forgets every counter. Used by tests; the application never calls it. */
    public synchronized void clear() {
        strict.clear();
        bestEffort.clear();
    }

    private static void add(Map<String, Window> map, String key, Duration window, long now) {
        Window w = map.get(key);
        if (w == null || expired(w, now)) {
            map.put(key, new Window(now, 1, window.toMillis()));
        } else {
            map.put(key, new Window(w.startMillis(), w.count() + 1, w.lengthMillis()));
        }
    }
}
