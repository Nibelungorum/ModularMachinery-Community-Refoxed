package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private boolean allowModifiers;
    private boolean allowMultithreading;
    private int maxParallelAmount = 1;
    private final java.util.Map<String, SmartInterfaceType> smartInterfaceTypes = new LinkedHashMap<>();
    private boolean shareSmartInterfaces;
    private final List<SmartInterfaceModifier> smartInterfaceModifiers = new ArrayList<>();
    private Identifier runningSoundId;
    private Identifier finishSoundId;

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

    public MachineBuilder allowModifiers() { return allowModifiers(true); }

    public MachineBuilder allowModifiers(boolean allow) {
        allowModifiers = allow;
        return this;
    }

    public MachineBuilder allowMultithreading() { return allowMultithreading(true); }

    public MachineBuilder allowMultithreading(boolean allow) {
        allowMultithreading = allow;
        return this;
    }

    public MachineBuilder maxParallelAmount(int amount) {
        if (amount < 1) throw new IllegalArgumentException("maxParallelAmount must be positive");
        maxParallelAmount = amount;
        return this;
    }

    public MachineBuilder smartInterface(SmartInterfaceType type) {
        Objects.requireNonNull(type, "type");
        if (smartInterfaceTypes.putIfAbsent(type.type(), type) != null) {
            throw new IllegalArgumentException("Duplicate smart interface type: " + type.type());
        }
        return this;
    }

    public MachineBuilder shareSmartInterfaces() { return shareSmartInterfaces(true); }

    public MachineBuilder shareSmartInterfaces(boolean share) {
        shareSmartInterfaces = share;
        return this;
    }

    public MachineBuilder smartInterfaceModifier(SmartInterfaceModifier modifier) {
        smartInterfaceModifiers.add(Objects.requireNonNull(modifier, "modifier"));
        return this;
    }

    public MachineBuilder runningSound(Identifier soundId) {
        this.runningSoundId = soundId;
        return this;
    }

    public MachineBuilder finishSound(Identifier soundId) {
        this.finishSoundId = soundId;
        return this;
    }

    public MachineBuilder failureAction(RecipeFailureActions failureAction) {
        this.failureAction = Objects.requireNonNull(failureAction, "failureAction");
        return this;
    }

    public MachineDefinition build() {
        return new MachineDefinition(id, displayNameKey, controller, appearance, factory, role,
                acceptedModuleIds, maxParallelism, parallelizable, failureAction, allowModifiers,
                allowMultithreading, maxParallelAmount, false, smartInterfaceTypes,
                shareSmartInterfaces, smartInterfaceModifiers, runningSoundId, finishSoundId, null);
    }
}
