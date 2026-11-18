package org.player.soft;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        GStreamerInitializer.init(args);

        SwingUtilities.invokeLater(() -> {
            setupLookAndFeel();

            PlayerView view = new PlayerView();
            FrameLoggerRepository repository = new FrameLoggerRepository();
            new PlayerController(view, repository);

            view.setVisible(true);
        });
    }

    private static void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception _) {
        }
    }
}
