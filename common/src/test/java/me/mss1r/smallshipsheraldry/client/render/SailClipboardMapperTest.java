package me.mss1r.smallshipsheraldry.client.render;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SailClipboardMapperTest {
    @Test
    void mapsPhysicalPixelOffsetsWithoutUsingScreenScale() {
        PaintableSailMesh.Face face = face(0, 0, 0, 30, 30, 2.0F);
        PaintableSailMesh.SurfacePoint target = SailClipboardMapper.point(face, 15, 15);

        Map<Integer, Integer> mapped = SailClipboardMapper.map(
                List.of(face),
                target,
                List.of(new PaintableSailMesh.ClipboardPixel(1.0F / 15.0F, 0.0F, 0xFF336699)),
                0,
                Set.of(),
                Set.of()
        );

        assertEquals(0xFF336699, mapped.get(15 * HeraldryPatternData.MESH_ATLAS_WIDTH + 16));
    }

    @Test
    void respectsSelectedAndHiddenElements() {
        PaintableSailMesh.Face sail = face(0, 0, 0, 15, 15, 1.0F);
        PaintableSailMesh.Face other = face(1, 20, 0, 15, 15, 1.0F);
        PaintableSailMesh.SurfacePoint target = SailClipboardMapper.point(sail, 7, 7);
        List<PaintableSailMesh.ClipboardPixel> clipboard =
                List.of(new PaintableSailMesh.ClipboardPixel(0.0F, 0.0F, 0xFFFFFFFF));

        assertEquals(1, SailClipboardMapper.map(
                List.of(sail, other), target, clipboard, 0, Set.of("sail"), Set.of()
        ).size());
        assertEquals(Map.of(), SailClipboardMapper.map(
                List.of(sail, other), target, clipboard, 0, Set.of(), Set.of("sail", "other")
        ));
    }

    @Test
    void crossesConnectedFaceBoundaryWithoutLocalCoordinateReset() {
        PaintableSailMesh.Face first = faceAt(0, "first", 0, 0.0F, 1.0F, 15);
        PaintableSailMesh.Face second = faceAt(1, "second", 20, 1.0F, 2.0F, 15);
        PaintableSailMesh.SurfacePoint target = SailClipboardMapper.point(first, 7, 7);

        Map<Integer, Integer> mapped = SailClipboardMapper.map(
                List.of(first, second),
                target,
                List.of(new PaintableSailMesh.ClipboardPixel(1.2F, 0.0F, 0xFFFFFFFF)),
                0,
                Set.of(),
                Set.of()
        );

        assertEquals(0xFFFFFFFF, mapped.get(7 * HeraldryPatternData.MESH_ATLAS_WIDTH + 30));
    }

    @Test
    void doesNotJumpToDisconnectedSurface() {
        PaintableSailMesh.Face first = faceAt(0, "first", 0, 0.0F, 1.0F, 15);
        PaintableSailMesh.Face disconnected = faceAt(1, "other", 20, 5.0F, 6.0F, 15);
        PaintableSailMesh.SurfacePoint target = SailClipboardMapper.point(first, 7, 7);

        Map<Integer, Integer> mapped = SailClipboardMapper.map(
                List.of(first, disconnected),
                target,
                List.of(new PaintableSailMesh.ClipboardPixel(5.2F, 0.0F, 0xFFFFFFFF)),
                0,
                Set.of(),
                Set.of()
        );

        assertEquals(Map.of(), mapped);
    }

    @Test
    void stitchesFacesWithOppositeLocalUvDirectionsIntoOneSurface() {
        PaintableSailMesh.Face first = faceAt(0, "first", 0, 0.0F, 1.0F, 15);
        PaintableSailMesh.Face reversed = reversedFaceAt(1, "second", 20, 1.0F, 2.0F, 15);
        List<PaintableSailMesh.Face> faces = List.of(first, reversed);

        PaintableSailMesh.SurfacePoint firstPoint = SailClipboardMapper.point(faces, first, 14, 7);
        PaintableSailMesh.SurfacePoint secondPoint = SailClipboardMapper.point(faces, reversed, 14, 7);
        float deltaU = firstPoint.u() - secondPoint.u();
        float deltaV = firstPoint.v() - secondPoint.v();

        assertEquals(firstPoint.componentId(), secondPoint.componentId());
        assertEquals(1.0F / 15.0F, (float) Math.sqrt(deltaU * deltaU + deltaV * deltaV), 0.000001F);
    }

    @Test
    void keepsPasteContinuousWhenAnchorCrossesReversedFaceBoundary() {
        PaintableSailMesh.Face first = faceAt(0, "first", 0, 0.0F, 1.0F, 15);
        PaintableSailMesh.Face reversed = reversedFaceAt(1, "second", 20, 1.0F, 2.0F, 15);
        List<PaintableSailMesh.Face> faces = List.of(first, reversed);
        List<PaintableSailMesh.ClipboardPixel> clipboard = List.of(
                new PaintableSailMesh.ClipboardPixel(-1.0F / 15.0F, 0.0F, 0xFFFF0000),
                new PaintableSailMesh.ClipboardPixel(0.0F, 0.0F, 0xFFFF0000),
                new PaintableSailMesh.ClipboardPixel(1.0F / 15.0F, 0.0F, 0xFFFF0000)
        );

        Map<Integer, Integer> beforeBoundary = SailClipboardMapper.map(
                faces,
                SailClipboardMapper.point(faces, first, 14, 7),
                clipboard,
                first.faceIndex(),
                Set.of(),
                Set.of()
        );
        Map<Integer, Integer> afterBoundary = SailClipboardMapper.map(
                faces,
                SailClipboardMapper.point(faces, reversed, 14, 7),
                clipboard,
                reversed.faceIndex(),
                Set.of(),
                Set.of()
        );

        int row = 7 * HeraldryPatternData.MESH_ATLAS_WIDTH;
        assertEquals(Set.of(row + 13, row + 14, row + 34), beforeBoundary.keySet());
        assertEquals(Set.of(row + 14, row + 34, row + 33), afterBoundary.keySet());
    }

    @Test
    void mapsFacesWhoseModelNormalsPointInNegativeCanonicalDirection() {
        PaintableSailMesh.Face positive = face(0, 0, 0, 15, 15, 1.0F);
        PaintableSailMesh.Face negative = new PaintableSailMesh.Face(
                positive.faceIndex(),
                positive.state(),
                positive.elementId(),
                positive.vertices(),
                new Vector3f(0.0F, 0.0F, -1.0F),
                positive.slot()
        );
        List<PaintableSailMesh.Face> faces = List.of(negative);

        Map<Integer, Integer> mapped = SailClipboardMapper.map(
                faces,
                SailClipboardMapper.point(faces, negative, 7, 7),
                List.of(new PaintableSailMesh.ClipboardPixel(0.0F, 0.0F, 0xFFFF0000)),
                negative.faceIndex(),
                Set.of(),
                Set.of()
        );

        assertEquals(0xFFFF0000, mapped.get(7 * HeraldryPatternData.MESH_ATLAS_WIDTH + 7));
    }

    @Test
    void locatesPixelsByUvCornersInsteadOfRawVertexOrder() {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F),
                new PaintableSailMesh.Vertex(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)
        };
        PaintableSailMesh.Face face = new PaintableSailMesh.Face(
                0,
                4,
                "sail",
                vertices,
                new Vector3f(0.0F, 0.0F, 1.0F),
                new PaintableSailMesh.AtlasSlot(0, 0, 0, 15, 15)
        );

        Vector3f topLeftPixel = SailClipboardMapper.position(face, 0, 0);
        Vector3f bottomRightPixel = SailClipboardMapper.position(face, 14, 14);

        assertEquals(0.5F / 15.0F, topLeftPixel.x(), 0.000001F);
        assertEquals(0.5F / 15.0F, topLeftPixel.y(), 0.000001F);
        assertEquals(14.5F / 15.0F, bottomRightPixel.x(), 0.000001F);
        assertEquals(14.5F / 15.0F, bottomRightPixel.y(), 0.000001F);
    }

    @Test
    void keepsPasteContinuousAcrossAFaceWithScrambledVertexOrder() {
        PaintableSailMesh.Face first = faceAt(0, "first", 0, 0.0F, 1.0F, 15);
        PaintableSailMesh.Face scrambled = scrambledFaceAt(1, "second", 20, 1.0F, 2.0F, 15);
        List<PaintableSailMesh.Face> faces = List.of(first, scrambled);
        List<PaintableSailMesh.ClipboardPixel> clipboard = List.of(
                new PaintableSailMesh.ClipboardPixel(-1.0F / 15.0F, 0.0F, 0xFFFF0000),
                new PaintableSailMesh.ClipboardPixel(0.0F, 0.0F, 0xFFFF0000),
                new PaintableSailMesh.ClipboardPixel(1.0F / 15.0F, 0.0F, 0xFFFF0000)
        );

        Map<Integer, Integer> beforeBoundary = SailClipboardMapper.map(
                faces,
                SailClipboardMapper.point(faces, first, 14, 7),
                clipboard,
                first.faceIndex(),
                Set.of(),
                Set.of()
        );
        Map<Integer, Integer> afterBoundary = SailClipboardMapper.map(
                faces,
                SailClipboardMapper.point(faces, scrambled, 0, 7),
                clipboard,
                scrambled.faceIndex(),
                Set.of(),
                Set.of()
        );

        int row = 7 * HeraldryPatternData.MESH_ATLAS_WIDTH;
        assertEquals(Set.of(row + 13, row + 14, row + 20), beforeBoundary.keySet());
        assertEquals(Set.of(row + 14, row + 20, row + 21), afterBoundary.keySet());
    }

    @Test
    void preservesPixelShapeAcrossABentSurface() {
        PaintableSailMesh.Face upper = faceAt(0, "upper", 0, 0.0F, 1.0F, 15);
        PaintableSailMesh.Face lower = tiltedBelowFace(1, "lower", 20);
        List<PaintableSailMesh.Face> faces = List.of(upper, lower);
        List<PaintableSailMesh.ClipboardPixel> clipboard = List.of(
                new PaintableSailMesh.ClipboardPixel(0.0F, -1.0F / 15.0F, 0xFFFF0000),
                new PaintableSailMesh.ClipboardPixel(0.0F, 0.0F, 0xFFFF0000),
                new PaintableSailMesh.ClipboardPixel(0.0F, 1.0F / 15.0F, 0xFFFF0000)
        );

        Map<Integer, Integer> acrossBend = SailClipboardMapper.map(
                faces,
                SailClipboardMapper.point(faces, upper, 7, 14),
                clipboard,
                upper.faceIndex(),
                Set.of(),
                Set.of()
        );
        Map<Integer, Integer> onLowerPanel = SailClipboardMapper.map(
                faces,
                SailClipboardMapper.point(faces, lower, 7, 5),
                clipboard,
                lower.faceIndex(),
                Set.of(),
                Set.of()
        );

        int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
        assertEquals(Set.of(
                13 * atlasWidth + 7,
                14 * atlasWidth + 7,
                27
        ), acrossBend.keySet());
        assertEquals(Set.of(
                4 * atlasWidth + 27,
                5 * atlasWidth + 27,
                6 * atlasWidth + 27
        ), onLowerPanel.keySet());
    }

    private static PaintableSailMesh.Face face(
            int faceIndex,
            int slotX,
            int slotY,
            int slotWidth,
            int slotHeight,
            float modelSize
    ) {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new PaintableSailMesh.Vertex(modelSize, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(modelSize, modelSize, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(0.0F, modelSize, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F)
        };
        PaintableSailMesh.AtlasSlot slot =
                new PaintableSailMesh.AtlasSlot(faceIndex, slotX, slotY, slotWidth, slotHeight);
        String elementId = faceIndex == 0 ? "sail" : "other";
        return new PaintableSailMesh.Face(
                faceIndex,
                4,
                elementId,
                vertices,
                new Vector3f(0.0F, 0.0F, 1.0F),
                slot
        );
    }

    private static PaintableSailMesh.Face faceAt(
            int faceIndex,
            String elementId,
            int slotX,
            float modelMinX,
            float modelMaxX,
            int slotWidth
    ) {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(modelMinX, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new PaintableSailMesh.Vertex(modelMaxX, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(modelMaxX, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(modelMinX, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F)
        };
        PaintableSailMesh.AtlasSlot slot =
                new PaintableSailMesh.AtlasSlot(faceIndex, slotX, 0, slotWidth, 15);
        return new PaintableSailMesh.Face(
                faceIndex,
                4,
                elementId,
                vertices,
                new Vector3f(0.0F, 0.0F, 1.0F),
                slot
        );
    }

    private static PaintableSailMesh.Face reversedFaceAt(
            int faceIndex,
            String elementId,
            int slotX,
            float modelMinX,
            float modelMaxX,
            int slotWidth
    ) {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(modelMaxX, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new PaintableSailMesh.Vertex(modelMinX, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(modelMinX, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(modelMaxX, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F)
        };
        PaintableSailMesh.AtlasSlot slot =
                new PaintableSailMesh.AtlasSlot(faceIndex, slotX, 0, slotWidth, 15);
        return new PaintableSailMesh.Face(
                faceIndex,
                4,
                elementId,
                vertices,
                new Vector3f(0.0F, 0.0F, 1.0F),
                slot
        );
    }

    private static PaintableSailMesh.Face scrambledFaceAt(
            int faceIndex,
            String elementId,
            int slotX,
            float modelMinX,
            float modelMaxX,
            int slotWidth
    ) {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(modelMaxX, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(modelMaxX, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(modelMinX, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F),
                new PaintableSailMesh.Vertex(modelMinX, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)
        };
        PaintableSailMesh.AtlasSlot slot =
                new PaintableSailMesh.AtlasSlot(faceIndex, slotX, 0, slotWidth, 15);
        return new PaintableSailMesh.Face(
                faceIndex,
                4,
                elementId,
                vertices,
                new Vector3f(0.0F, 0.0F, 1.0F),
                slot
        );
    }

    private static PaintableSailMesh.Face tiltedBelowFace(int faceIndex, String elementId, int slotX) {
        PaintableSailMesh.Vertex[] vertices = {
                new PaintableSailMesh.Vertex(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new PaintableSailMesh.Vertex(1.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F),
                new PaintableSailMesh.Vertex(1.0F, 2.0F, 0.5F, 1.0F, 1.0F, 1.0F, 1.0F),
                new PaintableSailMesh.Vertex(0.0F, 2.0F, 0.5F, 0.0F, 1.0F, 0.0F, 1.0F)
        };
        return new PaintableSailMesh.Face(
                faceIndex,
                4,
                elementId,
                vertices,
                new Vector3f(0.0F, -0.4472136F, 0.8944272F),
                new PaintableSailMesh.AtlasSlot(faceIndex, slotX, 0, 15, 15)
        );
    }
}
