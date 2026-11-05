package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SailPaintShadingTest {
    @Test
    void narrowRimsMatchThePanelWithoutMovingPixels() {
        var panel = face(0, new Vector3f(0, 0, 1), v(0, 0, 0), v(2, 0, 0), v(2, 2, 0), v(0, 2, 0));
        var rim = face(1, new Vector3f(0, -1, 0), v(0, 0, 0), v(2, 0, 0), v(2, 0, .01F), v(0, 0, .01F));
        var source = List.of(panel, rim);
        var shaded = SailPaintShading.build(source);
        assertTrue(shaded.get(1).vertices()[0].shadingNormal().z() > .99F);
        assertTrue(panel.normal().dot(shaded.get(0).vertices()[0].shadingNormal()) > .99F);
        for (int f = 0; f < source.size(); f++) for (int index = 0; index < 4; index++) {
            var before = source.get(f).vertices()[index];
            var after = shaded.get(f).vertices()[index];
            assertEquals(before.x(), after.x());
            assertEquals(before.y(), after.y());
            assertEquals(before.z(), after.z());
            assertEquals(before.originalU(), after.originalU());
            assertEquals(before.localV(), after.localV());
            assertEquals(1, after.shadingNormal().length(), .00001);
            assertTrue(after.shadingNormal().isFinite());
        }
    }

    @Test
    void oppositeClothSidesKeepTheirOwnLightDirection() {
        var front = face(0, new Vector3f(0, 0, 1), v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(0, 1, 0));
        var back = face(1, new Vector3f(0, 0, -1), front.vertices());
        var shaded = SailPaintShading.build(List.of(front, back));
        assertEquals(1, shaded.get(0).vertices()[0].shadingNormal().z());
        assertEquals(-1, shaded.get(1).vertices()[0].shadingNormal().z());
    }

    @Test
    void ropesKeepTheirOriginalFacetedShading() {
        var cloth = face(0, new Vector3f(0, 0, 1), v(0, 0, 0), v(2, 0, 0), v(2, 2, 0), v(0, 2, 0));
        var rope = new PaintableSailMesh.Face(1, 4, "rope_15", cloth.vertices(),
                new Vector3f(1, 0, 0), cloth.slot());
        assertSame(rope, SailPaintShading.build(List.of(cloth, rope)).get(1));
    }

    private static PaintableSailMesh.Face face(int id, Vector3f normal, PaintableSailMesh.Vertex... vertices) {
        return new PaintableSailMesh.Face(id, 4, "sail", vertices, normal,
                new PaintableSailMesh.AtlasSlot(id, 1 + id * 18, 1, 16, 16));
    }
    private static PaintableSailMesh.Vertex v(float x, float y, float z) {
        return new PaintableSailMesh.Vertex(x, y, z, .2F, .3F, .4F, .5F);
    }
}
