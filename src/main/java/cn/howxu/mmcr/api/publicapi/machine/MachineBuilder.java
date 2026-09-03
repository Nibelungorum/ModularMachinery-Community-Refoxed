package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.network.RequestFailed;
import cn.howxu.mmcr.api.network.RequestProcess;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
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
    private NetworkInterfaceSpec networkInterface = NetworkInterfaceSpec.disabled();
    private long maxParallelism = 1L;
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
    private MachineBehavior behavior = RecipeBehavior.defaults();
    private MachineBehavior.MachineCallback preServerTick;
    private MachineBehavior.MachineCallback postServerTick;
    private final java.util.Map<Identifier, RequestProcess> requestProcessors = new LinkedHashMap<>();
    private final java.util.Map<Identifier, RequestFailed> requestFailures = new LinkedHashMap<>();

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

    public MachineBuilder recipeBehavior(UnaryOperator<RecipeBehavior.Builder> builder) {
        RecipeBehavior.Builder behaviorBuilder = RecipeBehavior.builder();
        behavior = Objects.requireNonNull(Objects.requireNonNull(builder, "builder").apply(behaviorBuilder),
                "recipe behavior").build();
        return this;
    }

    public MachineBuilder tickBehavior(Consumer<TickBehavior.Builder> builder) {
        if (preServerTick != null || postServerTick != null) {
            throw new IllegalStateException("Cannot configure server tick hooks for tick behavior");
        }
        TickBehavior.Builder behaviorBuilder = TickBehavior.builder();
        Objects.requireNonNull(builder, "builder").accept(behaviorBuilder);
        behavior = behaviorBuilder.build();
        return this;
    }

    public MachineBuilder preServerTick(MachineBehavior.MachineCallback callback) {
        if (behavior instanceof TickBehavior) {
            throw new IllegalStateException("Recipe server tick hooks require recipe behavior");
        }
        preServerTick = Objects.requireNonNull(callback, "preServerTick");
        return this;
    }

    public MachineBuilder postServerTick(MachineBehavior.MachineCallback callback) {
        if (behavior instanceof TickBehavior) {
            throw new IllegalStateException("Recipe server tick hooks require recipe behavior");
        }
        postServerTick = Objects.requireNonNull(callback, "postServerTick");
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

    public MachineBuilder networkInterface(int maxCount, int maxConnections) {
        networkInterface = new NetworkInterfaceSpec(maxCount, maxConnections, networkInterface.allowedMachineIds());
        return this;
    }

    public MachineBuilder allowNetworkMachine(Identifier machineId) {
        networkInterface = networkInterface.withAllowedMachine(Objects.requireNonNull(machineId, "machineId"));
        return this;
    }

    public MachineBuilder maxParallelism(long maxParallelism) {
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

    public MachineBuilder requestProcess(Identifier requestId, RequestProcess process) {
        if (requestProcessors.putIfAbsent(Objects.requireNonNull(requestId, "requestId"),
                Objects.requireNonNull(process, "process")) != null) {
            throw new IllegalArgumentException("Duplicate request processor: " + requestId);
        }
        return this;
    }

    public MachineBuilder requestFailed(Identifier requestId, RequestFailed failure) {
        if (requestFailures.putIfAbsent(Objects.requireNonNull(requestId, "requestId"),
                Objects.requireNonNull(failure, "failure")) != null) {
            throw new IllegalArgumentException("Duplicate request failure handler: " + requestId);
        }
        return this;
    }

    public MachineDefinition build() {
        MachineBehavior resolvedBehavior = behavior;
        if (preServerTick != null || postServerTick != null) {
            if (!(behavior instanceof RecipeBehavior recipe)) {
                throw new IllegalStateException("Recipe server tick hooks require recipe behavior");
            }
            resolvedBehavior = RecipeBehavior.builder()
                    .idleStart(recipe.idleStart())
                    .idleEnd(recipe.idleEnd())
                    .beforeStart(recipe.beforeStart())
                    .recipeTick(recipe.recipeTick())
                    .beforeFinish(recipe.beforeFinish())
                    .preServerTick(preServerTick == null ? recipe.preServerTick() : preServerTick)
                    .postServerTick(postServerTick == null ? recipe.postServerTick() : postServerTick)
                    .build();
        }
        return new MachineDefinition(id, displayNameKey, controller, appearance, factory, role,
                acceptedModuleIds, networkInterface, maxParallelism, parallelizable, failureAction, allowModifiers,
                allowMultithreading, maxParallelAmount, false, smartInterfaceTypes,
                shareSmartInterfaces, smartInterfaceModifiers, runningSoundId, finishSoundId, null, resolvedBehavior,
                requestProcessors, requestFailures);
    }
}
