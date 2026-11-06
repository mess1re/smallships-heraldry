package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Mth;

import java.util.List;

/** Bakes paint into each face's copy of the native sail texture. */
final class SailTextureCompositor {
    private SailTextureCompositor() {
    }

    static void buildBase(NativeImage atlas, NativeImage source,
                          Iterable<List<PaintableSailMesh.Face>> stages, boolean clamp) {
        atlas.fillRect(0, 0, atlas.getWidth(), atlas.getHeight(), 0);
        fillFaces(atlas, source, stages, clamp, false);
    }

    static void compose(NativeImage paint, NativeImage source,
                        Iterable<List<PaintableSailMesh.Face>> stages, boolean clamp) {
        fillFaces(paint, source, stages, clamp, true);
    }

    private static void fillFaces(NativeImage atlas, NativeImage source,
                                  Iterable<List<PaintableSailMesh.Face>> stages, boolean clamp, boolean painted) {
        for (var faces : stages) for (var face : faces) {
            float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
            for (var vertex : face.vertices()) {
                minU = Math.min(minU, vertex.originalU());
                minV = Math.min(minV, vertex.originalV());
                maxU = Math.max(maxU, vertex.originalU());
                maxV = Math.max(maxV, vertex.originalV());
            }
            var slot = face.slot();
            for (int y = 0; y < slot.height(); y++) {
                int sourceY = sourcePixel(minV, maxV, y, slot.height(), source.getHeight(), clamp);
                for (int x = 0; x < slot.width(); x++) {
                    int sourceX = sourcePixel(minU, maxU, x, slot.width(), source.getWidth(), clamp);
                    int base = source.getPixelRGBA(sourceX, sourceY);
                    int color = painted ? composite(base, atlas.getPixelRGBA(slot.x() + x, slot.y() + y)) : base;
                    atlas.setPixelRGBA(slot.x() + x, slot.y() + y, color);
                }
            }
            padFace(atlas, slot);
        }
    }

    static void padFaces(NativeImage atlas, Iterable<List<PaintableSailMesh.Face>> stages) {
        for (var faces : stages) for (var face : faces) padFace(atlas, face.slot());
    }

    private static void padFace(NativeImage atlas, PaintableSailMesh.AtlasSlot slot) {
        // The existing layout reserves one texel around every face.
        for (int x = -1; x <= slot.width(); x++) {
            int sourceX = slot.x() + Mth.clamp(x, 0, slot.width() - 1);
            atlas.setPixelRGBA(slot.x() + x, slot.y() - 1, atlas.getPixelRGBA(sourceX, slot.y()));
            atlas.setPixelRGBA(slot.x() + x, slot.y() + slot.height(),
                    atlas.getPixelRGBA(sourceX, slot.y() + slot.height() - 1));
        }
        for (int y = 0; y < slot.height(); y++) {
            atlas.setPixelRGBA(slot.x() - 1, slot.y() + y, atlas.getPixelRGBA(slot.x(), slot.y() + y));
            atlas.setPixelRGBA(slot.x() + slot.width(), slot.y() + y,
                    atlas.getPixelRGBA(slot.x() + slot.width() - 1, slot.y() + y));
        }
    }

    static int sourcePixel(float min, float max, int pixel, int tileSize, int textureSize, boolean clamp) {
        int source = Mth.floor(Mth.lerp((pixel + 0.5F) / tileSize, min, max) * textureSize);
        return clamp ? Mth.clamp(source, 0, textureSize - 1) : Math.floorMod(source, textureSize);
    }

    static int composite(int baseAbgr, int paintAbgr) {
        int alpha = paintAbgr >>> 24;
        // Paint changes the colour of existing cloth, never fills its holes.
        if ((baseAbgr >>> 24) < 26) return 0;
        if (alpha == 0) return baseAbgr;
        int color = baseAbgr & 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int base = (baseAbgr >>> shift) & 255;
            int paint = (paintAbgr >>> shift) & 255;
            color |= ((paint * alpha + base * (255 - alpha) + 127) / 255) << shift;
        }
        return color;
    }
}
