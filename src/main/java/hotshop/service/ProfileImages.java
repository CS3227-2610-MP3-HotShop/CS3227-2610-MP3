package hotshop.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.time.Clock;

import hotshop.database.Database;
import hotshop.model.User;
import hotshop.repository.UserRepository;
import hotshop.storage.ImageStorage;

/** Saves profile image references; file handling is delegated to the profile image namespace. */
final class ProfileImages {
    private final Database database;
    private final UserRepository users;
    private final ManagedImages images;
    private final Clock clock;

    ProfileImages(Database database, UserRepository users, ImageStorage storage, Clock clock) {
        this.database = database;
        this.users = users;
        this.clock = clock;
        images = new ManagedImages(database, storage, "profiles", ImageStorage.PROFILE_LIMITS,
                users::getReferencedImages, clock);
    }

    User replace(UUID userId, Path source) throws SQLException, IOException {
        try {
            try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
                String name = images.importImage(source, guard);
                return save(userId, name, guard);
            }
        } finally {
            images.recoverSafely();
        }
    }

    User remove(UUID userId) throws SQLException {
        User user;
        try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
            user = save(userId, null, guard);
        }
        images.recoverSafely();
        return user;
    }

    void recover() throws SQLException, IOException {
        images.recover();
    }

    byte[] read(UUID userId) throws SQLException, IOException {
        String filename = database.executeTransaction(connection -> users.findById(connection, userId)
                .orElseThrow(() -> new ServiceException(ServiceException.Code.NOT_FOUND, "Profile was not found"))
                .getProfileImage().orElseThrow(() -> new ServiceException(ServiceException.Code.NOT_FOUND,
                        "No profile image is available")));
        return images.read(filename);
    }

    private User save(UUID userId, String name, Database.ImageLifecycleGuard guard) throws SQLException {
        return guard.transaction(connection -> {
            User old = users.findById(connection, userId).orElseThrow(() ->
                    new ServiceException(ServiceException.Code.NOT_FOUND, "Profile was not found"));
            User updated = User.restore(userId, old.getUsername(), old.getDisplayName(), name,
                    old.getPreferredPickupLocation().orElse(null));
            users.updateProfile(connection, updated);
            if (name != null) {
                images.activate(connection, name, clock.millis());
            }
            if (old.getProfileImage().isPresent()) {
                images.schedule(connection, old.getProfileImage().orElseThrow());
            }
            return updated;
        });
    }
}
