package me.mss1r.smallshipsheraldry.network;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraldryPatternTransferTest {
    @Test
    void splitsLargePatternBelowServerboundPayloadLimitAndRoundTrips() throws Exception {
        HeraldryPatternData source = largePattern();
        byte[] encoded = HeraldryPatternTransfer.encode(source.toTag());
        int chunkCount = HeraldryPatternTransfer.chunkCount(encoded.length);

        assertTrue(chunkCount > 1);
        HeraldryPatternTransfer.Collector collector = new HeraldryPatternTransfer.Collector();
        Optional<byte[]> complete = Optional.empty();
        for (int index = 0; index < chunkCount; index++) {
            byte[] chunk = HeraldryPatternTransfer.chunk(encoded, index);
            assertTrue(chunk.length <= HeraldryPatternTransfer.CHUNK_BYTES);
            complete = collector.accept(17, index, chunkCount, encoded.length, chunk);
        }

        byte[] assembled = complete.orElseThrow();
        assertArrayEquals(encoded, assembled);
        HeraldryPatternData restored = HeraldryPatternTransfer.decode(assembled)
                .flatMap(HeraldryPatternData::tryFromRootTag)
                .orElseThrow();
        assertEquals(source.visualHash(), restored.visualHash());
    }

    @Test
    void acceptsOutOfOrderChunksAndRejectsInvalidMetadata() throws Exception {
        byte[] encoded = HeraldryPatternTransfer.encode(largePattern().toTag());
        int chunkCount = HeraldryPatternTransfer.chunkCount(encoded.length);
        HeraldryPatternTransfer.Collector collector = new HeraldryPatternTransfer.Collector();

        for (int index = chunkCount - 1; index >= 0; index--) {
            Optional<byte[]> result = collector.accept(
                    31,
                    index,
                    chunkCount,
                    encoded.length,
                    HeraldryPatternTransfer.chunk(encoded, index)
            );
            if (index > 0) {
                assertFalse(result.isPresent());
            } else {
                assertArrayEquals(encoded, result.orElseThrow());
            }
        }

        assertFalse(collector.accept(
                32,
                0,
                HeraldryPatternTransfer.MAX_CHUNKS + 1,
                encoded.length,
                new byte[]{1}
        ).isPresent());
    }

    private static HeraldryPatternData largePattern() {
        int[] pixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        for (int run = 0; run < 12_000; run++) {
            pixels[run * 2] = 0xFF000000 | run;
        }
        return HeraldryPatternData.fromMeshPixels(
                "tester",
                ShipPreviewType.BRIGG,
                0xFF224466,
                pixels
        );
    }
}
