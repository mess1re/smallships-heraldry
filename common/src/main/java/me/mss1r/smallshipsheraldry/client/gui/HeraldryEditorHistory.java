package me.mss1r.smallshipsheraldry.client.gui;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class HeraldryEditorHistory {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    private final Map<ShipPreviewType, History> histories = new HashMap<>();

    boolean canUndo(ShipPreviewType shipType) {
        return !history(shipType).undo.isEmpty();
    }

    boolean canRedo(ShipPreviewType shipType) {
        return !history(shipType).redo.isEmpty();
    }

    void push(ShipPreviewType shipType, HeraldryPatternData snapshot) {
        History history = history(shipType);
        history.undo.push(snapshot);
        history.undoBytes += snapshot.estimatedMemoryBytes();
        history.redo.clear();
        history.redoBytes = 0;
        trim(history.undo, true, history);
    }

    void rememberImportedOver(HeraldryPatternData snapshot) {
        push(snapshot.shipType(), snapshot);
    }

    void discardLatestUndo(ShipPreviewType shipType) {
        History history = history(shipType);
        if (!history.undo.isEmpty()) {
            history.undoBytes -= history.undo.pop().estimatedMemoryBytes();
        }
    }

    Optional<HeraldryPatternData> takeLatestUndo(ShipPreviewType shipType) {
        History history = history(shipType);
        if (history.undo.isEmpty()) {
            return Optional.empty();
        }
        HeraldryPatternData restored = history.undo.pop();
        history.undoBytes -= restored.estimatedMemoryBytes();
        return Optional.of(restored);
    }

    Optional<HeraldryPatternData> undo(ShipPreviewType shipType, HeraldryPatternData current) {
        History history = history(shipType);
        if (history.undo.isEmpty()) {
            return Optional.empty();
        }
        history.redo.push(current);
        history.redoBytes += current.estimatedMemoryBytes();
        trim(history.redo, false, history);
        HeraldryPatternData restored = history.undo.pop();
        history.undoBytes -= restored.estimatedMemoryBytes();
        return Optional.of(restored);
    }

    Optional<HeraldryPatternData> redo(ShipPreviewType shipType, HeraldryPatternData current) {
        History history = history(shipType);
        if (history.redo.isEmpty()) {
            return Optional.empty();
        }
        history.undo.push(current);
        history.undoBytes += current.estimatedMemoryBytes();
        trim(history.undo, true, history);
        HeraldryPatternData restored = history.redo.pop();
        history.redoBytes -= restored.estimatedMemoryBytes();
        return Optional.of(restored);
    }

    void clear() {
        histories.clear();
    }

    private History history(ShipPreviewType shipType) {
        return histories.computeIfAbsent(shipType, ignored -> new History());
    }

    private static void trim(Deque<HeraldryPatternData> entries, boolean undo, History history) {
        int bytes = undo ? history.undoBytes : history.redoBytes;
        while (entries.size() > MAX_ENTRIES || bytes > MAX_BYTES) {
            bytes -= entries.removeLast().estimatedMemoryBytes();
        }
        if (undo) {
            history.undoBytes = bytes;
        } else {
            history.redoBytes = bytes;
        }
    }

    private static final class History {
        private final Deque<HeraldryPatternData> undo = new ArrayDeque<>();
        private final Deque<HeraldryPatternData> redo = new ArrayDeque<>();
        private int undoBytes;
        private int redoBytes;
    }
}
