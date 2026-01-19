package org.engine.pickerengine.service;

import java.util.concurrent.ThreadLocalRandom;

final class InstagramHttpBackoffPolicy {

    private InstagramHttpBackoffPolicy() {
    }

    static long computeDelayMs(
            String retryAfterHeader,
            int attempt,
            long baseBackoffMs,
            long maxBackoffMs) {
        long retryAfterMs = parseRetryAfterMs(retryAfterHeader);
        if (retryAfterMs > 0) {
            return retryAfterMs;
        }
        if (baseBackoffMs <= 0) {
            return 0;
        }
        int safeAttempt = Math.max(0, attempt);
        long cappedMax = Math.max(baseBackoffMs, maxBackoffMs);
        long base = (long) (baseBackoffMs * Math.pow(2, safeAttempt));
        long capped = Math.min(base, cappedMax);
        long jitterBound = Math.min(1000L, Math.max(1L, capped));
        long jitter = ThreadLocalRandom.current().nextLong(jitterBound);
        return capped + jitter;
    }

    static long parseRetryAfterMs(String retryAfterHeader) {
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(retryAfterHeader.trim()) * 1000;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
