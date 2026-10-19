package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DescriptionQuotaTest {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final AtomicLong clock = new AtomicLong();
    private final DescriptionQuota quota = new DescriptionQuota(clock::get);

    @Test
    void reserve_fifthAndSixthMinuteRequests_admitsFiveOnly() {
        dispatchAndComplete(4);
        var fifth = quota.reserve(ACCOUNT);
        assertAdmitted(fifth);
        assertTrue(quota.release(fifth.reservation()));
        assertRateLimited(quota.reserve(ACCOUNT), 60);
    }

    @Test
    void reserve_thirtiethAndThirtyFirstHourRequests_admitsThirtyOnly() {
        fillHour();
        clock.set(6 * MINUTE);
        assertRateLimited(quota.reserve(ACCOUNT), 54 * 60);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void reserve_minuteExpiryBoundary_expiresAtExactWindow(long offset) {
        dispatchAndComplete(5);
        clock.set(MINUTE + offset);
        var result = quota.reserve(ACCOUNT);
        if (offset < 0) {
            assertRateLimited(result, 1);
        } else {
            assertAdmitted(result);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void reserve_hourExpiryBoundary_expiresAtExactWindow(long offset) {
        fillHour();
        clock.set(HOUR + offset);
        var result = quota.reserve(ACCOUNT);
        if (offset < 0) {
            assertRateLimited(result, 1);
        } else {
            assertAdmitted(result);
        }
    }

    @Test
    void reserve_anotherAccount_hasIndependentActiveSlotAndWindows() {
        dispatchAndComplete(5);
        var other = quota.reserve(OTHER_ACCOUNT);
        assertAdmitted(other);
        assertRateLimited(quota.reserve(ACCOUNT), 60);
        assertActive(quota.reserve(OTHER_ACCOUNT));
    }

    @Test
    void reserve_sharedRegistryCallers_cannotBypassActiveSlot() {
        DescriptionQuota firstCaller = quota;
        DescriptionQuota secondCaller = quota;
        assertAdmitted(firstCaller.reserve(ACCOUNT));
        assertActive(secondCaller.reserve(ACCOUNT));
    }

    @Test
    void reserve_twoRacingCallers_admitsExactlyOne() throws Exception {
        var admissions = race(() -> quota.reserve(ACCOUNT), () -> quota.reserve(ACCOUNT));
        assertEquals(1, (admissions.first().admitted() ? 1 : 0) + (admissions.second().admitted() ? 1 : 0));
        assertActive(admissions.first().admitted() ? admissions.second() : admissions.first());
    }

    @Test
    void release_simultaneousAdmission_preservesSingleActiveOwner() throws Exception {
        var old = quota.reserve(ACCOUNT).reservation();
        var results = race(() -> quota.release(old), () -> quota.reserve(ACCOUNT));
        assertTrue(results.first());
        DescriptionQuota.Reservation current;
        if (results.second().admitted()) {
            current = results.second().reservation();
        } else {
            assertActive(results.second());
            var afterRelease = quota.reserve(ACCOUNT);
            assertAdmitted(afterRelease);
            current = afterRelease.reservation();
        }
        assertFalse(quota.release(old));
        assertActive(quota.reserve(ACCOUNT));
        assertTrue(quota.release(current));
    }

    @Test
    void release_dispatchedFailures_retainsMinuteCharge() {
        dispatchAndComplete(5);
        assertRateLimited(quota.reserve(ACCOUNT), 60);
    }

    @Test
    void release_dispatchedFailures_retainsHourCharge() {
        fillHour();
        clock.set(6 * MINUTE);
        assertRateLimited(quota.reserve(ACCOUNT), 54 * 60);
    }

    @Test
    void rollback_beforeDispatch_removesTimestampAndActiveSlotExactlyOnce() {
        dispatchAndComplete(4);
        var reservation = quota.reserve(ACCOUNT).reservation();
        assertTrue(quota.rollback(reservation));
        assertFalse(quota.rollback(reservation));
        assertFalse(quota.release(reservation));
        var replacement = quota.reserve(ACCOUNT);
        assertAdmitted(replacement);
        assertTrue(quota.release(replacement.reservation()));
        assertRateLimited(quota.reserve(ACCOUNT), 60);
    }

    @Test
    void release_staleOrForeignReservation_cannotReleaseNewOwner() {
        var old = quota.reserve(ACCOUNT).reservation();
        assertTrue(quota.release(old));
        var current = quota.reserve(ACCOUNT).reservation();
        assertFalse(quota.release(old));
        assertFalse(quota.release(new DescriptionQuota.Reservation(OTHER_ACCOUNT, current.requestId())));
        assertFalse(quota.release(new DescriptionQuota.Reservation(ACCOUNT, UUID.randomUUID())));
        assertActive(quota.reserve(ACCOUNT));
        assertTrue(quota.release(current));
        assertFalse(quota.release(current));
    }

    @Test
    void rollback_completedOldReservation_preservesChargedHistoryAndNewOwner() {
        var old = quota.reserve(ACCOUNT).reservation();
        assertTrue(quota.release(old));
        assertFalse(quota.rollback(old));
        dispatchAndComplete(3);
        var current = quota.reserve(ACCOUNT).reservation();
        assertFalse(quota.rollback(old));
        assertActive(quota.reserve(ACCOUNT));
        assertTrue(quota.release(current));
        assertRateLimited(quota.reserve(ACCOUNT), 60);
    }

    @Test
    void rollback_foreignReservation_preservesActiveOwnerAndCharge() {
        dispatchAndComplete(4);
        var current = quota.reserve(ACCOUNT).reservation();
        assertFalse(quota.rollback(new DescriptionQuota.Reservation(OTHER_ACCOUNT, current.requestId())));
        assertFalse(quota.rollback(new DescriptionQuota.Reservation(ACCOUNT, UUID.randomUUID())));
        assertActive(quota.reserve(ACCOUNT));
        assertTrue(quota.release(current));
        assertRateLimited(quota.reserve(ACCOUNT), 60);
    }

    @Test
    void reserve_bothFullWindows_returnsLargerMinuteDelayRoundedUp() {
        for (int batch = 0; batch < 5; batch++) {
            clock.set(batch * MINUTE);
            dispatchAndComplete(5);
        }
        clock.set(HOUR - 10 * SECOND);
        dispatchAndComplete(5);
        clock.incrementAndGet();
        assertRateLimited(quota.reserve(ACCOUNT), 60);
    }

    @Test
    void reserve_bothFullWindows_returnsLargerHourDelayRoundedUp() {
        fillHour();
        clock.incrementAndGet();
        assertRateLimited(quota.reserve(ACCOUNT), 55 * 60);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void reserve_staggeredMinuteAttempts_reportsEarliestCapacityWithCeiling(long offset) {
        for (int request = 0; request < 5; request++) {
            clock.set(request * SECOND);
            dispatchAndComplete(1);
        }
        clock.set(4 * SECOND + offset);
        var rejection = quota.reserve(ACCOUNT);
        assertRateLimited(rejection, 56);
        clock.set(MINUTE - 1);
        assertRateLimited(quota.reserve(ACCOUNT), 1);
        clock.set(MINUTE);
        assertAdmitted(quota.reserve(ACCOUNT));
    }

    @Test
    void reserve_expiredInactiveOtherAccount_removesRegistryEntry() {
        dispatchAndComplete(1);
        assertEquals(1, quota.retainedAccountCount());
        clock.set(HOUR);
        assertAdmitted(quota.reserve(OTHER_ACCOUNT));
        assertEquals(1, quota.retainedAccountCount());
    }

    @Test
    void release_expiredInactiveOtherAccount_removesRegistryEntry() {
        dispatchAndComplete(1);
        var other = quota.reserve(OTHER_ACCOUNT).reservation();
        clock.set(HOUR);
        assertTrue(quota.release(other));
        assertEquals(0, quota.retainedAccountCount());
    }

    @Test
    void reserve_expiredActiveEntry_keepsActiveSlotUntilRelease() {
        var active = quota.reserve(ACCOUNT).reservation();
        clock.set(HOUR + 1);
        assertAdmitted(quota.reserve(OTHER_ACCOUNT));
        assertEquals(2, quota.retainedAccountCount());
        assertActive(quota.reserve(ACCOUNT));
        assertTrue(quota.release(active));
        assertAdmitted(quota.reserve(ACCOUNT));
    }

    @Test
    void reserve_newRegistry_resetsProcessQuota() {
        dispatchAndComplete(5);
        assertRateLimited(quota.reserve(ACCOUNT), 60);
        assertAdmitted(new DescriptionQuota(clock::get).reserve(ACCOUNT));
    }

    @ParameterizedTest
    @ValueSource(longs = {-9000000000000L, 9223372036854775797L})
    void reserve_arbitraryNanoTimeOrigin_enforcesElapsedMinuteAcrossOverflow(long origin) {
        clock.set(origin);
        dispatchAndComplete(5);
        clock.set(origin + MINUTE - 1);
        assertRateLimited(quota.reserve(ACCOUNT), 1);
        clock.set(origin + MINUTE);
        assertAdmitted(quota.reserve(ACCOUNT));
    }

    private void fillHour() {
        for (int batch = 0; batch < 6; batch++) {
            clock.set(batch * MINUTE);
            dispatchAndComplete(5);
        }
    }

    @Test
    void markDispatched_delayedStart_anchorsMinuteWindowToActualDispatch() {
        var delayed = quota.reserve(ACCOUNT).reservation();
        clock.set(44 * SECOND);
        assertTrue(quota.markDispatched(delayed));
        assertTrue(quota.release(delayed));
        dispatchAndComplete(4);
        clock.set(MINUTE);
        assertRateLimited(quota.reserve(ACCOUNT), 44);
        clock.set(104 * SECOND);
        assertAdmitted(quota.reserve(ACCOUNT));
    }

    @Test
    void markDispatched_repeatedCallback_preservesFirstDispatchTime() {
        var delayed = quota.reserve(ACCOUNT).reservation();
        clock.set(44 * SECOND);
        assertTrue(quota.markDispatched(delayed));
        clock.set(50 * SECOND);
        quota.markDispatched(delayed);
        assertTrue(quota.release(delayed));
        dispatchAndComplete(4);
        clock.set(104 * SECOND);
        assertAdmitted(quota.reserve(ACCOUNT));
    }

    @Test
    void markDispatched_staleReservation_doesNotRetimestampNewRequest() {
        var stale = quota.reserve(ACCOUNT).reservation();
        assertTrue(quota.rollback(stale));
        var current = quota.reserve(ACCOUNT).reservation();
        clock.set(44 * SECOND);
        assertFalse(quota.markDispatched(stale));
        assertTrue(quota.markDispatched(current));
        assertTrue(quota.release(current));
        dispatchAndComplete(4);
        clock.set(MINUTE);
        assertRateLimited(quota.reserve(ACCOUNT), 44);
    }

    @Test
    void markDispatched_forgedRequestId_doesNotTakeReservationOwnership() {
        var current = quota.reserve(ACCOUNT).reservation();
        clock.set(44 * SECOND);
        assertFalse(quota.markDispatched(new DescriptionQuota.Reservation(ACCOUNT, UUID.randomUUID())));
        assertTrue(quota.markDispatched(current));
        assertActive(quota.reserve(ACCOUNT));
        assertTrue(quota.release(current));
    }

    @Test
    void markDispatched_releasedReservation_doesNotChangeRetainedTimestamp() {
        var old = quota.reserve(ACCOUNT).reservation();
        assertTrue(quota.markDispatched(old));
        assertTrue(quota.release(old));
        clock.set(44 * SECOND);
        assertFalse(quota.markDispatched(old));
        dispatchAndComplete(4);
        clock.set(MINUTE);
        assertAdmitted(quota.reserve(ACCOUNT));
    }

    @Test
    void rollback_markedReservation_preservesChargeAndRequiresMatchingRelease() {
        var reservation = quota.reserve(ACCOUNT).reservation();
        assertTrue(quota.markDispatched(reservation));
        assertFalse(quota.rollback(reservation));
        assertActive(quota.reserve(ACCOUNT));
        assertTrue(quota.release(reservation));
        dispatchAndComplete(4);
        assertRateLimited(quota.reserve(ACCOUNT), 60);
    }

    @Test
    void markDispatched_pendingBeyondHour_retainsOwnershipAndRecordsActualSend() {
        var pending = quota.reserve(ACCOUNT).reservation();
        clock.set(HOUR + 44 * SECOND);
        var other = quota.reserve(OTHER_ACCOUNT);
        assertAdmitted(other);
        assertTrue(quota.rollback(other.reservation()));
        assertTrue(quota.markDispatched(pending));
        assertTrue(quota.release(pending));
        dispatchAndComplete(4);
        clock.set(HOUR + MINUTE);
        assertRateLimited(quota.reserve(ACCOUNT), 44);
    }

    @ParameterizedTest
    @ValueSource(longs = {-9000000000000L, 9223372036854775797L})
    void markDispatched_arbitraryClockOrigin_preservesElapsedWindows(long origin) {
        clock.set(origin);
        var delayed = quota.reserve(ACCOUNT).reservation();
        clock.set(origin + 44 * SECOND);
        assertTrue(quota.markDispatched(delayed));
        assertTrue(quota.release(delayed));
        dispatchAndComplete(4);
        clock.set(origin + MINUTE);
        assertRateLimited(quota.reserve(ACCOUNT), 44);
        clock.set(origin + 104 * SECOND);
        assertAdmitted(quota.reserve(ACCOUNT));
    }

    private void dispatchAndComplete(int count) {
        for (int request = 0; request < count; request++) {
            var admission = quota.reserve(ACCOUNT);
            assertAdmitted(admission);
            assertTrue(quota.release(admission.reservation()));
        }
    }

    private static void assertAdmitted(DescriptionQuota.Admission admission) {
        assertTrue(admission.admitted());
        assertNotNull(admission.reservation());
        assertNull(admission.rejection());
        assertEquals(0, admission.retryAfterSeconds());
    }

    private static void assertActive(DescriptionQuota.Admission admission) {
        assertFalse(admission.admitted());
        assertNull(admission.reservation());
        assertEquals(DescriptionQuota.Rejection.ACTIVE_REQUEST, admission.rejection());
        assertEquals(0, admission.retryAfterSeconds());
    }

    private static void assertRateLimited(DescriptionQuota.Admission admission, long seconds) {
        assertFalse(admission.admitted());
        assertNull(admission.reservation());
        assertEquals(DescriptionQuota.Rejection.RATE_LIMIT, admission.rejection());
        assertEquals(seconds, admission.retryAfterSeconds());
    }

    private static <T, U> RaceResult<T, U> race(Callable<T> first, Callable<U> second) throws Exception {
        var barrier = new CyclicBarrier(3);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return first.call();
            });
            var secondResult = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return second.call();
            });
            barrier.await(5, TimeUnit.SECONDS);
            return new RaceResult<>(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
        }
    }

    private record RaceResult<T, U>(T first, U second) {
    }
}
