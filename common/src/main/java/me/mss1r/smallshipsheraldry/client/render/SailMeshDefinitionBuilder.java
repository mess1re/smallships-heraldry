package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talhanation.smallships.client.model.sail.BriggSailModel;
import com.talhanation.smallships.client.model.sail.CogSailModel;
import com.talhanation.smallships.client.model.sail.DrakkarSailModel;
import com.talhanation.smallships.client.model.sail.GalleySailModel;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

final class SailMeshDefinitionBuilder {
    private static final Map<ShipPreviewType, Supplier<SailModel>> MODEL_FACTORIES = Map.of(
            ShipPreviewType.COG, CogSailModel::new,
            ShipPreviewType.BRIGG, BriggSailModel::new,
            ShipPreviewType.GALLEY, GalleySailModel::new,
            ShipPreviewType.DRAKKAR, DrakkarSailModel::new
    );
    private static final Map<ShipPreviewType, Definition> DEFINITIONS = new HashMap<>();
    private static final Field MODEL_PART_CUBES = findModelPartField(List.class);
    private static final Field MODEL_PART_CHILDREN = findModelPartField(Map.class);
    private static final int TILE_PADDING = 1;

    private SailMeshDefinitionBuilder() {
    }

    static Definition definition(ShipPreviewType shipType) {
        return DEFINITIONS.computeIfAbsent(shipType, SailMeshDefinitionBuilder::buildDefinition);
    }

    static SailModel createModel(ShipPreviewType shipType) {
        Supplier<SailModel> factory = MODEL_FACTORIES.get(shipType);
        return factory == null ? null : factory.get();
    }

    static List<PaintableSailMesh.Face> collectFaces(
            SailModel sailModel,
            PoseStack poseStack,
            Definition definition,
            int state
    ) {
        List<PaintableSailMesh.Face> faces = new ArrayList<>();
        for (RawFace rawFace : collectRawFaces(sailModel, poseStack, state)) {
            PaintableSailMesh.AtlasSlot slot = definition.faceSlot(rawFace.key());
            if (slot != null && isPaintableFace(rawFace)) {
                faces.add(rawFace.toFace(slot));
            }
        }
        return faces;
    }

    private static Definition buildDefinition(ShipPreviewType shipType) {
        Ship previewShip = PreviewShipFactory.create(shipType, HeraldryPatternData.blank().withShipType(shipType));
        SailModel sailModel = createModel(shipType);
        if (previewShip == null || sailModel == null) {
            return Definition.empty();
        }

        TreeMap<String, TileSize> faceTiles = new TreeMap<>();
        Map<String, PaintableSailMesh.Element> elements = new LinkedHashMap<>();
        Map<Integer, Map<String, PaintableSailMesh.Element>> elementsByState = new HashMap<>();
        for (int state = 0; state < HeraldryPatternData.STAGE_COUNT; state++) {
            previewShip.setData(Ship.SAIL_STATE, (byte) state);
            sailModel.setupAnim(previewShip, 1.0F, 0.0F, -0.1F, 0.0F, 0.0F);
            for (RawFace rawFace : collectRawFaces(sailModel, new PoseStack(), state)) {
                if (!isPaintableFace(rawFace)) {
                    continue;
                }
                faceTiles.put(rawFace.key(), tileSize(rawFace));
                PaintableSailMesh.Element element = new PaintableSailMesh.Element(rawFace.elementId(), readableName(rawFace.elementId()));
                elements.putIfAbsent(rawFace.elementId(), element);
                elementsByState.computeIfAbsent(state, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(rawFace.elementId(), element);
            }
        }

        Map<String, PaintableSailMesh.AtlasSlot> faceSlots = packAtlasSlots(faceTiles, shipType);
        Map<Integer, List<PaintableSailMesh.Face>> facesByState = new HashMap<>();
        for (int state = 0; state < HeraldryPatternData.STAGE_COUNT; state++) {
            previewShip.setData(Ship.SAIL_STATE, (byte) state);
            sailModel.setupAnim(previewShip, 1.0F, 0.0F, -0.1F, 0.0F, 0.0F);
            List<PaintableSailMesh.Face> stateFaces = new ArrayList<>();
            for (RawFace rawFace : collectRawFaces(sailModel, new PoseStack(), state)) {
                PaintableSailMesh.AtlasSlot slot = faceSlots.get(rawFace.key());
                if (slot != null && isPaintableFace(rawFace)) {
                    stateFaces.add(rawFace.toFace(slot));
                }
            }
            facesByState.put(state, List.copyOf(stateFaces));
        }

        Map<Integer, List<PaintableSailMesh.Element>> immutableElementsByState = new HashMap<>();
        for (Map.Entry<Integer, Map<String, PaintableSailMesh.Element>> entry : elementsByState.entrySet()) {
            immutableElementsByState.put(entry.getKey(), List.copyOf(entry.getValue().values()));
        }
        return new Definition(faceSlots, List.copyOf(elements.values()), Map.copyOf(immutableElementsByState), Map.copyOf(facesByState));
    }

    private static Map<String, PaintableSailMesh.AtlasSlot> packAtlasSlots(
            TreeMap<String, TileSize> faceTiles,
            ShipPreviewType shipType
    ) {
        List<Map.Entry<String, TileSize>> entries = new ArrayList<>(faceTiles.entrySet());
        entries.sort(Comparator
                .<Map.Entry<String, TileSize>>comparingInt(entry -> entry.getValue().height()).reversed()
                .thenComparing(Comparator.<Map.Entry<String, TileSize>>comparingInt(entry -> entry.getValue().width()).reversed())
                .thenComparing(Map.Entry::getKey));

        Map<String, PaintableSailMesh.AtlasSlot> slots = new HashMap<>();
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
            slots.put(entry.getKey(), new PaintableSailMesh.AtlasSlot(index++, x + TILE_PADDING, y + TILE_PADDING, size.width(), size.height()));
            x += occupiedWidth;
            rowHeight = Math.max(rowHeight, occupiedHeight);
        }
        if (skipped > 0) {
            SmallShipsHeraldry.LOGGER.warn("Skipped {} paintable sail faces for {} because the dynamic paint atlas is full", skipped, shipType.serializedName());
        }
        return slots;
    }

