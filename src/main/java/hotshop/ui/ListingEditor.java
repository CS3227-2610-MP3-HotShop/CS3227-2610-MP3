package hotshop.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.Listing;
import hotshop.model.ListingDetails;
import hotshop.model.OfferStatus;
import hotshop.service.DescriptionService;
import hotshop.service.ListingDraft;
import hotshop.service.ListingPhoto;
import hotshop.storage.ImageStorage;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** One atomic listing form with an ordered photo selection; imported files remain service-owned. */
final class ListingEditor {
    private final MarketplaceUi app;
    private final UiPage page;
    private final Listing original;
    private final UiForm form = new UiForm();
    private final ComboBox<Category> category = SearchPage.choices(Category.values());
    private final ComboBox<Condition> condition = SearchPage.choices(Condition.values());
    private final List<ListingPhoto> photos = new ArrayList<>();
    private final VBox photoRows = new VBox(10);
    private final Button generate = UiControls.button("Generate Description", "generate-description",
            this::generateDescription);
    private final ProgressIndicator generationProgress = new ProgressIndicator();
    private final Label generationStatus = UiControls.label("", "muted");
    private TextField title;
    private TextArea description;
    private List<String> initialFields;
    private long formRevision;
    private long generationSequence;
    private UiImages.PreparedChooser photoChooser;

    ListingEditor(MarketplaceUi app, Listing original) {
        this.app = app;
        this.original = original;
        page = app.page(original == null ? "Create Listing" : "Edit Listing");
        page.setLeaving(() -> {
            generationSequence++;
            releaseTemporaryPhotos();
        });
        if (original == null) {
            page.load(app.runtime.getAccounts()::getOwnProfile,
                    user -> build(user.getPreferredPickupLocation().orElse("")));
        } else {
            original.getImages().forEach(image -> photos.add(ListingPhoto.keep(image.filename())));
            build(original.getDetails().pickupLocation());
        }
    }

    private void build(String pickup) {
        ListingDetails details = original == null ? null : original.getDetails();
        title = form.text("title", "Title", details == null ? "" : details.title());
        description = form.area("description", "Description", details == null ? "" : details.description());
        if (original == null) {
            addGenerationControls();
        }
        form.text("price", "Asking price (SGD)", details == null ? ""
                : BigDecimal.valueOf(details.priceCents(), 2).toPlainString());
        category.setId("category");
        category.setValue(details == null ? null : details.category());
        condition.setId("condition");
        condition.setValue(details == null ? null : details.condition());
        form.field("category", "Category", category);
        form.field("condition", "Condition", condition);
        form.text("pickup-location", "Listing pickup location", pickup);
        var addPhoto = UiControls.button("Add Photo", "add-photo", () -> { });
        photoChooser = UiImages.prepareChooser(app.stage, addPhoto, ImageStorage.LISTING_LIMITS);
        page.setLeaving(photoChooser::cancel);
        addPhoto.setOnAction(event -> addPhoto());
        form.getChildren().addAll(UiControls.label(
                "Up to 10 JPEG/PNG photos; each at most 10 MiB and 4096 × 4096 pixels. No automatic resizing.", "hint"),
                photoRows, addPhoto,
                UiControls.actions(UiControls.primary("Save Listing", "save-listing", this::save),
                        UiControls.button("Cancel", "cancel-edit", app::back)));
        page.body.getChildren().add(form);
        initialFields = fields();
        title.textProperty().addListener((observable, previous, value) -> formRevision++);
        description.textProperty().addListener((observable, previous, value) -> formRevision++);
        condition.valueProperty().addListener((observable, previous, value) -> formRevision++);
        page.setDirty(() -> !initialFields.equals(fields()) || !originalPhotos().equals(photos));
        renderPhotos();
    }

