package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class HeraldryButton extends AbstractButton {
    private final Consumer<HeraldryButton> onPress;

    HeraldryButton(int x, int y, int width, int height, Component message, Consumer<HeraldryButton> onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HeraldryGuiTheme.drawButton(graphics, Minecraft.getInstance().font, getX(), getY(), width, height,
                false, isHoveredOrFocused(), active, getMessage(), 3);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
