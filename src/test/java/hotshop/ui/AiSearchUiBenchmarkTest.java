package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.ApplicationRuntime;
import hotshop.llm.SocSearchClient;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.service.ListingSearch;
import hotshop.service.ListingDraft;
import hotshop.service.ListingSort;
import hotshop.support.PostgresTestSupport;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.Node;
import javafx.scene.AccessibleRole;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/** Frozen shared UI contract tests; the measured AI-search behavior is not implemented here. */
class AiSearchUiBenchmarkTest extends PostgresTestSupport {
    private static final String SAFE_AI_FAILURE =
            "AI Search could not create a preview. Edit your request and select AI Search to retry.";
    @TempDir
    Path directory;
    private ApplicationRuntime runtime;
    private Stage stage;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach
    void openRuntime() throws Exception {
        runtime = openTestRuntime(directory);
    }

    @AfterEach
    void closeRuntime() throws Exception {
        if (stage != null) {
            onFx(() -> {
                stage.hide();
                return null;
            });
        }
        runtime.close();
    }

    @Test
    void constructor_withProvider_retainsBorrowedProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Function<String, CompletableFuture<SocSearchClient.Result>> provider = text -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        };
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, provider);
            assertSame(provider, ui.aiSearch);
            assertEquals(0, calls.get());
            stage.hide();
            return null;
        });
    }

    @Test
    void constructor_withoutProvider_keepsManualSearchAvailable() throws Exception {
        var user = registerAndLogin("manualbuyer");
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime);
            ui.loggedIn(user);
            stage.show();
            assertNull(ui.aiSearch);
            assertNotNull(stage.getScene().lookup("#search-submit"));
            return null;
        });
    }

    @Test
    void missingProvider_disablesOnlyAiSearchAndPreservesManualSearch() throws Exception {
        var user = registerAndLogin("missingprovider");
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime);
            ui.loggedIn(user);
            stage.show();
            Button aiButton = aiButton();
            assertTrue(aiButton.isDisabled());
            assertNotNull(stage.getScene().lookup("#search-submit"));
            return null;
        });
    }

    @Test
    void manualSearchActions_neverInvokeInjectedAiProvider() throws Exception {
        var user = registerAndLogin("manualactions");
        AtomicInteger calls = new AtomicInteger();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            });
            ui.loggedIn(user);
            stage.show();
            TextField query = (TextField) stage.getScene().lookup("#search-query");
            query.setText("desk");
            query.fireEvent(new javafx.event.ActionEvent());
            ((ComboBox<?>) stage.getScene().lookup("#search-category")).getSelectionModel().select(0);
            ((TextField) stage.getScene().lookup("#minimum-price")).setText("1.00");
            ((Button) stage.getScene().lookup("#search-submit")).fire();
            ((ComboBox<?>) stage.getScene().lookup("#search-sort")).getSelectionModel().select(1);
            ((Button) stage.getScene().lookup("#search-clear")).fire();
            ((Button) stage.getScene().lookup("#search-refresh")).fire();
            assertEquals(0, calls.get());
            return null;
        });
    }

    @Test
    void aiSearch_reusesSingleQueryInputAndKeepsPriceFieldsSeparate() throws Exception {
        var user = registerAndLogin("querysurface");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> capturedInput = new AtomicReference<>();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                capturedInput.set(text);
                return CompletableFuture.completedFuture(new SocSearchClient.Failed(
                        SocSearchClient.Failure.INVALID_INPUT));
            });
            ui.loggedIn(user);
            stage.show();
            Set<String> editableInputs = stage.getScene().getRoot().lookupAll("*").stream()
                    .filter(TextInputControl.class::isInstance).map(TextInputControl.class::cast)
                    .filter(TextInputControl::isEditable).map(TextInputControl::getId).collect(Collectors.toSet());
            assertEquals(Set.of("search-query", "minimum-price", "maximum-price"), editableInputs);
            TextField query = (TextField) stage.getScene().lookup("#search-query");
            query.setText("used bicycle under S$200");
            assertEquals(0, calls.get(), "Editing the shared query field must not invoke AI");
            aiButton().fire();
            assertEquals(1, calls.get());
            assertEquals("used bicycle under S$200", capturedInput.get());
            return null;
        });
    }

    @Test
    void invalidAiInput_blankOrOver1000UnicodeCodePoints_neverInvokesProvider() throws Exception {
        var user = registerAndLogin("invalidinput");
        AtomicInteger calls = new AtomicInteger();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            });
            ui.loggedIn(user);
            stage.show();
            Button aiButton = aiButton();
            TextField query = (TextField) stage.getScene().lookup("#search-query");
            query.clear();
            aiButton.fire();
            query.setText("😀".repeat(1001));
            aiButton.fire();
            assertEquals(0, calls.get());
            return null;
        });
    }

    @Test
    void aiInput_at1000UnicodeCodePoints_isAccepted() throws Exception {
        var user = registerAndLogin("boundaryinput");
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return response;
            });
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("😀".repeat(1000));
            aiButton().fire();
            assertEquals(1, calls.get());
            response.complete(new SocSearchClient.Failed(SocSearchClient.Failure.INVALID_INPUT));
            return null;
        });
    }

    @Test
    void explicitAiClick_callsOnceBlocksReentryAndLeavesManualSearchAvailable() throws Exception {
        var user = registerAndLogin("pendingsearch");
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return response;
            });
            ui.loggedIn(user);
            stage.show();
            TextField query = (TextField) stage.getScene().lookup("#search-query");
            query.setText("used bicycle under S$200");
            Button aiButton = aiButton();
            aiButton.fire();
            aiButton.fire();
            assertEquals(1, calls.get());
            Node pendingStatus = stage.getScene().lookup("#ai-search-status");
            assertNotNull(pendingStatus, "The unresolved request must expose pending feedback");
            assertTrue(pendingStatus.isVisible(),
                    "Pending feedback must be observable while the request is unresolved");
            boolean hasPendingCue = !visibleText(pendingStatus).isBlank()
                    || pendingStatus.getAccessibleRole() == AccessibleRole.PROGRESS_INDICATOR
                    || pendingStatus.getAccessibleText() != null && !pendingStatus.getAccessibleText().isBlank()
                    || pendingStatus instanceof Labeled labeled && labeled.getGraphic() != null;
            assertTrue(hasPendingCue, "Pending feedback must provide a visible cue without fixed wording");
            ((Button) stage.getScene().lookup("#search-submit")).fire();
            assertEquals(1, calls.get(), "Manual search must not invoke AI");
            response.complete(new SocSearchClient.Failed(SocSearchClient.Failure.NETWORK));
            return null;
        });
    }

    @Test
    void proposedFilters_previewIsReadOnlyAndShowsAllCriteriaInSgd() throws Exception {
        var user = registerAndLogin("previewbuyer");
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        AtomicReference<MarketplaceUi> uiRef = new AtomicReference<>();
        AtomicReference<ListingSearch> submittedRef = new AtomicReference<>();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> response);
            uiRef.set(ui);
            ui.loggedIn(user);
            stage.show();
            TextField query = (TextField) stage.getScene().lookup("#search-query");
            query.setText("manual desk");
            ((Button) stage.getScene().lookup("#search-submit")).fire();
            ListingSearch submittedBefore = ui.searchState.getSubmitted().orElseThrow();
            submittedRef.set(submittedBefore);
            Button aiButton = aiButton();
            aiButton.fire();
            return null;
        });
        CompletableFuture.runAsync(() -> response.complete(new SocSearchClient.Proposed(new ListingSearch("bicycle",
                Category.SPORTS, Set.of(Condition.GOOD), 0L, 20_000L, ListingSort.PRICE_LOW_TO_HIGH))))
                .get(2, TimeUnit.SECONDS);
        onFx(() -> {
            Node preview = stage.getScene().lookup("#ai-search-preview");
            assertNotNull(preview, "A validated proposal must be shown before Apply");
            String text = visibleText(preview).toLowerCase(java.util.Locale.ROOT);
            for (String term : new String[] {"bicycle", "sports", "good", "s$0.00", "s$200.00", "price low"}) {
                assertTrue(text.contains(term), "Preview should communicate " + term);
            }
            assertEquals("manual desk", ((TextField) stage.getScene().lookup("#search-query")).getText());
            assertEquals(submittedRef.get(), uiRef.get().searchState.getSubmitted().orElseThrow());
            assertNotNull(stage.getScene().lookup("#ai-search-apply"));
            assertNotNull(stage.getScene().lookup("#ai-search-dismiss"));
            return null;
        });
    }

    @Test
    void failureNullAndException_useSafeFeedbackPreserveManualStateAndRetryOnlyExplicitly() throws Exception {
        var user = registerAndLogin("failurebuyer");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<MarketplaceUi> uiRef = new AtomicReference<>();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                int attempt = calls.incrementAndGet();
                return switch (attempt) {
                    case 1 -> CompletableFuture.completedFuture(new SocSearchClient.Failed(
                            SocSearchClient.Failure.PROVIDER_AUTH));
                    case 2 -> CompletableFuture.failedFuture(new IllegalStateException("private-error-token"));
                    default -> CompletableFuture.completedFuture(null);
                };
            });
            uiRef.set(ui);
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("manual desk");
            ((Button) stage.getScene().lookup("#search-submit")).fire();
            aiButton().fire();
            assertEquals(1, calls.get());
            return null;
        });
        String feedback = awaitSafeAiFailure("A failed result must provide user feedback");
        onFx(() -> {
            assertEquals("manual desk", ((TextField) stage.getScene().lookup("#search-query")).getText());
            assertEquals("manual desk", uiRef.get().searchState.getSubmitted().orElseThrow().titleText());
            return null;
        });
        onFx(() -> {
            assertEquals(1, calls.get(), "Failure must not retry automatically");
            aiButton().fire();
            assertEquals(2, calls.get(), "Retry requires another explicit click");
            return null;
        });
        assertEquals(feedback, awaitSafeAiFailure("Provider exceptions must provide user feedback"),
                "Provider exceptions must use the same fixed safe feedback");
        onFx(() -> {
            aiButton().fire();
            assertEquals(3, calls.get(), "Null response retry also requires an explicit click");
            return null;
        });
        assertEquals(feedback, awaitSafeAiFailure("Null responses must provide user feedback"));
        onFx(() -> {
            assertEquals("manual desk", uiRef.get().searchState.getSubmitted().orElseThrow().titleText());
            return null;
        });
    }

    @Test
    void dismissPreview_preservesManualQueryAndSubmittedCriteria() throws Exception {
        var user = registerAndLogin("dismissbuyer");
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        AtomicReference<MarketplaceUi> uiRef = new AtomicReference<>();
        AtomicReference<ListingSearch> submitted = new AtomicReference<>();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> response);
            uiRef.set(ui);
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("manual entry");
            ((Button) stage.getScene().lookup("#search-submit")).fire();
            submitted.set(ui.searchState.getSubmitted().orElseThrow());
            aiButton().fire();
            response.complete(new SocSearchClient.Proposed(new ListingSearch("proposal", null, Set.of(), null,
                    null, ListingSort.NEWEST)));
            return null;
        });
        onFx(() -> {
            Node preview = stage.getScene().lookup("#ai-search-preview");
            assertNotNull(preview, "Proposal must be reviewable before dismissal");
            Button dismiss = (Button) stage.getScene().lookup("#ai-search-dismiss");
            assertNotNull(dismiss, "Preview must have an explicit Dismiss action");
            dismiss.fire();
            assertTrue(preview == stage.getScene().lookup("#ai-search-preview") && !preview.isVisible()
                    || stage.getScene().lookup("#ai-search-preview") == null,
                    "Dismiss must clear the current proposal preview");
            assertEquals("manual entry", ((TextField) stage.getScene().lookup("#search-query")).getText());
            assertEquals(submitted.get(), uiRef.get().searchState.getSubmitted().orElseThrow());
            return null;
        });
    }

    @Test
    void pendingProposal_queryEditAlone_invalidatesLateCompletion() throws Exception {
        assertPendingCompletionInvalidated("pendingquery", scene ->
                ((TextField) scene.lookup("#search-query")).setText("manual replacement"));
    }

    @Test
    void pendingProposal_filterEditAlone_invalidatesLateCompletion() throws Exception {
        assertPendingCompletionInvalidated("pendingfilter", scene ->
                ((ComboBox<?>) scene.lookup("#search-category")).getSelectionModel().select(0));
    }

    @Test
    void pendingProposal_manualSubmitAlone_invalidatesLateCompletion() throws Exception {
        assertPendingCompletionInvalidated("pendingsubmit", scene ->
                ((Button) scene.lookup("#search-submit")).fire());
    }

    @Test
    void existingPreview_queryEditAlone_invalidatesPreview() throws Exception {
        assertExistingPreviewInvalidated("previewquery", scene ->
                ((TextField) scene.lookup("#search-query")).setText("manual replacement"));
    }

    @Test
    void existingPreview_filterEditAlone_invalidatesPreview() throws Exception {
        assertExistingPreviewInvalidated("previewfilter", scene ->
                ((ComboBox<?>) scene.lookup("#search-category")).getSelectionModel().select(0));
    }

    @Test
    void existingPreview_manualSubmitAlone_invalidatesPreview() throws Exception {
        assertExistingPreviewInvalidated("previewsubmit", scene ->
                ((Button) scene.lookup("#search-submit")).fire());
    }

    @Test
    void leavingSearchPage_ignoresLateProposalCompletion() throws Exception {
        var user = registerAndLogin("leavebuyer");
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return response;
            });
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("proposal request");
            aiButton().fire();
            assertEquals(1, calls.get(), "A valid query must start one pending proposal request");
            ((Button) stage.getScene().lookup("#nav-offers")).fire();
            response.complete(new SocSearchClient.Proposed(new ListingSearch("stale", null, Set.of(), null,
                    null, ListingSort.NEWEST)));
            return null;
        });
        awaitSceneText("#page-title", "My Offers");
        onFx(() -> {
            assertEquals("My Offers", ((Labeled) stage.getScene().lookup("#page-title")).getText());
            assertNull(stage.getScene().lookup("#ai-search-preview"));
            return null;
        });
    }

    @Test
    void logout_ignoresLateProposalCompletion() throws Exception {
        var user = registerAndLogin("logoutbuyer");
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return response;
            });
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("proposal request");
            aiButton().fire();
            assertEquals(1, calls.get(), "A valid query must start one pending proposal request");
            ((Button) stage.getScene().lookup("#nav-logout")).fire();
            return null;
        });
        runtime.getAccounts().register("logoutbarrier", "Sample1!", "Buyer").join();
        response.complete(new SocSearchClient.Proposed(new ListingSearch("stale", null, Set.of(), null,
                null, ListingSort.NEWEST)));
        onFx(() -> {
            assertEquals("Log in", ((Labeled) stage.getScene().lookup("#page-title")).getText());
            assertNull(stage.getScene().lookup("#ai-search-preview"));
            return null;
        });
    }

    @Test
    void applyProposal_replacesCompleteDraftAndSubmitsThroughListingSearch() throws Exception {
        registerAndLogin("applyseller");
        runtime.getListings().createListing(new ListingDraft("Bicycle", "Road bicycle", Category.SPORTS,
                2_500L, Condition.GOOD, "Campus"), java.util.List.of()).join();
        runtime.getAccounts().logout().join();
        var user = registerAndLogin("applybuyer");
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        AtomicReference<MarketplaceUi> uiRef = new AtomicReference<>();
        ListingSearch proposal = new ListingSearch("bicycle", null, Set.of(), null, 2_500L,
                ListingSort.PRICE_HIGH_TO_LOW);
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> response);
            uiRef.set(ui);
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("old title");
            ((TextField) stage.getScene().lookup("#minimum-price")).setText("12.50");
            ((TextField) stage.getScene().lookup("#maximum-price")).setText("90.00");
            ((ComboBox<?>) stage.getScene().lookup("#search-category")).getSelectionModel().select(0);
            Button aiButton = aiButton();
            aiButton.fire();
            response.complete(new SocSearchClient.Proposed(proposal));
            return null;
        });
        onFx(() -> {
            Button apply = (Button) stage.getScene().lookup("#ai-search-apply");
            assertNotNull(apply, "Validated proposal must offer an explicit Apply action");
            apply.fire();
            assertEquals(proposal, uiRef.get().searchState.getSubmitted().orElseThrow());
            assertEquals("bicycle", ((TextField) stage.getScene().lookup("#search-query")).getText());
            assertNull(((ComboBox<?>) stage.getScene().lookup("#search-category")).getValue());
            assertEquals("", ((TextField) stage.getScene().lookup("#minimum-price")).getText());
            assertEquals("25.00", ((TextField) stage.getScene().lookup("#maximum-price")).getText());
            for (Node node : stage.getScene().getRoot().lookupAll("CheckBox")) {
                assertFalse(((CheckBox) node).isSelected(), "Missing proposal conditions must clear old values");
            }
            return null;
        });
        assertEquals(1, runtime.getListings().searchListings(proposal).join().size(),
                "The applied filters must use ordinary PostgreSQL listing search");
        onFx(() -> {
            assertEquals("1 listing", ((Labeled) stage.getScene().lookup("#results-count")).getText());
            return null;
        });
    }

    @Test
    void searchPage_exposesExplicitAiSearchControlsWithExactLabel() throws Exception {
        var user = registerAndLogin("buyer");
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> CompletableFuture.completedFuture(null));
            ui.loggedIn(user);
            stage.show();
            Scene scene = stage.getScene();
            Button button = (Button) scene.lookup("#ai-search");
            assertNotNull(button, "Search page must expose the explicit AI search button");
            assertEquals("AI Search", ((Labeled) button).getText());
            return null;
        });
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private hotshop.model.User registerAndLogin(String username) {
        runtime.getAccounts().register(username, "Sample1!", "Buyer").join();
        return runtime.getAccounts().login(username, "Sample1!").join();
    }

    private Button aiButton() {
        Button button = (Button) stage.getScene().lookup("#ai-search");
        assertNotNull(button, "Search page must expose the explicit AI search button");
        assertEquals("AI Search", ((Labeled) button).getText());
        return button;
    }

    private String visibleText(Node root) {
        StringBuilder text = new StringBuilder();
        if (root instanceof Labeled labeled) {
            text.append(labeled.getText()).append(' ');
        }
        if (root instanceof Text value) {
            text.append(value.getText()).append(' ');
        }
        if (root instanceof TextField field) {
            text.append(field.getText()).append(' ');
        }
        for (Node child : root.lookupAll("*")) {
            if (child instanceof Labeled labeled) {
                text.append(labeled.getText()).append(' ');
            } else if (child instanceof Text value) {
                text.append(value.getText()).append(' ');
            } else if (child instanceof TextField field) {
                text.append(field.getText()).append(' ');
            }
        }
        return text.toString();
    }

    private void awaitSceneText(String selector, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (onFx(() -> stage.getScene().lookup(selector) instanceof Labeled label
                    && label.getText().equals(expected))) {
                return;
            }
            Thread.sleep(25);
        }
        assertEquals(expected, onFx(() -> ((Labeled) stage.getScene().lookup(selector)).getText()));
    }

    private String awaitSafeAiFailure(String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            String text = onFx(() -> {
                Node status = stage.getScene().lookup("#ai-search-status");
                Button button = aiButton();
                if (button.isDisabled()) {
                    return null;
                }
                assertNotNull(status, message);
                String visible = visibleText(status);
                assertFalse(visible.contains("PROVIDER_AUTH"));
                assertFalse(visible.contains("private-error-token"));
                return status instanceof Labeled labeled ? labeled.getText() : visible.strip();
            });
            if (SAFE_AI_FAILURE.equals(text)) {
                return text;
            }
            Thread.sleep(25);
        }
        String text = onFx(() -> visibleText(Objects.requireNonNull(stage.getScene().lookup("#ai-search-status"))));
        assertEquals(SAFE_AI_FAILURE, text);
        return text;
    }

    private void assertPendingCompletionInvalidated(String username, java.util.function.Consumer<Scene> action)
            throws Exception {
        var user = registerAndLogin(username);
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return response;
            });
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("proposal request");
            aiButton().fire();
            assertEquals(1, calls.get(), "A valid query must start one pending proposal request");
            action.accept(stage.getScene());
            response.complete(new SocSearchClient.Proposed(new ListingSearch("stale", null, Set.of(), null,
                    null, ListingSort.NEWEST)));
            return null;
        });
        onFx(() -> {
            Node preview = stage.getScene().lookup("#ai-search-preview");
            assertTrue(preview == null || !preview.isVisible(), "Invalidated completion must not show a preview");
            return null;
        });
    }

    private void assertExistingPreviewInvalidated(String username, java.util.function.Consumer<Scene> action)
            throws Exception {
        var user = registerAndLogin(username);
        CompletableFuture<SocSearchClient.Result> response = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime, text -> {
                calls.incrementAndGet();
                return response;
            });
            ui.loggedIn(user);
            stage.show();
            ((TextField) stage.getScene().lookup("#search-query")).setText("proposal request");
            aiButton().fire();
            assertEquals(1, calls.get(), "A valid query must start one proposal request");
            return null;
        });
        response.complete(new SocSearchClient.Proposed(new ListingSearch("proposal", null, Set.of(), null, null,
                ListingSort.NEWEST)));
        onFx(() -> {
            Node preview = stage.getScene().lookup("#ai-search-preview");
            assertNotNull(preview, "A successful response must create a reviewable preview");
            assertTrue(preview.isVisible(), "The proposal preview must be visible before the edit");
            action.accept(stage.getScene());
            Node afterEdit = stage.getScene().lookup("#ai-search-preview");
            assertTrue(afterEdit == null || !afterEdit.isVisible(), "The edit must invalidate the preview");
            return null;
        });
    }
}
