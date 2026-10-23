package hotshop.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable cleanup queue, partitioned by image namespace so each feature only processes
 * its own files; references are checked again before deleting any image.
 */
public final class ImageCleanupRepository {
    /**
     * Records an idempotent cleanup request in the caller's transaction.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param namespace the image namespace whose cleanup queue is used
     * @param filename the storage-relative image filename
     * @throws SQLException if database access fails
     */
    public void schedule(Connection connection, String namespace, String filename) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO image_cleanup(namespace, filename) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            statement.setString(1, namespace);
            statement.setString(2, filename);
            statement.executeUpdate();
        }
    }

    /**
     * Removes a handled request after cleanup or discovery of a live reference.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param namespace the image namespace whose cleanup queue is used
     * @param filename the storage-relative image filename
     * @throws SQLException if database access fails
     */
    public void finish(Connection connection, String namespace, String filename) throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM image_cleanup WHERE namespace = ? AND filename = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, filename);
            statement.executeUpdate();
        }
    }

    /**
     * Reads one namespace's pending filenames without deleting files or changing the queue.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param namespace the image namespace whose cleanup queue is used
     * @return the filenames awaiting cleanup in this namespace
     * @throws SQLException if database access fails
     */
    public Set<String> getPending(Connection connection, String namespace) throws SQLException {
        return new HashSet<>(getPendingBatch(connection, namespace, null, 32));
    }

    /** Reads a bounded slice of the cleanup queue after a fair per-runtime cursor. */
    public List<String> getPendingBatch(Connection connection, String namespace, String afterFilename, int limit)
            throws SQLException {
        List<String> result = new ArrayList<>();
        String cursorClause = afterFilename == null ? "" : " AND filename > ?";
        try (var statement = connection.prepareStatement("SELECT filename FROM image_cleanup "
                + "WHERE namespace = ?" + cursorClause + " ORDER BY filename LIMIT ?")) {
            statement.setString(1, namespace);
            int limitParameter = 2;
            if (afterFilename != null) {
                statement.setString(2, afterFilename);
                limitParameter++;
            }
            statement.setInt(limitParameter, limit);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
            }
        }
        return List.copyOf(result);
    }
}
