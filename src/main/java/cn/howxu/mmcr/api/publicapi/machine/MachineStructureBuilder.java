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

    public MachineStructureBuilder expandStructure(UnaryOperator<StructureStage.Builder> consumer) {
        if (stages.isEmpty() || stages.getFirst().kind() != StructureStage.Kind.FULL) {
            throw new IllegalStateException("expandStructure requires a full structure first");
        }
        stages.add(Objects.requireNonNull(consumer, "consumer").apply(StructureStage.builder().expansion()).build());
        return this;
    }

    public MachineStructureBuilder extension(UnaryOperator<StructureStage.Builder> consumer) {
        stages.add(Objects.requireNonNull(consumer, "consumer").apply(StructureStage.builder().extension()).build());
        return this;
    }

    public MachineStructureDefinition build(net.minecraft.resources.Identifier machineId) {
        List<StructureStage> boundStages = stages.stream()
                .map(stage -> new StructureStage(stage.kind(), stage.pattern().bindController(machineId),
                        stage.portRequirements(), stage.portTiers(), stage.requirements()))
                .toList();
        return new MachineStructureDefinition(machineId, boundStages);
    }
}
