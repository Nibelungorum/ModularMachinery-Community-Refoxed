package cn.howxu.mmcr.compat.appmek.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalViewFacet;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalHandlerPort;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalOutputAdmission;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * MMCR's multi-slot chemical view of an existing ME interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MEChemicalCapability implements ChemicalHandlerPort, ChemicalViewFacet, OperationFacet, TransferFacet, SyncFacet, PresentationFacet {
    private static final CapabilityType TYPE = new CapabilityType(MekanismRecipeTypes.CHEMICAL);
    private final IOPortBlockEntity host;
    private final IChemicalHandler handler;
    private final CapabilityDirections directions;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public MEChemicalCapability(@Nullable IOPortBlockEntity host, IChemicalHandler handler,
                                CapabilityDirections directions, boolean exposeTransferFacet) {
        this.host = host;
        this.handler = Objects.requireNonNull(handler, "handler");
        this.directions = Objects.requireNonNull(directions, "directions");
        asyncPlanning = handler instanceof InventoryChemicalHandler && directions.supports(IOType.INPUT)
                ? new AsyncPlanningFacet() {
                    @Override public Object planningIdentity() { return ((InventoryChemicalHandler) handler).reservationIdentity(); }
                    @Override protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                        List<AsyncCapabilitySnapshot.ResourceSlot> slots = new ArrayList<>();
                        for (int slot = 0; slot < handler.getChemicalTanks(); slot++) {
                            ChemicalStack stack = handler.getChemicalInTank(slot);
                            slots.add(new AsyncCapabilitySnapshot.ResourceSlot(stack.isEmpty() ? Optional.empty()
                                    : Optional.of(NativeAsyncResourceValues.chemical(stack.copyWithAmount(1L))),
                                    stack.getAmount(), handler.getChemicalTankCapacity(slot)));
                        }
                        return new AsyncCapabilitySnapshot.Resource(TYPE.id(), slots);
                    }
                    @Override protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                        return new AsyncCapabilityPlanner.Resource(TYPE.id());
                    }
                    @Override protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                        return commitAsync(operation);
                    }
                } : null;
        Set<Class<? extends CapabilityFacet>> facets = new HashSet<>(Set.of(ChemicalViewFacet.class,
                OperationFacet.class, SyncFacet.class, PresentationFacet.class));
        if (exposeTransferFacet) facets.add(TransferFacet.class);
        if (asyncPlanning != null) facets.add(AsyncPlanningFacet.class);
        view = CapabilityFactories.view(TYPE, directions, Set.copyOf(facets));
    }

    @Override public CapabilityType type() { return TYPE; }
    @Override public CapabilityDirections directions() { return directions; }
    @Override public CapabilityView view() { return view; }
    @Override public IChemicalHandler chemicalHandler() { return handler; }
    @Override public boolean supportsRadioactivity(boolean radioactive) { return true; }
    @Override public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.ofNullable(asyncPlanning).map(facetType::cast);
        return ChemicalHandlerPort.super.facet(facetType);
    }
    @Override public int outputPriority() { return host == null ? 0 : host.kind().outputPriority(); }
    @Override public @Nullable Level level() { return host == null ? null : host.getLevel(); }
    @Override public BlockPos position() { return host == null ? BlockPos.ZERO : host.getBlockPos(); }
    @Override public long transferLimit() { return Long.MAX_VALUE; }

    @Override public Object queryIdentity() {
        return level() != null && level().isClientSide() ? handler : planningIdentity();
    }

    @Override public List<ChemicalAmount> contents() { return contents(null); }
    @Override public List<ChemicalAmount> tagContents(ResourceLocation tagId) { return contents(tagId); }
    @Override public boolean matchesTag(ResourceLocation tagId) { return !tagContents(tagId).isEmpty(); }

    private List<ChemicalAmount> contents(@Nullable ResourceLocation tagId) {
        Map<ResourceLocation, Long> amounts = new LinkedHashMap<>();
        for (int slot = 0; slot < handler.getChemicalTanks(); slot++) {
            ChemicalStack stack = handler.getChemicalInTank(slot);
            if (stack.isEmpty() || tagId != null
                    && !stack.getChemicalHolder().is(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagId))) continue;
            ResourceLocation id = ResourceLocation.parse(stack.getChemicalHolder().getRegisteredName());
            amounts.merge(id, stack.getAmount(), RequirementHandlerSupport::saturatingAdd);
        }
        return amounts.entrySet().stream().map(entry -> new ChemicalAmount(entry.getKey(), entry.getValue())).toList();
    }

    @Override public Optional<ResourceLocation> chemicalId() {
        List<ChemicalAmount> contents = contents();
        return contents.size() == 1 ? Optional.of(contents.getFirst().id()) : Optional.empty();
    }

    @Override public long amount() {
        long amount = 0L;
        for (ChemicalAmount entry : contents()) amount = RequirementHandlerSupport.saturatingAdd(amount, entry.amount());
        return amount;
    }

    @Override public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return contents().stream().map(entry -> new CapabilityDisplay("chemical",
                Long.toString(entry.amount()), "mB", Optional.empty())).toList();
    }

    @Override public long outputCapacity(ResourceLocation id) {
        if (!directions.supports(IOType.OUTPUT)) return 0L;
        return MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id))
                .map(holder -> {
                    ChemicalStack stack = new ChemicalStack(holder, Long.MAX_VALUE);
                    if (handler instanceof ChemicalOutputAdmission admission) {
                        return admission.outputAvailable(stack.copyWithAmount(1L), new PlanningReservations());
                    }
                    return stack.getAmount() - handler.insertChemical(stack, Action.SIMULATE).getAmount();
                }).orElse(0L);
    }

    @Override public CapabilityOperation prepare(CapabilityRequest request) {
        return CapabilityFactories.operation(this, request);
    }

    @Override public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ResourceRequest<?> resourceRequest)) {
            return () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST, 0L, 0L);
        }
        return (NativeCapabilityOperation) () -> {
            for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                if (!(action.resource() instanceof ChemicalStack stack) || stack.isEmpty() || action.amount() <= 0L
                        || !directions.supports(action.insert() ? IOType.OUTPUT : IOType.INPUT)
                        || action.slot() < 0 && !(action.insert() && action.slot() == -1
                        && handler instanceof ChemicalOutputAdmission)
                        || action.slot() >= handler.getChemicalTanks()) {
                    return failure(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH, action.amount(), 0L);
                }
                long simulated = move(action.slot(), stack, action.amount(), action.insert(), Action.SIMULATE);
                if (simulated != action.amount()) return unavailable(action, simulated);
                long committed = move(action.slot(), stack, action.amount(), action.insert(), Action.EXECUTE);
                if (committed != action.amount()) return unavailable(action, committed);
            }
            return CapabilityResult.successful();
        };
    }

    private CapabilityResult commitAsync(AsyncCapabilityOperation operation) {
        if (operation instanceof AsyncCapabilityOperation.Group group) {
            for (AsyncCapabilityOperation child : group.operations()) {
                CapabilityResult result = commitAsync(child);
                if (!result.success()) return result;
            }
            return CapabilityResult.successful();
        }
        if (!(operation instanceof AsyncCapabilityOperation.Resource resource) || !TYPE.id().equals(resource.capabilityId())) {
            return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST, 0L, 0L);
        }
        ChemicalStack stack;
        try {
            stack = NativeAsyncResourceValues.chemical(resource.resource());
        } catch (IllegalArgumentException exception) {
            return failure(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH, resource.amount(), 0L);
        }
        return prepareOperation(new CapabilityRequests.ResourceRequest<>(TYPE,
                resource.insert() ? IOType.OUTPUT : IOType.INPUT, 1L,
                List.of(new CapabilityRequests.ResourceAction<>(resource.slot(), stack, resource.amount(), resource.insert())))).commit();
    }

    @Override public void encode(RegistryFriendlyByteBuf buffer) {
        int count = handler.getChemicalTanks();
        buffer.writeVarInt(count);
        for (int slot = 0; slot < count; slot++) {
            ChemicalStack stack = handler.getChemicalInTank(slot);
            buffer.writeBoolean(!stack.isEmpty());
            if (!stack.isEmpty()) Chemical.HOLDER_STREAM_CODEC.encode(buffer, stack.getChemicalHolder());
            buffer.writeLong(stack.getAmount());
            buffer.writeLong(handler.getChemicalTankCapacity(slot));
        }
    }

    @Override public void decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 1024 || count != handler.getChemicalTanks()) {
            throw new IllegalArgumentException("Invalid ME chemical sync slot count");
        }
        List<ChemicalStack> stacks = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            ChemicalStack identity = buffer.readBoolean()
                    ? new ChemicalStack(Chemical.HOLDER_STREAM_CODEC.decode(buffer), 1L) : ChemicalStack.EMPTY;
            long amount = buffer.readLong();
            long capacity = buffer.readLong();
            boolean validCapacity = handler instanceof InventoryChemicalHandler inventory
                    ? inventory.isSyncCapacityValid(identity, capacity)
                    : handler instanceof OutputChemicalHandler output ? output.isSyncCapacityValid(identity, capacity)
                    : capacity == handler.getChemicalTankCapacity(slot);
            if (amount < 0L || capacity < amount || !validCapacity
                    || amount > 0L && identity.isEmpty() || amount == 0L && !identity.isEmpty()
                    || !identity.isEmpty() && (!(handler instanceof NetworkChemicalHandler) && !handler.isValid(slot, identity)
                    || handler instanceof NetworkChemicalHandler network
                    && !network.storedKey(slot).equals(network.resourceKey(identity)))) {
                throw new IllegalArgumentException("Invalid ME chemical sync state");
            }
            stacks.add(identity.copyWithAmount(amount));
        }
        if (handler instanceof NetworkChemicalHandler network) network.prepareSync(count);
        for (int slot = 0; slot < count; slot++) handler.setChemicalInTank(slot, stacks.get(slot));
    }

    private long move(int slot, ChemicalStack identity, long amount, boolean insert, Action action) {
        ChemicalStack requested = identity.copyWithAmount(amount);
        if (insert) {
            ChemicalStack remainder = slot == -1 ? handler.insertChemical(requested, action)
                    : handler.insertChemical(slot, requested, action);
            return amount - remainder.getAmount();
        }
        ChemicalStack current = handler.getChemicalInTank(slot);
        return current.isEmpty() || !ChemicalStack.isSameChemical(current, requested) ? 0L
                : handler.extractChemical(slot, amount, action).getAmount();
    }

    private CapabilityResult unavailable(CapabilityRequests.ResourceAction<?> action, long moved) {
        return failure(action.insert() ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                : MekanismFailureReasons.CHEMICAL_INPUT_MISSING, action.amount(), moved);
    }

    private CapabilityResult failure(FailureReason reason, long required, long available) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT,
                null, null, Map.of("required", Long.toString(required), "available", Long.toString(available),
                        "shortfall", Long.toString(Math.max(0L, required - available))));
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(), occurrence));
    }
}
