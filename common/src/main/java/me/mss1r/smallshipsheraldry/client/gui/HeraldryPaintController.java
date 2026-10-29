package me.mss1r.smallshipsheraldry.client.gui;

import com.mojang.math.Axis;
import me.mss1r.smallshipsheraldry.client.render.GuiScissor;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;

final class HeraldryPaintController {
    private final HeraldryEditorSession session;
    private final HeraldryEditorSurface surface;
    private final HeraldryEditorEdits edits;
    private final IntConsumer sampledColor;
    private Settings settings;
    private boolean paintingPreview;
    private boolean temporaryPicker;
    private boolean strokeUndoCaptured;
    private PaintableSailMesh.Hit strokeStartHit;
    private PaintableSailMesh.Hit lastStrokeHit;
    private double strokeStartScreenX;
    private double strokeStartScreenY;
    private double strokeEndScreenX;
    private double strokeEndScreenY;

    HeraldryPaintController(HeraldryEditorSession session, HeraldryEditorSurface surface,
                            HeraldryEditorEdits edits, IntConsumer sampledColor) {
        this.session = session;
        this.surface = surface;
        this.edits = edits;
        this.sampledColor = sampledColor;
    }

    boolean isLine() { return paintingPreview && settings.stroke() == StrokeMode.LINE && strokeStartHit != null && !temporaryPicker; }

    boolean begin(double x, double y, Settings settings, boolean sampleOnly) {
        cancel();
        this.settings = settings;
        temporaryPicker = sampleOnly;
        if ((settings.tool() == PaintTool.BRUSH || settings.tool() == PaintTool.ERASER)
                && settings.stroke() == StrokeMode.LINE && !sampleOnly) {
            Optional<PaintableSailMesh.Hit> hit = surface.pick(x, y);
            if (hit.isEmpty() || !surface.accepts(hit.get())) return true;
            paintingPreview = true;
            strokeStartHit = hit.get();
            lastStrokeHit = hit.get();
            strokeStartScreenX = strokeEndScreenX = x;
            strokeStartScreenY = strokeEndScreenY = y;
            return true;
        }
        paintingPreview = settings.tool().drags() && !sampleOnly;
        return useToolOnPreview(x, y);
    }

    boolean drag(double x, double y) {
        if (!paintingPreview) return false;
        if (isLine()) {
            strokeEndScreenX = x;
            strokeEndScreenY = y;
            surface.pick(x, y).filter(surface::accepts).ifPresent(hit -> lastStrokeHit = hit);
        } else {
            useToolOnPreview(x, y);
        }
        return true;
    }

    void release() {
        if (isLine()) applyLineStroke();
        else if (paintingPreview) edits.commit();
        cancel();
    }

    void cancel() {
        paintingPreview = false;
        temporaryPicker = false;
        strokeUndoCaptured = false;
        strokeStartHit = null;
        lastStrokeHit = null;
    }

    record Settings(PaintTool tool, StrokeMode stroke, FillMode fill, int size,
                    boolean mirrorVertical, boolean mirrorHorizontal, int color) {
    }

