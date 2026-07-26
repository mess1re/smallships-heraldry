package me.mss1r.smallshipsheraldry.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class HeraldryPatternTransfer {
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_COMPRESSED_BYTES = 512 * 1024;
    public static final int MAX_CHUNKS = (MAX_COMPRESSED_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES;
    private static final long MAX_DECOMPRESSED_NBT_BYTES = 2L * 1024L * 1024L;

    private HeraldryPatternTransfer() {
    }

    public static byte[] encode(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(bytes))) {
            NbtIo.write(tag, output);
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length <= 0 || encoded.length > MAX_COMPRESSED_BYTES) {
            throw new IOException("Compressed heraldry pattern is too large: " + encoded.length);
        }
        return encoded;
    }

    public static Optional<CompoundTag> decode(byte[] encoded) {
        if (encoded == null || encoded.length <= 0 || encoded.length > MAX_COMPRESSED_BYTES) {
            return Optional.empty();
        }
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(encoded)))) {
            return Optional.ofNullable(NbtIo.read(input, NbtAccounter.create(MAX_DECOMPRESSED_NBT_BYTES)));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static int chunkCount(int byteCount) {
        return Math.max(1, (byteCount + CHUNK_BYTES - 1) / CHUNK_BYTES);
    }

    public static byte[] chunk(byte[] encoded, int chunkIndex) {
        int start = chunkIndex * CHUNK_BYTES;
        int length = Math.min(CHUNK_BYTES, encoded.length - start);
        byte[] chunk = new byte[length];
        System.arraycopy(encoded, start, chunk, 0, length);
        return chunk;
    }

    public static final class Collector {
        private int transferId = Integer.MIN_VALUE;
        private int totalBytes;
        private byte[][] chunks;
        private int receivedChunks;
        private int receivedBytes;

        public Optional<byte[]> accept(
                int incomingTransferId,
                int chunkIndex,
                int chunkCount,
                int incomingTotalBytes,
                byte[] chunk
        ) {
            if (!validMetadata(chunkIndex, chunkCount, incomingTotalBytes, chunk)) {
                reset();
                return Optional.empty();
            }
            if (incomingTransferId != transferId) {
                begin(incomingTransferId, chunkCount, incomingTotalBytes);
            }
            if (chunks == null || chunkCount != chunks.length || incomingTotalBytes != totalBytes) {
                reset();
                return Optional.empty();
            }
            if (chunks[chunkIndex] == null) {
                chunks[chunkIndex] = chunk;
                receivedChunks++;
                receivedBytes += chunk.length;
            }
            if (receivedChunks != chunks.length || receivedBytes != totalBytes) {
                return Optional.empty();
            }

            byte[] complete = new byte[totalBytes];
            int offset = 0;
            for (byte[] part : chunks) {
                System.arraycopy(part, 0, complete, offset, part.length);
                offset += part.length;
            }
            reset();
            return Optional.of(complete);
        }

        public void reset() {
            transferId = Integer.MIN_VALUE;
            totalBytes = 0;
            chunks = null;
            receivedChunks = 0;
            receivedBytes = 0;
        }

        private void begin(int incomingTransferId, int chunkCount, int incomingTotalBytes) {
            transferId = incomingTransferId;
            totalBytes = incomingTotalBytes;
            chunks = new byte[chunkCount][];
            receivedChunks = 0;
            receivedBytes = 0;
        }

        private static boolean validMetadata(
                int chunkIndex,
                int chunkCount,
                int totalBytes,
                byte[] chunk
        ) {
            if (chunkCount <= 0 || chunkCount > MAX_CHUNKS
                    || chunkIndex < 0 || chunkIndex >= chunkCount
                    || totalBytes <= 0 || totalBytes > MAX_COMPRESSED_BYTES
                    || chunk == null || chunk.length <= 0 || chunk.length > CHUNK_BYTES
                    || chunkCount != HeraldryPatternTransfer.chunkCount(totalBytes)) {
                return false;
            }
            int expectedLength = chunkIndex == chunkCount - 1
                    ? totalBytes - chunkIndex * CHUNK_BYTES
                    : CHUNK_BYTES;
            return chunk.length == expectedLength;
        }
    }
}
