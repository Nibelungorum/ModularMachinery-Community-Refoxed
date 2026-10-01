package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.internal.recipe.RequirementPlanner;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.extension.*;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.OutputMode;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

/** Adapts public extension callbacks into the single core planning/execution pipeline.
 * @author howxu <dev@howxu.cn> */
public final class PlanningAdapters {
    private PlanningAdapters() {}
    public static PlanningView wrap(List<MachineCapability> capabilities, PlanningContext context) { return new Planning(List.copyOf(capabilities), context); }
    public static RequirementPlanSpec wrap(RequirementPlan plan) { return new Plan(plan); }
    public static RequirementPlan unwrap(RequirementPlanSpec plan) { if (plan instanceof Plan view) return view.delegate; throw new IllegalArgumentException("Plan must be library-produced"); }
    public static ExecutionStatus unwrap(PlanStatus status) { if (status instanceof Status view) return view.delegate; throw new IllegalArgumentException("Status must be library-produced"); }
    private static Object unwrap(ResourceStore<?> storage) { if (storage instanceof Resources<?> view) return view.delegate; throw new IllegalArgumentException("Storage must be library-produced"); }
    private static Object unwrap(LongStore storage) { if (storage instanceof Values view) return view.delegate; throw new IllegalArgumentException("Storage must be library-produced"); }
    public static OutputSimulation unwrap(OutputSimulationView simulation) { if (simulation instanceof Simulation view) return view.delegate; throw new IllegalArgumentException("Simulation must be library-produced"); }
    private record Status(ExecutionStatus delegate) implements PlanStatus {
        public ResourceLocation id() { return delegate.id(); }
        public ResourceLocation source() { return delegate.source(); }
        public Map<String, String> details() { return Map.copyOf(delegate.details()); }
    }
    private record Plan(RequirementPlan delegate) implements RequirementPlanSpec {
        public int requirementIndex() { return delegate.requirementIndex(); }
        public long maxParallelism() { return delegate.maxParallelism(); }
        public boolean successful() { return delegate.successful(); }
        public Optional<PlanStatus> failure() { return Optional.ofNullable(delegate.failure()).map(Status::new); }
    }
    private record Capability(MachineCapability delegate) implements CapabilityAccess {
        public ResourceLocation kindId() { return delegate.type().id(); }
        public Set<IoDirection> directions() { return delegate.directions().values().stream().map(v -> switch (v) { case INPUT -> IoDirection.INPUT; case OUTPUT -> IoDirection.OUTPUT; }).collect(Collectors.toUnmodifiableSet()); }
        public int outputPriority() { return delegate.outputPriority(); }
        @SuppressWarnings("unchecked")
        public <R> Optional<ResourceStore<R>> resources(Class<R> type) {
            if (type == ItemStack.class) {
                return delegate.facet(ItemHandlerFacet.class).map(facet -> new Resources<>(facet.itemHandler(), type));
            }
            if (type == FluidStack.class) {
                return delegate.facet(FluidHandlerFacet.class).map(facet -> new Resources<>(facet.fluidHandler(), type));
            }
            return Optional.empty();
        }
        public Optional<LongStore> longValues() {
            EnergyStorageFacet energy = delegate.facet(EnergyStorageFacet.class).orElse(null);
            if (energy != null) return Optional.of(new Values(energy.energyStorage()));
            ValueFacet<?> facet = delegate.facet(ValueFacet.class).orElse(null);
            return facet != null && facet.storage() instanceof LongValueStorage v ? Optional.of(new Values(v)) : Optional.empty();
        }
        public boolean supportsLargeStacks() { ItemHandlerFacet facet = delegate.facet(ItemHandlerFacet.class).orElse(null); return facet != null && facet.supportsLargeStacks(); }
        public <R> RecipeOperation prepareResources(IoDirection io, long parallelism, List<ResourceAction<R>> actions) {
            if (delegate.facet(ItemHandlerFacet.class).isPresent()) {
                return new CoreOperation(delegate.prepare(new CapabilityRequests.ItemRequest(delegate.type(), direction(io), parallelism,
                        actions.stream().map(v -> new CapabilityRequests.ItemAction(v.slot(), (ItemStack) v.resource(), v.amount(), v.insert())).toList())));
            }
            if (delegate.facet(FluidHandlerFacet.class).isPresent()) {
                return new CoreOperation(delegate.prepare(new CapabilityRequests.FluidRequest(delegate.type(), direction(io), parallelism,
                        actions.stream().map(v -> new CapabilityRequests.FluidAction(v.slot(), (FluidStack) v.resource(), v.amount(), v.insert())).toList())));
            }
            return new CoreOperation(delegate.prepare(new CapabilityRequests.ResourceRequest<>(delegate.type(), direction(io), parallelism,
                    actions.stream().map(v -> new CapabilityRequests.ResourceAction<>(v.slot(), v.resource(), v.amount(), v.insert())).toList())));
        }
        public RecipeOperation prepareValue(IoDirection io, long parallelism, long amount, boolean insert) { return new CoreOperation(delegate.prepare(new CapabilityRequests.ValueRequest(delegate.type(), direction(io), parallelism, amount, insert))); }
        public RecipeOperation prepareSmartValue(IoDirection io, long parallelism, String type, float value) { return new CoreOperation(delegate.prepare(new CapabilityRequests.SmartValueRequest(delegate.type(), direction(io), parallelism, type, value))); }
    }
    private static IOType direction(IoDirection value) { return switch (value) { case INPUT -> IOType.INPUT; case OUTPUT -> IOType.OUTPUT; }; }
    private record Resources<R>(Object delegate, Class<R> resourceType) implements ResourceStore<R> {
        public int size() { return delegate instanceof IItemHandler items ? items.getSlots() : ((IFluidHandler) delegate).getTanks(); }
        public R resource(int slot) {
            return resourceType.cast(delegate instanceof IItemHandler items ? items.getStackInSlot(slot).copy() : ((IFluidHandler) delegate).getFluidInTank(slot).copy());
        }
        public long amount(int slot) {
            if (delegate instanceof LongItemStorage items) return items.amount(slot);
            if (delegate instanceof NativeStackSync.Item items) return items.amount(slot);
            if (delegate instanceof LongFluidStorage fluids) return fluids.amount(slot);
            if (delegate instanceof NativeStackSync.Fluid fluids) return fluids.amount(slot);
            return delegate instanceof IItemHandler items ? items.getStackInSlot(slot).getCount() : ((IFluidHandler) delegate).getFluidInTank(slot).getAmount();
        }
        public long capacity(int slot, R resource) {
            if (delegate instanceof LongItemStorage items) return items.capacity(slot);
            if (delegate instanceof NativeStackSync.Item items) return items.capacity(slot);
            if (delegate instanceof LongFluidStorage fluids) return fluids.capacity(slot);
            if (delegate instanceof NativeStackSync.Fluid fluids) return fluids.capacity(slot);
            return delegate instanceof IItemHandler items ? items.getSlotLimit(slot) : ((IFluidHandler) delegate).getTankCapacity(slot);
        }
        public boolean isValid(int slot, R resource) { return delegate instanceof IItemHandler items ? items.isItemValid(slot, (ItemStack) resource) : ((IFluidHandler) delegate).isFluidValid(slot, (FluidStack) resource); }
    }
    private record Values(Object delegate) implements LongStore {
        public long amount() { return delegate instanceof LongValueStorage value ? value.amount() : delegate instanceof LongEnergyHandler energy ? energy.getAmountAsLong() : ((IEnergyStorage) delegate).getEnergyStored(); }
        public long capacity() { return delegate instanceof LongValueStorage value ? value.capacity() : delegate instanceof LongEnergyHandler energy ? energy.getCapacityAsLong() : ((IEnergyStorage) delegate).getMaxEnergyStored(); }
        public long transferLimit() { return delegate instanceof LongValueStorage value ? value.transferLimit() : delegate instanceof LongEnergyHandler energy ? energy.getTransferLimit() : Integer.MAX_VALUE; }
    }
    private record Reservations(PlanningReservations delegate) implements ReservationsView {
        public <R> R resource(ResourceStore<R> storage, int slot) { Object handler = unwrap(storage); return storage.resourceType().cast(handler instanceof IItemHandler items ? delegate.item(items, slot).copy() : delegate.fluid((IFluidHandler) handler, slot).copy()); }
        public long amount(ResourceStore<?> storage, int slot) { Object handler = unwrap(storage); return handler instanceof IItemHandler items ? delegate.itemAmount(items, slot) : delegate.fluidAmount((IFluidHandler) handler, slot); }
        public <R> boolean reserveExtract(ResourceStore<R> storage, int slot, R resource, long amount) { Object handler = unwrap(storage); return handler instanceof IItemHandler items ? delegate.reserveItemExtract(items, slot, (ItemStack) resource, amount) : delegate.reserveFluidExtract((IFluidHandler) handler, slot, (FluidStack) resource, amount); }
        public <R> boolean reserveInsert(ResourceStore<R> storage, int slot, R resource, long amount) { Object handler = unwrap(storage); return handler instanceof IItemHandler items ? delegate.reserveItemInsert(items, slot, (ItemStack) resource, amount, storage.capacity(slot, resource)) : delegate.reserveFluidInsert((IFluidHandler) handler, slot, (FluidStack) resource, amount, storage.capacity(slot, resource)); }
        public <R> long outputAvailable(ResourceStore<R> storage, R key, long capacity) { return delegate.outputAvailable(unwrap(storage), key, capacity); }
        public <R> boolean reserveOutput(ResourceStore<R> storage, R key, long amount) { return delegate.reserveOutput(unwrap(storage), key, amount); }
        public long valueAvailable(LongStore storage, boolean insert) { Object value = unwrap(storage); return value instanceof LongValueStorage scalar ? delegate.valueAvailable(scalar, insert) : delegate.valueAvailable((IEnergyStorage) value, insert); }
        public boolean reserveValue(LongStore storage, long amount, boolean insert) { Object value = unwrap(storage); return value instanceof LongValueStorage scalar ? delegate.reserveValue(scalar, amount, insert) : delegate.reserveValue((IEnergyStorage) value, amount, insert); }
        public boolean reserveValueTotal(LongStore storage, long amount, boolean insert) { Object value = unwrap(storage); return value instanceof LongValueStorage scalar ? delegate.reserveValueTotal(scalar, amount, insert) : delegate.reserveValueTotal((IEnergyStorage) value, amount, insert); }
    }
    private record Simulation(OutputSimulation delegate) implements OutputSimulationView {
        public long requested() { return delegate.requested(); }
        public long accepted() { return delegate.accepted(); }
        public Fit fit() { return switch (delegate.fit()) { case NONE -> Fit.NONE; case PARTIAL -> Fit.PARTIAL; case FULL -> Fit.FULL; }; }
    }
    private record CoreOperation(CapabilityOperation delegate) implements RecipeOperation {
        public OperationResult commit() { CapabilityResult v = delegate.commit(); return v == null ? null : new OperationResult(v.success(), v.status() == null ? null : new Status(v.status())); }
        public RecipeOperation forParallelism(long parallelism) { CapabilityOperation v = delegate.forParallelism(parallelism); return v == null ? null : new CoreOperation(v); }
    }
    private record Operation(RecipeOperation delegate) implements CapabilityOperation {
        public CapabilityResult commit() {
            OperationResult result = delegate.commit();
            return result == null ? null : new CapabilityResult(result.success(), result.failure() == null ? null : unwrap(result.failure()));
        }
        public CapabilityOperation forParallelism(long parallelism) {
            RecipeOperation result = delegate.forParallelism(parallelism);
            return result == null ? null : new Operation(result);
        }
    }
    private record Planning(List<MachineCapability> source, PlanningContext context) implements PlanningView {
        public long requestedParallelism() { return context.requestedParallelism(); }
        public int requirementIndex() { return context.requirementIndex(); }
        public boolean allowPartialOutputs() { return context.allowPartialOutputs(); }
        public OutputMode outputMode() { return switch (context.outputPolicy()) { case REQUIRE_FULL -> OutputMode.REQUIRE_FULL; case ALLOW_PARTIAL -> OutputMode.ALLOW_PARTIAL; }; }
        public List<CapabilityAccess> capabilities() { return source.stream().map(v -> (CapabilityAccess) new Capability(v)).toList(); }
        public ReservationsView reservations() { return new Reservations(context.reservations()); }
        public RequirementPlanSpec plan(RequirementSpec requirement) {
            MachineRequirement value = RequirementAdapters.unwrap(requirement);
            RequirementType<?> type = RequirementHandlerRegistry.canonicalType(value.type());
            if (type != value.type()) throw new IllegalArgumentException("Requirement must have a canonical registered type");
            return wrap(PlanningAdapters.plan(type, value, source, context));
        }
        public RequirementPlanSpec prepared(long maximum, List<RecipeOperation> operations) {
            return wrap(new RequirementPlan(context.requirementIndex(), maximum,
                    operations.stream().map(v -> (CapabilityOperation) new Operation(v)).toList(), null)
                    .preparedAt(context.requestedParallelism()));
        }
        public RequirementPlanSpec blocked(PlanStatus failure) { return wrap(new RequirementPlan(context.requirementIndex(), 0, List.of(), unwrap(failure))); }
        public PlanStatus failure(ResourceLocation id, ResourceLocation source, Map<String, String> details) { return new Status(new ExecutionStatus(id, StatusSeverity.BLOCKED, source, details)); }
        public OutputSimulationView outputSimulation(long requested, long accepted) { OutputSimulation value = RequirementHandlerSupport.outputSimulation(requested, accepted); return value == null ? null : new Simulation(value); }
        public RequirementPlanSpec deferred(long maximum, OperationPlanner operations, ReservationPlanner reservations) {
            RequirementPlan.OperationFactory factory = (parallelism, state) -> {
                OperationBatch result = operations.create(parallelism, new Reservations(state));
                return new RequirementPlan.OperationPlan(result.operations().stream().map(v -> (CapabilityOperation) new Operation(v)).toList(),
                        result.failure() == null ? null : unwrap(result.failure()), result.outputSimulation() == null ? null : unwrap(result.outputSimulation()));
            };
            RequirementPlan.ReservationFactory reservationFactory = reservations == null ? null : new RequirementPlan.ReservationFactory() {
                public ExecutionStatus reserve(long parallelism, PlanningReservations state) { return reserveResult(parallelism, state).failure(); }
                public RequirementPlan.ReservationResult reserveResult(long parallelism, PlanningReservations state) {
                    ReservationCheck result = reservations.reserve(parallelism, new Reservations(state));
                    return new RequirementPlan.ReservationResult(result.failure() == null ? null : unwrap(result.failure()), result.outputSimulation() == null ? null : unwrap(result.outputSimulation()));
                }
            };
            return wrap(new RequirementPlan(context.requirementIndex(), maximum, List.of(), null, factory, reservationFactory));
        }
    }
    @SuppressWarnings("unchecked")
    private static <R extends MachineRequirement> RequirementPlan plan(RequirementType<?> type, MachineRequirement value,
            List<MachineCapability> capabilities, PlanningContext context) {
        return ((RequirementType<R>) type).handler().plan((R) value,
                RequirementPlanner.matchingCapabilities(value, capabilities), context);
    }
    public static <T> RequirementHandler.ResourceWakeup wakeup(ResourceWakeupSpec<T> spec) {
        RequirementHandler.WakeupReason reason = switch (spec.reason()) {
            case INPUT_AVAILABLE -> RequirementHandler.WakeupReason.INPUT_AVAILABLE;
            case ENERGY_AVAILABLE -> RequirementHandler.WakeupReason.ENERGY_AVAILABLE;
            case OUTPUT_CAPACITY -> RequirementHandler.WakeupReason.OUTPUT_CAPACITY;
        };
        return new RequirementHandler.ResourceWakeup(spec.failureReasonIds(), reason,
                resource -> {
                    Object value = resource instanceof CapabilityType type ? type.id() : resource;
                    return spec.resourceType().isInstance(value)
                            && spec.matcher().test(new Change<>(spec.resourceType().cast(value), spec.reason()));
                });
    }
    private record Change<T>(T resource, ResourceWakeupSpec.Reason reason) implements ResourceChangeView<T> {}
}
