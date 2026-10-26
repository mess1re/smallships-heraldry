package me.mss1r.smallshipsheraldry.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import me.mss1r.smallshipsheraldry.client.network.HeraldryClientPackets;
import me.mss1r.smallshipsheraldry.world.inventory.HeraldryTableMenu;
import net.minecraft.server.level.ServerPlayer;

public final class HeraldryPackets {
    private HeraldryPackets() {
    }

    public static void register() {
        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                ExportPatternPayload.TYPE,
                ExportPatternPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer serverPlayer
                                && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                            menu.handleExportChunk(
                                    serverPlayer,
                                    payload.blockPos(),
                                    payload.transferId(),
                                    payload.chunkIndex(),
                                    payload.chunkCount(),
                                    payload.totalBytes(),
                                    payload.chunk()
                            );
                        }
                    });
                }
        );
        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                TableSlotActionPayload.TYPE,
                TableSlotActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer serverPlayer
                                && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                            menu.handleSlotAction(serverPlayer, payload.blockPos(), payload.slot(), payload.action());
                        }
                    });
                }
        );
        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                TableDraftUpdatePayload.TYPE,
                TableDraftUpdatePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer serverPlayer
                                && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                            menu.handleDraftChunk(
                                    serverPlayer,
                                    payload.blockPos(),
                                    payload.transferId(),
                                    payload.chunkIndex(),
                                    payload.chunkCount(),
                                    payload.totalBytes(),
                                    payload.chunk()
                            );
                        }
                    });
                }
        );
        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                TableDraftRequestPayload.TYPE,
                TableDraftRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer serverPlayer
                                && serverPlayer.containerMenu instanceof HeraldryTableMenu menu) {
                            menu.handleDraftRequest(serverPlayer, payload.blockPos());
                        }
                    });
                }
        );
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(
                    NetworkManager.s2c(),
                    TableDraftSyncPayload.TYPE,
                    TableDraftSyncPayload.STREAM_CODEC,
                    (payload, context) -> EnvExecutor.runInEnv(
                            Env.CLIENT,
                            () -> () -> context.queue(() -> HeraldryClientPackets.handleDraftSync(payload))
                    )
            );
        } else {
            NetworkManager.registerS2CPayloadType(TableDraftSyncPayload.TYPE, TableDraftSyncPayload.STREAM_CODEC);
        }
    }
}
