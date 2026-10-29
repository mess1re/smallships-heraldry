package me.mss1r.smallshipsheraldry.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeraldryPreviewCameraTest {
    @Test
    void resizeRescalesPanWithoutChangingTheUsersZoomOrOrientation() {
        var camera = new HeraldryPreviewCamera();
        camera.restore(90, 15, 2);
        camera.pan(24, -12);
        camera.rescalePan(1.5F);
        assertEquals(36, camera.panX());
        assertEquals(-18, camera.panY());
        assertEquals(2, camera.zoom());
        assertEquals(90, camera.yaw());
        assertEquals(15, camera.pitch());
        camera.rescalePan(1 / 1.5F);
        assertEquals(24, camera.panX(), 0.0001);
        assertEquals(-12, camera.panY(), 0.0001);
    }

    @Test
    void zoomKeepsCursorPointAnchoredWithPan() {
        var camera = new HeraldryPreviewCamera();
        camera.pan(15, -8);
        float modelX = (230 - 150 - camera.panX()) / camera.zoom();
        float modelY = (80 - 100 - camera.panY()) / camera.zoom();
        camera.zoomAt(230, 80, 1, 150, 100);
        assertEquals(230, 150 + camera.panX() + modelX * camera.zoom(), 0.0001);
        assertEquals(80, 100 + camera.panY() + modelY * camera.zoom(), 0.0001);
    }

    @Test
    void rotationAndZoomRespectEditorLimits() {
        var camera = new HeraldryPreviewCamera();
        camera.rotate(10, 1000);
        assertEquals(194, camera.yaw());
        assertEquals(80, camera.pitch());
        for (int i = 0; i < 100; i++) camera.zoomAt(0, 0, 1, 0, 0);
        assertEquals(10, camera.zoom());
        for (int i = 0; i < 100; i++) camera.zoomAt(0, 0, -1, 0, 0);
        assertEquals(0.30F, camera.zoom());
    }

    @Test
    void presetResetsPanButOnlyFullResetChangesZoom() {
        var camera = new HeraldryPreviewCamera();
        camera.restore(30, 20, 2);
        camera.pan(100, 50);
        camera.orient(90, 0);
        assertEquals(0, camera.panX());
        assertEquals(0, camera.panY());
        assertEquals(2, camera.zoom());
        camera.reset();
        assertEquals(180, camera.yaw());
        assertEquals(-10, camera.pitch());
        assertEquals(1, camera.zoom());
    }
}
