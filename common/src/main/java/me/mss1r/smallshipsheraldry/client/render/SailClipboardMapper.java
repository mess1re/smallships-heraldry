package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SailClipboardMapper {
    private static final float GRID_SCALE = PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT;
    private static final int SEARCH_RADIUS = 1;
    private static final float MAX_SURFACE_DISTANCE_SQUARED = square(0.8F / GRID_SCALE);
    private static final Map<List<PaintableSailMesh.Face>, SailSurfaceLayout> LAYOUT_CACHE =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private SailClipboardMapper() {
    }

    static PaintableSailMesh.SurfacePoint point(PaintableSailMesh.Face face, int localPixelX, int localPixelY) {
        List<PaintableSailMesh.Face> faces = List.of(face);
        return point(faces, face, localPixelX, localPixelY);
    }

    static PaintableSailMesh.SurfacePoint point(
            List<PaintableSailMesh.Face> faces,
            PaintableSailMesh.Face face,
            int localPixelX,
            int localPixelY
    ) {
        SailSurfaceLayout.Point point = layout(faces).point(face, localPixelX, localPixelY);
        return new PaintableSailMesh.SurfacePoint(point.u(), point.v(), point.componentId());
    }

    static Vector3f position(PaintableSailMesh.Face face, int localPixelX, int localPixelY) {
        PaintableSailMesh.AtlasSlot slot = face.slot();
        float u = (localPixelX + 0.5F) / Math.max(1, slot.width());
        float v = (localPixelY + 0.5F) / Math.max(1, slot.height());
        return interpolate(face.vertices(), u, v);
    }

    static Map<Integer, Integer> map(
            List<PaintableSailMesh.Face> faces,
            PaintableSailMesh.SurfacePoint target,
            List<PaintableSailMesh.ClipboardPixel> clipboard,
            int targetAnchorFaceIndex,
            Set<String> selectedElements,
            Set<String> hiddenElements
    ) {
        if (faces.isEmpty() || clipboard.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<SurfacePixel>> grid = new HashMap<>();
        for (SailSurfaceLayout.Pixel pixel : layout(faces).pixels()) {
            if (pixel.componentId() != target.componentId()) {
                continue;
            }
            if (hiddenElements != null && hiddenElements.contains(pixel.elementId())) {
                continue;
            }
            if (selectedElements != null && !selectedElements.isEmpty()
                    && !selectedElements.contains(pixel.elementId())) {
                continue;
            }
            SurfacePixel candidate = new SurfacePixel(pixel.atlasIndex(), pixel.u(), pixel.v());
            grid.computeIfAbsent(key(cell(pixel.u()), cell(pixel.v())), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        Map<Integer, Integer> replacements = new HashMap<>();
        for (PaintableSailMesh.ClipboardPixel source : clipboard) {
            SurfacePixel best = closest(grid, target.u() + source.offsetU(), target.v() + source.offsetV());
            if (best != null) {
                replacements.put(best.atlasIndex(), source.color());
            }
        }
        return replacements;
    }

    private static SurfacePixel closest(Map<Long, List<SurfacePixel>> grid, float targetU, float targetV) {
        int centerX = cell(targetU);
        int centerY = cell(targetV);
        SurfacePixel best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int cellY = centerY - SEARCH_RADIUS; cellY <= centerY + SEARCH_RADIUS; cellY++) {
            for (int cellX = centerX - SEARCH_RADIUS; cellX <= centerX + SEARCH_RADIUS; cellX++) {
                for (SurfacePixel candidate : grid.getOrDefault(key(cellX, cellY), List.of())) {
                    float deltaU = candidate.u() - targetU;
                    float deltaV = candidate.v() - targetV;
                    float distance = deltaU * deltaU + deltaV * deltaV;
                    if (distance <= MAX_SURFACE_DISTANCE_SQUARED && distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static SailSurfaceLayout layout(List<PaintableSailMesh.Face> faces) {
        return LAYOUT_CACHE.computeIfAbsent(faces, SailSurfaceLayout::create);
    }

    private static Vector3f interpolate(PaintableSailMesh.Vertex[] vertices, float u, float v) {
        PaintableSailMesh.Vertex topLeft = closestVertex(vertices, 0.0F, 0.0F);
        PaintableSailMesh.Vertex topRight = closestVertex(vertices, 1.0F, 0.0F);
        PaintableSailMesh.Vertex bottomRight = closestVertex(vertices, 1.0F, 1.0F);
        PaintableSailMesh.Vertex bottomLeft = closestVertex(vertices, 0.0F, 1.0F);
        Vector3f top = lerp(topLeft, topRight, u);
        Vector3f bottom = lerp(bottomLeft, bottomRight, u);
        return top.lerp(bottom, v);
    }

    private static PaintableSailMesh.Vertex closestVertex(
            PaintableSailMesh.Vertex[] vertices,
            float targetU,
            float targetV
    ) {
        PaintableSailMesh.Vertex closest = vertices[0];
        float closestDistance = Float.MAX_VALUE;
        for (PaintableSailMesh.Vertex vertex : vertices) {
            float deltaU = vertex.localU() - targetU;
            float deltaV = vertex.localV() - targetV;
            float distance = deltaU * deltaU + deltaV * deltaV;
            if (distance < closestDistance) {
                closest = vertex;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static Vector3f lerp(PaintableSailMesh.Vertex first, PaintableSailMesh.Vertex second, float amount) {
        return new Vector3f(
                first.x() + (second.x() - first.x()) * amount,
                first.y() + (second.y() - first.y()) * amount,
                first.z() + (second.z() - first.z()) * amount
        );
    }

    private static int cell(float coordinate) {
        return (int) Math.floor(coordinate * GRID_SCALE);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    private static float square(float value) {
        return value * value;
    }

    private record SurfacePixel(int atlasIndex, float u, float v) {
    }
}
