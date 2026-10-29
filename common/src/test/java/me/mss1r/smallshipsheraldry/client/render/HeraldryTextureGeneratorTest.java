package me.mss1r.smallshipsheraldry.client.render;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeraldryTextureGeneratorTest {
    @Test
    void reusingAnAtlasClearsThePreviousShipAndPreservesNewPixelColors() {
        int[] pixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        Arrays.fill(pixels, 0xFFCC1122);
        var first = HeraldryPatternData.fromMeshPixels("", ShipPreviewType.COG, 0xFFFFFFFF, pixels);
        try (var image = HeraldryTextureGenerator.buildComposedTexture(first)) {
            Arrays.fill(pixels, 0);
            pixels[1281] = 0x80123456;
            pixels[pixels.length - 1] = 0xFF102030;
            var second = HeraldryPatternData.fromMeshPixels("", ShipPreviewType.COG, 0xFFFFFFFF, pixels);
            HeraldryTextureGenerator.paintInto(image, second);
            for (int i = 0; i < pixels.length; i++) {
                assertEquals(HeraldryTextureGenerator.argbToAbgr(pixels[i]),
                        image.getPixelRGBA(i % image.getWidth(), i / image.getWidth()));
            }
            HeraldryTextureGenerator.paintInto(image, HeraldryPatternData.blank());
            assertEquals(0, image.getPixelRGBA(1, 1));
            assertEquals(0, image.getPixelRGBA(1279, 1279));
        }
    }
}
