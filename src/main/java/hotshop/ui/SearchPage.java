package hotshop.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.ListingDetails;
import hotshop.llm.SocSearchClient;
import hotshop.service.ListingSearch;
import hotshop.service.ListingSort;
import hotshop.service.ListingWithSeller;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Explicit search submission with independent draft controls and last-submitted results. */
final class SearchPage {
    private final MarketplaceUi app;
    private final UiPage page;
    private final UiForm form = new UiForm();
    private final TextField query;
    private final TextField minimumPrice;
    private final TextField maximumPrice;
    private final ComboBox<Category> category = choices(Category.values());
    private final ComboBox<ListingSort> sort = choices(ListingSort.values());
    private final List<CheckBox> conditions = new ArrayList<>();
    private final VBox results = new VBox(16);
    private List<ListingWithSeller> loaded = List.of();
    private boolean hasLoaded;
    private final Label aiStatus = UiControls.label("", "muted");
    private final VBox aiPreview = new VBox(6);
    private final VBox aiPanel = new VBox(8);
    private final Button aiSearchButton;
    private final Button aiApplyButton;
    private final Button aiDismissButton;
    private int aiRequestId;
    private boolean aiPending;
    private ListingSearch aiProposal;

    SearchPage(MarketplaceUi app) {
        this.app = app;
        page = app.page("Search");
        ListingSearch draft = app.searchState.getDraft();
        var text = app.searchState.getDraftText();
        query = form.text("search-query", "Search listing titles", text.query());
        category.setId("search-category");
        category.setPromptText("All categories");
        category.setValue(draft.category());
        form.field("Category", category);
        FlowPane conditionControls = new FlowPane(12, 8);
        for (Condition value : Condition.values()) {
            CheckBox box = new CheckBox(UiControls.title(value));
            box.setUserData(value);
            box.setSelected(draft.conditions() != null && draft.conditions().contains(value));
            conditions.add(box);
            conditionControls.getChildren().add(box);
        }
        form.field("Conditions (none selected means all)", conditionControls);
        minimumPrice = form.text("minimum-price", "Minimum price (SGD)", text.minimumPrice());
        maximumPrice = form.text("maximum-price", "Maximum price (SGD)", text.maximumPrice());
        VBox filterFields = new VBox(12);
        var filterNodes = new ArrayList<>(form.getChildren().subList(1, form.getChildren().size()));
        form.getChildren().removeAll(filterNodes);
        filterFields.getChildren().addAll(filterNodes);
        TitledPane filters = new TitledPane("Filters", filterFields);
        filters.setId("search-filters");
        filters.setAnimated(false);
        filters.setExpanded(false);
        form.getChildren().add(filters);
        sort.setId("search-sort");
        sort.setValue(draft.sort() == null ? ListingSort.NEWEST : draft.sort());
        form.field("Sort", sort);
        sort.setOnAction(event -> {
            app.searchState.sort(sort.getValue());
            renderResults();
        });
        var submit = UiControls.primary("Search", "search-submit", this::submit);
        query.setOnAction(event -> submit.fire());
        aiStatus.setId("ai-search-status");
        aiPreview.setId("ai-search-preview");
        aiPreview.setVisible(false);
        aiPreview.setManaged(false);
        aiApplyButton = UiControls.primary("Apply filters", "ai-search-apply", this::applyProposal);
        aiDismissButton = UiControls.button("Dismiss", "ai-search-dismiss", this::dismissProposal);
        aiApplyButton.setVisible(false);
        aiApplyButton.setManaged(false);
        aiDismissButton.setVisible(false);
        aiDismissButton.setManaged(false);
        aiSearchButton = UiControls.button("AI Search", "ai-search", this::requestProposal);
        aiSearchButton.setDisable(app.aiSearch == null);
        page.setHeadingExtras(aiSearchButton);
        if (app.aiSearch == null) {
            aiStatus.setText("AI Search is unavailable. Configure the service to use it.");
        }
        form.getChildren().add(UiControls.actions(submit,
                UiControls.button("Clear Filters", "search-clear", this::clearFilters),
                UiControls.button("Refresh", "search-refresh", this::refresh)));
        aiPanel.getChildren().addAll(aiStatus, aiPreview, UiControls.actions(aiApplyButton, aiDismissButton));
        page.getChildren().add(2, aiPanel);
        installAiInvalidationListeners();
        page.body.getChildren().addAll(form, results);
        page.setLeaving(() -> {
            invalidateAi("AI search was cancelled because you left Search.");
            saveDraft();
            app.searchState.setScrollPosition(app.scroll().getVvalue());
        });
        if (app.searchState.getSubmitted().isPresent()) {
            refresh();
        } else {
            Label guidance = UiControls.label("Search for an item, or press Search to browse all listings.", "muted");
            guidance.setId("search-guidance");
            results.getChildren().add(guidance);
        }
    }

