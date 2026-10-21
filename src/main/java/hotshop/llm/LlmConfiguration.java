package hotshop.llm;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Immutable startup configuration for private generation settings. */
public final class LlmConfiguration {
    private static final String ENDPOINT_KEY = "SOCLAAS_BASE_URL";
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
            Map<String, String> settings = parse(contents);
            if (!settings.keySet().containsAll(REQUIRED_KEYS)) {
                return unavailable();
            }
            URI endpoint = URI.create(settings.get(ENDPOINT_KEY));
            String token = settings.get(TOKEN_KEY);
            String model = settings.get(MODEL_KEY);
            if (!validEndpoint(endpoint) || token.isEmpty() || token.codePoints().anyMatch(
                    codePoint -> codePoint > MAX_HEADER_CODE_POINT
                            || isSpace(codePoint) || Character.isISOControl(codePoint))
                    || model.codePoints().allMatch(LlmConfiguration::isSpace)) {
                return unavailable();
            }
            return new LlmConfiguration(endpoint, token, model);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            // Configuration failure disables generation; exception payloads may contain private values.
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
