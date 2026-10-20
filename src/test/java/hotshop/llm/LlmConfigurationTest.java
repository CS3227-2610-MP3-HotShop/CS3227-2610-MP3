package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.ApplicationRuntime;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.service.ListingDraft;

class LlmConfigurationTest {
    private static final String ENDPOINT_KEY = "SOCLAAS_BASE_URL";
    private static final String TOKEN_KEY = "SOCLAAS_API_KEY";
    private static final String MODEL_KEY = "SOCLAAS_MODEL";
    private static final String ENDPOINT = "https://gateway.invalid/custom/v1?route=a=b&mode=private";
    private static final String TOKEN = "synthetic-config-canary==";
    private static final String MODEL = "synthetic-model";

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"", "'", "\""})
    void load_validQuoteStyles_preservesExactSettings(String quote) throws Exception {
        write("  " + ENDPOINT_KEY + " = " + quote + ENDPOINT + quote + "  \n"
                + TOKEN_KEY + "=" + quote + TOKEN + quote + "\n"
                + MODEL_KEY + "=" + quote + MODEL + quote + "\n");
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, TOKEN, MODEL);
    }

    @Test
    void load_utf8CommentsAndUnrelatedKeys_ignoresUnrelatedValues() throws Exception {
        write("# Unicode comment \u4e2d\u6587\n\nUNRELATED='unmatched\n  # ignored comment\n"
                + settings(ENDPOINT, TOKEN, "\u4e2d\u6587-model"));
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, TOKEN, "\u4e2d\u6587-model");
    }

    @Test
    void load_quotedLiteralContents_doesNotExpandOrUnescape() throws Exception {
        String literalModel = "  $HOME ${NAME} $(echo synthetic) `command` C:\\models\\new # literal  ";
        write(settings(ENDPOINT, TOKEN, "'" + literalModel + "'"));
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, TOKEN, literalModel);
    }

    @Test
    void load_unquotedInlineHash_preservesLiteralValue() throws Exception {
        write(settings(ENDPOINT, TOKEN, "model#literal"));
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, TOKEN, "model#literal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"model'quote", "model\"quote"})
    void load_unquotedInteriorQuote_isUnavailable(String model) throws Exception {
        write(settings(ENDPOINT, TOKEN, model));
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'model\"quote'", "\"model'quote\""})
    void load_quotedOppositeInteriorQuote_preservesLiteralValue(String model) throws Exception {
        write(settings(ENDPOINT, TOKEN, model));
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, TOKEN, model.substring(1, model.length() - 1));
    }

    @Test
    void load_crlfLineEndings_acceptsSettings() throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL).replace("\n", "\r\n"));
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, TOKEN, MODEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost:8443/route?x=a%2Fb", "https://192.168.1.2/custom",
        "https://gateway.invalid"})
    void load_approvedHttpsDestinations_preservesExactUri(String endpoint) throws Exception {
        write(settings(endpoint, TOKEN, MODEL));
        assertSettings(LlmConfiguration.load(directory), endpoint, TOKEN, MODEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {ENDPOINT_KEY, TOKEN_KEY, MODEL_KEY})
    void load_missingRequiredKey_isUnavailable(String key) throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL).lines()
                .filter(line -> !line.startsWith(key + "=")).reduce("", (text, line) -> text + line + "\n"));
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {ENDPOINT_KEY, TOKEN_KEY, MODEL_KEY})
    void load_duplicateRequiredKey_isUnavailable(String key) throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL) + key + "='another-value'\n");
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'unterminated", "\"unterminated", "'mismatched\"", "\"mismatched'",
        "'closed'junk", "\"closed\"junk", "'line\nbreak'", "'carriage\rreturn'", "'nul\u0000value'"})
    void load_invalidRequiredValueSyntax_isUnavailable(String model) throws Exception {
        write(settings(ENDPOINT, TOKEN, model));
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @Test
    void load_malformedStandaloneLine_isUnavailable() throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL) + "malformed standalone line\n");
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://gateway.invalid/path", "/relative", "gateway.invalid/path",
        "https:///missing-host", "https://user:password@gateway.invalid/path",
        "https://gateway.invalid/path#fragment", "https://gateway.invalid/a b", "https:opaque"})
    void load_invalidEndpoint_isUnavailable(String endpoint) throws Exception {
        write(settings(endpoint, TOKEN, MODEL));
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void load_invalidBearerToken_isUnavailable(String token) throws Exception {
        write(settings(ENDPOINT, "'" + token + "'", MODEL));
        assertUnavailable(LlmConfiguration.load(directory));
    }

    static Stream<String> invalidTokens() {
        return Stream.of("", " ", " leading", "trailing ", "embedded space", "tab\tvalue", "line\nvalue",
                "return\rvalue", "nul\u0000value", "delete\u007fvalue", "nbsp\u00a0value",
                "em\u2003space", "control\u0085value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"synthetic\u0100token", "synthetic\ud83d\ude00token"})
    void load_nonHeaderSafeBearerToken_isUnavailable(String token) throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.newBuilder(URI.create(ENDPOINT)).header("Authorization", "Bearer " + token));
        write(settings(ENDPOINT, token, MODEL));
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @Test
    void load_headerSafeLatinOneBearerToken_preservesExactValue() throws Exception {
        String token = "synthetic\u00fftoken";
        var request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + token).build();
        assertEquals("Bearer " + token, request.headers().firstValue("Authorization").orElseThrow());
        write(settings(ENDPOINT, token, MODEL));
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, token, MODEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "''", "\"\"", "' \t '", "'\u2003'"})
    void load_blankModel_isUnavailable(String model) throws Exception {
        write(settings(ENDPOINT, TOKEN, model));
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @Test
    void load_missingFile_isUnavailableWithoutCreatingFiles() throws Exception {
        assertUnavailable(LlmConfiguration.load(directory));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    void load_missingWorkingDirectory_isUnavailableWithoutCreatingDirectory() {
        Path missing = directory.resolve("missing");
        assertUnavailable(LlmConfiguration.load(missing));
        assertFalse(Files.exists(missing));
    }

    @Test
    void load_directoryInsteadOfFile_isUnavailableWithoutChangingDirectory() throws Exception {
        Files.createDirectory(directory.resolve(".env"));
        assertUnavailable(LlmConfiguration.load(directory));
        assertTrue(Files.isDirectory(directory.resolve(".env")));
    }

    @Test
    void load_malformedUtf8_isUnavailable() throws Exception {
        Files.write(directory.resolve(".env"), new byte[] {(byte) 0xc3, (byte) 0x28});
        assertUnavailable(LlmConfiguration.load(directory));
    }

    @Test
    void load_fileChangedAndDeleted_keepsImmutableSnapshot() throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL));
        LlmConfiguration first = LlmConfiguration.load(directory);
        write(settings("https://replacement.invalid/next", "replacement-canary", "replacement-model"));
        assertSettings(first, ENDPOINT, TOKEN, MODEL);
        assertSettings(LlmConfiguration.load(directory), "https://replacement.invalid/next",
                "replacement-canary", "replacement-model");
        Files.delete(directory.resolve(".env"));
        assertSettings(first, ENDPOINT, TOKEN, MODEL);
    }

    @Test
    void load_previouslyUnavailableSnapshot_doesNotReloadAfterFileAppears() throws Exception {
        LlmConfiguration first = LlmConfiguration.load(directory);
        write(settings(ENDPOINT, TOKEN, MODEL));
        assertUnavailable(first);
        assertSettings(LlmConfiguration.load(directory), ENDPOINT, TOKEN, MODEL);
    }

    @Test
    void toString_availableConfiguration_redactsPrivateSettings() throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL));
        LlmConfiguration configuration = LlmConfiguration.load(directory);
        assertTrue(configuration.isAvailable());
        assertRedacted(configuration.toString());
    }

    @Test
    void load_missingConfiguration_manualListingStillPersists() throws Exception {
        assertUnavailable(LlmConfiguration.load(directory));
        assertUnavailable(loadEnvironment(Map.of(TOKEN_KEY, TOKEN)));
        Path data = directory.resolve("manual-data");
        java.util.UUID listingId;
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            runtime.getAccounts().register("manualuser", "Sample1!", "Manual User").join();
            runtime.getAccounts().login("manualuser", "Sample1!").join();
            var draft = new ListingDraft("Manual item", "Handwritten description", Category.OTHER,
                    100, Condition.GOOD, "Campus");
            listingId = runtime.getListings().createListing(draft, List.of()).join().listing().getId();
        }
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            runtime.getAccounts().login("manualuser", "Sample1!").join();
            var saved = runtime.getListings().getMyListings().join().getFirst().listing().listing();
            assertEquals(listingId, saved.getId());
            assertEquals("Handwritten description", saved.getDetails().description());
        }
        assertFalse(Files.exists(directory.resolve(".env")));
    }

    @Test
    void loadFromEnvironment_completeSettingsWithoutFile_preservesExactSettingsWithoutWrites() throws Exception {
        assertSettings(loadEnvironment(environmentSettings()), ENDPOINT, TOKEN, MODEL);
        try (var files = Files.list(directory)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    void loadFromEnvironment_completeSettingsWithoutWorkingDirectory_bypassesFallbackPath() {
        assertSettings(LlmConfiguration.loadFromEnvironment(null, LlmConfigurationTest::environmentSettings),
                ENDPOINT, TOKEN, MODEL);
    }

    @Test
    void loadFromEnvironment_noKeysAndNoWorkingDirectory_isUnavailable() {
        assertUnavailable(LlmConfiguration.loadFromEnvironment(null, Map::of));
    }

    @Test
    void loadFromEnvironment_conflictingFile_usesOnlyEnvironmentAndPreservesFile() throws Exception {
        String original = settings("https://file.invalid/route", "file-token", "file-model");
        write(original);
        assertSettings(loadEnvironment(environmentSettings()), ENDPOINT, TOKEN, MODEL);
        assertEquals(original, Files.readString(directory.resolve(".env")));
    }

    @Test
    void loadFromEnvironment_malformedFile_bypassesFileParsing() throws Exception {
        Files.write(directory.resolve(".env"), new byte[] {(byte) 0xc3, (byte) 0x28});
        assertSettings(loadEnvironment(environmentSettings()), ENDPOINT, TOKEN, MODEL);
    }

    @Test
    void loadFromEnvironment_directoryInsteadOfFile_bypassesFileReading() throws Exception {
        Files.createDirectory(directory.resolve(".env"));
        assertSettings(loadEnvironment(environmentSettings()), ENDPOINT, TOKEN, MODEL);
        assertTrue(Files.isDirectory(directory.resolve(".env")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loadFromEnvironment_noRequiredKeys_usesValidFile(boolean unrelatedKey) throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL));
        Map<String, String> environment = unrelatedKey ? Map.of("UNRELATED", "ignored") : Map.of();
        assertSettings(loadEnvironment(environment), ENDPOINT, TOKEN, MODEL);
    }

    @Test
    void loadFromEnvironment_noRequiredKeysAndMissingFile_isUnavailableWithoutWrites() throws Exception {
        assertUnavailable(loadEnvironment(Map.of("UNRELATED", "ignored")));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    void loadFromEnvironment_noRequiredKeysAndInvalidFile_isUnavailable() throws Exception {
        write("malformed file");
        assertUnavailable(loadEnvironment(Map.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void loadFromEnvironment_partialRequiredKeySubset_doesNotMixOrFallBack(int keyMask) throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL));
        List<String> keys = List.of(ENDPOINT_KEY, TOKEN_KEY, MODEL_KEY);
        Map<String, String> environment = new HashMap<>(environmentSettings());
        for (int index = 0; index < keys.size(); index++) {
            if ((keyMask & (1 << index)) == 0) {
                environment.remove(keys.get(index));
            }
        }
        assertUnavailable(loadEnvironment(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {ENDPOINT_KEY, TOKEN_KEY, MODEL_KEY})
    void loadFromEnvironment_presentNullValue_doesNotFallBack(String key) throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL));
        Map<String, String> environment = new HashMap<>(environmentSettings());
        environment.put(key, null);
        assertUnavailable(loadEnvironment(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {ENDPOINT_KEY, TOKEN_KEY, MODEL_KEY})
    void loadFromEnvironment_presentEmptyValue_doesNotFallBack(String key) throws Exception {
        assertInvalidEnvironment(key, "");
    }

    @ParameterizedTest
    @ValueSource(strings = {ENDPOINT_KEY, TOKEN_KEY, MODEL_KEY})
    void loadFromEnvironment_presentBlankValue_doesNotFallBack(String key) throws Exception {
        assertInvalidEnvironment(key, " \t\u2003");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://gateway.invalid/path", "/relative", "https:///missing-host",
        "https://user:password@gateway.invalid/path", "https://gateway.invalid/path#fragment",
        "https://gateway.invalid/a b", "https:opaque", " https://gateway.invalid/path "})
    void loadFromEnvironment_invalidEndpoint_doesNotFallBack(String endpoint) throws Exception {
        assertInvalidEnvironment(ENDPOINT_KEY, endpoint);
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void loadFromEnvironment_invalidBearerToken_doesNotFallBack(String token) throws Exception {
        assertInvalidEnvironment(TOKEN_KEY, token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"synthetic\u0100token", "synthetic\ud83d\ude00token"})
    void loadFromEnvironment_nonHeaderSafeBearerToken_doesNotFallBack(String token) throws Exception {
        assertInvalidEnvironment(TOKEN_KEY, token);
    }

    @Test
    void loadFromEnvironment_headerSafeLatinOneBoundary_preservesExactToken() {
        Map<String, String> environment = new HashMap<>(environmentSettings());
        String token = "synthetic\u00fftoken";
        environment.put(TOKEN_KEY, token);
        assertSettings(loadEnvironment(environment), ENDPOINT, token, MODEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"model\rreturn", "model\nline", "model\u0000nul"})
    void loadFromEnvironment_modelWithForbiddenControl_doesNotFallBack(String model) throws Exception {
        assertInvalidEnvironment(MODEL_KEY, model);
    }

    @Test
    void loadFromEnvironment_literalValues_doesNotTrimUnquoteExpandOrUnescape() {
        String token = "'synthetic=$HOME\\literal\"'";
        String model = "  '\"$HOME ${NAME} $(echo synthetic) `command` C:\\models\\new # = literal'  ";
        Map<String, String> environment = new HashMap<>(environmentSettings());
        environment.put(TOKEN_KEY, token);
        environment.put(MODEL_KEY, model);
        assertSettings(loadEnvironment(environment), ENDPOINT, token, model);
    }

    @Test
    void loadFromEnvironment_deniedAccess_isSafelyUnavailableWithoutFileFallback() throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL));
        assertUnavailable(LlmConfiguration.loadFromEnvironment(directory, () -> {
            throw new SecurityException(ENDPOINT + TOKEN + MODEL);
        }));
    }

    @Test
    void loadFromEnvironment_changedEnvironmentAndFile_retainsSingleReadSnapshot() throws Exception {
        Map<String, String> environment = new HashMap<>(environmentSettings());
        AtomicInteger reads = new AtomicInteger();
        LlmConfiguration configuration = LlmConfiguration.loadFromEnvironment(directory, () -> {
            reads.incrementAndGet();
            return environment;
        });
        environment.clear();
        write(settings("https://replacement.invalid", "replacement-token", "replacement-model"));
        assertSettings(configuration, ENDPOINT, TOKEN, MODEL);
        assertEquals(1, reads.get());
        assertRedacted(configuration.toString());
    }

    @Test
    void loadFromEnvironment_unavailableSnapshot_doesNotReloadWhenEnvironmentBecomesComplete() {
        Map<String, String> environment = new HashMap<>(Map.of(TOKEN_KEY, TOKEN));
        LlmConfiguration configuration = loadEnvironment(environment);
        environment.putAll(environmentSettings());
        assertUnavailable(configuration);
        assertSettings(loadEnvironment(environment), ENDPOINT, TOKEN, MODEL);
    }

    private void assertInvalidEnvironment(String key, String value) throws Exception {
        write(settings(ENDPOINT, TOKEN, MODEL));
        Map<String, String> environment = new HashMap<>(environmentSettings());
        environment.put(key, value);
        assertUnavailable(loadEnvironment(environment));
    }

    private LlmConfiguration loadEnvironment(Map<String, String> environment) {
        return LlmConfiguration.loadFromEnvironment(directory, () -> environment);
    }

    private static Map<String, String> environmentSettings() {
        return Map.of(ENDPOINT_KEY, ENDPOINT, TOKEN_KEY, TOKEN, MODEL_KEY, MODEL);
    }

    private void write(String contents) throws Exception {
        Files.writeString(directory.resolve(".env"), contents, StandardCharsets.UTF_8);
    }

    private static String settings(String endpoint, String token, String model) {
        return ENDPOINT_KEY + "=" + endpoint + "\n" + TOKEN_KEY + "=" + token + "\n"
                + MODEL_KEY + "=" + model + "\n";
    }

    private static void assertSettings(LlmConfiguration configuration, String endpoint, String token, String model) {
        assertTrue(configuration.isAvailable());
        assertEquals(URI.create(endpoint), configuration.endpoint());
        assertEquals(endpoint, configuration.endpoint().toString());
        assertEquals(token, configuration.bearerToken());
        assertEquals(model, configuration.model());
    }

    private static void assertUnavailable(LlmConfiguration configuration) {
        assertFalse(configuration.isAvailable());
        assertRedacted(configuration.toString());
        assertSafeFailure(assertThrows(IllegalStateException.class, configuration::endpoint));
        assertSafeFailure(assertThrows(IllegalStateException.class, configuration::bearerToken));
        assertSafeFailure(assertThrows(IllegalStateException.class, configuration::model));
    }

    private static void assertSafeFailure(IllegalStateException failure) {
        assertNotNull(failure.getMessage());
        assertRedacted(failure.getMessage());
        assertEquals(null, failure.getCause());
    }

    private static void assertRedacted(String text) {
        assertNotNull(text);
        assertFalse(text.contains(ENDPOINT));
        assertFalse(text.contains(TOKEN));
        assertFalse(text.contains(MODEL));
        assertFalse(text.contains("password"));
        assertFalse(text.contains("unterminated"));
    }
}
