package me.mss1r.smallshipsheraldry.client.network;

import dev.architectury.networking.NetworkManager;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryTableScreen;
import me.mss1r.smallshipsheraldry.network.HeraldryPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class HeraldryClientPackets {
    private static boolean registered;

    private HeraldryClientPackets() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        NetworkManager.registerReceiver(NetworkManager.s2c(), HeraldryPackets.TABLE_DRAFT_SYNC, (buf, context) -> {
            BlockPos blockPos = buf.readBlockPos();
            CompoundTag draftTag = buf.readNbt();
            context.queue(() -> HeraldryTableScreen.handleDraftSync(blockPos, draftTag));
        });
    }
}
