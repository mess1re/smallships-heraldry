package me.mss1r.smallshipsheraldry.client.render;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SailBannerTextureCacheTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void clearCache() {
        SailBannerTextureCache.clear();
    }

    @Test
    void unchangedComponentsReuseTheDecodedPattern() {
        ItemStack stack = patternStack(0xFF112233);
        CustomData component = stack.get(DataComponents.CUSTOM_DATA);
        HeraldryPatternData first = SailBannerTextureCache.decodePattern(stack, component);
        assertSame(first, SailBannerTextureCache.decodePattern(stack, component));
        assertEquals(0xFF112233, first.baseColorArgb());
    }

    @Test
    void replacingAComponentInvalidatesTheDecodedPattern() {
        ItemStack stack = patternStack(0xFF112233);
        HeraldryPatternData first = SailBannerTextureCache.decodePattern(stack, stack.get(DataComponents.CUSTOM_DATA));
        HeraldryPatternData.blank().withBaseColor(0xFF445566).writeToStack(stack);
        HeraldryPatternData second = SailBannerTextureCache.decodePattern(stack, stack.get(DataComponents.CUSTOM_DATA));
        assertNotSame(first, second);
        assertEquals(0xFF445566, second.baseColorArgb());
    }

    @Test
    void copiesOfComponentNbtCannotMutateTheCachedPattern() {
        ItemStack stack = patternStack(0xFF112233);
        CustomData component = stack.get(DataComponents.CUSTOM_DATA);
        HeraldryPatternData first = SailBannerTextureCache.decodePattern(stack, component);
        component.copyTag().getCompound(HeraldryPatternData.ROOT_TAG)
                .putInt(HeraldryPatternData.BASE_COLOR_TAG, 0xFF445566);
        assertSame(first, SailBannerTextureCache.decodePattern(stack, component));
        assertEquals(0xFF112233, first.baseColorArgb());
    }

    @Test
    void removedPatternDataDoesNotReturnAStaleCachedValue() {
        ItemStack stack = patternStack(0xFF112233);
        SailBannerTextureCache.decodePattern(stack, stack.get(DataComponents.CUSTOM_DATA));
        CustomData component = CustomData.of(new CompoundTag());
        stack.set(DataComponents.CUSTOM_DATA, component);
        assertNull(SailBannerTextureCache.decodePattern(stack, component));
    }

    @Test
    void clearingTheCacheForcesANewDecode() {
        ItemStack stack = patternStack(0xFF112233);
        CustomData component = stack.get(DataComponents.CUSTOM_DATA);
        HeraldryPatternData first = SailBannerTextureCache.decodePattern(stack, component);
        SailBannerTextureCache.clear();
        assertNotSame(first, SailBannerTextureCache.decodePattern(stack, component));
    }

    private static ItemStack patternStack(int baseColor) {
        ItemStack stack = new ItemStack(Items.PAPER);
        HeraldryPatternData.blank().withBaseColor(baseColor).writeToStack(stack);
        return stack;
    }
}
