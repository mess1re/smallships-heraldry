package me.mss1r.smallshipsheraldry.fabric;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldryClient;
import net.fabricmc.api.ClientModInitializer;

public final class SmallShipsHeraldryFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SmallShipsHeraldryClient.init();
    }
}
