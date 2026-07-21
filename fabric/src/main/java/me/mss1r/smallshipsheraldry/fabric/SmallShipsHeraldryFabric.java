package me.mss1r.smallshipsheraldry.fabric;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.fabricmc.api.ModInitializer;

public final class SmallShipsHeraldryFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SmallShipsHeraldry.init();
    }
}
