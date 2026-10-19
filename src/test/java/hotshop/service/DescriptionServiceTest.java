package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.database.Database;
import hotshop.llm.HttpLlmClient;
import hotshop.llm.LlmClient;
import hotshop.llm.LlmConfiguration;
import hotshop.model.Condition;
import hotshop.repository.UserRepository;
import hotshop.service.DescriptionService.Failed;
import hotshop.service.DescriptionService.FailureKind;
import hotshop.service.DescriptionService.Generated;
import hotshop.service.DescriptionService.GenerationResult;
import hotshop.storage.ImageStorage;

class DescriptionServiceTest {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String DESCRIPTION = "Wooden chair in good condition.";
    private static final String PRIVATE_CANARY = "synthetic-private-token-endpoint-body-canary";
    private static final long MINUTE = TimeUnit.MINUTES.toNanos(1);

    @TempDir
    Path directory;

    @Test
    void generateDescription_loggedOut_returnsLoginRequiredWithoutPreparing() throws Exception {
        try (Harness harness = new Harness()) {
            assertFailed(harness.generate("Chair", Condition.GOOD), FailureKind.LOGIN_REQUIRED);
            assertEquals(0, harness.client.preparations.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_loggedOutWithInvalidInput_prioritizesAuthentication() throws Exception {
        try (Harness harness = new Harness()) {
            assertFailed(harness.generate(null, null), FailureKind.LOGIN_REQUIRED);
            assertEquals(0, harness.client.preparations.get());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void generateDescription_missingTitle_rejectsWithoutPreparingOrCharging(String title) throws Exception {
        try (Harness harness = loggedIn()) {
            assertFailed(harness.generate(title, Condition.GOOD), FailureKind.INVALID_INPUT);
            assertEquals(0, harness.client.preparations.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_nullCondition_rejectsWithoutPreparingOrCharging() throws Exception {
        try (Harness harness = loggedIn()) {
            assertFailed(harness.generate("Chair", null), FailureKind.INVALID_INPUT);
            assertEquals(0, harness.client.preparations.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "\ud801\udc00"})
    void generateDescription_titleOver120CodePoints_rejectsWithoutPreparingOrCharging(String point) throws Exception {
        try (Harness harness = loggedIn()) {
            assertFailed(harness.generate(point.repeat(121), Condition.NEW), FailureKind.INVALID_INPUT);
            assertEquals(0, harness.client.preparations.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 120})
    void generateDescription_unicodeTitleBoundaries_dispatchesNormalizedProductFacts(int count) throws Exception {
        try (Harness harness = loggedIn()) {
            String normalized = "\ud801\udc00".repeat(count);
            var result = harness.generate(" \t" + normalized + "\n ", Condition.LIKE_NEW);
            Preparation prepared = harness.preparation(result);
            assertEquals(normalized, prepared.title());
            assertEquals(Condition.LIKE_NEW, prepared.condition());
            PendingAttempt attempt = harness.dispatch(prepared, true);
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(result));
            assertEquals(1, harness.client.dispatches.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Condition.class)
    void generateDescription_eachTypedCondition_preservesCondition(Condition condition) throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("System design textbook", condition);
            Preparation prepared = harness.preparation(result);
            assertEquals("System design textbook", prepared.title());
            assertEquals(condition, prepared.condition());
            harness.dispatch(prepared, true).completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(result));
        }
    }

    @Test
    void generateDescription_preparationPending_hasNoQuotaAndLeavesWorkerResponsive() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.NEW);
            Preparation preparation = harness.preparation(result);
            assertEquals(0, harness.quota.retainedAccountCount());
            harness.logout();
            preparation.completion().complete(harness.client.prepared(new PendingAttempt(true)));
            assertFailed(result, FailureKind.SESSION_CHANGED);
            assertEquals(0, harness.client.dispatches.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_sameAccountReloginDuringPreparation_doesNotDispatch() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.NEW);
            Preparation preparation = harness.preparation(result);
            harness.logout();
            harness.login(ACCOUNT);
            preparation.completion().complete(harness.client.prepared(new PendingAttempt(true)));
            assertFailed(result, FailureKind.SESSION_CHANGED);
            assertEquals(0, harness.client.dispatches.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_otherAccountDuringPreparation_doesNotDispatchOrChargeEitherAccount() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.NEW);
            Preparation preparation = harness.preparation(result);
            harness.logout();
            harness.login(OTHER_ACCOUNT);
            preparation.completion().complete(harness.client.prepared(new PendingAttempt(true)));
            assertFailed(result, FailureKind.SESSION_CHANGED);
            assertEquals(0, harness.client.dispatches.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @ParameterizedTest
    @EnumSource(value = LlmClient.Failure.class, names = {"UNAVAILABLE", "CLOSED"})
    void generateDescription_unavailablePreparation_doesNotAdmitQuota(LlmClient.Failure failure) throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.NEW);
            harness.preparation(result).completion().completeExceptionally(new LlmClient.PreparationException(failure));
            assertFailed(result, FailureKind.valueOf(failure.name()));
            assertEquals(0, harness.client.dispatches.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_oversizePreparationFailure_doesNotAdmitQuota() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.NEW);
            harness.preparation(result).completion().completeExceptionally(
                    new IllegalArgumentException(PRIVATE_CANARY));
            assertFailed(result, FailureKind.INVALID_INPUT);
            assertEquals(0, harness.client.dispatches.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_realUnavailableConfiguration_returnsUnavailableWithoutQuota() throws Exception {
        try (Harness harness = loggedIn();
                HttpLlmClient transport = new HttpLlmClient(LlmConfiguration.load(directory))) {
            DescriptionService service = new DescriptionService(
                    harness.worker, harness.session, harness.quota, transport);
            assertFailed(service.generateDescription("Chair", Condition.GOOD), FailureKind.UNAVAILABLE);
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_realOversizeRequest_rejectsBeforeQuotaOrNetwork() throws Exception {
        Files.writeString(directory.resolve(".env"), "SOCLAAS_BASE_URL=https://127.0.0.1:1/synthetic\n"
                + "SOCLAAS_API_KEY=synthetic-service-key\nSOCLAAS_MODEL=" + "m".repeat(8192) + "\n");
        LlmConfiguration configuration = LlmConfiguration.load(directory);
        assertTrue(configuration.isAvailable());
        try (Harness harness = loggedIn(); HttpLlmClient transport = new HttpLlmClient(configuration)) {
            DescriptionService service = new DescriptionService(
                    harness.worker, harness.session, harness.quota, transport);
            assertFailed(service.generateDescription("Chair", Condition.GOOD), FailureKind.INVALID_INPUT);
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_synchronousPreparationException_returnsSafeFailureWithoutQuota() throws Exception {
        try (Harness harness = loggedIn()) {
            harness.client.throwDuringPreparation = true;
            assertFailed(harness.generate("Chair", Condition.GOOD), FailureKind.UNAVAILABLE);
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_nullPreparationFuture_returnsSafeFailureWithoutQuota() throws Exception {
        try (Harness harness = loggedIn()) {
            harness.client.nullPreparationFuture = true;
            assertFailed(harness.generate("Chair", Condition.GOOD), FailureKind.UNAVAILABLE);
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_nullAttemptFuture_returnsSafeFailureAndConservativelyCharges() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt malformed = new PendingAttempt(true);
            malformed.nullResultFuture = true;
            harness.preparation(result).completion().complete(harness.client.prepared(malformed));
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_logoutWhileInFlight_remainsResponsiveAndDiscardsResult() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            harness.logout();
            assertFalse(result.isDone());
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertFailed(result, FailureKind.SESSION_CHANGED);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_sameAccountReloginWhileInFlight_discardsOldResult() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            harness.logout();
            harness.login(ACCOUNT);
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertFailed(result, FailureKind.SESSION_CHANGED);
        }
    }

    @Test
    void generateDescription_otherAccountWhileInFlight_discardsOldResult() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            harness.logout();
            harness.login(OTHER_ACCOUNT);
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertFailed(result, FailureKind.SESSION_CHANGED);
            var independent = harness.quota.reserve(OTHER_ACCOUNT);
            assertTrue(independent.admitted());
            harness.quota.rollback(independent.reservation());
        }
    }

    @Test
    void generateDescription_sharedQuotaAcrossServices_blocksSecondActiveRequest() throws Exception {
        try (Harness first = loggedIn(); Harness second = new Harness(first.quota, first.clock)) {
            second.login(ACCOUNT);
            var pending = first.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = first.dispatch(first.preparation(pending), true);
            var denied = second.generate("Desk", Condition.NEW);
            second.dispatch(second.preparation(denied), true, false);
            assertFailed(denied, FailureKind.IN_FLIGHT);
            assertEquals(0, second.client.dispatches.get());
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(pending));
            var next = second.generate("Desk", Condition.NEW);
            second.dispatch(second.preparation(next), true).completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(next));
        }
    }

    @Test
    void generateDescription_differentAccountsSharingQuota_allowsIndependentRequests() throws Exception {
        try (Harness first = loggedIn(); Harness second = new Harness(first.quota, first.clock)) {
            second.login(OTHER_ACCOUNT);
            var left = first.generate("Chair", Condition.GOOD);
            var right = second.generate("Desk", Condition.NEW);
            var leftAttempt = first.dispatch(first.preparation(left), true);
            var rightAttempt = second.dispatch(second.preparation(right), true);
            leftAttempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            rightAttempt.completion.complete(new LlmClient.Generated("Desk in new condition."));
            assertEquals(new Generated(DESCRIPTION), await(left));
            assertEquals(new Generated("Desk in new condition."), await(right));
        }
    }

    @Test
    void generateDescription_fiveDispatchedFailures_returnsSafeLocalRetryDelayWithoutSixthDispatch() throws Exception {
        try (Harness harness = loggedIn()) {
            for (int count = 0; count < 5; count++) {
                var result = harness.generate("Chair", Condition.GOOD);
                harness.dispatch(harness.preparation(result), true).completion.complete(
                        new LlmClient.Failed(LlmClient.Failure.TIMEOUT));
                assertFailed(result, FailureKind.TIMEOUT);
            }
            harness.clock.set(TimeUnit.SECONDS.toNanos(1) + 1);
            var denied = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(denied), true, false);
            assertEquals(new Failed(FailureKind.RATE_LIMITED, 59), await(denied));
            assertEquals(5, harness.client.dispatches.get());
            harness.clock.set(MINUTE);
            var recovered = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(recovered), true).completion.complete(
                    new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(recovered));
        }
    }

    @ParameterizedTest
    @EnumSource(LlmClient.Failure.class)
    void generateDescription_transportFailure_mapsTypedFailureAndRetainsCharge(LlmClient.Failure failure)
            throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(result), true).completion.complete(new LlmClient.Failed(failure));
            assertFailed(result, FailureKind.valueOf(failure.name()));
            assertReleasedButCharged(harness.quota, ACCOUNT);
            assertEquals(1, harness.client.dispatches.get());
        }
    }

    @Test
    void generateDescription_delayedActualSend_enforcesMinuteFromSendInsteadOfAdmission() throws Exception {
        try (Harness harness = loggedIn()) {
            var first = harness.generate("Chair", Condition.GOOD);
            Preparation preparation = harness.preparation(first);
            PendingAttempt delayed = new PendingAttempt(false);
            CompletableFuture<Runnable> actualSend = new CompletableFuture<>();
            preparation.completion().complete(beforeSend -> {
                harness.client.dispatches.incrementAndGet();
                actualSend.complete(beforeSend);
                return delayed;
            });
            harness.flush();
            assertEquals(DescriptionQuota.Rejection.ACTIVE_REQUEST, harness.quota.reserve(ACCOUNT).rejection());
            harness.clock.set(TimeUnit.SECONDS.toNanos(44));
            await(actualSend).run();
            delayed.dispatched = true;
            delayed.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(first));
            for (int count = 0; count < 4; count++) {
                var next = harness.generate("Chair", Condition.GOOD);
                harness.dispatch(harness.preparation(next), true).completion.complete(
                        new LlmClient.Generated(DESCRIPTION));
                assertEquals(new Generated(DESCRIPTION), await(next));
            }
            harness.clock.set(MINUTE);
            var denied = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(denied), true, false);
            assertEquals(new Failed(FailureKind.RATE_LIMITED, 44), await(denied));
            assertEquals(5, harness.client.dispatches.get());
            harness.clock.set(TimeUnit.SECONDS.toNanos(104));
            var recovered = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(recovered), true).completion.complete(
                    new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(recovered));
        }
    }

    @Test
    void generateDescription_terminalBeforeDispatch_rollsBackReservationAndCharge() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(result), false).completion.complete(
                    new LlmClient.Failed(LlmClient.Failure.UNAVAILABLE));
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_dispatchThrows_returnsSafeFailureAndConservativelyCharges() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.preparation(result).completion().complete(beforeSend -> {
                throw new IllegalStateException(PRIVATE_CANARY);
            });
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_nullDispatchAttempt_returnsSafeFailureAndConservativelyCharges() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.preparation(result).completion().complete(beforeSend -> null);
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_nullPreparation_returnsSafeFailureWithoutCharge() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.preparation(result).completion().complete(null);
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_unexpectedPreparationException_returnsSafeFailureWithoutCharge() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.preparation(result).completion().completeExceptionally(new IllegalStateException(PRIVATE_CANARY));
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void generateDescription_unexpectedTransportException_returnsSafeFailureAndRetainsCharge() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(result), true).completion.completeExceptionally(
                    new IllegalStateException(PRIVATE_CANARY));
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_nullTransportResult_returnsSafeFailureAndRetainsCharge() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(result), true).completion.complete(null);
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_cancelledCaller_retainsActiveSlotUntilTransportTerminates() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            assertTrue(result.cancel(true));
            harness.flush();
            assertFalse(attempt.completion.isCancelled());
            var denied = harness.quota.reserve(ACCOUNT);
            assertFalse(denied.admitted());
            assertEquals(DescriptionQuota.Rejection.ACTIVE_REQUEST, denied.rejection());
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            harness.flush();
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_oldTerminalCannotReleaseNewRequest_preservesMatchingReservation() throws Exception {
        try (Harness harness = loggedIn()) {
            var first = harness.generate("Chair", Condition.GOOD);
            PendingAttempt old = harness.dispatch(harness.preparation(first), true);
            old.completion.complete(new LlmClient.Generated(DESCRIPTION));
            await(first);
            var second = harness.generate("Desk", Condition.NEW);
            PendingAttempt current = harness.dispatch(harness.preparation(second), true);
            assertFalse(old.completion.complete(new LlmClient.Generated("Late duplicate")));
            harness.flush();
            assertEquals(DescriptionQuota.Rejection.ACTIVE_REQUEST, harness.quota.reserve(ACCOUNT).rejection());
            current.completion.complete(new LlmClient.Generated(DESCRIPTION));
            await(second);
        }
    }

    @Test
    void close_pendingPreparation_cancelsPreparationWithoutQuotaOrDispatch() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            Preparation preparation = harness.preparation(result);
            harness.service.close();
            assertFailed(result, FailureKind.CLOSED);
            assertTrue(preparation.completion().isCancelled());
            assertEquals(0, harness.client.dispatches.get());
            assertEquals(0, harness.quota.retainedAccountCount());
            assertFalse(preparation.completion().complete(harness.client.prepared(new PendingAttempt(true))));
            harness.flush();
            assertEquals(0, harness.client.dispatches.get());
        }
    }

    @Test
    void close_inFlight_cancelsTransportAndRetainsDispatchedCharge() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            harness.service.close();
            assertFailed(result, FailureKind.CLOSED);
            assertTrue(attempt.completion.isCancelled());
            harness.flush();
            assertReleasedButCharged(harness.quota, ACCOUNT);
            assertFalse(attempt.completion.complete(new LlmClient.Generated(DESCRIPTION)));
        }
    }

    @Test
    void close_notDispatchedAttempt_refundsReservation() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), false);
            harness.service.close();
            assertFailed(result, FailureKind.CLOSED);
            assertTrue(attempt.completion.isCancelled());
            harness.flush();
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void close_repeatedClose_rejectsNewRequestsAndPreservesInjectedWorker() throws Exception {
        try (Harness harness = loggedIn()) {
            harness.service.close();
            harness.service.close();
            assertFailed(harness.generate("Chair", Condition.GOOD), FailureKind.CLOSED);
            assertEquals(0, harness.client.preparations.get());
            assertEquals(ACCOUNT, await(harness.worker.submit(harness.session::requireUserId)));
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void close_initialWorkerCaptureQueued_preventsPreparation() throws Exception {
        try (Harness harness = loggedIn(); WorkerGate gate = harness.holdWorker()) {
            var result = harness.generate("Chair", Condition.GOOD);
            harness.service.close();
            assertFailed(result, FailureKind.CLOSED);
            gate.close();
            harness.flush();
            assertEquals(0, harness.client.preparations.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void close_preparedAdmissionQueued_preventsDispatchAndQuotaAdmission() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            Preparation preparation = harness.preparation(result);
            try (WorkerGate gate = harness.holdWorker()) {
                preparation.completion().complete(harness.client.prepared(new PendingAttempt(true)));
                harness.service.close();
                assertFailed(result, FailureKind.CLOSED);
                gate.close();
            }
            harness.flush();
            assertEquals(0, harness.client.dispatches.get());
            assertEquals(0, harness.quota.retainedAccountCount());
        }
    }

    @Test
    void close_finalWorkerCompletionQueued_deliversClosedExactlyOnceAndReleasesQuota() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            List<GenerationResult> delivered = new java.util.concurrent.CopyOnWriteArrayList<>();
            result.thenAccept(delivered::add);
            try (WorkerGate gate = harness.holdWorker()) {
                attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
                assertFalse(result.isDone());
                harness.service.close();
                assertFailed(result, FailureKind.CLOSED);
                gate.close();
            }
            harness.flush();
            assertEquals(List.of(new Failed(FailureKind.CLOSED, 0)), delivered);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void close_finalWorkerRejects_deliversClosedAndReleasesQuota() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            harness.worker.close();
            harness.service.close();
            assertFailed(result, FailureKind.CLOSED);
            assertTrue(attempt.completion.isCancelled());
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_finalWorkerRejects_returnsSafeUnavailableAndReleasesQuota() throws Exception {
        try (Harness harness = loggedIn()) {
            var result = harness.generate("Chair", Condition.GOOD);
            PendingAttempt attempt = harness.dispatch(harness.preparation(result), true);
            harness.worker.close();
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertFailed(result, FailureKind.UNAVAILABLE);
            assertReleasedButCharged(harness.quota, ACCOUNT);
        }
    }

    @Test
    void generateDescription_successAndFailure_preserveTemporaryDatabaseAndFiles() throws Exception {
        try (Harness harness = new Harness()) {
            Database database = new Database(directory.resolve("marketplace.db"));
            database.migrate();
            ImageStorage images = new ImageStorage(directory.resolve("images"));
            AccountService accounts = new AccountService(database, new UserRepository(),
                    harness.worker, harness.session, images);
            await(accounts.register("alice", "Sample1!", "Alice"));
            await(accounts.login("alice", "Sample1!"));
            Files.writeString(directory.resolve("private-context.txt"), PRIVATE_CANARY, StandardCharsets.UTF_8);
            Map<Path, List<Byte>> before = snapshot(directory);
            var successful = harness.generate("Chair", Condition.GOOD);
            harness.dispatch(harness.preparation(successful), true).completion.complete(
                    new LlmClient.Generated(DESCRIPTION));
            assertEquals(new Generated(DESCRIPTION), await(successful));
            var failed = harness.generate("Desk", Condition.NEW);
            harness.dispatch(harness.preparation(failed), true).completion.complete(
                    new LlmClient.Failed(LlmClient.Failure.NETWORK));
            assertFailed(failed, FailureKind.NETWORK);
            assertEquals(before, snapshot(directory));
            var pending = harness.generate("Chair", Condition.GOOD);
            var attempt = harness.dispatch(harness.preparation(pending), true);
            await(accounts.logout());
            assertTrue(await(accounts.getCurrentUserId()).isEmpty());
            attempt.completion.complete(new LlmClient.Generated(DESCRIPTION));
            assertFailed(pending, FailureKind.SESSION_CHANGED);
        }
    }

    private static Map<Path, List<Byte>> snapshot(Path root) throws Exception {
        Map<Path, List<Byte>> result = new HashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                List<Byte> bytes = new ArrayList<>();
                for (byte value : Files.readAllBytes(path)) {
                    bytes.add(value);
                }
                result.put(root.relativize(path), bytes);
            }
        }
        return result;
    }

    private static Harness loggedIn() throws Exception {
        Harness harness = new Harness();
        harness.login(ACCOUNT);
        return harness;
    }

    private static void assertReleasedButCharged(DescriptionQuota quota, UUID account) {
        // One dispatched request plus these four probes fills the minute window.
        for (int count = 0; count < 4; count++) {
            var probe = quota.reserve(account);
            assertTrue(probe.admitted());
            assertTrue(quota.release(probe.reservation()));
        }
        assertEquals(DescriptionQuota.Rejection.RATE_LIMIT, quota.reserve(account).rejection());
    }

    private static void assertFailed(CompletableFuture<GenerationResult> result, FailureKind expected)
            throws Exception {
        GenerationResult outcome = await(result);
        assertEquals(new Failed(expected, 0), outcome);
        assertFalse(outcome.toString().contains(PRIVATE_CANARY));
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static final class Harness implements AutoCloseable {
        private final ServiceWorker worker = new ServiceWorker();
        private final AuthenticatedSession session = new AuthenticatedSession();
        private final AtomicLong clock;
        private final DescriptionQuota quota;
        private final ControlledClient client = new ControlledClient();
        private final DescriptionService service;

        Harness() {
            this(null, new AtomicLong());
        }

        Harness(DescriptionQuota shared, AtomicLong clock) {
            this.clock = clock;
            quota = shared == null ? new DescriptionQuota(clock::get) : shared;
            service = new DescriptionService(worker, session, quota, client);
        }

        CompletableFuture<GenerationResult> generate(String title, Condition condition) {
            return service.generateDescription(title, condition);
        }

        Preparation preparation(CompletableFuture<GenerationResult> result) throws Exception {
            flush();
            assertFalse(result.isDone(), "Valid request must await controlled preparation");
            Preparation prepared = client.calls.poll();
            assertNotNull(prepared, "Expected product-only request preparation");
            return prepared;
        }

        PendingAttempt dispatch(Preparation preparation, boolean dispatched) throws Exception {
            return dispatch(preparation, dispatched, true);
        }

        PendingAttempt dispatch(Preparation preparation, boolean dispatched, boolean expected) throws Exception {
            PendingAttempt attempt = new PendingAttempt(dispatched);
            int before = client.dispatches.get();
            preparation.completion().complete(client.prepared(attempt));
            flush();
            assertEquals(before + (expected ? 1 : 0), client.dispatches.get());
            return attempt;
        }

        void login(UUID account) throws Exception {
            await(worker.submit(() -> {
                session.login(account);
                return null;
            }));
        }

        void logout() throws Exception {
            await(worker.submit(() -> {
                session.logout();
                return null;
            }));
        }

        void flush() throws Exception {
            await(worker.submit(() -> null));
        }

        WorkerGate holdWorker() throws Exception {
            CompletableFuture<Void> entered = new CompletableFuture<>();
            CompletableFuture<Void> release = new CompletableFuture<>();
            CompletableFuture<Void> finished = worker.submit(() -> {
                entered.complete(null);
                await(release);
                return null;
            });
            await(entered);
            return new WorkerGate(release, finished);
        }

        @Override
        public void close() {
            service.close();
            worker.close();
        }
    }

    private record WorkerGate(CompletableFuture<Void> release, CompletableFuture<Void> finished)
            implements AutoCloseable {
        @Override
        public void close() throws Exception {
            release.complete(null);
            await(finished);
        }
    }

    private static final class ControlledClient implements LlmClient {
        private final BlockingQueue<Preparation> calls = new LinkedBlockingQueue<>();
        private final AtomicInteger preparations = new AtomicInteger();
        private final AtomicInteger dispatches = new AtomicInteger();
        private boolean throwDuringPreparation;
        private boolean nullPreparationFuture;

        @Override
        public CompletableFuture<PreparedRequest> prepare(String title, Condition condition) {
            preparations.incrementAndGet();
            if (throwDuringPreparation) {
                throw new IllegalStateException(PRIVATE_CANARY);
            }
            if (nullPreparationFuture) {
                return null;
            }
            CompletableFuture<PreparedRequest> completion = new CompletableFuture<>();
            calls.add(new Preparation(title, condition, completion));
            return completion;
        }

        @Override
        public CompletableFuture<Result> generate(String title, Condition condition) {
            throw new AssertionError("Service must prepare and size-check before quota admission");
        }

        PreparedRequest prepared(PendingAttempt attempt) {
            return beforeSend -> {
                dispatches.incrementAndGet();
                if (attempt.dispatched) {
                    beforeSend.run();
                }
                return attempt;
            };
        }
    }

    private record Preparation(String title, Condition condition,
            CompletableFuture<LlmClient.PreparedRequest> completion) {
    }

    private static final class PendingAttempt implements LlmClient.Attempt {
        private final CompletableFuture<LlmClient.Result> completion = new CompletableFuture<>();
        private boolean dispatched;
        private boolean nullResultFuture;

        PendingAttempt(boolean dispatched) {
            this.dispatched = dispatched;
        }

        @Override
        public CompletableFuture<LlmClient.Result> result() {
            return nullResultFuture ? null : completion;
        }

        @Override
        public boolean wasDispatched() {
            return dispatched;
        }
    }
}
