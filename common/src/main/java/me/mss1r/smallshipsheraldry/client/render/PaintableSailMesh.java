package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PaintableSailMesh {
    private static final Map<ShipPreviewType, SailModel> EDITOR_MODELS = new HashMap<>();
    private static final ThreadLocal<Set<String>> EDITOR_HIDDEN_ELEMENTS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EDITOR_ENTITY_SAILS_SUPPRESSED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EDITOR_GRID_VISIBLE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> EDITOR_GRID_FACE_INDEX = new ThreadLocal<>();
    private static final ThreadLocal<Integer> EDITOR_HIGHLIGHT_FACE_INDEX = new ThreadLocal<>();
    private static EditorPickCache lastEditorPickCache;
    private static final float EDITOR_BASE_SCALE = 12.0F;
    public static final float EDITOR_VERTICAL_OFFSET = 28.0F;

    private PaintableSailMesh() {
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

    public static Collection<Element> elements(ShipPreviewType shipType) {
        return definition(shipType).elements();
    }

    public static Collection<Element> elements(ShipPreviewType shipType, int state) {
        return definition(shipType).elementsForState(Mth.clamp(state, 0, 4));
    }

    public static void render(Ship ship, SailModel sailModel, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, Set<String> hiddenElements) {
        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        SailMeshDefinitionBuilder.Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return;
        }

        List<Face> faces = new ArrayList<>();
        for (Face face : definition.facesForState(currentSailState(ship))) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            faces.add(face);
        }
        SailMeshRenderer.renderPainted(faces, poseStack, vertexConsumer, packedLight);
    }

    public static void renderOriginal(Ship ship, SailModel sailModel, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, Set<String> hiddenElements) {
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

    public static void renderGrid(Ship ship, SailModel sailModel, PoseStack poseStack, VertexConsumer vertexConsumer, Set<String> hiddenElements) {
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

    public static void renderHighlight(Ship ship, SailModel sailModel, PoseStack poseStack, VertexConsumer vertexConsumer, Set<String> hiddenElements) {
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

    public static void renderPreviewSails(
            Ship ship,
            GuiGraphics guiGraphics,
            Set<String> hiddenElements
    ) {
        if (ship == null) {
            return;
        }

        SailModel sailModel = editorModel(ShipPreviewType.fromShip(ship));
        if (sailModel == null) {
            return;
        }

        sailModel.setupAnim(ship, 1.0F, 0.0F, -0.1F, 0.0F, 0.0F);
        guiGraphics.pose().pushPose();
        applySmallShipsSailTransform(guiGraphics.pose());
        try {
            VertexConsumer baseConsumer = guiGraphics.bufferSource().getBuffer(sailModel.renderType(SailModel.getSailColor(ship.getData(Ship.SAIL_COLOR)).location));
            renderOriginal(ship, sailModel, guiGraphics.pose(), baseConsumer, LightTexture.FULL_BRIGHT, hiddenElements);

            ItemStack sailPattern = ((SailBannerAccess) ship).smallshipsheraldry$getSailBanner();
            ResourceLocation overlayTexture = sailPattern.isEmpty() ? null : SailBannerTextureCache.getTexture(ship, sailPattern);
            if (overlayTexture != null) {
                VertexConsumer overlayConsumer = guiGraphics.bufferSource().getBuffer(RenderType.entityTranslucent(overlayTexture));
                render(ship, sailModel, guiGraphics.pose(), overlayConsumer, LightTexture.FULL_BRIGHT, hiddenElements);
            }
        } finally {
            guiGraphics.pose().popPose();
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

        List<Face> faces = cachedEditorFaces(ship).orElseGet(() -> editorFaces(ship, left, top, right, bottom, yawDegrees, pitchDegrees, zoom, panX, panY));
        if (faces.isEmpty()) {
            return Optional.empty();
        }
        return SailMeshPicker.pick(faces, mouseX, mouseY, hiddenElements);
    }

    public static float projectedPixelScale(Ship ship, Hit hit) {
        if (ship == null || hit == null) {
            return 1.0F;
        }
        for (Face face : cachedEditorFaces(ship).orElse(List.of())) {
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

        int state = currentSailState(ship);
        List<Face> faces = SailMeshDefinitionBuilder.collectFaces(sailModel, poseStack, definition, state);
        lastEditorPickCache = new EditorPickCache(System.identityHashCode(ship), state, List.copyOf(faces));
    }

    private static Optional<List<Face>> cachedEditorFaces(Ship ship) {
        EditorPickCache cache = lastEditorPickCache;
        if (cache == null || cache.shipIdentity() != System.identityHashCode(ship) || cache.state() != currentSailState(ship)) {
            return Optional.empty();
        }
        return Optional.of(cache.faces());
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

        SailModel sailModel = editorModel(shipType);
        if (sailModel == null) {
            return List.of();
        }

        PoseStack poseStack = editorPose(left, top, right, bottom, yawDegrees, pitchDegrees, zoom, panX, panY);
        sailModel.setupAnim(ship, 1.0F, 0.0F, -0.1F, 0.0F, 0.0F);
        return SailMeshDefinitionBuilder.collectFaces(sailModel, poseStack, definition, currentSailState(ship));
    }

    public static PoseStack editorPose(int left, int top, int right, int bottom, float yawDegrees, float pitchDegrees, float zoom, float panX, float panY) {
        PoseStack poseStack = new PoseStack();
        float centerX = (left + right) * 0.5F + panX;
        float centerY = (top + bottom) * 0.5F + EDITOR_VERTICAL_OFFSET + panY;
        float scale = EDITOR_BASE_SCALE * zoom;
        poseStack.translate(centerX, centerY, 220.0D);
        poseStack.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
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

    private static SailModel editorModel(ShipPreviewType shipType) {
        return EDITOR_MODELS.computeIfAbsent(shipType, SailMeshDefinitionBuilder::createModel);
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

    record Vertex(float x, float y, float z, float originalU, float originalV, float localU, float localV) {
    }

    record Face(int faceIndex, int state, String elementId, Vertex[] vertices, Vector3f normal, AtlasSlot slot) {
        private void addTo(Bounds bounds) {
            for (Vertex vertex : vertices) {
                bounds.include(vertex.x(), vertex.y(), vertex.z());
            }
        }
    }

    private record EditorPickCache(int shipIdentity, int state, List<Face> faces) {
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
