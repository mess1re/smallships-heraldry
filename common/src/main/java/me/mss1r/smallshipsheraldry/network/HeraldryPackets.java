package me.mss1r.smallshipsheraldry.network;

import dev.architectury.networking.NetworkManager;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.world.inventory.HeraldryTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class HeraldryPackets {
    public static final ResourceLocation EXPORT_PATTERN = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "export_pattern");
    public static final ResourceLocation TABLE_SLOT_ACTION = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "table_slot_action");
    public static final ResourceLocation TABLE_DRAFT_UPDATE = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "table_draft_update");
    public static final ResourceLocation TABLE_DRAFT_REQUEST = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "table_draft_request");
    public static final ResourceLocation TABLE_DRAFT_SYNC = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "table_draft_sync");

    private HeraldryPackets() {
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), EXPORT_PATTERN, (buf, context) -> {
            BlockPos blockPos = buf.readBlockPos();
            int transferId = buf.readVarInt();
            int chunkIndex = buf.readVarInt();
            int chunkCount = buf.readVarInt();
            int totalBytes = buf.readVarInt();
            byte[] chunk = buf.readByteArray(HeraldryPatternTransfer.CHUNK_BYTES);
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                    menu.handleExportChunk(serverPlayer, blockPos, transferId, chunkIndex, chunkCount, totalBytes, chunk);
                }
            });
        });
        NetworkManager.registerReceiver(NetworkManager.c2s(), TABLE_SLOT_ACTION, (buf, context) -> {
            BlockPos blockPos = buf.readBlockPos();
            int slot = buf.readVarInt();
            int action = buf.readVarInt();
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                    menu.handleSlotAction(serverPlayer, blockPos, slot, action);
                }
            });
        });
        NetworkManager.registerReceiver(NetworkManager.c2s(), TABLE_DRAFT_UPDATE, (buf, context) -> {
            BlockPos blockPos = buf.readBlockPos();
            int transferId = buf.readVarInt();
            int chunkIndex = buf.readVarInt();
            int chunkCount = buf.readVarInt();
            int totalBytes = buf.readVarInt();
            byte[] chunk = buf.readByteArray(HeraldryPatternTransfer.CHUNK_BYTES);
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                    menu.handleDraftChunk(serverPlayer, blockPos, transferId, chunkIndex, chunkCount, totalBytes, chunk);
                }
            });
        });
        NetworkManager.registerReceiver(NetworkManager.c2s(), TABLE_DRAFT_REQUEST, (buf, context) -> {
            BlockPos blockPos = buf.readBlockPos();
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                    menu.handleDraftRequest(serverPlayer, blockPos);
                }
            });
        });
    }
}
