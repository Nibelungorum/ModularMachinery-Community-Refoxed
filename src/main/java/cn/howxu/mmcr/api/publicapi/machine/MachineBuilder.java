package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.network.RequestFailureReason;
import cn.howxu.mmcr.api.network.RequestProcess;
import cn.howxu.mmcr.api.publicapi.data.DataStorage;
import cn.howxu.mmcr.api.publicapi.network.MachineReference;
import cn.howxu.mmcr.api.publicapi.network.RequestBody;
import cn.howxu.mmcr.api.publicapi.network.RequestInfo;
import cn.howxu.mmcr.api.publicapi.network.RequestFailed;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

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
    private final ResourceLocation id;
    private List<ResourceLocation> recipePoolIds;
    private String displayNameKey;
    private ControllerSpec controller = ControllerSpec.builder().build();
    private AppearanceSpec appearance = AppearanceSpec.builder().build();
    private FactorySpec factory = FactorySpec.builder().build();
    private MachineRole role = MachineRole.NORMAL;
    private final Set<ResourceLocation> acceptedModuleIds = new LinkedHashSet<>();
    private NetworkInterfaceSpec networkInterface = NetworkInterfaceSpec.disabled();
    private long maxParallelism = 1L;
    private boolean parallelizable;
    private RecipeFailureActions failureAction = RecipeFailureActions.getDefaultAction();
    private boolean allowModifiers;
    private boolean allowMultithreading;
    private int maxParallelAmount = 1;
    private final Map<String, SmartInterfaceType> smartInterfaceTypes = new LinkedHashMap<>();
    private boolean shareSmartInterfaces;
    private final List<SmartInterfaceModifier> smartInterfaceModifiers = new ArrayList<>();
    private ResourceLocation runningSoundId;
    private ResourceLocation finishSoundId;
    private MachineBehavior behavior = RecipeBehavior.defaults();
    private MachineBehavior.MachineCallback preServerTick;
    private MachineBehavior.MachineCallback postServerTick;
    private final Map<ResourceLocation, RequestProcess> requestProcessors = new LinkedHashMap<>();
    private final Map<ResourceLocation, RequestFailed> requestFailures = new LinkedHashMap<>();

    private MachineBuilder(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static MachineBuilder machine(ResourceLocation id) {
        return new MachineBuilder(id);
    }

    public MachineBuilder displayNameKey(String displayNameKey) {
        this.displayNameKey = displayNameKey;
        return this;
    }

    public MachineBuilder recipePool(ResourceLocation... recipePoolIds) {
        this.recipePoolIds = MachineRegistration.copyRecipePoolIds(id, List.of(recipePoolIds));
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

    public MachineBuilder acceptedModule(ResourceLocation moduleId) {
        acceptedModuleIds.add(Objects.requireNonNull(moduleId, "moduleId"));
        return this;
    }

    public MachineBuilder networkInterface(int maxCount, int maxConnections) {
        networkInterface = new NetworkInterfaceSpec(maxCount, maxConnections, networkInterface.allowedMachineIds());
        return this;
    }

    public MachineBuilder allowNetworkMachine(ResourceLocation machineId) {
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

    public MachineBuilder runningSound(ResourceLocation soundId) {
        this.runningSoundId = soundId;
        return this;
    }

    public MachineBuilder finishSound(ResourceLocation soundId) {
        this.finishSoundId = soundId;
        return this;
    }

    public MachineBuilder failureAction(RecipeFailureActions failureAction) {
        this.failureAction = Objects.requireNonNull(failureAction, "failureAction");
        return this;
    }

    public MachineBuilder requestProcessInternal(ResourceLocation requestId, RequestProcess process) {
        if (requestProcessors.putIfAbsent(Objects.requireNonNull(requestId, "requestId"),
                Objects.requireNonNull(process, "process")) != null) {
            throw new IllegalArgumentException("Duplicate request processor: " + requestId);
        }
        return this;
    }

    public MachineBuilder requestProcess(ResourceLocation requestId, cn.howxu.mmcr.api.publicapi.network.RequestProcess process) {
        Objects.requireNonNull(process, "process");
        return requestProcessInternal(requestId, (body, request, senderStorage, receiverStorage) -> process.process(
                RequestBody.fromInternal(body),
                new RequestInfo(request.requestId(),
                        MachineReference.fromInternal(request.peer())),
                senderStorage == null ? null : DataStorage.view(senderStorage),
                receiverStorage == null ? null : DataStorage.view(receiverStorage)));
    }

    public MachineBuilder requestFailed(ResourceLocation requestId, RequestFailed failure) {
        if (requestFailures.putIfAbsent(Objects.requireNonNull(requestId, "requestId"),
                Objects.requireNonNull(failure, "failure")) != null) {
            throw new IllegalArgumentException("Duplicate request failure handler: " + requestId);
        }
        return this;
    }

    /** @deprecated Use {@link #requestFailed(ResourceLocation, RequestFailed)}. */
    @Deprecated(forRemoval = true)
    public MachineBuilder requestFailedLegacy(ResourceLocation requestId, cn.howxu.mmcr.api.network.RequestFailed failure) {
        Objects.requireNonNull(failure, "failure");
        return requestFailed(requestId, (body, request, senderStorage, reason) -> failure.fail(
                (cn.howxu.mmcr.api.network.RequestBody) body.bridgeValue(),
                new cn.howxu.mmcr.api.network.RequestInfo(request.requestId(),
                        (cn.howxu.mmcr.api.network.MachineReference) request.peer().bridgeValue()),
                senderStorage == null ? null : (cn.howxu.mmcr.api.data.DataStorage) senderStorage.bridgeValue(),
                RequestFailureReason.valueOf(reason.name())));
    }

    public MachineDefinition build() {
        MachineBehavior resolvedBehavior = behavior;
        if (preServerTick != null || postServerTick != null) {
            if (!(behavior instanceof RecipeBehavior recipe)) {
                throw new IllegalStateException("Recipe server tick hooks require recipe behavior");
            }
            RecipeBehavior.Builder builder = RecipeBehavior.builder();
            if (recipe.hasIdleStart()) builder.idleStart(recipe.idleStart());
            if (recipe.hasIdleEnd()) builder.idleEnd(recipe.idleEnd());
            if (recipe.hasBeforeStart()) builder.beforeStart(recipe.beforeStart());
            if (recipe.hasRecipeTick()) builder.recipeTick(recipe.recipeTick());
            if (recipe.hasBeforeFinish()) builder.beforeFinish(recipe.beforeFinish());
            if (preServerTick != null) builder.preServerTick(preServerTick);
            else if (recipe.hasPreServerTick()) builder.preServerTick(recipe.preServerTick());
            if (postServerTick != null) builder.postServerTick(postServerTick);
            else if (recipe.hasPostServerTick()) builder.postServerTick(recipe.postServerTick());
            resolvedBehavior = builder.build();
        }
        return new MachineDefinition(id, recipePoolIds, displayNameKey, controller, appearance, factory, role,
                acceptedModuleIds, networkInterface, maxParallelism, parallelizable, failureAction, allowModifiers,
                allowMultithreading, maxParallelAmount, false, smartInterfaceTypes,
                shareSmartInterfaces, smartInterfaceModifiers, runningSoundId, finishSoundId, null, resolvedBehavior,
                requestProcessors, requestFailures);
    }
}
