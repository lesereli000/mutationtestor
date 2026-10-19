package mutationtester;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * When a run should stop starting new mutants: the time_limit_minutes
 * deadline (counted from when the tool started) or a cancel (GitHub Actions
 * cancelling the job, Ctrl+C). Shared by the mutant workers and the shutdown
 * hook, so everything here is thread-safe.
 */
final class RunControl {
    enum StopReason { TIME_LIMIT, CANCELLED }

    private final LongSupplier clockMillis;
    private final Double timeLimitMinutes;
    private final Long deadlineMillis;
    private final AtomicReference<StopReason> stopReason = new AtomicReference<>();

    /** Counted down once the report and summary are written, so a cancel can wait for them. */
    final CountDownLatch finished = new CountDownLatch(1);

    RunControl(Double timeLimitMinutes) {
        this(timeLimitMinutes, RunControl::nowMillis, nowMillis());
    }

    /** @param startMillis when the run started, on the same clock; the limit counts from here */
    RunControl(Double timeLimitMinutes, LongSupplier clockMillis, long startMillis) {
        this.clockMillis = clockMillis;
        this.timeLimitMinutes = timeLimitMinutes;
        this.deadlineMillis = timeLimitMinutes == null
                ? null
                : startMillis + Math.round(timeLimitMinutes * 60_000);
    }

    static long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }

    static RunControl unlimited() {
        return new RunControl(null);
    }

    /** True once no new mutant should be started. */
    boolean shouldStop() {
        if (stopReason.get() != null) {
            return true;
        }
        if (deadlineMillis != null && clockMillis.getAsLong() >= deadlineMillis) {
            stopReason.compareAndSet(null, StopReason.TIME_LIMIT);
            return true;
        }
        return false;
    }

    /** A cancel wins over the time limit: results still in flight are discarded. */
    void cancel() {
        stopReason.set(StopReason.CANCELLED);
    }

    boolean isCancelled() {
        return stopReason.get() == StopReason.CANCELLED;
    }

    StopReason stopReason() {
        return stopReason.get();
    }

    /** "stopped after 140 of 212 mutants (time limit of 45 min reached)" */
    String describeStop(int tested, int total) {
        String why = stopReason.get() == StopReason.TIME_LIMIT
                ? "time limit of " + formatMinutes(timeLimitMinutes) + " min reached"
                : "the run was cancelled";
        return "stopped after " + tested + " of " + total + " mutants (" + why + ")";
    }

    private static String formatMinutes(Double minutes) {
        if (minutes == null) {
            return "?";
        }
        return minutes == Math.rint(minutes) ? String.valueOf(minutes.longValue()) : String.valueOf(minutes);
    }
}
