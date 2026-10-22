package hotshop.llm;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Manually advanced domain time, independent from wall clock and real sleeps. */
final class FakeMonotonicClock implements LongSupplier {
    private long nanoseconds;

    @Override
    public synchronized long getAsLong() {
        return nanoseconds;
    }

    synchronized void advance(Duration elapsed) {
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Monotonic time cannot move backwards");
        }
        nanoseconds = Math.addExact(nanoseconds, elapsed.toNanos());
    }
}
