package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class PaperButton extends AbstractButton {
    private final Consumer<PaperButton> onPress;

    PaperButton(int x, int y, int width, int height, Component message, Consumer<PaperButton> onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = !active ? HeraldryGuiTheme.MUTED_INK
                : isHoveredOrFocused() ? HeraldryGuiTheme.GOLD_LIGHT
                : HeraldryGuiTheme.FRAME_DARK;
        int fill = !active ? HeraldryGuiTheme.INSET
                : isHoveredOrFocused() ? HeraldryGuiTheme.GOLD
                : HeraldryGuiTheme.PAPER_ALT;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
        int textColor = active ? HeraldryGuiTheme.INK : HeraldryGuiTheme.MUTED_INK;
        Font font = Minecraft.getInstance().font;
        int textX = getX() + (width - font.width(getMessage())) / 2;
        // Minecraft's bitmap glyphs occupy eight visible pixels even though
        // Font#lineHeight is nine, so visual centering needs this baseline.
        int textY = getY() + (height - 8) / 2;
        graphics.drawString(font, getMessage(), textX, textY, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
