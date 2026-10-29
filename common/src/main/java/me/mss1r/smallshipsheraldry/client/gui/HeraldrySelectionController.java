package me.mss1r.smallshipsheraldry.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import me.mss1r.smallshipsheraldry.client.render.GuiScissor;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class HeraldrySelectionController {
    private final HeraldryEditorSession session;
    private final HeraldryEditorSurface surface;
    private final HeraldryEditorEdits edits;
    private boolean selectingPixels;
    private boolean pixelSelectionActive;
    private PaintableSailMesh.Hit selectionStartHit;
    private PaintableSailMesh.Hit selectionEndHit;
    private List<PaintableSailMesh.ScreenPixel> surfaceSelection = List.of();
    private SelectionClipboard clipboard;
    private boolean pasteActive;
    private boolean movingPaste;
    private PaintableSailMesh.Hit pasteTargetHit;
    private Map<Integer, Integer> pasteReplacements = Map.of();
    private final Set<Integer> pastePreviewIndices = new LinkedHashSet<>();

    HeraldrySelectionController(HeraldryEditorSession session, HeraldryEditorSurface surface, HeraldryEditorEdits edits) {
        this.session = session;
        this.surface = surface;
        this.edits = edits;
    }

    boolean hasSelection() { return pixelSelectionActive && !surfaceSelection.isEmpty(); }
    boolean hasClipboard() { return clipboard != null; }
    boolean isPasting() { return pasteActive; }
    boolean hasPasteTarget() { return pasteActive && !pasteReplacements.isEmpty(); }

    void release() {
        selectingPixels = false;
        movingPaste = false;
    }

    boolean drag(double x, double y) {
        if (selectingPixels) { updatePixelSelection(x, y); return true; }
        if (movingPaste) { movePaste(x, y); return true; }
        return false;
    }

    Component summary() {
        SelectionRect rect = pixelSelection();
        return pasteActive && clipboard != null
                ? Component.translatable("smallshipsheraldry.selection.size", clipboard.width(), clipboard.height())
                : rect == null ? Component.translatable("smallshipsheraldry.selection.none")
                : Component.translatable("smallshipsheraldry.selection.size", rect.width(), rect.height());
    }

    boolean beginPixelSelection(double mouseX, double mouseY) {
        if (pasteActive && clipboard != null) {
            movingPaste = true;
            updatePasteTarget(mouseX, mouseY);
            return true;
        }

        Optional<PaintableSailMesh.Hit> sampled = surface.pick(mouseX, mouseY);
        if (sampled.isEmpty() || !surface.accepts(sampled.get())) {
            clearPixelSelectionOverlay();
            return true;
        }
        pixelSelectionActive = true;
        selectingPixels = true;
        selectionStartHit = sampled.get();
        selectionEndHit = sampled.get();
        refreshSurfaceSelection();
        return true;
    }

    private void updatePixelSelection(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> sampled = surface.pick(mouseX, mouseY);
        if (sampled.isEmpty() || !surface.accepts(sampled.get())) {
            return;
        }
        Optional<PaintableSailMesh.SurfacePoint> startPoint =
                PaintableSailMesh.surfacePoint(surface.ship(), surface.stage(), selectionStartHit);
        Optional<PaintableSailMesh.SurfacePoint> endPoint =
                PaintableSailMesh.surfacePoint(surface.ship(), surface.stage(), sampled.get());
        if (startPoint.isEmpty() || endPoint.isEmpty()
                || startPoint.get().componentId() != endPoint.get().componentId()) {
            return;
        }
        selectionEndHit = sampled.get();
        refreshSurfaceSelection();
    }

    private void refreshSurfaceSelection() {
        if (!pixelSelectionActive || selectionStartHit == null || selectionEndHit == null) {
            surfaceSelection = List.of();
            return;
        }
        surfaceSelection = PaintableSailMesh.pixelsInSurfaceRect(
                surface.ship(),
                surface.stage(),
                selectionStartHit,
                selectionEndHit,
                surface.selectedElements(),
                surface.hiddenElements()
        );
    }

    void copyPixelSelection(boolean cut) {
        if (!pixelSelectionActive || surfaceSelection.isEmpty()) {
            edits.status(Component.translatable("smallshipsheraldry.selection.select_first"));
            return;
        }

        Map<Integer, CapturedPixel> capturedPixels = new HashMap<>();
        Set<Integer> clearIndices = new LinkedHashSet<>();
        int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
        for (PaintableSailMesh.ScreenPixel selectedPixel : surfaceSelection) {
            PaintableSailMesh.Hit hit = selectedPixel.hit();
            PaintableSailMesh.ScreenPixel projected = PaintableSailMesh.projectPixel(surface.ship(), hit).orElse(selectedPixel);
            int atlasIndex = hit.pixelY() * atlasWidth + hit.pixelX();
            int color = session.pixels()[atlasIndex];
            capturedPixels.putIfAbsent(atlasIndex, new CapturedPixel(
                    hit,
                    projected.screenX(),
                    projected.screenY(),
                    color
            ));
            if (cut && color != 0) {
                clearIndices.add(atlasIndex);
            }
        }
        if (capturedPixels.isEmpty()) {
            edits.status(Component.translatable("smallshipsheraldry.selection.empty"));
            return;
        }

        double selectionCenterX = capturedPixels.values().stream()
                .mapToDouble(CapturedPixel::screenX)
                .average()
                .orElse(surface.bounds().x() + surface.bounds().width() * 0.5D);
        double selectionCenterY = capturedPixels.values().stream()
                .mapToDouble(CapturedPixel::screenY)
                .average()
                .orElse(surface.bounds().y() + surface.bounds().height() * 0.5D);
        CapturedPixel anchor = capturedPixels.values().stream()
                .min((first, second) -> Double.compare(
                        distanceSquared(first.screenX(), first.screenY(), selectionCenterX, selectionCenterY),
                        distanceSquared(second.screenX(), second.screenY(), selectionCenterX, selectionCenterY)
                ))
                .orElseThrow();
        Optional<PaintableSailMesh.SurfacePoint> anchorPoint = PaintableSailMesh.surfacePoint(surface.ship(), surface.stage(), anchor.hit());
        if (anchorPoint.isEmpty()) {
            edits.status(Component.translatable("smallshipsheraldry.selection.empty"));
            return;
        }

        List<PaintableSailMesh.ClipboardPixel> geometryPixels = new ArrayList<>(capturedPixels.size());
        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (CapturedPixel captured : capturedPixels.values()) {
            Optional<PaintableSailMesh.SurfacePoint> point = PaintableSailMesh.surfacePoint(surface.ship(), surface.stage(), captured.hit());
            if (point.isEmpty() || point.get().componentId() != anchorPoint.get().componentId()) {
                continue;
            }
            float offsetU = point.get().u() - anchorPoint.get().u();
            float offsetV = point.get().v() - anchorPoint.get().v();
            geometryPixels.add(new PaintableSailMesh.ClipboardPixel(
                    offsetU,
                    offsetV,
                    captured.color()
            ));
            minU = Math.min(minU, offsetU);
            minV = Math.min(minV, offsetV);
            maxU = Math.max(maxU, offsetU);
            maxV = Math.max(maxV, offsetV);
        }
        if (geometryPixels.isEmpty()) {
            edits.status(Component.translatable("smallshipsheraldry.selection.empty"));
            return;
        }

        int geometryWidth = Math.max(1, Mth.ceil((maxU - minU) * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT) + 1);
        int geometryHeight = Math.max(1, Mth.ceil((maxV - minV) * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT) + 1);
        closeClipboard();
        clipboard = new SelectionClipboard(
                geometryWidth,
                geometryHeight,
                List.copyOf(geometryPixels),
                Mth.clamp(anchor.screenX() - surface.bounds().x(), 0, surface.bounds().width() - 1),
                Mth.clamp(anchor.screenY() - surface.bounds().y(), 0, surface.bounds().height() - 1)
        );

        if (cut && !clearIndices.isEmpty() && edits.pushUndo()) {
            for (int atlasIndex : clearIndices) {
                session.pixels()[atlasIndex] = 0;
            }
            surface.replacePixels(session.pixels());
            edits.commit();
        }
        edits.status(Component.translatable("smallshipsheraldry.selection.copied", geometryWidth, geometryHeight));
    }

    void beginPaste() {
        if (clipboard == null) {
            edits.status(Component.translatable("smallshipsheraldry.selection.clipboard_empty"));
            return;
        }
        pixelSelectionActive = false;
        selectingPixels = false;
        movingPaste = false;
        pasteActive = true;
        updatePasteTarget(surface.bounds().x() + clipboard.originX(), surface.bounds().y() + clipboard.originY());
    }

    private void movePaste(double mouseX, double mouseY) {
        if (!pasteActive || clipboard == null) {
            movingPaste = false;
            return;
        }
        updatePasteTarget(mouseX, mouseY);
    }

    void movePasteBy(int deltaX, int deltaY) {
        if (!pasteActive || clipboard == null || pasteTargetHit == null) {
            return;
        }
        float pixelScale = PaintableSailMesh.projectedPixelScale(surface.ship(), pasteTargetHit);
        Optional<PaintableSailMesh.ScreenPixel> anchor = PaintableSailMesh.projectPixel(surface.ship(), pasteTargetHit);
        if (anchor.isEmpty()) return;
        updatePasteTarget(
                anchor.get().screenX() + deltaX * pixelScale,
                anchor.get().screenY() + deltaY * pixelScale
        );
    }

    private boolean updatePasteTarget(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> sampled = surface.pick(mouseX, mouseY);
        if (sampled.isEmpty() || !surface.accepts(sampled.get()) || clipboard == null) {
            return false;
        }
        pasteTargetHit = sampled.get();
        updatePastePreview(PaintableSailMesh.mapClipboardPixels(
                surface.ship(),
                surface.stage(),
                pasteTargetHit,
                clipboard.pixels(),
                surface.selectedElements(),
                surface.hiddenElements()
        ));
        return true;
    }

    private void updatePastePreview(Map<Integer, Integer> replacements) {
        restorePastePreview();
        pasteReplacements = Map.copyOf(replacements);
        if (surface.texture() == null) {
            return;
        }
        int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
        for (Map.Entry<Integer, Integer> replacement : pasteReplacements.entrySet()) {
            int atlasIndex = replacement.getKey();
            surface.texture().setPixel(atlasIndex % atlasWidth, atlasIndex / atlasWidth, replacement.getValue());
            pastePreviewIndices.add(atlasIndex);
        }
    }

    private void restorePastePreview() {
        if (surface.texture() != null && !pastePreviewIndices.isEmpty()) {
            int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
            for (int atlasIndex : pastePreviewIndices) {
                surface.texture().setPixel(atlasIndex % atlasWidth, atlasIndex / atlasWidth, session.pixels()[atlasIndex]);
            }
        }
        pastePreviewIndices.clear();
    }

    void commitPaste() {
        if (!pasteActive || clipboard == null) {
            return;
        }

        Map<Integer, Integer> replacements = pasteReplacements;
        boolean changed = replacements.entrySet().stream()
                .anyMatch(entry -> session.pixels()[entry.getKey()] != entry.getValue());
        cancelPaste();
        if (!changed) {
            edits.status(Component.translatable(replacements.isEmpty()
                    ? "smallshipsheraldry.selection.no_target"
                    : "smallshipsheraldry.selection.unchanged"));
            return;
        }
        if (!edits.pushUndo()) {
            return;
        }
        replacements.forEach((index, color) -> session.pixels()[index] = color);
        surface.replacePixels(session.pixels());
        edits.commit();
        pixelSelectionActive = false;
        selectionStartHit = null;
        selectionEndHit = null;
        surfaceSelection = List.of();
    }

    void clearPixelSelection() {
        if (pasteActive) {
            cancelPaste();
            return;
        }
        if (!pixelSelectionActive || surfaceSelection.isEmpty()) {
            return;
        }

        Set<Integer> clearIndices = new LinkedHashSet<>();
        int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
        for (PaintableSailMesh.ScreenPixel selectedPixel : surfaceSelection) {
            PaintableSailMesh.Hit hit = selectedPixel.hit();
            int atlasIndex = hit.pixelY() * atlasWidth + hit.pixelX();
            if (session.pixels()[atlasIndex] != 0) {
                clearIndices.add(atlasIndex);
            }
        }
        if (clearIndices.isEmpty() || !edits.pushUndo()) {
            return;
        }
        for (int atlasIndex : clearIndices) {
            session.pixels()[atlasIndex] = 0;
        }
        surface.replacePixels(session.pixels());
        edits.commit();
    }

    private SelectionRect pixelSelection() {
        if (!pixelSelectionActive || surfaceSelection.isEmpty()
                || selectionStartHit == null || selectionEndHit == null) {
            return null;
        }
        Optional<PaintableSailMesh.SurfacePoint> start =
                PaintableSailMesh.surfacePoint(surface.ship(), surface.stage(), selectionStartHit);
        Optional<PaintableSailMesh.SurfacePoint> end =
                PaintableSailMesh.surfacePoint(surface.ship(), surface.stage(), selectionEndHit);
        if (start.isEmpty() || end.isEmpty()
                || start.get().componentId() != end.get().componentId()) {
            return null;
        }
        int width = Math.max(1, Mth.floor(
                Math.abs(end.get().u() - start.get().u())
                        * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT + 0.5F
        ) + 1);
        int height = Math.max(1, Mth.floor(
                Math.abs(end.get().v() - start.get().v())
                        * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT + 0.5F
        ) + 1);
        return new SelectionRect(0, 0, width, height);
    }

    void cancelPaste() {
        restorePastePreview();
        pasteActive = false;
        movingPaste = false;
        pasteTargetHit = null;
        pasteReplacements = Map.of();
    }

    boolean cancelSelection() {
        if (pasteActive) {
            cancelPaste();
            return true;
        }
        if (pixelSelectionActive) {
            clearPixelSelectionOverlay();
            return true;
        }
        return false;
    }

    void clearPixelSelectionOverlay() {
        selectingPixels = false;
        pixelSelectionActive = false;
        selectionStartHit = null;
        selectionEndHit = null;
        surfaceSelection = List.of();
        cancelPaste();
    }

    void closeClipboard() {
        cancelPaste();
        clipboard = null;
    }

    void drawPixelSelection(GuiGraphics guiGraphics) {
        SelectionRect rect = pixelSelection();
        if (rect == null && !pasteActive) {
            return;
        }

        int previewLeft = surface.bounds().x();
        int previewTop = surface.bounds().y();
        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
        GuiScissor.enable(guiGraphics, previewLeft, previewTop, previewLeft + surface.bounds().width(), previewTop + surface.bounds().height());
        try {
            if (pasteActive && pasteTargetHit != null) {
                Optional<PaintableSailMesh.ScreenPixel> anchor = PaintableSailMesh.projectPixel(surface.ship(), pasteTargetHit);
                if (anchor.isEmpty() || !isVisible(anchor.get())) return;
                int markerX = Mth.floor(anchor.get().screenX());
                int markerY = Mth.floor(anchor.get().screenY());
                guiGraphics.fill(markerX - 4, markerY, markerX + 5, markerY + 1, HeraldryGuiTheme.HIGHLIGHT);
                guiGraphics.fill(markerX, markerY - 4, markerX + 1, markerY + 5, HeraldryGuiTheme.HIGHLIGHT);
                guiGraphics.fill(markerX, markerY, markerX + 1, markerY + 1, 0xFFFFFFFF);
            } else if (rect != null) {
                Map<Integer, Integer> halfSizes = new HashMap<>();
                for (PaintableSailMesh.ScreenPixel selected : surfaceSelection) {
                    Optional<PaintableSailMesh.ScreenPixel> projected = PaintableSailMesh.projectPixel(surface.ship(), selected.hit());
                    if (projected.isEmpty() || !isVisible(projected.get())) continue;
                    PaintableSailMesh.ScreenPixel pixel = projected.get();
                    int halfSize = halfSizes.computeIfAbsent(
                            pixel.hit().faceIndex(),
                            ignored -> Math.max(1, Mth.ceil(
                                    PaintableSailMesh.projectedPixelScale(surface.ship(), pixel.hit()) * 0.42F
                            ))
                    );
                    int centerX = Mth.floor(pixel.screenX());
                    int centerY = Mth.floor(pixel.screenY());
                    guiGraphics.fill(
                            centerX - halfSize,
                            centerY - halfSize,
                            centerX + halfSize + 1,
                            centerY + halfSize + 1,
                            0x55E7C778
                    );
                    guiGraphics.fill(centerX, centerY, centerX + 1, centerY + 1, HeraldryGuiTheme.HIGHLIGHT);
                }
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            guiGraphics.disableScissor();
            guiGraphics.pose().popPose();
            guiGraphics.flush();
            RenderSystem.enableDepthTest();
        }
    }

    private boolean isVisible(PaintableSailMesh.ScreenPixel pixel) {
        return surface.pick(pixel.screenX(), pixel.screenY())
                .filter(hit -> hit.faceIndex() == pixel.hit().faceIndex()).isPresent();
    }

    private static double distanceSquared(double x, double y, double targetX, double targetY) {
        double deltaX = x - targetX;
        double deltaY = y - targetY;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private record SelectionRect(int x, int y, int width, int height) {
    }

    private record SelectionClipboard(
            int width,
            int height,
            List<PaintableSailMesh.ClipboardPixel> pixels,
            double originX,
            double originY
    ) {
    }

    private record CapturedPixel(PaintableSailMesh.Hit hit, double screenX, double screenY, int color) {
    }

}
