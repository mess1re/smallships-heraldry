package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.function.Consumer;

/** A single overflow texture, never referenced by deferred entity buffers. */
final class StreamingSailTexture<K> implements AutoCloseable {
    private DynamicTexture texture;
    private ResourceLocation location;
    private BufferBuilder vertices;
    private MultiBufferSource.BufferSource buffers;
    private K uploadedKey;

    void render(K key, ImageWriter paint, MultiBufferSource entityBuffers, Consumer<VertexConsumer> draw) throws IOException {
        if (texture == null) {
            texture = new DynamicTexture(HeraldryTextureGenerator.ATLAS_WIDTH, HeraldryTextureGenerator.ATLAS_HEIGHT, true);
            location = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "dynamic/sails/overflow");
            Minecraft.getInstance().getTextureManager().register(location, texture);
            vertices = new BufferBuilder(1536);
            buffers = MultiBufferSource.immediate(vertices);
        }
        if (!key.equals(uploadedKey)) {
            uploadedKey = null;
            paint.write(texture.getPixels());
            texture.upload();
            uploadedKey = key;
        }

        RenderType renderType = RenderType.entityTranslucent(location);
        // Make the same buffer transition as a resident overlay, so the base sail is
        // drawn first. Otherwise translucent paint would blend with the background.
        // No vertices are written into this deferred buffer.
        entityBuffers.getBuffer(renderType);

        // Complete this draw before another ship can overwrite the shared texture.
        // The main/outline buffer source must never retain vertices referencing it.
        try {
            draw.accept(buffers.getBuffer(renderType));
        } finally {
            buffers.endBatch();
        }
    }

    @Override
    public void close() {
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        } else if (texture != null) {
            texture.close();
        }
        location = null;
        texture = null;
        vertices = null;
        buffers = null;
        uploadedKey = null;
    }

    @FunctionalInterface
    interface ImageWriter {
        void write(NativeImage image) throws IOException;
    }
}
