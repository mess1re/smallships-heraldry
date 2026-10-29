package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SailPixelProjectionTest {
    @Test
    void cameraTransformMovesScreenCoordinatesWithoutChangingSelectedPixel() {
        var hit = new PaintableSailMesh.Hit(4, "sail", 2, 104, 207, 100, 200, 10, 10);
        var before = SailClipboardMapper.projectPixel(face(0, 0, 20, 0), hit);
        var after = SailClipboardMapper.projectPixel(face(30, 40, 40, 0), hit);

        assertEquals(hit, after.hit());
        assertEquals(before.screenX() * 2 + 30, after.screenX(), 0.0001);
        assertEquals(before.screenY() * 2 + 40, after.screenY(), 0.0001);
    }

    @Test
    void rotatedProjectionUsesTheSameAtlasPixel() {
        var hit = new PaintableSailMesh.Hit(4, "sail", 2, 104, 207, 100, 200, 10, 10);
        var before = SailClipboardMapper.projectPixel(face(0, 0, 20, 0), hit);
        var rotated = SailClipboardMapper.projectPixel(face(100, 50, 0, 20), hit);

        assertEquals(hit, rotated.hit());
        assertEquals(100 - before.screenY(), rotated.screenX(), 0.0001);
        assertEquals(50 + before.screenX(), rotated.screenY(), 0.0001);
    }

    private static PaintableSailMesh.Face face(float x, float y, float dx, float dy) {
        return new PaintableSailMesh.Face(2, 4, "sail", new PaintableSailMesh.Vertex[]{
                vertex(x, y, 0, 0), vertex(x + dx, y + dy, 1, 0),
                vertex(x + dx - dy, y + dy + dx, 1, 1), vertex(x - dy, y + dx, 0, 1)
        }, new Vector3f(0, 0, 1), new PaintableSailMesh.AtlasSlot(2, 100, 200, 10, 10));
    }

    private static PaintableSailMesh.Vertex vertex(float x, float y, float u, float v) {
        return new PaintableSailMesh.Vertex(x, y, 0, u, v, u, v);
    }
}
