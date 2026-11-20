package cn.howxu.mmcr.internal.api.facade.behavior;

import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehaviorContext;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.internal.api.facade.presentation.PresentationAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.publicapi.behavior.*;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.presentation.ControllerText;
import cn.howxu.mmcr.publicapi.presentation.JadeText;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Internal Java boundary holding authoritative core contexts and builders.
 * @author howxu <dev@howxu.cn>
 */
public final class BehaviorAdapters {
    private BehaviorAdapters() {}
    public static RecipeBehavior.Builder configureRecipe(RecipeBehavior.Builder builder, Consumer<RecipeHooks> configuration) {
        Objects.requireNonNull(configuration, "configuration").accept(new RecipeHooksAdapter(Objects.requireNonNull(builder)));
        return builder;
    }
    public static TickBehavior.Builder configureTick(TickBehavior.Builder builder, Consumer<TickHooks> configuration) {
        Objects.requireNonNull(configuration, "configuration").accept(new TickHooksAdapter(Objects.requireNonNull(builder)));
        return builder;
    }
    public static MachineContext wrap(MachineBehaviorContext value) { return new MachineAdapter(Objects.requireNonNull(value)); }
    public static TickContext wrap(TickBehaviorContext value) { return new TickAdapter(Objects.requireNonNull(value)); }
    public static RecipeStartContext wrap(cn.howxu.mmcr.api.machine.definition.RecipeStartContext value) { return new StartAdapter(value); }
    public static RecipeTickContext wrap(cn.howxu.mmcr.api.machine.definition.RecipeTickContext value) { return new RecipeTickAdapter(value); }
    public static RecipeFinishContext wrap(cn.howxu.mmcr.api.machine.definition.RecipeFinishContext value) { return new FinishAdapter(value); }
    public static MachineBehaviorContext unwrap(MachineContext value) { return ((MachineAdapter) value).delegate; }

    private static <P, H> Supplier<H> adaptFactory(Supplier<? extends Consumer<P>> factory, Function<Consumer<P>, H> adapt) {
        Objects.requireNonNull(factory, "factory");
        return () -> adapt.apply(Objects.requireNonNull(factory.get(), "hook factory returned null"));
    }

    public static Supplier<MachineBehavior.MachineCallback> machineCallbackFactory(Supplier<? extends Consumer<MachineContext>> factory) {
        return adaptFactory(factory, handler -> c -> handler.accept(wrap(c)));
    }

    public static <T> RecipeHooks.ForInstance<T> wrap(RecipeBehavior.InstanceBuilder<T> builder) {
        return new InstanceHooksAdapter<>(builder);
    }

    /** @author howxu <dev@howxu.cn> */
    private record InstanceHooksAdapter<T>(RecipeBehavior.InstanceBuilder<T> delegate) implements RecipeHooks.ForInstance<T> {
        private <P, C> BiConsumer<T, C> adapt(BiConsumer<T, P> callback, Function<C, P> wrap) {
            Objects.requireNonNull(callback, "callback");
            return (instance, c) -> callback.accept(instance, wrap.apply(c));
        }
        public RecipeHooks.ForInstance<T> idleStart(BiConsumer<T, MachineContext> callback) {
            delegate.idleStart(adapt(callback, BehaviorAdapters::wrap)); return this;
        }
        public RecipeHooks.ForInstance<T> idleEnd(BiConsumer<T, MachineContext> callback) {
            delegate.idleEnd(adapt(callback, BehaviorAdapters::wrap)); return this;
        }
        public RecipeHooks.ForInstance<T> beforeStart(BiConsumer<T, RecipeStartContext> callback) {
            delegate.beforeStart(adapt(callback, BehaviorAdapters::wrap)); return this;
        }
        public RecipeHooks.ForInstance<T> recipeTick(BiConsumer<T, RecipeTickContext> callback) {
            delegate.recipeTick(adapt(callback, BehaviorAdapters::wrap)); return this;
        }
        public RecipeHooks.ForInstance<T> beforeFinish(BiConsumer<T, RecipeFinishContext> callback) {
            delegate.beforeFinish(adapt(callback, BehaviorAdapters::wrap)); return this;
        }
        public RecipeHooks.ForInstance<T> preServerTick(BiConsumer<T, MachineContext> callback) {
            delegate.preServerTick(adapt(callback, BehaviorAdapters::wrap)); return this;
        }
        public RecipeHooks.ForInstance<T> postServerTick(BiConsumer<T, MachineContext> callback) {
            delegate.postServerTick(adapt(callback, BehaviorAdapters::wrap)); return this;
        }
    }

