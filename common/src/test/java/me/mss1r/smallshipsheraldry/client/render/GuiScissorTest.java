package me.mss1r.smallshipsheraldry.client.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiScissorTest {
    @Test
    void clipsToScaledAndTranslatedPreviewRatherThanUnscaledScreenCoordinates() {
        Matrix4f pose = new Matrix4f().scaling(0.5F, 0.5F, 1).translate(10, 10, 0);
        assertEquals(new GuiScissor.Bounds(55, 35, 105, 85),
                GuiScissor.transformedBounds(pose, 100, 60, 200, 160));
    }

    @Test
    void unscaledPreviewsKeepTheirOriginalClip() {
        assertEquals(new GuiScissor.Bounds(38, 48, 290, 238),
                GuiScissor.transformedBounds(new Matrix4f(), 38, 48, 290, 238));
    }
}
