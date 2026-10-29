package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PaintableSailMesh {
    private static final Map<ShipPreviewType, List<Map<Integer, Face>>> FACE_LOOKUPS = new EnumMap<>(ShipPreviewType.class);
    private static final ThreadLocal<Set<String>> EDITOR_HIDDEN_ELEMENTS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EDITOR_ENTITY_SAILS_SUPPRESSED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EDITOR_GRID_VISIBLE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> EDITOR_GRID_FACE_INDEX = new ThreadLocal<>();
    private static final ThreadLocal<Integer> EDITOR_HIGHLIGHT_FACE_INDEX = new ThreadLocal<>();
    private static final ThreadLocal<Float> EDITOR_GUI_SCALE = new ThreadLocal<>();
    private static EditorPickCache lastEditorPickCache;
    private static final float EDITOR_BASE_SCALE = 12.0F;
    public static final float PAINT_PIXELS_PER_MODEL_UNIT = 15.0F;
    public static final float EDITOR_VERTICAL_OFFSET = 28.0F;

    private PaintableSailMesh() {
    }

    public static void verifyModelCompatibility() {
        for (ShipPreviewType shipType : ShipPreviewType.availableTypes()) {
            SailMeshDefinitionBuilder.Definition definition = definition(shipType);
            if (definition.isEmpty()) {
                throw new IllegalStateException("No paintable sail geometry found for " + shipType.serializedName());
            }
            for (int state = 0; state < HeraldryPatternData.STAGE_COUNT; state++) {
                if (definition.facesForState(state).isEmpty()) {
                    throw new IllegalStateException(
                            "No paintable sail geometry found for " + shipType.serializedName() + " stage " + state
                    );
                }
            }
            SmallShipsHeraldry.LOGGER.info(
                    "Verified paintable sail geometry for {} ({} atlas faces)",
                    shipType.serializedName(),
                    definition.faceSlotsByKey().size()
            );
        }
    }

    public static void setEditorHiddenElements(Set<String> hiddenElements) {
        EDITOR_HIDDEN_ELEMENTS.set(hiddenElements == null ? Set.of() : Set.copyOf(hiddenElements));
    }

    public static void clearEditorHiddenElements() {
        EDITOR_HIDDEN_ELEMENTS.remove();
    }

    public static Set<String> editorHiddenElements() {
        Set<String> hiddenElements = EDITOR_HIDDEN_ELEMENTS.get();
        return hiddenElements == null ? Set.of() : hiddenElements;
    }

    public static void setEditorEntitySailsSuppressed(boolean suppressed) {
        EDITOR_ENTITY_SAILS_SUPPRESSED.set(suppressed);
    }

    public static void clearEditorEntitySailsSuppressed() {
        EDITOR_ENTITY_SAILS_SUPPRESSED.remove();
    }

    public static boolean editorEntitySailsSuppressed() {
        return Boolean.TRUE.equals(EDITOR_ENTITY_SAILS_SUPPRESSED.get());
    }

    public static void setEditorGridVisible(boolean visible) {
        EDITOR_GRID_VISIBLE.set(visible);
    }

    public static void clearEditorGridVisible() {
        EDITOR_GRID_VISIBLE.remove();
    }

    public static boolean editorGridVisible() {
        return Boolean.TRUE.equals(EDITOR_GRID_VISIBLE.get());
    }

    public static void setEditorGridFaceIndex(Integer faceIndex) {
        if (faceIndex == null) {
            EDITOR_GRID_FACE_INDEX.remove();
        } else {
            EDITOR_GRID_FACE_INDEX.set(faceIndex);
        }
    }

    public static void clearEditorGridFaceIndex() {
        EDITOR_GRID_FACE_INDEX.remove();
    }

    public static void setEditorHighlightFaceIndex(Integer faceIndex) {
        if (faceIndex == null) {
            EDITOR_HIGHLIGHT_FACE_INDEX.remove();
        } else {
            EDITOR_HIGHLIGHT_FACE_INDEX.set(faceIndex);
        }
    }

    public static void clearEditorHighlightFaceIndex() {
        EDITOR_HIGHLIGHT_FACE_INDEX.remove();
    }

    public static Collection<Element> elements(ShipPreviewType shipType, int state) {
        return definition(shipType).elementsForState(Mth.clamp(state, 0, 4));
    }

    public static void render(Ship ship, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, Set<String> hiddenElements) {
        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return;
        }

        SailMeshRenderer.renderPainted(
                definition.facesForState(currentSailState(ship)),
                poseStack,
                vertexConsumer,
                packedLight,
                hiddenElements
        );
    }

    public static void renderOriginal(Ship ship, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, Set<String> hiddenElements) {
        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return;
        }

        for (Face face : definition.facesForState(currentSailState(ship))) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            SailMeshRenderer.renderOriginal(face, poseStack, vertexConsumer, packedLight);
        }
    }

    public static void renderGrid(Ship ship, PoseStack poseStack, VertexConsumer vertexConsumer, Set<String> hiddenElements) {
        Integer targetFaceIndex = EDITOR_GRID_FACE_INDEX.get();
        if (targetFaceIndex == null) {
            return;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return;
        }

        for (Face face : definition.facesForState(currentSailState(ship))) {
            if (face.faceIndex() != targetFaceIndex) {
                continue;
            }
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            SailMeshRenderer.renderGrid(face, poseStack, vertexConsumer);
            return;
        }
    }

    public static void renderHighlight(Ship ship, PoseStack poseStack, VertexConsumer vertexConsumer, Set<String> hiddenElements) {
        Integer targetFaceIndex = EDITOR_HIGHLIGHT_FACE_INDEX.get();
        if (targetFaceIndex == null) {
            return;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return;
        }

        for (Face face : definition.facesForState(currentSailState(ship))) {
            if (face.faceIndex() != targetFaceIndex) {
                continue;
            }
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            SailMeshRenderer.renderHighlight(face, poseStack, vertexConsumer);
            return;
        }
    }

    public static Optional<Hit> pick(
            Ship ship,
            int left,
            int top,
            int right,
            int bottom,
            float yawDegrees,
            float pitchDegrees,
            float zoom,
            float panX,
            float panY,
            double mouseX,
            double mouseY,
            Set<String> hiddenElements
    ) {
        if (ship == null) {
            return Optional.empty();
        }

        SailMeshPicker.Index index = cachedEditorIndex(ship).orElseGet(() -> SailMeshPicker.index(
                editorFaces(ship, left, top, right, bottom, yawDegrees, pitchDegrees, zoom, panX, panY)
        ));
        if (index.faces().isEmpty()) {
            return Optional.empty();
        }
        return SailMeshPicker.pick(index, mouseX, mouseY, hiddenElements);
    }

    public static Optional<ScreenPixel> projectPixel(Ship ship, Hit hit) {
        if (ship == null || hit == null) return Optional.empty();
        return cachedEditorIndex(ship).flatMap(index -> index.face(hit.faceIndex()))
                .map(face -> SailClipboardMapper.projectPixel(face, hit));
    }

    public static float projectedPixelScale(Ship ship, Hit hit) {
        if (ship == null || hit == null) {
            return 1.0F;
        }
        for (Face face : cachedEditorIndex(ship).map(SailMeshPicker.Index::faces).orElse(List.of())) {
            if (face.faceIndex() != hit.faceIndex()) {
                continue;
            }
            Vertex[] vertices = face.vertices();
            float horizontal = (screenDistance(vertices[0], vertices[1]) + screenDistance(vertices[3], vertices[2]))
                    / (2.0F * Math.max(1, face.slot().width()));
            float vertical = (screenDistance(vertices[0], vertices[3]) + screenDistance(vertices[1], vertices[2]))
                    / (2.0F * Math.max(1, face.slot().height()));
            return Math.max(0.5F, (horizontal + vertical) * 0.5F);
        }
        return 1.0F;
    }

    public static List<ScreenPixel> pixelsInSurfaceRect(
            Ship ship,
            int stage,
            Hit start,
            Hit end,
            Set<String> selectedElements,
            Set<String> hiddenElements
    ) {
        if (ship == null || start == null || end == null) {
            return List.of();
        }
        List<Face> definitionFaces = facesFor(ship, stage);
        Face startFace = faceFor(ship, stage, start.faceIndex());
        Face endFace = faceFor(ship, stage, end.faceIndex());
        if (startFace == null || endFace == null) {
            return List.of();
        }

        int startLocalX = Mth.clamp(start.pixelX() - startFace.slot().x(), 0, startFace.slot().width() - 1);
        int startLocalY = Mth.clamp(start.pixelY() - startFace.slot().y(), 0, startFace.slot().height() - 1);
        int endLocalX = Mth.clamp(end.pixelX() - endFace.slot().x(), 0, endFace.slot().width() - 1);
        int endLocalY = Mth.clamp(end.pixelY() - endFace.slot().y(), 0, endFace.slot().height() - 1);
        SurfacePoint first = SailClipboardMapper.point(definitionFaces, startFace, startLocalX, startLocalY);
        SurfacePoint last = SailClipboardMapper.point(definitionFaces, endFace, endLocalX, endLocalY);
        if (first.componentId() != last.componentId()) {
            return List.of();
        }

        float padding = 0.25F / PAINT_PIXELS_PER_MODEL_UNIT;
        float minU = Math.min(first.u(), last.u()) - padding;
        float maxU = Math.max(first.u(), last.u()) + padding;
        float minV = Math.min(first.v(), last.v()) - padding;
        float maxV = Math.max(first.v(), last.v()) + padding;
        Optional<SailMeshPicker.Index> cached = cachedEditorIndex(ship);
        if (cached.isEmpty()) {
            return List.of();
        }

        SailMeshPicker.Index index = cached.get();
        List<ScreenPixel> pixels = new ArrayList<>();
        for (Face screenFace : index.faces()) {
            if (hiddenElements != null && hiddenElements.contains(screenFace.elementId())) {
                continue;
            }
            if (selectedElements != null && !selectedElements.isEmpty()
                    && !selectedElements.contains(screenFace.elementId())) {
                continue;
            }
            Face definitionFace = faceFor(ship, stage, screenFace.faceIndex());
            if (definitionFace == null) {
                continue;
            }
            AtlasSlot slot = definitionFace.slot();
            for (int localY = 0; localY < slot.height(); localY++) {
                for (int localX = 0; localX < slot.width(); localX++) {
                    SurfacePoint point = SailClipboardMapper.point(
                            definitionFaces,
                            definitionFace,
                            localX,
                            localY
                    );
                    if (point.componentId() != first.componentId()
                            || point.u() < minU || point.u() > maxU
                            || point.v() < minV || point.v() > maxV) {
                        continue;
                    }
                    Vector3f screen = SailClipboardMapper.position(screenFace, localX, localY);
                    Optional<Hit> visible = SailMeshPicker.pick(index, screen.x(), screen.y(), hiddenElements);
                    if (visible.isEmpty() || visible.get().faceIndex() != screenFace.faceIndex()) {
                        continue;
                    }
                    pixels.add(new ScreenPixel(
                            new Hit(
                                    definitionFace.state(),
                                    definitionFace.elementId(),
                                    definitionFace.faceIndex(),
                                    slot.x() + localX,
                                    slot.y() + localY,
                                    slot.x(),
                                    slot.y(),
                                    slot.width(),
                                    slot.height()
                            ),
                            screen.x(),
                            screen.y()
                    ));
                }
            }
        }
        return List.copyOf(pixels);
    }

    private static float screenDistance(Vertex first, Vertex second) {
        float x = second.x() - first.x();
        float y = second.y() - first.y();
        return Mth.sqrt(x * x + y * y);
    }

    public static void captureEditorPickFaces(Ship ship, SailModel sailModel, PoseStack poseStack) {
        if (ship == null || sailModel == null) {
            return;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            lastEditorPickCache = null;
            return;
        }

        poseStack = EditorPickTransform.logicalPose(poseStack, EDITOR_GUI_SCALE.get() == null ? 1 : EDITOR_GUI_SCALE.get());
        int state = currentSailState(ship);
        int transformHash = transformHash(poseStack.last().pose());
        EditorPickCache cached = lastEditorPickCache;
        if (cached != null && cached.ship() == ship && cached.state() == state && cached.transformHash() == transformHash) {
            return;
        }
        // Baking uses the shared registry model for all stages. Restore this ship's
        // pose before sampling its visible faces, including the very first frame.
        SailModelBridge.setupAnim(sailModel, ship, 1.0F);
        List<Face> faces = SailMeshDefinitionBuilder.collectFaces(sailModel, poseStack, definition, state);
        lastEditorPickCache = new EditorPickCache(ship, state, transformHash, SailMeshPicker.index(faces));
    }

    public static void setEditorGuiScale(float scale) {
        EDITOR_GUI_SCALE.set(scale);
    }

    public static void clearEditorGuiScale() {
        EDITOR_GUI_SCALE.remove();
    }

    private static Optional<SailMeshPicker.Index> cachedEditorIndex(Ship ship) {
        EditorPickCache cache = lastEditorPickCache;
        if (cache == null || cache.ship() != ship || cache.state() != currentSailState(ship)) {
            return Optional.empty();
        }
        return Optional.of(cache.index());
    }

    private static int transformHash(Matrix4f matrix) {
        int result = Float.floatToIntBits(matrix.m00());
        result = 31 * result + Float.floatToIntBits(matrix.m01());
        result = 31 * result + Float.floatToIntBits(matrix.m02());
        result = 31 * result + Float.floatToIntBits(matrix.m03());
        result = 31 * result + Float.floatToIntBits(matrix.m10());
        result = 31 * result + Float.floatToIntBits(matrix.m11());
        result = 31 * result + Float.floatToIntBits(matrix.m12());
        result = 31 * result + Float.floatToIntBits(matrix.m13());
        result = 31 * result + Float.floatToIntBits(matrix.m20());
        result = 31 * result + Float.floatToIntBits(matrix.m21());
        result = 31 * result + Float.floatToIntBits(matrix.m22());
        result = 31 * result + Float.floatToIntBits(matrix.m23());
        result = 31 * result + Float.floatToIntBits(matrix.m30());
        result = 31 * result + Float.floatToIntBits(matrix.m31());
        result = 31 * result + Float.floatToIntBits(matrix.m32());
        return 31 * result + Float.floatToIntBits(matrix.m33());
    }

    static List<Face> facesFor(Ship ship, int stage) {
        if (ship == null) {
            return List.of();
        }
        SailMeshDefinitionBuilder.Definition definition = definition(ShipPreviewType.fromShip(ship));
        if (definition.isEmpty()) {
            return List.of();
        }
        return definition.facesForState(Mth.clamp(stage, 0, HeraldryPatternData.STAGE_COUNT - 1));
    }

    static Face faceFor(Ship ship, int stage, int faceIndex) {
        if (ship == null || faceIndex < 0) {
            return null;
        }
        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        List<Map<Integer, Face>> stages = FACE_LOOKUPS.computeIfAbsent(shipType, PaintableSailMesh::buildFaceLookups);
        return stages.get(Mth.clamp(stage, 0, HeraldryPatternData.STAGE_COUNT - 1)).get(faceIndex);
    }

    public static Optional<SurfacePoint> surfacePoint(Ship ship, int stage, Hit hit) {
        if (hit == null) {
            return Optional.empty();
        }
        List<Face> faces = facesFor(ship, stage);
        Face face = faceFor(ship, stage, hit.faceIndex());
        if (face == null) {
            return Optional.empty();
        }
        int localX = Mth.clamp(hit.pixelX() - face.slot().x(), 0, Math.max(0, face.slot().width() - 1));
        int localY = Mth.clamp(hit.pixelY() - face.slot().y(), 0, Math.max(0, face.slot().height() - 1));
        return Optional.of(SailClipboardMapper.point(faces, face, localX, localY));
    }

    public static Map<Integer, Integer> mapClipboardPixels(
            Ship ship,
            int stage,
            Hit target,
            List<ClipboardPixel> clipboard,
            Set<String> selectedElements,
            Set<String> hiddenElements
    ) {
        Optional<SurfacePoint> targetPoint = surfacePoint(ship, stage, target);
        if (targetPoint.isEmpty()) {
            return Map.of();
        }
        return SailClipboardMapper.map(
                facesFor(ship, stage),
                targetPoint.get(),
                clipboard,
                target.faceIndex(),
                selectedElements,
                hiddenElements
        );
    }

    private static List<Map<Integer, Face>> buildFaceLookups(ShipPreviewType shipType) {
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        List<Map<Integer, Face>> stages = new ArrayList<>(HeraldryPatternData.STAGE_COUNT);
        for (int stage = 0; stage < HeraldryPatternData.STAGE_COUNT; stage++) {
            Map<Integer, Face> faces = new HashMap<>();
            for (Face face : definition.facesForState(stage)) {
                faces.put(face.faceIndex(), face);
            }
            stages.add(Map.copyOf(faces));
        }
        return List.copyOf(stages);
    }

    public static boolean fillElementPixels(Ship ship, String elementId, int stage, int[] meshPixels, int color) {
        return SailMeshPainter.fillElementPixels(ship, elementId, stage, meshPixels, color);
    }

    public static boolean fillStagePixels(Ship ship, int stage, int[] meshPixels, int color) {
        return SailMeshPainter.fillStagePixels(ship, stage, meshPixels, color);
    }

    public static boolean fillFacePixels(Ship ship, int faceIndex, int stage, int[] meshPixels, int color) {
        return SailMeshPainter.fillFacePixels(ship, faceIndex, stage, meshPixels, color);
    }

    public static boolean paintBrushPixels(
            Ship ship,
            Hit hit,
            int stage,
            int[] meshPixels,
            int color,
            int size,
            boolean mirrorVertical,
            boolean mirrorHorizontal
    ) {
        return SailMeshPainter.paintBrushPixels(ship, hit, stage, meshPixels, color, size, mirrorVertical, mirrorHorizontal);
    }

    public static boolean paintLinePixels(
            Ship ship,
            Hit start,
            Hit end,
            int stage,
            int[] meshPixels,
            int color,
            int size,
            boolean mirrorVertical,
            boolean mirrorHorizontal
    ) {
        return SailMeshPainter.paintLinePixels(ship, start, end, stage, meshPixels, color, size, mirrorVertical, mirrorHorizontal);
    }

    public static boolean floodFillPixels(Ship ship, Hit hit, int stage, int[] meshPixels, int color) {
        return SailMeshPainter.floodFillPixels(ship, hit, stage, meshPixels, color);
    }

    public static boolean replaceColorPixels(
            Ship ship,
            int stage,
            int[] meshPixels,
            int source,
            int color,
            Set<String> selectedElements,
            Set<String> hiddenElements
    ) {
        return SailMeshPainter.replaceColorPixels(ship, stage, meshPixels, source, color, selectedElements, hiddenElements);
    }

    public static boolean fillScopedPixels(
            Ship ship,
            int stage,
            int[] meshPixels,
            int color,
            Set<String> selectedElements,
            Set<String> hiddenElements
    ) {
        return SailMeshPainter.fillScopedPixels(ship, stage, meshPixels, color, selectedElements, hiddenElements);
    }


    private static List<Face> editorFaces(
            Ship ship,
            int left,
            int top,
            int right,
            int bottom,
            float yawDegrees,
            float pitchDegrees,
            float zoom,
            float panX,
            float panY
    ) {
        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return List.of();
        }

        SailModel sailModel = SailModelBridge.model(ship);
        if (sailModel == null) {
            return List.of();
        }

        PoseStack poseStack = editorPose(left, top, right, bottom, yawDegrees, pitchDegrees, zoom, panX, panY);
        SailModelBridge.setupAnim(sailModel, ship, 1.0F);
        return SailMeshDefinitionBuilder.collectFaces(sailModel, poseStack, definition, currentSailState(ship));
    }

    public static PoseStack editorPose(int left, int top, int right, int bottom, float yawDegrees, float pitchDegrees, float zoom, float panX, float panY) {
        PoseStack poseStack = new PoseStack();
        float centerX = (left + right) * 0.5F + panX;
        float centerY = (top + bottom) * 0.5F + EDITOR_VERTICAL_OFFSET + panY;
        float scale = EDITOR_BASE_SCALE * zoom;
        poseStack.translate(centerX, centerY, 220.0D);
        poseStack.scale(scale, scale, -scale);
        poseStack.translate(0.0F, 0.05F, 0.0F);
        poseStack.mulPose(new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX(pitchDegrees * ((float) Math.PI / 180.0F))
                .rotateY(yawDegrees * ((float) Math.PI / 180.0F))
        );
        applySmallShipsSailTransform(poseStack);
        return poseStack;
    }

    public static void applySmallShipsSailTransform(PoseStack poseStack) {
        poseStack.scale(-1.3F, -1.3F, 1.3F);
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
    }

    private static SailMeshDefinitionBuilder.Definition definition(ShipPreviewType shipType) {
        return SailMeshDefinitionBuilder.definition(shipType);
    }

    private static int currentSailState(Ship ship) {
        return ship.getData(Ship.SAIL_STATE);
    }

    public record Element(String id, String label) {
    }

    public record Hit(
            int stage,
            String elementId,
            int faceIndex,
            int pixelX,
            int pixelY,
            int slotX,
            int slotY,
            int slotWidth,
            int slotHeight
    ) {
    }

    public record SurfacePoint(float u, float v, int componentId) {
    }

    public record ClipboardPixel(float offsetU, float offsetV, int color) {
    }

    public record ScreenPixel(Hit hit, double screenX, double screenY) {
    }

    record Vertex(float x, float y, float z, float originalU, float originalV, float localU, float localV) {
    }

    record Face(int faceIndex, int state, String elementId, Vertex[] vertices, Vector3f normal, AtlasSlot slot) {
        private void addTo(Bounds bounds) {
            for (Vertex vertex : vertices) {
                bounds.include(vertex.x(), vertex.y(), vertex.z());
            }
        }
    }

    private record EditorPickCache(Ship ship, int state, int transformHash, SailMeshPicker.Index index) {
    }

    record AtlasSlot(int index, int x, int y, int width, int height) {
    }

    private static final class Bounds {
        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float minZ = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;
        private float maxZ = -Float.MAX_VALUE;

        private void include(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        private boolean isUsable() {
            return minX != Float.MAX_VALUE && spanX() > 0.001F && spanY() > 0.001F;
        }

        private float spanX() {
            return maxX - minX;
        }

        private float spanY() {
            return maxY - minY;
        }

        private float spanZ() {
            return maxZ - minZ;
        }

        private float centerX() {
            return (minX + maxX) * 0.5F;
        }

        private float centerY() {
            return (minY + maxY) * 0.5F;
        }

        private float centerZ() {
            return (minZ + maxZ) * 0.5F;
        }
    }
}
