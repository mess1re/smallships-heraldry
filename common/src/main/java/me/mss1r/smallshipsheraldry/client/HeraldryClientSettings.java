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
    private static final String BRUSH_COLOR_KEY = "brushColor";
    private static final String TOOL_KEY = "tool";
    private static final String EDITOR_MODE_KEY = "editorMode";
    private static final String STROKE_MODE_KEY = "strokeMode";
    private static final String FILL_MODE_KEY = "fillMode";
    private static final String BRUSH_SIZE_KEY = "brushSize";
    private static final String MIRROR_VERTICAL_KEY = "mirrorVertical";
    private static final String MIRROR_HORIZONTAL_KEY = "mirrorHorizontal";
    private static final String GRID_VISIBLE_KEY = "gridVisible";
    private static final String PREVIEW_STAGE_KEY = "previewStage";
    private static final String PREVIEW_YAW_KEY = "previewYaw";
    private static final String PREVIEW_PITCH_KEY = "previewPitch";
    private static final String PREVIEW_ZOOM_KEY = "previewZoom";

    private HeraldryClientSettings() {
    }

    public static void loadCustomColors(int[] colors) {
        Properties properties = loadProperties();

        for (int index = 0; index < colors.length; index++) {
            colors[index] = parseColor(properties.getProperty(CUSTOM_COLOR_KEY + index));
        }
    }

    public static void saveCustomColors(int[] colors) {
        Properties properties = loadProperties();
        for (int index = 0; index < colors.length; index++) {
            properties.remove(CUSTOM_COLOR_KEY + index);
            int color = colors[index];
            if (color != 0) {
                properties.setProperty(CUSTOM_COLOR_KEY + index, String.format("#%08X", color));
            }
        }
        saveProperties(properties);
    }

    public static EditorSettings loadEditorSettings() {
        Properties properties = loadProperties();
        int brushColor = parseColor(properties.getProperty(BRUSH_COLOR_KEY));
        if (brushColor == 0) {
            brushColor = EditorSettings.DEFAULT_BRUSH_COLOR;
        }
        return new EditorSettings(
                brushColor,
                properties.getProperty(TOOL_KEY, EditorSettings.DEFAULT_TOOL),
                properties.getProperty(EDITOR_MODE_KEY, EditorSettings.DEFAULT_EDITOR_MODE),
                properties.getProperty(STROKE_MODE_KEY, EditorSettings.DEFAULT_STROKE_MODE),
                properties.getProperty(FILL_MODE_KEY, EditorSettings.DEFAULT_FILL_MODE),
                parseInt(properties.getProperty(BRUSH_SIZE_KEY), 1, 1, 8),
                parseBoolean(properties.getProperty(MIRROR_VERTICAL_KEY), false),
                parseBoolean(properties.getProperty(MIRROR_HORIZONTAL_KEY), false),
                parseBoolean(properties.getProperty(GRID_VISIBLE_KEY), true),
                parseInt(properties.getProperty(PREVIEW_STAGE_KEY), 4, 0, 4),
                parseFloat(properties.getProperty(PREVIEW_YAW_KEY), 180.0F, -3600.0F, 3600.0F),
                parseFloat(properties.getProperty(PREVIEW_PITCH_KEY), -10.0F, -80.0F, 80.0F),
                parseFloat(properties.getProperty(PREVIEW_ZOOM_KEY), 1.0F, 0.30F, 10.0F)
        );
    }

    public static void saveEditorSettings(EditorSettings settings) {
        if (settings == null) {
            return;
        }
        Properties properties = loadProperties();
        properties.setProperty(BRUSH_COLOR_KEY, String.format("#%08X", settings.brushColor()));
        properties.setProperty(TOOL_KEY, settings.tool());
        properties.setProperty(EDITOR_MODE_KEY, settings.editorMode());
        properties.setProperty(STROKE_MODE_KEY, settings.strokeMode());
        properties.setProperty(FILL_MODE_KEY, settings.fillMode());
        properties.setProperty(BRUSH_SIZE_KEY, Integer.toString(settings.brushSize()));
        properties.setProperty(MIRROR_VERTICAL_KEY, Boolean.toString(settings.mirrorVertical()));
        properties.setProperty(MIRROR_HORIZONTAL_KEY, Boolean.toString(settings.mirrorHorizontal()));
        properties.setProperty(GRID_VISIBLE_KEY, Boolean.toString(settings.gridVisible()));
        properties.setProperty(PREVIEW_STAGE_KEY, Integer.toString(settings.previewStage()));
        properties.setProperty(PREVIEW_YAW_KEY, Float.toString(settings.previewYaw()));
        properties.setProperty(PREVIEW_PITCH_KEY, Float.toString(settings.previewPitch()));
        properties.setProperty(PREVIEW_ZOOM_KEY, Float.toString(settings.previewZoom()));
        saveProperties(properties);
    }

    private static Properties loadProperties() {
        Path path = configPath();
        Properties properties = new Properties();
        if (!Files.isRegularFile(path)) {
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            SmallShipsHeraldry.LOGGER.warn("Failed to load heraldry client settings", exception);
        }
        return properties;
    }

    private static void saveProperties(Properties properties) {
        Path path = configPath();

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

    private static boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static int parseInt(String value, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback, float minimum, float maximum) {
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? Math.max(minimum, Math.min(maximum, parsed)) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public record EditorSettings(
            int brushColor,
            String tool,
            String editorMode,
            String strokeMode,
            String fillMode,
            int brushSize,
            boolean mirrorVertical,
            boolean mirrorHorizontal,
            boolean gridVisible,
            int previewStage,
            float previewYaw,
            float previewPitch,
            float previewZoom
    ) {
        private static final int DEFAULT_BRUSH_COLOR = 0xFF993333;
        private static final String DEFAULT_TOOL = "brush";
        private static final String DEFAULT_EDITOR_MODE = "paint";
        private static final String DEFAULT_STROKE_MODE = "freehand";
        private static final String DEFAULT_FILL_MODE = "area";
    }
}
