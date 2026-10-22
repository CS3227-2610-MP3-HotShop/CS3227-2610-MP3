package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import hotshop.model.Condition;

/** Offline fixture quality checks only; no client creation or semantic model evidence. */
class LlmSecurityCorpusTest {
    @Test
    void load_securityCorpus_hasUniqueValidInputsAndRequiredCoverage() throws Exception {
        var cases = LlmSecurityCorpus.load();
        Set<String> identifiers = new HashSet<>();
        Set<String> categories = new HashSet<>();
        Set<Condition> controlConditions = new HashSet<>();
        assertEquals(26, cases.size());
        for (var item : cases) {
            assertTrue(item.id() != null && item.id().matches("[a-z0-9-]+"));
            assertTrue(identifiers.add(item.id()), "Corpus identifiers must be unique");
            assertTrue(item.title() != null && !item.title().isBlank());
            assertTrue(item.title().codePointCount(0, item.title().length()) <= 120,
                    "Each attack must reach the valid-title boundary");
            assertTrue(item.condition() != null);
            assertTrue(item.review() != null && !item.review().isBlank());
            assertTrue(item.category() != null);
            categories.add(item.category());
            if (item.control()) {
                controlConditions.add(item.condition());
            }
        }
        assertTrue(categories.containsAll(Set.of("ordinary", "benign-system", "benign-unicode", "role-spoof",
                "disclosure", "unrelated", "encoded", "unicode", "fragmented", "multilingual", "tool-action",
                "configuration")));
        assertEquals(Set.copyOf(Arrays.asList(Condition.values())), controlConditions);
        assertTrue(cases.stream().anyMatch(item -> item.control() && item.title().equals("System design textbook")));
        assertFalse(cases.stream().filter(item -> item.control()).anyMatch(item -> item.title().contains("Ignore")));
    }

    @Test
    void wordCount_publicEvaluationRule_handlesWhitespaceAndUnicodeBoundaries() {
        assertEquals(0, LlmLiveSecurityTest.wordCount("... -- 😀"));
        assertEquals(3, LlmLiveSecurityTest.wordCount("café\u00a0well-kept\n\ud801\udc00"));
        assertEquals(100, LlmLiveSecurityTest.wordCount("item ".repeat(100)));
        assertEquals(101, LlmLiveSecurityTest.wordCount("item ".repeat(101)));
    }
}
