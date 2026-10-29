package me.mss1r.smallshipsheraldry.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class SailMeshPicker {
    private static final int CELL_SIZE = 16;

    private SailMeshPicker() {
    }

    static Index index(List<PaintableSailMesh.Face> faces) {
        Map<Long, List<PaintableSailMesh.Face>> cells = new HashMap<>();
        for (PaintableSailMesh.Face face : faces) {
            PaintableSailMesh.Vertex[] vertices = face.vertices();
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (PaintableSailMesh.Vertex vertex : vertices) {
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
            }
            int minCellX = cell(minX);
            int minCellY = cell(minY);
            int maxCellX = cell(maxX);
            int maxCellY = cell(maxY);
            for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    cells.computeIfAbsent(key(cellX, cellY), ignored -> new ArrayList<>()).add(face);
                }
            }
        }
        return new Index(List.copyOf(faces), cells);
    }

    static Optional<PaintableSailMesh.Hit> pick(
            List<PaintableSailMesh.Face> faces,
            double mouseX,
            double mouseY,
            Set<String> hiddenElements
    ) {
        return pick(index(faces), mouseX, mouseY, hiddenElements);
    }

    static Optional<PaintableSailMesh.Hit> pick(
            Index index,
            double mouseX,
            double mouseY,
            Set<String> hiddenElements
    ) {
        PaintableSailMesh.Hit bestHit = null;
        float bestDepth = -Float.MAX_VALUE;
        for (PaintableSailMesh.Face face : index.facesAt(mouseX, mouseY)) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            PaintableSailMesh.Hit hit = hitFace(face, mouseX, mouseY);
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
            double mouseX,
            double mouseY
    ) {
        PaintableSailMesh.Vertex[] vertices = face.vertices();
        PaintableSailMesh.Hit hit = hitTriangle(face, vertices[0], vertices[1], vertices[2], mouseX, mouseY);
        return hit != null ? hit : hitTriangle(face, vertices[0], vertices[2], vertices[3], mouseX, mouseY);
    }

    private static PaintableSailMesh.Hit hitTriangle(
            PaintableSailMesh.Face face,
            PaintableSailMesh.Vertex a,
            PaintableSailMesh.Vertex b,
            PaintableSailMesh.Vertex c,
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

        float localU = weightA * a.localU() + weightB * b.localU() + weightC * c.localU();
        float localV = weightA * a.localV() + weightB * b.localV() + weightC * c.localV();
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
        PaintableSailMesh.Vertex[] vertices = face.vertices();
        return (vertices[0].z() + vertices[1].z() + vertices[2].z() + vertices[3].z()) * 0.25F;
    }

    private static int clampTilePixel(int value, int size) {
        return Math.max(0, Math.min(Math.max(1, size) - 1, value));
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    private static long key(int cellX, int cellY) {
        return ((long) cellX << 32) ^ (cellY & 0xFFFFFFFFL);
    }

    static final class Index {
        private final List<PaintableSailMesh.Face> faces;
        private final Map<Integer, PaintableSailMesh.Face> facesByIndex = new HashMap<>();
        private final Map<Long, List<PaintableSailMesh.Face>> cells;

        private Index(List<PaintableSailMesh.Face> faces, Map<Long, List<PaintableSailMesh.Face>> cells) {
            this.faces = faces;
            this.cells = cells;
            for (PaintableSailMesh.Face face : faces) facesByIndex.put(face.faceIndex(), face);
        }

        List<PaintableSailMesh.Face> faces() {
            return faces;
        }

        Optional<PaintableSailMesh.Face> face(int faceIndex) {
            return Optional.ofNullable(facesByIndex.get(faceIndex));
        }

        int candidateCountAt(double x, double y) {
            return facesAt(x, y).size();
        }

        private List<PaintableSailMesh.Face> facesAt(double x, double y) {
            return cells.getOrDefault(key(cell(x), cell(y)), List.of());
        }
    }
}
