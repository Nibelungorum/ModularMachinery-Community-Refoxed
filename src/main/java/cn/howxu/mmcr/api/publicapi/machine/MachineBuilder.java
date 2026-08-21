package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
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
    private ControllerSpec controller = ControllerSpec.builder().build();
    private AppearanceSpec appearance = AppearanceSpec.builder().build();
    private FactorySpec factory = FactorySpec.builder().build();
    private MachineRole role = MachineRole.NORMAL;
    private final Set<Identifier> acceptedModuleIds = new LinkedHashSet<>();
    private int maxParallelism = 1;
    private boolean parallelizable;
    private RecipeFailureActions failureAction = RecipeFailureActions.getDefaultAction();

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

    public MachineBuilder controller(UnaryOperator<ControllerSpec.Builder> builder) {
        controller = Objects.requireNonNull(builder, "builder").apply(ControllerSpec.builder()).build();
        return this;
    }

    public MachineBuilder appearance(UnaryOperator<AppearanceSpec.Builder> builder) {
        appearance = Objects.requireNonNull(builder, "builder").apply(AppearanceSpec.builder()).build();
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
        return new MachineDefinition(id, displayNameKey, controller, appearance, factory, role,
                acceptedModuleIds, maxParallelism, parallelizable, failureAction);
    }
}
