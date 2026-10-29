package me.mss1r.smallshipsheraldry;

import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.ReloadListenerRegistry;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryPatternPreviewScreen;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryTableScreen;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.client.render.SailBannerTextureCache;
import me.mss1r.smallshipsheraldry.registry.HeraldryBlocks;
import me.mss1r.smallshipsheraldry.registry.HeraldryMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;

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
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> SailBannerTextureCache.clear());
        ClientTickEvent.CLIENT_PRE.register(client -> SailBannerTextureCache.tick());
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES,
                (ResourceManagerReloadListener) manager -> SailBannerTextureCache.clear(),
                ResourceLocation.fromNamespaceAndPath(SmallShipsHeraldry.MOD_ID, "sail_textures"));
        if (Platform.isDevelopmentEnvironment()) {
            ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> PaintableSailMesh.verifyModelCompatibility());
        }
    }

    public static void registerScreen() {
        MenuRegistry.registerScreenFactory(HeraldryMenus.HERALDRY_TABLE.get(), HeraldryTableScreen::new);
    }

    public static void openPatternPreview(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new HeraldryPatternPreviewScreen(stack));
        }
    }
}
