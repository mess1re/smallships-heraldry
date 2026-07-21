package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraldryEditorHistoryTest {
    @Test
    void keepsUndoAndRedoIndependentPerShip() {
        HeraldryEditorHistory history = new HeraldryEditorHistory();
        HeraldryPatternData cogBefore = pattern(ShipPreviewType.COG, 0xFF112233);
        HeraldryPatternData cogAfter = pattern(ShipPreviewType.COG, 0xFF445566);
        HeraldryPatternData briggBefore = pattern(ShipPreviewType.BRIGG, 0xFF778899);

        history.push(ShipPreviewType.COG, cogBefore);
        history.push(ShipPreviewType.BRIGG, briggBefore);

        assertEquals(cogBefore.visualHash(), history.undo(ShipPreviewType.COG, cogAfter).orElseThrow().visualHash());
        assertTrue(history.canRedo(ShipPreviewType.COG));
        assertTrue(history.canUndo(ShipPreviewType.BRIGG));
        assertEquals(cogAfter.visualHash(), history.redo(ShipPreviewType.COG, cogBefore).orElseThrow().visualHash());
    }

    @Test
    void canRestoreFailedEditWithoutCreatingRedoEntry() {
        HeraldryEditorHistory history = new HeraldryEditorHistory();
        HeraldryPatternData snapshot = pattern(ShipPreviewType.GALLEY, 0xFFABCDEF);

        history.push(ShipPreviewType.GALLEY, snapshot);

        assertEquals(snapshot.visualHash(), history.takeLatestUndo(ShipPreviewType.GALLEY).orElseThrow().visualHash());
        assertFalse(history.canUndo(ShipPreviewType.GALLEY));
        assertFalse(history.canRedo(ShipPreviewType.GALLEY));
    }

    private static HeraldryPatternData pattern(ShipPreviewType shipType, int baseColor) {
        return HeraldryPatternData.blank().withShipType(shipType).withBaseColor(baseColor);
    }
}
