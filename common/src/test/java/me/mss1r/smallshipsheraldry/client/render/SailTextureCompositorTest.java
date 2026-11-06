package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SailTextureCompositorTest {
    @Test
    void transparentPaintPreservesTheNativeTexel() {
        assertEquals(0xFF123456, SailTextureCompositor.composite(0xFF123456, 0x00102030));
    }

    @Test
    void opaquePaintReplacesRgbWithoutChangingTheClothOpacity() {
        assertEquals(0xFF123456, SailTextureCompositor.composite(0xFFABCDEF, 0xFF123456));
        assertEquals(0x80123456, SailTextureCompositor.composite(0x80ABCDEF, 0xFF123456));
    }

    @Test
    void translucentPaintIsMixedIntoClothBeforeLighting() {
        assertEquals(0xFF808080, SailTextureCompositor.composite(0xFF000000, 0x80FFFFFF));
        assertEquals(0xFF7F7F7F, SailTextureCompositor.composite(0xFFFFFFFF, 0x80000000));
    }

    @Test
    void evenOpaquePaintCannotFillTornSailHoles() {
        assertEquals(0, SailTextureCompositor.composite(0x00123456, 0xFFFFFFFF));
        assertEquals(0, SailTextureCompositor.composite(0x19123456, 0xFFFFFFFF));
        assertEquals(0x1AFFFFFF, SailTextureCompositor.composite(0x1A123456, 0xFFFFFFFF));
    }

    @Test
    void repeatedNativeUvsGetIndependentPaintAndEdgePadding() {
        var first = face(0, 1, 0, 1);
        var second = face(1, 5, 0, 1);
        var stages = List.of(List.of(first), List.of(second));
        try (var source = new NativeImage(2, 2, true); var atlas = new NativeImage(8, 4, true)) {
            source.setPixelRGBA(0, 0, 0xFF102030);
            source.setPixelRGBA(1, 0, 0xFF405060);
            source.setPixelRGBA(0, 1, 0xFF708090);
            source.setPixelRGBA(1, 1, 0xFFA0B0C0);
            atlas.fillRect(0, 0, 8, 4, 0);
            atlas.setPixelRGBA(1, 1, 0xFF0000FF);
            SailTextureCompositor.compose(atlas, source, stages, false);
            assertEquals(0xFF0000FF, atlas.getPixelRGBA(1, 1));
            assertEquals(0xFF102030, atlas.getPixelRGBA(5, 1));
            assertEquals(0xFF405060, atlas.getPixelRGBA(2, 1));
            assertEquals(atlas.getPixelRGBA(1, 1), atlas.getPixelRGBA(0, 0));
            assertEquals(atlas.getPixelRGBA(2, 2), atlas.getPixelRGBA(3, 3));
        }
    }

    @Test
    void blankAtlasMatchesSourceAndKeepsTheUvOrientation() {
        var face = face(0, 1, 0, 1);
        try (var source = new NativeImage(2, 2, true); var atlas = new NativeImage(4, 4, true)) {
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                source.setPixelRGBA(x, y, 0xFF000000 | (y * 2 + x + 1));
            }
            SailTextureCompositor.buildBase(atlas, source, List.of(List.of(face)), false);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                assertEquals(source.getPixelRGBA(x, y), atlas.getPixelRGBA(x + 1, y + 1));
            }
            // Mirrored faces retain their original vertex/local-UV pairing.
            assertEquals(1, face.vertices()[0].localU());
            assertEquals(0, face.vertices()[1].localU());
        }
    }

    @Test
    void sourceSamplingUsesPixelCentersAndResourcePackResolution() {
        for (int pixel = 0; pixel < 4; pixel++) {
            assertEquals(4 + pixel, SailTextureCompositor.sourcePixel(.25F, .5F, pixel, 4, 16, false));
        }
        assertEquals(9, SailTextureCompositor.sourcePixel(.25F, .5F, 0, 4, 32, false));
        assertEquals(15, SailTextureCompositor.sourcePixel(.25F, .5F, 3, 4, 32, false));
    }

    @Test
    void repeatingAndClampedTexturesKeepTheirNativeAddressing() {
        assertEquals(7, SailTextureCompositor.sourcePixel(1, 1, 0, 1, 8, true));
        assertEquals(0, SailTextureCompositor.sourcePixel(-1, -1, 0, 1, 8, true));
        assertEquals(3, SailTextureCompositor.sourcePixel(.375F, .375F, 0, 1, 8, true));
        assertEquals(0, SailTextureCompositor.sourcePixel(1, 1, 0, 1, 8, false));
        assertEquals(6, SailTextureCompositor.sourcePixel(-.25F, -.25F, 0, 1, 8, false));
        assertEquals(2, SailTextureCompositor.sourcePixel(1.25F, 1.25F, 0, 1, 8, false));
    }

    @Test
    void rebuildingBaseDoesNotKeepPixelsFromThePreviousTexture() {
        var stages = List.of(List.of(face(0, 1, 0, 1)));
        try (var source = new NativeImage(2, 2, true); var atlas = new NativeImage(4, 4, true)) {
            source.fillRect(0, 0, 2, 2, 0xFFFFFFFF);
            SailTextureCompositor.buildBase(atlas, source, stages, false);
            source.fillRect(0, 0, 2, 2, 0xFF000000);
            SailTextureCompositor.buildBase(atlas, source, stages, false);
            assertEquals(0xFF000000, atlas.getPixelRGBA(1, 1));
            assertEquals(0xFF000000, atlas.getPixelRGBA(0, 0));
        }
    }

    private static PaintableSailMesh.Face face(int index, int atlasX, float minU, float maxU) {
        return new PaintableSailMesh.Face(index, index, "sail", new PaintableSailMesh.Vertex[] {
                new PaintableSailMesh.Vertex(0, 0, 0, maxU, 0, 1, 0),
                new PaintableSailMesh.Vertex(1, 0, 0, minU, 0, 0, 0),
                new PaintableSailMesh.Vertex(1, 1, 0, minU, 1, 0, 1),
                new PaintableSailMesh.Vertex(0, 1, 0, maxU, 1, 1, 1)
        }, new Vector3f(0, 0, 1), new PaintableSailMesh.AtlasSlot(index, atlasX, 1, 2, 2));
    }
}
