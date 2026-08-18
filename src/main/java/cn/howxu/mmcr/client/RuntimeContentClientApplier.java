package cn.howxu.mmcr.client;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Applies runtime content snapshots to client-only caches.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentClientApplier {
    private RuntimeContentClientApplier() {
    }

    public static void apply(Map<Identifier, MachineControllerSpec> controllerSpecs,
                             Map<Identifier, MachineAppearanceSpec> appearances) {
        ControllerSpecCache.replaceSnapshot(controllerSpecs);
        MachineAppearanceCache.replaceSnapshot(appearances);
    }
}