    private void addGenerationControls() {
        Label notice = UiControls.label("Your title and condition are sent to an external LLM. "
                + "A successful result replaces your existing description immediately. "
                + "Review and edit the generated text before saving your listing.", "hint");
        notice.setId("generation-notice");
        generate.setAccessibleText("Generate Description");
        generationProgress.setId("generation-progress");
        generationProgress.setMaxSize(24, 24);
        generationProgress.setVisible(false);
        generationProgress.managedProperty().bind(generationProgress.visibleProperty());
        generationStatus.setId("generation-status");
        generationStatus.visibleProperty().bind(generationStatus.textProperty().isNotEmpty());
        generationStatus.managedProperty().bind(generationStatus.visibleProperty());
        form.getChildren().addAll(notice, UiControls.actions(generate, generationProgress), generationStatus);
    }

    private void generateDescription() {
        if (generate.isDisabled()) {
            return;
        }
        form.clearErrors();
        form.textLength("title", 120, true);
        if (condition.getValue() == null) {
            form.reject("condition", "Choose a condition.");
        }
        if (!form.isValid()) {
            generationMessage("Enter a valid title and choose a condition to generate a description.", "error");
            return;
        }
        generate.setDisable(true);
        generationProgress.setVisible(true);
        generationMessage("Generating description… You can keep editing or save your listing.", "muted");
        long sequence = ++generationSequence;
        long revision = formRevision;
        long authentication = app.authenticationRevision();
        try {
            app.runtime.getDescriptions().generateDescription(title.getText(), condition.getValue())
                    .whenComplete((result, failure) -> Platform.runLater(() -> app.completeGeneration(() ->
                            completeGeneration(sequence, revision, authentication, failure == null ? result : null))));
        } catch (RuntimeException failure) {
            completeGeneration(sequence, revision, authentication, null);
        }
    }

    private void completeGeneration(long sequence, long revision, long authentication,
            DescriptionService.GenerationResult result) {
        if (!app.isGenerationCurrent(page, authentication) || sequence != generationSequence) {
            return;
        }
        if (revision != formRevision) {
            generate.setDisable(false);
            generationProgress.setVisible(false);
            generationMessage("Your draft changed. Generate a new description when you are ready.", "muted");
            return;
        }
        finishGeneration(result);
    }

    private void finishGeneration(DescriptionService.GenerationResult result) {
        generate.setDisable(false);
        generationProgress.setVisible(false);
        if (result instanceof DescriptionService.Generated generated) {
            description.setText(generated.description());
            generationMessage("Description generated. Review and edit it before saving your listing.", "success");
        } else if (result instanceof DescriptionService.Failed failed) {
            generationMessage(generationFailure(failed), "error");
        } else {
            generationMessage("Description generation is unavailable. Try again or write your own description.",
                    "error");
        }
    }

    private void generationMessage(String message, String style) {
        generationStatus.getStyleClass().setAll(style);
        generationStatus.setText(message);
    }

    private static String generationFailure(DescriptionService.Failed failed) {
        return switch (failed.kind()) {
            case INVALID_INPUT -> "Enter a valid title and choose a condition to generate a description.";
            case LOGIN_REQUIRED, SESSION_CHANGED -> "Your session changed. Log in again to generate a description.";
            case IN_FLIGHT -> "A description is already being generated for your account. Wait before trying again.";
            case RATE_LIMITED -> "Generation limit reached. Try again in " + failed.retryAfterSeconds() + " seconds.";
            case TIMEOUT -> "Description generation timed out. Try again or write your own description.";
            case PROVIDER_AUTH, UNAVAILABLE, CLOSED ->
                    "Description generation is unavailable. Try again or write your own description.";
            case PROVIDER_BUSY -> "The description provider is busy. Try again later or write your own description.";
            case NETWORK -> "Could not connect to the description provider. Try again or write your own description.";
            case INVALID_RESPONSE ->
                    "The provider returned an invalid description. Try again or write your own description.";
        };
    }

    private List<ListingPhoto> originalPhotos() {
        return original == null ? List.of() : original.getImages().stream()
                .map(image -> ListingPhoto.keep(image.filename())).toList();
    }

    private List<String> fields() {
        return List.of(form.value("title"), form.value("description"), form.value("price"),
                String.valueOf(category.getValue()), String.valueOf(condition.getValue()),
                form.value("pickup-location"));
    }

