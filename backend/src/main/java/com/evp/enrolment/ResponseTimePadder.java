package com.evp.enrolment;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * SEC-08: makes every enrolment check take at least a fixed minimum time, so an
 * attacker cannot tell "found" from "not found" by how quickly the server answers.
 *
 * The minimum should comfortably exceed the slowest normal lookup. If a lookup ever
 * runs longer than the minimum, the response is not delayed further, so set the
 * minimum with headroom and monitor for overruns.
 */
public final class ResponseTimePadder {

    /** Sleeps for the given nanoseconds. Swappable in tests. */
    @FunctionalInterface
    public interface Sleeper {
        void sleepNanos(long nanos) throws InterruptedException;
    }

    private final Duration minimum;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;

    public ResponseTimePadder(Duration minimum) {
        this(minimum, System::nanoTime, nanos -> Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000)));
    }

    public ResponseTimePadder(Duration minimum, LongSupplier nanoClock, Sleeper sleeper) {
        if (minimum == null || minimum.isNegative() || minimum.isZero()) {
            throw new IllegalArgumentException("minimum must be positive");
        }
        this.minimum = minimum;
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
    }

    /** Call at the start of handling a request. */
    public long start() {
        return nanoClock.getAsLong();
    }

    /**
     * Call just before returning. Sleeps until at least {@code minimum} has passed since
     * {@code startNanos}.
     *
     * @return true if the work overran the minimum (worth logging, as timing may leak)
     */
    public boolean padFrom(long startNanos) {
        long remaining = minimum.toNanos() - (nanoClock.getAsLong() - startNanos);
        if (remaining <= 0) {
            return true;
        }
        try {
            sleeper.sleepNanos(remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    public Duration getMinimum() {
        return minimum;
    }
}
