package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** An edit box whose text follows the paper UI instead of using Minecraft's forced shadow. */
final class PaperEditBox extends EditBox {
    private static final int ALMOST_TRANSPARENT = 0x01FFFFFF;

    private final Font font;
    private int paperTextColor = HeraldryGuiTheme.INK;
    private int paperTextColorUneditable = HeraldryGuiTheme.MUTED_INK;

    PaperEditBox(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
        this.font = font;
        super.setTextColor(ALMOST_TRANSPARENT);
        super.setTextColorUneditable(ALMOST_TRANSPARENT);
    }

    @Override
    public void setTextColor(int color) {
        paperTextColor = color;
        super.setTextColor(ALMOST_TRANSPARENT);
    }

    @Override
    public void setTextColorUneditable(int color) {
        paperTextColorUneditable = color;
        super.setTextColorUneditable(ALMOST_TRANSPARENT);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int color = active ? paperTextColor : paperTextColorUneditable;
        graphics.drawString(font, getValue(), getX(), getY() + (height - 8) / 2, color, false);
        // The nearly transparent vanilla text keeps its caret and selection behavior,
        // while the visible text above is rendered once and without a shadow.
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
