package me.mss1r.smallshipsheraldry.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraldryEditorLayoutTest {
    @Test
    void wrappedHintsCannotReachTheFooter() {
        for (int height : new int[] {324, 360, 480, 720}) {
            var layout = new HeraldryEditorLayout(640, height, 1);
            for (int y : new int[] {152, 180, 248}) {
                int lines = layout.textLinesBeforeFooter(y);
                assertTrue(lines > 0);
                assertTrue(y + lines * 10 <= layout.footerY() - 6);
                assertTrue(y + (lines + 1) * 10 > layout.footerY() - 6);
            }
            assertEquals(0, layout.textLinesBeforeFooter(layout.footerY()));
            assertEquals(0, layout.textLinesBeforeFooter(layout.height()));
        }
    }

    @Test
    void hitRegionsIncludeTopLeftAndExcludeBottomRight() {
        assertTrue(HeraldryEditorLayout.isInside(10, 20, 10, 20, 18, 18));
        assertTrue(HeraldryEditorLayout.isInside(27.999, 37.999, 10, 20, 18, 18));
        assertFalse(HeraldryEditorLayout.isInside(28, 25, 10, 20, 18, 18));
        assertFalse(HeraldryEditorLayout.isInside(15, 38, 10, 20, 18, 18));
        assertFalse(HeraldryEditorLayout.isInside(9.999, 25, 10, 20, 18, 18));
        assertFalse(HeraldryEditorLayout.isInside(15, 19.999, 10, 20, 18, 18));
    }

    @Test
    void emptyRegionsCannotBeClicked() {
        assertFalse(HeraldryEditorLayout.isInside(10, 20, 10, 20, 0, 18));
        assertFalse(HeraldryEditorLayout.isInside(10, 20, 10, 20, 18, 0));
    }

    @Test
    void fitsCommonWindowSizesAtEveryMinecraftGuiScale() {
        for (int[] window : new int[][] {{854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}}) {
            for (int minecraftScale = 1; minecraftScale <= 4; minecraftScale++) {
                int width = (int) Math.ceil(window[0] / (double) minecraftScale);
                int height = (int) Math.ceil(window[1] / (double) minecraftScale);
                float scale = HeraldryEditorLayout.fitScale(width, height, minecraftScale);
                assertTrue((HeraldryEditorLayout.MIN_WIDTH + 12) * scale <= width + 0.001);
                assertTrue((HeraldryEditorLayout.MIN_HEIGHT + 12) * scale <= height + 0.001);
                double physicalPixel = scale * minecraftScale;
                assertEquals(Math.rint(physicalPixel), physicalPixel, 0.00001);
                assertTrue(scale <= 1);
            }
        }
    }

    @Test
    void doesNotEnlargeUiBeyondUserScale() {
        assertEquals(1, HeraldryEditorLayout.fitScale(1280, 720, 2));
        assertEquals(0.75F, HeraldryEditorLayout.fitScale(480, 270, 4));
        assertEquals(0.5F, HeraldryEditorLayout.fitScale(427, 240, 2));
    }

    @Test
    void verySmallWindowsStillKeepTheWholeEditorAccessible() {
        float scale = HeraldryEditorLayout.fitScale(320, 240, 1);
        assertTrue((HeraldryEditorLayout.MIN_WIDTH + 12) * scale <= 320 + 0.001);
        assertTrue((HeraldryEditorLayout.MIN_HEIGHT + 12) * scale <= 240 + 0.001);
    }

    @Test
    void usesTheAvailableWindowInsteadOfCenteringAFixedSizeContainer() {
        for (int[] window : new int[][] {{854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3440, 1440}}) {
            for (int guiScale = 1; guiScale <= 4; guiScale++) {
                int width = (int) Math.ceil(window[0] / (double) guiScale);
                int height = (int) Math.ceil(window[1] / (double) guiScale);
                var layout = HeraldryEditorLayout.create(width, height, guiScale);
                assertTrue(layout.width() >= HeraldryEditorLayout.MIN_WIDTH - 1);
                assertTrue(layout.height() >= HeraldryEditorLayout.MIN_HEIGHT - 1);
                assertTrue(width - layout.width() * layout.scale() <= 13);
                assertTrue(height - layout.height() * layout.scale() <= 13);
                assertTrue(layout.previewWidth() >= 270);
                assertTrue(layout.previewHeight() >= 231);
                assertTrue(layout.previewX() + layout.previewWidth() < layout.rightX());
                assertTrue(HeraldryEditorLayout.PREVIEW_Y + layout.previewHeight() < layout.navigationY());
                assertTrue(layout.navigationY() + 18 < layout.footerY());
                assertTrue(layout.footerY() + 18 < layout.height());
                assertTrue(206 < layout.paperSlotX() - 1);
                assertTrue(layout.resultSlotX() + 17 < layout.rightX() + 4);
                assertTrue(layout.shipNameWidth() >= 104);
                assertTrue(layout.shipNextX() + 20 < layout.stagePreviousX());
            }
        }
    }

    @Test
    void paintingAreaAtFullHdIsMoreThanTwiceThePreviousArea() {
        var layout = HeraldryEditorLayout.create(640, 360, 3);
        assertTrue(layout.previewWidth() * layout.previewHeight() > 252 * 190 * 2);
        assertTrue(layout.previewScale() > 1);
    }
}
