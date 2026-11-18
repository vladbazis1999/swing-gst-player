package org.player.soft;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FrameLoggerRepository implements AutoCloseable {
    private Connection connection;
    private final BlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread loggerThread = new Thread(task, "SQLite-Frame-Logger");
        loggerThread.setDaemon(true);
        return loggerThread;
    });
    private volatile boolean running = false;

    public void initForVideo(File videoFile) throws SQLException {
        closeCurrentConnection();

        connection = openConnection(videoFile);

        // Сначала выполняем PRAGMA и создание таблицы (пока autoCommit = true)
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

        startBackgroundWriter();
    }

    public void logFrameTimestamp(long unixTimeMs) {
        if (running) {
            timestampQueue.offer(unixTimeMs);
        }
    }

    private void startBackgroundWriter() {
        running = true;
        writerExecutor.submit(() -> {
            List<Long> batch = new ArrayList<>(100);
            var sql = "INSERT INTO frame_logs (unix_time_ms) VALUES (?)";

            while (running || !timestampQueue.isEmpty()) {
                try {
                    Long first = timestampQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (first != null) {
                        batch.add(first);
                        timestampQueue.drainTo(batch, 99);
                        flushBatch(sql, batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void flushBatch(String sql, List<Long> batch) throws SQLException {
        if (connection == null || connection.isClosed()) return;
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (Long ts : batch) {
                preparedStatement.setLong(1, ts);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            connection.commit();
        }
    }

    private Connection openConnection(File videoFile) throws SQLException {
        var fileName = videoFile.getName();
        var dotIndex = fileName.lastIndexOf('.');
        var baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        var dbFile = new File(videoFile.getParentFile(), baseName + ".db");

        var url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        return DriverManager.getConnection(url);
    }

    private void closeCurrentConnection() {
        running = false;
        while (!timestampQueue.isEmpty()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        closeCurrentConnection();
        writerExecutor.shutdownNow();
    }
}
