package cn.howxu.mmcr.client.model;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * Tracks client-local idle controller easter egg animations.
 *
 * @author howxu <dev@howxu.cn>
 */
final class ControllerIdleEasterEggTracker {
    private static final long CHECK_INTERVAL = 12000L;
    private static final long ANIMATION_DURATION = 104L;
    private static final double TRIGGER_CHANCE = 0.12D;

    private final DoubleSupplier random;
    private final Map<BlockPos, Entry> entries = new HashMap<>();

    ControllerIdleEasterEggTracker(DoubleSupplier random) {
        this.random = random;
    }

    synchronized void track(BlockPos pos, long tick) {
        entries.putIfAbsent(pos.immutable(), new Entry(tick + CHECK_INTERVAL));
    }

    synchronized void untrack(BlockPos pos) {
        entries.remove(pos);
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized List<BlockPos> tick(long tick, Predicate<BlockPos> eligible) {
        var changed = new LinkedHashSet<BlockPos>();
        entries.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            Entry state = entry.getValue();
            if (!eligible.test(pos)) {
                return true;
            }
            if (state.activeUntil != 0L && tick >= state.activeUntil) {
                state.activeUntil = 0L;
                changed.add(pos);
            }
            if (tick >= state.nextCheck) {
                state.nextCheck = tick + CHECK_INTERVAL;
                double roll = random.getAsDouble();
                if (roll < TRIGGER_CHANCE) {
                    state.activeUntil = tick + ANIMATION_DURATION;
                    changed.add(pos);
                }
            }
            return false;
        });
        return List.copyOf(changed);
    }

    synchronized boolean isActive(BlockPos pos, long tick) {
        Entry entry = entries.get(pos);
        return entry != null && entry.activeUntil > tick;
    }

    private static final class Entry {
        private long nextCheck;
        private long activeUntil;

        private Entry(long nextCheck) {
            this.nextCheck = nextCheck;
        }
    }
}
