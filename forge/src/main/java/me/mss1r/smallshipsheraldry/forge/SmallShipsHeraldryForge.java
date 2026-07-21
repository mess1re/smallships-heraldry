package me.mss1r.smallshipsheraldry.forge;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldryClient;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SmallShipsHeraldry.MOD_ID)
public final class SmallShipsHeraldryForge {
    public SmallShipsHeraldryForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(SmallShipsHeraldry.MOD_ID, modEventBus);
        SmallShipsHeraldry.init();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modEventBus.addListener(SmallShipsHeraldryForge::onClientSetup));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(SmallShipsHeraldryClient::init);
    }
}
