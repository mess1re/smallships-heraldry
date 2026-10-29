package me.mss1r.smallshipsheraldry.client.network;

import dev.architectury.networking.NetworkManager;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryTableScreen;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import me.mss1r.smallshipsheraldry.network.HeraldryPackets;
import me.mss1r.smallshipsheraldry.network.HeraldryPatternTransfer;
import net.minecraft.core.BlockPos;

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
            ShipPreviewType activeType = ShipPreviewType.byName(buf.readUtf(64));
            ShipPreviewType draftType = ShipPreviewType.byName(buf.readUtf(64));
            boolean first = buf.readBoolean();
            boolean last = buf.readBoolean();
            byte[] encoded = buf.readByteArray(HeraldryPatternTransfer.MAX_COMPRESSED_BYTES);
            context.queue(() -> HeraldryPatternTransfer.decode(encoded).ifPresent(
                    patternTag -> HeraldryTableScreen.handleDraftSync(
                            blockPos,
                            activeType,
                            draftType,
                            patternTag,
                            first,
                            last
                    )
            ));
        });
    }
}
