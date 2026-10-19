package hotshop.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Process-owned account admission registry for description generation. */
public final class DescriptionQuota {
    private static final long MINUTE_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final long HOUR_NANOS = TimeUnit.HOURS.toNanos(1);
    private static final long SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int MINUTE_LIMIT = 5;
    private static final int HOUR_LIMIT = 30;

    private final LongSupplier clock;
    private final Map<UUID, AccountState> accounts = new HashMap<>();

    /** Creates an empty server registry using elapsed process time. */
    public DescriptionQuota() {
        this(System::nanoTime);
    }

    DescriptionQuota(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /** Reserves one active slot and charges its attempt atomically, without waiting. */
    synchronized Admission reserve(UUID accountId) {
        Objects.requireNonNull(accountId);
        long now = clock.getAsLong();
        prune(now);
        AccountState state = accounts.get(accountId);
        if (state != null) {
            if (state.active != null) {
                return new Admission(null, Rejection.ACTIVE_REQUEST, 0);
            }
            long delay = retryDelay(state, now);
            if (delay > 0) {
                long seconds = 1 + (delay - 1) / SECOND_NANOS;
                return new Admission(null, Rejection.RATE_LIMIT, seconds);
            }
        } else {
            state = new AccountState();
            accounts.put(accountId, state);
        }
        Reservation reservation = new Reservation(accountId, UUID.randomUUID());
        state.active = reservation;
        state.attempts.add(new Attempt(reservation.requestId(), now));
        return new Admission(reservation, null, 0);
    }

    /** Releases only the matching active request, retaining dispatched attempt history. */
    synchronized boolean release(Reservation reservation) {
        return finish(reservation, false);
    }

    /** Removes the matching still-active reservation when dispatch could not start locally. */
    synchronized boolean rollback(Reservation reservation) {
        return finish(reservation, true);
    }

    /** Observes retained registry state without triggering expiry or other mutations. */
    synchronized int retainedAccountCount() {
        return accounts.size();
    }

    private boolean finish(Reservation reservation, boolean rollback) {
        Objects.requireNonNull(reservation);
        long now = clock.getAsLong();
        prune(now);
        AccountState state = accounts.get(reservation.accountId());
        if (state == null || !reservation.equals(state.active)) {
            return false;
        }
        if (rollback) {
            state.attempts.removeIf(attempt -> attempt.requestId().equals(reservation.requestId()));
        }
        state.active = null;
        if (state.attempts.isEmpty()) {
            accounts.remove(reservation.accountId());
        }
        return true;
    }

    private void prune(long now) {
        var iterator = accounts.values().iterator();
        while (iterator.hasNext()) {
            AccountState state = iterator.next();
            // Subtract elapsed times instead of comparing absolute nanoTime values: origins may be negative or wrap.
            state.attempts.removeIf(attempt -> now - attempt.startedAt() >= HOUR_NANOS);
            if (state.active == null && state.attempts.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private long retryDelay(AccountState state, long now) {
        long delay = 0;
        if (state.attempts.size() >= HOUR_LIMIT) {
            delay = HOUR_NANOS - (now - state.attempts.getFirst().startedAt());
        }
        int minuteCount = 0;
        long minuteDelay = MINUTE_NANOS;
        for (Attempt attempt : state.attempts) {
            long elapsed = now - attempt.startedAt();
            if (elapsed < MINUTE_NANOS) {
                minuteCount++;
                minuteDelay = Math.min(minuteDelay, MINUTE_NANOS - elapsed);
            }
        }
        if (minuteCount >= MINUTE_LIMIT) {
            delay = Math.max(delay, minuteDelay);
        }
        return delay;
    }

    private static final class AccountState {
        private final List<Attempt> attempts = new ArrayList<>();
        private Reservation active;
    }

    private record Attempt(UUID requestId, long startedAt) {
    }

    enum Rejection {
        ACTIVE_REQUEST, RATE_LIMIT
    }

    record Reservation(UUID accountId, UUID requestId) {
    }

    record Admission(Reservation reservation, Rejection rejection, long retryAfterSeconds) {
        boolean admitted() {
            return reservation != null;
        }
    }
}
