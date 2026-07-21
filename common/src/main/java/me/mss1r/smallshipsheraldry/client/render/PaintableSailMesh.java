package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.talhanation.smallships.client.model.sail.BriggSailModel;
import com.talhanation.smallships.client.model.sail.CogSailModel;
import com.talhanation.smallships.client.model.sail.DrakkarSailModel;
import com.talhanation.smallships.client.model.sail.GalleySailModel;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

public final class PaintableSailMesh {
    private static final Map<ShipPreviewType, Supplier<SailModel>> MODEL_FACTORIES = Map.of(
            ShipPreviewType.COG, CogSailModel::new,
            ShipPreviewType.BRIGG, BriggSailModel::new,
            ShipPreviewType.GALLEY, GalleySailModel::new,
            ShipPreviewType.DRAKKAR, DrakkarSailModel::new
    );
    private static final Map<ShipPreviewType, Definition> DEFINITIONS = new HashMap<>();
    private static final Map<ShipPreviewType, SailModel> EDITOR_MODELS = new HashMap<>();
    private static final ThreadLocal<Set<String>> EDITOR_HIDDEN_ELEMENTS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EDITOR_ENTITY_SAILS_SUPPRESSED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EDITOR_GRID_VISIBLE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> EDITOR_GRID_FACE_INDEX = new ThreadLocal<>();
    private static final ThreadLocal<Integer> EDITOR_HIGHLIGHT_FACE_INDEX = new ThreadLocal<>();
    private static EditorPickCache lastEditorPickCache;
    private static final Field MODEL_PART_CUBES = findModelPartField(List.class);
    private static final Field MODEL_PART_CHILDREN = findModelPartField(Map.class);
    private static final float EDITOR_BASE_SCALE = 12.0F;
    private static final float PAINT_PIXELS_PER_MODEL_UNIT = 15.0F;
    private static final float PAINT_NORMAL_PUSH = 0.003F;
    private static final float PAINT_SEAM_OVERLAP = 0.004F;
    private static final float EDGE_KEY_SCALE = 10000.0F;
    private static final int TILE_PADDING = 1;
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
        Definition definition = definition(shipType);
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
        emitFaces(faces, poseStack, vertexConsumer, packedLight);
    }

    public static void renderOriginal(Ship ship, SailModel sailModel, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, Set<String> hiddenElements) {
        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return;
        }

        for (Face face : definition.facesForState(currentSailState(ship))) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }
            emitOriginalFace(face, poseStack, vertexConsumer, packedLight);
        }
    }

    public static void renderGrid(Ship ship, SailModel sailModel, PoseStack poseStack, VertexConsumer vertexConsumer, Set<String> hiddenElements) {
        Integer targetFaceIndex = EDITOR_GRID_FACE_INDEX.get();
        if (targetFaceIndex == null) {
            return;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        Definition definition = definition(shipType);
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
            emitGridFace(face, poseStack, vertexConsumer);
            return;
        }
    }

    public static void renderHighlight(Ship ship, SailModel sailModel, PoseStack poseStack, VertexConsumer vertexConsumer, Set<String> hiddenElements) {
        Integer targetFaceIndex = EDITOR_HIGHLIGHT_FACE_INDEX.get();
        if (targetFaceIndex == null) {
            return;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        Definition definition = definition(shipType);
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
            emitHighlightFace(face, poseStack, vertexConsumer);
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

        Hit bestHit = null;
        float bestDepth = -Float.MAX_VALUE;
        for (Face face : faces) {
            if (hiddenElements != null && hiddenElements.contains(face.elementId())) {
                continue;
            }

            ProjectedVertex[] projected = new ProjectedVertex[4];
            for (int i = 0; i < 4; i++) {
                Vertex vertex = face.vertices()[i];
                projected[i] = new ProjectedVertex(vertex.x(), vertex.y(), vertex.z(), vertex.localU(), vertex.localV());
            }

            Hit hit = hitFace(face, projected, mouseX, mouseY);
            if (hit != null) {
                float depth = averageDepth(face);
                if (depth > bestDepth) {
                    bestDepth = depth;
                    bestHit = hit;
                }
            }
        }

        return Optional.ofNullable(bestHit);
    }

    public static void captureEditorPickFaces(Ship ship, SailModel sailModel, PoseStack poseStack) {
        if (ship == null || sailModel == null) {
            return;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            lastEditorPickCache = null;
            return;
        }

        int state = currentSailState(ship);
        List<Face> faces = collectFaces(ship, sailModel, poseStack, definition, state);
        lastEditorPickCache = new EditorPickCache(System.identityHashCode(ship), state, List.copyOf(faces));
    }

    private static Optional<List<Face>> cachedEditorFaces(Ship ship) {
        EditorPickCache cache = lastEditorPickCache;
        if (cache == null || cache.shipIdentity() != System.identityHashCode(ship) || cache.state() != currentSailState(ship)) {
            return Optional.empty();
        }
        return Optional.of(cache.faces());
    }

    public static boolean fillElementPixels(Ship ship, String elementId, int stage, int[] meshPixels, int color) {
        if (ship == null || elementId == null || elementId.isBlank() || meshPixels == null) {
            return false;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return false;
        }

        int clampedStage = Mth.clamp(stage, 0, HeraldryPatternData.STAGE_COUNT - 1);
        boolean changed = false;
        for (Face face : definition.facesForState(clampedStage)) {
            if (!elementId.equals(face.elementId())) {
                continue;
            }
            fillFacePixels(face, meshPixels, color);
            changed = true;
        }
        return changed;
    }

    public static boolean fillStagePixels(Ship ship, int stage, int[] meshPixels, int color) {
        if (ship == null || meshPixels == null) {
            return false;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return false;
        }

        int clampedStage = Mth.clamp(stage, 0, HeraldryPatternData.STAGE_COUNT - 1);
        boolean changed = false;
        for (Face face : definition.facesForState(clampedStage)) {
            fillFacePixels(face, meshPixels, color);
            changed = true;
        }
        return changed;
    }

    public static boolean fillFacePixels(Ship ship, int faceIndex, int stage, int[] meshPixels, int color) {
        if (ship == null || meshPixels == null) {
            return false;
        }

        ShipPreviewType shipType = ShipPreviewType.fromShip(ship);
        Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return false;
        }

        int clampedStage = Mth.clamp(stage, 0, HeraldryPatternData.STAGE_COUNT - 1);
        for (Face face : definition.facesForState(clampedStage)) {
            if (face.faceIndex() == faceIndex) {
                fillFacePixels(face, meshPixels, color);
                return true;
            }
        }
        return false;
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
        Definition definition = definition(shipType);
        if (definition.isEmpty()) {
            return List.of();
        }

        SailModel sailModel = editorModel(shipType);
        if (sailModel == null) {
            return List.of();
        }

        PoseStack poseStack = editorPose(left, top, right, bottom, yawDegrees, pitchDegrees, zoom, panX, panY);
        sailModel.setupAnim(ship, 1.0F, 0.0F, -0.1F, 0.0F, 0.0F);
        return collectFaces(ship, sailModel, poseStack, definition, currentSailState(ship));
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
        return EDITOR_MODELS.computeIfAbsent(shipType, type -> MODEL_FACTORIES.get(type).get());
    }

    private static void emitFaces(List<Face> faces, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight) {
        if (faces.isEmpty()) {
            return;
        }

        Map<PointKey, Vector3f> vertexPushes = buildVertexPushes(faces);
        for (Face face : faces) {
            emitFace(face, poseStack, vertexConsumer, packedLight, vertexPushes);
        }
    }

    private static void emitFace(Face face, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, Map<PointKey, Vector3f> vertexPushes) {
        PoseStack.Pose pose = poseStack.last();
        AtlasSlot slot = face.slot();
        for (Vertex vertex : face.vertices()) {
            float u = (slot.x() + vertex.localU() * slot.width()) / HeraldryPatternData.MESH_ATLAS_WIDTH;
            float v = (slot.y() + vertex.localV() * slot.height()) / HeraldryPatternData.MESH_ATLAS_HEIGHT;
            emitPaintVertex(face, pose, vertexConsumer, paintPosition(face, vertex, vertexPushes), u, v, packedLight);
        }
    }

    private static Vector3f paintPosition(Face face, Vertex vertex) {
        return new Vector3f(
                vertex.x() + face.normal().x() * PAINT_NORMAL_PUSH,
                vertex.y() + face.normal().y() * PAINT_NORMAL_PUSH,
                vertex.z() + face.normal().z() * PAINT_NORMAL_PUSH
        );
    }

    private static Vector3f paintPosition(Face face, Vertex vertex, Map<PointKey, Vector3f> vertexPushes) {
        Vector3f push = vertexPushes.get(pointKey(vertex));
        if (push == null || push.lengthSquared() < 0.00001F) {
            push = face.normal();
        }
        return new Vector3f(
                vertex.x() + push.x() * PAINT_NORMAL_PUSH,
                vertex.y() + push.y() * PAINT_NORMAL_PUSH,
                vertex.z() + push.z() * PAINT_NORMAL_PUSH
        );
    }

    private static Vector3f paintPosition(Face face, GridCorners corners, float u, float v) {
        Vector3f position = interpolate(corners, u, v);
        position.add(
                face.normal().x() * PAINT_NORMAL_PUSH,
                face.normal().y() * PAINT_NORMAL_PUSH,
                face.normal().z() * PAINT_NORMAL_PUSH
        );
        return position;
    }

    private static void emitSeamSkirts(Face face, PoseStack.Pose pose, VertexConsumer vertexConsumer, int packedLight, Set<EdgeSide> sharedSides) {
        GridCorners corners = gridCorners(face);
        AtlasSlot slot = face.slot();
        float geometryU = seamLocalOverlap(horizontalLength(corners));
        float geometryV = seamLocalOverlap(verticalLength(corners));
        float textureU = 1.0F / Math.max(1, slot.width());
        float textureV = 1.0F / Math.max(1, slot.height());

        if (!sharedSides.contains(EdgeSide.TOP)) {
            emitPaintQuad(face, pose, vertexConsumer, packedLight, corners,
                    0.0F, 0.0F, 1.0F, 0.0F, 1.0F, -geometryV, 0.0F, -geometryV,
                    0.0F, 0.0F, 1.0F, 0.0F, 1.0F, -textureV, 0.0F, -textureV);
        }
        if (!sharedSides.contains(EdgeSide.BOTTOM)) {
            emitPaintQuad(face, pose, vertexConsumer, packedLight, corners,
                    1.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F + geometryV, 1.0F, 1.0F + geometryV,
                    1.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F + textureV, 1.0F, 1.0F + textureV);
        }
        if (!sharedSides.contains(EdgeSide.LEFT)) {
            emitPaintQuad(face, pose, vertexConsumer, packedLight, corners,
                    0.0F, 1.0F, 0.0F, 0.0F, -geometryU, 0.0F, -geometryU, 1.0F,
                    0.0F, 1.0F, 0.0F, 0.0F, -textureU, 0.0F, -textureU, 1.0F);
        }
        if (!sharedSides.contains(EdgeSide.RIGHT)) {
            emitPaintQuad(face, pose, vertexConsumer, packedLight, corners,
                    1.0F, 0.0F, 1.0F, 1.0F, 1.0F + geometryU, 1.0F, 1.0F + geometryU, 0.0F,
                    1.0F, 0.0F, 1.0F, 1.0F, 1.0F + textureU, 1.0F, 1.0F + textureU, 0.0F);
        }
    }

    private static void emitSharedSeamBridge(SharedEdge sharedEdge, PoseStack.Pose pose, VertexConsumer vertexConsumer, int packedLight) {
        FaceEdge first = sharedEdge.first();
        FaceEdge second = alignedTo(sharedEdge.second(), first.from(), first.to());

        Vector3f firstFrom = paintPosition(first.face(), first.from());
        Vector3f firstTo = paintPosition(first.face(), first.to());
        Vector3f secondFrom = paintPosition(second.face(), second.from());
        Vector3f secondTo = paintPosition(second.face(), second.to());
        Vector3f middleFrom = midpoint(firstFrom, secondFrom);
        Vector3f middleTo = midpoint(firstTo, secondTo);

        emitBridgeHalf(first, pose, vertexConsumer, packedLight, firstFrom, firstTo, middleTo, middleFrom);
        emitBridgeHalf(second, pose, vertexConsumer, packedLight, secondFrom, secondTo, middleTo, middleFrom);
    }

    private static void emitBridgeHalf(
            FaceEdge edge,
            PoseStack.Pose pose,
            VertexConsumer vertexConsumer,
            int packedLight,
            Vector3f edgeFrom,
            Vector3f edgeTo,
            Vector3f bridgeTo,
            Vector3f bridgeFrom
    ) {
        AtlasSlot slot = edge.face().slot();
        float edgeFromU = textureU(slot, edge.from().localU());
        float edgeFromV = textureV(slot, edge.from().localV());
        float edgeToU = textureU(slot, edge.to().localU());
        float edgeToV = textureV(slot, edge.to().localV());
        float bridgeFromU = textureU(slot, outsideLocalU(edge.side(), edge.from(), slot));
        float bridgeFromV = textureV(slot, outsideLocalV(edge.side(), edge.from(), slot));
        float bridgeToU = textureU(slot, outsideLocalU(edge.side(), edge.to(), slot));
        float bridgeToV = textureV(slot, outsideLocalV(edge.side(), edge.to(), slot));

        emitPaintVertex(edge.face(), pose, vertexConsumer, edgeFrom, edgeFromU, edgeFromV, packedLight);
        emitPaintVertex(edge.face(), pose, vertexConsumer, edgeTo, edgeToU, edgeToV, packedLight);
        emitPaintVertex(edge.face(), pose, vertexConsumer, bridgeTo, bridgeToU, bridgeToV, packedLight);
        emitPaintVertex(edge.face(), pose, vertexConsumer, bridgeFrom, bridgeFromU, bridgeFromV, packedLight);
    }

    private static Vector3f midpoint(Vector3f first, Vector3f second) {
        return new Vector3f(first).add(second).mul(0.5F);
    }

    private static float outsideLocalU(EdgeSide side, Vertex vertex, AtlasSlot slot) {
        float step = 1.0F / Math.max(1, slot.width());
        if (side == EdgeSide.LEFT) {
            return vertex.localU() - step;
        }
        if (side == EdgeSide.RIGHT) {
            return vertex.localU() + step;
        }
        return vertex.localU();
    }

    private static float outsideLocalV(EdgeSide side, Vertex vertex, AtlasSlot slot) {
        float step = 1.0F / Math.max(1, slot.height());
        if (side == EdgeSide.TOP) {
            return vertex.localV() - step;
        }
        if (side == EdgeSide.BOTTOM) {
            return vertex.localV() + step;
        }
        return vertex.localV();
    }

    private static void emitPaintQuad(
            Face face,
            PoseStack.Pose pose,
            VertexConsumer vertexConsumer,
            int packedLight,
            GridCorners corners,
            float gu0,
            float gv0,
            float gu1,
            float gv1,
            float gu2,
            float gv2,
            float gu3,
            float gv3,
            float tu0,
            float tv0,
            float tu1,
            float tv1,
            float tu2,
            float tv2,
            float tu3,
            float tv3
    ) {
        emitPaintVertex(face, pose, vertexConsumer, paintPosition(face, corners, gu0, gv0), textureU(face.slot(), tu0), textureV(face.slot(), tv0), packedLight);
        emitPaintVertex(face, pose, vertexConsumer, paintPosition(face, corners, gu1, gv1), textureU(face.slot(), tu1), textureV(face.slot(), tv1), packedLight);
        emitPaintVertex(face, pose, vertexConsumer, paintPosition(face, corners, gu2, gv2), textureU(face.slot(), tu2), textureV(face.slot(), tv2), packedLight);
        emitPaintVertex(face, pose, vertexConsumer, paintPosition(face, corners, gu3, gv3), textureU(face.slot(), tu3), textureV(face.slot(), tv3), packedLight);
    }

    private static void emitPaintVertex(Face face, PoseStack.Pose pose, VertexConsumer vertexConsumer, Vector3f position, float u, float v, int packedLight) {
        vertexConsumer.vertex(pose.pose(), position.x(), position.y(), position.z())
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(pose.normal(), face.normal().x(), face.normal().y(), face.normal().z())
                .endVertex();
    }

    private static float textureU(AtlasSlot slot, float localU) {
        return (slot.x() + localU * slot.width()) / HeraldryPatternData.MESH_ATLAS_WIDTH;
    }

    private static float textureV(AtlasSlot slot, float localV) {
        return (slot.y() + localV * slot.height()) / HeraldryPatternData.MESH_ATLAS_HEIGHT;
    }

    private static float seamLocalOverlap(float length) {
        if (length < 0.00001F) {
            return 0.0F;
        }
        return Math.min(0.08F, PAINT_SEAM_OVERLAP / length);
    }

    private static float horizontalLength(GridCorners corners) {
        return (distance(corners.topLeft(), corners.topRight()) + distance(corners.bottomLeft(), corners.bottomRight())) * 0.5F;
    }

    private static float verticalLength(GridCorners corners) {
        return (distance(corners.topLeft(), corners.bottomLeft()) + distance(corners.topRight(), corners.bottomRight())) * 0.5F;
    }

    private static Map<PointKey, Vector3f> buildVertexPushes(List<Face> faces) {
        Map<PointKey, Vector3f> pushes = new HashMap<>();
        for (Face face : faces) {
            for (Vertex vertex : face.vertices()) {
                pushes.computeIfAbsent(pointKey(vertex), ignored -> new Vector3f()).add(face.normal());
            }
        }

        for (Vector3f push : pushes.values()) {
            if (push.lengthSquared() > 0.00001F) {
                push.normalize();
            } else {
                push.set(0.0F, 1.0F, 0.0F);
            }
        }
        return pushes;
    }

    private static EdgeSeams buildEdgeSeams(List<Face> faces) {
        Map<EdgeKey, List<FaceEdge>> edgesByPosition = new HashMap<>();
        for (Face face : faces) {
            for (FaceEdge edge : faceEdges(face)) {
                edgesByPosition.computeIfAbsent(edgeKey(edge), ignored -> new ArrayList<>()).add(edge);
            }
        }

        Map<Integer, EnumSet<EdgeSide>> sharedSides = new HashMap<>();
        List<SharedEdge> sharedEdges = new ArrayList<>();
        for (List<FaceEdge> edges : edgesByPosition.values()) {
            if (edges.size() < 2) {
                continue;
            }

            FaceEdge anchor = edges.get(0);
            markSharedSide(sharedSides, anchor);
            for (int i = 1; i < edges.size(); i++) {
                FaceEdge edge = edges.get(i);
                markSharedSide(sharedSides, edge);
                if (anchor.face().faceIndex() != edge.face().faceIndex()) {
                    sharedEdges.add(new SharedEdge(anchor, edge));
                }
            }
        }
        return new EdgeSeams(sharedSides, sharedEdges);
    }

    private static List<FaceEdge> faceEdges(Face face) {
        Vertex[] vertices = face.vertices();
        if (vertices.length < 4) {
            return List.of();
        }

        List<FaceEdge> edges = new ArrayList<>(4);
        for (int index = 0; index < vertices.length; index++) {
            Vertex from = vertices[index];
            Vertex to = vertices[(index + 1) % vertices.length];
            EdgeSide side = edgeSide(from, to);
            if (side != null) {
                edges.add(new FaceEdge(face, side, from, to));
            }
        }
        return edges;
    }

    private static EdgeSide edgeSide(Vertex first, Vertex second) {
        if (close(first.localV(), 0.0F) && close(second.localV(), 0.0F)) {
            return EdgeSide.TOP;
        }
        if (close(first.localV(), 1.0F) && close(second.localV(), 1.0F)) {
            return EdgeSide.BOTTOM;
        }
        if (close(first.localU(), 0.0F) && close(second.localU(), 0.0F)) {
            return EdgeSide.LEFT;
        }
        if (close(first.localU(), 1.0F) && close(second.localU(), 1.0F)) {
            return EdgeSide.RIGHT;
        }
        return null;
    }

    private static boolean close(float value, float target) {
        return Math.abs(value - target) < 0.0005F;
    }

    private static void markSharedSide(Map<Integer, EnumSet<EdgeSide>> sharedSides, FaceEdge edge) {
        sharedSides.computeIfAbsent(edge.face().faceIndex(), ignored -> EnumSet.noneOf(EdgeSide.class)).add(edge.side());
    }

    private static EdgeKey edgeKey(FaceEdge edge) {
        PointKey first = pointKey(edge.from());
        PointKey second = pointKey(edge.to());
        return first.compareTo(second) <= 0 ? new EdgeKey(first, second) : new EdgeKey(second, first);
    }

    private static PointKey pointKey(Vertex vertex) {
        return new PointKey(
                Math.round(vertex.x() * EDGE_KEY_SCALE),
                Math.round(vertex.y() * EDGE_KEY_SCALE),
                Math.round(vertex.z() * EDGE_KEY_SCALE)
        );
    }

    private static FaceEdge alignedTo(FaceEdge edge, Vertex from, Vertex to) {
        float sameDirection = distance(from, edge.from()) + distance(to, edge.to());
        float reverseDirection = distance(from, edge.to()) + distance(to, edge.from());
        if (reverseDirection < sameDirection) {
            return new FaceEdge(edge.face(), edge.side(), edge.to(), edge.from());
        }
        return edge;
    }

    private static void emitOriginalFace(Face face, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        for (Vertex vertex : face.vertices()) {
            vertexConsumer.vertex(pose.pose(), vertex.x(), vertex.y(), vertex.z())
                    .color(255, 255, 255, 255)
                    .uv(vertex.originalU(), vertex.originalV())
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(packedLight)
                    .normal(pose.normal(), face.normal().x(), face.normal().y(), face.normal().z())
                    .endVertex();
        }
    }

    private static void emitHighlightFace(Face face, PoseStack poseStack, VertexConsumer vertexConsumer) {
        Vertex[] vertices = face.vertices();
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[0], vertices[1]);
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[1], vertices[2]);
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[2], vertices[3]);
        emitHighlightLine(face, poseStack, vertexConsumer, vertices[3], vertices[0]);
    }

    private static void emitHighlightLine(Face face, PoseStack poseStack, VertexConsumer vertexConsumer, Vertex from, Vertex to) {
        float push = 0.028F;
        Vector3f normal = face.normal();
        Vector3f lineDirection = new Vector3f(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        if (lineDirection.lengthSquared() > 0.00001F) {
            lineDirection.normalize();
        } else {
            lineDirection.set(1.0F, 0.0F, 0.0F);
        }
        emitHighlightVertex(poseStack, vertexConsumer, from.x() + normal.x() * push, from.y() + normal.y() * push, from.z() + normal.z() * push, lineDirection);
        emitHighlightVertex(poseStack, vertexConsumer, to.x() + normal.x() * push, to.y() + normal.y() * push, to.z() + normal.z() * push, lineDirection);
    }

    private static void emitHighlightVertex(PoseStack poseStack, VertexConsumer vertexConsumer, float x, float y, float z, Vector3f lineDirection) {
        PoseStack.Pose pose = poseStack.last();
        vertexConsumer.vertex(pose.pose(), x, y, z)
                .color(95, 220, 255, 230)
                .normal(pose.normal(), lineDirection.x(), lineDirection.y(), lineDirection.z())
                .endVertex();
    }

    private static void emitGridFace(Face face, PoseStack poseStack, VertexConsumer vertexConsumer) {
        GridCorners corners = gridCorners(face);
        AtlasSlot slot = face.slot();
        for (int x = 1; x < slot.width(); x++) {
            float u = x / (float) slot.width();
            emitGridLine(face, poseStack, vertexConsumer, interpolate(corners, u, 0.0F), interpolate(corners, u, 1.0F));
        }
        for (int y = 1; y < slot.height(); y++) {
            float v = y / (float) slot.height();
            emitGridLine(face, poseStack, vertexConsumer, interpolate(corners, 0.0F, v), interpolate(corners, 1.0F, v));
        }
    }

    private static GridCorners gridCorners(Face face) {
        return new GridCorners(
                closestVertex(face, 0.0F, 0.0F),
                closestVertex(face, 1.0F, 0.0F),
                closestVertex(face, 1.0F, 1.0F),
                closestVertex(face, 0.0F, 1.0F)
        );
    }

    private static Vertex closestVertex(Face face, float targetU, float targetV) {
        Vertex best = face.vertices()[0];
        float bestDistance = Float.MAX_VALUE;
        for (Vertex vertex : face.vertices()) {
            float du = vertex.localU() - targetU;
            float dv = vertex.localV() - targetV;
            float distance = du * du + dv * dv;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = vertex;
            }
        }
        return best;
    }

    private static Vector3f interpolate(GridCorners corners, float u, float v) {
        Vector3f top = lerp(corners.topLeft(), corners.topRight(), u);
        Vector3f bottom = lerp(corners.bottomLeft(), corners.bottomRight(), u);
        return top.lerp(bottom, v);
    }

    private static Vector3f lerp(Vertex from, Vertex to, float delta) {
        return new Vector3f(from.x(), from.y(), from.z()).lerp(new Vector3f(to.x(), to.y(), to.z()), delta);
    }

    private static void emitGridLine(Face face, PoseStack poseStack, VertexConsumer vertexConsumer, Vector3f from, Vector3f to) {
        float push = 0.014F;
        Vector3f normal = face.normal();
        Vector3f lineDirection = new Vector3f(to).sub(from);
        if (lineDirection.lengthSquared() > 0.00001F) {
            lineDirection.normalize();
        } else {
            lineDirection.set(1.0F, 0.0F, 0.0F);
        }
        emitGridVertex(poseStack, vertexConsumer, from.x() + normal.x() * push, from.y() + normal.y() * push, from.z() + normal.z() * push, lineDirection);
        emitGridVertex(poseStack, vertexConsumer, to.x() + normal.x() * push, to.y() + normal.y() * push, to.z() + normal.z() * push, lineDirection);
    }

    private static void emitGridVertex(PoseStack poseStack, VertexConsumer vertexConsumer, float x, float y, float z, Vector3f lineDirection) {
        PoseStack.Pose pose = poseStack.last();
        vertexConsumer.vertex(pose.pose(), x, y, z)
                .color(255, 224, 124, 230)
                .normal(pose.normal(), lineDirection.x(), lineDirection.y(), lineDirection.z())
                .endVertex();
    }

    private static Hit hitFace(Face face, ProjectedVertex[] vertices, double mouseX, double mouseY) {
        Hit hit = hitTriangle(face, vertices[0], vertices[1], vertices[2], mouseX, mouseY);
        if (hit != null) {
            return hit;
        }
        return hitTriangle(face, vertices[0], vertices[2], vertices[3], mouseX, mouseY);
    }

    private static Hit hitTriangle(Face face, ProjectedVertex a, ProjectedVertex b, ProjectedVertex c, double mouseX, double mouseY) {
        float denominator = (b.y() - c.y()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.y() - c.y());
        if (Math.abs(denominator) < 0.00001F) {
            return null;
        }

        float px = (float) mouseX;
        float py = (float) mouseY;
        float weightA = ((b.y() - c.y()) * (px - c.x()) + (c.x() - b.x()) * (py - c.y())) / denominator;
        float weightB = ((c.y() - a.y()) * (px - c.x()) + (a.x() - c.x()) * (py - c.y())) / denominator;
        float weightC = 1.0F - weightA - weightB;
        if (weightA < -0.001F || weightB < -0.001F || weightC < -0.001F) {
            return null;
        }

        float u = weightA * a.u() + weightB * b.u() + weightC * c.u();
        float v = weightA * a.v() + weightB * b.v() + weightC * c.v();
        AtlasSlot slot = face.slot();
        int pixelX = slot.x() + clampTilePixel(Mth.floor(u * slot.width()), slot.width());
        int pixelY = slot.y() + clampTilePixel(Mth.floor(v * slot.height()), slot.height());
        return new Hit(face.state(), face.elementId(), face.faceIndex(), pixelX, pixelY);
    }

    private static void fillFacePixels(Face face, int[] meshPixels, int color) {
        AtlasSlot slot = face.slot();
        PixelVertex[] vertices = new PixelVertex[4];
        for (int i = 0; i < face.vertices().length; i++) {
            Vertex vertex = face.vertices()[i];
            vertices[i] = new PixelVertex(
                    vertex.localU() * slot.width(),
                    vertex.localV() * slot.height()
            );
        }
        fillUvTriangle(face, vertices[0], vertices[1], vertices[2], meshPixels, color);
        fillUvTriangle(face, vertices[0], vertices[2], vertices[3], meshPixels, color);
    }

    private static void fillUvTriangle(Face face, PixelVertex a, PixelVertex b, PixelVertex c, int[] meshPixels, int color) {
        AtlasSlot slot = face.slot();
        int minX = clampTilePixel(Mth.floor(Math.min(a.x(), Math.min(b.x(), c.x()))), slot.width());
        int minY = clampTilePixel(Mth.floor(Math.min(a.y(), Math.min(b.y(), c.y()))), slot.height());
        int maxX = clampTilePixel(Mth.ceil(Math.max(a.x(), Math.max(b.x(), c.x())) - 1), slot.width());
        int maxY = clampTilePixel(Mth.ceil(Math.max(a.y(), Math.max(b.y(), c.y())) - 1), slot.height());
        float denominator = (b.y() - c.y()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.y() - c.y());
        if (Math.abs(denominator) < 0.00001F) {
            fillUvBounds(face, minX, minY, maxX, maxY, meshPixels, color);
            return;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5F;
                float py = y + 0.5F;
                float weightA = ((b.y() - c.y()) * (px - c.x()) + (c.x() - b.x()) * (py - c.y())) / denominator;
                float weightB = ((c.y() - a.y()) * (px - c.x()) + (a.x() - c.x()) * (py - c.y())) / denominator;
                float weightC = 1.0F - weightA - weightB;
                if (weightA >= -0.001F && weightB >= -0.001F && weightC >= -0.001F) {
                    setMeshPixel(meshPixels, face, x, y, color);
                }
            }
        }
    }

    private static void fillUvBounds(Face face, int minX, int minY, int maxX, int maxY, int[] meshPixels, int color) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                setMeshPixel(meshPixels, face, x, y, color);
            }
        }
    }

    private static void setMeshPixel(int[] meshPixels, int faceIndex, int x, int y, int color) {
        setMeshPixel(meshPixels, null, faceIndex, x, y, color);
    }

    private static void setMeshPixel(int[] meshPixels, Face face, int x, int y, int color) {
        setMeshPixel(meshPixels, face.slot(), face.faceIndex(), x, y, color);
    }

    private static void setMeshPixel(int[] meshPixels, AtlasSlot slot, int faceIndex, int x, int y, int color) {
        AtlasSlot resolvedSlot = slot == null ? fixedSlot(faceIndex) : slot;
        int localX = clampTilePixel(x, resolvedSlot.width());
        int localY = clampTilePixel(y, resolvedSlot.height());
        int atlasX = resolvedSlot.x() + localX;
        int atlasY = resolvedSlot.y() + localY;
        int minX = localX == 0 ? -TILE_PADDING : 0;
        int maxX = localX == resolvedSlot.width() - 1 ? TILE_PADDING : 0;
        int minY = localY == 0 ? -TILE_PADDING : 0;
        int maxY = localY == resolvedSlot.height() - 1 ? TILE_PADDING : 0;
        for (int dy = minY; dy <= maxY; dy++) {
            for (int dx = minX; dx <= maxX; dx++) {
                setAtlasPixel(meshPixels, atlasX + dx, atlasY + dy, color);
            }
        }
    }

    private static void setAtlasPixel(int[] meshPixels, int x, int y, int color) {
        if (x < 0 || x >= HeraldryPatternData.MESH_ATLAS_WIDTH || y < 0 || y >= HeraldryPatternData.MESH_ATLAS_HEIGHT) {
            return;
        }

        int index = y * HeraldryPatternData.MESH_ATLAS_WIDTH + x;
        if (index >= 0 && index < meshPixels.length) {
            meshPixels[index] = color;
        }
    }

    private static float averageDepth(Face face) {
        float depth = 0.0F;
        for (Vertex vertex : face.vertices()) {
            depth += vertex.z();
        }
        return depth / face.vertices().length;
    }

    private static int clampMeshTilePixel(int value) {
        return Math.max(0, Math.min(HeraldryPatternData.MESH_FACE_SIZE - 1, value));
    }

    private static int clampTilePixel(int value, int size) {
        return Math.max(0, Math.min(Math.max(1, size) - 1, value));
    }

    private static int meshTileX(int faceIndex) {
        return (faceIndex % HeraldryPatternData.MESH_ATLAS_COLUMNS) * HeraldryPatternData.MESH_FACE_SIZE;
    }

    private static int meshTileY(int faceIndex) {
        return (faceIndex / HeraldryPatternData.MESH_ATLAS_COLUMNS) * HeraldryPatternData.MESH_FACE_SIZE;
    }

    private static AtlasSlot fixedSlot(int faceIndex) {
        return new AtlasSlot(faceIndex, meshTileX(faceIndex), meshTileY(faceIndex), HeraldryPatternData.MESH_FACE_SIZE, HeraldryPatternData.MESH_FACE_SIZE);
    }

    private static int clampTextureX(int value) {
        return Math.max(0, Math.min(HeraldryPatternData.WIDTH - 1, value));
    }

    private static int clampTextureY(int value) {
        return Math.max(0, Math.min(HeraldryPatternData.HEIGHT - 1, value));
    }

    private static Definition definition(ShipPreviewType shipType) {
        return DEFINITIONS.computeIfAbsent(shipType, PaintableSailMesh::buildDefinition);
    }

    private static Definition buildDefinition(ShipPreviewType shipType) {
        Ship previewShip = PreviewShipFactory.create(shipType, HeraldryPatternData.blank().withShipType(shipType));
        if (previewShip == null) {
            return Definition.empty();
        }

        SailModel sailModel = MODEL_FACTORIES.get(shipType).get();
        TreeMap<String, TileSize> faceTiles = new TreeMap<>();
        Map<String, Element> elements = new LinkedHashMap<>();
        Map<Integer, Map<String, Element>> elementsByState = new HashMap<>();
        for (int state = 0; state <= 4; state++) {
            previewShip.setData(Ship.SAIL_STATE, (byte) state);
            sailModel.setupAnim(previewShip, 1.0F, 0.0F, -0.1F, 0.0F, 0.0F);
            List<RawFace> rawFaces = collectRawFaces(sailModel, new PoseStack(), state);
            for (RawFace rawFace : rawFaces) {
                if (!isPaintableFace(rawFace)) {
                    continue;
                }
                faceTiles.put(rawFace.key(), tileSize(rawFace));
                elements.putIfAbsent(rawFace.elementId(), new Element(rawFace.elementId(), readableName(rawFace.elementId())));
                elementsByState.computeIfAbsent(state, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(rawFace.elementId(), new Element(rawFace.elementId(), readableName(rawFace.elementId())));
            }
        }

        Map<String, AtlasSlot> faceSlotsByKey = packAtlasSlots(faceTiles, shipType);

        Map<Integer, List<Face>> facesByState = new HashMap<>();
        for (int state = 0; state <= 4; state++) {
            previewShip.setData(Ship.SAIL_STATE, (byte) state);
            sailModel.setupAnim(previewShip, 1.0F, 0.0F, -0.1F, 0.0F, 0.0F);
            List<Face> stateFaces = new ArrayList<>();
            for (RawFace rawFace : collectRawFaces(sailModel, new PoseStack(), state)) {
                AtlasSlot slot = faceSlotsByKey.get(rawFace.key());
                if (slot != null && isPaintableFace(rawFace)) {
                    stateFaces.add(rawFace.toFace(slot));
                }
            }
            facesByState.put(state, List.copyOf(stateFaces));
        }

        Map<Integer, List<Element>> immutableElementsByState = new HashMap<>();
        for (Map.Entry<Integer, Map<String, Element>> entry : elementsByState.entrySet()) {
            immutableElementsByState.put(entry.getKey(), List.copyOf(entry.getValue().values()));
        }

        return new Definition(faceSlotsByKey, List.copyOf(elements.values()), Map.copyOf(immutableElementsByState), Map.copyOf(facesByState));
    }

    private static Map<String, AtlasSlot> packAtlasSlots(TreeMap<String, TileSize> faceTiles, ShipPreviewType shipType) {
        List<Map.Entry<String, TileSize>> entries = new ArrayList<>(faceTiles.entrySet());
        entries.sort(Comparator
                .<Map.Entry<String, TileSize>>comparingInt(entry -> entry.getValue().height()).reversed()
                .thenComparing(Comparator.<Map.Entry<String, TileSize>>comparingInt(entry -> entry.getValue().width()).reversed())
                .thenComparing(Map.Entry::getKey));

        Map<String, AtlasSlot> slots = new HashMap<>();
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        int index = 0;
        int skipped = 0;
        for (Map.Entry<String, TileSize> entry : entries) {
            TileSize size = entry.getValue();
            int occupiedWidth = size.width() + TILE_PADDING * 2;
            int occupiedHeight = size.height() + TILE_PADDING * 2;
            if (x + occupiedWidth > HeraldryPatternData.MESH_ATLAS_WIDTH) {
                x = 0;
                y += rowHeight;
                rowHeight = 0;
            }
            if (y + occupiedHeight > HeraldryPatternData.MESH_ATLAS_HEIGHT) {
                skipped++;
                continue;
            }

            slots.put(entry.getKey(), new AtlasSlot(index++, x + TILE_PADDING, y + TILE_PADDING, size.width(), size.height()));
            x += occupiedWidth;
            rowHeight = Math.max(rowHeight, occupiedHeight);
        }

        if (skipped > 0) {
            SmallShipsHeraldry.LOGGER.warn("Skipped {} paintable sail faces for {} because the dynamic paint atlas is full", skipped, shipType.serializedName());
        }
        return slots;
    }

    private static TileSize tileSize(RawFace face) {
        Vertex[] vertices = face.vertices();
        if (vertices.length < 4) {
            return new TileSize(HeraldryPatternData.MESH_FACE_SIZE, HeraldryPatternData.MESH_FACE_SIZE);
        }

        float width = distance(vertices[0], vertices[1]);
        float height = distance(vertices[1], vertices[2]);
        if (width < 0.0001F || height < 0.0001F) {
            return new TileSize(HeraldryPatternData.MESH_FACE_SIZE, HeraldryPatternData.MESH_FACE_SIZE);
        }

        int maxTileSize = HeraldryPatternData.MESH_FACE_SIZE * 6;
        int tileWidth = Mth.clamp(Mth.ceil(width * PAINT_PIXELS_PER_MODEL_UNIT), 1, maxTileSize);
        int tileHeight = Mth.clamp(Mth.ceil(height * PAINT_PIXELS_PER_MODEL_UNIT), 1, maxTileSize);
        return new TileSize(tileWidth, tileHeight);
    }

    private static float distance(Vertex a, Vertex b) {
        float x = a.x() - b.x();
        float y = a.y() - b.y();
        float z = a.z() - b.z();
        return Mth.sqrt(x * x + y * y + z * z);
    }

    private static List<Face> collectFaces(Ship ship, SailModel sailModel, PoseStack poseStack, Definition definition, int state) {
        List<Face> faces = new ArrayList<>();
        for (RawFace rawFace : collectRawFaces(sailModel, poseStack, state)) {
            AtlasSlot slot = definition.faceSlot(rawFace.key());
            if (slot != null && isPaintableFace(rawFace)) {
                faces.add(rawFace.toFace(slot));
            }
        }
        return faces;
    }

    private static List<RawFace> collectRawFaces(SailModel sailModel, PoseStack poseStack, int state) {
        ModelPart root = rootPart(sailModel);
        if (root == null) {
            return List.of();
        }

        List<RawFace> faces = new ArrayList<>();
        collectVisibleRawFaces(root, poseStack, "", state, faces);
        return faces;
    }

    private static void collectVisibleRawFaces(ModelPart part, PoseStack poseStack, String path, int state, List<RawFace> faces) {
        if (!part.visible) {
            return;
        }

        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        String elementId = normalizePath(path);

        if (!part.skipDraw) {
            List<ModelPart.Cube> cubes = cubes(part);
            for (int cubeIndex = 0; cubeIndex < cubes.size(); cubeIndex++) {
                cubes.get(cubeIndex).compile(
                        poseStack.last(),
                        new CubeFaceCollector(state, elementId, cubeIndex, faces),
                        0,
                        OverlayTexture.NO_OVERLAY,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F
                );
            }
        }

        for (Map.Entry<String, ModelPart> entry : children(part).entrySet()) {
            String childPath = path.isBlank() ? entry.getKey() : path + "/" + entry.getKey();
            collectVisibleRawFaces(entry.getValue(), poseStack, childPath, state, faces);
        }
        poseStack.popPose();
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart part) {
        if (MODEL_PART_CUBES == null) {
            return List.of();
        }
        try {
            return (List<ModelPart.Cube>) MODEL_PART_CUBES.get(part);
        } catch (IllegalAccessException exception) {
            SmallShipsHeraldry.LOGGER.warn("Failed to read ModelPart cubes", exception);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart part) {
        if (MODEL_PART_CHILDREN == null) {
            return Map.of();
        }
        try {
            return (Map<String, ModelPart>) MODEL_PART_CHILDREN.get(part);
        } catch (IllegalAccessException exception) {
            SmallShipsHeraldry.LOGGER.warn("Failed to read ModelPart children", exception);
            return Map.of();
        }
    }

    private static Field findModelPartField(Class<?> fieldType) {
        for (Field field : ModelPart.class.getDeclaredFields()) {
            if (fieldType.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        SmallShipsHeraldry.LOGGER.warn("Could not find ModelPart {} field for sail mesh baking", fieldType.getSimpleName());
        return null;
    }

    private static boolean isPaintableFace(RawFace face) {
        return isSailOrRopeElement(face.elementId());
    }

    private static boolean hasSailLikeUv(Vertex[] vertices) {
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (Vertex vertex : vertices) {
            minU = Math.min(minU, vertex.originalU());
            maxU = Math.max(maxU, vertex.originalU());
            minV = Math.min(minV, vertex.originalV());
            maxV = Math.max(maxV, vertex.originalV());
        }
        float centerU = (minU + maxU) * 0.5F * HeraldryPatternData.WIDTH;
        float centerV = (minV + maxV) * 0.5F * HeraldryPatternData.HEIGHT;
        return centerU >= 78.0F && centerV >= 3.0F && centerV <= 42.0F;
    }

    private static boolean isSailOrRopeElement(String elementId) {
        String normalized = elementId.toLowerCase(Locale.ROOT);
        if (normalized.contains("mast")) {
            return false;
        }
        return normalized.contains("sail")
                || normalized.contains("segel")
                || normalized.contains("rope")
                || normalized.contains("base");
    }

    private static ModelPart rootPart(SailModel sailModel) {
        for (Field field : sailModel.getClass().getDeclaredFields()) {
            if (ModelPart.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return (ModelPart) field.get(sailModel);
                } catch (IllegalAccessException exception) {
                    SmallShipsHeraldry.LOGGER.warn("Failed to access Small Ships sail root part", exception);
                }
            }
        }
        return null;
    }

    private static int currentSailState(Ship ship) {
        return ship.getData(Ship.SAIL_STATE);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "root";
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.isBlank() ? "root" : normalized;
    }

    private static String readableName(String elementId) {
        String[] parts = elementId.split("/");
        if (parts.length == 0) {
            return elementId;
        }
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    private static final class CubeFaceCollector implements VertexConsumer {
        private final int state;
        private final String elementId;
        private final int cubeIndex;
        private final List<RawFace> faces;
        private final List<CapturedVertex> vertices = new ArrayList<>(4);
        private CapturedVertex current;
        private int polygonIndex;

        private CubeFaceCollector(int state, String elementId, int cubeIndex, List<RawFace> faces) {
            this.state = state;
            this.elementId = elementId;
            this.cubeIndex = cubeIndex;
            this.faces = faces;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            current = new CapturedVertex((float) x, (float) y, (float) z);
            return this;
        }

        @Override
        public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
            Vector3f pos = new Vector3f(x, y, z);
            matrix.transformPosition(pos);
            current = new CapturedVertex(pos.x(), pos.y(), pos.z());
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            if (current != null) {
                current.u = u;
                current.v = v;
            }
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            if (current != null) {
                current.normal.set(x, y, z);
            }
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
            Vector3f normal = new Vector3f(x, y, z);
            matrix.transform(normal);
            return normal(normal.x(), normal.y(), normal.z());
        }

        @Override
        public void endVertex() {
            if (current == null) {
                return;
            }
            vertices.add(current);
            current = null;
            if (vertices.size() == 4) {
                flushFace();
                vertices.clear();
            }
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }

        private void flushFace() {
            float minU = Float.MAX_VALUE;
            float minV = Float.MAX_VALUE;
            float maxU = -Float.MAX_VALUE;
            float maxV = -Float.MAX_VALUE;
            Vector3f normal = new Vector3f();
            for (CapturedVertex vertex : vertices) {
                minU = Math.min(minU, vertex.u);
                minV = Math.min(minV, vertex.v);
                maxU = Math.max(maxU, vertex.u);
                maxV = Math.max(maxV, vertex.v);
                normal.add(vertex.normal);
            }

            float uSpan = Math.max(0.0001F, maxU - minU);
            float vSpan = Math.max(0.0001F, maxV - minV);
            Vertex[] normalizedVertices = new Vertex[4];
            for (int i = 0; i < vertices.size(); i++) {
                CapturedVertex vertex = vertices.get(i);
                normalizedVertices[i] = new Vertex(
                        vertex.x,
                        vertex.y,
                        vertex.z,
                        vertex.u,
                        vertex.v,
                        (vertex.u - minU) / uSpan,
                        (vertex.v - minV) / vSpan
                );
            }

            if (normal.lengthSquared() > 0.00001F) {
                normal.normalize();
            } else {
                normal.set(0.0F, 1.0F, 0.0F);
            }

            String key = state + "|" + elementId + "|" + cubeIndex + "|" + polygonIndex++;
            faces.add(new RawFace(key, state, elementId, normalizedVertices, normal));
        }
    }

    private static final class CapturedVertex {
        private final float x;
        private final float y;
        private final float z;
        private float u;
        private float v;
        private final Vector3f normal = new Vector3f(0.0F, 1.0F, 0.0F);

        private CapturedVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public record Element(String id, String label) {
    }

    public record Hit(int stage, String elementId, int faceIndex, int pixelX, int pixelY) {
    }

    private record ProjectedVertex(float x, float y, float depth, float u, float v) {
    }

    private record PixelVertex(float x, float y) {
    }

    private enum EdgeSide {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    private record PointKey(int x, int y, int z) implements Comparable<PointKey> {
        @Override
        public int compareTo(PointKey other) {
            int xCompare = Integer.compare(x, other.x);
            if (xCompare != 0) {
                return xCompare;
            }
            int yCompare = Integer.compare(y, other.y);
            if (yCompare != 0) {
                return yCompare;
            }
            return Integer.compare(z, other.z);
        }
    }

    private record EdgeKey(PointKey first, PointKey second) {
    }

    private record FaceEdge(Face face, EdgeSide side, Vertex from, Vertex to) {
    }

    private record SharedEdge(FaceEdge first, FaceEdge second) {
    }

    private record EdgeSeams(Map<Integer, EnumSet<EdgeSide>> sharedSides, List<SharedEdge> sharedEdges) {
        private Set<EdgeSide> sharedSides(int faceIndex) {
            return sharedSides.getOrDefault(faceIndex, EnumSet.noneOf(EdgeSide.class));
        }
    }

    private record GridCorners(Vertex topLeft, Vertex topRight, Vertex bottomRight, Vertex bottomLeft) {
    }

    private record Vertex(float x, float y, float z, float originalU, float originalV, float localU, float localV) {
    }

    private record RawFace(String key, int state, String elementId, Vertex[] vertices, Vector3f normal) {
        private Face toFace(AtlasSlot slot) {
            return new Face(slot.index(), state, elementId, vertices, normal, slot);
        }
    }

    private record Face(int faceIndex, int state, String elementId, Vertex[] vertices, Vector3f normal, AtlasSlot slot) {
        private void addTo(Bounds bounds) {
            for (Vertex vertex : vertices) {
                bounds.include(vertex.x(), vertex.y(), vertex.z());
            }
        }
    }

    private record EditorPickCache(int shipIdentity, int state, List<Face> faces) {
    }

    private record AtlasSlot(int index, int x, int y, int width, int height) {
    }

    private record TileSize(int width, int height) {
    }

    private record Definition(Map<String, AtlasSlot> faceSlotsByKey, List<Element> elements, Map<Integer, List<Element>> elementsByState, Map<Integer, List<Face>> facesByState) {
        private static Definition empty() {
            return new Definition(Map.of(), List.of(), Map.of(), Map.of());
        }

        private boolean isEmpty() {
            return faceSlotsByKey.isEmpty();
        }

        private AtlasSlot faceSlot(String key) {
            return faceSlotsByKey.get(key);
        }

        private List<Element> elementsForState(int state) {
            return elementsByState.getOrDefault(state, elements);
        }

        private List<Face> facesForState(int state) {
            return facesByState.getOrDefault(Mth.clamp(state, 0, HeraldryPatternData.STAGE_COUNT - 1), List.of());
        }
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
