package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.HeraldryTableDrafts;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraldryEditorSessionTest {
    @Test
    void switchingShipsKeepsIndependentPixelBuffers() {
        HeraldryEditorSession session = new HeraldryEditorSession();
        session.initialize(HeraldryTableDrafts.blank());
        session.pixels()[3] = 0xFF123456;
        session.currentPattern();

        assertTrue(session.switchShip(ShipPreviewType.BRIGG));
        assertEquals(0, session.pixels()[3]);
        session.pixels()[7] = 0xFFABCDEF;
        session.currentPattern();

        assertTrue(session.switchShip(ShipPreviewType.COG));
        assertEquals(0xFF123456, session.pixels()[3]);
        assertEquals(0, session.pixels()[7]);
    }

    @Test
    void localWorkIsNotOverwrittenByLateServerSync() {
        HeraldryEditorSession session = new HeraldryEditorSession();
        session.initialize(HeraldryTableDrafts.blank());
        session.pixels()[5] = 0xFF445566;
        session.markLocallyModified();

        HeraldryPatternData replacement = pattern(ShipPreviewType.COG, 0xFF998877);
        boolean applied = session.applyServerSync(HeraldryTableDrafts.blank().withDraft(replacement));

        assertFalse(applied);
        assertEquals(0xFF445566, session.pixels()[5]);
    }

    @Test
    void undoRestoresPatternAndSchedulesSave() {
        HeraldryEditorSession session = new HeraldryEditorSession();
        session.initialize(HeraldryTableDrafts.blank());
        session.pixels()[9] = 0xFF112233;
        session.pushUndo();
        session.pixels()[9] = 0xFF778899;

        HeraldryEditorSession.Restore restore = session.undo().orElseThrow();

        assertFalse(restore.shipChanged());
        assertEquals(0xFF112233, session.pixels()[9]);
        assertTrue(session.saveDue(Long.MAX_VALUE));
    }

    private static HeraldryPatternData pattern(ShipPreviewType type, int color) {
        int[] pixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        pixels[0] = color;
        return HeraldryPatternData.fromMeshPixels("tester", type, 0xFFFFFFFF, pixels);
    }
}
