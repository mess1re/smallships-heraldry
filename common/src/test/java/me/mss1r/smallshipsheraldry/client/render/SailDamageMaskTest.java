package me.mss1r.smallshipsheraldry.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SailDamageMaskTest {
    @Test
    void samplesPixelCentersRatherThanTileBoundaries() {
        for (int pixel = 0; pixel < 4; pixel++) {
            assertEquals(4 + pixel, SailDamageMask.sourcePixel(0.25F, 0.5F, pixel, 4, 16, false));
        }
    }

    @Test
    void scalesNormalizedUvsToResourcePackResolution() {
        assertEquals(9, SailDamageMask.sourcePixel(0.25F, 0.5F, 0, 4, 32, false));
        assertEquals(15, SailDamageMask.sourcePixel(0.25F, 0.5F, 3, 4, 32, false));
    }

    @Test
    void degenerateAndEdgeUvsStayInsideTheSourceTexture() {
        assertEquals(7, SailDamageMask.sourcePixel(1, 1, 0, 1, 8, true));
        assertEquals(0, SailDamageMask.sourcePixel(-1, -1, 0, 1, 8, true));
        assertEquals(3, SailDamageMask.sourcePixel(0.375F, 0.375F, 0, 1, 8, true));
    }

    @Test
    void repeatingUvsWrapLikeTheNativeSailTexture() {
        assertEquals(0, SailDamageMask.sourcePixel(1, 1, 0, 1, 8, false));
        assertEquals(6, SailDamageMask.sourcePixel(-0.25F, -0.25F, 0, 1, 8, false));
        assertEquals(2, SailDamageMask.sourcePixel(1.25F, 1.25F, 0, 1, 8, false));
    }

    @Test
    void holesRemovePaintButKeepRgbUnchanged() {
        assertEquals(0x00123456, SailDamageMask.maskedColor(0xFF123456, 0));
        assertEquals(0x00123456, SailDamageMask.maskedColor(0xFF123456, 25));
    }

    @Test
    void intactClothPreservesTranslucentPaint() {
        assertEquals(0x80123456, SailDamageMask.maskedColor(0x80123456, 255));
        assertEquals(0x40123456, SailDamageMask.maskedColor(0x80123456, 128));
        assertEquals(0x00123456, SailDamageMask.maskedColor(0x00123456, 255));
    }
}
