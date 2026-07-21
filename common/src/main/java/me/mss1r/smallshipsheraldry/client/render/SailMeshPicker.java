package me.mss1r.smallshipsheraldry.client.render;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class SailMeshPicker {
    private SailMeshPicker() {
    }

    static Optional<PaintableSailMesh.Hit> pick(
            List<PaintableSailMesh.Face> faces,
            double mouseX,
            double mouseY,
            Set<String> hiddenElements
    ) {
        PaintableSailMesh.Hit bestHit = null;
        float bestDepth = -Float.MAX_VALUE;
        for (PaintableSailMesh.Face face : faces) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            ProjectedVertex[] projected = new ProjectedVertex[4];
            for (int index = 0; index < projected.length; index++) {
                PaintableSailMesh.Vertex vertex = face.vertices()[index];
                projected[index] = new ProjectedVertex(vertex.x(), vertex.y(), vertex.z(), vertex.localU(), vertex.localV());
            }
            PaintableSailMesh.Hit hit = hitFace(face, projected, mouseX, mouseY);
            if (hit != null) {
                float depth = averageDepth(face);
                if (depth > bestDepth) {
                    bestDepth = depth;
                    bestHit = hit;
                }
            }
        }
        return Optional.ofNullable(bestHit);
    }

    private static PaintableSailMesh.Hit hitFace(
            PaintableSailMesh.Face face,
            ProjectedVertex[] vertices,
            double mouseX,
            double mouseY
    ) {
        PaintableSailMesh.Hit hit = hitTriangle(face, vertices[0], vertices[1], vertices[2], mouseX, mouseY);
        return hit != null ? hit : hitTriangle(face, vertices[0], vertices[2], vertices[3], mouseX, mouseY);
    }

    private static PaintableSailMesh.Hit hitTriangle(
            PaintableSailMesh.Face face,
            ProjectedVertex a,
            ProjectedVertex b,
            ProjectedVertex c,
            double mouseX,
            double mouseY
    ) {
        float denominator = (b.y() - c.y()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.y() - c.y());
        if (Math.abs(denominator) < 0.00001F) {
            return null;
        }
        float px = (float) mouseX;
        float py = (float) mouseY;
        float weightA = ((b.y() - c.y()) * (px - c.x()) + (c.x() - b.x()) * (py - c.y())) / denominator;
        float weightB = ((c.y() - a.y()) * (px - c.x()) + (a.x() - c.x()) * (py - c.y())) / denominator;
        float weightC = 1.0F - weightA - weightB;
        if (weightA < -0.001F || weightB < -0.001F || weightC < -0.001F) {
            return null;
        }

        float localU = weightA * a.u() + weightB * b.u() + weightC * c.u();
        float localV = weightA * a.v() + weightB * b.v() + weightC * c.v();
        PaintableSailMesh.AtlasSlot slot = face.slot();
        int localPixelX = clampTilePixel((int) Math.floor(localU * slot.width()), slot.width());
        int localPixelY = clampTilePixel((int) Math.floor(localV * slot.height()), slot.height());
        return new PaintableSailMesh.Hit(
                face.state(), face.elementId(), face.faceIndex(),
                slot.x() + localPixelX, slot.y() + localPixelY,
                slot.x(), slot.y(), slot.width(), slot.height()
        );
    }

    private static float averageDepth(PaintableSailMesh.Face face) {
        float depth = 0.0F;
        for (PaintableSailMesh.Vertex vertex : face.vertices()) {
            depth += vertex.z();
        }
        return depth / face.vertices().length;
    }

    private static int clampTilePixel(int value, int size) {
        return Math.max(0, Math.min(Math.max(1, size) - 1, value));
    }

    private record ProjectedVertex(float x, float y, float depth, float u, float v) {
    }
}
