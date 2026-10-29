package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorPickScaleTest {
    @Test
    void scaledRenderingKeepsPickingInLogicalScreenCoordinates() {
        for (float scale : new float[] {1, 0.75F, 0.5F, 1 / 3.0F}) {
            PoseStack rendered = new PoseStack();
            rendered.scale(scale, scale, 1);
            rendered.translate(164, 170, 180);
            rendered.scale(12, 12, -12);
            PoseStack logical = EditorPickTransform.logicalPose(rendered, scale);
            Vector3f vertex = logical.last().pose().transformPosition(new Vector3f(2, -3, 1));
            assertEquals(188, vertex.x, 0.0001);
            assertEquals(134, vertex.y, 0.0001);
            assertEquals(168, vertex.z, 0.0001);
            assertEquals(164 * scale, rendered.last().pose().m30(), 0.0001);
        }
    }
}
