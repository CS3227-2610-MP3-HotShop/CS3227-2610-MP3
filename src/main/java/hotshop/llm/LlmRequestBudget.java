package hotshop.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Shared process-local application ceiling for buyer and seller AI dispatches. */
public final class LlmRequestBudget {
    static final int PRODUCTION_MINUTE_LIMIT = 90;
    private static final long MINUTE_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final LongSupplier clock;
    private final Map<UUID, Reservation> pending = new HashMap<>();
    private final ArrayList<Attempt> attempts = new ArrayList<>();

    /** Uses elapsed process time and starts with empty application-wide history. */
    public LlmRequestBudget() {
        this(System::nanoTime);
    }

    LlmRequestBudget(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    synchronized Reservation reserve() {
        long now = clock.getAsLong();
        prune(now);
        int recentMinute = 0;
        for (Attempt attempt : attempts) {
            if (now - attempt.dispatchedAt() < MINUTE_NANOS) {
                recentMinute++;
            }
        }
        if (recentMinute + pending.size() >= PRODUCTION_MINUTE_LIMIT) {
            return null;
        }
        Reservation reservation = new Reservation(UUID.randomUUID());
        pending.put(reservation.id(), reservation);
        return reservation;
    }

    synchronized boolean commit(Reservation reservation) {
        if (reservation == null || pending.remove(reservation.id()) != reservation) {
            return false;
        }
        attempts.add(new Attempt(reservation.id(), clock.getAsLong()));
        return true;
    }

    /** Refunds a reservation that has not reached the HTTP dispatch call. */
    synchronized void rollback(Reservation reservation) {
        if (reservation == null) {
            return;
        }
        pending.remove(reservation.id());
    }

    synchronized int retainedAttemptCount() {
        prune(clock.getAsLong());
        return attempts.size();
    }

    private void prune(long now) {
        attempts.removeIf(attempt -> now - attempt.dispatchedAt() >= MINUTE_NANOS);
    }

    record Reservation(UUID id) {
    }

    private record Attempt(UUID id, long dispatchedAt) {
    }
}
