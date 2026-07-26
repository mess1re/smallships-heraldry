package me.mss1r.smallshipsheraldry.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Optional;

/**
 * Immutable, compact representation of a painted sail atlas.
 *
 * <p>The editor works with one dense pixel buffer while it is open. Persisted
 * patterns, undo entries, network packets and render caches use consecutive
 * same-colour runs instead. This keeps an empty pattern tiny and places a hard
 * upper bound on item/network size.</p>
 */
public final class HeraldryPatternData {
    public static final String ROOT_TAG = "HeraldryPattern";
    public static final String AUTHOR_TAG = "Author";
    public static final String SHIP_TYPE_TAG = "ShipType";
    public static final String BASE_COLOR_TAG = "BaseColor";

    private static final String PIXEL_FORMAT_TAG = "PixelFormat";
    private static final String PIXEL_RUNS_TAG = "PixelRuns";
    private static final int PIXEL_FORMAT_VERSION = 1;

    // Read-only migration support for patterns created before the compact format.
    private static final String LEGACY_MESH_PIXELS_TAG = "MeshPixels";
    private static final String LEGACY_MESH_PIXEL_INDICES_TAG = "MeshPixelIndices";
    private static final String LEGACY_MESH_PIXEL_COLORS_TAG = "MeshPixelColors";

    public static final int WIDTH = 128;
    public static final int HEIGHT = 64;
    public static final int STAGE_COUNT = 5;
    public static final int MESH_FACE_SIZE = 10;
    public static final int MESH_ATLAS_COLUMNS = 128;
    public static final int MESH_ATLAS_ROWS = 128;
    public static final int MESH_MAX_FACES = MESH_ATLAS_COLUMNS * MESH_ATLAS_ROWS;
    public static final int MESH_ATLAS_WIDTH = MESH_ATLAS_COLUMNS * MESH_FACE_SIZE;
    public static final int MESH_ATLAS_HEIGHT = MESH_ATLAS_ROWS * MESH_FACE_SIZE;
    public static final int MESH_PIXEL_COUNT = MESH_ATLAS_WIDTH * MESH_ATLAS_HEIGHT;

    public static final int MAX_RUNS = 65_536;
    public static final int MAX_ENCODED_BYTES = 384 * 1024;
    public static final int MAX_AUTHOR_LENGTH = 32;

    private static final int[] EMPTY = new int[0];

    private final String author;
    private final ShipPreviewType shipType;
    private final int baseColorArgb;
    private final int[] runStarts;
    private final int[] runLengths;
    private final int[] runColors;
    private final byte[] encodedRuns;
    private final int paintedPixelCount;
    private final long visualHash;

