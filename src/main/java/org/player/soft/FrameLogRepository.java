package org.player.soft;

import java.io.File;
import java.sql.*;
import java.util.List;

public class FrameLogRepository implements AutoCloseable {
    private static final String INSERT_SQL = "INSERT INTO frame_logs (unix_time_ms) VALUES (?)";
    private final Connection connection;

    public FrameLogRepository(File videoFile) throws SQLException {
        this.connection = openConnection(videoFile);
        initSchema();
    }

    private Connection openConnection(File videoFile) throws SQLException {
        String fileName = videoFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        File dbFile = new File(videoFile.getParentFile(), baseName + ".db");

        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS frame_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    unix_time_ms INTEGER NOT NULL
                );
            """);
        }
        connection.setAutoCommit(false);
    }

    public void saveBatch(List<Long> timestamps) throws SQLException {
        if (timestamps.isEmpty() || connection.isClosed()) return;

        try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
            for (Long ts : timestamps) {
                ps.setLong(1, ts);
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
