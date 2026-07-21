package me.mss1r.smallshipsheraldry.common;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraldryPatternDataTest {
    @Test
    void blankPatternStaysSmall() {
        HeraldryPatternData blank = HeraldryPatternData.blank();

        assertEquals(0, blank.runCount());
        assertEquals(0, blank.paintedPixelCount());
        assertTrue(blank.encodedSizeBytes() <= 2);
        assertTrue(blank.estimatedMemoryBytes() < 128);
    }

    @Test
    void compactTagRoundTripsExactly() {
        int[] pixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        Arrays.fill(pixels, 100, 300, 0xFFAA3311);
        Arrays.fill(pixels, 2_000, 2_100, 0x804477CC);
        pixels[90_000] = 0xFFFFFFFF;

        HeraldryPatternData source = HeraldryPatternData.fromMeshPixels(
                "TestAuthor",
                ShipPreviewType.BRIGG,
                0xFF123456,
                pixels
        );
        HeraldryPatternData decoded = HeraldryPatternData.tryFromRootTag(source.toTag()).orElseThrow();

        assertEquals("TestAuthor", decoded.author());
        assertEquals(ShipPreviewType.BRIGG, decoded.shipType());
        assertEquals(0xFF123456, decoded.baseColorArgb());
        assertEquals(301, decoded.paintedPixelCount());
        assertArrayEquals(pixels, decoded.toDenseMeshPixels());
        assertTrue(decoded.encodedSizeBytes() < 64);
    }

    @Test
    void migratesPreviousSparseFormat() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString(HeraldryPatternData.SHIP_TYPE_TAG, "galley");
        legacy.putIntArray("MeshPixelIndices", new int[]{5, 6, 100});
        legacy.putIntArray("MeshPixelColors", new int[]{0xFF112233, 0xFF112233, 0xFF445566});

        HeraldryPatternData migrated = HeraldryPatternData.tryFromRootTag(legacy).orElseThrow();

        assertEquals(2, migrated.runCount());
        assertEquals(3, migrated.paintedPixelCount());
        assertEquals(0xFF112233, migrated.meshAtlasPixelAt(5, 0));
        assertEquals(0xFF445566, migrated.meshAtlasPixelAt(100, 0));
    }

    @Test
    void rejectsMalformedOrPathologicalPayloads() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("PixelFormat", 1);
        malformed.putByteArray("PixelRuns", new byte[]{1, 1, (byte) 0x80});
        assertFalse(HeraldryPatternData.tryFromRootTag(malformed).isPresent());

        int[] pixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        for (int index = 0; index <= HeraldryPatternData.MAX_RUNS * 2; index += 2) {
            pixels[index] = 0xFFFFFFFF;
        }
        assertThrows(
                HeraldryPatternData.PatternTooComplexException.class,
                () -> HeraldryPatternData.fromMeshPixels("", ShipPreviewType.COG, 0xFFFFFFFF, pixels)
        );
    }
}
