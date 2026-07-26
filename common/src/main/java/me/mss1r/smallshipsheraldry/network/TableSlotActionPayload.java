package me.mss1r.smallshipsheraldry.network;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TableSlotActionPayload(BlockPos blockPos, int slot, int action) implements CustomPacketPayload {
    public static final Type<TableSlotActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmallShipsHeraldry.MOD_ID, "table_slot_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TableSlotActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(TableSlotActionPayload::write, TableSlotActionPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(blockPos);
        buffer.writeVarInt(slot);
        buffer.writeVarInt(action);
    }

    private static TableSlotActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new TableSlotActionPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<TableSlotActionPayload> type() {
        return TYPE;
    }
}
