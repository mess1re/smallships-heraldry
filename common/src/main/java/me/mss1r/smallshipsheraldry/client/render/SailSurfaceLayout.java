package me.mss1r.smallshipsheraldry.client.render;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SailSurfaceLayout {
    private static final float GRID_SCALE = PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT;
    private static final float EDGE_JOIN_DISTANCE = 3.5F / GRID_SCALE;
    private static final float MIN_NORMAL_DOT = 0.25F;
    private static final float EPSILON = 0.000001F;

    private final Map<Integer, FacePlacement> placements;
    private final List<Pixel> pixels;

    private SailSurfaceLayout(Map<Integer, FacePlacement> placements, List<Pixel> pixels) {
        this.placements = placements;
        this.pixels = pixels;
    }

    static SailSurfaceLayout create(List<PaintableSailMesh.Face> faces) {
        if (faces.isEmpty()) {
            return new SailSurfaceLayout(Map.of(), List.of());
        }

        Map<Integer, FaceGeometry> geometry = new HashMap<>();
        for (PaintableSailMesh.Face face : faces) {
            geometry.put(face.faceIndex(), FaceGeometry.of(face));
        }
        Map<Integer, List<Connection>> connections = connectEdges(faces, geometry);
        Map<Integer, FacePlacement> placements = placeFaces(faces, geometry, connections);
        return new SailSurfaceLayout(Map.copyOf(placements), buildPixels(faces, placements));
    }

    Point point(PaintableSailMesh.Face face, int localPixelX, int localPixelY) {
        FacePlacement placement = placements.get(face.faceIndex());
        if (placement == null) {
            return new Point(0.0F, 0.0F, face.faceIndex());
        }
        float localU = (localPixelX + 0.5F) / GRID_SCALE;
        float localV = (localPixelY + 0.5F) / GRID_SCALE;
        Vector2 surface = placement.transform().map(localU, localV);
        return new Point(surface.x(), surface.y(), placement.componentId());
    }

    List<Pixel> pixels() {
        return pixels;
    }

    private static Map<Integer, List<Connection>> connectEdges(
            List<PaintableSailMesh.Face> faces,
            Map<Integer, FaceGeometry> geometry
    ) {
        List<EdgeCandidate> candidates = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < faces.size(); firstIndex++) {
            PaintableSailMesh.Face firstFace = faces.get(firstIndex);
            FaceGeometry first = geometry.get(firstFace.faceIndex());
            for (int secondIndex = firstIndex + 1; secondIndex < faces.size(); secondIndex++) {
                PaintableSailMesh.Face secondFace = faces.get(secondIndex);
                if (new Vector3f(firstFace.normal()).normalize()
                        .dot(new Vector3f(secondFace.normal()).normalize()) < MIN_NORMAL_DOT) {
                    continue;
                }
                FaceGeometry second = geometry.get(secondFace.faceIndex());
                for (Edge firstEdge : first.edges()) {
                    for (Edge secondEdge : second.edges()) {
                        EdgeMatch match = match(firstEdge, secondEdge);
                        if (match != null) {
                            candidates.add(new EdgeCandidate(
                                    firstFace.faceIndex(),
                                    firstEdge.index(),
                                    secondFace.faceIndex(),
                                    secondEdge.index(),
                                    match.reversed(),
                                    match.score()
                            ));
                        }
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(EdgeCandidate::score));

        Set<Long> occupiedEdges = new HashSet<>();
        Map<Integer, List<Connection>> result = new HashMap<>();
        for (EdgeCandidate candidate : candidates) {
            long firstKey = edgeKey(candidate.firstFaceIndex(), candidate.firstEdgeIndex());
            long secondKey = edgeKey(candidate.secondFaceIndex(), candidate.secondEdgeIndex());
            if (occupiedEdges.contains(firstKey) || occupiedEdges.contains(secondKey)) {
                continue;
            }
            occupiedEdges.add(firstKey);
            occupiedEdges.add(secondKey);
            Connection connection = new Connection(
                    candidate.firstFaceIndex(),
                    candidate.firstEdgeIndex(),
                    candidate.secondFaceIndex(),
                    candidate.secondEdgeIndex(),
                    candidate.reversed()
            );
            result.computeIfAbsent(candidate.firstFaceIndex(), ignored -> new ArrayList<>()).add(connection);
            result.computeIfAbsent(candidate.secondFaceIndex(), ignored -> new ArrayList<>()).add(connection);
        }
        return result;
    }

    private static EdgeMatch match(Edge first, Edge second) {
        float firstLength = first.start3().distance(first.end3());
        float secondLength = second.start3().distance(second.end3());
        if (firstLength < EPSILON || secondLength < EPSILON) {
            return null;
        }
        float lengthRatio = Math.min(firstLength, secondLength) / Math.max(firstLength, secondLength);
        if (lengthRatio < 0.72F) {
            return null;
        }

        float directStart = first.start3().distance(second.start3());
        float directEnd = first.end3().distance(second.end3());
        float reversedStart = first.start3().distance(second.end3());
        float reversedEnd = first.end3().distance(second.start3());
        float directMax = Math.max(directStart, directEnd);
        float reversedMax = Math.max(reversedStart, reversedEnd);
        boolean reversed = reversedMax < directMax;
        float maxDistance = reversed ? reversedMax : directMax;
        if (maxDistance > EDGE_JOIN_DISTANCE) {
            return null;
        }
        float score = reversed
                ? reversedStart + reversedEnd + (1.0F - lengthRatio)
                : directStart + directEnd + (1.0F - lengthRatio);
        return new EdgeMatch(reversed, score);
    }

    private static Map<Integer, FacePlacement> placeFaces(
            List<PaintableSailMesh.Face> faces,
            Map<Integer, FaceGeometry> geometry,
            Map<Integer, List<Connection>> connections
    ) {
        Map<Integer, PaintableSailMesh.Face> facesByIndex = new HashMap<>();
        for (PaintableSailMesh.Face face : faces) {
            facesByIndex.put(face.faceIndex(), face);
        }

        List<PaintableSailMesh.Face> orderedFaces = new ArrayList<>(faces);
        orderedFaces.sort(Comparator.comparingInt(PaintableSailMesh.Face::faceIndex));
        Map<Integer, FacePlacement> placements = new HashMap<>();
        for (PaintableSailMesh.Face seed : orderedFaces) {
            if (placements.containsKey(seed.faceIndex())) {
                continue;
            }
            int componentId = seed.faceIndex();
            Set<Integer> componentFaces = collectComponent(seed.faceIndex(), connections);
            Transform seedTransform = seedTransform(seed, geometry.get(seed.faceIndex()), componentFaces, facesByIndex);
            placements.put(seed.faceIndex(), new FacePlacement(seedTransform, componentId));

            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(seed.faceIndex());
            while (!queue.isEmpty()) {
                int currentFaceIndex = queue.removeFirst();
                FacePlacement currentPlacement = placements.get(currentFaceIndex);
                FaceGeometry currentGeometry = geometry.get(currentFaceIndex);
                for (Connection connection : connections.getOrDefault(currentFaceIndex, List.of())) {
                    int neighborFaceIndex = connection.other(currentFaceIndex);
                    if (placements.containsKey(neighborFaceIndex)) {
                        continue;
                    }
                    FaceGeometry neighborGeometry = geometry.get(neighborFaceIndex);
                    Transform neighborTransform = unfold(
                            currentFaceIndex,
                            currentGeometry,
                            currentPlacement.transform(),
                            neighborGeometry,
                            connection
                    );
                    placements.put(neighborFaceIndex, new FacePlacement(neighborTransform, componentId));
                    queue.addLast(neighborFaceIndex);
                }
            }
        }
        return placements;
    }

    private static Set<Integer> collectComponent(
            int seedFaceIndex,
            Map<Integer, List<Connection>> connections
    ) {
        Set<Integer> component = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        component.add(seedFaceIndex);
        queue.add(seedFaceIndex);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (Connection connection : connections.getOrDefault(current, List.of())) {
                int neighbor = connection.other(current);
                if (component.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return component;
    }

    private static Transform seedTransform(
            PaintableSailMesh.Face seed,
            FaceGeometry seedGeometry,
            Set<Integer> componentFaces,
            Map<Integer, PaintableSailMesh.Face> facesByIndex
    ) {
        Vector3f normal = new Vector3f();
        Vector3f reference = new Vector3f(seed.normal()).normalize();
        for (int faceIndex : componentFaces) {
            PaintableSailMesh.Face face = facesByIndex.get(faceIndex);
            if (face == null || face.normal().lengthSquared() < EPSILON) {
                continue;
            }
            Vector3f faceNormal = new Vector3f(face.normal()).normalize();
            if (faceNormal.dot(reference) < 0.0F) {
                faceNormal.negate();
            }
            normal.add(faceNormal);
        }
        if (normal.lengthSquared() < EPSILON) {
            normal.set(reference);
        } else {
            normal.normalize();
        }
        canonicalize(normal);

        Vector3f vertical = projectOntoPlane(new Vector3f(0.0F, 1.0F, 0.0F), normal);
        if (vertical.lengthSquared() < EPSILON) {
            vertical = projectOntoPlane(new Vector3f(0.0F, 0.0F, 1.0F), normal);
        }
        vertical.normalize();
        Vector3f horizontal = new Vector3f(vertical).cross(normal).normalize();
        canonicalize(horizontal);

        Vector3f physicalU = new Vector3f(seedGeometry.topRight3()).sub(seedGeometry.topLeft3());
        Vector3f physicalV = new Vector3f(seedGeometry.bottomLeft3()).sub(seedGeometry.topLeft3());
        Vector2 xAxis = projectedDirection(physicalU, horizontal, vertical);
        Vector2 expectedY = projectedDirection(physicalV, horizontal, vertical);
        Vector2 clockwiseY = new Vector2(-xAxis.y(), xAxis.x());
        Vector2 counterClockwiseY = new Vector2(xAxis.y(), -xAxis.x());
        Vector2 yAxis = clockwiseY.dot(expectedY) >= counterClockwiseY.dot(expectedY)
                ? clockwiseY
                : counterClockwiseY;
        return new Transform(xAxis.x(), yAxis.x(), xAxis.y(), yAxis.y(), 0.0F, 0.0F);
    }

    private static Vector2 projectedDirection(Vector3f direction, Vector3f horizontal, Vector3f vertical) {
        Vector2 projected = new Vector2(direction.dot(horizontal), direction.dot(vertical));
        if (projected.lengthSquared() < EPSILON) {
            return new Vector2(1.0F, 0.0F);
        }
        return projected.normalize();
    }

    private static Transform unfold(
            int currentFaceIndex,
            FaceGeometry currentGeometry,
            Transform currentTransform,
            FaceGeometry neighborGeometry,
            Connection connection
    ) {
        Edge currentEdge = currentGeometry.edges()[connection.edgeIndex(currentFaceIndex)];
        Edge neighborEdge = neighborGeometry.edges()[connection.otherEdgeIndex(currentFaceIndex)];
        Vector2 currentStart = currentTransform.map(currentEdge.start2().x(), currentEdge.start2().y());
        Vector2 currentEnd = currentTransform.map(currentEdge.end2().x(), currentEdge.end2().y());
        Vector2 targetStart = connection.reversed() ? currentEnd : currentStart;
        Vector2 targetEnd = connection.reversed() ? currentStart : currentEnd;

        Transform regular = Transform.between(neighborEdge.start2(), neighborEdge.end2(), targetStart, targetEnd, false);
        Transform reflected = Transform.between(neighborEdge.start2(), neighborEdge.end2(), targetStart, targetEnd, true);
        Vector2 currentCenter = currentTransform.map(currentGeometry.width() * 0.5F, currentGeometry.height() * 0.5F);
        Vector2 regularCenter = regular.map(neighborGeometry.width() * 0.5F, neighborGeometry.height() * 0.5F);
        Vector2 reflectedCenter = reflected.map(neighborGeometry.width() * 0.5F, neighborGeometry.height() * 0.5F);
        Vector2 edgeDirection = targetEnd.subtract(targetStart);
        float currentSide = edgeDirection.cross(currentCenter.subtract(targetStart));
        float regularSide = edgeDirection.cross(regularCenter.subtract(targetStart));
        float reflectedSide = edgeDirection.cross(reflectedCenter.subtract(targetStart));
        boolean regularOpposite = currentSide * regularSide < 0.0F;
        boolean reflectedOpposite = currentSide * reflectedSide < 0.0F;
        if (regularOpposite != reflectedOpposite) {
            return regularOpposite ? regular : reflected;
        }
        return Math.abs(regularSide + currentSide) <= Math.abs(reflectedSide + currentSide)
                ? regular
                : reflected;
    }

    private static List<Pixel> buildPixels(
            List<PaintableSailMesh.Face> faces,
            Map<Integer, FacePlacement> placements
    ) {
        List<Pixel> pixels = new ArrayList<>();
        for (PaintableSailMesh.Face face : faces) {
            FacePlacement placement = placements.get(face.faceIndex());
            if (placement == null) {
                continue;
            }
            PaintableSailMesh.AtlasSlot slot = face.slot();
            for (int localY = 0; localY < slot.height(); localY++) {
                for (int localX = 0; localX < slot.width(); localX++) {
                    Vector2 point = placement.transform().map(
                            (localX + 0.5F) / GRID_SCALE,
                            (localY + 0.5F) / GRID_SCALE
                    );
                    int atlasIndex = (slot.y() + localY) * HeraldryPatternData.MESH_ATLAS_WIDTH
                            + slot.x() + localX;
                    pixels.add(new Pixel(
                            atlasIndex,
                            face.faceIndex(),
                            face.elementId(),
                            point.x(),
                            point.y(),
                            placement.componentId()
                    ));
                }
            }
        }
        return List.copyOf(pixels);
    }

    private static Vector3f projectOntoPlane(Vector3f axis, Vector3f normal) {
        return axis.sub(new Vector3f(normal).mul(axis.dot(normal)));
    }

    private static void canonicalize(Vector3f vector) {
        float x = Math.abs(vector.x());
        float y = Math.abs(vector.y());
        float z = Math.abs(vector.z());
        float dominant = x >= y && x >= z ? vector.x() : y >= z ? vector.y() : vector.z();
        if (dominant < 0.0F) {
            vector.negate();
        }
    }

    private static long edgeKey(int faceIndex, int edgeIndex) {
        return ((long) faceIndex << 32) ^ (edgeIndex & 0xFFFFFFFFL);
    }

    record Point(float u, float v, int componentId) {
    }

    record Pixel(int atlasIndex, int faceIndex, String elementId, float u, float v, int componentId) {
    }

    private record FacePlacement(Transform transform, int componentId) {
    }

    private record FaceGeometry(
            float width,
            float height,
            Vector3f topLeft3,
            Vector3f topRight3,
            Vector3f bottomRight3,
            Vector3f bottomLeft3,
            Edge[] edges
    ) {
        private static FaceGeometry of(PaintableSailMesh.Face face) {
            PaintableSailMesh.AtlasSlot slot = face.slot();
            float width = slot.width() / GRID_SCALE;
            float height = slot.height() / GRID_SCALE;
            Vector3f topLeft = position(closestVertex(face, 0.0F, 0.0F));
            Vector3f topRight = position(closestVertex(face, 1.0F, 0.0F));
            Vector3f bottomRight = position(closestVertex(face, 1.0F, 1.0F));
            Vector3f bottomLeft = position(closestVertex(face, 0.0F, 1.0F));
            Edge[] edges = {
                    new Edge(0, new Vector2(0.0F, 0.0F), new Vector2(width, 0.0F), topLeft, topRight),
                    new Edge(1, new Vector2(width, 0.0F), new Vector2(width, height), topRight, bottomRight),
                    new Edge(2, new Vector2(width, height), new Vector2(0.0F, height), bottomRight, bottomLeft),
                    new Edge(3, new Vector2(0.0F, height), new Vector2(0.0F, 0.0F), bottomLeft, topLeft)
            };
            return new FaceGeometry(width, height, topLeft, topRight, bottomRight, bottomLeft, edges);
        }
    }

    private static PaintableSailMesh.Vertex closestVertex(
            PaintableSailMesh.Face face,
            float targetU,
            float targetV
    ) {
        PaintableSailMesh.Vertex closest = face.vertices()[0];
        float closestDistance = Float.MAX_VALUE;
        for (PaintableSailMesh.Vertex vertex : face.vertices()) {
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

    private static Vector3f position(PaintableSailMesh.Vertex vertex) {
        return new Vector3f(vertex.x(), vertex.y(), vertex.z());
    }

    private record Edge(int index, Vector2 start2, Vector2 end2, Vector3f start3, Vector3f end3) {
    }

    private record EdgeMatch(boolean reversed, float score) {
    }

    private record EdgeCandidate(
            int firstFaceIndex,
            int firstEdgeIndex,
            int secondFaceIndex,
            int secondEdgeIndex,
            boolean reversed,
            float score
    ) {
    }

    private record Connection(
            int firstFaceIndex,
            int firstEdgeIndex,
            int secondFaceIndex,
            int secondEdgeIndex,
            boolean reversed
    ) {
        private int other(int faceIndex) {
            return faceIndex == firstFaceIndex ? secondFaceIndex : firstFaceIndex;
        }

        private int edgeIndex(int faceIndex) {
            return faceIndex == firstFaceIndex ? firstEdgeIndex : secondEdgeIndex;
        }

        private int otherEdgeIndex(int faceIndex) {
            return faceIndex == firstFaceIndex ? secondEdgeIndex : firstEdgeIndex;
        }
    }

    private record Transform(
            float m00,
            float m01,
            float m10,
            float m11,
            float offsetX,
            float offsetY
    ) {
        private Vector2 map(float x, float y) {
            return new Vector2(
                    m00 * x + m01 * y + offsetX,
                    m10 * x + m11 * y + offsetY
            );
        }

        private static Transform between(
                Vector2 localStart,
                Vector2 localEnd,
                Vector2 targetStart,
                Vector2 targetEnd,
                boolean reflected
        ) {
            Vector2 local = localEnd.subtract(localStart);
            Vector2 target = targetEnd.subtract(targetStart);
            float lengthSquared = local.lengthSquared();
            if (lengthSquared < EPSILON) {
                return new Transform(1.0F, 0.0F, 0.0F, 1.0F,
                        targetStart.x() - localStart.x(), targetStart.y() - localStart.y());
            }

            float m00;
            float m01;
            float m10;
            float m11;
            if (reflected) {
                float a = (target.x() * local.x() - target.y() * local.y()) / lengthSquared;
                float b = (target.x() * local.y() + target.y() * local.x()) / lengthSquared;
                m00 = a;
                m01 = b;
                m10 = b;
                m11 = -a;
            } else {
                float a = (target.x() * local.x() + target.y() * local.y()) / lengthSquared;
                float b = (target.y() * local.x() - target.x() * local.y()) / lengthSquared;
                m00 = a;
                m01 = -b;
                m10 = b;
                m11 = a;
            }
            float offsetX = targetStart.x() - (m00 * localStart.x() + m01 * localStart.y());
            float offsetY = targetStart.y() - (m10 * localStart.x() + m11 * localStart.y());
            return new Transform(m00, m01, m10, m11, offsetX, offsetY);
        }
    }

    private record Vector2(float x, float y) {
        private Vector2 subtract(Vector2 other) {
            return new Vector2(x - other.x, y - other.y);
        }

        private float dot(Vector2 other) {
            return x * other.x + y * other.y;
        }

        private float cross(Vector2 other) {
            return x * other.y - y * other.x;
        }

        private float lengthSquared() {
            return x * x + y * y;
        }

        private Vector2 normalize() {
            float length = (float) Math.sqrt(lengthSquared());
            return length < EPSILON ? new Vector2(1.0F, 0.0F) : new Vector2(x / length, y / length);
        }
    }
}
