package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SailBannerTextureCache {
    private static final int MAX_CACHED_TEXTURES = 8;
    private static final Map<ItemStack, StackPattern> STACK_PATTERN_CACHE = new WeakHashMap<>();
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() <= MAX_CACHED_TEXTURES) {
                return false;
            }
            release(eldest.getValue());
            return true;
        }
    };
    private static ClientLevel cachedLevel;

    private SailBannerTextureCache() {
    }

    public static ResourceLocation getTexture(Ship ship, ItemStack sailPattern) {
        clearForLevelChange();
        HeraldryPatternData pattern = getPatternData(sailPattern);
        if (pattern == null || pattern.paintedPixelCount() == 0) {
            return null;
        }

        String cacheKey = ship.getClass().getName() + "|" + Long.toUnsignedString(pattern.visualHash(), 16);
        ResourceLocation cached = TEXTURE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ResourceLocation created = createTexture(cacheKey, pattern);
        if (created != null) {
            TEXTURE_CACHE.put(cacheKey, created);
        }
        return created;
    }

    public static HeraldryPatternData getPatternData(ItemStack sailPattern) {
        clearForLevelChange();
        if (sailPattern == null || !sailPattern.is(HeraldryItems.HERALDRY_PATTERN.get())) {
            return null;
        }
        CompoundTag stackTag = sailPattern.getTag();
        if (stackTag == null || !stackTag.contains(HeraldryPatternData.ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag rootTag = stackTag.getCompound(HeraldryPatternData.ROOT_TAG);
        int tagHash = rootTag.hashCode();
        StackPattern cached = STACK_PATTERN_CACHE.get(sailPattern);
        if (cached != null && cached.rootTag() == rootTag && cached.tagHash() == tagHash) {
            return cached.data();
        }

        HeraldryPatternData parsed = HeraldryPatternData.fromRootTag(rootTag);
        STACK_PATTERN_CACHE.put(sailPattern, new StackPattern(rootTag, tagHash, parsed));
        return parsed;
    }

    public static void clear() {
        for (ResourceLocation location : TEXTURE_CACHE.values()) {
            release(location);
        }
        TEXTURE_CACHE.clear();
        STACK_PATTERN_CACHE.clear();
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

    private static ResourceLocation createTexture(String cacheKey, HeraldryPatternData patternData) {
        NativeImage image = null;
        DynamicTexture dynamicTexture = null;
        try {
            image = HeraldryTextureGenerator.buildComposedTexture(patternData);
            dynamicTexture = new DynamicTexture(image);
            image = null; // DynamicTexture owns it from this point on.
            String textureId = SmallShipsHeraldry.MOD_ID + "/sails/"
                    + UUID.nameUUIDFromBytes(cacheKey.getBytes(StandardCharsets.UTF_8));
            return Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
        } catch (RuntimeException exception) {
            if (dynamicTexture != null) {
                dynamicTexture.close();
            } else if (image != null) {
                image.close();
            }
            SmallShipsHeraldry.LOGGER.warn("Failed to build heraldry sail texture", exception);
            return null;
        }
    }

    private static void release(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getTextureManager().release(location);
        }
    }

    private record StackPattern(CompoundTag rootTag, int tagHash, HeraldryPatternData data) {
    }
}
