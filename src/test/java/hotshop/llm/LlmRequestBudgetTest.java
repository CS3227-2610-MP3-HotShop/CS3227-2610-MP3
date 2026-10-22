package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class LlmRequestBudgetTest {
    @Test
    void reserve_minuteBoundary_allowsNextDispatchAtExpiry() {
        AtomicLong clock = new AtomicLong();
        LlmRequestBudget budget = new LlmRequestBudget(clock::get);
        for (int index = 0; index < LlmRequestBudget.PRODUCTION_MINUTE_LIMIT; index++) {
            commit(budget);
        }
        assertNull(budget.reserve());
        clock.addAndGet(TimeUnit.MINUTES.toNanos(1));
        assertNotNull(budget.reserve());
    }

    @Test
    void reserve_parallelRequests_neverExceedsProcessLimit() throws Exception {
        LlmRequestBudget budget = new LlmRequestBudget(() -> 0L);
        try (var workers = Executors.newFixedThreadPool(16)) {
            ArrayList<Future<LlmRequestBudget.Reservation>> reservations = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                reservations.add(workers.submit(budget::reserve));
            }
            int accepted = 0;
            for (Future<LlmRequestBudget.Reservation> reservation : reservations) {
                if (reservation.get() != null) {
                    accepted++;
                }
            }
            assertEquals(LlmRequestBudget.PRODUCTION_MINUTE_LIMIT, accepted);
        }
    }

    @Test
    void reserve_pendingSlotsAreAtomicAndRollbackRestoresCapacity() {
        LlmRequestBudget budget = new LlmRequestBudget(() -> 0L);
        var first = budget.reserve();
        for (int index = 1; index < LlmRequestBudget.PRODUCTION_MINUTE_LIMIT; index++) {
            assertNotNull(budget.reserve());
        }
        assertNull(budget.reserve());
        budget.rollback(first);
        assertNotNull(budget.reserve());
    }

    @Test
    void rollback_undispatchedReservationLeavesNoAttemptCharge() {
        LlmRequestBudget budget = new LlmRequestBudget(() -> 0L);
        var reservation = budget.reserve();
        assertNotNull(reservation);
        budget.rollback(reservation);
        assertEquals(0, budget.retainedAttemptCount());
        assertNotNull(budget.reserve());
    }

    private static void commit(LlmRequestBudget budget) {
        var reservation = budget.reserve();
        assertNotNull(reservation);
        assertTrue(budget.commit(reservation));
    }
}
