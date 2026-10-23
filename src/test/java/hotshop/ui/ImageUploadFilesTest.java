package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hotshop.storage.ImageStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageUploadFilesTest {
    @TempDir
    Path directory;

    @Test
    void copyUploadedFile_validBrowserBytes_ownsAndReleasesServerTemporary() throws Exception {
        Path uploaded = directory.resolve("untrusted-name.jpg");
        byte[] expected = png();
        Files.write(uploaded, expected);

        Path selected = ImageUploadFiles.copyUploadedFile(uploaded.toFile(), ImageStorage.PROFILE_LIMITS, directory);

        assertFalse(Files.exists(uploaded));
        assertTrue(ImageUploadFiles.isOwned(selected));
        assertArrayEquals(expected, Files.readAllBytes(selected));
        ImageUploadFiles.release(selected);
        assertFalse(Files.exists(selected));
        assertFalse(ImageUploadFiles.isOwned(selected));
    }

    @Test
    void copyUploadedFile_invalidImage_deletesBothTemporaryCopies() throws Exception {
        Path uploaded = directory.resolve("malicious.png");
        Files.writeString(uploaded, "not image bytes");

        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadFiles.copyUploadedFile(uploaded.toFile(), ImageStorage.PROFILE_LIMITS, directory));

        assertFalse(Files.exists(uploaded));
        try (var files = Files.list(directory)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    @Test
    void copyUploadedFile_overLimit_rejectsBeforeCopying() throws Exception {
        Path uploaded = directory.resolve("too-large.png");
        Files.write(uploaded, png());

        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadFiles.copyUploadedFile(uploaded.toFile(), new ImageStorage.Limits(4, 512, 512),
                        directory));

        assertFalse(Files.exists(uploaded));
    }

    @Test
    void copyUploadedFile_sourceChangesAfterSizeCheck_boundsBytesAndCleansTemporary() throws Exception {
        Path initial = directory.resolve("initial.png");
        Path changed = directory.resolve("changed.png");
        Files.write(initial, png());
        Files.write(changed, new byte[101]);
        ImageStorage.Limits limits = new ImageStorage.Limits(100, 512, 512);
        File changingFile = new File(initial.toString()) {
            private static final long serialVersionUID = 1L;
            private int calls;

            @Override
            public Path toPath() {
                return ++calls == 1 ? initial : changed;
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> ImageUploadFiles.copyUploadedFile(changingFile, limits, directory));

        assertFalse(Files.exists(changed));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