    static <T extends Enum<T>> ComboBox<T> choices(T[] values) {
        ComboBox<T> choice = new ComboBox<>();
        choice.getItems().addAll(values);
        choice.setMaxWidth(Double.MAX_VALUE);
        choice.setConverter(new StringConverter<>() {
            /**
             * Formats an enum option for display in the choice control.
             *
             * @param value the option, or null for an empty label
             * @return the option label, or an empty string for null
             */
            @Override
            public String toString(T value) {
                return value == null ? "" : UiControls.title(value);
            }

            /**
             * Rejects text conversion because users must choose an existing option.
             *
             * @param text the typed text, which is not supported
             * @return no value; this operation always throws
             * @throws UnsupportedOperationException always; free-text conversion is unsupported
             */
            @Override
            public T fromString(String text) {
                throw new UnsupportedOperationException("Choose an existing option");
            }
        });
        return choice;
    }

    private boolean saveDraft() {
        form.clearErrors();
        Long minimum = form.cents("minimum-price", true);
        Long maximum = form.cents("maximum-price", true);
        if (minimum != null && minimum > ListingDetails.MAX_PRICE_CENTS) {
            form.reject("minimum-price", "Maximum permitted amount is S$1,000,000.00.");
        }
        if (maximum != null && maximum > ListingDetails.MAX_PRICE_CENTS) {
            form.reject("maximum-price", "Maximum permitted amount is S$1,000,000.00.");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            form.reject("maximum-price", "Maximum must be at least the minimum.");
        }
        Set<Condition> selected = conditions.stream().filter(CheckBox::isSelected)
                .map(box -> (Condition) box.getUserData()).collect(Collectors.toSet());
        app.searchState.setDraft(new ListingSearch(query.getText(), category.getValue(), selected,
                minimum, maximum, sort.getValue()));
        app.searchState.setDraftText(new SearchState.DraftText(query.getText(),
                form.value("minimum-price"), form.value("maximum-price")));
        return form.isValid();
    }

    private void submit() {
        invalidateAi("");
        if (saveDraft()) {
            app.searchState.submit();
            app.searchState.setScrollPosition(0);
            refresh();
        } else {
            ((TitledPane) form.lookup("#search-filters")).setExpanded(true);
        }
    }

    private void clearFilters() {
        invalidateAi("");
        category.setValue(null);
        conditions.forEach(box -> box.setSelected(false));
        form.clear("minimum-price");
        form.clear("maximum-price");
        form.clearErrors();
    }

    private void refresh() {
        invalidateAi("");
        app.searchState.getSubmitted().ifPresent(criteria -> {
            results.getChildren().clear();
            hasLoaded = false;
            page.load(() -> app.runtime.getListings().searchListings(criteria), values -> {
                loaded = values;
                hasLoaded = true;
                renderResults();
                Platform.runLater(() -> {
                    if (app.isCurrent(page)) {
                        app.scroll().applyCss();
                        app.scroll().layout();
                        app.scroll().setVvalue(app.searchState.getScrollPosition());
                    }
                });
            });
        });
    }

    private void installAiInvalidationListeners() {
        query.textProperty().addListener((observable, oldValue, newValue) -> invalidateAi(""));
        minimumPrice.textProperty().addListener((observable, oldValue, newValue) -> invalidateAi(""));
        maximumPrice.textProperty().addListener((observable, oldValue, newValue) -> invalidateAi(""));
        category.valueProperty().addListener((observable, oldValue, newValue) -> invalidateAi(""));
        sort.valueProperty().addListener((observable, oldValue, newValue) -> invalidateAi(""));
        conditions.forEach(box -> box.selectedProperty().addListener(
                (observable, oldValue, newValue) -> invalidateAi("")));
    }

    private void requestProposal() {
        if (app.aiSearch == null || aiPending) {
            return;
        }
        String input = query.getText().strip();
        if (input.isEmpty() || input.codePointCount(0, input.length()) > 1_000) {
            invalidateAi("Enter a request of 1 to 1,000 characters before selecting AI Search.");
            return;
        }
        clearAiPreview();
        int requestId = ++aiRequestId;
        UUID sessionId = app.userId();
        aiPending = true;
        aiSearchButton.setDisable(true);
        aiStatus.getStyleClass().setAll("muted");
        aiStatus.setText("Preparing an AI search preview…");
        try {
            var response = app.aiSearch.apply(input);
            if (response == null) {
                finishProposal(requestId, sessionId, null, new IllegalStateException());
            } else {
                response.whenComplete((result, failure) -> {
                    Runnable finish = () -> finishProposal(requestId, sessionId, result, failure);
                    if (Platform.isFxApplicationThread()) {
                        finish.run();
                    } else {
                        Platform.runLater(finish);
                    }
                });
            }
        } catch (RuntimeException failure) {
            finishProposal(requestId, sessionId, null, failure);
        }
    }

