package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SailPaintSeamTest {

    @Test
    void foldedFacesKeepSharedEdgesClosed() {
        var faces = foldedFaces();
        var vertices = new ArrayList<Vector3f>();
        SailMeshRenderer.renderPainted(faces, new PoseStack(), capture(vertices), 15728880, Set.of());
        assertEquals(8, vertices.size());
        assertEquals(vertices.get(1), vertices.get(4));
        assertEquals(vertices.get(2), vertices.get(7));
        for (int face = 0; face < faces.size(); face++) {
            for (int index = 0; index < 4; index++) {
                var source = faces.get(face).vertices()[index];
                assertEquals(new Vector3f(source.x(), source.y(), source.z()), vertices.get(face * 4 + index));
            }
        }
    }

    @Test
    void paintUsesTheOriginalSurfaceAndStillHonoursHiddenElements() {
        var faces = foldedFaces();
        var painted = new ArrayList<Vector3f>();
        var original = new ArrayList<Vector3f>();
        SailMeshRenderer.renderPainted(faces, new PoseStack(), capture(painted), 0, Set.of("second"));
        SailMeshRenderer.renderOriginal(faces.get(0), new PoseStack(), capture(original), 0);
        assertEquals(4, painted.size());
        assertEquals(original, painted);
    }

    private static VertexConsumer capture(List<Vector3f> vertices) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[] {VertexConsumer.class}, (proxy, method, args) -> {
                    if (args != null && args.length == 4 && args[0] instanceof Matrix4f
                            && (method.getName().equals("vertex") || method.getName().equals("addVertex"))) {
                        vertices.add(new Vector3f((float) args[1], (float) args[2], (float) args[3]));
                    }
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
    }

    private static List<PaintableSailMesh.Face> foldedFaces() {
        return List.of(
                new PaintableSailMesh.Face(0, 4, "first", new PaintableSailMesh.Vertex[] {
                        vertex(0, 0, 0), vertex(1, 0, 0), vertex(1, 1, 0), vertex(0, 1, 0)},
                        new Vector3f(0, 0, 1), new PaintableSailMesh.AtlasSlot(0, 1, 1, 16, 16)),
                new PaintableSailMesh.Face(1, 4, "second", new PaintableSailMesh.Vertex[] {
                        vertex(1, 0, 0), vertex(2, 0, 1), vertex(2, 1, 1), vertex(1, 1, 0)},
                        new Vector3f(-1, 0, 1).normalize(), new PaintableSailMesh.AtlasSlot(1, 20, 1, 16, 16)));
    }

    private static PaintableSailMesh.Vertex vertex(float x, float y, float z) {
        return new PaintableSailMesh.Vertex(x, y, z, 0, 0, 0, 0);
    }
}
