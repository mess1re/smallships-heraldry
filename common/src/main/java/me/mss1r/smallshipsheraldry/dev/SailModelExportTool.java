package me.mss1r.smallshipsheraldry.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talhanation.smallships.client.model.sail.BriggSailModel;
import com.talhanation.smallships.client.model.sail.CogSailModel;
import com.talhanation.smallships.client.model.sail.DrakkarSailModel;
import com.talhanation.smallships.client.model.sail.GalleySailModel;
import com.talhanation.smallships.client.model.sail.SailModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class SailModelExportTool {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Field MODEL_PART_CUBES = findModelPartField(List.class);
    private static final Field MODEL_PART_CHILDREN = findModelPartField(Map.class);

    private SailModelExportTool() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = args.length == 0 ? Path.of("exports", "blockbench") : Path.of(args[0]);
        Files.createDirectories(outputDir);

        Map<String, Supplier<SailModel>> models = Map.of(
                "cog", CogSailModel::new,
                "brigg", BriggSailModel::new,
                "galley", GalleySailModel::new,
                "drakkar", DrakkarSailModel::new
        );

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", "smallshipsheraldry:sail_paint_manifest");
        manifest.addProperty("version", 1);
        JsonObject ships = new JsonObject();
        manifest.add("ships", ships);

        for (Map.Entry<String, Supplier<SailModel>> entry : models.entrySet()) {
            String ship = entry.getKey();
            SailModel model = entry.getValue().get();
            ModelPart root = rootPart(model);
            if (root == null) {
                continue;
            }

            Export export = new Export(ship);
            collect(root, new PoseStack(), "", export);
            Files.writeString(outputDir.resolve(ship + "_sails.obj"), export.toObj(), StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve(ship + "_sails.bbmodel"), GSON.toJson(export.toBlockbenchModel(ship)), StandardCharsets.UTF_8);

            JsonArray elements = new JsonArray();
            for (Map.Entry<String, ElementStats> element : export.elements.entrySet()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", element.getKey());
                item.addProperty("paintable", defaultPaintable(element.getKey()));
                item.addProperty("note", defaultPaintable(element.getKey()) ? "review: cloth/rope candidate" : "review: hardware/rigging candidate");
                item.addProperty("cubeCount", element.getValue().cubeCount);
                item.addProperty("faceCount", element.getValue().faceCount);
                item.addProperty("stage", element.getValue().stage);
                JsonArray cubes = new JsonArray();
                for (String cube : element.getValue().cubes) {
                    cubes.add(cube);
                }
                item.add("cubes", cubes);
                elements.add(item);
            }
            JsonObject shipObject = new JsonObject();
            shipObject.add("elements", elements);
            ships.add(ship, shipObject);
        }

        Files.writeString(outputDir.resolve("sail_paint_manifest.json"), GSON.toJson(manifest), StandardCharsets.UTF_8);
        System.out.println("Exported sail OBJ files and manifest to " + outputDir.toAbsolutePath());
    }

    private static void collect(ModelPart part, PoseStack poseStack, String path, Export export) {
        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        String elementId = path.isBlank() ? "root" : path;

        List<ModelPart.Cube> cubes = cubes(part);
        for (int cubeIndex = 0; cubeIndex < cubes.size(); cubeIndex++) {
            String objectName = sanitizeObjName(elementId + "__cube_" + cubeIndex);
            export.beginObject(objectName, elementId, cubeIndex);
            cubes.get(cubeIndex).compile(
                    poseStack.last(),
                    new ObjCollector(export),
                    0,
                    OverlayTexture.NO_OVERLAY,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
            export.endCube(elementId);
        }

        for (Map.Entry<String, ModelPart> entry : children(part).entrySet()) {
            String childPath = path.isBlank() ? entry.getKey() : path + "/" + entry.getKey();
            collect(entry.getValue(), poseStack, childPath, export);
        }
        poseStack.popPose();
    }

    private static boolean defaultPaintable(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (normalized.contains("mast")) {
            return false;
        }
        return normalized.contains("segel")
                || normalized.contains("sail")
                || normalized.contains("base")
                || normalized.contains("rope");
    }

    private static String sanitizeObjName(String value) {
        return value.replace('/', '_').replace(' ', '_');
    }

    private static int stageOf(String elementId) {
        String[] parts = elementId.split("/");
        for (String part : parts) {
            if (part.length() == 6
                    && part.startsWith("Sail_")
                    && part.charAt(5) >= '0'
                    && part.charAt(5) <= '4') {
                return part.charAt(5) - '0';
            }
        }
        return -1;
    }

    private static List<String> reviewGroupPath(String elementId) {
        String[] parts = elementId.split("/");
        int stage = stageOf(elementId);
        List<String> result = new ArrayList<>();
        if (stage < 0) {
            result.add("Shared / outside stages");
            for (String part : parts) {
                if (!part.isBlank() && !"root".equals(part)) {
                    result.add(part);
                }
            }
            return result;
        }

        result.add("Stage " + stage + " (editor stage " + (stage + 1) + ")");
        boolean afterStage = false;
        for (String part : parts) {
            if (!afterStage) {
                afterStage = part.equals("Sail_" + stage);
                continue;
            }
            if (!part.isBlank()) {
                result.add(part);
            }
        }
        return result;
    }

    private static ModelPart rootPart(SailModel sailModel) {
        for (Field field : sailModel.getClass().getDeclaredFields()) {
            if (ModelPart.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return (ModelPart) field.get(sailModel);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Failed to read sail root", exception);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart part) {
        if (MODEL_PART_CUBES == null) {
            return List.of();
        }
        try {
            return (List<ModelPart.Cube>) MODEL_PART_CUBES.get(part);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to read ModelPart cubes", exception);
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
            throw new IllegalStateException("Failed to read ModelPart children", exception);
        }
    }

    private static Field findModelPartField(Class<?> fieldType) {
        for (Field field : ModelPart.class.getDeclaredFields()) {
            if (fieldType.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static final class Export {
        private final String ship;
        private final StringBuilder obj = new StringBuilder();
        private final Map<String, ElementStats> elements = new LinkedHashMap<>();
        private final List<Integer> currentFace = new ArrayList<>(4);
        private final JsonArray blockbenchElements = new JsonArray();
        private final Map<String, GroupNode> topLevelGroups = new LinkedHashMap<>();
        private String currentElement = "root";
        private MeshElement currentMesh;

        private Export(String ship) {
            this.ship = ship;
            obj.append("# Small Ships Heraldry sail export for Blockbench review\n");
            obj.append("# Import this OBJ into Blockbench, then edit sail_paint_manifest.json paintable flags.\n");
        }

        private void beginObject(String name, String elementId, int cubeIndex) {
            currentElement = elementId;
            ElementStats stats = elements.computeIfAbsent(elementId, ignored -> new ElementStats(stageOf(elementId)));
            stats.cubeCount++;
            String cubeName = elementId + "/cube_" + cubeIndex;
            stats.cubes.add(cubeName);
            obj.append("\no ").append(name).append('\n');
            currentMesh = new MeshElement(ship, cubeName);
        }

        private void vertex(float x, float y, float z) {
            obj.append("v ").append(x * 16.0F).append(' ').append(-y * 16.0F).append(' ').append(z * 16.0F).append('\n');
            currentFace.add(++vertexCount);
            if (currentMesh != null) {
                currentMesh.vertex(x * 16.0F, -y * 16.0F, z * 16.0F);
            }
            if (currentFace.size() == 4) {
                obj.append("f ");
                for (int i = 0; i < currentFace.size(); i++) {
                    if (i > 0) {
                        obj.append(' ');
                    }
                    obj.append(currentFace.get(i));
                }
                obj.append('\n');
                elements.computeIfAbsent(currentElement, ignored -> new ElementStats(stageOf(currentElement))).faceCount++;
                currentFace.clear();
            }
        }

        private int vertexCount;

        private void endCube(String elementId) {
            currentFace.clear();
            if (currentMesh != null) {
                blockbenchElements.add(currentMesh.toJson());
                groupFor(currentElement).children.add(currentMesh.uuid);
                currentMesh = null;
            }
        }

        private String toObj() {
            return obj.toString();
        }

        private JsonObject toBlockbenchModel(String ship) {
            JsonObject root = new JsonObject();
            JsonObject meta = new JsonObject();
            meta.addProperty("format_version", "5.0");
            meta.addProperty("model_format", "free");
            meta.addProperty("box_uv", false);
            root.add("meta", meta);
            root.addProperty("name", "smallshipsheraldry_" + ship + "_sails_review");

            JsonObject resolution = new JsonObject();
            resolution.addProperty("width", 128);
            resolution.addProperty("height", 64);
            root.add("resolution", resolution);
            root.add("elements", blockbenchElements);
            root.add("groups", new JsonArray());
            JsonArray outliner = new JsonArray();
            topLevelGroups.values().stream()
                    .sorted(Comparator.comparingInt(GroupNode::displayOrder))
                    .forEach(group -> outliner.add(group.toJson()));
            root.add("outliner", outliner);
            root.add("textures", new JsonArray());
            return root;
        }

        private GroupNode groupFor(String elementId) {
            List<String> path = reviewGroupPath(elementId);
            Map<String, GroupNode> siblings = topLevelGroups;
            GroupNode current = null;
            StringBuilder stablePath = new StringBuilder(ship);
            for (String part : path) {
                stablePath.append('/').append(part);
                GroupNode parent = current;
                current = siblings.computeIfAbsent(part, ignored -> new GroupNode(part, stablePath.toString()));
                siblings = current.groups;
                if (parent != null && !parent.children.contains(current)) {
                    parent.children.add(current);
                }
            }
            return current;
        }
    }

    private static final class ElementStats {
        private final int stage;
        private final List<String> cubes = new ArrayList<>();
        private int cubeCount;
        private int faceCount;

        private ElementStats(int stage) {
            this.stage = stage;
        }
    }

    private static final class GroupNode {
        private final String name;
        private final String uuid;
        private final Map<String, GroupNode> groups = new LinkedHashMap<>();
        private final List<Object> children = new ArrayList<>();

        private GroupNode(String name, String stablePath) {
            this.name = name;
            this.uuid = UUID.nameUUIDFromBytes(
                    ("smallshipsheraldry:group:" + stablePath).getBytes(StandardCharsets.UTF_8)
            ).toString();
        }

        private JsonObject toJson() {
            JsonObject group = new JsonObject();
            group.addProperty("name", name);
            group.add("origin", MeshElement.vector(0.0F, 0.0F, 0.0F));
            group.addProperty("color", stageColor(name));
            group.addProperty("uuid", uuid);
            group.addProperty("export", true);
            group.addProperty("isOpen", name.startsWith("Stage "));
            group.addProperty("locked", false);
            group.addProperty("visibility", !name.startsWith("Stage ") || name.startsWith("Stage 4 "));
            group.addProperty("autouv", 0);
            JsonArray jsonChildren = new JsonArray();
            for (Object child : children) {
                if (child instanceof GroupNode childGroup) {
                    jsonChildren.add(childGroup.toJson());
                } else if (child instanceof String childUuid) {
                    jsonChildren.add(childUuid);
                }
            }
            group.add("children", jsonChildren);
            return group;
        }

        private int displayOrder() {
            if (name.startsWith("Stage ") && name.length() >= 7) {
                char stage = name.charAt(6);
                if (stage >= '0' && stage <= '4') {
                    return stage - '0';
                }
            }
            return 10;
        }

        private static int stageColor(String name) {
            if (!name.startsWith("Stage ") || name.length() < 7) {
                return 0;
            }
            char stage = name.charAt(6);
            return stage >= '0' && stage <= '4' ? stage - '0' : 0;
        }
    }

    private static final class ObjCollector implements VertexConsumer {
        private final Export export;
        private CapturedVertex current;

        private ObjCollector(Export export) {
            this.export = export;
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
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            if (current != null) {
                export.vertex(current.x, current.y, current.z);
                current = null;
            }
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private record CapturedVertex(float x, float y, float z) {
    }

    private static final class MeshElement {
        private final String name;
        private final String uuid;
        private final JsonObject vertices = new JsonObject();
        private final JsonObject faces = new JsonObject();
        private final List<String> currentFaceVertices = new ArrayList<>(4);
        private int vertexIndex;
        private int faceIndex;

        private MeshElement(String ship, String name) {
            this.name = name;
            this.uuid = UUID.nameUUIDFromBytes(("smallshipsheraldry:" + ship + ':' + name).getBytes(StandardCharsets.UTF_8)).toString();
        }

        private void vertex(float x, float y, float z) {
            String vertexId = "v" + vertexIndex++;
            JsonArray position = new JsonArray();
            position.add(round(x));
            position.add(round(y));
            position.add(round(z));
            vertices.add(vertexId, position);
            currentFaceVertices.add(vertexId);
            if (currentFaceVertices.size() == 4) {
                face();
                currentFaceVertices.clear();
            }
        }

        private void face() {
            JsonObject face = new JsonObject();
            JsonArray faceVertices = new JsonArray();
            JsonObject uv = new JsonObject();
            int[][] uvCorners = {{0, 0}, {8, 0}, {8, 8}, {0, 8}};
            for (int i = 0; i < currentFaceVertices.size(); i++) {
                String vertexId = currentFaceVertices.get(i);
                faceVertices.add(vertexId);
                JsonArray uvPoint = new JsonArray();
                uvPoint.add(uvCorners[i][0]);
                uvPoint.add(uvCorners[i][1]);
                uv.add(vertexId, uvPoint);
            }
            face.add("vertices", faceVertices);
            face.add("uv", uv);
            face.addProperty("texture", false);
            faces.add("f" + faceIndex++, face);
        }

        private JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("name", name);
            object.addProperty("type", "mesh");
            object.addProperty("uuid", uuid);
            object.add("origin", vector(0.0F, 0.0F, 0.0F));
            object.add("rotation", vector(0.0F, 0.0F, 0.0F));
            object.addProperty("color", 3);
            object.addProperty("export", true);
            object.addProperty("visibility", true);
            object.addProperty("locked", false);
            object.addProperty("render_order", "default");
            object.addProperty("allow_mirror_modeling", true);
            object.add("vertices", vertices);
            object.add("faces", faces);
            return object;
        }

        private static JsonArray vector(float x, float y, float z) {
            JsonArray vector = new JsonArray();
            vector.add(round(x));
            vector.add(round(y));
            vector.add(round(z));
            return vector;
        }

        private static float round(float value) {
            return Math.round(value * 10000.0F) / 10000.0F;
        }
    }
}
