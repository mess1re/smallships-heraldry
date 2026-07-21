package me.mss1r.smallshipsheraldry;

import me.mss1r.smallshipsheraldry.network.HeraldryPackets;
import me.mss1r.smallshipsheraldry.registry.HeraldryBlockEntities;
import me.mss1r.smallshipsheraldry.registry.HeraldryBlocks;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import me.mss1r.smallshipsheraldry.registry.HeraldryMenus;
import me.mss1r.smallshipsheraldry.registry.HeraldryRecipes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmallShipsHeraldry {
    public static final String MOD_ID = "smallshipsheraldry";
    public static final String MOD_NAME = "Small Ships Heraldry";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static boolean initialized;

    private SmallShipsHeraldry() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        HeraldryBlocks.register();
        HeraldryItems.register();
        HeraldryRecipes.register();
        HeraldryBlockEntities.register();
        HeraldryMenus.register();
        HeraldryPackets.register();
        LOGGER.info("{} initialized", MOD_NAME);
    }
}