    private static TileSize tileSize(RawFace face) {
        PaintableSailMesh.Vertex[] vertices = face.vertices();
        if (vertices.length < 4) {
            return new TileSize(HeraldryPatternData.MESH_FACE_SIZE, HeraldryPatternData.MESH_FACE_SIZE);
        }
        float width = distance(vertices[0], vertices[1]);
        float height = distance(vertices[1], vertices[2]);
        if (width < 0.0001F || height < 0.0001F) {
            return new TileSize(HeraldryPatternData.MESH_FACE_SIZE, HeraldryPatternData.MESH_FACE_SIZE);
        }
        int maxTileSize = HeraldryPatternData.MESH_FACE_SIZE * 6;
        return new TileSize(
                Mth.clamp(Mth.ceil(width * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT), 1, maxTileSize),
                Mth.clamp(Mth.ceil(height * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT), 1, maxTileSize)
        );
    }

    private static float distance(PaintableSailMesh.Vertex first, PaintableSailMesh.Vertex second) {
        float x = first.x() - second.x();
        float y = first.y() - second.y();
        float z = first.z() - second.z();
        return Mth.sqrt(x * x + y * y + z * z);
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
                        poseStack.last(), new CubeFaceCollector(state, elementId, cubeIndex, faces),
                        0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF
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
        String normalized = face.elementId().toLowerCase(Locale.ROOT);
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

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "root";
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.isBlank() ? "root" : normalized;
    }

    private static String readableName(String elementId) {
        String[] parts = elementId.split("/");
        if (parts.length <= 1) {
            return parts.length == 0 ? elementId : parts[0];
        }
        return parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    private static final class CubeFaceCollector implements VertexConsumer {
        private final int state;
        private final String elementId;
        private final int cubeIndex;
        private final List<RawFace> faces;
        private final List<CapturedVertex> vertices = new ArrayList<>(4);
        private int polygonIndex;

        private CubeFaceCollector(int state, String elementId, int cubeIndex, List<RawFace> faces) {
            this.state = state;
            this.elementId = elementId;
            this.cubeIndex = cubeIndex;
            this.faces = faces;
        }

        @Override
        public void addVertex(
                float x,
                float y,
                float z,
                int color,
                float u,
                float v,
                int overlay,
                int light,
                float normalX,
                float normalY,
                float normalZ
        ) {
            CapturedVertex vertex = new CapturedVertex(x, y, z);
            vertex.u = u;
            vertex.v = v;
            vertex.normal.set(normalX, normalY, normalZ);
            vertices.add(vertex);
            if (vertices.size() == 4) {
                flushFace();
                vertices.clear();
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
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
            PaintableSailMesh.Vertex[] normalizedVertices = new PaintableSailMesh.Vertex[4];
            for (int index = 0; index < vertices.size(); index++) {
                CapturedVertex vertex = vertices.get(index);
                normalizedVertices[index] = new PaintableSailMesh.Vertex(
                        vertex.x, vertex.y, vertex.z, vertex.u, vertex.v,
                        (vertex.u - minU) / uSpan, (vertex.v - minV) / vSpan
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

    private record RawFace(
            String key,
            int state,
            String elementId,
            PaintableSailMesh.Vertex[] vertices,
            Vector3f normal
    ) {
        private PaintableSailMesh.Face toFace(PaintableSailMesh.AtlasSlot slot) {
            return new PaintableSailMesh.Face(slot.index(), state, elementId, vertices, normal, slot);
        }
    }

    private record TileSize(int width, int height) {
    }

    record Definition(
            Map<String, PaintableSailMesh.AtlasSlot> faceSlotsByKey,
            List<PaintableSailMesh.Element> elements,
            Map<Integer, List<PaintableSailMesh.Element>> elementsByState,
            Map<Integer, List<PaintableSailMesh.Face>> facesByState
    ) {
        private static Definition empty() {
            return new Definition(Map.of(), List.of(), Map.of(), Map.of());
        }

        boolean isEmpty() {
            return faceSlotsByKey.isEmpty();
        }

        PaintableSailMesh.AtlasSlot faceSlot(String key) {
            return faceSlotsByKey.get(key);
        }

        List<PaintableSailMesh.Element> elementsForState(int state) {
            return elementsByState.getOrDefault(state, elements);
        }

        List<PaintableSailMesh.Face> facesForState(int state) {
            return facesByState.getOrDefault(Mth.clamp(state, 0, HeraldryPatternData.STAGE_COUNT - 1), List.of());
        }
    }
}
