package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.util.FastColor;

public final class HeraldryTextureGenerator {
    public static final int ATLAS_WIDTH = HeraldryPatternData.MESH_ATLAS_WIDTH;
    public static final int ATLAS_HEIGHT = HeraldryPatternData.MESH_ATLAS_HEIGHT;

    private HeraldryTextureGenerator() {
    }

    public static NativeImage buildComposedTexture(HeraldryPatternData data) {
        NativeImage image = new NativeImage(ATLAS_WIDTH, ATLAS_HEIGHT, true);
        data.forEachRun((start, length, argb) -> {
            int abgr = argbToAbgr(argb);
            int end = start + length;
            for (int index = start; index < end; index++) {
                image.setPixelRGBA(index % ATLAS_WIDTH, index / ATLAS_WIDTH, abgr);
            }
        });
        return image;
    }

    public static int argbToAbgr(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return FastColor.ABGR32.color(alpha, blue, green, red);
    }
}
