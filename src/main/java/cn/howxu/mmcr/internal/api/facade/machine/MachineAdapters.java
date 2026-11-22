package cn.howxu.mmcr.internal.api.facade.machine;

import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.internal.api.facade.behavior.BehaviorAdapters;
import cn.howxu.mmcr.internal.api.facade.network.NetworkAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.publicapi.behavior.MachineContext;
import cn.howxu.mmcr.publicapi.behavior.RecipeHooks;
import cn.howxu.mmcr.publicapi.behavior.TickHooks;
import cn.howxu.mmcr.publicapi.machine.AppearanceOptions;
import cn.howxu.mmcr.publicapi.machine.ControllerOptions;
import cn.howxu.mmcr.publicapi.machine.FactoryOptions;
import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import cn.howxu.mmcr.publicapi.machine.MachineKind;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.machine.SmartInterfaceSpec;
import cn.howxu.mmcr.publicapi.network.FailureHandler;
import cn.howxu.mmcr.publicapi.network.NetworkSettings;
import cn.howxu.mmcr.publicapi.network.RequestHandler;
import cn.howxu.mmcr.publicapi.recipe.modifier.SmartModifierSpec;
import cn.howxu.mmcr.publicapi.runtime.RecipeFailureMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/** Machine boundary: a single core builder or definition owns all state. @author howxu <dev@howxu.cn> */
public final class MachineAdapters {
    private MachineAdapters() {}

    public static MachineDraft machine(Identifier id) {
        return wrap(MachineBuilder.machine(id));
    }

    public static MachineDraft wrap(MachineBuilder builder) {
        return new Draft(Objects.requireNonNull(builder, "builder"));
    }

    public static MachineSpec wrap(MachineDefinition definition) {
        return new Spec(Objects.requireNonNull(definition, "definition"));
    }

    public static MachineDefinition unwrap(MachineSpec spec) {
        return ((Spec) Objects.requireNonNull(spec, "spec")).delegate;
    }

