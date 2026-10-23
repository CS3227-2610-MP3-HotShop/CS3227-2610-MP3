package hotshop.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hotshop.database.Database;
import hotshop.database.DatabaseConfig;
import hotshop.repository.ImageObjectRepository;
import hotshop.repository.ListingRepository;
import hotshop.repository.UserRepository;

/** Explicit operator import/verification tool for existing image references. */
public final class ImageImportCommand {
    private static final int REPORT_LIMIT = 20;
    private static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;

    private ImageImportCommand() {
    }

    public static void main(String[] arguments) {
        try {
            run(arguments, System.getenv(), Clock.systemUTC());
        } catch (Exception failure) {
            System.err.println("Image import failed. Check operator settings, source inventory, and private buckets.");
            System.exit(1);
        }
    }

    static void run(String[] arguments, Map<String, String> environment, Clock clock) throws IOException, SQLException {
        Database database = new Database(DatabaseConfig.fromEnvironment(environment));
        SupabaseStorageConfig config = SupabaseStorageConfig.fromEnvironment(environment);
        try (SupabaseImageObjectStore storage = new SupabaseImageObjectStore(config)) {
            runWith(arguments, database, config, storage, clock);
        }
    }

    static void runWith(String[] arguments, Database database, SupabaseStorageConfig config,
            SupabaseImageObjectStore storage, Clock clock) throws IOException, SQLException {
        Options options = Options.parse(arguments);
        database.validateSchema();
        UserRepository users = new UserRepository();
        ListingRepository listings = new ListingRepository();
        Set<String> profileNames = database.executeTransaction(users::getReferencedImages);
        Set<String> listingNames = database.executeTransaction(listings::getReferencedImages);
        Map<Key, byte[]> sourceImages = readSources(options, profileNames, listingNames);
        ImageObjectRepository objects = new ImageObjectRepository();
        List<String> problems = new ArrayList<>();
        try (Database.ImageLifecycleGuard guard = options.verifyOnly() ? null
                : database.acquireImageLifecycleGuard()) {
            for (Map.Entry<Key, byte[]> entry : sourceImages.entrySet()) {
                Key key = entry.getKey();
                byte[] expected = entry.getValue();
                byte[] remote = storage.readIfExists(key.namespace(), key.filename(), limit(key.namespace()));
                if (remote != null && !digest(remote).equals(digest(expected))) {
                    addProblem(problems, "remote conflict " + key.display());
                    continue;
                }
                if (remote == null && options.verifyOnly()) {
                    addProblem(problems, "missing remote " + key.display());
                    continue;
                }
                if (!options.verifyOnly()) {
                    String hash = digest(expected);
                    String registered = guard.transaction(connection -> objects.sha256(connection,
                            key.namespace(), key.filename()));
                    if (registered != null && !registered.equals(hash)) {
                        addProblem(problems, "manifest conflict " + key.display());
                        continue;
                    }
                    guard.transaction(connection -> {
                        objects.reserve(connection, key.namespace(), key.filename(), hash, expected.length,
                                clock.millis());
                        return null;
                    });
                }
                if (remote == null) {
                    storage.put(key.namespace(), key.filename(), expected,
                            "image/" + ImageStorage.detectFormat(expected));
                }
            }
            if (!problems.isEmpty()) {
                throw new IOException("Image verification found " + problems.size() + " issue(s): "
                        + String.join("; ", problems));
            }
            if (!options.verifyOnly()) {
                guard.transaction(connection -> {
                    for (Key key : sourceImages.keySet()) {
                        objects.activate(connection, key.namespace(), key.filename(), clock.millis());
                    }
                    Database.activateImageStorageBindings(connection, config, clock.millis());
                    return null;
                });
            }
        }
        System.out.println((options.verifyOnly() ? "Verified" : "Imported") + " " + sourceImages.size()
                + " referenced images across both private namespaces.");
    }

    private static Map<Key, byte[]> readSources(Options options, Set<String> profiles, Set<String> listings)
            throws IOException {
        Map<Key, byte[]> result = new HashMap<>();
        List<String> problems = new ArrayList<>();
        long total = 0;
        for (String namespace : List.of("profiles", "listings")) {
            Set<String> names = "profiles".equals(namespace) ? profiles : listings;
            ImageStorage.Limits limits = "profiles".equals(namespace)
                    ? ImageStorage.PROFILE_LIMITS : ImageStorage.LISTING_LIMITS;
            for (String name : names) {
                Key key = new Key(namespace, name);
                byte[] selected = null;
                for (Path root : List.of(options.windowsRoot(), options.dockerRoot())) {
                    Path path = root.resolve(namespace).resolve(name);
                    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    try {
                        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                            throw new IOException("not a regular file");
                        }
                        if (Files.size(path) > limits.maximumBytes()) {
                            throw new IOException("source file exceeds its namespace size limit");
                        }
                        byte[] bytes = Files.readAllBytes(path);
                        ImageStorage.validateBytes(bytes, limits);
                        total += bytes.length;
                        if (total > MAX_TOTAL_BYTES) {
                            throw new IOException("source inventory exceeds the 1 GiB operator limit");
                        }
                        if (selected != null && !digest(selected).equals(digest(bytes))) {
                            throw new IOException("conflicting source copies");
                        }
                        selected = bytes;
                    } catch (IOException | IllegalArgumentException failure) {
                        addProblem(problems, key.display() + " (source invalid or conflicting)");
                    }
                }
                if (selected == null) {
                    addProblem(problems, key.display() + " (missing source)");
                } else {
                    result.put(key, selected);
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IOException("Image source inventory found " + problems.size() + " issue(s): "
                    + String.join("; ", problems));
        }
        return result;
    }

    private static int limit(String namespace) {
        return "profiles".equals(namespace) ? ImageStorage.PROFILE_LIMITS.maximumBytes()
                : ImageStorage.LISTING_LIMITS.maximumBytes();
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void addProblem(List<String> problems, String problem) {
        if (problems.size() < REPORT_LIMIT) {
            problems.add(problem);
        }
    }

    private record Key(String namespace, String filename) {
        private Key {
            if (!Set.of("profiles", "listings").contains(namespace)
                    || filename == null || !filename.matches(
                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpeg)")) {
                throw new IllegalArgumentException("Invalid database image reference");
            }
        }

        String display() {
            return namespace + "/" + filename;
        }
    }

    private record Options(Path windowsRoot, Path dockerRoot, boolean verifyOnly) {
        static Options parse(String[] arguments) {
            Path windows = null;
            Path docker = null;
            boolean verifyOnly = false;
            for (int index = 0; index < arguments.length; index++) {
                switch (arguments[index]) {
                    case "--windows-root" -> windows = Path.of(requireNext(arguments, ++index));
                    case "--docker-root" -> docker = Path.of(requireNext(arguments, ++index));
                    case "--verify-only" -> verifyOnly = true;
                    default -> throw new IllegalArgumentException("Invalid image import option");
                }
            }
            if (windows == null || docker == null) {
                throw new IllegalArgumentException("Both read-only image source roots are required");
            }
            return new Options(windows.toAbsolutePath().normalize(), docker.toAbsolutePath().normalize(), verifyOnly);
        }

        private static String requireNext(String[] arguments, int index) {
            if (index >= arguments.length || arguments[index].startsWith("--")) {
                throw new IllegalArgumentException("Image source path is missing");
            }
            return arguments[index];
        }
    }
}
