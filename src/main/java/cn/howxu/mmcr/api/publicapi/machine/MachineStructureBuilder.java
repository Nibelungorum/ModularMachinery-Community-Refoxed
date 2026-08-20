package cn.howxu.mmcr.api.publicapi.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Builder for the structure phase of a public machine declaration.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureBuilder {
    private final List<StructureStage> stages = new ArrayList<>();

    private MachineStructureBuilder() {
    }

    public static MachineStructureBuilder structure() {
        return new MachineStructureBuilder();
    }

    public MachineStructureBuilder fullStructure(UnaryOperator<StructureStage.Builder> consumer) {
        stages.add(Objects.requireNonNull(consumer, "consumer").apply(StructureStage.builder().full()).build());
        return this;
    }

    public MachineStructureBuilder extension(UnaryOperator<StructureStage.Builder> consumer) {
        stages.add(Objects.requireNonNull(consumer, "consumer").apply(StructureStage.builder().extension()).build());
        return this;
    }

    public MachineStructureDefinition build(net.minecraft.resources.Identifier machineId) {
        return new MachineStructureDefinition(machineId, stages);
    }
}
