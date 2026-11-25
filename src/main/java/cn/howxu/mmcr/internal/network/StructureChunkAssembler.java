package cn.howxu.mmcr.internal.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Bounded transport for one compressed structure. */
final class StructureChunkAssembler {
    static final int MAX_BYTES = 64 * 1024 * 1024;

    private final byte[][] slices;
    private int received;
    private int bytes;

    StructureChunkAssembler(PktStructureChunkPayload first) {
        slices = new byte[first.chunkCount()][];
    }

    static List<byte[]> split(ByteBuf raw, int chunkSize) {
        if (chunkSize < 1 || chunkSize > PktStructureChunkPayload.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid structure chunk size: " + chunkSize);
        }
        checkSize(raw.readableBytes());
        var output = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(output)) {
            raw.getBytes(raw.readerIndex(), deflater, raw.readableBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not compress structure", e);
        }
        checkSize(output.size());
        byte[] compressed = output.toByteArray();
        if (compressed.length <= chunkSize) return List.of(compressed);
        int count = Math.ceilDiv(compressed.length, chunkSize);
        if (count > PktStructureChunkPayload.MAX_CHUNKS) throw new IllegalArgumentException("Too many structure chunks: " + count);
        List<byte[]> result = new ArrayList<>(count);
        for (int from = 0; from < compressed.length; from += chunkSize) {
            result.add(Arrays.copyOfRange(compressed, from, Math.min(from + chunkSize, compressed.length)));
        }
        return result;
    }

    void accept(PktStructureChunkPayload chunk) {
        int index = chunk.chunkIndex();
        if (chunk.chunkCount() != slices.length || slices[index] != null) {
            throw new IllegalArgumentException("Conflicting structure chunk " + index + " for " + chunk.machineId());
        }
        byte[] data = chunk.data();
        checkSize(bytes + data.length);
        bytes += data.length;
        slices[index] = data;
        received++;
    }

    boolean inflateInto(ByteBuf target) {
        if (received != slices.length) return false;
        var compressed = Unpooled.wrappedBuffer(slices.length, slices);
        try (var inflater = new InflaterInputStream(new ByteBufInputStream(compressed, true))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int n; (n = inflater.read(buffer)) != -1;) {
                total += n;
                checkSize(total);
                target.writeBytes(buffer, 0, n);
            }
            return true;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not decompress structure", e);
        }
    }

    private static void checkSize(int bytes) {
        if (bytes > MAX_BYTES) throw new IllegalArgumentException("Structure exceeds " + MAX_BYTES + " bytes");
    }
}
