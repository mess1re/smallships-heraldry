package me.mss1r.smallshipsheraldry.network;

import io.netty.buffer.Unpooled;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TableDraftSyncPayloadTest {
    @Test
    void addonShipNamesRoundTripWithoutTheOldSixteenCharacterLimit() {
        for (var type : ShipPreviewType.values()) {
            String name = type.serializedName();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                var original = new TableDraftSyncPayload(BlockPos.ZERO, name, name, true, true, new byte[]{1, 2, 3});
                TableDraftSyncPayload.STREAM_CODEC.encode(buffer, original);
                var restored = TableDraftSyncPayload.STREAM_CODEC.decode(buffer);
                assertEquals(name, restored.activeShipType());
                assertEquals(name, restored.draftShipType());
                assertArrayEquals(original.encodedPattern(), restored.encodedPattern());
            } finally {
                buffer.release();
            }
        }
    }
}
