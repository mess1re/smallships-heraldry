package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.HeraldryTableDrafts;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import me.mss1r.smallshipsheraldry.network.HeraldryPackets;
import me.mss1r.smallshipsheraldry.network.HeraldryPatternTransfer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** Owns table draft transfers; widget and preview updates stay with the screen. */
final class HeraldryDraftSync {
    private final BlockPos blockPos;
    private final HeraldryEditorSession session;
    private final BiConsumer<ResourceLocation, FriendlyByteBuf> send;
    private final Consumer<Component> status;
    private final Encoder encoder;
    private boolean requested;
    private boolean initialSyncComplete;
    private int nextTransferId;
    private HeraldryTableDrafts incoming;

    HeraldryDraftSync(BlockPos blockPos, HeraldryEditorSession session,
                     BiConsumer<ResourceLocation, FriendlyByteBuf> send, Consumer<Component> status) {
        this(blockPos, session, send, status, HeraldryPatternTransfer::encode);
    }

    HeraldryDraftSync(BlockPos blockPos, HeraldryEditorSession session,
                     BiConsumer<ResourceLocation, FriendlyByteBuf> send, Consumer<Component> status, Encoder encoder) {
        this.blockPos = blockPos.immutable();
        this.session = session;
        this.send = send;
        this.status = status;
        this.encoder = encoder;
    }

    void request() {
        if (!requested) {
            requested = true;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(blockPos);
            send.accept(HeraldryPackets.TABLE_DRAFT_REQUEST, buf);
        }
    }

    boolean save() {
        if (isLoading()) {
            return !session.hasPendingSave();
        }
        try {
            return transfer(false, session.currentPattern());
        } catch (HeraldryPatternData.PatternTooComplexException exception) {
            failed();
            return false;
        }
    }

    void saveBeforeClose(Runnable closeMenu) {
        if (save()) {
            closeMenu.run();
        }
    }

    boolean isLoading() {
        return requested && !initialSyncComplete;
    }

    boolean transfer(boolean print, HeraldryPatternData data) {
        if (isLoading()) return false;
        byte[] encoded;
        try {
            encoded = encoder.encode(data.toTag());
        } catch (IOException exception) {
            failed();
            return false;
        }
        int transferId = ++nextTransferId;
        int count = HeraldryPatternTransfer.chunkCount(encoded.length);
        for (int index = 0; index < count; index++) {
            byte[] chunk = HeraldryPatternTransfer.chunk(encoded, index);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(blockPos);
            buf.writeVarInt(transferId);
            buf.writeVarInt(index);
            buf.writeVarInt(count);
            buf.writeVarInt(encoded.length);
            buf.writeByteArray(chunk);
            send.accept(print ? HeraldryPackets.EXPORT_PATTERN : HeraldryPackets.TABLE_DRAFT_UPDATE, buf);
        }
        session.markSaved();
        return true;
    }

    boolean receive(BlockPos position, ShipPreviewType activeType, ShipPreviewType draftType,
                    CompoundTag patternTag, boolean first, boolean last) {
        if (!blockPos.equals(position) || patternTag == null) return false;
        if (first) incoming = HeraldryTableDrafts.blank().withActiveShipType(activeType);
        if (incoming == null) return false;
        HeraldryPatternData.tryFromRootTag(patternTag)
                .filter(data -> data.shipType() == draftType)
                .ifPresent(data -> incoming = incoming.withDraft(data).withActiveShipType(activeType));
        if (!last) return false;
        HeraldryTableDrafts completed = incoming;
        incoming = null;
        initialSyncComplete = true;
        return session.applyServerSync(completed);
    }

    void slotAction(int slot, int action) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(blockPos);
        buf.writeVarInt(slot);
        buf.writeVarInt(action);
        send.accept(HeraldryPackets.TABLE_SLOT_ACTION, buf);
    }

    private void failed() {
        session.deferSaveRetry();
        status.accept(Component.translatable("smallshipsheraldry.message.pattern_too_complex"));
    }

    @FunctionalInterface
    interface Encoder {
        byte[] encode(CompoundTag tag) throws IOException;
    }
}
