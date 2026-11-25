package org.player.soft;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.event.SeekFlags;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

public class PlayerController {
    private final PlayerView view;
    private final AsyncFrameLogProcessor frameLogProcessor;
    private final PlayBin playBin;
    private final Timer uiUpdateTimer;
    private boolean isSeeking = false;

    public PlayerController(PlayerView view, AsyncFrameLogProcessor frameLogProcessor) {
        this.view = view;
        this.frameLogProcessor = frameLogProcessor;

        this.playBin = new PlayBin("VideoPlayer");
        this.playBin.setVideoSink(view.getVideoComponent().getElement());
        this.playBin.setVolume(view.getVolumeSlider().getValue() / 100.0);

        setupFrameListener();
        setupBusListeners();
        setupUiListeners();

        this.uiUpdateTimer = new Timer(200, e -> updateTimeline());
    }

    private void setupFrameListener() {
        AppSink appSink = (AppSink) view.getVideoComponent().getElement();
        appSink.set("emit-signals", true);

        appSink.connect((AppSink.NEW_SAMPLE) _ -> {
            long currentUnixTimeMs = System.currentTimeMillis();
            frameLogProcessor.onFrameTimestamp(currentUnixTimeMs);
            return FlowReturn.OK;
        });
    }

    private void setupBusListeners() {
        Bus bus = playBin.getBus();
        bus.connect((Bus.EOS) source -> SwingUtilities.invokeLater(this::stopPlayback));
        bus.connect((Bus.ERROR) (source, code, message) ->
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(view, "Ошибка GStreamer: " + message, "Ошибка", JOptionPane.ERROR_MESSAGE)
            )
        );
    }

    private void setupUiListeners() {
        view.getOpenButton().addActionListener(e -> openFile());
        view.getPlayButton().addActionListener(e -> startPlayback());
        view.getPauseButton().addActionListener(e -> pausePlayback());
        view.getStopButton().addActionListener(e -> stopPlayback());

        view.getVolumeSlider().addChangeListener(e -> {
            double volume = view.getVolumeSlider().getValue() / 100.0;
            playBin.setVolume(volume);
        });

        JSlider slider = view.getTimelineSlider();
        slider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isSeeking = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                seekToSliderPosition();
                isSeeking = false;
            }
        });

        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                uiUpdateTimer.stop();
                playBin.stop();
                playBin.dispose();
                frameLogProcessor.close();
            }
        });
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Видеофайлы (*.mp4, *.mkv, *.avi, *.mov)", "mp4", "mkv", "avi", "mov", "wmv", "flv"
        ));

        if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            loadVideo(selectedFile);
        }
    }

    private void loadVideo(File file) {
        stopPlayback();
        try {
            frameLogProcessor.startForVideo(file);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view, "Не удалось создать БД: " + ex.getMessage(), "Ошибка БД", JOptionPane.ERROR_MESSAGE);
            return;
        }

        playBin.setURI(file.toURI());
        view.setTitle("GStreamer Swing Video Player — " + file.getName());
        view.getTimelineSlider().setEnabled(true);
        startPlayback();
    }

    private void startPlayback() {
        playBin.play();
        uiUpdateTimer.start();
    }

    private void pausePlayback() {
        playBin.pause();
    }

    private void stopPlayback() {
        uiUpdateTimer.stop();
        playBin.ready();
        view.getTimelineSlider().setValue(0);
        updateTimeLabel(0, playBin.queryDuration(Format.TIME));
    }

    private void seekToSliderPosition() {
        long duration = playBin.queryDuration(Format.TIME);
        if (duration > 0) {
            double ratio = view.getTimelineSlider().getValue() / 1000.0;
            long targetNs = (long) (duration * ratio);
            playBin.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT), targetNs);
            updateTimeLabel(targetNs, duration);
        }
    }

    private void updateTimeline() {
        if (isSeeking) return;

        long duration = playBin.queryDuration(Format.TIME);
        long position = playBin.queryPosition(Format.TIME);

        if (duration > 0 && position >= 0) {
            int sliderValue = (int) ((position * 1000) / duration);
            view.getTimelineSlider().setValue(sliderValue);
            updateTimeLabel(position, duration);
        }
    }

    private void updateTimeLabel(long posNs, long durNs) {
        String posStr = formatNanos(posNs);
        String durStr = (durNs > 0) ? formatNanos(durNs) : "00:00";
        view.getTimeLabel().setText(posStr + " / " + durStr);
    }

    private String formatNanos(long nanos) {
        long totalSeconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
