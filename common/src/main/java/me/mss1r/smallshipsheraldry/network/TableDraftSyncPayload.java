package me.mss1r.smallshipsheraldry.network;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TableDraftSyncPayload(
        BlockPos blockPos,
        String activeShipType,
        String draftShipType,
        boolean first,
        boolean last,
        byte[] encodedPattern
) implements CustomPacketPayload {
    public static final Type<TableDraftSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmallShipsHeraldry.MOD_ID, "table_draft_sync")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TableDraftSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(TableDraftSyncPayload::write, TableDraftSyncPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(blockPos);
        buffer.writeUtf(activeShipType, 16);
        buffer.writeUtf(draftShipType, 16);
        buffer.writeBoolean(first);
        buffer.writeBoolean(last);
        buffer.writeByteArray(encodedPattern);
    }

    private static TableDraftSyncPayload read(RegistryFriendlyByteBuf buffer) {
        return new TableDraftSyncPayload(
                buffer.readBlockPos(),
                buffer.readUtf(16),
                buffer.readUtf(16),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readByteArray(HeraldryPatternTransfer.MAX_COMPRESSED_BYTES)
        );
    }

    @Override
    public Type<TableDraftSyncPayload> type() {
        return TYPE;
    }
}
