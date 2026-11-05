package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SailPaintShading {
    private SailPaintShading() {}

    static List<PaintableSailMesh.Face> build(List<PaintableSailMesh.Face> faces) {
        Map<Point, List<WeightedNormal>> incident = new HashMap<>();
        for (var face : faces) {
            if (face.elementId().toLowerCase(java.util.Locale.ROOT).contains("rope")) continue;
            var a = face.vertices()[0];
            var b = face.vertices()[1];
            var d = face.vertices()[3];
            float area = new Vector3f(b.x() - a.x(), b.y() - a.y(), b.z() - a.z())
                    .cross(d.x() - a.x(), d.y() - a.y(), d.z() - a.z()).length();
            for (var vertex : face.vertices()) {
                incident.computeIfAbsent(Point.of(vertex), ignored -> new ArrayList<>())
                        .add(new WeightedNormal(face.normal(), area));
            }
        }
        List<PaintableSailMesh.Face> result = new ArrayList<>(faces.size());
        for (var face : faces) {
            if (face.elementId().toLowerCase(java.util.Locale.ROOT).contains("rope")) {
                result.add(face);
                continue;
            }
            var vertices = new PaintableSailMesh.Vertex[face.vertices().length];
            for (int index = 0; index < vertices.length; index++) {
                var vertex = face.vertices()[index];
                Vector3f normal = new Vector3f();
                for (var neighbour : incident.get(Point.of(vertex))) {
                    // Never average opposite sides of zero-thickness cloth.
                    if (neighbour.normal.dot(face.normal()) > -0.75F) {
                        normal.fma(neighbour.area, neighbour.normal);
                    }
                }
                if (normal.lengthSquared() < 0.00000001F) normal.set(face.normal());
                else normal.normalize();
                vertices[index] = new PaintableSailMesh.Vertex(vertex.x(), vertex.y(), vertex.z(),
                        vertex.originalU(), vertex.originalV(), vertex.localU(), vertex.localV(), normal);
            }
            result.add(new PaintableSailMesh.Face(face.faceIndex(), face.state(), face.elementId(),
                    vertices, face.normal(), face.slot()));
        }
        return List.copyOf(result);
    }

    private record WeightedNormal(Vector3f normal, float area) {}
    private record Point(int x, int y, int z) {
        static Point of(PaintableSailMesh.Vertex vertex) {
            return new Point(Math.round(vertex.x() * 10000), Math.round(vertex.y() * 10000),
                    Math.round(vertex.z() * 10000));
        }
    }
}
