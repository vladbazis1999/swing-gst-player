package org.player.soft;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Version;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class GStreamerInitializer {
    private static final String CONFIG_FILE_NAME = "config.properties";

    public static void init(String[] args) {
        Properties props = loadProperties();

        String gstBin = getRequiredProperty(props, "gstreamer.bin.path");
        String gstPlugins = getRequiredProperty(props, "gstreamer.plugins.path");
        String libsRaw = props.getProperty("gstreamer.core.libs", "");

        System.setProperty("jna.library.path", gstBin);
        System.setProperty("gstreamer.plugin.path", gstPlugins);

        if (!libsRaw.isBlank()) {
            preloadNativeLibraries(gstBin, libsRaw.split(","));
        }

        Gst.init(Version.of(1, 20), "SwingVideoPlayer", args);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        File externalConfig = new File(CONFIG_FILE_NAME);

        if (externalConfig.exists()) {
            try (InputStream is = new FileInputStream(externalConfig)) {
                props.load(is);
                return props;
            } catch (IOException e) {
                throw new IllegalStateException("Ошибка чтения внешнего файла " + CONFIG_FILE_NAME, e);
            }
        }

        try (InputStream is = GStreamerInitializer.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (is != null) {
                props.load(is);
                return props;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка чтения встроенного файла " + CONFIG_FILE_NAME, e);
        }

        throw new IllegalStateException("Файл конфигурации " + CONFIG_FILE_NAME + " не найден ни в папке запуска, ни в resources.");
    }

    private static String getRequiredProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("В " + CONFIG_FILE_NAME + " не указан обязательный параметр: " + key);
        }
        return value.trim();
    }

    private static void preloadNativeLibraries(String gstBin, String[] libs) {
        for (String lib : libs) {
            String cleanLibName = lib.trim();
            if (cleanLibName.isEmpty()) continue;

            File file = new File(gstBin, cleanLibName);
            if (file.exists()) {
                System.load(file.getAbsolutePath());
            }
        }
    }
}
