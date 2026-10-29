package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HeraldryElementSelectionTest {
    private final HeraldryElementSelection parts = new HeraldryElementSelection((type, stage) -> List.of(
            element("rope/z"), element("sail/c"), element("sail/a"), element("sail/b")));

    private static PaintableSailMesh.Element element(String id) { return new PaintableSailMesh.Element(id, id); }

    @Test
    void rangeSelectionUsesSortedRowsAndPreservesAnchor() {
        parts.switchContext(ShipPreviewType.COG, 0);
        parts.selectRow(1, false, false);
        parts.selectRow(3, true, false);
        assertEquals(Set.of("sail/b", "sail/c", "rope/z"), parts.selected());
        parts.selectRow(0, true, false);
        assertEquals(Set.of("sail/a", "sail/b"), parts.selected());
    }

    @Test
    void additiveSelectionCanToggleAndExtendRanges() {
        parts.switchContext(ShipPreviewType.COG, 0);
        parts.selectRow(0, false, false);
        parts.selectRow(2, false, true);
        parts.selectRow(3, true, true);
        assertEquals(Set.of("sail/a", "sail/c", "rope/z"), parts.selected());
        parts.selectRow(0, false, true);
        assertEquals(Set.of("sail/c", "rope/z"), parts.selected());
    }

    @Test
    void shipsAndStagesKeepIndependentFilters() {
        parts.switchContext(ShipPreviewType.COG, 0);
        parts.selectRow(0, false, false);
        parts.hideSelected();
        parts.switchContext(ShipPreviewType.COG, 1);
        assertTrue(parts.selected().isEmpty());
        assertTrue(parts.hidden().isEmpty());
        parts.selectAll();
        parts.switchContext(ShipPreviewType.BRIGG, 0);
        assertTrue(parts.selected().isEmpty());
        parts.switchContext(ShipPreviewType.COG, 0);
        assertEquals(Set.of("sail/a"), parts.selected());
        assertEquals(parts.selected(), parts.hidden());
    }

    @Test
    void isolateTogglesVisibilityWithoutChangingSelection() {
        parts.switchContext(ShipPreviewType.COG, 0);
        parts.selectRow(1, false, false);
        parts.toggleIsolate();
        assertTrue(parts.isIsolated());
        assertEquals(Set.of("sail/a", "sail/c", "rope/z"), parts.hidden());
        parts.toggleIsolate();
        assertTrue(parts.hidden().isEmpty());
        assertEquals(Set.of("sail/b"), parts.selected());
    }

    @Test
    void scrollIsClampedAndPreviewSelectionBringsRowIntoView() {
        parts.switchContext(ShipPreviewType.COG, 0);
        parts.scrollBy(100, 2);
        assertEquals(2, parts.scroll());
        parts.selectHit("sail/a", false, false, 2);
        assertEquals(0, parts.scroll());
        parts.selectHit("rope/z", false, false, 2);
        assertEquals(2, parts.scroll());
    }

    @Test
    void externalCodeCannotModifyFiltersAndServerResetClearsContexts() {
        parts.switchContext(ShipPreviewType.COG, 0);
        parts.selectAll();
        assertThrows(UnsupportedOperationException.class, () -> parts.selected().clear());
        parts.clearContexts();
        parts.switchContext(ShipPreviewType.COG, 0);
        assertTrue(parts.selected().isEmpty());
    }
}
