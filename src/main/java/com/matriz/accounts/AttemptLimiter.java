package com.matriz.accounts;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Counts attempts per key in fixed windows, in memory (NFR-11, SP-08; decided 2026-11-18, no new dependency).
 *
 * <p>The counters start again when the application restarts, and they are only correct with one instance. The
 * number of keys is bounded: when the limit is reached, expired windows are dropped first and, if that is not
 * enough, all counters are cleared (a flood of different keys can therefore reset counters, but it needs as many
 * distinct client addresses as keys). Keys hold an IP address and, for sign-in, a normalized email address; they
 * are never logged.
 */
@Component
public class AttemptLimiter {

    static final int MAX_KEYS = 20_000;

    private record Window(long startMillis, int count, long lengthMillis) {
    }

    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public AttemptLimiter() {
        this(Clock.systemUTC());
    }

    AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one attempt for {@code key} and says whether it may go on.
     *
     * @return 0 if the attempt is within the limit, otherwise the seconds until the window ends
     */
    public synchronized long acquire(String key, int max, Duration window) {
        long retry = retryAfter(key, max, window);
        if (retry > 0) {
            return retry;
        }
        add(key, window);
        return 0;
    }

    /** Whether more attempts are allowed for {@code key}, without counting one. 0 if allowed, else seconds to wait. */
    public synchronized long retryAfter(String key, int max, Duration window) {
        Window w = windows.get(key);
        long now = clock.millis();
        if (w == null || now - w.startMillis() >= window.toMillis() || w.count() < max) {
            return 0;
        }
        return Math.max(1, (w.startMillis() + window.toMillis() - now + 999) / 1000);
    }

    /** Counts one (failed) attempt for {@code key}. */
    public synchronized void record(String key, Duration window) {
        add(key, window);
    }

    /** Gives back one attempt counted earlier for {@code key} (for example a sign-in that turned out to succeed). */
    public synchronized void release(String key) {
        Window w = windows.get(key);
        if (w == null) {
            return;
        }
        if (w.count() <= 1) {
            windows.remove(key);
        } else {
            windows.put(key, new Window(w.startMillis(), w.count() - 1, w.lengthMillis()));
        }
    }

    /** Forgets every counter. Used by tests; the application never calls it. */
    public synchronized void clear() {
        windows.clear();
    }

    private void add(String key, Duration window) {
        long now = clock.millis();
        Window w = windows.get(key);
        if (w == null || now - w.startMillis() >= window.toMillis()) {
            if (w == null && windows.size() >= MAX_KEYS) {
                makeRoom(now);
            }
            windows.put(key, new Window(now, 1, window.toMillis()));
        } else {
            windows.put(key, new Window(w.startMillis(), w.count() + 1, w.lengthMillis()));
        }
    }

    private void makeRoom(long now) {
        windows.values().removeIf(w -> now - w.startMillis() >= w.lengthMillis());
        if (windows.size() >= MAX_KEYS) {
            windows.clear();
        }
    }
}
