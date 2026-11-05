package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewShipLightingTest {
    @Test
    void depthReflectionPreservesUnitNormalsAtEveryZoomAndRotation() {
        for (float scale : new float[] {3.6F, 12, 36, 120}) {
            for (int yaw = 0; yaw < 360; yaw += 30) {
                var poses = new PoseStack();
                PreviewShipLighting.scale(poses, scale);
                poses.mulPose(new Quaternionf().rotateZ((float) Math.PI)
                        .rotateX(-0.2F).rotateY((float) Math.toRadians(yaw)));
                for (Vector3f axis : new Vector3f[] {
                        new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
                    Vector3f normal = poses.last().normal().transform(new Vector3f(axis));
                    assertTrue(normal.isFinite(), "non-finite normal at scale " + scale);
                    assertEquals(1, normal.length(), 0.00001);
                    Vector3f direction = poses.last().pose().transformDirection(new Vector3f(axis)).normalize();
                    assertEquals(0, normal.distance(direction), 0.00001);
                }
            }
        }
    }

    @Test
    void scalingPreservesPositionAndDoesNotLeakOutsideThePreviewPose() {
        var poses = new PoseStack();
        poses.translate(30, 40, 180);
        poses.pushPose();
        PreviewShipLighting.scale(poses, 12);
        Vector3f point = poses.last().pose().transformPosition(new Vector3f(1, 2, 3));
        assertEquals(0, point.distance(new Vector3f(42, 64, 144)), 0.00001);
        poses.popPose();
        assertEquals(0, poses.last().normal().transform(new Vector3f(0, 0, 1))
                .distance(new Vector3f(0, 0, 1)), 0.00001);
    }
}
