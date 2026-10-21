package hotshop.storage;

import java.net.URI;
import java.util.Map;

/** Validated server-side Supabase Storage settings. */
public final class SupabaseStorageConfig {
    public static final String LISTING_BUCKET_SETTING = "HOTSHOP_LISTING_IMAGE_BUCKET";
    public static final String PROFILE_BUCKET_SETTING = "HOTSHOP_PROFILE_IMAGE_BUCKET";
    private final URI projectUrl;
    private final String secretKey;
    private final String listingBucket;
    private final String profileBucket;

    public SupabaseStorageConfig(URI projectUrl, String secretKey, String listingBucket, String profileBucket) {
        this(projectUrl, secretKey, listingBucket, profileBucket, false);
    }

    private SupabaseStorageConfig(URI projectUrl, String secretKey, String listingBucket, String profileBucket,
            boolean fixture) {
        boolean loopbackFixture = fixture && projectUrl != null && "http".equalsIgnoreCase(projectUrl.getScheme())
                && projectUrl.getHost() != null && ("localhost".equalsIgnoreCase(projectUrl.getHost())
                        || "127.0.0.1".equals(projectUrl.getHost()));
        if (projectUrl == null || !loopbackFixture && !"https".equalsIgnoreCase(projectUrl.getScheme())
                || projectUrl.getHost() == null || projectUrl.getRawUserInfo() != null
                || projectUrl.getRawQuery() != null || projectUrl.getRawFragment() != null
                || !"/".equals(projectUrl.getPath()) || secretKey == null || secretKey.isBlank()
                || !"listingImages".equals(listingBucket) || !"profileImages".equals(profileBucket)) {
            throw invalid();
        }
        this.projectUrl = projectUrl;
        this.secretKey = secretKey;
        this.listingBucket = listingBucket;
        this.profileBucket = profileBucket;
    }

    static SupabaseStorageConfig localFixture(URI projectUrl) {
        return new SupabaseStorageConfig(projectUrl, "test-secret", "listingImages", "profileImages", true);
    }

    public static SupabaseStorageConfig fromEnvironment(Map<String, String> environment) {
        try {
            return new SupabaseStorageConfig(URI.create(environment.get("SUPABASE_URL")),
                    environment.get("SUPABASE_SECRET_KEY"), environment.get(LISTING_BUCKET_SETTING),
                    environment.get(PROFILE_BUCKET_SETTING));
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    public URI projectUrl() {
        return projectUrl;
    }

    public String secretKey() {
        return secretKey;
    }

    public String listingBucket() {
        return listingBucket;
    }

    public String profileBucket() {
        return profileBucket;
    }

    public String bucket(String namespace) {
        return switch (namespace) {
            case "listings" -> listingBucket;
            case "profiles" -> profileBucket;
            default -> throw new IllegalArgumentException("Unknown image namespace");
        };
    }

    @Override
    public String toString() {
        return "SupabaseStorageConfig[redacted]";
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Supabase image storage settings; check operator configuration");
    }
}
