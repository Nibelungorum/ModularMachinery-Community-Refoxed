package cn.howxu.mmcr.client;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import cn.howxu.mmcr.client.model.RuntimeMachineModelRegistry;
import cn.howxu.mmcr.client.preview.StructurePreviewCompilationCache;
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
        if (!ControllerSpecCache.replaceSnapshot(controllerSpecs)
                || !MachineAppearanceCache.replaceSnapshot(appearances)) {
            throw new IllegalArgumentException("Invalid runtime client cache snapshot");
        }
        StructurePreviewCompilationCache.instance().clear();
        RuntimeMachineModelRegistry.invalidate();
    }

    public static void validate(Map<Identifier, MachineControllerSpec> controllerSpecs,
                                Map<Identifier, MachineAppearanceSpec> appearances) {
        if (controllerSpecs == null || appearances == null) {
            throw new IllegalArgumentException("Runtime client cache snapshot null");
        }
        controllerSpecs.forEach((id, spec) -> {
            if (id == null || spec == null || !id.equals(spec.id())
                    || spec.frontTexture() == null || spec.sideTexture() == null
                    || spec.topTexture() == null || spec.bottomTexture() == null) {
                throw new IllegalArgumentException("Invalid controller spec entry: " + id);
            }
        });
        appearances.forEach((id, spec) -> {
            if (id == null || spec == null) {
                throw new IllegalArgumentException("Invalid appearance entry: " + id);
            }
        });
    }
}
