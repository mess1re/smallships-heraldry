package me.mss1r.smallshipsheraldry.neoforge.mixin;

import net.neoforged.fml.event.config.ModConfigEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.talhanation.smallships.neoforge.common.CommonModBus", remap = false)
public abstract class SmallShipsConfigEventsMixin {
    @Inject(method = "initRegisterConfigs", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skipUnloadedConfig(ModConfigEvent event, CallbackInfo callback) {
        // Small Ships 2.0.2 rereads the config after NeoForge has detached its file.
        if (event instanceof ModConfigEvent.Unloading) callback.cancel();
    }
}
