package cn.howxu.mmcr.client.controller;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerSpecCache {
    private static final List<Runnable> INVALIDATION_LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile Map<ResourceLocation, MachineControllerSpec> snapshot = Map.of();
    private static volatile long revision;

    private ControllerSpecCache() {
    }

    public static Map<ResourceLocation, MachineControllerSpec> snapshot() {
        return snapshot;
    }

    public static MachineControllerSpec specFor(ResourceLocation machineId) {
        MachineControllerSpec spec = snapshot.get(machineId);
        return spec != null ? spec : MachineControllerSpec.defaultsFor(machineId);
    }

    public static long revision() {
        return revision;
    }

    public static boolean replaceSnapshot(Map<ResourceLocation, MachineControllerSpec> replacement) {
        return replaceSnapshot(replacement, revision + 1);
    }

    public static boolean replaceSnapshot(Map<ResourceLocation, MachineControllerSpec> replacement, long contentVersion) {
        if (replacement == null || !isValid(replacement)) {
            return false;
        }

        snapshot = Map.copyOf(replacement);
        revision = contentVersion;
        for (Runnable listener : INVALIDATION_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Controller spec cache invalidation listener failed", exception);
            }
        }
        return true;
    }

    public static void addInvalidationListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener null");
        }
        INVALIDATION_LISTENERS.add(listener);
    }

    private static boolean isValid(Map<ResourceLocation, MachineControllerSpec> replacement) {
        for (Map.Entry<ResourceLocation, MachineControllerSpec> entry : replacement.entrySet()) {
            MachineControllerSpec spec = entry.getValue();
            if (entry.getKey() == null || spec == null || spec.id() == null
                    || spec.frontTexture() == null || spec.sideTexture() == null
                    || spec.topTexture() == null || spec.bottomTexture() == null) {
                return false;
            }
        }
        return true;
    }
}
