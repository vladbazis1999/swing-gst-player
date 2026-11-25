package org.player.soft;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AsyncFrameLogProcessor implements AutoCloseable {
    private static final int BATCH_SIZE = 100;

    private final BlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SQLite-Frame-Logger");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean acceptingFrames = false;
    private Future<?> activeWriterTask;

    public void startForVideo(File videoFile) throws SQLException {
        stopCurrentSession();

        FrameLogRepository repository = new FrameLogRepository(videoFile);
        timestampQueue.clear();
        acceptingFrames = true;

        activeWriterTask = writerExecutor.submit(() -> runWriterLoop(repository));
    }

    public void onFrameTimestamp(long unixTimeMs) {
        if (acceptingFrames) {
            timestampQueue.offer(unixTimeMs);
        }
    }

    private void runWriterLoop(FrameLogRepository repository) {
        List<Long> batch = new ArrayList<>(BATCH_SIZE);

        try (repository) {
            while (acceptingFrames || !timestampQueue.isEmpty()) {
                Long first = timestampQueue.poll(100, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    timestampQueue.drainTo(batch, BATCH_SIZE - 1);
                    repository.saveBatch(batch);
                    batch.clear();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void stopCurrentSession() {
        acceptingFrames = false;
        if (activeWriterTask != null) {
            try {
                activeWriterTask.get(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                e.printStackTrace();
            } finally {
                activeWriterTask = null;
            }
        }
    }

    @Override
    public void close() {
        stopCurrentSession();
        writerExecutor.shutdown();
    }
}
