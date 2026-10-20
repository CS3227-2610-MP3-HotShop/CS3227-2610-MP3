package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.ApplicationRuntime;
import hotshop.DescriptionRuntime;
import hotshop.llm.LlmClient;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.service.DescriptionService;
import hotshop.service.ListingDraft;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Real-control checks against temporary storage and a controlled, network-free transport. */
class ListingDescriptionGenerationTest {
    private static final String DESCRIPTION = "A wooden chair in good condition.";

    @TempDir
    Path directory;
    private ApplicationRuntime runtime;
    private DescriptionRuntime owner;
    private FakeClient client;
    private MarketplaceUi ui;
    private Stage stage;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Runnable initialize = () -> {
            Platform.setImplicitExit(false);
            started.countDown();
        };
        try {
            Platform.startup(initialize);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(initialize);
        }
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @BeforeEach
    void openCreateListing() throws Exception {
        client = new FakeClient();
        owner = new DescriptionRuntime(client);
        runtime = ApplicationRuntime.open(directory, Clock.systemUTC(), owner);
        runtime.getAccounts().register("alice", "Sample1!", "Alice").get(10, TimeUnit.SECONDS);
        var user = runtime.getAccounts().login("alice", "Sample1!").get(10, TimeUnit.SECONDS);
        fx(() -> {
            stage = new Stage();
            ui = new MarketplaceUi(stage, runtime);
            ui.loggedIn(user);
            ui.replace(() -> new ListingEditor(ui, null));
            stage.show();
            return null;
        });
        await(() -> node("title") != null && !node("page-body").isDisabled());
    }

    @AfterEach
    void closeWindow() throws Exception {
        if (stage != null) {
            fx(() -> {
                stage.hide();
                return null;
            });
        }
        if (runtime != null) {
            runtime.close();
        }
        if (owner != null) {
            owner.close();
        }
    }

    @Test
    void createListing_generationControls_disclosesExternalFactsReplacementAndReview() throws Exception {
        fx(() -> {
            Button generate = button("generate-description");
            assertEquals("Generate Description", generate.getText());
            assertTrue(generate.isFocusTraversable());
            assertFalse(generate.isDisabled());
            Label notice = (Label) node("generation-notice");
            assertNotNull(notice);
            String text = notice.getText().toLowerCase();
            for (String expected : List.of("title", "condition", "external", "replac", "review", "sav")) {
                assertTrue(text.contains(expected), () -> "Missing disclosure: " + expected);
            }
            assertTrue(notice.isVisible());
            assertTrue(notice.isManaged());
            assertFalse(node("generation-progress").isVisible());
            assertFalse(node("generation-progress").isManaged());
            return null;
        });
    }

