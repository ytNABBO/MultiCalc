package multicalc.server;

import java.util.concurrent.TimeUnit;

final class RequestRateLimiter {
    private static final String RATE_SETTING = "MULTICALC_REQUESTS_PER_SECOND";
    private static final int DEFAULT_RATE = 100;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final int requestsPerSecond;
    private final long capacity;
    private long credits;
    private long lastRefill = System.nanoTime();

    private RequestRateLimiter(int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
        // Measure credits in billionths of a request to keep refill arithmetic integral.
        capacity = NANOS_PER_SECOND * requestsPerSecond;
        credits = capacity;
    }

    static RequestRateLimiter fromEnvironment() {
        String setting = System.getenv(RATE_SETTING);
        if (setting == null) {
            return new RequestRateLimiter(DEFAULT_RATE);
        }

        int rate;
        try {
            rate = Integer.parseInt(setting);
        } catch (NumberFormatException invalidRate) {
            rate = 0;
        }
        if (rate <= 0) {
            throw new IllegalArgumentException(RATE_SETTING + " must be a positive 32-bit integer.");
        }
        return new RequestRateLimiter(rate);
    }

    synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        // One second of elapsed time is enough to refill an empty bucket.
        long elapsed = Math.min(now - lastRefill, NANOS_PER_SECOND);
        lastRefill = now;
        credits = Math.min(capacity, credits + elapsed * requestsPerSecond);
        if (credits < NANOS_PER_SECOND) {
            return false;
        }
        credits -= NANOS_PER_SECOND;
        return true;
    }
}