    private static MachineRole toCore(MachineKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case NORMAL -> MachineRole.NORMAL;
            case HOST -> MachineRole.HOST;
            case MODULE -> MachineRole.MODULE;
        };
    }

    private static MachineKind fromCore(MachineRole role) {
        return switch (role) {
            case NORMAL -> MachineKind.NORMAL;
            case HOST -> MachineKind.HOST;
            case MODULE -> MachineKind.MODULE;
        };
    }

    private static RecipeFailureActions toCore(RecipeFailureMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case RESET -> RecipeFailureActions.RESET;
            case STILL -> RecipeFailureActions.STILL;
            case DECREASE -> RecipeFailureActions.DECREASE;
        };
    }

    private static RecipeFailureMode fromCore(RecipeFailureActions action) {
        return switch (action) {
            case RESET -> RecipeFailureMode.RESET;
            case STILL -> RecipeFailureMode.STILL;
            case DECREASE -> RecipeFailureMode.DECREASE;
        };
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class Draft implements MachineDraft {
        private final MachineBuilder delegate;
        private Draft(MachineBuilder delegate) { this.delegate = delegate; }
        @Override public MachineDraft displayNameKey(String key) { delegate.displayNameKey(key); return this; }
        @Override public MachineDraft recipePool(Identifier... ids) { delegate.recipePool(ids); return this; }
        @Override public MachineDraft controller(Consumer<ControllerOptions> configure) {
            Objects.requireNonNull(configure, "configure");
            delegate.controller(builder -> { configure.accept(new OptionAdapters.Controller(builder)); return builder; });
            return this;
        }
        @Override public MachineDraft appearance(Consumer<AppearanceOptions> configure) {
            Objects.requireNonNull(configure, "configure");
            delegate.appearance(builder -> { configure.accept(new OptionAdapters.Appearance(builder)); return builder; });
            return this;
        }
        @Override public MachineDraft factory(Consumer<FactoryOptions> configure) {
            Objects.requireNonNull(configure, "configure");
            delegate.factory(builder -> { configure.accept(new OptionAdapters.Factory(builder)); return builder; });
            return this;
        }
        @Override public MachineDraft recipeBehavior(Consumer<RecipeHooks> configure) {
            Objects.requireNonNull(configure, "configure");
            delegate.recipeBehavior(builder -> BehaviorAdapters.configureRecipe(builder, configure));
            return this;
        }
        @Override public <T> MachineDraft recipeBehavior(Supplier<T> factory, Consumer<RecipeHooks.ForInstance<T>> configure) {
            Objects.requireNonNull(configure, "configure");
            delegate.recipeBehavior(factory, builder -> configure.accept(BehaviorAdapters.wrap(builder)));
            return this;
        }
        @Override public MachineDraft tickBehavior(Consumer<TickHooks> configure) {
            Objects.requireNonNull(configure, "configure");
            delegate.tickBehavior(builder -> BehaviorAdapters.configureTick(builder, configure));
            return this;
        }
        @Override public MachineDraft preServerTick(Supplier<? extends Consumer<MachineContext>> factory) {
            delegate.preServerTick(BehaviorAdapters.machineCallbackFactory(factory)); return this;
        }
        @Override public MachineDraft preServerTick(Consumer<MachineContext> callback) {
            Objects.requireNonNull(callback, "callback");
            delegate.preServerTick(context -> callback.accept(BehaviorAdapters.wrap(context)));
            return this;
        }
        @Override public MachineDraft postServerTick(Supplier<? extends Consumer<MachineContext>> factory) {
            delegate.postServerTick(BehaviorAdapters.machineCallbackFactory(factory)); return this;
        }
        @Override public MachineDraft postServerTick(Consumer<MachineContext> callback) {
            Objects.requireNonNull(callback, "callback");
            delegate.postServerTick(context -> callback.accept(BehaviorAdapters.wrap(context)));
            return this;
        }
        @Override public MachineDraft role(MachineKind kind) { delegate.role(toCore(kind)); return this; }
        @Override public MachineDraft acceptedModule(Identifier id) { delegate.acceptedModule(id); return this; }
        @Override public MachineDraft networkInterface(int count, int connections) { delegate.networkInterface(count, connections); return this; }
        @Override public MachineDraft allowNetworkMachine(Identifier id) { delegate.allowNetworkMachine(id); return this; }
        @Override public MachineDraft maxParallelism(long amount) { delegate.maxParallelism(amount); return this; }
        @Override public MachineDraft parallelizable(boolean enabled) { delegate.parallelizable(enabled); return this; }
        @Override public MachineDraft allowModifier(Identifier id) { delegate.allowModifier(id); return this; }
        @Override public MachineDraft allowMultithreading() { delegate.allowMultithreading(); return this; }
        @Override public MachineDraft allowMultithreading(boolean enabled) { delegate.allowMultithreading(enabled); return this; }
        @Override public MachineDraft maxParallelAmount(int amount) { delegate.maxParallelAmount(amount); return this; }
        @Override public MachineDraft smartInterface(SmartInterfaceSpec type) { delegate.smartInterface(SmartInterfaceAdapters.unwrap(type)); return this; }
        @Override public MachineDraft shareSmartInterfaces() { delegate.shareSmartInterfaces(); return this; }
        @Override public MachineDraft shareSmartInterfaces(boolean enabled) { delegate.shareSmartInterfaces(enabled); return this; }
        @Override public MachineDraft smartInterfaceModifier(SmartModifierSpec modifier) { delegate.smartInterfaceModifier(ModifierAdapters.unwrap(modifier)); return this; }
        @Override public MachineDraft runningSound(Identifier id) { delegate.runningSound(id); return this; }
        @Override public MachineDraft finishSound(Identifier id) { delegate.finishSound(id); return this; }
        @Override public MachineDraft failureAction(RecipeFailureMode mode) { delegate.failureAction(toCore(mode)); return this; }
        @Override public MachineDraft requestProcess(Identifier id, RequestHandler handler) { delegate.requestProcess(id, NetworkAdapters.toCore(handler)); return this; }
        @Override public MachineDraft requestFailed(Identifier id, FailureHandler handler) { delegate.requestFailed(id, NetworkAdapters.toCore(handler)); return this; }
        @Override public MachineSpec build() { return wrap(delegate.build()); }
    }

    /** @author howxu <dev@howxu.cn> */
    private record Spec(MachineDefinition delegate) implements MachineSpec {
        @Override public Identifier id() { return delegate.id(); }
        @Override public List<Identifier> recipePoolIds() { return delegate.recipePoolIds(); }
        @Override public Identifier recipePoolId() { return delegate.recipePoolId(); }
        @Override public String displayNameKey() { return delegate.displayNameKey(); }
        @Override public ControllerOptions.View controller() { return new OptionAdapters.ControllerView(delegate.controller()); }
        @Override public AppearanceOptions.View appearance() { return new OptionAdapters.AppearanceView(delegate.appearance()); }
        @Override public FactoryOptions.View factory() { return new OptionAdapters.FactoryView(delegate.factory()); }
        @Override public MachineKind role() { return fromCore(delegate.role()); }
        @Override public Set<Identifier> acceptedModuleIds() { return delegate.acceptedModuleIds(); }
        @Override public NetworkSettings networkInterface() { return NetworkAdapters.settings(delegate.networkInterface()); }
        @Override public long maxParallelism() { return delegate.maxParallelism(); }
        @Override public int maxParallelAmount() { return delegate.maxParallelAmount(); }
        @Override public boolean parallelizable() { return delegate.parallelizable(); }
        @Override public Set<Identifier> allowedModifierIds() { return delegate.allowedModifierIds(); }
        @Override public boolean allowMultithreading() { return delegate.allowMultithreading(); }
        @Override public boolean shareSmartInterfaces() { return delegate.shareSmartInterfaces(); }
        @Override public RecipeFailureMode failureAction() { return fromCore(delegate.failureAction()); }
        @Override public Map<String, SmartInterfaceSpec> smartInterfaceTypes() {
            Map<String, SmartInterfaceSpec> result = new LinkedHashMap<>();
            delegate.smartInterfaceTypes().forEach((key, value) -> result.put(key, SmartInterfaceAdapters.wrap(value)));
            return Collections.unmodifiableMap(result);
        }
        @Override public List<SmartModifierSpec> smartInterfaceModifiers() {
            return delegate.smartInterfaceModifiers().stream().map(ModifierAdapters::wrap).toList();
        }
        @Override public Identifier runningSoundId() { return delegate.runningSoundId(); }
        @Override public Identifier finishSoundId() { return delegate.finishSoundId(); }
        @Override public Optional<RecipeHooksView> recipeHooks() {
            return delegate.behavior() instanceof RecipeBehavior recipe
                    ? Optional.of(new RecipeView(recipe)) : Optional.empty();
        }
        @Override public Optional<TickHooksView> tickHooks() {
            return delegate.behavior() instanceof TickBehavior tick
                    ? Optional.of(new TickView(tick)) : Optional.empty();
        }
        @Override public Set<Identifier> requestProcessorIds() { return delegate.requestProcessors().keySet(); }
        @Override public Set<Identifier> requestFailureIds() { return delegate.requestFailures().keySet(); }
        @Override public Map<Identifier, RequestHandler> requestProcessors() {
            Map<Identifier, RequestHandler> result = new LinkedHashMap<>();
            delegate.requestProcessors().forEach((key, value) -> result.put(key, NetworkAdapters.toPublic(value)));
            return Collections.unmodifiableMap(result);
        }
        @Override public Map<Identifier, FailureHandler> requestFailures() {
            Map<Identifier, FailureHandler> result = new LinkedHashMap<>();
            delegate.requestFailures().forEach((key, value) -> result.put(key, NetworkAdapters.toPublic(value)));
            return Collections.unmodifiableMap(result);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record RecipeView(RecipeBehavior delegate) implements MachineSpec.RecipeHooksView {
        @Override public boolean hasIdleStart() { return delegate.hasIdleStart(); }
        @Override public boolean hasIdleEnd() { return delegate.hasIdleEnd(); }
        @Override public boolean hasBeforeStart() { return delegate.hasBeforeStart(); }
        @Override public boolean hasRecipeTick() { return delegate.hasRecipeTick(); }
        @Override public boolean hasBeforeFinish() { return delegate.hasBeforeFinish(); }
        @Override public boolean hasPreServerTick() { return delegate.hasPreServerTick(); }
        @Override public boolean hasPostServerTick() { return delegate.hasPostServerTick(); }
    }

    /** @author howxu <dev@howxu.cn> */
    private record TickView(TickBehavior delegate) implements MachineSpec.TickHooksView {
        @Override public boolean hasServerTick() { return delegate.hasServerTick(); }
    }
}
