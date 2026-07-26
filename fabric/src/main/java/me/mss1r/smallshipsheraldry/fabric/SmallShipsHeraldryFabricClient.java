package me.mss1r.smallshipsheraldry.fabric;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldryClient;
import me.mss1r.smallshipsheraldry.client.network.HeraldryClientPackets;
import me.mss1r.smallshipsheraldry.network.TableDraftSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SmallShipsHeraldryFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SmallShipsHeraldryClient.init();
        SmallShipsHeraldryClient.registerScreen();
        ClientPlayNetworking.registerGlobalReceiver(
                TableDraftSyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> HeraldryClientPackets.handleDraftSync(payload)
                )
        );
    }
}
