package hotshop;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/** Process-local owner that lends independently releasable leases for one data directory. */
final class ApplicationRuntimeDirectoryLocks {
    private final Map<Path, Entry> entries = new HashMap<>();

    synchronized Lease acquire(Path directory) throws IOException {
        Path key = directory.toAbsolutePath().normalize();
        Files.createDirectories(key);
        key = key.toRealPath();
        Entry entry = entries.get(key);
        if (entry == null) {
            FileChannel channel = FileChannel.open(key.resolve("application.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock;
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException failure) {
                    throw new IOException("HotShop is already using this data directory", failure);
                }
                if (lock == null) {
                    throw new IOException("HotShop is already using this data directory");
                }
                entry = new Entry(channel, lock);
                entries.put(key, entry);
            } catch (IOException | RuntimeException failure) {
                channel.close();
                throw failure;
            }
        }
        entry.references++;
        return new Lease(this, key, entry);
    }

    private synchronized void release(Path key, Entry expected) throws IOException {
        if (entries.get(key) != expected) {
            return;
        }
        expected.references--;
        if (expected.references == 0) {
            entries.remove(key);
            try {
                expected.lock.release();
            } finally {
                expected.channel.close();
            }
        }
    }

    static final class Lease implements AutoCloseable {
        private final ApplicationRuntimeDirectoryLocks owner;
        private final Path key;
        private final Entry entry;
        private boolean closed;

        private Lease(ApplicationRuntimeDirectoryLocks owner, Path key, Entry entry) {
            this.owner = owner;
            this.key = key;
            this.entry = entry;
        }

        @Override
        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                owner.release(key, entry);
            }
        }
    }

    private static final class Entry {
        private final FileChannel channel;
        private final FileLock lock;
        private int references;

        Entry(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }
    }
}
