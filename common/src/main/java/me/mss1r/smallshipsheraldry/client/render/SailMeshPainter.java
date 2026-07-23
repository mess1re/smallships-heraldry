package me.mss1r.smallshipsheraldry.client.render;

import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

final class SailMeshPainter {
    private static final int TILE_PADDING = 1;

    private SailMeshPainter() {
    }

    static boolean fillElementPixels(Ship ship, String elementId, int stage, int[] pixels, int color) {
        if (ship == null || elementId == null || elementId.isBlank() || !validPixels(pixels)) {
            return false;
        }
        boolean changed = false;
        for (PaintableSailMesh.Face face : PaintableSailMesh.facesFor(ship, stage)) {
            if (elementId.equals(face.elementId())) {
                fillFace(face, pixels, color);
                changed = true;
            }
        }
        return changed;
    }

    static boolean fillStagePixels(Ship ship, int stage, int[] pixels, int color) {
        if (ship == null || !validPixels(pixels)) {
            return false;
        }
        boolean changed = false;
        for (PaintableSailMesh.Face face : PaintableSailMesh.facesFor(ship, stage)) {
            fillFace(face, pixels, color);
            changed = true;
        }
        return changed;
    }

    static boolean fillFacePixels(Ship ship, int faceIndex, int stage, int[] pixels, int color) {
        if (ship == null || !validPixels(pixels)) {
            return false;
        }
        for (PaintableSailMesh.Face face : PaintableSailMesh.facesFor(ship, stage)) {
            if (face.faceIndex() == faceIndex) {
                fillFace(face, pixels, color);
                return true;
            }
        }
        return false;
    }

    static boolean paintBrushPixels(
            Ship ship,
            PaintableSailMesh.Hit hit,
            int stage,
            int[] pixels,
            int color,
            int size,
            boolean mirrorVertical,
            boolean mirrorHorizontal
    ) {
        PaintableSailMesh.Face face = faceForHit(ship, hit, stage);
        if (face == null || !validPixels(pixels)) {
            return false;
        }
        PaintableSailMesh.AtlasSlot slot = face.slot();
        int localX = hit.pixelX() - slot.x();
        int localY = hit.pixelY() - slot.y();
        return paintBrushWithMirrors(face, localX, localY, pixels, color, Mth.clamp(size, 1, 8), mirrorVertical, mirrorHorizontal);
    }

    static boolean paintLinePixels(
            Ship ship,
            PaintableSailMesh.Hit start,
            PaintableSailMesh.Hit end,
            int stage,
            int[] pixels,
            int color,
            int size,
            boolean mirrorVertical,
            boolean mirrorHorizontal
    ) {
        if (start == null || end == null || start.faceIndex() != end.faceIndex()) {
            return paintBrushPixels(ship, end, stage, pixels, color, size, mirrorVertical, mirrorHorizontal);
        }
        PaintableSailMesh.Face face = faceForHit(ship, start, stage);
        if (face == null || !validPixels(pixels)) {
            return false;
        }
        PaintableSailMesh.AtlasSlot slot = face.slot();
        int x0 = start.pixelX() - slot.x();
        int y0 = start.pixelY() - slot.y();
        int x1 = end.pixelX() - slot.x();
        int y1 = end.pixelY() - slot.y();
        int dx = Math.abs(x1 - x0);
        int stepX = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int stepY = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int clampedSize = Mth.clamp(size, 1, 8);
        boolean changed = false;
        while (true) {
            changed |= paintBrushWithMirrors(face, x0, y0, pixels, color, clampedSize, mirrorVertical, mirrorHorizontal);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int twiceError = error * 2;
            if (twiceError >= dy) {
                error += dy;
                x0 += stepX;
            }
            if (twiceError <= dx) {
                error += dx;
                y0 += stepY;
            }
        }
        return changed;
    }

    static boolean floodFillPixels(Ship ship, PaintableSailMesh.Hit hit, int stage, int[] pixels, int color) {
        PaintableSailMesh.Face face = faceForHit(ship, hit, stage);
        if (face == null || !validPixels(pixels)) {
            return false;
        }
        PaintableSailMesh.AtlasSlot slot = face.slot();
        int startX = hit.pixelX() - slot.x();
        int startY = hit.pixelY() - slot.y();
        if (!isInsideFacePixel(face, startX, startY)) {
            return false;
        }
        int source = pixels[hit.pixelY() * HeraldryPatternData.MESH_ATLAS_WIDTH + hit.pixelX()];
        if (source == color) {
            return false;
        }

        boolean[] visited = new boolean[slot.width() * slot.height()];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(startY * slot.width() + startX);
        boolean changed = false;
        while (!pending.isEmpty()) {
            int packed = pending.removeFirst();
            if (packed < 0 || packed >= visited.length || visited[packed]) {
                continue;
            }
            visited[packed] = true;
            int localX = packed % slot.width();
            int localY = packed / slot.width();
            if (!isInsideFacePixel(face, localX, localY)) {
                continue;
            }
            int atlasX = slot.x() + localX;
            int atlasY = slot.y() + localY;
            int index = atlasY * HeraldryPatternData.MESH_ATLAS_WIDTH + atlasX;
            if (pixels[index] != source) {
                continue;
            }
            setMeshPixel(pixels, face, localX, localY, color);
            changed = true;
            if (localX > 0) pending.add(packed - 1);
            if (localX + 1 < slot.width()) pending.add(packed + 1);
            if (localY > 0) pending.add(packed - slot.width());
            if (localY + 1 < slot.height()) pending.add(packed + slot.width());
        }
        return changed;
    }

