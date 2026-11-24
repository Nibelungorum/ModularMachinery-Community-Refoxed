package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Accumulates deflated structure slices until a whole structure (and then a whole snapshot) is available.
 *
 * <p>A single structure can be several megabytes, well past vanilla's 1 MiB custom payload limit, so the
 * server deflates and slices it. Reassembly is byte-level: slices are concatenated and inflated with no
 * understanding of the structure format, which keeps the protocol independent of the structure codec.
 *
 * @author howxu {@literal <dev@howxu.cn>}
 */
final class StructureChunkAssembler {

    private static final int MAX_STRUCTURE_BYTES = 64 * 1024 * 1024;

    private StructureChunkAssembler() {
    }

    static byte[] deflate(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
        try (OutputStream deflater = new DeflaterOutputStream(out)) {
            deflater.write(raw);
        } catch (IOException e) {
            throw new IllegalStateException("failed to compress structure payload", e);
        }
        return out.toByteArray();
    }

    static byte[] inflate(byte[] compressed, int expectedMaximum) {
        int limit = expectedMaximum <= 0 ? MAX_STRUCTURE_BYTES : Math.min(expectedMaximum, MAX_STRUCTURE_BYTES);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 1 << 20));
        byte[] buffer = new byte[8192];
        try (InputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            int read;
            while ((read = inflater.read(buffer)) > 0) {
                if (out.size() + read > limit) {
                    throw new IllegalArgumentException("inflated structure payload exceeds " + limit + " bytes");
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to decompress structure payload", e);
        }
        return out.toByteArray();
    }

    /** Splits compressed bytes into slices no larger than {@code maxChunk}. */
    static java.util.List<byte[]> split(byte[] compressed, int maxChunk) {
        if (maxChunk < 1 || maxChunk > PktStructureChunkPayload.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid chunk size: " + maxChunk);
        }
        if (compressed.length == 0) return java.util.List.of(new byte[0]);
        int count = (compressed.length + maxChunk - 1) / maxChunk;
        if (count > PktStructureChunkPayload.MAX_CHUNKS) {
            throw new IllegalArgumentException("Structure needs too many chunks: " + count);
        }
        java.util.List<byte[]> slices = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int from = index * maxChunk;
            int to = Math.min(compressed.length, from + maxChunk);
            slices.add(java.util.Arrays.copyOfRange(compressed, from, to));
        }
        return slices;
    }

    /** Per-transfer accumulation state; keyed by content version so a new sync discards stale slices. */
    static final class Transfer {
        private final long contentVersion;
        private final Map<ResourceLocation, Map<Integer, byte[]>> slices = new HashMap<>();
        private final Map<ResourceLocation, Integer> expectedChunks = new HashMap<>();

        Transfer(long contentVersion) {
            this.contentVersion = contentVersion;
        }

        long contentVersion() {
            return contentVersion;
        }

        synchronized void accept(ResourceLocation machineId, int chunkIndex, int chunkCount, byte[] data) {
            Objects.requireNonNull(machineId, "machineId");
            Integer known = expectedChunks.putIfAbsent(machineId, chunkCount);
            if (known != null && known != chunkCount) {
                throw new IllegalArgumentException("Inconsistent chunk count for " + machineId
                        + ": " + known + " then " + chunkCount);
            }
            slices.computeIfAbsent(machineId, ignored -> new HashMap<>()).put(chunkIndex, data);
        }

        synchronized boolean isComplete(ResourceLocation machineId) {
            Integer expected = expectedChunks.get(machineId);
            if (expected == null) return false;
            return slices.getOrDefault(machineId, Map.of()).size() == expected;
        }

        synchronized long pendingBytes() {
            long total = 0;
            for (Map<Integer, byte[]> byIndex : slices.values()) {
                for (byte[] slice : byIndex.values()) total += slice.length;
            }
            return total;
        }

        /** Reassembles and inflates one structure, or {@code null} while slices are still missing. */
        synchronized byte[] assemble(ResourceLocation machineId) {
            Integer expected = expectedChunks.get(machineId);
            if (expected == null) return null;
            Map<Integer, byte[]> byIndex = slices.get(machineId);
            if (byIndex == null || byIndex.size() != expected) return null;
            int total = 0;
            for (int index = 0; index < expected; index++) {
                byte[] slice = byIndex.get(index);
                if (slice == null) return null;
                total += slice.length;
            }
            if (total > MAX_STRUCTURE_BYTES) {
                throw new IllegalArgumentException("Structure payload exceeds " + MAX_STRUCTURE_BYTES + " bytes");
            }
            byte[] compressed = new byte[total];
            int offset = 0;
            for (int index = 0; index < expected; index++) {
                byte[] slice = byIndex.get(index);
                System.arraycopy(slice, 0, compressed, offset, slice.length);
                offset += slice.length;
            }
            return inflate(compressed, MAX_STRUCTURE_BYTES);
        }

        synchronized void clear() {
            slices.clear();
            expectedChunks.clear();
        }

        synchronized Map<Integer, byte[]> slicesFor(ResourceLocation machineId) {
            return new LinkedHashMap<>(slices.getOrDefault(machineId, Map.of()));
        }
    }

    /** Test hook: decodes a reassembled structure payload; production decoding goes through the codec. */
    static MachineStructureDefinition decode(byte[] raw, java.util.function.Function<byte[], MachineStructureDefinition> decoder) {
        return decoder.apply(raw);
    }
}
