package io.sharpen.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small in-memory "at most N per window" counter, keyed by any string (an address, an email, an API key).
 * One instance per use, so the limits are independent. Bounded: when the map grows past {@code maxKeys} the
 * expired windows are dropped, and if that is not enough the whole map is — a brief amnesty is better than
 * growing without limit. Not shared across instances; Sharpen runs as one.
 */
public final class WindowLimiter {

    private final int limit;
    private final long windowSeconds;
    private final int maxKeys;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();   // key → [windowStartEpochSec, count]

    public WindowLimiter(int limit, long windowSeconds, int maxKeys) {
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.maxKeys = maxKeys;
    }

    /** Counts one event for {@code key}; true while the key is within its limit for the current window. */
    public boolean allow(String key) {
        long now = System.currentTimeMillis() / 1000;
        long[] w = windows.compute(key, (k, v) -> {
            if (v == null || now - v[0] >= windowSeconds) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        if (windows.size() > maxKeys) {
            windows.entrySet().removeIf(e -> now - e.getValue()[0] >= windowSeconds);
            if (windows.size() > maxKeys) windows.clear();
        }
        return w[1] <= limit;
    }

    /** True if {@code key} has already reached its limit — without counting anything. */
    public boolean exhausted(String key) {
        long[] w = windows.get(key);
        long now = System.currentTimeMillis() / 1000;
        return w != null && now - w[0] < windowSeconds && w[1] >= limit;
    }

    public void reset(String key) { windows.remove(key); }
}
