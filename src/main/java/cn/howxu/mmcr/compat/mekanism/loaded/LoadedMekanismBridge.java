package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.transfer.TransferContext;
import cn.howxu.mmcr.api.capability.transfer.ContainerResourceTransfer;
import cn.howxu.mmcr.api.capability.transfer.TransferPolicy;
import cn.howxu.mmcr.api.capability.transfer.TransferResult;
import cn.howxu.mmcr.api.capability.transfer.TransferStrategyRegistry;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler.ResourceWakeup;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler.WakeupReason;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge.MenuRegistrar;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge.PortDeclaration;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge.PortType;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.network.PktPortContainerTransferPayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalResource;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.proxy.AutomatedResourceHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Mekanism-present bridge implementation and loaded recipe handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedMekanismBridge implements MekanismBridge {
    /** Machine capability seam supplied by a loaded Mekanism chemical port. */
    public interface ChemicalPort extends MachineCapability {
        IChemicalTank chemicalTank();

        default boolean radioactive() {
            return false;
        }

        @Override
        default CapabilityOperation prepare(CapabilityRequest request) {
            if (!(request instanceof CapabilityRequests.ResourceRequest<?> resourceRequest)) {
                return transaction -> capabilityFailure(this, BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            }
            if (!view().directions().supports(resourceRequest.ioType())) {
                FailureReason reason = resourceRequest.ioType() == IOType.OUTPUT
                        ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                        : MekanismFailureReasons.CHEMICAL_INPUT_MISSING;
                return transaction -> capabilityFailure(this, reason);
            }
            return transaction -> {
                for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                    if (!(action.resource() instanceof ChemicalResource resource)) {
                        return capabilityFailure(this, MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
                    }
                    long moved = transfer(chemicalTank(), resource, action.amount(), transaction, action.insert());
                    if (moved != action.amount()) {
                        return capabilityFailure(this, action.insert()
                                ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                                : MekanismFailureReasons.CHEMICAL_INPUT_MISSING);
                    }
                }
                return CapabilityResult.successful();
            };
        }
    }

    /** Machine capability seam supplied by a loaded Mekanism heat port. */
    public interface HeatPort extends MachineCapability {
        IHeatHandler heatHandler();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean isNonEmptyRadioactiveChemicalPort(BlockEntity blockEntity) {
        return blockEntity instanceof ChemicalPortBlockEntity port
                && port.isRadioactive() && !port.chemicalTank().isEmpty();
    }

    @Override
    public boolean supportsPortFamily(Identifier familyId) {
        return MekanismRecipeTypes.CHEMICAL.equals(familyId)
                || MekanismPortFamilies.RADIOACTIVE_CHEMICAL.equals(familyId)
                || MekanismRecipeTypes.HEAT_TEMPERATURE.equals(familyId)
                || MekanismRecipeTypes.HEAT.equals(familyId);
    }

    @Override
    public List<PortDeclaration> portDeclarations() {
        List<PortDeclaration> declarations = new ArrayList<>();
        for (MekanismPortSizes.ChemicalTier tier : MekanismPortSizes.ChemicalTier.values()) {
            declarations.add(new PortDeclaration("chemical_input_hatch_" + tier.id(), PortType.CHEMICAL,
                    IOType.INPUT, PortTiers.ItemTier.NORMAL.ordinal() + tier.ordinal(), tier.capacity(), false));
            declarations.add(new PortDeclaration("chemical_output_hatch_" + tier.id(), PortType.CHEMICAL,
                    IOType.OUTPUT, PortTiers.ItemTier.NORMAL.ordinal() + tier.ordinal(), tier.capacity(), false));
        }
        declarations.add(new PortDeclaration("radioactive_chemical_input_hatch", PortType.CHEMICAL,
                IOType.INPUT, PortTiers.ItemTier.HUGE.ordinal(),
                MekanismPortSizes.RADIOACTIVE_CHEMICAL_CAPACITY, true));
        declarations.add(new PortDeclaration("radioactive_chemical_output_hatch", PortType.CHEMICAL,
                IOType.OUTPUT, PortTiers.ItemTier.HUGE.ordinal(),
                MekanismPortSizes.RADIOACTIVE_CHEMICAL_CAPACITY, true));
        declarations.add(new PortDeclaration("heat_input_hatch", PortType.HEAT, IOType.INPUT,
                PortTiers.EnergyTier.ULTIMATE.ordinal(),
                (long) MekanismPortSizes.HEAT_CAPACITY, false));
        declarations.add(new PortDeclaration("heat_output_hatch", PortType.HEAT, IOType.OUTPUT,
                PortTiers.EnergyTier.ULTIMATE.ordinal(),
                (long) MekanismPortSizes.HEAT_CAPACITY, false));
        return List.copyOf(declarations);
    }

    @Override
    public MachineCapability createChemicalCapability(CapabilityCreationContext context) {
        if (context.host() instanceof ChemicalPortBlockEntity port) return new ChemicalPortCapability(port);
        throw new IllegalArgumentException("Mekanism chemical capability requires a chemical port");
    }

    @Override
    public MachineCapability createHeatCapability(CapabilityCreationContext context) {
        if (context.host() instanceof HeatPortBlockEntity port) return new HeatPortCapability(port);
        throw new IllegalArgumentException("Mekanism heat capability requires a heat port");
    }

    @Override
    public IOPortBlockEntity createChemicalPort(BlockPos pos, BlockState state, IOPortKind kind,
                                                 long capacity, boolean radioactive) {
        return new DeclaredChemicalPort(pos, state, kind, capacity, radioactive);
    }

    @Override
    public IOPortBlockEntity createHeatPort(BlockPos pos, BlockState state, IOPortKind kind) {
        return new DeclaredHeatPort(pos, state, kind);
    }

    @Override
    public void registerMenus(MenuRegistrar registrar) {
        registrar.register("chemical_port", () -> new MenuType<>((IContainerFactory<ChemicalPortMenu>) ChemicalPortMenu::clientOpen,
                FeatureFlags.VANILLA_SET));
        registrar.register("heat_port", () -> new MenuType<>((IContainerFactory<HeatPortMenu>) HeatPortMenu::clientOpen,
                FeatureFlags.VANILLA_SET));
    }

    @Override
    public Identifier capabilityIdForMenu(AbstractContainerMenu menu) {
        if (menu instanceof ChemicalPortMenu) return MekanismRecipeTypes.CHEMICAL;
        if (menu instanceof HeatPortMenu) return MekanismRecipeTypes.HEAT;
        return null;
    }

    @Override
    public boolean isPortMenuAt(AbstractContainerMenu menu, BlockPos pos, IOPortBlockEntity port) {
        if (menu instanceof ChemicalPortMenu chemical) {
            return matches(chemical.pos(), chemical.owner(), pos, port);
        }
        if (menu instanceof HeatPortMenu heat) {
            return matches(heat.pos(), heat.owner(), pos, port);
        }
        return false;
    }

    @Override
    public ResourceHandler<FluidResource> manualFluidContainerHandler(ResourceHandler<FluidResource> handler) {
        return AutomatedResourceHandler.manual(handler);
    }

    @Override
    public int transferChemicalContainer(ServerPlayer player, AbstractContainerMenu menu,
                                         int tankIndex, TransactionContext transaction) {
        if (!(menu instanceof ChemicalPortMenu chemical) || tankIndex != 0) return 0;
        ChemicalPortBlockEntity port = chemical.owner();
        if (!PktPortContainerTransferPayload.validTarget(player, menu, port)) return 0;
        ItemAccess access = ItemAccess.forPlayerCursor(player, menu);
        ResourceHandler<ChemicalResource> container = AutomatedResourceHandler.manual(
                access.getCapability(Capabilities.CHEMICAL.item()));
        return ContainerResourceTransfer.transfer(port.ioType(), container,
                port.chemicalHandler(null), transaction);
    }

    private static boolean matches(BlockPos menuPos, IOPortBlockEntity menuOwner,
                                   BlockPos pos, IOPortBlockEntity port) {
        if (pos == null) return false;
        return menuPos.equals(pos) && (port == null || menuOwner == port);
    }

    @Override
    public AbstractContainerMenu createMenu(String id, int containerId, Inventory playerInventory,
                                             Level level, BlockPos pos) {
        return switch (id) {
            case "chemical_input_hatch_basic", "chemical_output_hatch_basic",
                    "chemical_input_hatch_advanced", "chemical_output_hatch_advanced",
                    "chemical_input_hatch_elite", "chemical_output_hatch_elite",
                    "chemical_input_hatch_ultimate", "chemical_output_hatch_ultimate",
                    "radioactive_chemical_input_hatch", "radioactive_chemical_output_hatch" ->
                    new ChemicalPortMenu(containerId, playerInventory,
                            level.getBlockEntity(pos) instanceof ChemicalPortBlockEntity port ? port : null);
            case "heat_input_hatch", "heat_output_hatch" ->
                    new HeatPortMenu(containerId, playerInventory,
                            level.getBlockEntity(pos) instanceof HeatPortBlockEntity port ? port : null);
            default -> null;
        };
    }

    private static final class DeclaredChemicalPort extends ChemicalPortBlockEntity {
        private final IOPortKind kind;
        private final IOType ioType;

        private DeclaredChemicalPort(BlockPos pos, BlockState state, IOPortKind kind,
                                     long capacity, boolean radioactive) {
            super(typeForKind(kind), pos, state, kind, capacity, radioactive);
            this.kind = kind;
            this.ioType = kind.ioType();
        }

        @Override
        public IOType ioType() {
            return ioType;
        }

        @Override
        public IOPortKind kind() {
            return kind;
        }
    }

    private static final class DeclaredHeatPort extends HeatPortBlockEntity {
        private final IOPortKind kind;
        private final IOType ioType;

        private DeclaredHeatPort(BlockPos pos, BlockState state, IOPortKind kind) {
            super(typeForKind(kind), pos, state, kind);
            this.kind = kind;
            this.ioType = kind.ioType();
        }

        @Override
        public IOType ioType() {
            return ioType;
        }

        @Override
        public IOPortKind kind() {
            return kind;
        }
    }

    @Override
    public Identifier unavailableReason() {
        return null;
    }

    @Override
    public ChemicalRenderData chemicalRenderData(Identifier chemicalId) {
        if (chemicalId == null) return null;
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.get(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, chemicalId));
        if (holder == null || holder.isEmpty()) return null;
        Chemical chemical = holder.get().value();
        return new ChemicalRenderData(chemical.getIcon(), chemical.getTint(), chemical.getTextComponent());
    }

    @Override
    public HeatDisplayData heatDisplayData(double kelvin) {
        var unit = MekanismTemperatureDisplay.configuredUnit();
        return new HeatDisplayData(MekanismTemperatureDisplay.fromKelvin(kelvin, unit),
                MekanismTemperatureDisplay.symbol(unit));
    }

    @Override
    public void registerRecipeTypes(Identifier chemical, Identifier heatTemperature, Identifier heat) {
        registerRequirement(LoadedChemicalRequirement.TYPE);
        registerRequirement(LoadedHeatRequirement.TEMPERATURE_TYPE);
        registerRequirement(LoadedHeatRequirement.HEAT_TYPE);
        registerOutput(LoadedChemicalOutput.TYPE);
        registerOutput(LoadedHeatOutput.TYPE);
        LoadedChemicalRequirement.installHandler(chemicalHandler());
        LoadedHeatRequirement.installHandler(heatHandler());
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        ModBlockEntities.BES.values().forEach(holder -> {
            event.registerBlockEntity(Capabilities.CHEMICAL.block(), holder.get(), (be, side) ->
                    be instanceof ChemicalPortBlockEntity port
                            && exposes(port, MekanismRecipeTypes.CHEMICAL, side)
                            ? port.chemicalHandler(side) : null);
            event.registerBlockEntity(Capabilities.HEAT, holder.get(), (be, side) ->
                    be instanceof HeatPortBlockEntity port
                            && exposes(port, MekanismRecipeTypes.HEAT, side)
                            ? port.externalHeatHandler() : null);
        });
    }

    @Override
    public synchronized void registerTransferPolicies() {
        CapabilityType type = new CapabilityType(MekanismRecipeTypes.CHEMICAL);
        if (TransferStrategyRegistry.policyFor(type).isEmpty()) {
            TransferStrategyRegistry.register(type, new ChemicalTransferPolicy());
        }
    }

    private static boolean exposes(IOPortBlockEntity port, Identifier type, Direction side) {
        return port.kind().definition().bindings().stream()
                .filter(binding -> binding.type().id().equals(type))
                .anyMatch(binding -> port.isNativeSideExposed(binding, side));
    }

    private static void registerRequirement(RequirementType<?> type) {
        if (RequirementHandlerRegistry.typeFor(type.id()) == null) {
            registerRequirementUnchecked(type);
        }
    }

    private static void registerOutput(OutputType<?> type) {
        if (OutputRegistry.typeFor(type.id()) == null) {
            registerOutputUnchecked(type);
        }
    }

    private static <R extends MachineRequirement> void registerRequirementUnchecked(
            RequirementType<R> type) {
        RequirementHandlerRegistry.register(type);
    }

    private static <O extends MachineOutput> void registerOutputUnchecked(OutputType<O> type) {
        OutputRegistry.register(type);
    }

    private static final class ChemicalTransferPolicy implements TransferPolicy {
        @Override
        public boolean hasWork(MachineCapability capability) {
            ChemicalPort port = chemicalPort(capability);
            if (port == null) return false;
            IChemicalTank tank = port.chemicalTank();
            return capability.directions().supports(IOType.OUTPUT)
                    ? tank.amountAsLong() > 0L
                    : tank.amountAsLong() < tank.capacityAsLong(tank.resource());
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentChemical(capability, side) != null;
        }

        @Override
        public TransferResult transfer(TransferContext context) {
            ChemicalPort port = chemicalPort(context.capability());
            TransferFacet transfer = transferFacet(context.capability());
            if (port == null || transfer == null) {
                return transferBlocked(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            }
            if (context.eject() ? port.chemicalTank().amountAsLong() <= 0L : !hasWork(port)) {
                return transferBlocked(BuiltinFailureReasons.NO_WORK);
            }
            ResourceHandler<ChemicalResource> adjacent = adjacentChemical(context.capability(), context.side());
            if (adjacent == null) return transferBlocked(BuiltinFailureReasons.NO_TARGET);
            ResourceHandler<ChemicalResource> internal = ChemicalPortCapability.resourceHandler(port.chemicalTank());
            int limit = (int) Math.min(context.eject() ? context.ejectionLimit() : transfer.transferLimit(),
                    Integer.MAX_VALUE);
            ChemicalResource selected = context.ejectionResource() instanceof ChemicalResource chemical
                    ? chemical : null;
            Predicate<ChemicalResource> filter = selected == null ? resource -> true : selected::equals;
            long moved = context.eject()
                    ? moveResource(internal, adjacent, filter, limit, context)
                    : context.ioType() == IOType.INPUT
                    ? moveResource(adjacent, internal, resource -> true, limit, context)
                    : moveResource(internal, adjacent, resource -> true, limit, context);
            return TransferResult.moved(moved);
        }

        private static boolean hasWork(ChemicalPort port) {
            IChemicalTank tank = port.chemicalTank();
            return tank.amountAsLong() < tank.capacityAsLong(tank.resource());
        }

        private static ResourceHandler<ChemicalResource> adjacentChemical(MachineCapability capability,
                                                                            Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || transfer.level() == null || side == null) return null;
            return transfer.level().getCapability(Capabilities.CHEMICAL.block(),
                    transfer.position().relative(side), side.getOpposite());
        }

        private static long moveResource(ResourceHandler<ChemicalResource> from,
                                         ResourceHandler<ChemicalResource> to, Predicate<ChemicalResource> filter,
                                         int limit,
                                         TransferContext context) {
            if (limit <= 0) return 0L;
            if (!context.simulate()) {
                return ResourceHandlerUtil.move(from, to, filter, limit, context.transaction());
            }
            try (Transaction transaction = Transaction.open(context.transaction())) {
                return ResourceHandlerUtil.move(from, to, filter, limit, transaction);
            }
        }
    }

    private static TransferFacet transferFacet(MachineCapability capability) {
        return capability == null ? null : capability.facet(TransferFacet.class).orElse(null);
    }

    private static ChemicalPort chemicalPort(MachineCapability capability) {
        return capability instanceof ChemicalPort port ? port : null;
    }

    private static TransferResult transferBlocked(FailureReason reason) {
        Identifier source = MMCR.id("auto_io");
        FailureOccurrence occurrence = FailureOccurrence.at(reason, source, FailurePhase.CAPABILITY_COMMIT,
                null, null, Map.of());
        return TransferResult.blocked(ExecutionStatus.blocked(source, source, occurrence));
    }

    public static RequirementHandler<LoadedChemicalRequirement> chemicalHandler() {
        return new ChemicalHandler();
    }

    public static RequirementHandler<LoadedHeatRequirement> heatHandler() {
        return new HeatHandler();
    }

    private static final class ChemicalHandler implements RequirementHandler<LoadedChemicalRequirement> {
        private final Map<ChemicalPort, ResourceStorage<ChemicalResource>> storages = new IdentityHashMap<>();

        @Override
        public RequirementPlan plan(LoadedChemicalRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            long requestedParallelism = context.requestedParallelism();
            if (requirement.io() == RecipeModifier.IOType.OUTPUT
                    && !RequirementHandlerSupport.shouldProduce(requirement.chance())) {
                return new RequirementPlan(context.requirementIndex(), requestedParallelism, List.of(), null);
            }

            ChemicalMatcher matcher = ChemicalMatcher.resolve(requirement.ingredient());
            if (matcher == null) {
                return RequirementHandlerSupport.blockedPlan(requirement, context,
                        MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
            }

            boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
            IOType direction = IOType.valueOf(requirement.io().name());
            List<ChemicalPort> ports = chemicalPorts(capabilities, direction);
            List<ChemicalPort> plannedPorts = output
                    ? ports.stream().sorted(Comparator.comparingInt(ChemicalPort::outputPriority).reversed()).toList()
                    : ports;
            boolean resourceRadioactive = matcher.exactHolder() != null
                    && matcher.exactHolder().value().isRadioactive();
            List<ChemicalPort> matchingPorts = plannedPorts.stream()
                    .filter(p -> p.radioactive() == resourceRadioactive)
                    .toList();
            boolean allowPartialOutput = output && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
            long maximum;
            FailureReason failureReason = null;
            if (output) {
                if (matcher.exactHolder() == null) {
                    return RequirementHandlerSupport.blockedPlan(requirement, context,
                            MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
                }
                if (matchingPorts.isEmpty()) {
                    return RequirementHandlerSupport.blockedOutputPlan(requirement, context,
                            BuiltinFailureReasons.MISSING_OUTPUT,
                            RequirementHandlerSupport.scaled(requirement.ingredient().amount(), requestedParallelism));
                }
                OutputCapacity capacity = outputCapacity(matcher.exactHolder(), matchingPorts);
                maximum = allowPartialOutput
                        ? capacity.amount() > 0L ? requestedParallelism : 0L
                        : Math.min(requestedParallelism, capacity.amount() / requirement.ingredient().amount());
                if (maximum == 0L && !allowPartialOutput && capacity.amount() > 0L) maximum = 1L;
                if (maximum <= 0L) {
                    failureReason = capacity.radioactivityRejected()
                            ? MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED
                            : MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED;
                }
            } else {
                if (matchingPorts.isEmpty()) {
                    return RequirementHandlerSupport.blockedPlan(requirement, context,
                            BuiltinFailureReasons.MISSING_INPUT);
                }
                maximum = inputMaximum(matcher, matchingPorts, requirement.ingredient().amount(), requestedParallelism);
                if (maximum <= 0L) failureReason = MekanismFailureReasons.CHEMICAL_INPUT_MISSING;
            }

            if (failureReason != null) {
                return output
                        ? RequirementHandlerSupport.blockedOutputPlan(requirement, context,
                        failureReason,
                        RequirementHandlerSupport.scaled(requirement.ingredient().amount(), requestedParallelism))
                        : RequirementHandlerSupport.blockedPlan(requirement, context, failureReason);
            }
            if (!output && requirement.consumeChance() <= 0F) {
                return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
            }

            RequirementHandlerSupport.ConsumeProfile consumed = !output
                    ? RequirementHandlerSupport.consumeProfile(requirement.consumeChance(), requestedParallelism) : null;
            RequirementPlan.OperationFactory operationFactory = (parallelism, reservations) -> planOperations(
                    requirement, matcher, plannedPorts, parallelism, consumed, reservations, direction, allowPartialOutput, true);
            RequirementPlan.ReservationFactory reservationFactory = RequirementHandlerSupport.reservationFactory(
                    (parallelism, reservations) -> planOperations(requirement, matcher, plannedPorts, parallelism,
                            consumed, reservations, direction, allowPartialOutput, false));
            return RequirementHandlerSupport.deferredPlan(context, maximum, operationFactory, reservationFactory);
        }

        @Override
        public List<ResourceWakeup> resourceWakeups(LoadedChemicalRequirement requirement) {
            ChemicalMatcher matcher = ChemicalMatcher.resolve(requirement.ingredient());
            if (matcher == null) return List.of();
            Predicate<Object> resourceMatcher = chemicalMatcher(matcher);
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                return List.of(new ResourceWakeup(Set.of(
                        BuiltinFailureReasons.MISSING_INPUT.id(),
                        BuiltinFailureReasons.PER_TICK.id(),
                        MekanismFailureReasons.CHEMICAL_INPUT_MISSING.id()),
                        WakeupReason.INPUT_AVAILABLE, resourceMatcher));
            }
            return List.of(new ResourceWakeup(Set.of(
                    BuiltinFailureReasons.MISSING_OUTPUT.id(),
                    BuiltinFailureReasons.FINISH.id(),
                    MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED.id(),
                    MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED.id()),
                    WakeupReason.OUTPUT_CAPACITY, resourceMatcher));
        }

        @Override
        public LoadedChemicalRequirement applyModifiers(LoadedChemicalRequirement requirement,
                                                         List<RecipeModifier> modifiers) {
            return LoadedChemicalRequirement.applyModifiers(requirement, modifiers);
        }

        private RequirementPlan.OperationPlan planOperations(LoadedChemicalRequirement requirement,
                                                              ChemicalMatcher matcher, List<ChemicalPort> ports,
                                                              long rawParallelism,
                                                              RequirementHandlerSupport.ConsumeProfile consumed,
                                                              PlanningReservations reservations,
                                                              IOType direction,
                                                              boolean allowPartialOutput, boolean materialize) {
            boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
            long parallelism = input ? consumed.consumedBatches(rawParallelism) : rawParallelism;
            long amount = RequirementHandlerSupport.scaled(requirement.ingredient().amount(), parallelism);
            long requested = !input ? amount : 0L;
            ChemicalResource requestedResource = matcher.exactHolder() == null ? null
                    : ChemicalResource.of(matcher.exactHolder());
            Map<MachineCapability, List<CapabilityRequests.ResourceAction<ChemicalResource>>> actions =
                    new LinkedHashMap<>();
            long remaining = amount;
            for (ChemicalPort port : ports) {
                ResourceStorage<ChemicalResource> storage = storage(port);
                long currentAmount = reservations.amount(storage, 0);
                Object reservedResource = reservations.resource(storage, 0);
                if (requirement.io() == RecipeModifier.IOType.INPUT) {
                    if (!(reservedResource instanceof ChemicalResource resource) || resource.isEmpty()
                            || currentAmount <= 0L || !matcher.matches(resource.typeHolder())
                            || !port.chemicalTank().isCurrentValidForExtraction(AutomationType.INTERNAL)) continue;
                    long moved = Math.min(remaining, currentAmount);
                    if (reservations.reserveExtract(storage, 0, resource, moved)) {
                        actions.computeIfAbsent(port, ignored -> new ArrayList<>())
                                .add(new CapabilityRequests.ResourceAction<>(0, resource, moved, false));
                        remaining -= moved;
                    }
                } else {
                    if (requestedResource == null || reservedResource instanceof ChemicalResource resource
                            && !resource.isEmpty() && !resource.equals(requestedResource)) continue;
                    if (!port.chemicalTank().getAttributeValidator().process(requestedResource.typeHolder())
                            || !port.chemicalTank().isValidForInsertion(requestedResource, AutomationType.INTERNAL)) continue;
                    long capacity = Math.max(0L,
                            port.chemicalTank().capacityAsLong(requestedResource) - currentAmount);
                    long moved = Math.min(remaining, capacity);
                    if (moved > 0L && reservations.reserveInsert(storage, 0, requestedResource, moved)) {
                        actions.computeIfAbsent(port, ignored -> new ArrayList<>())
                                .add(new CapabilityRequests.ResourceAction<>(0, requestedResource, moved, true));
                        remaining -= moved;
                    }
                }
                if (remaining == 0L) break;
            }

            if (remaining > 0L && !(allowPartialOutput && requirement.io() == RecipeModifier.IOType.OUTPUT)) {
                return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement,
                        requirement.io() == RecipeModifier.IOType.OUTPUT
                                ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                                : MekanismFailureReasons.CHEMICAL_INPUT_MISSING),
                        RequirementHandlerSupport.outputSimulation(requested, amount - remaining));
            }
            if (actions.isEmpty()) {
                return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement,
                        requirement.io() == RecipeModifier.IOType.OUTPUT
                                ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                                : MekanismFailureReasons.CHEMICAL_INPUT_MISSING),
                        RequirementHandlerSupport.outputSimulation(requested, 0L));
            }
            return RequirementHandlerSupport.resourceOperations(actions, direction, parallelism, materialize,
                    RequirementHandlerSupport.outputSimulation(requested, amount - remaining));
        }

        private ResourceStorage<ChemicalResource> storage(ChemicalPort port) {
            return storages.computeIfAbsent(port, ChemicalPortStorage::new);
        }

        private long inputMaximum(ChemicalMatcher matcher, List<ChemicalPort> ports,
                                  long amount, long requested) {
            long available = 0L;
            for (ChemicalPort port : ports) {
                ResourceStorage<ChemicalResource> storage = storage(port);
                ChemicalResource resource = storage.resource(0);
                if (resource == null || resource.isEmpty() || !matcher.matches(resource.typeHolder())
                        || !port.chemicalTank().isCurrentValidForExtraction(AutomationType.INTERNAL)) continue;
                available = RequirementHandlerSupport.saturatingAdd(available, storage.amount(0));
            }
            return Math.min(requested, available / amount);
        }

        private OutputCapacity outputCapacity(Holder<Chemical> holder, List<ChemicalPort> ports) {
            ChemicalResource resource = ChemicalResource.of(holder);
            long capacity = 0L;
            boolean rejected = false;
            for (ChemicalPort port : ports) {
                IChemicalTank tank = port.chemicalTank();
                if (!tank.getAttributeValidator().process(holder)) {
                    rejected |= resource.isRadioactive();
                    continue;
                }
                if (!tank.isValidForInsertion(resource, AutomationType.INTERNAL)) continue;
                ResourceStorage<ChemicalResource> storage = storage(port);
                ChemicalResource current = storage.resource(0);
                if (current != null && !current.isEmpty() && !current.equals(resource)) continue;
                capacity = RequirementHandlerSupport.saturatingAdd(capacity,
                        Math.max(0L, tank.capacityAsLong(resource) - storage.amount(0)));
            }
            return new OutputCapacity(capacity, rejected);
        }

        private static List<ChemicalPort> chemicalPorts(List<MachineCapability> capabilities, IOType direction) {
            return capabilities.stream().filter(ChemicalPort.class::isInstance)
                    .map(ChemicalPort.class::cast)
                    .filter(port -> port.view().directions().supports(direction)).toList();
        }

        private static Predicate<Object> chemicalMatcher(ChemicalMatcher matcher) {
            return resource -> resource instanceof ChemicalResource chemical
                    && !chemical.isEmpty() && matcher.matches(chemical.typeHolder());
        }
    }

    private static final class HeatHandler implements RequirementHandler<LoadedHeatRequirement> {
        @Override
        public RequirementPlan plan(LoadedHeatRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            IOType direction = requirement.heat().kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE
                    ? IOType.INPUT : IOType.OUTPUT;
            List<HeatPort> ports = capabilities.stream().filter(HeatPort.class::isInstance)
                    .map(HeatPort.class::cast)
                    .filter(port -> port.view().directions().supports(direction)).toList();
            if (requirement.heat().kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE) {
                boolean sufficient = ports.stream().anyMatch(port ->
                        port.heatHandler().getTemperature() >= requirement.heat().value());
                double availableTemperature = ports.stream()
                        .mapToDouble(port -> port.heatHandler().getTemperature())
                        .max().orElse(0D);
                return sufficient
                        ? new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null)
                        : RequirementHandlerSupport.blockedPlan(requirement, context,
                        MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT,
                        Map.of("required_temperature", Double.toString(requirement.heat().value()),
                                "available_temperature", Double.toString(availableTemperature)));
            }
            if (ports.isEmpty()) {
                return RequirementHandlerSupport.blockedOutputPlan(requirement, context,
                        MekanismFailureReasons.HEAT_OUTPUT_BLOCKED,
                        requestedHeat(requirement.heat().value(), context.requestedParallelism()),
                        Map.of("requested_heat", Long.toString(
                                requestedHeat(requirement.heat().value(), context.requestedParallelism()))));
            }
            return RequirementHandlerSupport.deferredPlan(context, context.requestedParallelism(),
                    (parallelism, ignored) -> heatPlan(requirement, ports, parallelism));
        }

        @Override
        public List<ResourceWakeup> resourceWakeups(LoadedHeatRequirement requirement) {
            CapabilityType heatType = new CapabilityType(MekanismRecipeTypes.HEAT);
            if (requirement.heat().kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE) {
                return List.of(new ResourceWakeup(Set.of(
                        MekanismFailureReasons.HEAT_INPUT_MISSING.id(),
                        MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT.id()),
                        WakeupReason.INPUT_AVAILABLE, heatType::equals));
            }
            return List.of(new ResourceWakeup(Set.of(
                    BuiltinFailureReasons.MISSING_OUTPUT.id(),
                    BuiltinFailureReasons.FINISH.id(),
                    MekanismFailureReasons.HEAT_OUTPUT_BLOCKED.id()),
                    WakeupReason.OUTPUT_CAPACITY, heatType::equals));
        }

        @Override
        public LoadedHeatRequirement applyModifiers(LoadedHeatRequirement requirement,
                                                     List<RecipeModifier> modifiers) {
            return LoadedHeatRequirement.applyModifiers(requirement, modifiers);
        }

        private static RequirementPlan.OperationPlan heatPlan(LoadedHeatRequirement requirement,
                                                               List<HeatPort> ports, long parallelism) {
            double amount = saturatingHeatMultiply(requirement.heat().value(), parallelism);
            if (amount < 0D) {
                return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement,
                        MekanismFailureReasons.HEAT_OUTPUT_BLOCKED,
                        Map.of("requested_heat", Long.toString(
                                requestedHeat(requirement.heat().value(), parallelism)))));
            }
            if (amount == 0D) return new RequirementPlan.OperationPlan(List.of(), null);
            HeatPort port = ports.getFirst();
            CapabilityOperation operation = transaction -> {
                try {
                    port.heatHandler().handleHeat(amount, transaction);
                } catch (RuntimeException exception) {
                    return capabilityFailure(requirement.type().id(), MekanismFailureReasons.HEAT_OUTPUT_BLOCKED,
                            Map.of("requested_heat", Long.toString(requestedHeat(requirement.heat().value(), parallelism))));
                }
                return CapabilityResult.successful();
            };
            long requested = requestedHeat(requirement.heat().value(), parallelism);
            return new RequirementPlan.OperationPlan(List.of(operation), null,
                    RequirementHandlerSupport.outputSimulation(requested, requested));
        }
    }

    private record ChemicalMatcher(Holder<Chemical> exactHolder, TagKey<Chemical> tag) {
        static ChemicalMatcher resolve(ChemicalIngredient ingredient) {
            if (ingredient.kind() == ChemicalIngredient.Kind.CHEMICAL) {
                Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.get(
                        ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id()));
                return holder.map(value -> new ChemicalMatcher(value, null)).orElse(null);
            }
            TagKey<Chemical> tag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id());
            return MekanismAPI.CHEMICAL_REGISTRY.get(tag).isPresent()
                    ? new ChemicalMatcher(null, tag) : null;
        }

        boolean matches(Holder<Chemical> holder) {
            return exactHolder != null ? exactHolder.equals(holder) : holder.is(tag);
        }
    }

    private record OutputCapacity(long amount, boolean radioactivityRejected) {
    }

    private record ChemicalPortStorage(ChemicalPort port) implements ResourceStorage<ChemicalResource> {
        @Override
        public Class<ChemicalResource> resourceType() {
            return ChemicalResource.class;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ChemicalResource resource(int slot) {
            checkSlot(slot);
            return port.chemicalTank().resource();
        }

        @Override
        public long amount(int slot) {
            checkSlot(slot);
            return port.chemicalTank().amountAsLong();
        }

        @Override
        public long capacity(int slot, ChemicalResource resource) {
            checkSlot(slot);
            return port.chemicalTank().capacityAsLong(resource == null
                    ? port.chemicalTank().resource() : resource);
        }

        @Override
        public boolean isValid(int slot, ChemicalResource resource) {
            checkSlot(slot);
            return !resource.isEmpty() && port.chemicalTank().getAttributeValidator().process(resource.typeHolder())
                    && port.chemicalTank().isValid(resource);
        }

        @Override
        public long insert(int slot, ChemicalResource resource, long amount, TransactionContext transaction) {
            checkSlot(slot);
            return transfer(port.chemicalTank(), resource, amount, transaction, true);
        }

        @Override
        public long extract(int slot, ChemicalResource resource, long amount, TransactionContext transaction) {
            checkSlot(slot);
            return transfer(port.chemicalTank(), resource, amount, transaction, false);
        }

        private static void checkSlot(int slot) {
            if (slot != 0) throw new IndexOutOfBoundsException("chemical port slot must be zero");
        }
    }

    private static CapabilityResult capabilityFailure(MachineCapability capability, FailureReason reason) {
        return capabilityFailure(capability.type().id(), reason, Map.of());
    }

    private static CapabilityResult capabilityFailure(Identifier source, FailureReason reason,
                                                      Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, source, FailurePhase.CAPABILITY_COMMIT,
                null, null, details);
        return CapabilityResult.failure(ExecutionStatus.blocked(source, source, occurrence));
    }

    private static long transfer(IChemicalTank tank, ChemicalResource resource, long amount,
                                 TransactionContext transaction, boolean insert) {
        long remaining = amount;
        long movedTotal = 0L;
        while (remaining > 0L) {
            int request = (int) Math.min(remaining, Integer.MAX_VALUE);
            int moved = insert ? tank.insert(resource, request, transaction, AutomationType.INTERNAL)
                    : tank.extract(resource, request, transaction, AutomationType.INTERNAL);
            if (moved <= 0) break;
            movedTotal += moved;
            remaining -= moved;
        }
        return movedTotal;
    }

    private static long requestedHeat(double heat, long parallelism) {
        if (heat <= 0D || parallelism <= 0L) return 0L;
        double requested = heat * parallelism;
        return !Double.isFinite(requested) || requested >= Long.MAX_VALUE
                ? Long.MAX_VALUE : (long) Math.ceil(requested);
    }

    private static double saturatingHeatMultiply(double heat, long parallelism) {
        if (heat <= 0D || parallelism <= 0L) return 0D;
        return heat >= Double.MAX_VALUE / parallelism ? Double.MAX_VALUE : heat * parallelism;
    }
}