    private record RecipeHooksAdapter(RecipeBehavior.Builder delegate) implements RecipeHooks {
        public RecipeHooks idleStart(Supplier<? extends Consumer<MachineContext>> factory) {
            delegate.idleStart(machineCallbackFactory(factory)); return this;
        }
        public RecipeHooks idleStart(Consumer<MachineContext> callback) {
            Objects.requireNonNull(callback, "idleStart"); delegate.idleStart(c -> callback.accept(wrap(c))); return this;
        }
        public RecipeHooks idleEnd(Supplier<? extends Consumer<MachineContext>> factory) {
            delegate.idleEnd(machineCallbackFactory(factory)); return this;
        }
        public RecipeHooks idleEnd(Consumer<MachineContext> callback) {
            Objects.requireNonNull(callback, "idleEnd"); delegate.idleEnd(c -> callback.accept(wrap(c))); return this;
        }
        public RecipeHooks beforeStart(Supplier<? extends Consumer<RecipeStartContext>> factory) {
            delegate.beforeStart(adaptFactory(factory, handler -> c -> handler.accept(wrap(c)))); return this;
        }
        public RecipeHooks beforeStart(Consumer<RecipeStartContext> callback) {
            Objects.requireNonNull(callback, "beforeStart"); delegate.beforeStart(c -> callback.accept(wrap(c))); return this;
        }
        public RecipeHooks recipeTick(Supplier<? extends Consumer<RecipeTickContext>> factory) {
            delegate.recipeTick(adaptFactory(factory, handler -> c -> handler.accept(wrap(c)))); return this;
        }
        public RecipeHooks recipeTick(Consumer<RecipeTickContext> callback) {
            Objects.requireNonNull(callback, "recipeTick"); delegate.recipeTick(c -> callback.accept(wrap(c))); return this;
        }
        public RecipeHooks beforeFinish(Supplier<? extends Consumer<RecipeFinishContext>> factory) {
            delegate.beforeFinish(adaptFactory(factory, handler -> c -> handler.accept(wrap(c)))); return this;
        }
        public RecipeHooks beforeFinish(Consumer<RecipeFinishContext> callback) {
            Objects.requireNonNull(callback, "beforeFinish"); delegate.beforeFinish(c -> callback.accept(wrap(c))); return this;
        }
        public RecipeHooks preServerTick(Supplier<? extends Consumer<MachineContext>> factory) {
            delegate.preServerTick(machineCallbackFactory(factory)); return this;
        }
        public RecipeHooks preServerTick(Consumer<MachineContext> callback) {
            Objects.requireNonNull(callback, "preServerTick"); delegate.preServerTick(c -> callback.accept(wrap(c))); return this;
        }
        public RecipeHooks postServerTick(Supplier<? extends Consumer<MachineContext>> factory) {
            delegate.postServerTick(machineCallbackFactory(factory)); return this;
        }
        public RecipeHooks postServerTick(Consumer<MachineContext> callback) {
            Objects.requireNonNull(callback, "postServerTick"); delegate.postServerTick(c -> callback.accept(wrap(c))); return this;
        }
    }
    private record TickHooksAdapter(TickBehavior.Builder delegate) implements TickHooks {
        public TickHooks serverTick(Supplier<? extends Consumer<TickContext>> factory) {
            delegate.serverTick(adaptFactory(factory, handler -> c -> handler.accept(wrap(c)))); return this;
        }
        public TickHooks serverTick(Consumer<TickContext> callback) {
            Objects.requireNonNull(callback, "serverTick"); delegate.serverTick(c -> callback.accept(wrap(c))); return this;
        }
    }
    private static class MachineAdapter implements MachineContext {
        final MachineBehaviorContext delegate;
        MachineAdapter(MachineBehaviorContext delegate) { this.delegate = delegate; }
        public @Nullable MachineView controller() { return delegate.controller() == null ? null : new ControllerAdapter(delegate); }
        public ServerLevel level() { return delegate.level(); }
        public BlockPos controllerPos() { return delegate.controllerPos(); }
        public @Nullable Identifier machineId() { return delegate.machineId(); }
        public long gameTime() { return delegate.gameTime(); }
        public boolean isDue(long period) { return delegate.isDue(period); }
        public ControllerText screenText() { return PresentationAdapters.wrap(delegate.screenText()); }
        public @Nullable DataStore dataStorage() {
            var storage = delegate.dataStorage();
            return storage == null ? null : StorageAdapters.wrap(storage);
        }
        public IoSnapshot ioView() { return IoAdapters.wrap(delegate.ioView()); }
        public List<ItemStack> upgradeItems() { return delegate.upgradeItems(); }
        public JadeText jadeText() { return PresentationAdapters.wrap(delegate.jadeText()); }
        public long countStructureBlocks(Block block) { return delegate.countStructureBlocks(block); }
        public long countStructureBlocks(Identifier blockId) { return delegate.countStructureBlocks(blockId); }
    }
    private static final class TickAdapter extends MachineAdapter implements TickContext {
        private final TickBehaviorContext tick;
        TickAdapter(TickBehaviorContext tick) { super(tick); this.tick = tick; }
        public int factoryThreadCount() { return tick.factoryThreadCount(); }
        public long parallelism() { return tick.parallelism(); }
        public Optional<Float> smartInterfaceValue(String name) { return tick.smartInterfaceValue(name); }
        public Map<String, Float> smartInterfaceValues() { return tick.smartInterfaceValues(); }
        public IoTransaction ioPlan() { return IoAdapters.wrap(tick.ioPlan()); }
    }
    private record ControllerAdapter(MachineBehaviorContext delegate) implements MachineView {
        public @Nullable Identifier machineId() { return delegate.machineId(); }
        public BlockPos controllerPos() { return delegate.controllerPos(); }
        public boolean formed() { return delegate.controller().currentStructureSnapshot().formed(); }
        public long countStructureBlocks(Block block) { return delegate.countStructureBlocks(block); }
        public long countStructureBlocks(Identifier id) { return delegate.countStructureBlocks(id); }
    }
    private record StartAdapter(cn.howxu.mmcr.api.machine.definition.RecipeStartContext delegate) implements RecipeStartContext {
        public RecipeView recipe() { return RecipeAdapters.wrap(delegate.recipe()); }
        public MachineContext machineContext() { return wrap(delegate.machineContext()); }
        public Identifier recipeId() { return delegate.recipeId(); }
        public long requestedParallelism() { return delegate.requestedParallelism(); }
        public long effectiveParallelism() { return delegate.effectiveParallelism(); }
        public int duration() { return delegate.duration(); }
        public void setDuration(int ticks) { delegate.setDuration(ticks); }
        public boolean replaceExactItemInputCount(Item item, int expected, int replacement) { return delegate.replaceExactItemInputCount(item, expected, replacement); }
        public List<RequirementSpec> requirements() { return delegate.requirements().stream().map(RequirementAdapters::wrap).toList(); }
        public void setRequirements(List<RequirementSpec> values) { delegate.setRequirements(values.stream().map(RequirementAdapters::unwrap).toList()); }
        public List<OutputView> outputs() { return delegate.outputs().stream().map(OutputAdapters::wrap).toList(); }
        public void setOutputs(List<OutputView> values) { delegate.setOutputs(values.stream().map(OutputAdapters::unwrap).toList()); }
        public RecipeExecutionView snapshot() { return new ExecutionAdapter(delegate.snapshot()); }
        public void cancel() { delegate.cancel(); }
        public boolean cancelled() { return delegate.cancelled(); }
    }
    private record RecipeTickAdapter(cn.howxu.mmcr.api.machine.definition.RecipeTickContext delegate) implements RecipeTickContext {
        public MachineContext machineContext() { return wrap(delegate.machineContext()); }
        public RecipeView recipe() { return RecipeAdapters.wrap(delegate.recipe()); }
        public int currentTick() { return delegate.currentTick(); }
        public int totalTick() { return delegate.totalTick(); }
        public long parallelism() { return delegate.parallelism(); }
        public List<RequirementSpec> requirements() { return delegate.requirements().stream().map(RequirementAdapters::wrap).toList(); }
        public List<OutputView> outputs() { return delegate.outputs().stream().map(OutputAdapters::wrap).toList(); }
        public IoSnapshot ioSnapshot() { return IoAdapters.wrap(new MachineIoView(delegate.capabilitySnapshot())); }
    }
    private record FinishAdapter(cn.howxu.mmcr.api.machine.definition.RecipeFinishContext delegate) implements RecipeFinishContext {
        public RecipeView recipe() { return RecipeAdapters.wrap(delegate.recipe()); }
        public MachineContext machineContext() { return wrap(delegate.machineContext()); }
        public Identifier recipeId() { return delegate.recipeId(); }
        public long requestedParallelism() { return delegate.requestedParallelism(); }
        public long effectiveParallelism() { return delegate.effectiveParallelism(); }
        public List<OutputView> outputs() { return delegate.outputs().stream().map(OutputAdapters::wrap).toList(); }
        public void setOutputs(List<OutputView> values) { delegate.setOutputs(values.stream().map(OutputAdapters::unwrap).toList()); }
        public void discardOutputs() { delegate.discardOutputs(); }
        public boolean outputsDiscarded() { return delegate.outputsDiscarded(); }
        public void cancel() { delegate.cancel(); }
        public boolean cancelled() { return delegate.cancelled(); }
    }
    private record ExecutionAdapter(cn.howxu.mmcr.api.machine.definition.RecipeStartContext.ExecutionSnapshot delegate) implements RecipeExecutionView {
        public int duration() { return delegate.duration(); }
        public List<RequirementSpec> requirements() { return delegate.requirements().stream().map(RequirementAdapters::wrap).toList(); }
        public List<OutputView> outputs() { return delegate.outputs().stream().map(OutputAdapters::wrap).toList(); }
    }
}
