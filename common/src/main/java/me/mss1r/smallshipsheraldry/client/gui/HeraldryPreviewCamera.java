package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.util.Mth;

/** Camera motion in logical GUI pixels. Zoom keeps the point under the cursor stationary. */
final class HeraldryPreviewCamera {
    private float yaw = 180;
    private float pitch = -10;
    private float zoom = 1;
    private float panX;
    private float panY;

    float yaw() { return yaw; }
    float pitch() { return pitch; }
    float zoom() { return zoom; }
    float panX() { return panX; }
    float panY() { return panY; }

    void restore(float yaw, float pitch, float zoom) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
    }

    void rotate(double dx, double dy) {
        yaw = (yaw + (float) dx * 1.4F) % 360;
        pitch = Mth.clamp(pitch + (float) dy * 1.2F, -80, 80);
    }

    void pan(double dx, double dy) {
        panX += (float) dx;
        panY += (float) dy;
    }

    void rescalePan(float ratio) {
        panX *= ratio;
        panY *= ratio;
    }

    void center() {
        panX = panY = 0;
    }

    void orient(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        center();
    }

    void reset() {
        orient(180, -10);
        zoom = 1;
    }

    void zoomAt(double x, double y, double amount, float centerX, float centerY) {
        float newZoom = Mth.clamp(zoom * (amount > 0 ? 1.14F : 1 / 1.14F), 0.30F, 10);
        if (Math.abs(newZoom - zoom) < 0.0001F) return;
        float ratio = newZoom / zoom;
        panX = (float) (x - centerX - (x - centerX - panX) * ratio);
        panY = (float) (y - centerY - (y - centerY - panY) * ratio);
        zoom = newZoom;
    }
}
