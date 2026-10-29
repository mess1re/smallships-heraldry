package me.mss1r.smallshipsheraldry.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryEditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

@Mixin(EditBox.class)
abstract class EditBoxMixin {
    // Keep vanilla cursor and text layout, without shadows in heraldry fields.
    @Group(name = "smallshipsheraldry$shadowless", min = 1)
    @WrapOperation(method = "renderWidget", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int smallshipsheraldry$drawText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof HeraldryEditBox ? graphics.drawString(font, text, x, y, color, false)
                : original.call(graphics, font, text, x, y, color);
    }

    @Group(name = "smallshipsheraldry$shadowless")
    @WrapOperation(method = "renderWidget", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int smallshipsheraldry$drawHint(GuiGraphics graphics, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof HeraldryEditBox ? graphics.drawString(font, text, x, y, color, false)
                : original.call(graphics, font, text, x, y, color);
    }

    @Group(name = "smallshipsheraldry$shadowless")
    @WrapOperation(method = "renderWidget", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int smallshipsheraldry$drawCursor(GuiGraphics graphics, Font font, String text, int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof HeraldryEditBox ? graphics.drawString(font, text, x, y, color, false)
                : original.call(graphics, font, text, x, y, color);
    }

}