    private boolean useToolOnPreview(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> hit = surface.pick(mouseX, mouseY);
        if (hit.isEmpty()) {
            return true;
        }

        PaintableSailMesh.Hit paintHit = hit.get();
        if (!temporaryPicker && settings.tool() != PaintTool.PICKER && !surface.accepts(paintHit)) {
            return true;
        }

        if (temporaryPicker || settings.tool() == PaintTool.PICKER) {
            int sampledColor = session.pixels()[paintHit.pixelY() * HeraldryPatternData.MESH_ATLAS_WIDTH + paintHit.pixelX()];
            this.sampledColor.accept((sampledColor >>> 24) == 0 ? session.pattern().baseColorArgb() : sampledColor);
            return true;
        }

        if (settings.tool() == PaintTool.FILL) {
            if (!edits.pushUndo()) {
                return true;
            }
            boolean changed = switch (settings.fill()) {
                case AREA -> PaintableSailMesh.floodFillPixels(surface.ship(), paintHit, surface.stage(), session.pixels(), settings.color());
                case FACE -> PaintableSailMesh.fillFacePixels(surface.ship(), paintHit.faceIndex(), surface.stage(), session.pixels(), settings.color());
                case REPLACE -> {
                    int source = session.pixels()[paintHit.pixelY() * HeraldryPatternData.MESH_ATLAS_WIDTH + paintHit.pixelX()];
                    yield PaintableSailMesh.replaceColorPixels(surface.ship(), surface.stage(), session.pixels(), source, settings.color(), surface.selectedElements(), surface.hiddenElements());
                }
            };
            if (!changed) {
                session.discardLatestUndo();
                return true;
            }
            if (settings.fill() == FillMode.REPLACE) {
                surface.replacePixels(session.pixels());
            } else {
                surface.syncFace(session.pixels(), paintHit);
            }
            edits.commit();
            return true;
        }

        int color = settings.tool() == PaintTool.ERASER ? 0 : settings.color();
        if (!strokeUndoCaptured) {
            if (!edits.pushUndo()) {
                return true;
            }
            strokeUndoCaptured = true;
        }
        boolean changed;
        if (lastStrokeHit != null && lastStrokeHit.faceIndex() == paintHit.faceIndex()) {
            changed = PaintableSailMesh.paintLinePixels(surface.ship(), lastStrokeHit, paintHit, surface.stage(), session.pixels(), color,
                    settings.size(), settings.mirrorVertical(), settings.mirrorHorizontal());
        } else {
            changed = PaintableSailMesh.paintBrushPixels(surface.ship(), paintHit, surface.stage(), session.pixels(), color,
                    settings.size(), settings.mirrorVertical(), settings.mirrorHorizontal());
        }
        lastStrokeHit = paintHit;
        if (changed) {
            surface.syncFace(session.pixels(), paintHit);
            session.markLocallyModified();
        }
        return true;
    }