    @ParameterizedTest
    @EnumSource(Condition.class)
    void generateDescription_onlyTitleAndEachCondition_dispatchesWithoutOtherListingFields(Condition condition)
            throws Exception {
        minimum("  Chair  ", condition);
        click("generate-description");
        var attempt = client.next();
        assertEquals("Chair", attempt.title());
        assertEquals(condition, attempt.condition());
        attempt.result().complete(new LlmClient.Generated(DESCRIPTION));
        await(() -> text("description").equals(DESCRIPTION));
        assertTrue(runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 120})
    void generateDescription_titleCodePointBoundaries_dispatches(int count) throws Exception {
        String title = "\ud801\udc00".repeat(count);
        minimum(title, Condition.GOOD);
        click("generate-description");
        var attempt = client.next();
        assertEquals(title, attempt.title());
        attempt.result().complete(new LlmClient.Generated(DESCRIPTION));
        await(() -> text("description").equals(DESCRIPTION));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void generateDescription_missingTitle_showsFieldFeedbackWithoutDispatch(String title) throws Exception {
        minimum(title, Condition.GOOD);
        click("generate-description");
        assertTrue(fx(() -> !label("title-error").getText().isBlank()));
        assertEquals(0, client.preparations.get());
        assertEquals("", fx(() -> label("condition-error").getText()));
    }

    @Test
    void generateDescription_titleAboveBoundary_showsFieldFeedbackWithoutDispatch() throws Exception {
        minimum("\ud801\udc00".repeat(121), Condition.GOOD);
        click("generate-description");
        assertTrue(fx(() -> !label("title-error").getText().isBlank()));
        assertEquals(0, client.preparations.get());
    }

    @Test
    void generateDescription_missingCondition_showsSeparateFieldFeedbackWithoutDispatch() throws Exception {
        type("title", "Chair");
        click("generate-description");
        assertTrue(fx(() -> !label("condition-error").getText().isBlank()));
        assertEquals("", fx(() -> label("title-error").getText()));
        assertEquals(0, client.preparations.get());
    }

    @Test
    void generateDescription_pending_disablesOnlyGenerateAndLeavesEditingAndSaveResponsive() throws Exception {
        minimum("Chair", Condition.GOOD);
        click("generate-description");
        var attempt = client.next();
        fx(() -> {
            assertTrue(button("generate-description").isDisabled());
            assertTrue(node("generation-progress").isVisible());
            assertTrue(node("generation-progress").isManaged());
            assertFalse(label("generation-status").getText().isBlank());
            for (String id : List.of("page-body", "title", "description", "condition", "price", "category",
                    "pickup-location", "save-listing", "add-photo", "cancel-edit", "nav-logout")) {
                assertFalse(node(id).isDisabled(), () -> id + " must remain available");
            }
            return null;
        });
        type("price", "12.34");
        assertEquals("12.34", fx(() -> text("price")));
        assertFalse(runtime.getAccounts().getCurrentUserId().get(10, TimeUnit.SECONDS).isEmpty());
        attempt.result().complete(new LlmClient.Failed(LlmClient.Failure.NETWORK));
        awaitFinished();
    }

    @Test
    void generateDescription_success_replacesOnceKeepsDraftEditableAndRequiresExplicitSave() throws Exception {
        fullDraft();
        AtomicInteger changes = new AtomicInteger();
        fx(() -> {
            ((TextInputControl) node("description")).textProperty().addListener(
                    (observable, previous, current) -> changes.incrementAndGet());
            return null;
        });
        click("generate-description");
        var attempt = client.next();
        attempt.result().complete(new LlmClient.Generated(DESCRIPTION));
        await(() -> text("description").equals(DESCRIPTION));
        awaitFinished();
        assertEquals(1, changes.get());
        assertFalse(attempt.result().complete(new LlmClient.Generated("Duplicate result")));
        assertEquals(1, changes.get());
        assertTrue(runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS).isEmpty());
        assertDraftExceptDescription();
        assertTrue(fx(() -> ((TextInputControl) node("description")).isEditable()));
        type("description", DESCRIPTION + " Reviewed.");
        click("save-listing");
        await(() -> label("page-title").getText().equals("Listing Details")
                && !node("page-body").isDisabled());
        var listings = runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS);
        assertEquals(1, listings.size());
        assertEquals(DESCRIPTION + " Reviewed.", listings.getFirst().listing().listing().getDetails().description());
    }

    @ParameterizedTest
    @EnumSource(LlmClient.Failure.class)
    void generateDescription_safeTransportFailure_preservesFullDraftAndShowsLocalFeedback(LlmClient.Failure failure)
            throws Exception {
        fullDraft();
        click("generate-description");
        client.next().result().complete(new LlmClient.Failed(failure));
        awaitFinished();
        assertEquals("Manual draft", fx(() -> text("description")));
        assertDraftExceptDescription();
        fx(() -> {
            Label status = label("generation-status");
            assertTrue(status.isVisible());
            assertTrue(status.isManaged());
            assertFalse(status.getText().isBlank());
            assertFalse(status.getText().contains("Exception"));
            assertFalse(status.getText().contains("http"));
            return null;
        });
        assertTrue(runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void generateDescription_otherRequestPending_preservesDraftAndExplainsActiveAdmission() throws Exception {
        fullDraft();
        var pending = runtime.getDescriptions().generateDescription("Other chair", Condition.GOOD);
        var transport = client.next();
        click("generate-description");
        awaitFinished();
        fx(() -> {
            String status = label("generation-status").getText().toLowerCase();
            assertTrue(status.contains("already") || status.contains("progress") || status.contains("pending"));
            assertEquals("Manual draft", text("description"));
            return null;
        });
        assertDraftExceptDescription();
        assertEquals(0, client.attempts.size());
        transport.result().complete(new LlmClient.Generated(DESCRIPTION));
        pending.get(10, TimeUnit.SECONDS);
    }

    @Test
    void generateDescription_localRateLimit_preservesDraftAndDisplaysRetrySeconds() throws Exception {
        fullDraft();
        for (int index = 0; index < 5; index++) {
            var generation = runtime.getDescriptions().generateDescription("Other chair", Condition.GOOD);
            client.next().result().complete(new LlmClient.Generated(DESCRIPTION));
            generation.get(10, TimeUnit.SECONDS);
        }
        click("generate-description");
        awaitFinished();
        fx(() -> {
            String status = label("generation-status").getText().toLowerCase();
            assertTrue(status.matches("(?s).*\\d+.*"), "Rate-limit feedback must include retry duration");
            assertTrue(status.contains("second"));
            assertEquals("Manual draft", text("description"));
            return null;
        });
        assertDraftExceptDescription();
        assertEquals(0, client.attempts.size());
        assertTrue(runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void generateDescription_keyboardSpace_dispatchesAndAnnouncesSuccess() throws Exception {
        minimum("Chair", Condition.GOOD);
        fx(() -> {
            Button target = button("generate-description");
            target.requestFocus();
            target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE,
                    false, false, false, false));
            target.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.SPACE,
                    false, false, false, false));
            return null;
        });
        client.next().result().complete(new LlmClient.Generated(DESCRIPTION));
        awaitFinished();
        assertEquals(DESCRIPTION, fx(() -> text("description")));
        assertFalse(fx(() -> label("generation-status").getText().isBlank()));
    }

    @Test
    void saveListing_generationPending_savesManualDraftWithoutWaitingForProvider() throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        click("save-listing");
        await(() -> label("page-title").getText().equals("Listing Details")
                && !node("page-body").isDisabled());
        var saved = runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS);
        assertEquals(1, saved.size());
        assertEquals("Manual draft", saved.getFirst().listing().listing().getDetails().description());
        attempt.result().complete(new LlmClient.Generated(DESCRIPTION));
        runtime.getAccounts().getCurrentUserId().get(10, TimeUnit.SECONDS);
        fx(() -> null);
        assertEquals("Manual draft", runtime.getListings().getMyListings()
                .get(10, TimeUnit.SECONDS).getFirst().listing().listing().getDetails().description());
    }

    @Test
    void editListing_existingListing_hasNoGenerationControls() throws Exception {
        var listing = runtime.getListings().createListing(new ListingDraft("Chair", "Manual draft",
                Category.FURNITURE, 1234, Condition.GOOD, "Library"), List.of()).get(10, TimeUnit.SECONDS);
        fx(() -> {
            ui.replace(() -> new ListingEditor(ui, listing.listing()));
            return null;
        });
        fx(() -> {
            assertNull(node("generate-description"));
            assertNull(node("generation-notice"));
            assertEquals("Manual draft", text("description"));
            return null;
        });
    }

    @Test
    void createListing_minimumWindowAndLongDraft_keepsControlsInsideScrollableForm() throws Exception {
        fullDraft();
        type("description", "A wooden chair. ".repeat(300));
        fx(() -> {
            stage.setWidth(960);
            stage.setHeight(640);
            return null;
        });
        await(() -> stage.getWidth() == 960 && stage.getHeight() == 640);
        fx(() -> {
            assertTrue(button("generate-description").getWidth() > 0);
            assertTrue(label("generation-notice").isWrapText());
            assertNotNull(ui.scroll());
            assertTrue(ui.scroll().isFitToWidth());
            assertTrue(node("generation-notice").getBoundsInParent().getWidth()
                    <= ui.scroll().getViewportBounds().getWidth());
            var snapshot = stage.getScene().snapshot(null);
            int width = (int) snapshot.getWidth();
            int height = (int) snapshot.getHeight();
            int[] pixels = new int[width * height];
            snapshot.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(),
                    pixels, 0, width);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            Path output = Path.of("build", "ui-checks", "listing-description-generation-minimum.png");
            Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
            return null;
        });
    }

    @Test
    void close_javaFxThreadWithBusyWorker_returnsBeforeDrainAndCancelsGeneration() throws Exception {
        minimum("Chair", Condition.GOOD);
        click("generate-description");
        var attempt = client.next();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var busy = runtime.getAccounts().changePassword("Sample1!", "Other2!Password").thenRun(() -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Worker gate timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            fx(() -> {
                runtime.close();
                assertFalse(runtime.closeAsync().isDone());
                assertTrue(attempt.result().isCancelled());
                return null;
            });
        } finally {
            release.countDown();
        }
        busy.get(10, TimeUnit.SECONDS);
        runtime.closeAsync().get(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "description", "condition"})
    void generateDescription_relevantFieldEdited_preservesDraftAndAllowsRetry(String field) throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        editGenerationField(field);
        String expected = fx(() -> text("description"));
        completeAndDrain(attempt);
        assertEquals(expected, fx(() -> text("description")));
        assertFalse(fx(() -> button("generate-description").isDisabled()));
        assertFalse(fx(() -> node("generation-progress").isVisible()));
        assertTrue(fx(() -> label("generation-status").getText().toLowerCase().contains("chang")));
        click("generate-description");
        client.next().result().complete(new LlmClient.Generated(DESCRIPTION));
        awaitFinished();
        assertEquals(DESCRIPTION, fx(() -> text("description")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "description", "condition"})
    void generateDescription_relevantFieldEditedThenReverted_rejectsOldResult(String field) throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        editGenerationField(field);
        if (field.equals("condition")) {
            fx(() -> {
                ((ComboBox<?>) node("condition")).getSelectionModel().select(Condition.GOOD.ordinal());
                return null;
            });
        } else {
            type(field, field.equals("title") ? "Chair" : "Manual draft");
        }
        completeAndDrain(attempt);
        assertEquals("Manual draft", fx(() -> text("description")));
        assertFalse(fx(() -> button("generate-description").isDisabled()));
        assertTrue(fx(() -> label("generation-status").getText().toLowerCase().contains("chang")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"navigate", "back", "replace", "login", "relogin", "logout", "save", "hide"})
    void generateDescription_lifecycleInvalidated_doesNotMutateOldControls(String action) throws Exception {
        if (action.equals("back")) {
            fx(() -> {
                ui.navigate(() -> new ListingEditor(ui, null));
                return null;
            });
            await(() -> node("title") != null && !node("page-body").isDisabled());
        }
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        var before = captureGeneration();
        if (action.equals("navigate") || action.equals("back") || action.equals("logout")) {
            confirmAction(() -> {
                if (action.equals("navigate")) {
                    button("nav-search").fire();
                } else if (action.equals("back")) {
                    ui.back();
                } else {
                    button("nav-logout").fire();
                }
            }, "Discard Changes");
        } else if (action.equals("save")) {
            click("save-listing");
            await(() -> label("page-title").getText().equals("Listing Details")
                    && !node("page-body").isDisabled());
        } else if (action.equals("relogin")) {
            runtime.getAccounts().logout().get(10, TimeUnit.SECONDS);
            var user = runtime.getAccounts().login("alice", "Sample1!").get(10, TimeUnit.SECONDS);
            fx(() -> {
                ui.loggedIn(user);
                return null;
            });
        } else {
            fx(() -> {
                switch (action) {
                    case "replace":
                        ui.replace(() -> new ListingEditor(ui, null));
                        break;
                    case "login":
                        ui.login("");
                        break;
                    case "hide":
                        stage.hide();
                        break;
                    default:
                        throw new AssertionError(action);
                }
                return null;
            });
        }
        completeAndDrain(attempt);
        assertUnchanged(before);
    }

    @Test
    void generateDescription_detachedPage_retainsAccountAdmissionUntilTransportTerminates() throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        fx(() -> {
            ui.replace(() -> new ListingEditor(ui, null));
            return null;
        });
        var denied = runtime.getDescriptions().generateDescription("Other chair", Condition.GOOD)
                .get(10, TimeUnit.SECONDS);
        assertEquals(DescriptionService.FailureKind.IN_FLIGHT, ((DescriptionService.Failed) denied).kind());
        assertFalse(attempt.result().isCancelled());
        assertEquals(0, client.attempts.size());
        completeAndDrain(attempt);
        var next = runtime.getDescriptions().generateDescription("Other chair", Condition.GOOD);
        client.next().result().complete(new LlmClient.Generated(DESCRIPTION));
        assertTrue(next.get(10, TimeUnit.SECONDS) instanceof DescriptionService.Generated);
    }

    @Test
    void generateDescription_cancelledClose_keepsPendingFormUsable() throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        confirmAction(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)), "Cancel");
        assertTrue(fx(() -> stage.isShowing()));
        completeAndDrain(attempt);
        assertEquals(DESCRIPTION, fx(() -> text("description")));
        assertFalse(fx(() -> button("generate-description").isDisabled()));
        type("description", "Still editable");
        assertEquals("Still editable", fx(() -> text("description")));
    }

    @Test
    void generateDescription_acceptedClose_doesNotMutateOldControls() throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        var before = captureGeneration();
        confirmAction(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)),
                "Discard Changes");
        completeAndDrain(attempt);
        assertUnchanged(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept-close", "cancel-close", "logout", "relogin"})
    void generateDescription_completionQueuedBeforeLifecycleChange_checksFinalDecision(String action) throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        var before = captureGeneration();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> closed = new CompletableFuture<>();
        var runtimeUser = runtime.getAccounts().getOwnProfile().get(10, TimeUnit.SECONDS);
        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch workerRelease = new CountDownLatch(1);
        Platform.runLater(() -> {
            entered.countDown();
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
                if (action.equals("logout")) {
                    button("nav-logout").fire();
                } else if (action.equals("relogin")) {
                    ui.loggedIn(runtimeUser);
                } else {
                    stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                }
                closed.complete(null);
            } catch (Throwable failure) {
                closed.completeExceptionally(failure);
            }
        });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        try {
            attempt.result().complete(new LlmClient.Generated(DESCRIPTION));
            runtime.getAccounts().getCurrentUserId().get(10, TimeUnit.SECONDS);
            if (action.equals("relogin")) {
                runtime.getAccounts().logout().get(10, TimeUnit.SECONDS);
                runtime.getAccounts().login("alice", "Sample1!").get(10, TimeUnit.SECONDS);
            }
            if (action.equals("logout")) {
                runtime.getAccounts().changePassword("Sample1!", "Other2!Password").thenRun(() -> {
                    workerEntered.countDown();
                    try {
                        assertTrue(workerRelease.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                });
                assertTrue(workerEntered.await(10, TimeUnit.SECONDS));
            }
        } finally {
            release.countDown();
        }
        try {
            if (!action.equals("relogin")) {
                acceptDialog(action.equals("cancel-close") ? "Cancel" : "Discard Changes");
            }
            closed.get(10, TimeUnit.SECONDS);
            fx(() -> null);
            if (action.equals("cancel-close")) {
                assertTrue(fx(() -> stage.isShowing()));
                assertEquals(DESCRIPTION, fx(() -> text("description")));
                assertFalse(fx(() -> button("generate-description").isDisabled()));
            } else if (action.equals("logout")) {
                fx(() -> {
                    assertEquals(before.text(), before.description().getText());
                    assertEquals(before.message(), before.status().getText());
                    return null;
                });
            } else {
                assertUnchanged(before);
            }
        } finally {
            workerRelease.countDown();
        }
    }

    @Test
    void generateDescription_failureAfterFieldEdit_preservesDraftAndReportsChangedDraft() throws Exception {
        fullDraft();
        click("generate-description");
        var attempt = client.next();
        type("description", "New manual draft");
        attempt.result().complete(new LlmClient.Failed(LlmClient.Failure.NETWORK));
        runtime.getAccounts().getCurrentUserId().get(10, TimeUnit.SECONDS);
        awaitFinished();
        assertEquals("New manual draft", fx(() -> text("description")));
        assertTrue(fx(() -> label("generation-status").getText().toLowerCase().contains("chang")));
    }

    private void editGenerationField(String field) throws Exception {
        if (field.equals("condition")) {
            fx(() -> {
                ((ComboBox<?>) node("condition")).getSelectionModel().select(Condition.NEW.ordinal());
                return null;
            });
        } else {
            type(field, "Changed draft");
        }
    }

    private GenerationControls captureGeneration() throws Exception {
        return fx(() -> new GenerationControls((TextInputControl) node("description"),
                label("generation-status"), button("generate-description"), node("generation-progress"),
                text("description"), label("generation-status").getText(),
                button("generate-description").isDisabled(), node("generation-progress").isVisible()));
    }

    private void assertUnchanged(GenerationControls before) throws Exception {
        fx(() -> {
            assertEquals(before.text(), before.description().getText());
            assertEquals(before.message(), before.status().getText());
            assertEquals(before.disabled(), before.generate().isDisabled());
            assertEquals(before.visible(), before.progress().isVisible());
            return null;
        });
    }

    private void completeAndDrain(PendingAttempt attempt) throws Exception {
        attempt.result().complete(new LlmClient.Generated(DESCRIPTION));
        runtime.getAccounts().getCurrentUserId().get(10, TimeUnit.SECONDS);
        fx(() -> null);
        fx(() -> null);
    }

    private void confirmAction(Runnable action, String choice) throws Exception {
        Platform.runLater(action);
        acceptDialog(choice);
        fx(() -> null);
    }

    private void acceptDialog(String choice) throws Exception {
        await(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window != stage && window.isShowing()) {
                    for (Node node : window.getScene().getRoot().lookupAll(".button")) {
                        if (node instanceof Button button && button.getText().equals(choice)) {
                            button.fire();
                            return true;
                        }
                    }
                }
            }
            return false;
        });
    }

    private record GenerationControls(TextInputControl description, Label status, Button generate, Node progress,
            String text, String message, boolean disabled, boolean visible) {
    }

    private void fullDraft() throws Exception {
        minimum("Chair", Condition.GOOD);
        type("description", "Manual draft");
        type("price", "12.34");
        type("pickup-location", "Library");
        fx(() -> {
            ((ComboBox<?>) node("category")).getSelectionModel().select(Category.FURNITURE.ordinal());
            return null;
        });
    }

    private void assertDraftExceptDescription() throws Exception {
        fx(() -> {
            assertEquals("Chair", text("title"));
            assertEquals("12.34", text("price"));
            assertEquals("Library", text("pickup-location"));
            assertEquals(Category.FURNITURE, ((ComboBox<?>) node("category")).getValue());
            assertEquals(Condition.GOOD, ((ComboBox<?>) node("condition")).getValue());
            return null;
        });
    }

    private void minimum(String title, Condition condition) throws Exception {
        type("title", title);
        fx(() -> {
            ((ComboBox<?>) node("condition")).getSelectionModel().select(condition.ordinal());
            return null;
        });
    }

    private void type(String id, String value) throws Exception {
        fx(() -> {
            ((TextInputControl) node(id)).setText(value == null ? "" : value);
            return null;
        });
    }

    private void click(String id) throws Exception {
        fx(() -> {
            button(id).fire();
            return null;
        });
    }

    private Node node(String id) {
        return stage.getScene().lookup("#" + id);
    }

    private Label label(String id) {
        return (Label) node(id);
    }

    private String text(String id) {
        return ((TextInputControl) node(id)).getText();
    }

    private Button button(String id) {
        Button result = (Button) node(id);
        assertNotNull(result, () -> "Missing control #" + id);
        return result;
    }

    private void awaitFinished() throws Exception {
        await(() -> !button("generate-description").isDisabled()
                && !node("generation-progress").isVisible());
    }

    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            if (fx(condition)) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertTrue(fx(condition), "UI condition did not become true before deadline");
    }

    private <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(() -> {
            if (stage != null) {
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
            }
            return action.call();
        });
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private record PendingAttempt(String title, Condition condition, CompletableFuture<LlmClient.Result> result) {
    }

    private static final class FakeClient implements LlmClient {
        private final BlockingQueue<PendingAttempt> attempts = new LinkedBlockingQueue<>();
        private final AtomicInteger preparations = new AtomicInteger();

        @Override
        public CompletableFuture<Result> generate(String title, Condition condition) {
            return prepare(title, condition).thenCompose(prepared -> prepared.dispatch().result());
        }

        @Override
        public CompletableFuture<PreparedRequest> prepare(String title, Condition condition) {
            preparations.incrementAndGet();
            return CompletableFuture.completedFuture(beforeSend -> {
                beforeSend.run();
                CompletableFuture<Result> result = new CompletableFuture<>();
                attempts.add(new PendingAttempt(title, condition, result));
                return new LlmClient.Attempt() {
                    @Override
                    public CompletableFuture<Result> result() {
                        return result;
                    }

                    @Override
                    public boolean wasDispatched() {
                        return true;
                    }
                };
            });
        }

        PendingAttempt next() throws InterruptedException {
            var attempt = attempts.poll(10, TimeUnit.SECONDS);
            assertNotNull(attempt, "Expected a dispatched fake request");
            return attempt;
        }
    }
}
