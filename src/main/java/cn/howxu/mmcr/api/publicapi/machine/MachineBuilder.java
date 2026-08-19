package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Fluent machine declaration builder.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineBuilder {
    private final Identifier id;
    private String displayNameKey;
    private PatternDefinition pattern;
    private ControllerSpec controller = ControllerSpec.builder().build();
    private AppearanceSpec appearance = AppearanceSpec.builder().build();
    private PortRequirements portRequirements = PortRequirements.none();
    private PortTiers portTiers = PortTiers.none();
    private StructureRequirements requirements = StructureRequirements.EMPTY;
    private FactorySpec factory = FactorySpec.builder().build();
    private MachineRole role = MachineRole.NORMAL;
    private final Set<Identifier> acceptedModuleIds = new LinkedHashSet<>();
    private int maxParallelism = 1;
    private boolean parallelizable;
    private RecipeFailureActions failureAction = RecipeFailureActions.getDefaultAction();
    private final List<StructureStage> additionalStages = new ArrayList<>();

    private MachineBuilder(Identifier id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static MachineBuilder machine(Identifier id) {
        return new MachineBuilder(id);
    }

    public MachineBuilder displayNameKey(String displayNameKey) {
        this.displayNameKey = displayNameKey;
        return this;
    }

    public MachineBuilder pattern(UnaryOperator<PatternBuilder> builder) {
        pattern = Objects.requireNonNull(builder, "builder").apply(PatternBuilder.pattern()).build();
        return this;
    }

    public MachineBuilder controller(UnaryOperator<ControllerSpec.Builder> builder) {
        controller = Objects.requireNonNull(builder, "builder").apply(ControllerSpec.builder()).build();
        return this;
    }

    public MachineBuilder appearance(UnaryOperator<AppearanceSpec.Builder> builder) {
        appearance = Objects.requireNonNull(builder, "builder").apply(AppearanceSpec.builder()).build();
        return this;
    }

    public MachineBuilder ports(UnaryOperator<PortRequirements.Builder> builder) {
        portRequirements = Objects.requireNonNull(builder, "builder").apply(PortRequirements.builder()).build();
        return this;
    }

    public MachineBuilder portTiers(UnaryOperator<PortTiers.Builder> builder) {
        portTiers = Objects.requireNonNull(builder, "builder").apply(PortTiers.builder()).build();
        return this;
    }

    public MachineBuilder stage(UnaryOperator<StructureStage.Builder> builder) {
        additionalStages.add(Objects.requireNonNull(builder, "builder").apply(StructureStage.builder()).build());
        return this;
    }

    public MachineBuilder requirements(UnaryOperator<StructureRequirements.Builder> builder) {
        requirements = Objects.requireNonNull(builder, "builder").apply(StructureRequirements.builder()).build();
        return this;
    }

    public MachineBuilder factory(UnaryOperator<FactorySpec.Builder> builder) {
        factory = Objects.requireNonNull(builder, "builder").apply(FactorySpec.builder()).build();
        return this;
    }

    public MachineBuilder role(MachineRole role) {
        this.role = Objects.requireNonNull(role, "role");
        return this;
    }

    public MachineBuilder acceptedModule(Identifier moduleId) {
        acceptedModuleIds.add(Objects.requireNonNull(moduleId, "moduleId"));
        return this;
    }

    public MachineBuilder maxParallelism(int maxParallelism) {
        this.maxParallelism = maxParallelism;
        return this;
    }

    public MachineBuilder parallelizable(boolean parallelizable) {
        this.parallelizable = parallelizable;
        return this;
    }

    public MachineBuilder failureAction(RecipeFailureActions failureAction) {
        this.failureAction = Objects.requireNonNull(failureAction, "failureAction");
        return this;
    }

    public MachineDefinition build() {
        if (pattern == null) throw new IllegalStateException("Machine pattern is required");
        List<StructureStage> stages = new ArrayList<>(1 + additionalStages.size());
        stages.add(new StructureStage(StructureStage.Kind.FULL, pattern, portRequirements, portTiers, requirements));
        stages.addAll(additionalStages);
        return new MachineDefinition(id, displayNameKey, pattern, controller, appearance, portRequirements, portTiers,
                stages, requirements, factory, role, acceptedModuleIds, maxParallelism, parallelizable, failureAction);
    }
}
