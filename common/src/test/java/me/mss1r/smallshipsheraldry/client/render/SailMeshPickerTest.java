package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SailMeshPickerTest {
    @Test
    void mapsScreenPositionToAtlasPixel() {
        PaintableSailMesh.Face face = face("sail", 0.0F, 5, 10, 20, 8, 4);

        PaintableSailMesh.Hit hit = SailMeshPicker.pick(List.of(face), 50.0, 25.0, Set.of()).orElseThrow();

        assertEquals(14, hit.pixelX());
        assertEquals(21, hit.pixelY());
        assertEquals("sail", hit.elementId());
    }

    @Test
    void picksFrontFaceAndHonorsHiddenElements() {
        PaintableSailMesh.Face back = face("back", 0.0F, 1, 0, 0, 4, 4);
        PaintableSailMesh.Face front = face("front", 2.0F, 2, 8, 8, 4, 4);

        assertEquals("front", SailMeshPicker.pick(List.of(back, front), 50.0, 50.0, Set.of()).orElseThrow().elementId());
        assertEquals("back", SailMeshPicker.pick(List.of(back, front), 50.0, 50.0, Set.of("front")).orElseThrow().elementId());
        assertTrue(SailMeshPicker.pick(List.of(front), 50.0, 50.0, Set.of("front")).isEmpty());
    }

    private static PaintableSailMesh.Face face(
            String elementId,
            float depth,
            int index,
            int slotX,
            int slotY,
            int slotWidth,
            int slotHeight
    ) {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(0.0F, 0.0F, depth, 0.0F, 0.0F, 0.0F, 0.0F),
                new PaintableSailMesh.Vertex(100.0F, 0.0F, depth, 1.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(100.0F, 100.0F, depth, 1.0F, 1.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(0.0F, 100.0F, depth, 0.0F, 1.0F, 0.0F, 1.0F)
        };
        PaintableSailMesh.AtlasSlot slot = new PaintableSailMesh.AtlasSlot(index, slotX, slotY, slotWidth, slotHeight);
        return new PaintableSailMesh.Face(index, 3, elementId, vertices, new Vector3f(0.0F, 0.0F, 1.0F), slot);
    }
}
