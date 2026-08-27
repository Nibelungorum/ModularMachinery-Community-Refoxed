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
    private static final Map<BlockPos, Entry> SNAPSHOTS = new HashMap<>();
    private static final List<Runnable> INVALIDATION_LISTENERS = new CopyOnWriteArrayList<>();

    private ControllerScreenTextCache() {
    }

    public static boolean replace(BlockPos pos, long revision, List<ControllerScreenTextSnapshot.Line> lines) {
        BlockPos key = Objects.requireNonNull(pos, "pos").immutable();
        if (revision < 0L) throw new IllegalArgumentException("revision must not be negative");
        List<ControllerScreenTextSnapshot.Line> replacement = copyLines(lines);

        synchronized (LOCK) {
            Entry current = SNAPSHOTS.get(key);
            if (current != null && revision <= current.revision()) return false;
            SNAPSHOTS.put(key, new Entry(revision, replacement));
        }

        notifyListeners();
        return true;
    }

    public static List<ControllerScreenTextSnapshot.Line> linesAt(BlockPos pos) {
        BlockPos key = Objects.requireNonNull(pos, "pos").immutable();
        Entry entry;
        synchronized (LOCK) {
            entry = SNAPSHOTS.get(key);
        }
        return entry == null ? List.of() : copyLines(entry.lines());
    }

    public static void clear(BlockPos pos) {
        BlockPos key = Objects.requireNonNull(pos, "pos").immutable();
        synchronized (LOCK) {
            SNAPSHOTS.remove(key);
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
