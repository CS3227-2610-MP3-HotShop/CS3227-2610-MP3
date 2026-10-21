package hotshop.repository;

import java.sql.Connection;
import java.sql.SQLException;

/** Durable ownership and activation state for known image objects. */
public final class ImageObjectRepository {
    public void reserve(Connection connection, String namespace, String filename, String sha256, int byteSize,
            long now) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO image_objects(namespace, filename, sha256, "
                + "byte_size, state, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', ?, ?) "
                + "ON CONFLICT(namespace, filename) DO NOTHING")) {
            statement.setString(1, namespace);
            statement.setString(2, filename);
            statement.setString(3, sha256);
            statement.setInt(4, byteSize);
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    public void activate(Connection connection, String namespace, String filename, long now) throws SQLException {
        updateState(connection, namespace, filename, "ACTIVE", now);
    }

    public void retire(Connection connection, String namespace, String filename, long now) throws SQLException {
        updateState(connection, namespace, filename, "RETIRED", now);
    }

    private void updateState(Connection connection, String namespace, String filename, String state, long now)
            throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE image_objects SET state = ?, updated_at = ? "
                + "WHERE namespace = ? AND filename = ?")) {
            statement.setString(1, state);
            statement.setLong(2, now);
            statement.setString(3, namespace);
            statement.setString(4, filename);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Image object reservation is unavailable");
            }
        }
    }

    public String sha256(Connection connection, String namespace, String filename) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT sha256 FROM image_objects WHERE namespace = ? AND filename = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, filename);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    public boolean isRetired(Connection connection, String namespace, String filename) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT state = 'RETIRED' FROM image_objects "
                + "WHERE namespace = ? AND filename = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, filename);
            try (var rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }

    public void finish(Connection connection, String namespace, String filename) throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM image_objects WHERE namespace = ? AND filename = ? AND state = 'RETIRED'")) {
            statement.setString(1, namespace);
            statement.setString(2, filename);
            statement.executeUpdate();
        }
    }
}
