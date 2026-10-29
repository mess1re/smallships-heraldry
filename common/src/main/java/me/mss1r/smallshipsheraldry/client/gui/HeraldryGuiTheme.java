package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class HeraldryGuiTheme {
    static final int SURFACE = 0xFFC6C6C6;
    static final int SURFACE_ALT = 0xFFB0B0B0;
    static final int INK = 0xFF373737;
    static final int MUTED_INK = 0xFF656565;
    static final int HIGHLIGHT = 0xFFEEEEEE;
    static final int SELECTED = 0xFF555555;
    static final int BUTTON_TEXT = 0xFFE0E0E0;
    static final int BUTTON_TEXT_DISABLED = 0xFFA0A0A0;
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");

    private HeraldryGuiTheme() {
    }

    static void drawWindow(GuiGraphics graphics, int x, int y, int width, int height) {
        drawSprite(graphics, Sprite.WINDOW, x, y, width, height);
    }

    static void drawInset(GuiGraphics graphics, int x, int y, int width, int height) {
        drawSprite(graphics, Sprite.INSET, x, y, width, height);
    }

    static void drawPreviewInset(GuiGraphics graphics, int x, int y, int width, int height) {
        drawSprite(graphics, Sprite.PREVIEW, x, y, width, height);
    }

    static void drawButton(GuiGraphics graphics, int x, int y, int width, int height, boolean highlighted, boolean hovered, boolean enabled) {
        graphics.blitSprite(!enabled ? BUTTON_DISABLED : hovered || highlighted ? BUTTON_HIGHLIGHTED : BUTTON, x, y, width, height);
    }

    static void drawButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                           boolean highlighted, boolean hovered, boolean enabled, Component text, int textPadding) {
        drawButton(graphics, x, y, width, height, highlighted, hovered, enabled);
        String label = font.plainSubstrByWidth(text.getString(), Math.max(1, width - textPadding * 2));
        // Bitmap glyphs occupy eight visible pixels; Font#lineHeight also includes spacing.
        graphics.drawString(font, label, x + (width - font.width(label)) / 2, y + (height - 8) / 2,
                enabled ? BUTTON_TEXT : BUTTON_TEXT_DISABLED, false);
    }

    static void drawToggle(GuiGraphics graphics, int x, int y, int width, int height, boolean selected, boolean hovered) {
        Sprite sprite = selected
                ? (hovered ? Sprite.TOGGLE_SELECTED_HOVERED : Sprite.TOGGLE_SELECTED)
                : (hovered ? Sprite.TOGGLE_HOVERED : Sprite.TOGGLE);
        drawSprite(graphics, sprite, x, y, width, height);
    }

    static void drawField(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        drawSprite(graphics, focused ? Sprite.FIELD_FOCUSED : Sprite.FIELD, x, y, width, height);
    }

    static void drawColorFrame(GuiGraphics graphics, int x, int y, int width, int height, boolean selected) {
        drawSprite(graphics, selected ? Sprite.COLOR_FRAME_SELECTED : Sprite.COLOR_FRAME, x, y, width, height);
    }

    static void drawRow(GuiGraphics graphics, int x, int y, int width, int height, boolean selected, boolean muted) {
        drawSprite(graphics, selected ? Sprite.ROW_SELECTED : muted ? Sprite.ROW_MUTED : Sprite.ROW,
                x, y, width, height);
    }

    static void drawChoiceRow(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        drawSprite(graphics, focused ? Sprite.CHOICE_ROW_SELECTED : Sprite.CHOICE_ROW, x, y, width, height);
    }

    private static void drawSprite(GuiGraphics graphics, Sprite sprite, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (sprite.border == 0) {
            blitRegion(graphics, sprite, x, y, width, height, 0, 0, sprite.width, sprite.height);
            return;
        }
        int bx = Math.min(sprite.border, width / 2);
        int by = Math.min(sprite.border, height / 2);
        int[] sourceX = {0, sprite.border, sprite.width - sprite.border, sprite.width};
        int[] sourceY = {0, sprite.border, sprite.height - sprite.border, sprite.height};
        int[] targetX = {x, x + bx, x + width - bx, x + width};
        int[] targetY = {y, y + by, y + height - by, y + height};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                blitRegion(graphics, sprite, targetX[column], targetY[row],
                        targetX[column + 1] - targetX[column], targetY[row + 1] - targetY[row],
                        sourceX[column], sourceY[row],
                        sourceX[column + 1] - sourceX[column], sourceY[row + 1] - sourceY[row]);
            }
        }
    }

    private static void blitRegion(GuiGraphics graphics, Sprite sprite, int x, int y, int width, int height,
                                   int u, int v, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) return;
        graphics.blit(sprite.texture, x, y, width, height, u, v,
                sourceWidth, sourceHeight, sprite.width, sprite.height);
    }

    enum Sprite {
        WINDOW("window", 16, 16, 4),
        INSET("inset", 8, 8, 1),
        PREVIEW("preview", 8, 8, 1),
        TOGGLE("toggle", 8, 8, 1),
        TOGGLE_HOVERED("toggle_hovered", 8, 8, 1),
        TOGGLE_SELECTED("toggle_selected", 8, 8, 1),
        TOGGLE_SELECTED_HOVERED("toggle_selected_hovered", 8, 8, 1),
        FIELD("field", 8, 8, 1),
        FIELD_FOCUSED("field_focused", 8, 8, 1),
        COLOR_FRAME("color_frame", 8, 8, 1),
        COLOR_FRAME_SELECTED("color_frame_selected", 8, 8, 1),
        ROW("row", 1, 1, 0),
        ROW_SELECTED("row_selected", 1, 1, 0),
        ROW_MUTED("row_muted", 1, 1, 0),
        CHOICE_ROW("choice_row", 1, 1, 0),
        CHOICE_ROW_SELECTED("choice_row_selected", 1, 1, 0);

        final String assetName;
        final int width;
        final int height;
        final int border;
        final ResourceLocation texture;

        Sprite(String assetName, int width, int height, int border) {
            this.assetName = assetName;
            this.width = width;
            this.height = height;
            this.border = border;
            this.texture = ResourceLocation.fromNamespaceAndPath(SmallShipsHeraldry.MOD_ID, "textures/gui/editor/" + assetName + ".png");
        }
    }
}
