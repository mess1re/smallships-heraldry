package me.mss1r.smallshipsheraldry.mixin;

import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.common.SailBannerInteractionHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Ship.class)
public abstract class ShipInteractionMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void smallshipsheraldry$handleSailBannerInteraction(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = SailBannerInteractionHandler.tryHandle((Ship) (Object) this, player, interactionHand);
        if (result != InteractionResult.PASS) {
            cir.setReturnValue(result);
        }
    }
}
