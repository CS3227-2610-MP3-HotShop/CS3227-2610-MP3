package hotshop.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.ListingDetails;

/** Validates an AI-generated proposal against the supported listing-search contract. */
public final class AiSearchProposalValidator {
    private static final String INVALID_PROPOSAL_MESSAGE = "Invalid AI search proposal";
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "titleText", "category", "conditions", "minPriceCents", "maxPriceCents", "sort");
    private static final BigInteger MAX_PRICE_CENTS = BigInteger.valueOf(ListingDetails.MAX_PRICE_CENTS);

    private AiSearchProposalValidator() {
    }

    /**
     * Converts a proposal to listing-search criteria.
     *
     * @param proposal untrusted proposal fields
     * @return validated listing-search criteria
     * @throws IllegalArgumentException if the proposal is invalid
     */
    public static ListingSearch validate(Map<?, ?> proposal) {
        try {
            return validateProposal(proposal);
        } catch (RuntimeException exception) {
            throw invalidProposal();
        }
    }

    private static ListingSearch validateProposal(Map<?, ?> proposal) {
        if (proposal == null) {
            throw invalidProposal();
        }
        for (Object key : proposal.keySet()) {
            if (!(key instanceof String stringKey) || !ALLOWED_KEYS.contains(stringKey)) {
                throw invalidProposal();
            }
        }

        String titleText = validateTitle(proposal.get("titleText"));
        Category category = parseEnum(Category.class, proposal.get("category"));
        Set<Condition> conditions = parseConditions(proposal.get("conditions"));
        Long minimum = parsePrice(proposal.get("minPriceCents"));
        Long maximum = parsePrice(proposal.get("maxPriceCents"));
        ListingSort sort = parseEnum(ListingSort.class, proposal.get("sort"));
        if (minimum != null && maximum != null && minimum > maximum) {
            throw invalidProposal();
        }
        return new ListingSearch(titleText, category, conditions, minimum, maximum,
                sort == null ? ListingSort.NEWEST : sort);
    }

    private static String validateTitle(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String title)) {
            throw invalidProposal();
        }
        String strippedTitle = title.strip();
        if (strippedTitle.codePointCount(0, strippedTitle.length()) > 120) {
            throw invalidProposal();
        }
        return strippedTitle.isEmpty() ? null : strippedTitle;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String name)) {
            throw invalidProposal();
        }
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException exception) {
            throw invalidProposal();
        }
    }

    private static Set<Condition> parseConditions(Object value) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof List<?> values)) {
            throw invalidProposal();
        }
        List<Condition> parsed = new ArrayList<>();
        for (Object condition : values) {
            parsed.add(parseEnum(Condition.class, condition));
        }
        return Set.copyOf(new HashSet<>(parsed));
    }

    private static Long parsePrice(Object value) {
        if (value == null) {
            return null;
        }
        BigInteger price;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            price = BigInteger.valueOf(((Number) value).longValue());
        } else if (value instanceof BigInteger bigInteger) {
            price = bigInteger;
        } else {
            throw invalidProposal();
        }
        if (price.signum() < 0 || price.compareTo(MAX_PRICE_CENTS) > 0) {
            throw invalidProposal();
        }
        return price.longValue();
    }

    private static IllegalArgumentException invalidProposal() {
        return new IllegalArgumentException(INVALID_PROPOSAL_MESSAGE);
    }
}
