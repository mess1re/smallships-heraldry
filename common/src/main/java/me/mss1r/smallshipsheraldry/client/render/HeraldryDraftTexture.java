package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** A single mutable GPU texture used only for the open editor. */
public final class HeraldryDraftTexture implements AutoCloseable {
    private final ResourceLocation textureLocation;
    private final DynamicTexture dynamicTexture;
    private boolean dirty;
    private boolean closed;

    public HeraldryDraftTexture(String id, int[] initialPixels) {
        this.dynamicTexture = new DynamicTexture(
                HeraldryPatternData.MESH_ATLAS_WIDTH,
                HeraldryPatternData.MESH_ATLAS_HEIGHT,
                true
        );
        this.textureLocation = Minecraft.getInstance().getTextureManager().register(
                SmallShipsHeraldry.MOD_ID + "/draft/" + id + "/" + UUID.randomUUID(),
                dynamicTexture
        );
        replacePixels(initialPixels);
        uploadIfDirty();
    }

    public ResourceLocation textureLocation() {
        return textureLocation;
    }

    public void setPixel(int x, int y, int argb) {
        if (closed || x < 0 || x >= HeraldryPatternData.MESH_ATLAS_WIDTH
                || y < 0 || y >= HeraldryPatternData.MESH_ATLAS_HEIGHT) {
            return;
        }
        pixels().setPixelRGBA(x, y, HeraldryTextureGenerator.argbToAbgr(argb));
        dirty = true;
    }

    public void replacePixels(int[] source) {
        if (closed || source.length < HeraldryPatternData.MESH_PIXEL_COUNT) {
            return;
        }
        NativeImage pixels = pixels();
        int width = HeraldryPatternData.MESH_ATLAS_WIDTH;
        for (int index = 0; index < HeraldryPatternData.MESH_PIXEL_COUNT; index++) {
            pixels.setPixelRGBA(index % width, index / width, HeraldryTextureGenerator.argbToAbgr(source[index]));
        }
        dirty = true;
    }

    public void uploadIfDirty() {
        if (!closed && dirty) {
            dynamicTexture.upload();
            dirty = false;
        }
    }

    private NativeImage pixels() {
        return Objects.requireNonNull(dynamicTexture.getPixels(), "Heraldry draft texture has no pixel buffer");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Minecraft.getInstance().getTextureManager().release(textureLocation);
    }
}