    private void finishProposal(int requestId, UUID sessionId, SocSearchClient.Result result, Throwable failure) {
        if (requestId != aiRequestId || !app.isCurrent(page) || !sessionId.equals(app.userId())) {
            return;
        }
        aiPending = false;
        aiSearchButton.setDisable(false);
        if (failure != null || !(result instanceof SocSearchClient.Proposed proposed)
                || proposed.search() == null) {
            clearAiPreview();
            aiStatus.getStyleClass().setAll("error");
            aiStatus.setText("AI Search could not create a preview. Edit your request and select AI Search to retry.");
            return;
        }
        aiProposal = proposed.search();
        showProposal(aiProposal);
        aiStatus.getStyleClass().setAll("muted");
        aiStatus.setText("Review the proposed filters. Your current search has not changed.");
    }

    private void showProposal(ListingSearch proposal) {
        aiPreview.getChildren().setAll(
                UiControls.label("Title: " + valueOrAll(proposal.titleText()), "muted"),
                UiControls.label("Category: " + valueOrAll(proposal.category() == null
                        ? null : UiControls.title(proposal.category())), "muted"),
                UiControls.label("Conditions: " + (proposal.conditions() == null || proposal.conditions().isEmpty()
                        ? "All" : proposal.conditions().stream().map(UiControls::title).sorted()
                                .collect(Collectors.joining(", "))), "muted"),
                UiControls.label("Minimum: " + amountOrAll(proposal.minPriceCents()), "muted"),
                UiControls.label("Maximum: " + amountOrAll(proposal.maxPriceCents()), "muted"),
                UiControls.label("Sort: " + valueOrAll(proposal.sort() == null
                        ? null : UiControls.title(proposal.sort())), "muted"));
        aiPreview.setVisible(true);
        aiPreview.setManaged(true);
        aiApplyButton.setVisible(true);
        aiApplyButton.setManaged(true);
        aiDismissButton.setVisible(true);
        aiDismissButton.setManaged(true);
    }

    private String valueOrAll(String value) {
        return value == null || value.isBlank() ? "All" : value;
    }

    private String amountOrAll(Long cents) {
        return cents == null ? "All" : UiControls.money(cents);
    }

    private void applyProposal() {
        if (aiProposal == null || !app.isCurrent(page)) {
            return;
        }
        ListingSearch proposal = aiProposal;
        clearAiPreview();
        query.setText(proposal.titleText() == null ? "" : proposal.titleText());
        category.setValue(proposal.category());
        conditions.forEach(box -> box.setSelected(proposal.conditions() != null
                && proposal.conditions().contains(box.getUserData())));
        minimumPrice.setText(amountOrEmpty(proposal.minPriceCents()));
        maximumPrice.setText(amountOrEmpty(proposal.maxPriceCents()));
        sort.setValue(proposal.sort() == null ? ListingSort.NEWEST : proposal.sort());
        submit();
    }

    private String amountOrEmpty(Long cents) {
        return cents == null ? "" : java.math.BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private void dismissProposal() {
        invalidateAi("");
    }

    private void invalidateAi(String message) {
        aiRequestId++;
        aiPending = false;
        if (aiSearchButton != null && app.aiSearch != null) {
            aiSearchButton.setDisable(false);
        }
        clearAiPreview();
        if (!message.isEmpty()) {
            aiStatus.setText(message);
        } else if (app.aiSearch != null) {
            aiStatus.setText("");
        }
    }

    private void clearAiPreview() {
        aiProposal = null;
        aiPreview.getChildren().clear();
        aiPreview.setVisible(false);
        aiPreview.setManaged(false);
        aiApplyButton.setVisible(false);
        aiApplyButton.setManaged(false);
        aiDismissButton.setVisible(false);
        aiDismissButton.setManaged(false);
    }

    private void renderResults() {
        if (app.searchState.getSubmitted().isEmpty() || !hasLoaded) {
            return;
        }
        List<ListingWithSeller> ordered = new ArrayList<>(loaded);
        ordered.sort(Comparator.comparing(ListingWithSeller::listing,
                app.searchState.getSubmitted().orElseThrow().order()));
        Label count = UiControls.label(ordered.size() + (ordered.size() == 1 ? " listing" : " listings"), "muted");
        count.setId("results-count");
        results.getChildren().setAll(count);
        if (ordered.isEmpty()) {
            results.getChildren().add(UiControls.label("No listings match your search.", "muted"));
        } else {
            var grid = ListingCards.grid();
            ordered.forEach(value -> grid.getChildren().add(ListingCards.card(app, value, null)));
            results.getChildren().add(grid);
        }
    }
}