    static boolean replaceColorPixels(
            Ship ship,
            int stage,
            int[] pixels,
            int source,
            int color,
            Set<String> selectedElements,
            Set<String> hiddenElements
    ) {
        if (ship == null || !validPixels(pixels) || source == color) {
            return false;
        }
        boolean restrictSelection = selectedElements != null && !selectedElements.isEmpty();
        boolean changed = false;
        for (PaintableSailMesh.Face face : PaintableSailMesh.facesFor(ship, stage)) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            if (restrictSelection && !selectedElements.contains(face.elementId())) {
                continue;
            }
            PaintableSailMesh.AtlasSlot slot = face.slot();
            for (int y = 0; y < slot.height(); y++) {
                for (int x = 0; x < slot.width(); x++) {
                    if (!isInsideFacePixel(face, x, y)) {
                        continue;
                    }
                    int index = (slot.y() + y) * HeraldryPatternData.MESH_ATLAS_WIDTH + slot.x() + x;
                    if (pixels[index] == source) {
                        setMeshPixel(pixels, face, x, y, color);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    static boolean fillScopedPixels(
            Ship ship,
            int stage,
            int[] pixels,
            int color,
            Set<String> selectedElements,
            Set<String> hiddenElements
    ) {
        if (ship == null || !validPixels(pixels)) {
            return false;
        }
        boolean restrictSelection = selectedElements != null && !selectedElements.isEmpty();
        boolean changed = false;
        for (PaintableSailMesh.Face face : PaintableSailMesh.facesFor(ship, stage)) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            if (restrictSelection && !selectedElements.contains(face.elementId())) {
                continue;
            }
            fillFace(face, pixels, color);
            changed = true;
        }
        return changed;
    }

    private static PaintableSailMesh.Face faceForHit(Ship ship, PaintableSailMesh.Hit hit, int stage) {
        if (ship == null || hit == null) {
            return null;
        }
        return PaintableSailMesh.faceFor(ship, stage, hit.faceIndex());
    }

    private static boolean paintBrushWithMirrors(
            PaintableSailMesh.Face face,
            int localX,
            int localY,
            int[] pixels,
            int color,
            int size,
            boolean mirrorVertical,
            boolean mirrorHorizontal
    ) {
        PaintableSailMesh.AtlasSlot slot = face.slot();
        boolean changed = paintBrushAt(face, localX, localY, pixels, color, size);
        if (mirrorVertical) {
            changed |= paintBrushAt(face, slot.width() - 1 - localX, localY, pixels, color, size);
        }
        if (mirrorHorizontal) {
            changed |= paintBrushAt(face, localX, slot.height() - 1 - localY, pixels, color, size);
        }
        if (mirrorVertical && mirrorHorizontal) {
            changed |= paintBrushAt(face, slot.width() - 1 - localX, slot.height() - 1 - localY, pixels, color, size);
        }
        return changed;
    }

    private static boolean paintBrushAt(PaintableSailMesh.Face face, int centerX, int centerY, int[] pixels, int color, int size) {
        int firstOffset = -(size / 2);
        int lastOffset = firstOffset + size - 1;
        boolean changed = false;
        PaintableSailMesh.AtlasSlot slot = face.slot();
        for (int offsetY = firstOffset; offsetY <= lastOffset; offsetY++) {
            for (int offsetX = firstOffset; offsetX <= lastOffset; offsetX++) {
                int localX = centerX + offsetX;
                int localY = centerY + offsetY;
                if (!isInsideFacePixel(face, localX, localY)) {
                    continue;
                }
                int index = (slot.y() + localY) * HeraldryPatternData.MESH_ATLAS_WIDTH + slot.x() + localX;
                if (pixels[index] != color) {
                    setMeshPixel(pixels, face, localX, localY, color);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean isInsideFacePixel(PaintableSailMesh.Face face, int localX, int localY) {
        PaintableSailMesh.AtlasSlot slot = face.slot();
        if (localX < 0 || localX >= slot.width() || localY < 0 || localY >= slot.height()) {
            return false;
        }
        PaintableSailMesh.Vertex[] vertices = face.vertices();
        float x = localX + 0.5F;
        float y = localY + 0.5F;
        return isInsideTriangle(vertices[0], vertices[1], vertices[2], slot, x, y)
                || isInsideTriangle(vertices[0], vertices[2], vertices[3], slot, x, y);
    }

    private static boolean isInsideTriangle(
            PaintableSailMesh.Vertex first,
            PaintableSailMesh.Vertex second,
            PaintableSailMesh.Vertex third,
            PaintableSailMesh.AtlasSlot slot,
            float x,
            float y
    ) {
        float ax = first.localU() * slot.width();
        float ay = first.localV() * slot.height();
        float bx = second.localU() * slot.width();
        float by = second.localV() * slot.height();
        float cx = third.localU() * slot.width();
        float cy = third.localV() * slot.height();
        float denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(denominator) < 0.00001F) {
            float minX = Math.min(ax, Math.min(bx, cx));
            float maxX = Math.max(ax, Math.max(bx, cx));
            float minY = Math.min(ay, Math.min(by, cy));
            float maxY = Math.max(ay, Math.max(by, cy));
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
        float weightA = ((by - cy) * (x - cx) + (cx - bx) * (y - cy)) / denominator;
        float weightB = ((cy - ay) * (x - cx) + (ax - cx) * (y - cy)) / denominator;
        float weightC = 1.0F - weightA - weightB;
        return weightA >= -0.001F && weightB >= -0.001F && weightC >= -0.001F;
    }

    private static void fillFace(PaintableSailMesh.Face face, int[] pixels, int color) {
        PaintableSailMesh.AtlasSlot slot = face.slot();
        PixelVertex[] vertices = new PixelVertex[4];
        for (int index = 0; index < face.vertices().length; index++) {
            PaintableSailMesh.Vertex vertex = face.vertices()[index];
            vertices[index] = new PixelVertex(vertex.localU() * slot.width(), vertex.localV() * slot.height());
        }
        fillTriangle(face, vertices[0], vertices[1], vertices[2], pixels, color);
        fillTriangle(face, vertices[0], vertices[2], vertices[3], pixels, color);
    }

    private static void fillTriangle(
            PaintableSailMesh.Face face,
            PixelVertex a,
            PixelVertex b,
            PixelVertex c,
            int[] pixels,
            int color
    ) {
        PaintableSailMesh.AtlasSlot slot = face.slot();
        int minX = clampPixel(Mth.floor(Math.min(a.x(), Math.min(b.x(), c.x()))), slot.width());
        int minY = clampPixel(Mth.floor(Math.min(a.y(), Math.min(b.y(), c.y()))), slot.height());
        int maxX = clampPixel(Mth.ceil(Math.max(a.x(), Math.max(b.x(), c.x())) - 1), slot.width());
        int maxY = clampPixel(Mth.ceil(Math.max(a.y(), Math.max(b.y(), c.y())) - 1), slot.height());
        float denominator = (b.y() - c.y()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.y() - c.y());
        if (Math.abs(denominator) < 0.00001F) {
            fillBounds(face, minX, minY, maxX, maxY, pixels, color);
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5F;
                float py = y + 0.5F;
                float weightA = ((b.y() - c.y()) * (px - c.x()) + (c.x() - b.x()) * (py - c.y())) / denominator;
                float weightB = ((c.y() - a.y()) * (px - c.x()) + (a.x() - c.x()) * (py - c.y())) / denominator;
                float weightC = 1.0F - weightA - weightB;
                if (weightA >= -0.001F && weightB >= -0.001F && weightC >= -0.001F) {
                    setMeshPixel(pixels, face, x, y, color);
                }
            }
        }
    }

    private static void fillBounds(
            PaintableSailMesh.Face face,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int[] pixels,
            int color
    ) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                setMeshPixel(pixels, face, x, y, color);
            }
        }
    }

    private static void setMeshPixel(int[] pixels, PaintableSailMesh.Face face, int x, int y, int color) {
        PaintableSailMesh.AtlasSlot slot = face.slot();
        int localX = clampPixel(x, slot.width());
        int localY = clampPixel(y, slot.height());
        int atlasX = slot.x() + localX;
        int atlasY = slot.y() + localY;
        int minX = localX == 0 ? -TILE_PADDING : 0;
        int maxX = localX == slot.width() - 1 ? TILE_PADDING : 0;
        int minY = localY == 0 ? -TILE_PADDING : 0;
        int maxY = localY == slot.height() - 1 ? TILE_PADDING : 0;
        for (int dy = minY; dy <= maxY; dy++) {
            for (int dx = minX; dx <= maxX; dx++) {
                setAtlasPixel(pixels, atlasX + dx, atlasY + dy, color);
            }
        }
    }

    private static void setAtlasPixel(int[] pixels, int x, int y, int color) {
        if (x < 0 || x >= HeraldryPatternData.MESH_ATLAS_WIDTH || y < 0 || y >= HeraldryPatternData.MESH_ATLAS_HEIGHT) {
            return;
        }
        int index = y * HeraldryPatternData.MESH_ATLAS_WIDTH + x;
        if (index >= 0 && index < pixels.length) {
            pixels[index] = color;
        }
    }

    private static boolean validPixels(int[] pixels) {
        return pixels != null && pixels.length >= HeraldryPatternData.MESH_PIXEL_COUNT;
    }

    private static int clampPixel(int value, int size) {
        return Math.max(0, Math.min(Math.max(1, size) - 1, value));
    }

    private record PixelVertex(float x, float y) {
    }
}
