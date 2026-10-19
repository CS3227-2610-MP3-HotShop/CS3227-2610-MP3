package hotshop.database;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** PostgreSQL connection values kept separate from printable diagnostic information. */
public final class DatabaseConfig {
    private final String url;
    private final String username;
    private final String password;

    /** Stores required connection values without opening a connection. */
    public DatabaseConfig(String url, String username, String password) {
        requireValue(url);
        requireValue(username);
        requireValue(password);
        parseUrl(url);
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /** Loads and validates server configuration without opening a connection. */
    public static DatabaseConfig fromEnvironment(Map<String, String> environment) {
        if (environment == null) {
            throw invalidConfiguration();
        }
        DatabaseConfig config = new DatabaseConfig(environment.get("HOTSHOP_DB_URL"),
                environment.get("HOTSHOP_DB_USER"), environment.get("HOTSHOP_DB_PASSWORD"));
        config.connectionProperties();
        return config;
    }

    /** Validates TLS before connection and supplies credentials and bounded driver timeouts. */
    Properties connectionProperties() {
        URI parsed = parseUrl(url);
        Map<String, String> parameters = parameters(parsed);
        String host = parsed.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equals(host) || "::1".equals(host);
        if (!local && !"verify-full".equals(parameters.get("sslmode"))) {
            throw invalidConfiguration();
        }
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "30");
        return properties;
    }

    private static void requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw invalidConfiguration();
        }
    }

    private static URI parseUrl(String value) {
        if (!value.startsWith("jdbc:postgresql://")) {
            throw invalidConfiguration();
        }
        try {
            URI parsed = new URI(value.substring("jdbc:".length()));
            if (parsed.getHost() == null || parsed.getUserInfo() != null || parsed.getFragment() != null
                    || parsed.getPath() == null || parsed.getPath().length() < 2
                    || parsed.getPort() > 65535 || parsed.getPort() == 0) {
                throw invalidConfiguration();
            }
            parameters(parsed);
            return parsed;
        } catch (URISyntaxException failure) {
            throw invalidConfiguration();
        }
    }

    private static Map<String, String> parameters(URI parsed) {
        Map<String, String> parameters = new HashMap<>();
        if (parsed.getRawQuery() == null) {
            return parameters;
        }
        try {
            for (String entry : parsed.getRawQuery().split("&", -1)) {
                String[] pair = entry.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                if (key.isBlank() || key.equals("user") || key.contains("password")
                        || key.equals("sslmode") && !pair[0].equals("sslmode")
                        || parameters.putIfAbsent(key, value) != null
                        || key.equals("connecttimeout") && !value.equals("10")
                        || key.equals("sockettimeout") && !value.equals("30")) {
                    throw invalidConfiguration();
                }
            }
        } catch (IllegalArgumentException failure) {
            throw invalidConfiguration();
        }
        return parameters;
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("Invalid PostgreSQL configuration; check database settings and TLS");
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public String toString() {
        return "DatabaseConfig[redacted]";
    }
}
