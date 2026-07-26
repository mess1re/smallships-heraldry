package me.mss1r.smallshipsheraldry.neoforge;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(SmallShipsHeraldry.MOD_ID)
public final class SmallShipsHeraldryNeoForge {
    public SmallShipsHeraldryNeoForge(IEventBus modEventBus) {
        SmallShipsHeraldry.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SmallShipsHeraldryNeoForgeClient.register(modEventBus);
        }
    }
}
