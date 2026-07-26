package me.mss1r.smallshipsheraldry.network;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExportPatternPayload(
        BlockPos blockPos,
        int transferId,
        int chunkIndex,
        int chunkCount,
        int totalBytes,
        byte[] chunk
) implements CustomPacketPayload {
    public static final Type<ExportPatternPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmallShipsHeraldry.MOD_ID, "export_pattern")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ExportPatternPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExportPatternPayload::write, ExportPatternPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(blockPos);
        buffer.writeVarInt(transferId);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeVarInt(totalBytes);
        buffer.writeByteArray(chunk);
    }

    private static ExportPatternPayload read(RegistryFriendlyByteBuf buffer) {
        return new ExportPatternPayload(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(HeraldryPatternTransfer.CHUNK_BYTES)
        );
    }

    @Override
    public Type<ExportPatternPayload> type() {
        return TYPE;
    }
}
