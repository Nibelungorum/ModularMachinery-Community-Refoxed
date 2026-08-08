package cn.howxu.mmcr.client.controller;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerModelCache {
    private static final Map<ModelKey, ModelKey> MODELS = new ConcurrentHashMap<>();

    static {
        ControllerSpecCache.addInvalidationListener(ControllerModelCache::clear);
        MachineAppearanceCache.addInvalidationListener(ControllerModelCache::clear);
    }

    private ControllerModelCache() {
    }

    public static ModelKey modelFor(Identifier machineId) {
        ModelKey key = new ModelKey(
                machineId,
                ControllerSpecCache.specFor(machineId),
                MachineAppearanceCache.specFor(machineId),
                ControllerSpecCache.revision(),
                MachineAppearanceCache.revision());
        return MODELS.computeIfAbsent(key, ignored -> key);
    }

    public static void clear() {
        MODELS.clear();
    }

    public static int size() {
        return MODELS.size();
    }

    public record ModelKey(
            Identifier machineId,
            MachineControllerSpec spec,
            MachineAppearanceSpec appearance,
            long controllerRevision,
            long appearanceRevision) {
    }
}
