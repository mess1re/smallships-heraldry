package me.mss1r.smallshipsheraldry.network;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TableDraftRequestPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<TableDraftRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmallShipsHeraldry.MOD_ID, "table_draft_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TableDraftRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(TableDraftRequestPayload::write, TableDraftRequestPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(blockPos);
    }

    private static TableDraftRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new TableDraftRequestPayload(buffer.readBlockPos());
    }

    @Override
    public Type<TableDraftRequestPayload> type() {
        return TYPE;
    }
}
