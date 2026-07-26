package me.mss1r.smallshipsheraldry.client.network;

import me.mss1r.smallshipsheraldry.client.gui.HeraldryTableScreen;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import me.mss1r.smallshipsheraldry.network.HeraldryPatternTransfer;
import me.mss1r.smallshipsheraldry.network.TableDraftSyncPayload;

public final class HeraldryClientPackets {
    private HeraldryClientPackets() {
    }

    public static void handleDraftSync(TableDraftSyncPayload payload) {
        HeraldryPatternTransfer.decode(payload.encodedPattern()).ifPresent(
                patternTag -> HeraldryTableScreen.handleDraftSync(
                        payload.blockPos(),
                        ShipPreviewType.byName(payload.activeShipType()),
                        ShipPreviewType.byName(payload.draftShipType()),
                        patternTag,
                        payload.first(),
                        payload.last()
                )
        );
    }
}
