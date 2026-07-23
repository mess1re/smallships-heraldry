package me.mss1r.smallshipsheraldry;

import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.architectury.event.events.client.ClientPlayerEvent;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryPatternPreviewScreen;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryTableScreen;
import me.mss1r.smallshipsheraldry.client.network.HeraldryClientPackets;
import me.mss1r.smallshipsheraldry.client.render.SailBannerTextureCache;
import me.mss1r.smallshipsheraldry.registry.HeraldryBlocks;
import me.mss1r.smallshipsheraldry.registry.HeraldryMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;

import dev.architectury.registry.menu.MenuRegistry;

public final class SmallShipsHeraldryClient {
    private static boolean initialized;

    private SmallShipsHeraldryClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        RenderTypeRegistry.register(RenderType.cutout(), HeraldryBlocks.HERALDRY_TABLE.get());
        HeraldryClientPackets.register();
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> SailBannerTextureCache.clear());
        MenuRegistry.registerScreenFactory(HeraldryMenus.HERALDRY_TABLE.get(), HeraldryTableScreen::new);
    }

    public static void openPatternPreview(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new HeraldryPatternPreviewScreen(stack));
        }
    }
}
