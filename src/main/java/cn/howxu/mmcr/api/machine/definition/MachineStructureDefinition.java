package cn.howxu.mmcr.api.machine.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Immutable public structure declaration kept separate from machine properties.
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureDefinition(ResourceLocation machineId, List<StructureStage> stages, boolean stateSensitive) {
    public MachineStructureDefinition(ResourceLocation machineId, List<StructureStage> stages) {
        this(machineId, stages, false);
    }

    public MachineStructureDefinition {
        Objects.requireNonNull(machineId, "machineId");
        stages = List.copyOf(stages == null ? List.of() : stages);
        if (stages.isEmpty() || stages.getFirst().kind() != StructureStage.Kind.FULL) {
            throw new IllegalArgumentException("Main machine structure is required");
        }
        if (stages.stream().filter(stage -> stage.kind() == StructureStage.Kind.FULL).count() != 1) {
            throw new IllegalArgumentException("Only one main machine structure is allowed");
        }
    }
}
