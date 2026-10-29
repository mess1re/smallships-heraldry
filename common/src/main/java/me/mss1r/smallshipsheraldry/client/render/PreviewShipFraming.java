package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Entity-space bounds and the camera-independent math used to frame a preview. */
public record PreviewShipFraming(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    private static final float BASE_SCALE = 12;

    public float fitZoom(int width, int height) {
        Quaternionf orientation = orientation(180, -10);
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = orientation.transform(new Vector3f(
                    (corner & 1) == 0 ? minX : maxX, (corner & 2) == 0 ? minY : maxY, (corner & 4) == 0 ? minZ : maxZ));
            left = Math.min(left, point.x); right = Math.max(right, point.x);
            top = Math.min(top, point.y); bottom = Math.max(bottom, point.y);
        }
        return Math.min(Math.max(1, width - 32) / (Math.max(.01F, right - left) * BASE_SCALE),
                Math.max(1, height - 32) / (Math.max(.01F, bottom - top) * BASE_SCALE));
    }

    public Vector3f centerOffset(float yaw, float pitch, float zoom) {
        Vector3f center = orientation(yaw, pitch).transform(new Vector3f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2));
        return new Vector3f(-center.x * BASE_SCALE * zoom,
                -PaintableSailMesh.EDITOR_VERTICAL_OFFSET - (center.y + .05F) * BASE_SCALE * zoom, 0);
    }

    private static Quaternionf orientation(float yaw, float pitch) {
        return new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
    }

}
