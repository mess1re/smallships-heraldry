package me.mss1r.smallshipsheraldry.neoforge;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldryClient;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryTableScreen;
import me.mss1r.smallshipsheraldry.registry.HeraldryMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class SmallShipsHeraldryNeoForgeClient {
    private SmallShipsHeraldryNeoForgeClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(SmallShipsHeraldryNeoForgeClient::onRegisterMenuScreens);
        modEventBus.addListener(SmallShipsHeraldryNeoForgeClient::onClientSetup);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(HeraldryMenus.HERALDRY_TABLE.get(), HeraldryTableScreen::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(SmallShipsHeraldryClient::init);
    }
}
