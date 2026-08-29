package cn.howxu.mmcr.client.controller;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side versioned controller screen text cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerScreenTextCache {
    private static final Object LOCK = new Object();
    private static final Map<BlockPos, Map<String, Entry>> SNAPSHOTS = new HashMap<>();
    private static final List<Runnable> INVALIDATION_LISTENERS = new CopyOnWriteArrayList<>();

    private ControllerScreenTextCache() {
    }

    public static boolean replace(BlockPos pos, long revision, List<ControllerScreenTextSnapshot.Line> lines) {
        return replace(pos, "", revision, lines);
    }

    public static boolean replace(BlockPos pos, String laneId, long revision,
                                  List<ControllerScreenTextSnapshot.Line> lines) {
        BlockPos key = Objects.requireNonNull(pos, "pos").immutable();
        String snapshotKey = Objects.requireNonNull(laneId, "laneId");
        if (revision < 0L) throw new IllegalArgumentException("revision must not be negative");
        List<ControllerScreenTextSnapshot.Line> replacement = copyLines(lines);

        synchronized (LOCK) {
            Map<String, Entry> snapshots = SNAPSHOTS.computeIfAbsent(key, ignored -> new HashMap<>());
            Entry current = snapshots.get(snapshotKey);
            if (current != null && revision <= current.revision()) return false;
            snapshots.put(snapshotKey, new Entry(revision, replacement));
        }

        notifyListeners();
        return true;
    }

    public static List<ControllerScreenTextSnapshot.Line> linesAt(BlockPos pos) {
        return linesAt(pos, "");
    }

    public static List<ControllerScreenTextSnapshot.Line> linesAt(BlockPos pos, String laneId) {
        BlockPos key = Objects.requireNonNull(pos, "pos").immutable();
        String snapshotKey = Objects.requireNonNull(laneId, "laneId");
        Entry controllerEntry;
        Entry laneEntry;
        synchronized (LOCK) {
            Map<String, Entry> snapshots = SNAPSHOTS.get(key);
            controllerEntry = snapshots == null ? null : snapshots.get("");
            laneEntry = snapshotKey.isEmpty() || snapshots == null ? null : snapshots.get(snapshotKey);
        }
        if (controllerEntry == null && laneEntry == null) return List.of();
        if (laneEntry == null) return copyLines(controllerEntry.lines());
        List<ControllerScreenTextSnapshot.Line> lines = new ArrayList<>();
        if (controllerEntry != null) lines.addAll(controllerEntry.lines());
        lines.addAll(laneEntry.lines());
        return List.copyOf(lines);
    }

    public static void clear(BlockPos pos) {
        BlockPos key = Objects.requireNonNull(pos, "pos").immutable();
        synchronized (LOCK) {
            SNAPSHOTS.remove(key);
        }
    }

    public static void clearChunk(int chunkX, int chunkZ) {
        synchronized (LOCK) {
            SNAPSHOTS.keySet().removeIf(pos -> (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ);
        }
    }

    public static void clearAll() {
        synchronized (LOCK) {
            SNAPSHOTS.clear();
        }
    }

    public static void addInvalidationListener(Runnable listener) {
        if (listener == null) throw new IllegalArgumentException("listener null");
        INVALIDATION_LISTENERS.add(listener);
    }

    private static List<ControllerScreenTextSnapshot.Line> copyLines(
            List<ControllerScreenTextSnapshot.Line> lines) {
        if (lines == null) return List.of();
        List<ControllerScreenTextSnapshot.Line> copy = new ArrayList<>(lines.size());
        for (ControllerScreenTextSnapshot.Line line : lines) {
            Objects.requireNonNull(line, "line");
            copy.add(new ControllerScreenTextSnapshot.Line(line.scope(), line.lineId(), line.text().copy()));
        }
        return List.copyOf(copy);
    }

    private static void notifyListeners() {
        for (Runnable listener : INVALIDATION_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Controller screen text cache invalidation listener failed", exception);
            }
        }
    }

    private record Entry(long revision, List<ControllerScreenTextSnapshot.Line> lines) {
    }
}
