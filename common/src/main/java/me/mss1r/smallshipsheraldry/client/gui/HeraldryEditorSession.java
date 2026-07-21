package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.HeraldryTableDrafts;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;

import java.util.Optional;

final class HeraldryEditorSession {
    private static final long SAVE_DELAY_MILLIS = 750L;

    private final HeraldryEditorHistory history = new HeraldryEditorHistory();
    private HeraldryTableDrafts drafts = HeraldryTableDrafts.blank();
    private HeraldryPatternData pattern = HeraldryPatternData.blank();
    private int[] pixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
    private ShipPreviewType shipType = ShipPreviewType.COG;
    private boolean initialized;
    private boolean locallyModified;
    private boolean savePending;
    private long saveAt;

    void initialize(HeraldryTableDrafts initialDrafts) {
        if (initialized) {
            return;
        }
        apply(initialDrafts == null ? HeraldryTableDrafts.blank() : initialDrafts);
        initialized = true;
    }

    boolean applyServerSync(HeraldryTableDrafts serverDrafts) {
        if (serverDrafts == null || locallyModified) {
            return false;
        }
        apply(serverDrafts);
        history.clear();
        initialized = true;
        return true;
    }

    boolean switchShip(ShipPreviewType newShipType) {
        if (newShipType == shipType) {
            return false;
        }
        drafts = drafts.withActiveShipType(newShipType);
        shipType = newShipType;
        pattern = drafts.get(shipType);
        pixels = pattern.toDenseMeshPixels();
        markDirty();
        return true;
    }

    void importPattern(HeraldryPatternData imported) {
        HeraldryPatternData replaced = drafts.get(imported.shipType());
        history.rememberImportedOver(replaced);
        shipType = imported.shipType();
        pattern = imported;
        drafts = drafts.withDraft(imported).withActiveShipType(shipType);
        pixels = imported.toDenseMeshPixels();
        markDirty();
    }

    HeraldryPatternData currentPattern() throws HeraldryPatternData.PatternTooComplexException {
        pattern = HeraldryPatternData.fromMeshPixels(
                pattern.author(),
                shipType,
                pattern.baseColorArgb(),
                pixels
        );
        drafts = drafts.withDraft(pattern);
        return pattern;
    }

    void setBaseColor(int argb) {
        pattern = pattern.withBaseColor(argb);
    }

    boolean pushUndo() throws HeraldryPatternData.PatternTooComplexException {
        history.push(shipType, currentPattern());
        return true;
    }

    Optional<Restore> undo() throws HeraldryPatternData.PatternTooComplexException {
        if (!history.canUndo(shipType)) {
            return Optional.empty();
        }
        return history.undo(shipType, currentPattern()).map(this::restore);
    }

    Optional<Restore> redo() throws HeraldryPatternData.PatternTooComplexException {
        if (!history.canRedo(shipType)) {
            return Optional.empty();
        }
        return history.redo(shipType, currentPattern()).map(this::restore);
    }

    Optional<Restore> restoreFailedEdit() {
        if (!history.canUndo(shipType)) {
            return Optional.empty();
        }
        return history.takeLatestUndo(shipType).map(this::restore);
    }

    void discardLatestUndo() {
        history.discardLatestUndo(shipType);
    }

    void markDirty() {
        locallyModified = true;
        savePending = true;
        saveAt = System.currentTimeMillis() + SAVE_DELAY_MILLIS;
    }

    void markLocallyModified() {
        locallyModified = true;
    }

    boolean saveDue(long now) {
        return savePending && now >= saveAt;
    }

    void markSaved() {
        savePending = false;
    }

    HeraldryPatternData pattern() {
        return pattern;
    }

    HeraldryTableDrafts drafts() {
        return drafts;
    }

    int[] pixels() {
        return pixels;
    }

    ShipPreviewType shipType() {
        return shipType;
    }

    boolean hasDraftWork(ShipPreviewType type) {
        HeraldryPatternData draft = drafts.get(type);
        return draft.paintedPixelCount() > 0 || draft.baseColorArgb() != 0xFFFFFFFF;
    }

    private void apply(HeraldryTableDrafts loadedDrafts) {
        drafts = loadedDrafts;
        shipType = drafts.activeShipType();
        pattern = drafts.get(shipType);
        pixels = pattern.toDenseMeshPixels();
    }

    private Restore restore(HeraldryPatternData snapshot) {
        ShipPreviewType previousType = shipType;
        pattern = snapshot;
        shipType = snapshot.shipType();
        drafts = drafts.withDraft(snapshot).withActiveShipType(shipType);
        pixels = snapshot.toDenseMeshPixels();
        markDirty();
        return new Restore(previousType != shipType);
    }

    record Restore(boolean shipChanged) {
    }
}
