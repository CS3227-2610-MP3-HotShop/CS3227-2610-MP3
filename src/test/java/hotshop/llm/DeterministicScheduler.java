package hotshop.llm;

import java.time.Duration;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Explicitly driven deadline queue; callbacks never run merely because real time passes. */
final class DeterministicScheduler {
    private final LongSupplier clock;
    private final PriorityQueue<Task> tasks = new PriorityQueue<>(
            Comparator.comparingLong(Task::deadline).thenComparingLong(Task::sequence));
    private long sequence;

    DeterministicScheduler(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized Handle schedule(Runnable callback, Duration delay) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        Handle handle = new Handle();
        tasks.add(new Task(Math.addExact(clock.getAsLong(), delay.toNanos()), sequence++, callback, handle));
        return handle;
    }

    void runDue() {
        while (true) {
            Task task;
            synchronized (this) {
                if (tasks.isEmpty() || tasks.peek().deadline() > clock.getAsLong()) {
                    return;
                }
                task = tasks.remove();
            }
            if (!task.handle().cancelled.get()) {
                task.callback().run();
            }
        }
    }

    private record Task(long deadline, long sequence, Runnable callback, Handle handle) {
    }

    static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        boolean cancel() {
            return cancelled.compareAndSet(false, true);
        }
    }
}
