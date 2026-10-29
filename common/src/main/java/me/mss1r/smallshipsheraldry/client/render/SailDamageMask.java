package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.util.Mth;

import java.util.List;

final class SailDamageMask {
    private SailDamageMask() {
    }

    static void apply(NativeImage paint, NativeImage sail, ShipPreviewType shipType, boolean clamp) {
        for (var faces : SailMeshDefinitionBuilder.definition(shipType).facesByState().values()) {
            applyFaces(paint, sail, faces, clamp);
        }
    }

    private static void applyFaces(NativeImage paint, NativeImage sail, List<PaintableSailMesh.Face> faces, boolean clamp) {
        for (var face : faces) {
            float minU = Float.POSITIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (var vertex : face.vertices()) {
                minU = Math.min(minU, vertex.originalU());
                minV = Math.min(minV, vertex.originalV());
                maxU = Math.max(maxU, vertex.originalU());
                maxV = Math.max(maxV, vertex.originalV());
            }
            // Atlas-local UVs are normalized original UVs, including on mirrored/back faces.
            var slot = face.slot();
            for (int y = 0; y < slot.height(); y++) {
                int sourceY = sourcePixel(minV, maxV, y, slot.height(), sail.getHeight(), clamp);
                for (int x = 0; x < slot.width(); x++) {
                    int color = paint.getPixelRGBA(slot.x() + x, slot.y() + y);
                    if ((color >>> 24) == 0) {
                        continue;
                    }
                    int sourceX = sourcePixel(minU, maxU, x, slot.width(), sail.getWidth(), clamp);
                    int alpha = sail.getPixelRGBA(sourceX, sourceY) >>> 24;
                    paint.setPixelRGBA(slot.x() + x, slot.y() + y, maskedColor(color, alpha));
                }
            }
        }
    }

    static int sourcePixel(float min, float max, int pixel, int tileSize, int textureSize, boolean clamp) {
        float uv = Mth.lerp((pixel + 0.5F) / tileSize, min, max);
        int source = Mth.floor(uv * textureSize);
        return clamp ? Mth.clamp(source, 0, textureSize - 1) : Math.floorMod(source, textureSize);
    }

    static int maskedColor(int paintAbgr, int sailAlpha) {
        // Match the base sail's cutout threshold; translucent paint keeps its own opacity.
        int alpha = sailAlpha < 26 ? 0 : (paintAbgr >>> 24) * sailAlpha / 255;
        return (paintAbgr & 0x00FFFFFF) | (alpha << 24);
    }
}
