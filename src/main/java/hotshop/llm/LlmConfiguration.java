package hotshop.llm;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Immutable startup configuration for private generation settings. */
public final class LlmConfiguration {
    private static final String ENDPOINT_KEY = "SOCLAAS_DESCRIPTION_URL";
    private static final String TOKEN_KEY = "SOCLAAS_API_KEY";
    private static final String MODEL_KEY = "SOCLAAS_MODEL";
    private static final Set<String> REQUIRED_KEYS = Set.of(ENDPOINT_KEY, TOKEN_KEY, MODEL_KEY);
    private static final String UNAVAILABLE_MESSAGE = "Description generation configuration is unavailable";
    private static final int MAX_HEADER_CODE_POINT = 0xff;

    private final URI endpoint;
    private final String bearerToken;
    private final String model;

    private LlmConfiguration(URI endpoint, String bearerToken, String model) {
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.model = model;
    }

    /**
     * Loads a UTF-8 .env snapshot without changing files or exposing read/parse failures.
     *
     * @param workingDirectory the trusted application working directory
     * @return immutable settings, unavailable if the file or required values are invalid
     */
    public static LlmConfiguration load(Path workingDirectory) {
        if (workingDirectory == null) {
            return unavailable();
        }
        try {
            String contents = Files.readString(workingDirectory.resolve(".env"), StandardCharsets.UTF_8);
            return fromSettings(parse(contents));
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            // Configuration failure disables generation; exception payloads may contain private values.
            return unavailable();
        }
    }

    /**
     * Loads trusted process settings, with local file fallback when no required key is present.
     *
     * @param workingDirectory the fallback file's trusted working directory
     * @return immutable validated settings, unavailable if the selected source is invalid
     */
    public static LlmConfiguration loadFromEnvironment(Path workingDirectory) {
        return loadFromEnvironment(workingDirectory, System::getenv);
    }

    static LlmConfiguration loadFromEnvironment(Path workingDirectory,
            Supplier<Map<String, String>> environment) {
        if (environment == null) {
            return unavailable();
        }
        try {
            Map<String, String> settings = environment.get();
            if (settings == null) {
                return unavailable();
            }
            if (REQUIRED_KEYS.stream().anyMatch(settings::containsKey)) {
                return fromSettings(settings);
            }
            return load(workingDirectory);
        } catch (IllegalArgumentException | SecurityException failure) {
            // Selected environment failures never expose private values or fall back to a file.
            return unavailable();
        }
    }

    /**
     * Returns whether all required generation settings are usable.
     *
     * @return whether this startup snapshot is available
     */
    public boolean isAvailable() {
        return endpoint != null;
    }

    URI endpoint() {
        requireAvailable();
        return endpoint;
    }

    String bearerToken() {
        requireAvailable();
        return bearerToken;
    }

    String model() {
        requireAvailable();
        return model;
    }

    @Override
    public String toString() {
        return "LlmConfiguration[available=" + isAvailable() + "]";
    }

    private static LlmConfiguration unavailable() {
        return new LlmConfiguration(null, null, null);
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException(UNAVAILABLE_MESSAGE);
        }
    }

    private static LlmConfiguration fromSettings(Map<String, String> settings) {
        String endpointValue = settings.get(ENDPOINT_KEY);
        String token = settings.get(TOKEN_KEY);
        String model = settings.get(MODEL_KEY);
        if (endpointValue == null || token == null || model == null
                || containsLineBreakOrNul(endpointValue) || containsLineBreakOrNul(token)
                || containsLineBreakOrNul(model)) {
            return unavailable();
        }
        URI endpoint = URI.create(endpointValue);
        if (!validEndpoint(endpoint) || token.isEmpty() || token.codePoints().anyMatch(
                codePoint -> codePoint > MAX_HEADER_CODE_POINT
                        || isSpace(codePoint) || Character.isISOControl(codePoint))
                || model.codePoints().allMatch(LlmConfiguration::isSpace)) {
            return unavailable();
        }
        return new LlmConfiguration(endpoint, token, model);
    }

    private static boolean containsLineBreakOrNul(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0;
    }

    private static Map<String, String> parse(String contents) {
        Map<String, String> settings = new HashMap<>();
        String[] lines = contents.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (index < lines.length - 1 && line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.indexOf('\r') >= 0 || line.indexOf('\0') >= 0) {
                throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
            }
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
            }
            String key = line.substring(0, separator).strip();
            if (!REQUIRED_KEYS.contains(key)) {
                continue;
            }
            if (settings.containsKey(key)) {
                throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
            }
            settings.put(key, parseValue(line.substring(separator + 1).strip()));
        }
        return settings;
    }

    private static String parseValue(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (first == '\'' || first == '"') {
            if (value.length() < 2 || last != first) {
                throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
            }
            return value.substring(1, value.length() - 1);
        }
        if (value.indexOf('\'') >= 0 || value.indexOf('"') >= 0) {
            throw new IllegalArgumentException(UNAVAILABLE_MESSAGE);
        }
        return value;
    }

    private static boolean validEndpoint(URI endpoint) {
        return endpoint.isAbsolute() && "https".equalsIgnoreCase(endpoint.getScheme())
                && endpoint.getHost() != null && endpoint.getRawUserInfo() == null && endpoint.getRawFragment() == null;
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
