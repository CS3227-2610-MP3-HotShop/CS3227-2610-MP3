package hotshop.ui;

import hotshop.storage.ImageStorage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns server-side temporary files created from browser-uploaded image bytes. */
final class ImageUploadFiles {
    private static final Set<Path> OWNED = ConcurrentHashMap.newKeySet();

    private ImageUploadFiles() {
    }

    static Path copyUploadedFile(File uploaded, ImageStorage.Limits limits) throws IOException {
        return copyUploadedFile(uploaded, limits, Path.of(System.getProperty("java.io.tmpdir")));
    }

    static Path copyUploadedFile(File uploaded, ImageStorage.Limits limits, Path temporaryDirectory)
            throws IOException {
        Path temporary = null;
        try {
            long size = uploaded == null ? 0 : Files.size(uploaded.toPath());
            if (size < 1 || size > limits.maximumBytes()) {
                throw new IllegalArgumentException("Image file exceeds the size limit");
            }
            temporary = Files.createTempFile(temporaryDirectory, "hotshop-image-", ".upload");
            byte[] bytes;
            try (var input = Files.newInputStream(uploaded.toPath())) {
                bytes = input.readNBytes(limits.maximumBytes() + 1);
            }
            if (bytes.length < 1 || bytes.length > limits.maximumBytes()) {
                throw new IllegalArgumentException("Image file exceeds the size limit");
            }
            Files.write(temporary, bytes);
            ImageStorage.validateImage(temporary, limits);
            OWNED.add(temporary);
            return temporary;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } finally {
            try {
                if (uploaded != null) {
                    Files.deleteIfExists(uploaded.toPath());
                }
            } catch (IOException cleanupFailure) {
                // A failed temporary cleanup does not change the image selection result.
            }
            if (temporary != null && !OWNED.contains(temporary)) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    // A failed cleanup is confined to the process temporary directory.
                }
            }
        }
    }

    static void release(Path selected) {
        if (selected != null && OWNED.remove(selected)) {
            try {
                Files.deleteIfExists(selected);
            } catch (IOException cleanupFailure) {
                // A failed cleanup is confined to the process temporary directory.
            }
        }
    }

    static boolean isOwned(Path path) {
        return OWNED.contains(path);
    }
}
