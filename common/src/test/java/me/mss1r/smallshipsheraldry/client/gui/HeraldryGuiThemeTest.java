package me.mss1r.smallshipsheraldry.client.gui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class HeraldryGuiThemeTest {
    private static final String ROOT = "/assets/smallshipsheraldry/textures/gui/editor/";

    @Test
    void everyDeclaredSpriteHasAnEditablePngWithMatchingDimensions() throws IOException {
        var names = new HashSet<String>();
        for (var sprite : HeraldryGuiTheme.Sprite.values()) {
            assertTrue(names.add(sprite.assetName), "Duplicate asset " + sprite.assetName);
            BufferedImage image = image(sprite);
            assertEquals(sprite.width, image.getWidth(), sprite.assetName);
            assertEquals(sprite.height, image.getHeight(), sprite.assetName);
        }
    }

    @Test
    void bordersLeaveAStretchableCentre() {
        for (var sprite : HeraldryGuiTheme.Sprite.values()) {
            if (sprite.border == 0) continue;
            assertTrue(sprite.border * 2 < sprite.width);
            assertTrue(sprite.border * 2 < sprite.height);
        }
    }

    @Test
    void windowHasTransparentCornersAndAnOpaqueFlatInterior() throws IOException {
        BufferedImage image = image(HeraldryGuiTheme.Sprite.WINDOW);
        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0, image.getRGB(15, 15) >>> 24);
        assertEquals(255, image.getRGB(8, 0) >>> 24);
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) assertEquals(0xFFC6C6C6, image.getRGB(x, y));
        }
    }

    @Test
    void toggleSelectionAndHoverHaveIndependentVisualStates() throws IOException {
        BufferedImage normal = image(HeraldryGuiTheme.Sprite.TOGGLE);
        BufferedImage hovered = image(HeraldryGuiTheme.Sprite.TOGGLE_HOVERED);
        BufferedImage selected = image(HeraldryGuiTheme.Sprite.TOGGLE_SELECTED);
        BufferedImage selectedHovered = image(HeraldryGuiTheme.Sprite.TOGGLE_SELECTED_HOVERED);
        assertNotEquals(normal.getRGB(4, 4), selected.getRGB(4, 4));
        assertEquals(normal.getRGB(4, 4), hovered.getRGB(4, 4));
        assertEquals(selected.getRGB(4, 4), selectedHovered.getRGB(4, 4));
        assertNotEquals(normal.getRGB(0, 0), hovered.getRGB(0, 0));
        assertNotEquals(selected.getRGB(0, 0), selectedHovered.getRGB(0, 0));
    }

    @Test
    void colourFramesDoNotCoverTheDynamicColours() throws IOException {
        for (var sprite : new HeraldryGuiTheme.Sprite[] {
                HeraldryGuiTheme.Sprite.COLOR_FRAME, HeraldryGuiTheme.Sprite.COLOR_FRAME_SELECTED}) {
            BufferedImage image = image(sprite);
            assertEquals(255, image.getRGB(0, 0) >>> 24);
            for (int y = 1; y < 7; y++) {
                for (int x = 1; x < 7; x++) assertEquals(0, image.getRGB(x, y) >>> 24);
            }
        }
    }

    private BufferedImage image(HeraldryGuiTheme.Sprite sprite) throws IOException {
        try (var stream = getClass().getResourceAsStream(ROOT + sprite.assetName + ".png")) {
            assertNotNull(stream, sprite.assetName);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, sprite.assetName);
            return image;
        }
    }
}
