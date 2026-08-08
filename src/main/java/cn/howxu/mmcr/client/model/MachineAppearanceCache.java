package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side machine appearance snapshot cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineAppearanceCache {
    private static final List<Runnable> INVALIDATION_LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong REVISION = new AtomicLong();

    private static volatile Map<Identifier, MachineAppearanceSpec> snapshot = Map.of();

    private MachineAppearanceCache() {
    }

    public static Map<Identifier, MachineAppearanceSpec> snapshot() {
        return snapshot;
    }

    public static MachineAppearanceSpec specFor(Identifier machineId) {
        MachineAppearanceSpec spec = snapshot.get(machineId);
        return spec != null ? spec : MachineAppearanceSpec.defaults();
    }

    public static long revision() {
        return REVISION.get();
    }

    public static boolean replaceSnapshot(Map<Identifier, MachineAppearanceSpec> replacement) {
        if (replacement == null) {
            return false;
        }

        Map<Identifier, MachineAppearanceSpec> copy = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineAppearanceSpec> entry : replacement.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                return false;
            }
            copy.put(entry.getKey(), entry.getValue());
        }

        snapshot = Map.copyOf(copy);
        REVISION.incrementAndGet();
        notifyListeners();
        savePersistedSnapshot();
        return true;
    }

    public static void addInvalidationListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener null");
        }
        INVALIDATION_LISTENERS.add(listener);
    }

    public static void loadPersistedSnapshot() {
    }

    public static void savePersistedSnapshot() {
    }

    private static void notifyListeners() {
        for (Runnable listener : INVALIDATION_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Machine appearance cache invalidation listener failed", exception);
            }
        }
    }
}
