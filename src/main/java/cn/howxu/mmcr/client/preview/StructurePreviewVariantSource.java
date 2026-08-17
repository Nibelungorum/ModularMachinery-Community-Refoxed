package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.api.machine.MachineStructureStage;

/**
 * Package-private seam for resolving a stage's selected preview variants.
 *
 * @author howxu <dev@howxu.cn>
 */
interface StructurePreviewVariantSource {
    StructurePreviewSchema resolve(MachineStructureStage stage, StructurePreviewVariantSelection selection);
}