    private void applyLineStroke() {
        if (strokeStartHit == null || !edits.pushUndo()) {
            return;
        }
        int color = settings.tool() == PaintTool.ERASER ? 0 : settings.color();
        double deltaX = strokeEndScreenX - strokeStartScreenX;
        double deltaY = strokeEndScreenY - strokeStartScreenY;
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        double length = Math.sqrt(lengthSquared);
        double thickness = lineScreenThickness();
        double halfThickness = thickness * 0.5D;
        int previewLeft = surface.bounds().x();
        int previewTop = surface.bounds().y();
        int previewRight = previewLeft + surface.bounds().width() - 1;
        int previewBottom = previewTop + surface.bounds().height() - 1;
        int minX = Math.max(previewLeft, Mth.floor(Math.min(strokeStartScreenX, strokeEndScreenX) - halfThickness));
        int maxX = Math.min(previewRight, Mth.ceil(Math.max(strokeStartScreenX, strokeEndScreenX) + halfThickness));
        int minY = Math.max(previewTop, Mth.floor(Math.min(strokeStartScreenY, strokeEndScreenY) - halfThickness));
        int maxY = Math.min(previewBottom, Mth.ceil(Math.max(strokeStartScreenY, strokeEndScreenY) + halfThickness));
        Map<Integer, PaintableSailMesh.Hit> touchedFaces = new HashMap<>();
        Set<Long> touchedPixels = new LinkedHashSet<>();
        boolean changed = false;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double sampleX = x + 0.5D;
                double sampleY = y + 0.5D;
                if (!insideLineRectangle(sampleX, sampleY, deltaX, deltaY, lengthSquared, length, halfThickness)) {
                    continue;
                }
                Optional<PaintableSailMesh.Hit> sampled = surface.pick(sampleX, sampleY);
                if (sampled.isEmpty() || !surface.accepts(sampled.get())) {
                    continue;
                }
                PaintableSailMesh.Hit hit = sampled.get();
                long pixelKey = ((long) hit.faceIndex() << 32)
                        ^ ((long) hit.pixelY() << 16)
                        ^ (hit.pixelX() & 0xFFFFL);
                if (!touchedPixels.add(pixelKey)) {
                    continue;
                }
                changed |= PaintableSailMesh.paintBrushPixels(surface.ship(), hit, surface.stage(),
                        session.pixels(), color, 1, settings.mirrorVertical(), settings.mirrorHorizontal());
                touchedFaces.put(hit.faceIndex(), hit);
            }
        }
        if (!changed) {
            session.discardLatestUndo();
            return;
        }
        touchedFaces.values().forEach(hit -> surface.syncFace(session.pixels(), hit));
        session.markLocallyModified();
        edits.commit();
    }

    private boolean insideLineRectangle(
            double x,
            double y,
            double deltaX,
            double deltaY,
            double lengthSquared,
            double length,
            double halfThickness
    ) {
        if (lengthSquared < 0.0001D) {
            return Math.abs(x - strokeStartScreenX) <= halfThickness
                    && Math.abs(y - strokeStartScreenY) <= halfThickness;
        }
        double relativeX = x - strokeStartScreenX;
        double relativeY = y - strokeStartScreenY;
        double position = (relativeX * deltaX + relativeY * deltaY) / lengthSquared;
        if (position < 0.0D || position > 1.0D) {
            return false;
        }
        double perpendicularDistance = Math.abs(relativeX * deltaY - relativeY * deltaX) / length;
        return perpendicularDistance <= halfThickness;
    }

    private float lineScreenThickness() {
        float startScale = PaintableSailMesh.projectedPixelScale(surface.ship(), strokeStartHit);
        float endScale = lastStrokeHit == null
                ? startScale
                : PaintableSailMesh.projectedPixelScale(surface.ship(), lastStrokeHit);
        return Math.max(1.0F, settings.size() * (startScale + endScale) * 0.5F);
    }

    void drawLinePreview(GuiGraphics guiGraphics) {
        double deltaX = strokeEndScreenX - strokeStartScreenX;
        double deltaY = strokeEndScreenY - strokeStartScreenY;
        float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        float thickness = lineScreenThickness();
        GuiScissor.enable(guiGraphics, surface.bounds().x(), surface.bounds().y(),
                surface.bounds().x() + surface.bounds().width(), surface.bounds().y() + surface.bounds().height());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(strokeStartScreenX, strokeStartScreenY, 0.0D);
        guiGraphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(deltaY, deltaX)));
        int half = Math.max(1, Mth.ceil(thickness * 0.5F));
        guiGraphics.fill(0, -half, Math.max(1, Mth.ceil(length)), half, settings.color() | 0xA0000000);
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    enum PaintTool {
        BRUSH("brush", true, 0),
        ERASER("eraser", true, 16),
        PICKER("picker", false, 32),
        FILL("fill", false, 48),
        SELECT("select", false, 128);

        private final String label;
        private final boolean drags;
        private final int iconU;

        PaintTool(String label, boolean drags, int iconU) {
            this.label = label;
            this.drags = drags;
            this.iconU = iconU;
        }

        Component displayName() {
            return Component.translatable("smallshipsheraldry.tool." + label);
        }

        boolean drags() {
            return drags;
        }

        String label() {
            return label;
        }

        int iconU() {
            return iconU;
        }

        static PaintTool byName(String name) {
            if ("fill_face".equalsIgnoreCase(name)) {
                return FILL;
            }
            for (PaintTool tool : values()) {
                if (tool.label.equalsIgnoreCase(name)) {
                    return tool;
                }
            }
            return BRUSH;
        }
    }

    enum StrokeMode {
        FREEHAND("freehand"),
        LINE("line");

        private final String label;

        StrokeMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static StrokeMode byName(String name) {
            for (StrokeMode mode : values()) {
                if (mode.label.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return FREEHAND;
        }
    }

    enum FillMode {
        AREA("area"),
        FACE("face"),
        REPLACE("replace");

        private final String label;

        FillMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        Component displayName() {
            return Component.translatable("smallshipsheraldry.fill." + label);
        }

        static FillMode byName(String name) {
            for (FillMode mode : values()) {
                if (mode.label.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return AREA;
        }
    }

}
