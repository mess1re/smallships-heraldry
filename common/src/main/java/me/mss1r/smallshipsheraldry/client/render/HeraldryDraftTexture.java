package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Mutable composed atlas; brush strokes update only their changed pixels. */
public final class HeraldryDraftTexture implements AutoCloseable {
    private final ResourceLocation textureLocation;
    private final DynamicTexture dynamicTexture;
    private final int[] paint = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
    private NativeImage baseAtlas;
    private ShipPreviewType shipType;
    private ResourceLocation baseLocation;
    private long resourceGeneration = -1;
    private boolean dirty;
    private boolean rebuild;
    private boolean closed;
    private boolean warnedFailure;

    public HeraldryDraftTexture(String id, int[] initialPixels) {
        this.dynamicTexture = new DynamicTexture(
                HeraldryPatternData.MESH_ATLAS_WIDTH, HeraldryPatternData.MESH_ATLAS_HEIGHT, true);
        this.textureLocation = Minecraft.getInstance().getTextureManager().register(
                SmallShipsHeraldry.MOD_ID + "/draft/" + id + "/" + UUID.randomUUID(), dynamicTexture);
        replacePixels(initialPixels);
    }

    ResourceLocation prepare(ShipPreviewType type, ResourceLocation sourceLocation) {
        if (closed) return null;
        try {
            ResourceLocation result = prepareTexture(type, sourceLocation);
            warnedFailure = false;
            return result;
        } catch (IOException | RuntimeException error) {
            if (!warnedFailure) SmallShipsHeraldry.LOGGER.warn("Failed to compose heraldry draft texture", error);
            warnedFailure = true;
            return null;
        }
    }

    private ResourceLocation prepareTexture(ShipPreviewType type, ResourceLocation sourceLocation) throws IOException {
        if (closed) throw new IllegalStateException("Heraldry draft texture is closed");
        if (baseAtlas == null || type != shipType || !sourceLocation.equals(baseLocation)
                || resourceGeneration != SailTextureSources.generation()) {
            var source = SailTextureSources.get(sourceLocation);
            NativeImage replacement = new NativeImage(
                    HeraldryPatternData.MESH_ATLAS_WIDTH, HeraldryPatternData.MESH_ATLAS_HEIGHT, true);
            try {
                SailTextureCompositor.buildBase(replacement, source.image(),
                        SailMeshDefinitionBuilder.definition(type).facesByState().values(), source.clamp());
            } catch (RuntimeException error) {
                replacement.close();
                throw error;
            }
            if (baseAtlas != null) baseAtlas.close();
            baseAtlas = replacement;
            shipType = type;
            baseLocation = sourceLocation;
            resourceGeneration = SailTextureSources.generation();
            rebuild = true;
            dirty = true;
        }
        uploadIfDirty();
        return textureLocation;
    }

    public void setPixel(int x, int y, int argb) {
        if (closed || x < 0 || x >= HeraldryPatternData.MESH_ATLAS_WIDTH
                || y < 0 || y >= HeraldryPatternData.MESH_ATLAS_HEIGHT) return;
        writePixel(x, y, argb);
        dirty = true;
    }

    private void writePixel(int x, int y, int argb) {
        paint[y * HeraldryPatternData.MESH_ATLAS_WIDTH + x] = argb;
        if (baseAtlas != null && !rebuild) {
            pixels().setPixelRGBA(x, y, SailTextureCompositor.composite(
                    baseAtlas.getPixelRGBA(x, y), HeraldryTextureGenerator.argbToAbgr(argb)));
        }
    }

    public void replacePixels(int[] source) {
        if (closed || source.length < paint.length) return;
        System.arraycopy(source, 0, paint, 0, paint.length);
        rebuild = true;
        dirty = true;
    }

    public void updateRegion(int[] source, int x, int y, int width, int height) {
        if (closed || source.length < paint.length || width <= 0 || height <= 0) return;
        int minX = Math.max(0, x), minY = Math.max(0, y);
        int maxX = Math.min(HeraldryPatternData.MESH_ATLAS_WIDTH, x + width);
        int maxY = Math.min(HeraldryPatternData.MESH_ATLAS_HEIGHT, y + height);
        for (int localY = minY; localY < maxY; localY++) {
            int row = localY * HeraldryPatternData.MESH_ATLAS_WIDTH;
            for (int localX = minX; localX < maxX; localX++) {
                writePixel(localX, localY, source[row + localX]);
            }
        }
        dirty = true;
    }

    public void uploadIfDirty() {
        if (closed || !dirty || baseAtlas == null) return;
        NativeImage output = pixels();
        if (rebuild) {
            int width = HeraldryPatternData.MESH_ATLAS_WIDTH;
            for (int index = 0; index < paint.length; index++) {
                int x = index % width, y = index / width;
                output.setPixelRGBA(x, y, SailTextureCompositor.composite(
                        baseAtlas.getPixelRGBA(x, y), HeraldryTextureGenerator.argbToAbgr(paint[index])));
            }
            rebuild = false;
        }
        SailTextureCompositor.padFaces(output,
                SailMeshDefinitionBuilder.definition(shipType).facesByState().values());
        dynamicTexture.upload();
        dirty = false;
    }

    private NativeImage pixels() {
        return Objects.requireNonNull(dynamicTexture.getPixels(), "Heraldry draft texture has no pixel buffer");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (baseAtlas != null) baseAtlas.close();
        Minecraft.getInstance().getTextureManager().release(textureLocation);
    }
}
