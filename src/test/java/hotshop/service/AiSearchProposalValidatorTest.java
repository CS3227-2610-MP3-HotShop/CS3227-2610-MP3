package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.model.Category;
import hotshop.model.Condition;

@Tag("acceptance")
class AiSearchProposalValidatorTest {
    private static final String INVALID_MESSAGE = "Invalid AI search proposal";
    private static final String SUPPLEMENTARY_CHARACTER = "\uD83D\uDE00";

    @Test
    void validate_allOptionalFields_validatesSearchCriteria() {
        ListingSearch search = validate(Map.of(
                "titleText", "  desk lamp  ",
                "category", "FURNITURE",
                "conditions", List.of("GOOD", "LIKE_NEW"),
                "minPriceCents", 10,
                "maxPriceCents", 500L,
                "sort", "PRICE_LOW_TO_HIGH"));

        assertEquals("desk lamp", search.titleText());
        assertEquals(Category.FURNITURE, search.category());
        assertEquals(Set.of(Condition.GOOD, Condition.LIKE_NEW), search.conditions());
        assertEquals(10L, search.minPriceCents());
        assertEquals(500L, search.maxPriceCents());
        assertEquals(ListingSort.PRICE_LOW_TO_HIGH, search.sort());
    }

    @Test
    void validate_emptyProposal_usesDefaults() {
        ListingSearch search = validate(Map.of());

        assertEquals(null, search.titleText());
        assertEquals(null, search.category());
        assertEquals(Set.of(), search.conditions());
        assertEquals(null, search.minPriceCents());
        assertEquals(null, search.maxPriceCents());
        assertEquals(ListingSort.NEWEST, search.sort());
        assertThrows(UnsupportedOperationException.class, () -> search.conditions().add(Condition.NEW));
    }

