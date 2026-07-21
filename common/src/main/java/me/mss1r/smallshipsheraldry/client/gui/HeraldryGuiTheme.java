package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.client.gui.GuiGraphics;

final class HeraldryGuiTheme {
    static final int BACKDROP = 0xCC171411;
    static final int FRAME_DARK = 0xFF3B3025;
    static final int FRAME = 0xFF6B5842;
    static final int PAPER = 0xFFE6D8BB;
    static final int PAPER_ALT = 0xFFD8C5A1;
    static final int PANEL = 0xFFE0CFAD;
    static final int INSET = 0xFFC4AB80;
    static final int INK = 0xFF2C241C;
    static final int MUTED_INK = 0xFF6C5B46;
    static final int GOLD = 0xFF9D7A4B;
    static final int GOLD_LIGHT = 0xFFC7A86F;
    static final int SELECTED = 0xFF8C653F;
    static final int PREVIEW = 0xFF25303A;
    static final int PREVIEW_EDGE = 0xFF141B22;

    private HeraldryGuiTheme() {
    }

    static void drawWindow(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, FRAME_DARK);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, FRAME);
        graphics.fill(x + 5, y + 5, x + width - 5, y + height - 5, PAPER);
        graphics.fill(x + 7, y + 7, x + width - 7, y + 8, GOLD_LIGHT);
        graphics.fill(x + 7, y + height - 8, x + width - 7, y + height - 7, INSET);
    }

    static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    static void drawInset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, FRAME_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INSET);
    }

    static void drawPreviewInset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, FRAME_DARK);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PREVIEW_EDGE);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, PREVIEW);
    }
}
