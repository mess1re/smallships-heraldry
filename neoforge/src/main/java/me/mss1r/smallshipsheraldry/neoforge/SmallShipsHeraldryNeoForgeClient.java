package me.mss1r.smallshipsheraldry.neoforge;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldryClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class SmallShipsHeraldryNeoForgeClient {
    private SmallShipsHeraldryNeoForgeClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(SmallShipsHeraldryNeoForgeClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(SmallShipsHeraldryClient::init);
    }
}
