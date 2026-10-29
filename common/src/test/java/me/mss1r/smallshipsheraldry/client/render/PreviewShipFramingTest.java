package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewShipFramingTest {
    @Test
    void fittedGeometryStaysInsideTheViewportWithPadding() {
        var bounds = new PreviewShipFraming(-2, -1, -4, 2, 7, 4);
        for (int[] viewport : new int[][] {{270, 232}, {478, 256}, {800, 440}}) {
            int width = viewport[0], height = viewport[1];
            float zoom = bounds.fitZoom(width, height);
            Vector3f offset = bounds.centerOffset(180, -10, zoom);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-10)).rotateY((float) Math.PI);
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = rotation.transform(new Vector3f((corner & 1) == 0 ? -2 : 2,
                        (corner & 2) == 0 ? -1 : 7, (corner & 4) == 0 ? -4 : 4));
                float x = width / 2F + offset.x + point.x * 12 * zoom;
                float y = height / 2F + PaintableSailMesh.EDITOR_VERTICAL_OFFSET + offset.y + (point.y + .05F) * 12 * zoom;
                assertTrue(x >= 15.99F && x <= width - 15.99F);
                assertTrue(y >= 15.99F && y <= height - 15.99F);
            }
        }
    }

    @Test
    void rotatingOrZoomingKeepsTheModelCenterAtTheViewportCenter() {
        var bounds = new PreviewShipFraming(-3, -2, -5, 1, 8, 3);
        for (float yaw : new float[] {0, 45, 90, 180, 270}) for (float pitch : new float[] {-70, -10, 0, 65}) {
            for (float zoom : new float[] {.3F, 1, 4, 10}) {
                Vector3f center = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(pitch))
                        .rotateY((float) Math.toRadians(yaw)).transform(new Vector3f(-1, 3, -1));
                Vector3f offset = bounds.centerOffset(yaw, pitch, zoom);
                assertEquals(0, offset.x + center.x * 12 * zoom, .0001);
                assertEquals(0, PaintableSailMesh.EDITOR_VERTICAL_OFFSET + offset.y + (center.y + .05F) * 12 * zoom, .0001);
            }
        }
    }
}
