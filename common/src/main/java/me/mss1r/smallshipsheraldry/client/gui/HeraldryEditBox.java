package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Uses the vanilla text layout; EditBoxMixin disables shadows only for this widget. */
public final class HeraldryEditBox extends EditBox {
    HeraldryEditBox(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
        setTextColor(HeraldryGuiTheme.INK);
        setTextColorUneditable(HeraldryGuiTheme.MUTED_INK);
    }
}
