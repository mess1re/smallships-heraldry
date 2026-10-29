package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class SailBannerTextureCache {
    // Reserve one atlas for immediate overflow draws. Each GPU atlas also has a native copy.
    private static final long TEXTURE_BUDGET_BYTES = 192L * 1024 * 1024;
    private static final int TEXTURE_BYTES = HeraldryTextureGenerator.ATLAS_WIDTH * HeraldryTextureGenerator.ATLAS_HEIGHT * 4;
    static final int MAX_RESIDENT_TEXTURES = (int) (TEXTURE_BUDGET_BYTES / TEXTURE_BYTES) - 1;
    private static final Map<ItemStack, StackPattern> STACK_PATTERN_CACHE = new WeakHashMap<>();
    private static final TextureResidencyCache<TextureKey, ResourceLocation> TEXTURE_CACHE =
            new TextureResidencyCache<>(MAX_RESIDENT_TEXTURES, 2, SailBannerTextureCache::release);
    private static final StreamingSailTexture<TextureKey> OVERFLOW = new StreamingSailTexture<>();
    private static ResourceLocation damageLocation;
    private static NativeImage damageImage;
    private static boolean damageClamp;
    private static boolean warnedStreamingFailure;
    private static ClientLevel cachedLevel;

    private SailBannerTextureCache() {
    }

    public static void render(HeraldryPatternData pattern, ResourceLocation damageTexture,
                              MultiBufferSource buffers, Consumer<VertexConsumer> draw) {
        clearForLevelChange();
        if (pattern == null || pattern.paintedPixelCount() == 0) {
            return;
        }

        TextureKey cacheKey = new TextureKey(pattern.visualHash(), damageTexture);
        TEXTURE_CACHE.use(cacheKey, () -> createTexture(cacheKey, pattern),
                texture -> draw.accept(buffers.getBuffer(RenderType.entityTranslucent(texture))),
                () -> {
                    try {
                        OVERFLOW.render(cacheKey, image -> paintTexture(image, cacheKey, pattern), buffers, draw);
                    } catch (IOException | RuntimeException exception) {
                        if (!warnedStreamingFailure) {
                            warnedStreamingFailure = true;
                            SmallShipsHeraldry.LOGGER.warn("Failed to render overflow heraldry sail texture", exception);
                        }
                    }
                });
    }

    public static HeraldryPatternData getPatternData(ItemStack sailPattern) {
        clearForLevelChange();
        if (sailPattern == null || !sailPattern.is(HeraldryItems.HERALDRY_PATTERN.get())) {
            return null;
        }
        CustomData customData = sailPattern.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        return decodePattern(sailPattern, customData);
    }

    static HeraldryPatternData decodePattern(ItemStack sailPattern, CustomData customData) {
        StackPattern cached = STACK_PATTERN_CACHE.get(sailPattern);
        // CustomData updates replace the component; unchanged stacks need no NBT copy or hash.
        if (cached != null && cached.customData() == customData) {
            return cached.data();
        }
        CompoundTag stackTag = customData.copyTag();
        if (!stackTag.contains(HeraldryPatternData.ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag rootTag = stackTag.getCompound(HeraldryPatternData.ROOT_TAG);
        HeraldryPatternData parsed = HeraldryPatternData.fromRootTag(rootTag);
        STACK_PATTERN_CACHE.put(sailPattern, new StackPattern(customData, parsed));
        return parsed;
    }

    public static void clear() {
        TEXTURE_CACHE.clear();
        OVERFLOW.close();
        clearDamageImage();
        warnedStreamingFailure = false;
        STACK_PATTERN_CACHE.clear();
        cachedLevel = null;
    }

    public static void tick() {
        TEXTURE_CACHE.advanceTick();
    }

    private static void clearForLevelChange() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel currentLevel = minecraft == null ? null : minecraft.level;
        if (currentLevel == cachedLevel) {
            return;
        }
        clear();
        cachedLevel = currentLevel;
    }

    private static ResourceLocation createTexture(TextureKey cacheKey, HeraldryPatternData patternData) {
        NativeImage image = null;
        DynamicTexture dynamicTexture = null;
        try {
            image = new NativeImage(HeraldryTextureGenerator.ATLAS_WIDTH, HeraldryTextureGenerator.ATLAS_HEIGHT, true);
            paintTexture(image, cacheKey, patternData);
            dynamicTexture = new DynamicTexture(image);
            image = null; // DynamicTexture owns it from this point on.
            // TextureManager retains a counter per prefix, even after a dynamic texture is released.
            String textureId = SmallShipsHeraldry.MOD_ID + "/sails/pattern";
            return Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
        } catch (IOException | RuntimeException exception) {
            if (dynamicTexture != null) {
                dynamicTexture.close();
            } else if (image != null) {
                image.close();
            }
            SmallShipsHeraldry.LOGGER.warn("Failed to build heraldry sail texture", exception);
            return null;
        }
    }

    private static void paintTexture(NativeImage image, TextureKey key, HeraldryPatternData pattern) throws IOException {
        HeraldryTextureGenerator.paintInto(image, pattern);
        if (key.damageTexture() == null) return;
        if (!key.damageTexture().equals(damageLocation)) {
            clearDamageImage();
            var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(key.damageTexture());
            boolean clamp = resource.metadata().getSection(TextureMetadataSection.SERIALIZER)
                    .map(TextureMetadataSection::isClamp).orElse(false);
            try (var input = resource.open()) {
                damageImage = NativeImage.read(input);
            }
            damageLocation = key.damageTexture();
            damageClamp = clamp;
        }
        SailDamageMask.apply(image, damageImage, pattern.shipType(), damageClamp);
    }

    private static void clearDamageImage() {
        if (damageImage != null) damageImage.close();
        damageImage = null;
        damageLocation = null;
    }

    private static void release(ResourceLocation location) {
        if (location == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getTextureManager().release(location);
        }
    }

    private record StackPattern(CustomData customData, HeraldryPatternData data) {
    }

    private record TextureKey(long visualHash, ResourceLocation damageTexture) {
    }
}
