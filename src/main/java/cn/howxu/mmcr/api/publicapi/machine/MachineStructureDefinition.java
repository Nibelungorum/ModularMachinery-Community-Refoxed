package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/** Immutable public structure declaration kept separate from machine properties.
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureDefinition(Identifier machineId, List<StructureStage> stages) {
    public MachineStructureDefinition {
        Objects.requireNonNull(machineId, "machineId");
        stages = List.copyOf(stages == null ? List.of() : stages);
        if (stages.isEmpty() || stages.getFirst().kind() != StructureStage.Kind.FULL) {
            throw new IllegalArgumentException("Main machine structure is required");
        }
    }
}
