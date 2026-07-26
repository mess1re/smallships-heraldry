package me.mss1r.smallshipsheraldry.network;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TableDraftUpdatePayload(
        BlockPos blockPos,
        int transferId,
        int chunkIndex,
        int chunkCount,
        int totalBytes,
        byte[] chunk
) implements CustomPacketPayload {
    public static final Type<TableDraftUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmallShipsHeraldry.MOD_ID, "table_draft_update")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TableDraftUpdatePayload> STREAM_CODEC =
            StreamCodec.ofMember(TableDraftUpdatePayload::write, TableDraftUpdatePayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(blockPos);
        buffer.writeVarInt(transferId);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeVarInt(totalBytes);
        buffer.writeByteArray(chunk);
    }

    private static TableDraftUpdatePayload read(RegistryFriendlyByteBuf buffer) {
        return new TableDraftUpdatePayload(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(HeraldryPatternTransfer.CHUNK_BYTES)
        );
    }

    @Override
    public Type<TableDraftUpdatePayload> type() {
        return TYPE;
    }
}