    private void addPhoto() {
        if (photos.size() == Listing.MAX_IMAGES) {
            page.error(new IllegalArgumentException("A listing can have at most 10 photos."));
            return;
        }
        page.performSelection(photoChooser.choose(),
                selected -> java.util.concurrent.CompletableFuture.completedFuture(selected), selected -> {
                    if (selected != null) {
                        photos.add(ListingPhoto.add(selected));
                        renderPhotos();
                    }
                });
    }

    private void renderPhotos() {
        photoRows.getChildren().clear();
        for (int index = 0; index < photos.size(); index++) {
            int position = index;
            ListingPhoto photo = photos.get(index);
            Node image = switch (photo) {
                case ListingPhoto.Existing existing -> UiImages.displayRemote(
                        () -> app.runtime.getListings().getListingImage(original.getId(), existing.filename()),
                        100, 75);
                case ListingPhoto.NewFile added -> UiImages.display(() -> added.source(), 100, 75);
            };
            var up = UiControls.button("Move Up", "photo-up-" + index, () -> move(position, -1));
            up.setDisable(index == 0);
            var down = UiControls.button("Move Down", "photo-down-" + index, () -> move(position, 1));
            down.setDisable(index == photos.size() - 1);
            photoRows.getChildren().add(UiControls.actions(image,
                    UiControls.label("Photo " + (index + 1), "muted"), up, down,
                    UiControls.button("Remove", "photo-remove-" + index, () -> {
                        ListingPhoto removed = photos.remove(position);
                        if (removed instanceof ListingPhoto.NewFile added) {
                            UiImages.release(added.source());
                        }
                        renderPhotos();
                    })));
        }
    }

    private void move(int position, int change) {
        Collections.swap(photos, position, position + change);
        renderPhotos();
    }

    private void save() {
        form.clearErrors();
        form.textLength("title", 120, true);
        form.textLength("description", 5000, true);
        form.textLength("pickup-location", 200, true);
        Long price = form.cents("price", false);
        if (price != null && (price < 1 || price > ListingDetails.MAX_PRICE_CENTS)) {
            form.reject("price", "Enter S$0.01 to S$1,000,000.00.");
        }
        if (category.getValue() == null) {
            form.reject("category", "Choose a category.");
        }
        if (condition.getValue() == null) {
            form.reject("condition", "Choose a condition.");
        }
        if (!form.isValid()) {
            return;
        }
        ListingDraft draft = new ListingDraft(form.value("title"), form.value("description"),
                category.getValue(), price, condition.getValue(), form.value("pickup-location"));
        if (original == null) {
            persist(draft);
        } else {
            page.perform(() -> app.runtime.getOffers().getOffersForListing(original.getId()), offers -> {
                boolean hasPending = offers.stream()
                        .anyMatch(value -> value.offer().getStatus() == OfferStatus.PENDING);
                ListingDetails updated = new ListingDetails(draft.title(), draft.description(), draft.category(),
                        draft.priceCents(), draft.condition(), draft.pickupLocation());
                boolean isChanged = !updated.equals(original.getDetails()) || !photos.equals(originalPhotos());
                if (!hasPending || !isChanged || app.confirm("Save Listing",
                        "These changes will reject all pending offers on this listing.")) {
                    persist(draft);
                }
            });
        }
    }

    private void persist(ListingDraft draft) {
        page.perform(() -> original == null
                ? app.runtime.getListings().createListing(draft, List.copyOf(photos))
                : app.runtime.getListings().updateListing(original.getId(), draft, List.copyOf(photos)), saved -> {
                    page.setDirty(() -> false);
                    app.replace(() -> app.listings.details(saved.listing().getId()));
                });
    }

    private void releaseTemporaryPhotos() {
        photos.stream().filter(ListingPhoto.NewFile.class::isInstance).map(ListingPhoto.NewFile.class::cast)
                .forEach(photo -> UiImages.release(photo.source()));
    }
}
