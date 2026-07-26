package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;

final class SailMeshRenderer {
    private static final float PAINT_NORMAL_PUSH = 0.003F;

    private SailMeshRenderer() {
    }

    static void renderPainted(
            List<PaintableSailMesh.Face> faces,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            Set<String> hiddenElements
    ) {
        if (faces.isEmpty()) {
            return;
        }
        for (PaintableSailMesh.Face face : faces) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            PoseStack.Pose pose = poseStack.last();
            PaintableSailMesh.AtlasSlot slot = face.slot();
            for (PaintableSailMesh.Vertex vertex : face.vertices()) {
                float u = (slot.x() + vertex.localU() * slot.width()) / HeraldryPatternData.MESH_ATLAS_WIDTH;
                float v = (slot.y() + vertex.localV() * slot.height()) / HeraldryPatternData.MESH_ATLAS_HEIGHT;
                Vector3f normal = face.normal();
                emitPaintVertex(
                        face,
                        pose,
                        vertexConsumer,
                        vertex.x() + normal.x() * PAINT_NORMAL_PUSH,
                        vertex.y() + normal.y() * PAINT_NORMAL_PUSH,
                        vertex.z() + normal.z() * PAINT_NORMAL_PUSH,
                        u,
                        v,
                        packedLight
                );
            }
        }
    }

    static void renderOriginal(PaintableSailMesh.Face face, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        for (PaintableSailMesh.Vertex vertex : face.vertices()) {
            vertexConsumer.addVertex(pose.pose(), vertex.x(), vertex.y(), vertex.z())
                    .setColor(255, 255, 255, 255)
                    .setUv(vertex.originalU(), vertex.originalV())
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, face.normal().x(), face.normal().y(), face.normal().z());
        }
    }

    static void renderHighlight(PaintableSailMesh.Face face, PoseStack poseStack, VertexConsumer vertexConsumer) {
        PaintableSailMesh.Vertex[] vertices = face.vertices();
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[0], vertices[1]);
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[1], vertices[2]);
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[2], vertices[3]);
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[3], vertices[0]);
    }

    static void renderGrid(PaintableSailMesh.Face face, PoseStack poseStack, VertexConsumer vertexConsumer) {
        GridCorners corners = gridCorners(face);
        PaintableSailMesh.AtlasSlot slot = face.slot();
        for (int x = 1; x < slot.width(); x++) {
            float u = x / (float) slot.width();
            emitGridLine(face, poseStack, vertexConsumer, interpolate(corners, u, 0.0F), interpolate(corners, u, 1.0F));
        }
        for (int y = 1; y < slot.height(); y++) {
            float v = y / (float) slot.height();
            emitGridLine(face, poseStack, vertexConsumer, interpolate(corners, 0.0F, v), interpolate(corners, 1.0F, v));
        }
    }

    private static void emitPaintVertex(
            PaintableSailMesh.Face face,
            PoseStack.Pose pose,
            VertexConsumer vertexConsumer,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight
    ) {
        vertexConsumer.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, face.normal().x(), face.normal().y(), face.normal().z());
    }

    private static void emitHighlightLine(
            PaintableSailMesh.Face face,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            PaintableSailMesh.Vertex from,
            PaintableSailMesh.Vertex to
    ) {
        float push = 0.028F;
        Vector3f normal = face.normal();
        Vector3f lineDirection = new Vector3f(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        normalizeLineDirection(lineDirection);
        emitLineVertex(poseStack, vertexConsumer, from.x() + normal.x() * push, from.y() + normal.y() * push, from.z() + normal.z() * push, lineDirection, 95, 220, 255);
        emitLineVertex(poseStack, vertexConsumer, to.x() + normal.x() * push, to.y() + normal.y() * push, to.z() + normal.z() * push, lineDirection, 95, 220, 255);
    }

    private static GridCorners gridCorners(PaintableSailMesh.Face face) {
        return new GridCorners(
                closestVertex(face, 0.0F, 0.0F),
                closestVertex(face, 1.0F, 0.0F),
                closestVertex(face, 1.0F, 1.0F),
                closestVertex(face, 0.0F, 1.0F)
        );
    }

    private static PaintableSailMesh.Vertex closestVertex(PaintableSailMesh.Face face, float targetU, float targetV) {
        PaintableSailMesh.Vertex best = face.vertices()[0];
        float bestDistance = Float.MAX_VALUE;
        for (PaintableSailMesh.Vertex vertex : face.vertices()) {
            float du = vertex.localU() - targetU;
            float dv = vertex.localV() - targetV;
            float distance = du * du + dv * dv;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = vertex;
            }
        }
        return best;
    }

    private static Vector3f interpolate(GridCorners corners, float u, float v) {
        Vector3f top = lerp(corners.topLeft(), corners.topRight(), u);
        Vector3f bottom = lerp(corners.bottomLeft(), corners.bottomRight(), u);
        return top.lerp(bottom, v);
    }

    private static Vector3f lerp(PaintableSailMesh.Vertex from, PaintableSailMesh.Vertex to, float delta) {
        return new Vector3f(from.x(), from.y(), from.z()).lerp(new Vector3f(to.x(), to.y(), to.z()), delta);
    }

    private static void emitGridLine(
            PaintableSailMesh.Face face,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            Vector3f from,
            Vector3f to
    ) {
        float push = 0.014F;
        Vector3f normal = face.normal();
        Vector3f lineDirection = new Vector3f(to).sub(from);
        normalizeLineDirection(lineDirection);
        emitLineVertex(poseStack, vertexConsumer, from.x() + normal.x() * push, from.y() + normal.y() * push, from.z() + normal.z() * push, lineDirection, 255, 224, 124);
        emitLineVertex(poseStack, vertexConsumer, to.x() + normal.x() * push, to.y() + normal.y() * push, to.z() + normal.z() * push, lineDirection, 255, 224, 124);
    }

    private static void normalizeLineDirection(Vector3f direction) {
        if (direction.lengthSquared() > 0.00001F) {
            direction.normalize();
        } else {
            direction.set(1.0F, 0.0F, 0.0F);
        }
    }

    private static void emitLineVertex(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            float x,
            float y,
            float z,
            Vector3f lineDirection,
            int red,
            int green,
            int blue
    ) {
        PoseStack.Pose pose = poseStack.last();
        vertexConsumer.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, 230)
                .setNormal(pose, lineDirection.x(), lineDirection.y(), lineDirection.z());
    }

    private record GridCorners(
            PaintableSailMesh.Vertex topLeft,
            PaintableSailMesh.Vertex topRight,
            PaintableSailMesh.Vertex bottomRight,
            PaintableSailMesh.Vertex bottomLeft
    ) {
    }
}
