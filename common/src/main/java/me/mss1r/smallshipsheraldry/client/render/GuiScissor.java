package me.mss1r.smallshipsheraldry.client.render;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class GuiScissor {
    private GuiScissor() {
    }

    public static void enable(GuiGraphics graphics, int left, int top, int right, int bottom) {
        // GuiGraphics scissors use screen coordinates, not the current GUI pose.
        Bounds bounds = transformedBounds(graphics.pose().last().pose(), left, top, right, bottom);
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
    }

    static Bounds transformedBounds(Matrix4f pose, int left, int top, int right, int bottom) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            Vector3f point = pose.transformPosition(new Vector3f((corner & 1) == 0 ? left : right, (corner & 2) == 0 ? top : bottom, 0));
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return new Bounds((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }

    record Bounds(int left, int top, int right, int bottom) {
    }
}
