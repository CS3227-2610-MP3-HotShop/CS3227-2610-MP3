package hotshop;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationRuntimeDirectoryLocksTest {
    @TempDir
    Path directory;

    @Test
    void acquire_sameOwner_releasesPhysicalLockAfterLastLease() throws Exception {
        ApplicationRuntimeDirectoryLocks owner = new ApplicationRuntimeDirectoryLocks();
        var first = owner.acquire(directory);
        var second = owner.acquire(directory.resolve(".").resolve("child").getParent());
        ApplicationRuntimeDirectoryLocks otherOwner = new ApplicationRuntimeDirectoryLocks();

        assertThrows(IOException.class, () -> otherOwner.acquire(directory));
        first.close();
        assertThrows(IOException.class, () -> otherOwner.acquire(directory));
        second.close();
        try (var reopened = otherOwner.acquire(directory)) {
            assertNotNull(reopened);
        }
    }

    @Test
    void acquire_competingOwner_rejectsSameCanonicalDirectory() throws Exception {
        ApplicationRuntimeDirectoryLocks firstOwner = new ApplicationRuntimeDirectoryLocks();
        ApplicationRuntimeDirectoryLocks secondOwner = new ApplicationRuntimeDirectoryLocks();
        try (var ignored = firstOwner.acquire(directory)) {
            assertNotNull(ignored);
            assertThrows(IOException.class, () -> secondOwner.acquire(directory.toAbsolutePath()));
        }
        try (var ignored = secondOwner.acquire(directory)) {
            assertNotNull(ignored);
        }
    }
}
