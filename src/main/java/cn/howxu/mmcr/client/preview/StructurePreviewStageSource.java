package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.api.machine.MachineStructureStage;

import java.util.List;

/**
 * Package-private seam for selecting and materializing machine structure stages.
 *
 * @author howxu <dev@howxu.cn>
 */
interface StructurePreviewStageSource {
    List<MachineStructureStage> stages();

    StructurePreviewSchema createSchema(int stageNumber, StructurePreviewVariantSelection selection);
}
