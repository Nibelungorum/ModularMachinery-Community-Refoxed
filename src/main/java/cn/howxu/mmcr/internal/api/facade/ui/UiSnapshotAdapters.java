package cn.howxu.mmcr.internal.api.facade.ui;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import cn.howxu.mmcr.publicapi.runtime.RuntimeFailure;
import cn.howxu.mmcr.publicapi.ui.ControllerUiSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thin presentation boundary; ownership and capture remain in the core API.
 * @author howxu <dev@howxu.cn>
 */
public final class UiSnapshotAdapters {
    private UiSnapshotAdapters() {}

    public static ControllerUiSnapshot wrap(cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot value) {
        return new SnapshotAdapter(Objects.requireNonNull(value, "value"));
    }

    public static cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot unwrap(ControllerUiSnapshot value) {
        if (value instanceof SnapshotAdapter adapter) return adapter.delegate;
        throw new IllegalArgumentException("Snapshot must be library-produced");
    }

    /** Delegates semantic fields and adapts existing data/output/failure boundaries.
     * @author howxu <dev@howxu.cn> */
    private record SnapshotAdapter(cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot delegate)
            implements ControllerUiSnapshot {
        public UUID sessionId() { return delegate.sessionId(); }
        public long revision() { return delegate.revision(); }
        public boolean ready() { return delegate.ready(); }
        public ResourceKey<Level> dimension() { return delegate.dimension(); }
        public BlockPos controllerPos() { return delegate.controllerPos(); }
        public Identifier machineId() { return delegate.machineId(); }
        public Kind kind() {
            return switch (delegate.kind()) { case NORMAL -> Kind.NORMAL; case TICK -> Kind.TICK; case FACTORY -> Kind.FACTORY; };
        }
        public Role role() {
            return switch (delegate.role()) { case NORMAL -> Role.NORMAL; case HOST -> Role.HOST; case MODULE -> Role.MODULE; };
        }
        public Component machineName() { return delegate.machineName(); }
        public boolean formed() { return delegate.formed(); }
        public boolean active() { return delegate.active(); }
        public boolean redstonePaused() { return delegate.redstonePaused(); }
        public int installedModuleCount() { return delegate.installedModuleCount(); }
        public Optional<Identifier> connectedHostId() { return delegate.connectedHostId(); }
        public int matchedStage() { return delegate.matchedStage(); }
        public int stageCount() { return delegate.stageCount(); }
        public List<Identifier> foundLevelIds() { return delegate.foundLevelIds(); }
        public int parallelSlots() { return delegate.parallelSlots(); }
        public long maxParallelism() { return delegate.maxParallelism(); }
        public int threadLimit() { return delegate.threadLimit(); }
        public int activeThreadCount() { return delegate.activeThreadCount(); }
        public List<Identifier> recipePoolIds() { return delegate.recipePoolIds(); }
        public Optional<Identifier> currentRecipePoolId() { return delegate.currentRecipePoolId(); }
        public Optional<RuntimeFailure> failure() { return delegate.failure().map(IoAdapters::wrap); }
        public boolean hasDataStorage() { return delegate.hasDataStorage(); }
        public Map<String, DataKey> dataStorageValues() { return StorageAdapters.values(delegate.dataStorageValues()); }
        public List<TextLine> lines() { return delegate.lines().stream().<TextLine>map(TextAdapter::new).toList(); }
        public List<Lane> lanes() { return delegate.lanes().stream().<Lane>map(LaneAdapter::new).toList(); }
    }

    /** Lane facade retains no UI-local state.
     * @author howxu <dev@howxu.cn> */
    private record LaneAdapter(cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Lane delegate)
            implements ControllerUiSnapshot.Lane {
        public String id() { return delegate.id(); }
        public int index() { return delegate.index(); }
        public boolean base() { return delegate.base(); }
        public boolean core() { return delegate.core(); }
        public boolean active() { return delegate.active(); }
        public Optional<Identifier> recipeId() { return delegate.recipeId(); }
        public int tick() { return delegate.tick(); }
        public int totalTick() { return delegate.totalTick(); }
        public long parallelism() { return delegate.parallelism(); }
        public Optional<RuntimeFailure> failure() { return delegate.failure().map(IoAdapters::wrap); }
        public List<ControllerUiSnapshot.TextLine> lines() {
            return delegate.lines().stream().<ControllerUiSnapshot.TextLine>map(TextAdapter::new).toList();
        }
        public ControllerUiSnapshot.RecipePresentation recipe() { return new RecipeAdapter(delegate.recipe()); }
    }

    /** Recipe facade preserves pre-scaled rates and expected amounts.
     * @author howxu <dev@howxu.cn> */
    private record RecipeAdapter(cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.RecipePresentation delegate)
            implements ControllerUiSnapshot.RecipePresentation {
        public List<ControllerUiSnapshot.Output> outputs() {
            return delegate.outputs().stream().<ControllerUiSnapshot.Output>map(OutputAdapter::new).toList();
        }
        public long energyInputPerTick() { return delegate.energyInputPerTick(); }
        public long energyOutputPerTick() { return delegate.energyOutputPerTick(); }
        public double heatOutputPerTick() { return delegate.heatOutputPerTick(); }
        public int durationTicks() { return delegate.durationTicks(); }
        public long parallelism() { return delegate.parallelism(); }
    }

    /** Built-in stack getters independently read the core's frozen resource, even on the same public view.
     * @author howxu <dev@howxu.cn> */
    private record OutputAdapter(cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Output delegate)
            implements ControllerUiSnapshot.Output {
        public OutputView resource() {
            MachineOutput resource = delegate.resource();
            return resource instanceof MachineOutput.ItemOutput || resource instanceof MachineOutput.FluidOutput
                    ? OutputAdapters.wrap(resource, delegate::resource) : OutputAdapters.wrap(resource);
        }
        public long amount() { return delegate.amount(); }
    }

    /** Text facade relies on the core's copy-on-read contract.
     * @author howxu <dev@howxu.cn> */
    private record TextAdapter(cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.TextLine delegate)
            implements ControllerUiSnapshot.TextLine {
        public Identifier id() { return delegate.id(); }
        public Scope scope() {
            return switch (delegate.scope()) { case CONTROLLER -> Scope.CONTROLLER; case OPERATION -> Scope.OPERATION; };
        }
        public Component text() { return delegate.text(); }
    }
}
