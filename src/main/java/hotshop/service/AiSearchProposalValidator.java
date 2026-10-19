package hotshop.service;

import java.util.Map;

/** Validates an AI-generated proposal against the supported listing-search contract. */
public final class AiSearchProposalValidator {
    private AiSearchProposalValidator() {
    }

    /**
     * Converts a proposal to listing-search criteria.
     *
     * @param proposal untrusted proposal fields
     * @return validated listing-search criteria
     * @throws UnsupportedOperationException until the implementation arm is evaluated
     */
    public static ListingSearch validate(Map<?, ?> proposal) {
        throw new UnsupportedOperationException();
    }
}