    private HeraldryPatternData(
            String author,
            ShipPreviewType shipType,
            int baseColorArgb,
            int[] runStarts,
            int[] runLengths,
            int[] runColors
    ) {
        this.author = sanitizeAuthor(author);
        this.shipType = shipType == null ? ShipPreviewType.COG : shipType;
        this.baseColorArgb = sanitizeBaseColor(baseColorArgb);
        this.runStarts = runStarts;
        this.runLengths = runLengths;
        this.runColors = runColors;
        this.encodedRuns = encodeRuns(runStarts, runLengths, runColors);

        int painted = 0;
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, this.shipType.ordinal());
        hash = mix(hash, this.baseColorArgb);
        for (int i = 0; i < runStarts.length; i++) {
            painted += runLengths[i];
            hash = mix(hash, runStarts[i]);
            hash = mix(hash, runLengths[i]);
            hash = mix(hash, runColors[i]);
        }
        this.paintedPixelCount = painted;
        this.visualHash = hash;
    }

    public static HeraldryPatternData blank() {
        return new HeraldryPatternData("", ShipPreviewType.COG, 0xFFFFFFFF, EMPTY, EMPTY, EMPTY);
    }

    public static HeraldryPatternData fromMeshPixels(
            String author,
            ShipPreviewType shipType,
            int baseColorArgb,
            int[] pixels
    ) {
        if (pixels == null || pixels.length == 0) {
            return new HeraldryPatternData(author, shipType, baseColorArgb, EMPTY, EMPTY, EMPTY);
        }

        int limit = Math.min(pixels.length, MESH_PIXEL_COUNT);
        IntBuilder starts = new IntBuilder();
        IntBuilder lengths = new IntBuilder();
        IntBuilder colors = new IntBuilder();
        int index = 0;
        while (index < limit) {
            int color = sanitizePaintColor(pixels[index]);
            if (color == 0) {
                index++;
                continue;
            }

            int start = index;
            index++;
            while (index < limit && sanitizePaintColor(pixels[index]) == color) {
                index++;
            }
            starts.add(start);
            lengths.add(index - start);
            colors.add(color);
            if (starts.size() > MAX_RUNS) {
                throw new PatternTooComplexException(starts.size());
            }
        }

        return new HeraldryPatternData(
                author,
                shipType,
                baseColorArgb,
                starts.toArray(),
                lengths.toArray(),
                colors.toArray()
        );
    }

    public String author() {
        return author;
    }

    public ShipPreviewType shipType() {
        return shipType;
    }

    public int baseColorArgb() {
        return baseColorArgb;
    }

    public int runCount() {
        return runStarts.length;
    }

    public int paintedPixelCount() {
        return paintedPixelCount;
    }

    public int encodedSizeBytes() {
        return encodedRuns.length;
    }

    public int estimatedMemoryBytes() {
        return 96 + encodedRuns.length + runStarts.length * Integer.BYTES * 3;
    }

    public long visualHash() {
        return visualHash;
    }

    public int meshPixelAt(int faceIndex, int x, int y) {
        if (faceIndex < 0 || faceIndex >= MESH_MAX_FACES
                || x < 0 || x >= MESH_FACE_SIZE
                || y < 0 || y >= MESH_FACE_SIZE) {
            return 0;
        }
        int tileX = (faceIndex % MESH_ATLAS_COLUMNS) * MESH_FACE_SIZE;
        int tileY = (faceIndex / MESH_ATLAS_COLUMNS) * MESH_FACE_SIZE;
        return pixelAtIndex((tileY + y) * MESH_ATLAS_WIDTH + tileX + x);
    }

    public int meshAtlasPixelAt(int x, int y) {
        if (x < 0 || x >= MESH_ATLAS_WIDTH || y < 0 || y >= MESH_ATLAS_HEIGHT) {
            return 0;
        }
        return pixelAtIndex(y * MESH_ATLAS_WIDTH + x);
    }

    public int[] toDenseMeshPixels() {
        int[] pixels = new int[MESH_PIXEL_COUNT];
        copyRunsInto(pixels);
        return pixels;
    }

    public void copyRunsInto(int[] pixels) {
        if (pixels.length < MESH_PIXEL_COUNT) {
            throw new IllegalArgumentException("Sail atlas buffer is too small: " + pixels.length);
        }
        Arrays.fill(pixels, 0, MESH_PIXEL_COUNT, 0);
        for (int i = 0; i < runStarts.length; i++) {
            Arrays.fill(pixels, runStarts[i], runStarts[i] + runLengths[i], runColors[i]);
        }
    }

    public void forEachRun(PixelRunConsumer consumer) {
        for (int i = 0; i < runStarts.length; i++) {
            consumer.accept(runStarts[i], runLengths[i], runColors[i]);
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(AUTHOR_TAG, author);
        tag.putString(SHIP_TYPE_TAG, shipType.serializedName());
        tag.putInt(BASE_COLOR_TAG, baseColorArgb);
        tag.putInt(PIXEL_FORMAT_TAG, PIXEL_FORMAT_VERSION);
        if (encodedRuns.length > 2) {
            tag.putByteArray(PIXEL_RUNS_TAG, encodedRuns);
        }
        return tag;
    }

    public ItemStack writeToStack(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(ROOT_TAG, toTag()));
        return stack;
    }

    public HeraldryPatternData withAuthor(String newAuthor) {
        return new HeraldryPatternData(newAuthor, shipType, baseColorArgb, runStarts, runLengths, runColors);
    }

    public HeraldryPatternData withShipType(ShipPreviewType newShipType) {
        return new HeraldryPatternData(author, newShipType, baseColorArgb, runStarts, runLengths, runColors);
    }

    public HeraldryPatternData withBaseColor(int newBaseColorArgb) {
        return new HeraldryPatternData(author, shipType, newBaseColorArgb, runStarts, runLengths, runColors);
    }

    public static HeraldryPatternData fromRootTag(CompoundTag rootTag) {
        return tryFromRootTag(rootTag).orElseGet(HeraldryPatternData::blank);
    }

    public static Optional<HeraldryPatternData> tryFromRootTag(CompoundTag rootTag) {
        if (rootTag == null) {
            return Optional.empty();
        }

        String author = rootTag.getString(AUTHOR_TAG);
        ShipPreviewType shipType = ShipPreviewType.byName(rootTag.getString(SHIP_TYPE_TAG));
        int baseColor = rootTag.contains(BASE_COLOR_TAG, Tag.TAG_ANY_NUMERIC)
                ? rootTag.getInt(BASE_COLOR_TAG)
                : 0xFFFFFFFF;

        if (rootTag.contains(PIXEL_FORMAT_TAG, Tag.TAG_ANY_NUMERIC)) {
            if (rootTag.getInt(PIXEL_FORMAT_TAG) != PIXEL_FORMAT_VERSION) {
                return Optional.empty();
            }
            if (!rootTag.contains(PIXEL_RUNS_TAG, Tag.TAG_BYTE_ARRAY)) {
                return Optional.of(new HeraldryPatternData(author, shipType, baseColor, EMPTY, EMPTY, EMPTY));
            }
            DecodedRuns decoded = decodeRuns(rootTag.getByteArray(PIXEL_RUNS_TAG));
            if (decoded == null) {
                return Optional.empty();
            }
            return Optional.of(new HeraldryPatternData(
                        author,
                        shipType,
                        baseColor,
                        decoded.starts(),
                        decoded.lengths(),
                        decoded.colors()
                ));
        }

        int[] legacyPixels = readLegacyMeshPixels(rootTag);
        try {
            return Optional.of(fromMeshPixels(author, shipType, baseColor, legacyPixels));
        } catch (PatternTooComplexException exception) {
            return Optional.empty();
        }
    }

    public static HeraldryPatternData fromStack(ItemStack stack) {
        return tryFromStack(stack).orElseGet(HeraldryPatternData::blank);
    }

    public static Optional<HeraldryPatternData> tryFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return tryFromRootTag(tag.getCompound(ROOT_TAG));
    }

    private int pixelAtIndex(int pixelIndex) {
        int low = 0;
        int high = runStarts.length - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int start = runStarts[middle];
            if (pixelIndex < start) {
                high = middle - 1;
                continue;
            }
            int end = start + runLengths[middle];
            if (pixelIndex >= end) {
                low = middle + 1;
                continue;
            }
            return runColors[middle];
        }
        return 0;
    }

    private static int[] readLegacyMeshPixels(CompoundTag rootTag) {
        if (rootTag.contains(LEGACY_MESH_PIXELS_TAG, Tag.TAG_INT_ARRAY)) {
            int[] source = rootTag.getIntArray(LEGACY_MESH_PIXELS_TAG);
            return source.length <= MESH_PIXEL_COUNT ? source : Arrays.copyOf(source, MESH_PIXEL_COUNT);
        }
        if (!rootTag.contains(LEGACY_MESH_PIXEL_INDICES_TAG, Tag.TAG_INT_ARRAY)
                || !rootTag.contains(LEGACY_MESH_PIXEL_COLORS_TAG, Tag.TAG_INT_ARRAY)) {
            return EMPTY;
        }

        int[] indices = rootTag.getIntArray(LEGACY_MESH_PIXEL_INDICES_TAG);
        int[] colors = rootTag.getIntArray(LEGACY_MESH_PIXEL_COLORS_TAG);
        int[] pixels = new int[MESH_PIXEL_COUNT];
        int count = Math.min(indices.length, colors.length);
        for (int i = 0; i < count; i++) {
            int index = indices[i];
            if (index >= 0 && index < MESH_PIXEL_COUNT) {
                pixels[index] = sanitizePaintColor(colors[i]);
            }
        }
        return pixels;
    }

    private static byte[] encodeRuns(int[] starts, int[] lengths, int[] colors) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(8, starts.length * 7));
        output.write(PIXEL_FORMAT_VERSION);
        writeVarInt(output, starts.length);
        int previousEnd = 0;
        for (int i = 0; i < starts.length; i++) {
            writeVarInt(output, starts[i] - previousEnd);
            writeVarInt(output, lengths[i]);
            writeInt(output, colors[i]);
            previousEnd = starts[i] + lengths[i];
        }
        byte[] encoded = output.toByteArray();
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new PatternTooComplexException(starts.length);
        }
        return encoded;
    }

    private static DecodedRuns decodeRuns(byte[] encoded) {
        if (encoded.length < 2 || encoded.length > MAX_ENCODED_BYTES || encoded[0] != PIXEL_FORMAT_VERSION) {
            return null;
        }
        ByteCursor cursor = new ByteCursor(encoded, 1);
        int count = cursor.readVarInt();
        if (count < 0 || count > MAX_RUNS) {
            return null;
        }

        int[] starts = new int[count];
        int[] lengths = new int[count];
        int[] colors = new int[count];
        int previousEnd = 0;
        for (int i = 0; i < count; i++) {
            int gap = cursor.readVarInt();
            int length = cursor.readVarInt();
            int color = cursor.readInt();
            if (gap < 0 || length <= 0 || color == 0) {
                return null;
            }
            long start = (long) previousEnd + gap;
            long end = start + length;
            if (start < previousEnd || end > MESH_PIXEL_COUNT) {
                return null;
            }
            starts[i] = (int) start;
            lengths[i] = length;
            colors[i] = sanitizePaintColor(color);
            if (colors[i] == 0) {
                return null;
            }
            previousEnd = (int) end;
        }
        return cursor.atEnd() ? new DecodedRuns(starts, lengths, colors) : null;
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value >>> 24);
        output.write(value >>> 16);
        output.write(value >>> 8);
        output.write(value);
    }

    private static String sanitizeAuthor(String author) {
        if (author == null || author.isBlank()) {
            return "";
        }
        String trimmed = author.trim();
        return trimmed.length() <= MAX_AUTHOR_LENGTH ? trimmed : trimmed.substring(0, MAX_AUTHOR_LENGTH);
    }

    private static int sanitizeBaseColor(int argb) {
        return (argb & 0x00FFFFFF) | 0xFF000000;
    }

    private static int sanitizePaintColor(int argb) {
        return (argb >>> 24) == 0 ? 0 : argb;
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    @FunctionalInterface
    public interface PixelRunConsumer {
        void accept(int start, int length, int argb);
    }

    public static final class PatternTooComplexException extends IllegalArgumentException {
        public PatternTooComplexException(int runCount) {
            super("Pattern contains too many colour runs: " + runCount + " (maximum " + MAX_RUNS + ")");
        }
    }

    private record DecodedRuns(int[] starts, int[] lengths, int[] colors) {
    }

    private static final class IntBuilder {
        private int[] values = new int[64];
        private int size;

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return size == 0 ? EMPTY : Arrays.copyOf(values, size);
        }
    }

    private static final class ByteCursor {
        private final byte[] data;
        private int position;
        private boolean failed;

        ByteCursor(byte[] data, int position) {
            this.data = data;
            this.position = position;
        }

        int readVarInt() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                if (position >= data.length) {
                    failed = true;
                    return -1;
                }
                int current = data[position++] & 0xFF;
                value |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) {
                    return value;
                }
            }
            failed = true;
            return -1;
        }

        int readInt() {
            if (position + Integer.BYTES > data.length) {
                failed = true;
                return 0;
            }
            return (data[position++] & 0xFF) << 24
                    | (data[position++] & 0xFF) << 16
                    | (data[position++] & 0xFF) << 8
                    | data[position++] & 0xFF;
        }

        boolean atEnd() {
            return !failed && position == data.length;
        }
    }
}
