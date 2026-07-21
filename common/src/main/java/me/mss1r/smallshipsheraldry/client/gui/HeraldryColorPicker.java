package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.client.HeraldryClientSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class HeraldryColorPicker {
    private static final int COLOR_Y = 26;
    private static final int COLOR_WIDTH = 62;
    private static final int COLOR_HEIGHT = 52;
    private static final int HUE_WIDTH = 8;
    private static final int OPACITY_Y = 80;
    private static final int OPACITY_HEIGHT = 5;
    private static final int CURRENT_COLOR_Y = 88;
    private static final int CURRENT_COLOR_SIZE = 14;
    private static final int HEX_Y = 88;
    private static final int HEX_WIDTH = 62;
    private static final int HEX_HEIGHT = 14;
    private static final int CUSTOM_Y = 106;
    private static final int SWATCH_Y = 118;
    private static final int SWATCH_SIZE = 8;
    private static final int SWATCH_GAP = 2;
    private static final int SWATCH_COLUMNS = 8;

    private static final int[] BASE_COLORS = {
            0xFFFFFFFF, 0xFFD87F33, 0xFFB24CD8, 0xFF6699D8,
            0xFFE5E533, 0xFF7FCC19, 0xFFF27FA5, 0xFF4C4C4C,
            0xFF999999, 0xFF4C7F99, 0xFF7F3FB2, 0xFF334CB2,
            0xFF664C33, 0xFF667F33, 0xFF993333, 0xFF191919
    };
    private static final int[] CUSTOM_COLORS = new int[SWATCH_COLUMNS];
    private static boolean customColorsLoaded;

    private final Font font;
    private final int panelX;
    private final int panelWidth;
    private PaperEditBox hexInput;
    private int left;
    private int top;
    private float hue;
    private float saturation = 0.66F;
    private float brightness = 0.60F;
    private int color = 0xFF993333;
    private int opacity = 255;
    private boolean pickingColor;
    private boolean pickingHue;
    private boolean pickingOpacity;
    private boolean syncingHexInput;

    HeraldryColorPicker(Font font, int panelX, int panelWidth) {
        this.font = font;
        this.panelX = panelX;
        this.panelWidth = panelWidth;
        ensureCustomColorsLoaded();
    }

    PaperEditBox init(int left, int top) {
        this.left = left;
        this.top = top;
        hexInput = new PaperEditBox(
                font,
                left + hexX() + 3,
                top + HEX_Y + 2,
                HEX_WIDTH - 6,
                HEX_HEIGHT - 4,
                Component.translatable("smallshipsheraldry.screen.hex")
        );
        hexInput.setMaxLength(7);
        hexInput.setBordered(false);
        hexInput.setTextColor(HeraldryGuiTheme.INK);
        hexInput.setTextColorUneditable(HeraldryGuiTheme.MUTED_INK);
        hexInput.setFilter(HeraldryColorPicker::isHexDraft);
        hexInput.setResponder(this::handleHexInput);
        syncHexInput();
        return hexInput;
    }

    int color() {
        return color;
    }

    void setColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        opacity = alpha == 0 ? 255 : alpha;
        color = (argb & 0x00FFFFFF) | (opacity << 24);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;

        brightness = max;
        saturation = max <= 0.0001F ? 0.0F : delta / max;
        if (delta <= 0.0001F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = ((green - blue) / delta) / 6.0F;
        } else if (max == green) {
            hue = (2.0F + (blue - red) / delta) / 6.0F;
        } else {
            hue = (4.0F + (red - green) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        hue = Mth.clamp(hue, 0.0F, 0.995F);
        syncHexInput();
    }

    void setVisible(boolean visible) {
        if (hexInput == null) {
            return;
        }
        hexInput.visible = visible;
        if (!visible) {
            hexInput.setFocused(false);
        }
    }

    boolean isInputFocused() {
        return hexInput != null && hexInput.isFocused();
    }

    void render(GuiGraphics guiGraphics) {
        int colorX = colorX();
        int hueX = hueX();
        int opacityX = opacityX();
        int opacityWidth = opacityWidth();
        guiGraphics.fill(left + colorX - 1, top + COLOR_Y - 1, left + colorX + COLOR_WIDTH + 1, top + COLOR_Y + COLOR_HEIGHT + 1, HeraldryGuiTheme.FRAME_DARK);
        for (int x = 0; x < COLOR_WIDTH; x += 2) {
            for (int y = 0; y < COLOR_HEIGHT; y += 2) {
                float localSaturation = x / (float) (COLOR_WIDTH - 1);
                float localBrightness = 1.0F - y / (float) (COLOR_HEIGHT - 1);
                int fieldColor = 0xFF000000 | Mth.hsvToRgb(hue, localSaturation, localBrightness);
                guiGraphics.fill(left + colorX + x, top + COLOR_Y + y, left + colorX + x + 2, top + COLOR_Y + y + 2, fieldColor);
            }
        }
        guiGraphics.fill(left + hueX - 1, top + COLOR_Y - 1, left + hueX + HUE_WIDTH + 1, top + COLOR_Y + COLOR_HEIGHT + 1, HeraldryGuiTheme.FRAME_DARK);
        for (int y = 0; y < COLOR_HEIGHT; y++) {
            float localHue = Math.min(0.995F, y / (float) COLOR_HEIGHT);
            int hueColor = 0xFF000000 | Mth.hsvToRgb(localHue, 1.0F, 1.0F);
            guiGraphics.fill(left + hueX, top + COLOR_Y + y, left + hueX + HUE_WIDTH, top + COLOR_Y + y + 1, hueColor);
        }
        drawOpacityBar(guiGraphics);
        int markerX = left + colorX + Mth.clamp(Math.round(saturation * (COLOR_WIDTH - 1)), 0, COLOR_WIDTH - 1);
        int markerY = top + COLOR_Y + Mth.clamp(Math.round((1.0F - brightness) * (COLOR_HEIGHT - 1)), 0, COLOR_HEIGHT - 1);
        guiGraphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY - 1, 0xFFFFFFFF);
        guiGraphics.fill(markerX - 2, markerY + 2, markerX + 3, markerY + 3, 0xFFFFFFFF);
        guiGraphics.fill(markerX - 2, markerY - 2, markerX - 1, markerY + 3, 0xFFFFFFFF);
        guiGraphics.fill(markerX + 2, markerY - 2, markerX + 3, markerY + 3, 0xFFFFFFFF);

        int hueY = top + COLOR_Y + Mth.clamp(Math.round(hue * COLOR_HEIGHT), 0, COLOR_HEIGHT - 1);
        guiGraphics.fill(left + hueX - 2, hueY - 1, left + hueX + HUE_WIDTH + 2, hueY, 0xFF07101D);
        guiGraphics.fill(left + hueX - 2, hueY, left + hueX + HUE_WIDTH + 2, hueY + 2, 0xFFFFFFFF);
        int markerOpacityX = left + opacityX + Mth.clamp(Math.round((opacity - 1) * (opacityWidth - 1) / 254.0F), 0, opacityWidth - 1);
        guiGraphics.fill(markerOpacityX - 1, top + OPACITY_Y - 2, markerOpacityX, top + OPACITY_Y + OPACITY_HEIGHT + 2, 0xFF07101D);
        guiGraphics.fill(markerOpacityX, top + OPACITY_Y - 2, markerOpacityX + 2, top + OPACITY_Y + OPACITY_HEIGHT + 2, 0xFFFFFFFF);

        int currentColorX = panelX + 4;
        guiGraphics.fill(left + currentColorX - 1, top + CURRENT_COLOR_Y - 1, left + currentColorX + CURRENT_COLOR_SIZE + 1, top + CURRENT_COLOR_Y + CURRENT_COLOR_SIZE + 1, 0xFF07101D);
        drawChecker(guiGraphics, left + currentColorX, top + CURRENT_COLOR_Y, CURRENT_COLOR_SIZE, CURRENT_COLOR_SIZE);
        guiGraphics.fill(left + currentColorX, top + CURRENT_COLOR_Y, left + currentColorX + CURRENT_COLOR_SIZE, top + CURRENT_COLOR_Y + CURRENT_COLOR_SIZE, color);
        guiGraphics.fill(left + hexX() - 1, top + HEX_Y - 1, left + hexX() + HEX_WIDTH + 1, top + HEX_Y + HEX_HEIGHT + 1, isInputFocused() ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.FRAME_DARK);
        guiGraphics.fill(left + hexX(), top + HEX_Y, left + hexX() + HEX_WIDTH, top + HEX_Y + HEX_HEIGHT, HeraldryGuiTheme.PAPER);
        drawCustomSwatches(guiGraphics);
        drawBaseSwatches(guiGraphics);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, boolean shiftDown) {
        if (button != 0 && button != 1) {
            return false;
        }
        if (button == 0 && isInside(mouseX, mouseY, left + colorX(), top + COLOR_Y, COLOR_WIDTH, COLOR_HEIGHT)) {
            pickingColor = true;
            setColorFromField(mouseX, mouseY);
            return true;
        }
        if (button == 0 && isInside(mouseX, mouseY, left + hueX(), top + COLOR_Y, HUE_WIDTH, COLOR_HEIGHT)) {
            pickingHue = true;
            setHueFromBar(mouseY);
            return true;
        }
        if (button == 0 && isInside(mouseX, mouseY, left + opacityX(), top + OPACITY_Y, opacityWidth(), OPACITY_HEIGHT)) {
            pickingOpacity = true;
            setOpacityFromBar(mouseX);
            return true;
        }
        for (int index = 0; index < SWATCH_COLUMNS; index++) {
            int x = left + panelX + 4 + index * (SWATCH_SIZE + SWATCH_GAP);
            int y = top + CUSTOM_Y;
            if (isInside(mouseX, mouseY, x, y, SWATCH_SIZE, SWATCH_SIZE)) {
                if (button == 1 || shiftDown || CUSTOM_COLORS[index] == 0) {
                    CUSTOM_COLORS[index] = color;
                    HeraldryClientSettings.saveCustomColors(CUSTOM_COLORS);
                } else {
                    setColor(CUSTOM_COLORS[index]);
                }
                return true;
            }
        }
        if (button != 0) {
            return false;
        }
        for (int index = 0; index < BASE_COLORS.length; index++) {
            int column = index % SWATCH_COLUMNS;
            int row = index / SWATCH_COLUMNS;
            int x = left + panelX + 4 + column * (SWATCH_SIZE + SWATCH_GAP);
            int y = top + SWATCH_Y + row * (SWATCH_SIZE + SWATCH_GAP);
            if (isInside(mouseX, mouseY, x, y, SWATCH_SIZE, SWATCH_SIZE)) {
                setColor(BASE_COLORS[index]);
                return true;
            }
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (pickingColor) {
            setColorFromField(mouseX, mouseY);
            return true;
        }
        if (pickingHue) {
            setHueFromBar(mouseY);
            return true;
        }
        if (pickingOpacity) {
            setOpacityFromBar(mouseX);
            return true;
        }
        return false;
    }

    void mouseReleased() {
        pickingColor = false;
        pickingHue = false;
        pickingOpacity = false;
    }

    private void drawOpacityBar(GuiGraphics guiGraphics) {
        int x = left + opacityX();
        int y = top + OPACITY_Y;
        int width = opacityWidth();
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + OPACITY_HEIGHT + 1, HeraldryGuiTheme.FRAME_DARK);
        drawChecker(guiGraphics, x, y, width, OPACITY_HEIGHT);
        int rgb = color & 0x00FFFFFF;
        for (int localX = 0; localX < width; localX++) {
            int alpha = Mth.clamp(Math.round(1.0F + localX * 254.0F / (width - 1)), 1, 255);
            guiGraphics.fill(x + localX, y, x + localX + 1, y + OPACITY_HEIGHT, (alpha << 24) | rgb);
        }
    }

    private void drawCustomSwatches(GuiGraphics guiGraphics) {
        for (int index = 0; index < SWATCH_COLUMNS; index++) {
            int x = left + panelX + 4 + index * (SWATCH_SIZE + SWATCH_GAP);
            int y = top + CUSTOM_Y;
            int swatchColor = CUSTOM_COLORS[index];
            boolean empty = swatchColor == 0;
            boolean selected = swatchColor != 0 && swatchColor == color;
            guiGraphics.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, selected ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.FRAME_DARK);
            if (empty) {
                guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, HeraldryGuiTheme.PAPER_ALT);
                int centerX = x + SWATCH_SIZE / 2;
                int centerY = y + SWATCH_SIZE / 2;
                guiGraphics.fill(centerX - 2, centerY, centerX + 3, centerY + 1, 0xFF6E7F96);
                guiGraphics.fill(centerX, centerY - 2, centerX + 1, centerY + 3, 0xFF6E7F96);
            } else {
                drawChecker(guiGraphics, x, y, SWATCH_SIZE, SWATCH_SIZE);
                guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, swatchColor);
            }
        }
    }

    private void drawBaseSwatches(GuiGraphics guiGraphics) {
        for (int index = 0; index < BASE_COLORS.length; index++) {
            int column = index % SWATCH_COLUMNS;
            int row = index / SWATCH_COLUMNS;
            int x = left + panelX + 4 + column * (SWATCH_SIZE + SWATCH_GAP);
            int y = top + SWATCH_Y + row * (SWATCH_SIZE + SWATCH_GAP);
            int swatchColor = BASE_COLORS[index];
            boolean selected = (swatchColor & 0x00FFFFFF) == (color & 0x00FFFFFF);
            guiGraphics.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, selected ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.FRAME_DARK);
            guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, swatchColor);
        }
    }

    private void drawChecker(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        for (int localY = 0; localY < height; localY += 2) {
            for (int localX = 0; localX < width; localX += 2) {
                boolean bright = ((localX + localY) / 2) % 2 == 0;
                guiGraphics.fill(x + localX, y + localY, x + Math.min(localX + 2, width), y + Math.min(localY + 2, height), bright ? 0xFFE1E6EF : 0xFF77869A);
            }
        }
    }

    private void setColorFromField(double mouseX, double mouseY) {
        saturation = Mth.clamp((float) ((mouseX - (left + colorX())) / (COLOR_WIDTH - 1)), 0.0F, 1.0F);
        brightness = Mth.clamp(1.0F - (float) ((mouseY - (top + COLOR_Y)) / (COLOR_HEIGHT - 1)), 0.0F, 1.0F);
        rebuildColor();
    }

    private void setHueFromBar(double mouseY) {
        hue = Mth.clamp((float) ((mouseY - (top + COLOR_Y)) / COLOR_HEIGHT), 0.0F, 0.995F);
        rebuildColor();
    }

    private void setOpacityFromBar(double mouseX) {
        float value = Mth.clamp((float) ((mouseX - (left + opacityX())) / (opacityWidth() - 1)), 0.0F, 1.0F);
        opacity = Mth.clamp(Math.round(1.0F + value * 254.0F), 1, 255);
        rebuildColor();
    }

    private void rebuildColor() {
        color = (opacity << 24) | Mth.hsvToRgb(hue, saturation, brightness);
        syncHexInput();
    }

    private void handleHexInput(String value) {
        if (syncingHexInput) {
            return;
        }
        String clean = cleanHex(value);
        if (clean.length() != 6 || !isHex(clean)) {
            return;
        }
        int parsed = (opacity << 24) | Integer.parseInt(clean, 16);
        if ((parsed & 0x00FFFFFF) != (color & 0x00FFFFFF)) {
            setColor(parsed);
        }
    }

    private void syncHexInput() {
        if (hexInput == null) {
            return;
        }
        String value = String.format("#%06X", color & 0x00FFFFFF);
        if (value.equals(hexInput.getValue())) {
            return;
        }
        syncingHexInput = true;
        hexInput.setValue(value);
        syncingHexInput = false;
    }

    private static boolean isHexDraft(String value) {
        if (value.length() > 7 || value.indexOf('#') > 0) {
            return false;
        }
        String clean = cleanHex(value);
        return clean.length() <= 6 && isHex(clean);
    }

    private static String cleanHex(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static boolean isHex(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean upper = character >= 'A' && character <= 'F';
            boolean lower = character >= 'a' && character <= 'f';
            if (!digit && !upper && !lower) {
                return false;
            }
        }
        return true;
    }

    private static void ensureCustomColorsLoaded() {
        if (!customColorsLoaded) {
            customColorsLoaded = true;
            HeraldryClientSettings.loadCustomColors(CUSTOM_COLORS);
        }
    }

    private int colorX() {
        return panelX + 4;
    }

    private int hueX() {
        return colorX() + COLOR_WIDTH + 6;
    }

    private int opacityX() {
        return panelX + 4;
    }

    private int opacityWidth() {
        return panelWidth - 8;
    }

    private int hexX() {
        return panelX + 22;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
