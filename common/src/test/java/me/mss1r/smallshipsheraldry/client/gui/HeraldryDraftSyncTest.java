package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.HeraldryTableDrafts;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import me.mss1r.smallshipsheraldry.network.ExportPatternPayload;
import me.mss1r.smallshipsheraldry.network.HeraldryPatternTransfer;
import me.mss1r.smallshipsheraldry.network.TableDraftRequestPayload;
import me.mss1r.smallshipsheraldry.network.TableDraftUpdatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeraldryDraftSyncTest {
    private final HeraldryEditorSession session = new HeraldryEditorSession();
    private final List<CustomPacketPayload> packets = new ArrayList<>();
    private final List<Component> messages = new ArrayList<>();

    private HeraldryDraftSync sync() {
        session.initialize(HeraldryTableDrafts.blank());
        return new HeraldryDraftSync(BlockPos.ZERO, session, packets::add, messages::add);
    }

    @Test
    void failedEncodingKeepsPendingSaveWithBackoffAndCanBeRetried() {
        sync();
        boolean[] fail = {true};
        HeraldryDraftSync sync = new HeraldryDraftSync(BlockPos.ZERO, session, packets::add, messages::add, tag -> {
            if (fail[0]) throw new IOException("test encoding failure");
            return HeraldryPatternTransfer.encode(tag);
        });
        session.markDirty();
        assertFalse(sync.save());
        assertTrue(session.saveDue(Long.MAX_VALUE));
        assertFalse(session.saveDue(System.currentTimeMillis()));
        assertTrue(packets.isEmpty());
        assertEquals(1, messages.size());
        fail[0] = false;
        assertTrue(sync.save());
        assertFalse(session.saveDue(Long.MAX_VALUE));
        assertInstanceOf(TableDraftUpdatePayload.class, packets.getFirst());
    }

    @Test
    void closingImmediatelySendsLatestPixelsBeforeClosingMenu() {
        HeraldryDraftSync sync = sync();
        session.pixels()[7] = 0xFF123456;
        session.markDirty();
        assertFalse(session.saveDue(System.currentTimeMillis()));
        boolean[] closed = {false};
        sync.saveBeforeClose(() -> {
            assertFalse(packets.isEmpty());
            TableDraftUpdatePayload packet = assertInstanceOf(TableDraftUpdatePayload.class, packets.getFirst());
            assertEquals(1, packet.chunkCount());
            var decoded = HeraldryPatternTransfer.decode(packet.chunk()).orElseThrow();
            assertEquals(0xFF123456, HeraldryPatternData.fromRootTag(decoded).toDenseMeshPixels()[7]);
            assertFalse(session.saveDue(Long.MAX_VALUE));
            closed[0] = true;
        });
        assertTrue(closed[0]);
    }

    @Test
    void allDraftChunksAreSentBeforeClosingMenu() {
        sync();
        var sync = new HeraldryDraftSync(BlockPos.ZERO, session, packets::add, messages::add,
                tag -> new byte[HeraldryPatternTransfer.CHUNK_BYTES * 2 + 7]);
        sync.saveBeforeClose(() -> assertEquals(3, packets.size()));
        assertEquals(3, packets.size());
    }

    @Test
    void failedSaveKeepsMenuOpenAndCanBeRetried() {
        sync();
        boolean[] fail = {true};
        var sync = new HeraldryDraftSync(BlockPos.ZERO, session, packets::add, messages::add, tag -> {
            if (fail[0]) throw new IOException("test encoding failure");
            return HeraldryPatternTransfer.encode(tag);
        });
        session.markDirty();
        boolean[] closed = {false};
        sync.saveBeforeClose(() -> closed[0] = true);
        assertFalse(closed[0]);
        assertTrue(session.saveDue(Long.MAX_VALUE));
        assertTrue(packets.isEmpty());
        assertEquals(1, messages.size());
        fail[0] = false;
        sync.saveBeforeClose(() -> closed[0] = true);
        assertTrue(closed[0]);
        assertFalse(session.saveDue(Long.MAX_VALUE));
    }

    @Test
    void closingBeforeInitialReplyCannotOverwriteServerDraftWithBlankPattern() {
        HeraldryDraftSync sync = sync();
        sync.request();
        assertTrue(sync.isLoading());
        int sent = packets.size();
        boolean[] closed = {false};
        sync.saveBeforeClose(() -> closed[0] = true);
        assertTrue(closed[0]);
        assertEquals(sent, packets.size());
    }

    @Test
    void printingWaitsForTheCompleteInitialReply() {
        HeraldryDraftSync sync = sync();
        sync.request();
        assertFalse(sync.transfer(true, session.currentPattern()));
        var tag = HeraldryPatternData.blank().toTag();
        assertFalse(sync.receive(BlockPos.ZERO, ShipPreviewType.COG, ShipPreviewType.COG, tag, true, false));
        assertTrue(sync.isLoading());
        assertFalse(sync.transfer(true, session.currentPattern()));
        assertTrue(sync.receive(BlockPos.ZERO, ShipPreviewType.COG, ShipPreviewType.COG, tag, false, true));
        assertFalse(sync.isLoading());
        assertTrue(sync.transfer(true, session.currentPattern()));
    }

    @Test
    void pendingEditsAreNotMarkedSavedWhileInitialReplyIsStillLoading() {
        HeraldryDraftSync sync = sync();
        sync.request();
        session.markDirty();
        assertFalse(sync.save());
        assertTrue(session.hasPendingSave());
        boolean[] closed = {false};
        sync.saveBeforeClose(() -> closed[0] = true);
        assertFalse(closed[0]);
        sync.receive(BlockPos.ZERO, ShipPreviewType.COG, ShipPreviewType.COG,
                HeraldryPatternData.blank().toTag(), true, true);
        sync.saveBeforeClose(() -> closed[0] = true);
        assertTrue(closed[0]);
        assertFalse(session.hasPendingSave());
    }

    @Test
    void requestIsSentOnlyOnceAcrossScreenReinitializations() {
        HeraldryDraftSync sync = sync();
        sync.request();
        sync.request();
        assertEquals(1, packets.size());
        assertInstanceOf(TableDraftRequestPayload.class, packets.getFirst());
    }

    @Test
    void failedPrintDoesNotCreateAnUnrequestedBackgroundSave() {
        sync();
        var sync = new HeraldryDraftSync(BlockPos.ZERO, session, packets::add, messages::add,
                tag -> { throw new IOException("test failure"); });
        assertFalse(sync.transfer(true, session.currentPattern()));
        assertFalse(session.saveDue(Long.MAX_VALUE));
    }

    @Test
    void printingUsesExportPacketsAndPreservesPatternData() throws IOException {
        HeraldryDraftSync sync = sync();
        session.pixels()[7] = 0xFF123456;
        assertTrue(sync.transfer(true, session.currentPattern()));
        ExportPatternPayload packet = assertInstanceOf(ExportPatternPayload.class, packets.getFirst());
        var decoded = HeraldryPatternTransfer.decode(packet.chunk());
        assertEquals(0xFF123456, HeraldryPatternData.fromRootTag(decoded.orElseThrow()).toDenseMeshPixels()[7]);
    }

    @Test
    void syncAppliesOnlyWhenCompleteAndIgnoresOtherTables() {
        HeraldryDraftSync sync = sync();
        HeraldryPatternData brigg = HeraldryPatternData.blank().withShipType(ShipPreviewType.BRIGG).withBaseColor(0xFF123456);
        assertFalse(sync.receive(new BlockPos(1, 0, 0), ShipPreviewType.BRIGG, ShipPreviewType.BRIGG, brigg.toTag(), true, true));
        assertFalse(sync.receive(BlockPos.ZERO, ShipPreviewType.BRIGG, ShipPreviewType.BRIGG, brigg.toTag(), true, false));
        assertEquals(ShipPreviewType.COG, session.shipType());
        assertTrue(sync.receive(BlockPos.ZERO, ShipPreviewType.BRIGG, ShipPreviewType.COG, HeraldryPatternData.blank().toTag(), false, true));
        assertEquals(ShipPreviewType.BRIGG, session.shipType());
        assertEquals(0xFF123456, session.pattern().baseColorArgb());
    }

    @Test
    void lateServerReplyCannotOverwriteLocalEdits() {
        HeraldryDraftSync sync = sync();
        session.pixels()[4] = 0xFFABCDEF;
        session.markLocallyModified();
        assertFalse(sync.receive(BlockPos.ZERO, ShipPreviewType.COG, ShipPreviewType.COG,
                HeraldryPatternData.blank().toTag(), true, true));
        assertEquals(0xFFABCDEF, session.pixels()[4]);
    }

    @Test
    void orphanedOrRepeatedLastPacketCannotReapplyOldDrafts() {
        HeraldryDraftSync sync = sync();
        var tag = HeraldryPatternData.blank().toTag();
        assertFalse(sync.receive(BlockPos.ZERO, ShipPreviewType.COG, ShipPreviewType.COG, tag, false, true));
        assertTrue(sync.receive(BlockPos.ZERO, ShipPreviewType.COG, ShipPreviewType.COG, tag, true, true));
        assertFalse(sync.receive(BlockPos.ZERO, ShipPreviewType.COG, ShipPreviewType.COG, tag, false, true));
    }
}
