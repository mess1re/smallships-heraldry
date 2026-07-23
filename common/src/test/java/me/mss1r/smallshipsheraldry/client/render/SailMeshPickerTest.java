package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    @Test
    void spatialIndexKeepsLargeMeshesOutOfEachPick() {
        List<PaintableSailMesh.Face> faces = new ArrayList<>();
        for (int index = 0; index < 2_208; index++) {
            int x = (index % 48) * 20;
            int y = (index / 48) * 20;
            faces.add(faceAt("face_" + index, index, x, y, 10));
        }

        SailMeshPicker.Index index = SailMeshPicker.index(faces);

        assertTrue(index.candidateCountAt(5.0, 5.0) <= 1);
        assertTrue(index.candidateCountAt(800.0, 800.0) <= 1);
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

    private static PaintableSailMesh.Face faceAt(String elementId, int index, float x, float y, float size) {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(x, y, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new PaintableSailMesh.Vertex(x + size, y, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(x + size, y + size, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(x, y + size, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F)
        };
        PaintableSailMesh.AtlasSlot slot = new PaintableSailMesh.AtlasSlot(index, 0, 0, 10, 10);
        return new PaintableSailMesh.Face(index, 4, elementId, vertices, new Vector3f(0.0F, 0.0F, 1.0F), slot);
    }
}
