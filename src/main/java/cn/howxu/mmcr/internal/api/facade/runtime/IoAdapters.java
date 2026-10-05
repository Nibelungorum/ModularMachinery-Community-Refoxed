package cn.howxu.mmcr.internal.api.facade.runtime;

import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureTrace;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.internal.api.facade.presentation.PresentationAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.presentation.IoDisplay;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Holds core IO delegates; planning, commit consumption and rollback remain core-owned.
 * @author howxu <dev@howxu.cn>
 */
public final class IoAdapters {
    private IoAdapters() {}
    public static IoSnapshot wrap(MachineIoView value) { return new SnapshotAdapter(Objects.requireNonNull(value)); }
    public static IoTransaction wrap(MachineIoPlan value) { return new TransactionAdapter(Objects.requireNonNull(value)); }
    public static @Nullable RuntimeFailure wrap(@Nullable ExecutionStatus value) { return value == null ? null : new FailureAdapter(value); }
    public static OutputPolicy unwrap(OutputMode value) {
        return switch (value) {
            case REQUIRE_FULL -> OutputPolicy.REQUIRE_FULL;
            case ALLOW_PARTIAL -> OutputPolicy.ALLOW_PARTIAL;
        };
    }
    private static OutputAcceptance wrap(OutputSimulation value) { return new AcceptanceAdapter(value); }
    private static StressState wrap(cn.howxu.mmcr.api.compat.create.StressState value) {
        return new StressState(value.position(), value.actualRpm(), value.theoreticalRpm(), value.generatedRpm(),
                value.baseContribution(), value.actualContribution(), value.networkCapacity(), value.networkStress(),
                value.connected(), value.overstressed());
    }
    private record SnapshotAdapter(MachineIoView delegate) implements IoSnapshot {
        public IoSnapshot forTags(Set<String> tags) { return wrap(delegate.forTags(tags)); }
        public List<IoDisplay> displays() { return delegate.displays().stream().map(PresentationAdapters::wrap).toList(); }
        public List<ResourceAmount<ItemStack>> itemInputs() { return delegate.itemInputs().stream().map(v -> new ResourceAmount<>(v.resource().copy(), v.amount())).toList(); }
        public List<ResourceAmount<FluidStack>> fluidInputs() { return delegate.fluidInputs().stream().map(v -> new ResourceAmount<>(v.resource().copy(), v.amount())).toList(); }
        public List<ResourceAmount<ResourceLocation>> chemicalInputs() { return delegate.chemicalInputs().stream().map(v -> new ResourceAmount<>(v.resource(), v.amount())).toList(); }
        public long chemicalAmount(ResourceLocation id) { return delegate.chemicalAmount(id); }
        public long chemicalTagAmount(ResourceLocation id) { return delegate.chemicalTagAmount(id); }
        public long chemicalOutputCapacity(ResourceLocation id) { return delegate.chemicalOutputCapacity(id); }
        public List<HeatState> heatInputs() { return delegate.heatInputs().stream().map(v -> new HeatState(v.heat(), v.temperature(), v.heatCapacity())).toList(); }
        public List<HeatState> heatOutputs() { return delegate.heatOutputs().stream().map(v -> new HeatState(v.heat(), v.temperature(), v.heatCapacity())).toList(); }
        public List<StressState> stressInputs() { return delegate.stressInputs().stream().map(IoAdapters::wrap).toList(); }
        public List<StressState> stressOutputs() { return delegate.stressOutputs().stream().map(IoAdapters::wrap).toList(); }
        public long energyInput() { return delegate.energyInput(); }
        public long sourceInput() { return delegate.sourceInput(); }
        public long sourceOutputCapacity() { return delegate.sourceOutputCapacity(); }
        public long manaInput() { return delegate.manaInput(); }
        public long manaOutputCapacity() { return delegate.manaOutputCapacity(); }
        public long itemAmount(Ingredient ingredient) { return delegate.itemAmount(ingredient); }
        public long fluidAmount(FluidIngredient ingredient) { return delegate.fluidAmount(ingredient); }
        public long itemOutputCapacity(ItemStack stack) { return delegate.itemOutputCapacity(stack); }
        public long fluidOutputCapacity(FluidStack stack) { return delegate.fluidOutputCapacity(stack); }
        public long energyOutputCapacity() { return delegate.energyOutputCapacity(); }
        public Optional<Float> smartInterfaceValue(String name) { return delegate.smartInterfaceValue(name); }
        public Map<String, Float> smartInterfaceValues() { return delegate.smartInterfaceValues(); }
    }
    private record TransactionAdapter(MachineIoPlan delegate) implements IoTransaction {
        public IoSnapshot view() { return wrap(delegate.view()); }
        public IoTransaction addInput(RequirementSpec value) { delegate.addInput(RequirementAdapters.unwrap(value)); return this; }
        public IoTransaction addOutput(RequirementSpec value, OutputMode mode) { delegate.addOutput(RequirementAdapters.unwrap(value), mode == null ? null : unwrap(mode)); return this; }
        public IoTransaction add(RequirementSpec value) { delegate.add(RequirementAdapters.unwrap(value)); return this; }
        public List<RequirementSpec> requirements() { return delegate.requirements().stream().map(RequirementAdapters::wrap).toList(); }
        public IoSimulation simulate() { return new SimulationAdapter(delegate.simulate()); }
        public IoCommitResult commit() { return new CommitAdapter(delegate.commit()); }
        public IoCommitResult commit(Consumer<DataStore.Transaction> writes) {
            Objects.requireNonNull(writes, "writes");
            return new CommitAdapter(delegate.commit(transaction -> writes.accept(StorageAdapters.wrap(transaction))));
        }
        public IoCommitResult commitData(Consumer<DataStore.Transaction> writes) {
            Objects.requireNonNull(writes, "writes");
            return new CommitAdapter(delegate.commitData(transaction -> writes.accept(StorageAdapters.wrap(transaction))));
        }
        public List<OutputAcceptance> outputSimulations() { return delegate.outputSimulations().stream().map(IoAdapters::wrap).toList(); }
        public boolean inputsSatisfied() { return delegate.inputsSatisfied(); }
        public boolean energySatisfied() { return delegate.energySatisfied(); }
    }
    private record SimulationAdapter(MachineIoPlan.Simulation delegate) implements IoSimulation {
        public boolean inputsSatisfied() { return delegate.inputsSatisfied(); }
        public boolean energySatisfied() { return delegate.energySatisfied(); }
        public List<OutputAcceptance> outputs() { return delegate.outputs().stream().map(IoAdapters::wrap).toList(); }
        public @Nullable RuntimeFailure failure() { return wrap(delegate.failure()); }
    }
    private record CommitAdapter(MachineIoPlan.CommitResult delegate) implements IoCommitResult {
        public boolean successful() { return delegate.successful(); }
        public @Nullable RuntimeFailure failure() { return wrap(delegate.failure()); }
    }
    private record AcceptanceAdapter(OutputSimulation delegate) implements OutputAcceptance {
        public long requested() { return delegate.requested(); }
        public long accepted() { return delegate.accepted(); }
        public OutputFit fit() {
            return switch (delegate.fit()) {
                case FULL -> OutputFit.FULL; case PARTIAL -> OutputFit.PARTIAL; case NONE -> OutputFit.NONE;
            };
        }
    }
    private record FailureAdapter(ExecutionStatus delegate) implements RuntimeFailure {
        public ResourceLocation id() { return delegate.id(); }
        public FailureSeverity severity() {
            return switch (delegate.severity()) {
                case INFO -> FailureSeverity.INFO; case BLOCKED -> FailureSeverity.BLOCKED; case FAILURE -> FailureSeverity.FAILURE;
            };
        }
        public ResourceLocation source() { return delegate.source(); }
        public Map<String, String> details() { return delegate.details(); }
        public @Nullable ResourceLocation reasonId() { return delegate.reason() == null ? null : delegate.reason().id(); }
        public @Nullable String reasonTranslationKey() { return delegate.reason() == null ? null : delegate.reason().translationKey(); }
        public @Nullable Integer reasonPriority() { return delegate.reason() == null ? null : delegate.reason().priority(); }
        public List<FailureFrame> trace() {
            return delegate.failure() == null ? List.of() : delegate.failure().trace().frames().stream()
                    .<FailureFrame>map(FrameAdapter::new).toList();
        }
    }
    private record FrameAdapter(FailureTrace.Frame delegate) implements FailureFrame {
        public ResourceLocation source() { return delegate.source(); }
        public FailurePhase phase() {
            return switch (delegate.phase()) {
                case CAPABILITY_PREPARE -> FailurePhase.CAPABILITY_PREPARE;
                case CAPABILITY_COMMIT -> FailurePhase.CAPABILITY_COMMIT;
                case REQUIREMENT_PLAN -> FailurePhase.REQUIREMENT_PLAN;
                case LEVEL_CHECK -> FailurePhase.LEVEL_CHECK; case RECIPE_SEARCH -> FailurePhase.RECIPE_SEARCH;
                case RECIPE_LOAD -> FailurePhase.RECIPE_LOAD; case RECIPE_START -> FailurePhase.RECIPE_START;
                case PER_TICK -> FailurePhase.PER_TICK; case FINISH -> FailurePhase.FINISH;
                case RUNTIME -> FailurePhase.RUNTIME; case UNKNOWN -> FailurePhase.UNKNOWN;
            };
        }
        public @Nullable ResourceLocation recipeId() { return delegate.recipeId(); }
        public @Nullable Integer requirementIndex() { return delegate.requirementIndex(); }
    }
}
