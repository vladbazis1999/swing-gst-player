package org.player.soft;

import org.freedesktop.gstreamer.swing.GstVideoComponent;

import javax.swing.*;
import java.awt.*;

public class PlayerView extends JFrame {
    private final GstVideoComponent videoComponent;
    private final JButton openButton = new JButton("Открыть");
    private final JButton playButton = new JButton("Пуск");
    private final JButton pauseButton = new JButton("Пауза");
    private final JButton stopButton = new JButton("Стоп");

    private final JSlider timelineSlider = new JSlider(0, 1000, 0);
    private final JLabel timeLabel = new JLabel("00:00 / 00:00");
    private final JSlider volumeSlider = new JSlider(0, 100, 80);

    public PlayerView() {
        super("GStreamer Swing Video Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        videoComponent = new GstVideoComponent();
        videoComponent.setBackground(Color.BLACK);
        add(videoComponent, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JPanel timelinePanel = new JPanel(new BorderLayout(10, 0));
        timelineSlider.setEnabled(false);
        timelinePanel.add(timelineSlider, BorderLayout.CENTER);
        timelinePanel.add(timeLabel, BorderLayout.EAST);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controlsPanel.add(openButton);
        controlsPanel.add(playButton);
        controlsPanel.add(pauseButton);
        controlsPanel.add(stopButton);
        controlsPanel.add(new JLabel("Громкость:"));
        volumeSlider.setPreferredSize(new Dimension(100, 25));
        controlsPanel.add(volumeSlider);

        bottomPanel.add(timelinePanel, BorderLayout.NORTH);
        bottomPanel.add(controlsPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    public GstVideoComponent getVideoComponent() { return videoComponent; }
    public JButton getOpenButton() { return openButton; }
    public JButton getPlayButton() { return playButton; }
    public JButton getPauseButton() { return pauseButton; }
    public JButton getStopButton() { return stopButton; }
    public JSlider getTimelineSlider() { return timelineSlider; }
    public JLabel getTimeLabel() { return timeLabel; }
    public JSlider getVolumeSlider() { return volumeSlider; }
}
