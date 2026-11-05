package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;

final class PreviewShipLighting {
    private static final Vector3f KEY = new Vector3f(-0.35F, -0.8F, 0.65F).normalize();
    private static final Vector3f FILL = new Vector3f(0.6F, -0.35F, -0.2F).normalize();

    private PreviewShipLighting() {
    }

    static void setup() {
        RenderSystem.setShaderLights(KEY, FILL);
    }

    static void scale(PoseStack poses, float scale) {
        // 1.20.1's PoseStack normalizes negative-determinant scales incorrectly.
        // Apply the depth reflection separately so normals stay unit length.
        poses.scale(scale, scale, scale);
        poses.last().pose().scale(1, 1, -1);
        poses.last().normal().scale(1, 1, -1);
    }
}
