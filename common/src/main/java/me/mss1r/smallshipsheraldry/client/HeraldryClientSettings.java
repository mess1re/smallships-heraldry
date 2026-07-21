package me.mss1r.smallshipsheraldry.client;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class HeraldryClientSettings {
    private static final String FILE_NAME = "smallshipsheraldry-client.properties";
    private static final String CUSTOM_COLOR_KEY = "customColor";

    private HeraldryClientSettings() {
    }

    public static void loadCustomColors(int[] colors) {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            SmallShipsHeraldry.LOGGER.warn("Failed to load heraldry client settings", exception);
            return;
        }

        for (int index = 0; index < colors.length; index++) {
            colors[index] = parseColor(properties.getProperty(CUSTOM_COLOR_KEY + index));
        }
    }

    public static void saveCustomColors(int[] colors) {
        Path path = configPath();
        Properties properties = new Properties();
        for (int index = 0; index < colors.length; index++) {
            int color = colors[index];
            if (color != 0) {
                properties.setProperty(CUSTOM_COLOR_KEY + index, String.format("#%08X", color));
            }
        }

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "Small Ships Heraldry client settings");
            }
        } catch (IOException exception) {
            SmallShipsHeraldry.LOGGER.warn("Failed to save heraldry client settings", exception);
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static int parseColor(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        String clean = value.startsWith("#") ? value.substring(1) : value;
        try {
            if (clean.length() == 6) {
                return 0xFF000000 | Integer.parseInt(clean, 16);
            }
            if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (NumberFormatException exception) {
            SmallShipsHeraldry.LOGGER.warn("Ignoring invalid heraldry custom color '{}'", value);
        }
        return 0;
    }
}
