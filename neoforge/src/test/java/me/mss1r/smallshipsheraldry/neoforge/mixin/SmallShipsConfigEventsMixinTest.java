package me.mss1r.smallshipsheraldry.neoforge.mixin;

import net.neoforged.fml.event.config.ModConfigEvent;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;

class SmallShipsConfigEventsMixinTest {
    @Test
    void unloadingIsCancelledWithoutAccessingTheDetachedConfig() throws Exception {
        assertTrue(invoke(new ModConfigEvent.Unloading(null)).isCancelled());
    }

    @Test
    void loadingStillReachesSmallShips() throws Exception {
        assertFalse(invoke(new ModConfigEvent.Loading(null)).isCancelled());
    }

    @Test
    void reloadingStillReachesSmallShips() throws Exception {
        assertFalse(invoke(new ModConfigEvent.Reloading(null)).isCancelled());
    }

    private CallbackInfo invoke(ModConfigEvent event) throws Exception {
        var method = SmallShipsConfigEventsMixin.class.getDeclaredMethod(
                "skipUnloadedConfig", ModConfigEvent.class, CallbackInfo.class);
        method.setAccessible(true);
        var callback = new CallbackInfo("initRegisterConfigs", true);
        method.invoke(null, event, callback);
        return callback;
    }
}