    @Test
    void validate_nullOptionalValues_usesDefaults() {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("titleText", null);
        proposal.put("category", null);
        proposal.put("conditions", null);
        proposal.put("minPriceCents", null);
        proposal.put("maxPriceCents", null);
        proposal.put("sort", null);

        ListingSearch search = validate(proposal);

        assertEquals(null, search.titleText());
        assertEquals(null, search.category());
        assertEquals(Set.of(), search.conditions());
        assertEquals(null, search.minPriceCents());
        assertEquals(null, search.maxPriceCents());
        assertEquals(ListingSort.NEWEST, search.sort());
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void validate_eachCategoryName_acceptsExactValue(Category category) {
        assertEquals(category, validate(Map.of("category", category.name())).category());
    }

    @ParameterizedTest
    @EnumSource(ListingSort.class)
    void validate_eachSortName_acceptsExactValue(ListingSort sort) {
        assertEquals(sort, validate(Map.of("sort", sort.name())).sort());
    }

    @ParameterizedTest
    @EnumSource(Condition.class)
    void validate_eachConditionName_acceptsExactValue(Condition condition) {
        assertEquals(Set.of(condition), validate(Map.of("conditions", List.of(condition.name()))).conditions());
    }

    @Test
    void validate_duplicateConditions_deduplicatesValues() {
        assertEquals(Set.of(Condition.GOOD), validate(Map.of("conditions", List.of("GOOD", "GOOD"))).conditions());
    }

    @Test
    void validate_emptyConditions_usesImmutableEmptySet() {
        ListingSearch search = validate(Map.of("conditions", List.of()));

        assertEquals(Set.of(), search.conditions());
        assertThrows(UnsupportedOperationException.class, () -> search.conditions().add(Condition.NEW));
    }

    @ParameterizedTest
    @MethodSource("acceptedPriceValues")
    void validate_integerPriceRepresentations_acceptsInclusiveRange(Number price) {
        ListingSearch search = validate(Map.of("minPriceCents", price, "maxPriceCents", price));

        assertEquals(price.longValue(), search.minPriceCents());
        assertEquals(price.longValue(), search.maxPriceCents());
    }

    private static List<Number> acceptedPriceValues() {
        return List.of((byte) 0, (short) 1, 2, 3L, BigInteger.valueOf(4),
                100_000_000L, BigInteger.valueOf(100_000_000));
    }

    @Test
    void validate_zeroAndMaximumPrice_acceptsBounds() {
        ListingSearch search = validate(Map.of("minPriceCents", 0, "maxPriceCents", 100_000_000));

        assertEquals(0L, search.minPriceCents());
        assertEquals(100_000_000L, search.maxPriceCents());
    }

    @Test
    void validate_equalPriceBounds_acceptsRange() {
        ListingSearch search = validate(Map.of("minPriceCents", 25, "maxPriceCents", 25));

        assertEquals(25L, search.minPriceCents());
        assertEquals(25L, search.maxPriceCents());
    }

    @Test
    void validate_reversedPriceBounds_rejectsProposal() {
        assertInvalid(Map.of("minPriceCents", 26, "maxPriceCents", 25));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void validate_blankTitle_usesNoTitleFilter(String title) {
        assertEquals(null, validate(Map.of("titleText", title)).titleText());
    }

    @Test
    void validate_titleAt120CodePoints_acceptsTrimmedTitle() {
        String title = "x".repeat(120);

        assertEquals(title, validate(Map.of("titleText", "  " + title + "  ")).titleText());
    }

    @Test
    @Tag("security")
    void validate_titleAt121CodePoints_rejectsProposal() {
        assertInvalid(Map.of("titleText", "x".repeat(121)));
    }

    @Test
    @Tag("security")
    void validate_supplementaryTitleCharacters_countsUnicodeCodePoints() {
        String title = SUPPLEMENTARY_CHARACTER.repeat(120);

        assertEquals(title, validate(Map.of("titleText", title)).titleText());
        assertInvalid(Map.of("titleText", SUPPLEMENTARY_CHARACTER.repeat(121)));
    }

    @Test
    void validate_sqlLikeTitle_preservesTextAsLiteral() {
        String title = "  %' OR 1=1; --  ";

        assertEquals("%' OR 1=1; --", validate(Map.of("titleText", title)).titleText());
    }

    @Test
    @Tag("security")
    void validate_inputMapAndConditions_doesNotMutateAndCopiesConditions() {
        List<String> inputConditions = new ArrayList<>(List.of("NEW"));
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("titleText", " desk ");
        proposal.put("conditions", inputConditions);
        Map<String, Object> originalProposal = new HashMap<>(proposal);

        ListingSearch search = validate(proposal);
        inputConditions.set(0, "POOR");
        inputConditions.add("GOOD");

        assertEquals(originalProposal.get("titleText"), proposal.get("titleText"));
        assertEquals(inputConditions, proposal.get("conditions"));
        assertEquals(Set.of(Condition.NEW), search.conditions());
        assertFalse(search.conditions().contains(Condition.POOR));
    }

    @Test
    @Tag("security")
    void validate_unknownFieldWithNullValue_rejectsProposal() {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("role", null);

        assertInvalid(proposal);
    }

    @ParameterizedTest
    @Tag("security")
    @ValueSource(strings = {"systemPrompt", "userId", "sellerId", "isAdmin", "sql", "query"})
    void validate_privilegedOrUnknownField_rejectsProposal(String key) {
        assertInvalid(Map.of(key, "ignored"));
    }

    @Test
    @Tag("security")
    void validate_nonStringKeyWithNullValue_rejectsProposal() {
        Map<Object, Object> proposal = new HashMap<>();
        proposal.put(7, null);

        assertInvalid(proposal);
    }

    @Test
    @Tag("security")
    void validate_nullProposal_rejectsProposal() {
        assertInvalid(null);
    }

    @ParameterizedTest
    @Tag("security")
    @MethodSource("invalidPriceValues")
    void validate_invalidPriceRepresentation_rejectsProposal(Object price) {
        assertInvalid(Map.of("minPriceCents", price));
    }

    private static List<Object> invalidPriceValues() {
        return List.of(-1, 100_000_001L, Long.MAX_VALUE, BigInteger.ONE.shiftLeft(100),
                1.0d, 1.0f, BigDecimal.ONE, "1", true);
    }

    @ParameterizedTest
    @Tag("security")
    @MethodSource("invalidCategoryValues")
    void validate_invalidCategoryValue_rejectsProposal(Object value) {
        assertInvalid(singleEntryAllowingNull("category", value));
    }

    private static List<Object> invalidCategoryValues() {
        return java.util.Arrays.asList(null, Category.ELECTRONICS, "electronics", " ELECTRONICS", "UNKNOWN", 1);
    }

    @ParameterizedTest
    @Tag("security")
    @MethodSource("invalidSortValues")
    void validate_invalidSortValue_rejectsProposal(Object value) {
        assertInvalid(singleEntryAllowingNull("sort", value));
    }

    private static List<Object> invalidSortValues() {
        return java.util.Arrays.asList(null, ListingSort.NEWEST, "newest", " NEWEST", "UNKNOWN", 1);
    }

    @ParameterizedTest
    @Tag("security")
    @MethodSource("invalidConditionValues")
    void validate_invalidConditionsValue_rejectsProposal(Object value) {
        assertInvalid(singleEntryAllowingNull("conditions", value));
    }

    private static List<Object> invalidConditionValues() {
        return java.util.Arrays.asList(Set.of("NEW"), "NEW", java.util.Arrays.asList((Object) null), List.of("new"),
                List.of("NEW", "UNKNOWN"), List.of(Condition.NEW), List.of(1));
    }

    @Test
    @Tag("security")
    void validate_conditionListWithNullElement_rejectsProposal() {
        List<Object> conditions = new ArrayList<>();
        conditions.add("NEW");
        conditions.add(null);

        assertInvalid(Map.of("conditions", conditions));
    }

    @ParameterizedTest
    @Tag("security")
    @MethodSource("invalidTitleValues")
    void validate_invalidTitleValue_rejectsProposal(Object value) {
        assertInvalid(singleEntryAllowingNull("titleText", value));
    }

    private static List<Object> invalidTitleValues() {
        return java.util.Arrays.asList(null, 1, true);
    }

    @Test
    @Tag("security")
    void validate_errorMessageAndCause_areSafe() {
        Map<Object, Object> proposal = new HashMap<>();
        proposal.put("private-payload", "sensitive prompt text");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AiSearchProposalValidator.validate(proposal));

        assertEquals(INVALID_MESSAGE, exception.getMessage());
        assertEquals(null, exception.getCause());
        assertFalse(exception.getMessage().contains("sensitive prompt text"));
    }

    private static ListingSearch validate(Map<?, ?> proposal) {
        return AiSearchProposalValidator.validate(proposal);
    }

    private static void assertInvalid(Map<?, ?> proposal) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AiSearchProposalValidator.validate(proposal));

        assertEquals(INVALID_MESSAGE, exception.getMessage());
        assertEquals(null, exception.getCause());
    }

    private static Map<String, Object> singleEntryAllowingNull(String key, Object value) {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put(key, value);
        return proposal;
    }
}
