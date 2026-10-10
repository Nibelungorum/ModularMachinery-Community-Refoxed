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
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.internal.autoio.AutoIoHandler;
import cn.howxu.mmcr.internal.autoio.AutoIoResult;
import cn.howxu.mmcr.internal.autoio.CapabilityTransferPolicies;
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
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.api.chemical.ChemicalUtils;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.IContainerFactory;

import java.util.ArrayList;
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
    public interface ChemicalPort extends ChemicalHandlerPort {
        IChemicalTank chemicalTank();

        @Override
        default Object planningIdentity() { return chemicalTank(); }

        @Override
        default IChemicalHandler chemicalHandler() {
            IChemicalTank tank = chemicalTank();
            return new IChemicalHandler() {
                @Override public int getChemicalTanks() { return 1; }
                @Override public ChemicalStack getChemicalInTank(int slot) { return tank.getStack().copy(); }
                @Override public void setChemicalInTank(int slot, ChemicalStack stack) { tank.setStack(stack); }
                @Override public long getChemicalTankCapacity(int slot) { return tank.getCapacity(); }
                @Override public boolean isValid(int slot, ChemicalStack stack) { return tank.isValid(stack); }
                @Override public ChemicalStack insertChemical(int slot, ChemicalStack stack, Action action) {
                    return tank.insert(stack, action, AutomationType.INTERNAL);
                }
                @Override public ChemicalStack extractChemical(int slot, long amount, Action action) {
                    return tank.extract(amount, action, AutomationType.INTERNAL);
                }
            };
        }

        default boolean radioactive() {
            return false;
        }

        @Override
        default CapabilityOperation prepare(CapabilityRequest request) {
            if (!(request instanceof CapabilityRequests.ResourceRequest<?> resourceRequest)) {
                return (NativeCapabilityOperation) () -> capabilityFailure(this, BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            }
            if (!view().directions().supports(resourceRequest.ioType())) {
                FailureReason reason = resourceRequest.ioType() == IOType.OUTPUT
                        ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                        : MekanismFailureReasons.CHEMICAL_INPUT_MISSING;
                return (NativeCapabilityOperation) () -> capabilityFailure(this, reason);
            }
            return (NativeCapabilityOperation) () -> {
                for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                    if (!(action.resource() instanceof ChemicalStack resource) || resource.isEmpty()) {
                        return capabilityFailure(this, MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
                    }
                    long moved = transfer(chemicalTank(), ChemicalPortCapability.identity(resource), action.amount(),
                            action.insert(), Action.SIMULATE);
                    if (moved != action.amount()) {
                        return capabilityFailure(this, action.insert()
                                ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                                : MekanismFailureReasons.CHEMICAL_INPUT_MISSING);
                    }
                }
                for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                    ChemicalStack resource = (ChemicalStack) action.resource();
                    long moved = transfer(chemicalTank(), ChemicalPortCapability.identity(resource), action.amount(),
                            action.insert(), Action.EXECUTE);
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
    public boolean supportsPortFamily(ResourceLocation familyId) {
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
    public ResourceLocation capabilityIdForMenu(AbstractContainerMenu menu) {
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
    public IFluidHandlerItem manualFluidContainerHandler(IFluidHandlerItem handler) {
        if (!(handler instanceof IMekanismFluidHandler mekanism)) return handler;
        return new IFluidHandlerItem() {
            public ItemStack getContainer() { return handler.getContainer(); }
            public int getTanks() { return handler.getTanks(); }
            public FluidStack getFluidInTank(int tank) { return handler.getFluidInTank(tank); }
            public int getTankCapacity(int tank) { return handler.getTankCapacity(tank); }
            public boolean isFluidValid(int tank, FluidStack stack) { return handler.isFluidValid(tank, stack); }
            public int fill(FluidStack stack, FluidAction action) {
                return stack.getAmount() - ExtendedFluidHandlerUtils.insert(stack, null, mekanism::getFluidTanks,
                        action.simulate() ? Action.SIMULATE : Action.EXECUTE, AutomationType.MANUAL).getAmount();
            }
            public FluidStack drain(FluidStack stack, FluidAction action) {
                return ExtendedFluidHandlerUtils.extract(stack, null, mekanism::getFluidTanks,
                        action.simulate() ? Action.SIMULATE : Action.EXECUTE, AutomationType.MANUAL);
            }
            public FluidStack drain(int amount, FluidAction action) {
                return ExtendedFluidHandlerUtils.extract(amount, null, mekanism::getFluidTanks,
                        action.simulate() ? Action.SIMULATE : Action.EXECUTE, AutomationType.MANUAL);
            }
        };
    }

    @Override
    public int transferChemicalContainer(ServerPlayer player, AbstractContainerMenu menu,
                                         int tankIndex) {
        if (!(menu instanceof ChemicalPortMenu chemical) || tankIndex != 0) return 0;
        ChemicalPortBlockEntity port = chemical.owner();
        if (!PktPortContainerTransferPayload.validTarget(player, menu, port)) return 0;
        ItemStack carried = menu.getCarried();
        ItemStack changed = carried.copyWithCount(1);
        IChemicalHandler container = changed.getCapability(Capabilities.CHEMICAL.item());
        if (container == null) return 0;
        IChemicalTank tank = port.chemicalTank();
        ChemicalStack resource;
        if (port.ioType() == IOType.INPUT) {
            resource = drainChemicalContainer(container, tank);
        } else {
            resource = tank.extract(Integer.MAX_VALUE, Action.SIMULATE, AutomationType.MANUAL);
            long accepted = resource.getAmount() - insertContainerChemical(container, resource, Action.EXECUTE).getAmount();
            if (accepted == 0) return 0;
            resource = resource.copyWithAmount(accepted);
        }
        if (resource.isEmpty()) return 0;
        long total = resource.getAmount() * carried.getCount();
        if (total > Integer.MAX_VALUE) return 0;
        resource = resource.copyWithAmount(total);
        long available = port.ioType() == IOType.INPUT
                ? total - tank.insert(resource, Action.SIMULATE, AutomationType.MANUAL).getAmount()
                : tank.extract(total, Action.SIMULATE, AutomationType.MANUAL).getAmount();
        if (available != total) return 0;
        long moved = port.ioType() == IOType.INPUT
                ? resource.getAmount() - tank.insert(resource, Action.EXECUTE, AutomationType.MANUAL).getAmount()
                : tank.extract(resource.getAmount(), Action.EXECUTE, AutomationType.MANUAL).getAmount();
        if (!changed.isEmpty()) changed.setCount(changed.getCount() * carried.getCount());
        int cursorCount = Math.min(changed.getCount(), changed.getMaxStackSize());
        menu.setCarried(changed.copyWithCount(cursorCount));
        if (changed.getCount() > cursorCount) {
            ItemHandlerHelper.giveItemToPlayer(player, changed.copyWithCount(changed.getCount() - cursorCount));
        }
        return (int) moved;
    }

    static ChemicalStack drainChemicalContainer(IChemicalHandler container, IChemicalTank tank) {
        ChemicalStack resource = ChemicalStack.EMPTY;
        for (int index = 0; index < container.getChemicalTanks(); index++) {
            ChemicalStack candidate = container.getChemicalInTank(index);
            if (candidate.isEmpty() || !resource.isEmpty() && !ChemicalStack.isSameChemical(resource, candidate)) continue;
            candidate = candidate.copyWithAmount(Integer.MAX_VALUE);
            long remaining = candidate.getAmount() - tank.insert(candidate, Action.SIMULATE, AutomationType.MANUAL).getAmount()
                    - resource.getAmount();
            if (remaining <= 0) continue;
            ChemicalStack extracted = extractContainerChemical(container, candidate.copyWithAmount(remaining), Action.EXECUTE);
            if (extracted.isEmpty()) continue;
            resource = resource.isEmpty() ? extracted : resource.copyWithAmount(resource.getAmount() + extracted.getAmount());
        }
        return resource;
    }

    private static ChemicalStack extractContainerChemical(IChemicalHandler handler, ChemicalStack stack, Action action) {
        return handler instanceof IMekanismChemicalHandler mekanism
                ? ChemicalUtils.extract(stack, null, mekanism::getChemicalTanks, action, AutomationType.MANUAL)
                : handler.extractChemical(stack, action);
    }

    private static ChemicalStack insertContainerChemical(IChemicalHandler handler, ChemicalStack stack, Action action) {
        return handler instanceof IMekanismChemicalHandler mekanism
                ? ChemicalUtils.insert(stack, null, mekanism::getChemicalTanks, action, AutomationType.MANUAL)
                : handler.insertChemical(stack, action);
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
    public ResourceLocation unavailableReason() {
        return null;
    }

    @Override
    public ChemicalRenderData chemicalRenderData(ResourceLocation chemicalId) {
        if (chemicalId == null) return null;
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
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
    public void registerRecipeTypes(ResourceLocation chemical, ResourceLocation heatTemperature, ResourceLocation heat) {
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
        if (!CapabilityTransferPolicies.isRegistered(type)) {
            CapabilityTransferPolicies.register(type, new ChemicalTransferPolicy());
        }
    }

    private static boolean exposes(IOPortBlockEntity port, ResourceLocation type, Direction side) {
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

    private static final class ChemicalTransferPolicy implements AutoIoHandler {
        @Override
        public boolean hasWork(MachineCapability capability) {
            ChemicalHandlerPort port = chemicalPort(capability);
            if (port == null) return false;
            IChemicalHandler handler = port.chemicalHandler();
            for (int slot = 0; slot < handler.getChemicalTanks(); slot++) {
                long amount = handler.getChemicalInTank(slot).getAmount();
                if (capability.directions().supports(IOType.OUTPUT)
                        ? amount > 0L : amount < handler.getChemicalTankCapacity(slot)) return true;
            }
            return false;
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentChemical(capability, side) != null;
        }

        @Override
        public AutoIoResult transfer(MachineCapability capability, Direction side, Object selectedResource, long ejectionLimit) {
            ChemicalHandlerPort port = chemicalPort(capability);
            TransferFacet transfer = transferFacet(capability);
            if (port == null || transfer == null) {
                return transferBlocked(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
            }
            boolean eject = ejectionLimit > 0L;
            boolean work = hasWork(port);
            if (eject) {
                work = false;
                IChemicalHandler handler = port.chemicalHandler();
                for (int slot = 0; slot < handler.getChemicalTanks(); slot++) {
                    if (!handler.getChemicalInTank(slot).isEmpty()) {
                        work = true;
                        break;
                    }
                }
            }
            if (!work) {
                return transferBlocked(BuiltinFailureReasons.NO_WORK);
            }
            IChemicalHandler adjacent = adjacentChemical(capability, side);
            if (adjacent == null) return transferBlocked(BuiltinFailureReasons.NO_TARGET);
            long limit = eject ? ejectionLimit : transfer.transferLimit();
            long moved = eject ? moveChemical(port.chemicalHandler(), adjacent, limit, false)
                    : capability.directions().supports(IOType.INPUT)
                    ? moveChemical(adjacent, port.chemicalHandler(), limit, false)
                    : moveChemical(port.chemicalHandler(), adjacent, limit, false);
            return AutoIoResult.moved(moved);
        }

        private static IChemicalHandler adjacentChemical(MachineCapability capability, Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || transfer.level() == null || side == null) return null;
            return transfer.level().getCapability(Capabilities.CHEMICAL.block(),
                    transfer.position().relative(side), side.getOpposite());
        }

    }

    private static TransferFacet transferFacet(MachineCapability capability) {
        return capability == null ? null : capability.facet(TransferFacet.class).orElse(null);
    }

    private static ChemicalHandlerPort chemicalPort(MachineCapability capability) {
        return capability instanceof ChemicalHandlerPort port ? port : null;
    }

    private static AutoIoResult transferBlocked(FailureReason reason) {
        ResourceLocation source = MMCR.id("auto_io");
        FailureOccurrence occurrence = FailureOccurrence.at(reason, source, FailurePhase.CAPABILITY_COMMIT,
                null, null, Map.of());
        return AutoIoResult.blocked(ExecutionStatus.blocked(source, source, occurrence));
    }

    public static RequirementHandler<LoadedChemicalRequirement> chemicalHandler() {
        return new ChemicalHandler();
    }

    public static RequirementHandler<LoadedHeatRequirement> heatHandler() {
        return new HeatHandler();
    }

    private static final class ChemicalHandler implements RequirementHandler<LoadedChemicalRequirement> {
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
            List<ChemicalHandlerPort> ports = chemicalPorts(capabilities, direction);
            List<ChemicalHandlerPort> plannedPorts = output
                    ? ports.stream().sorted(Comparator.comparingInt(ChemicalHandlerPort::outputPriority).reversed()).toList()
                    : ports;
            boolean resourceRadioactive = matcher.exactHolder() != null
                    && matcher.exactHolder().value().isRadioactive();
            List<ChemicalHandlerPort> matchingPorts = plannedPorts.stream()
                    .filter(p -> p.supportsRadioactivity(resourceRadioactive))
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
                    requirement, matcher, matchingPorts, parallelism, consumed, reservations, direction, allowPartialOutput, true);
            RequirementPlan.ReservationFactory reservationFactory = RequirementHandlerSupport.reservationFactory(
                    (parallelism, reservations) -> planOperations(requirement, matcher, matchingPorts, parallelism,
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
                                                              ChemicalMatcher matcher, List<ChemicalHandlerPort> ports,
                                                              long rawParallelism,
                                                              RequirementHandlerSupport.ConsumeProfile consumed,
                                                              PlanningReservations reservations,
                                                              IOType direction,
                                                              boolean allowPartialOutput, boolean materialize) {
            boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
            long parallelism = input ? consumed.consumedBatches(rawParallelism) : rawParallelism;
            long amount = RequirementHandlerSupport.scaled(requirement.ingredient().amount(), parallelism);
            long requested = !input ? amount : 0L;
            ChemicalStack requestedResource = matcher.exactHolder() == null ? null
                    : new ChemicalStack(matcher.exactHolder(), 1L);
            Map<MachineCapability, List<CapabilityRequests.ResourceAction<ChemicalStack>>> actions =
                    new LinkedHashMap<>();
            long remaining = amount;
            for (ChemicalHandlerPort port : ports) {
                IChemicalHandler handler = port.chemicalHandler();
                if (!input && requestedResource != null && handler instanceof ChemicalOutputAdmission admission) {
                    List<CapabilityRequests.ResourceAction<ChemicalStack>> admitted =
                            admission.reserveOutput(requestedResource, remaining, reservations);
                    for (CapabilityRequests.ResourceAction<ChemicalStack> action : admitted) remaining -= action.amount();
                    if (!admitted.isEmpty()) actions.computeIfAbsent(port, ignored -> new ArrayList<>()).addAll(admitted);
                    if (remaining == 0L) break;
                    continue;
                }
                for (int slot = 0; slot < handler.getChemicalTanks() && remaining > 0L; slot++) {
                    ChemicalStack current = ChemicalPortCapability.identity(handler.getChemicalInTank(slot));
                    Object identity = port.planningIdentity();
                    Object slotKey = port.planningSlot(slot);
                    Object storedKey = port.storedKey(slot);
                    long storedAmount = port.storedAmount(slot);
                    long currentAmount = reservations.nativeAmount(identity, slotKey, storedAmount);
                    Object virtualKey = reservations.nativeKey(identity, slotKey, storedKey);
                    if (input) {
                        if (current.isEmpty() || !matcher.matches(current.getChemicalHolder())) continue;
                        long available = Math.min(currentAmount,
                                handler.extractChemical(slot, Long.MAX_VALUE, Action.SIMULATE).getAmount());
                        long moved = Math.min(remaining, available);
                        if (reservations.reserveNativeExtract(identity, slotKey, port.planningKey(current),
                                storedKey, storedAmount, moved)) {
                            actions.computeIfAbsent(port, ignored -> new ArrayList<>())
                                    .add(new CapabilityRequests.ResourceAction<>(slot, current, moved, false));
                            remaining -= moved;
                        }
                    } else {
                        if (requestedResource == null || !handler.isValid(slot, requestedResource)
                                || currentAmount > 0L && !port.planningKey(requestedResource).equals(virtualKey)) continue;
                        long capacity = handler.getChemicalTankCapacity(slot);
                        long simulated = remaining - handler.insertChemical(slot,
                                requestedResource.copyWithAmount(remaining), Action.SIMULATE).getAmount();
                        long moved = Math.min(simulated, Math.max(0L, capacity - currentAmount));
                        if (reservations.reserveNativeInsert(identity, slotKey, port.planningKey(requestedResource),
                                storedKey, storedAmount, capacity, moved)) {
                            actions.computeIfAbsent(port, ignored -> new ArrayList<>())
                                    .add(new CapabilityRequests.ResourceAction<>(slot, requestedResource, moved, true));
                            remaining -= moved;
                        }
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
            List<CapabilityOperation> operations = new ArrayList<>();
            if (materialize) {
                for (Map.Entry<MachineCapability, List<CapabilityRequests.ResourceAction<ChemicalStack>>> entry
                        : actions.entrySet()) {
                    MachineCapability capability = entry.getKey();
                    operations.add(capability.prepare(new CapabilityRequests.ResourceRequest<>(
                            capability.view().type(), direction, parallelism, entry.getValue())));
                }
            }
            return new RequirementPlan.OperationPlan(operations, null,
                    RequirementHandlerSupport.outputSimulation(requested, amount - remaining));
        }

        private long inputMaximum(ChemicalMatcher matcher, List<ChemicalHandlerPort> ports,
                                   long amount, long requested) {
            long available = 0L;
            PlanningReservations reservations = new PlanningReservations();
            for (ChemicalHandlerPort port : ports) {
                IChemicalHandler handler = port.chemicalHandler();
                for (int slot = 0; slot < handler.getChemicalTanks(); slot++) {
                    ChemicalStack resource = handler.getChemicalInTank(slot);
                    if (resource.isEmpty() || !matcher.matches(resource.getChemicalHolder())) continue;
                    long simulated = handler.extractChemical(slot, Long.MAX_VALUE, Action.SIMULATE).getAmount();
                    long remaining = reservations.nativeAmount(port.planningIdentity(), port.planningSlot(slot), port.storedAmount(slot));
                    long moved = Math.min(simulated, remaining);
                    if (reservations.reserveNativeExtract(port.planningIdentity(), port.planningSlot(slot), port.planningKey(resource),
                            port.storedKey(slot), port.storedAmount(slot), moved)) {
                        available = RequirementHandlerSupport.saturatingAdd(available, moved);
                    }
                }
            }
            return Math.min(requested, available / amount);
        }

        private OutputCapacity outputCapacity(Holder<Chemical> holder, List<ChemicalHandlerPort> ports) {
            ChemicalStack resource = new ChemicalStack(holder, 1L);
            long capacity = 0L;
            boolean rejected = false;
            PlanningReservations reservations = new PlanningReservations();
            for (ChemicalHandlerPort port : ports) {
                if (port instanceof ChemicalPort single && !single.chemicalTank().getAttributeValidator().process(resource)) {
                    rejected |= resource.isRadioactive();
                    continue;
                }
                IChemicalHandler handler = port.chemicalHandler();
                if (handler instanceof ChemicalOutputAdmission admission) {
                    for (CapabilityRequests.ResourceAction<ChemicalStack> action
                            : admission.reserveOutput(resource, Long.MAX_VALUE, reservations)) {
                        capacity = RequirementHandlerSupport.saturatingAdd(capacity, action.amount());
                    }
                    continue;
                }
                for (int slot = 0; slot < handler.getChemicalTanks(); slot++) {
                    if (!handler.isValid(slot, resource)) continue;
                    long simulated = Long.MAX_VALUE - handler.insertChemical(slot, resource.copyWithAmount(Long.MAX_VALUE),
                            Action.SIMULATE).getAmount();
                    long stored = port.storedAmount(slot);
                    long available = Math.min(simulated, Math.max(0L, handler.getChemicalTankCapacity(slot)
                            - reservations.nativeAmount(port.planningIdentity(), port.planningSlot(slot), stored)));
                    if (reservations.reserveNativeInsert(port.planningIdentity(), port.planningSlot(slot), port.planningKey(resource),
                            port.storedKey(slot), stored, handler.getChemicalTankCapacity(slot), available)) {
                        capacity = RequirementHandlerSupport.saturatingAdd(capacity, available);
                    }
                }
            }
            return new OutputCapacity(capacity, rejected);
        }

        private static List<ChemicalHandlerPort> chemicalPorts(List<MachineCapability> capabilities, IOType direction) {
            return capabilities.stream().filter(ChemicalHandlerPort.class::isInstance)
                    .map(ChemicalHandlerPort.class::cast)
                    .filter(port -> port.view().directions().supports(direction)).toList();
        }

        private static java.util.function.Predicate<Object> chemicalMatcher(ChemicalMatcher matcher) {
            return resource -> {
                if (resource instanceof ChemicalStack chemical) {
                    return !chemical.isEmpty() && matcher.matches(chemical.getChemicalHolder());
                }
                if (!(resource instanceof ResourceLocation chemicalId)) return false;
                return MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, chemicalId))
                        .map(matcher::matches).orElse(false);
            };
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
                    port.heatHandler().getTotalTemperature() >= requirement.heat().value());
                double availableTemperature = ports.stream()
                        .mapToDouble(port -> port.heatHandler().getTotalTemperature())
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
            CapabilityOperation operation = (NativeCapabilityOperation) () -> {
                try {
                    port.heatHandler().handleHeat(amount);
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
                Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                        ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id()));
                return holder.map(value -> new ChemicalMatcher(value, null)).orElse(null);
            }
            TagKey<Chemical> tag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id());
            return MekanismAPI.CHEMICAL_REGISTRY.getTag(tag).isPresent()
                    ? new ChemicalMatcher(null, tag) : null;
        }

        boolean matches(Holder<Chemical> holder) {
            return exactHolder != null ? exactHolder.equals(holder) : holder.is(tag);
        }
    }

    private record OutputCapacity(long amount, boolean radioactivityRejected) {
    }

    private static CapabilityResult capabilityFailure(MachineCapability capability, FailureReason reason) {
        return capabilityFailure(capability.type().id(), reason, Map.of());
    }

    private static CapabilityResult capabilityFailure(ResourceLocation source, FailureReason reason,
                                                      Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, source, FailurePhase.CAPABILITY_COMMIT,
                null, null, details);
        return CapabilityResult.failure(ExecutionStatus.blocked(source, source, occurrence));
    }

    static long transfer(IChemicalTank tank, ChemicalStack identity, long amount, boolean insert, Action action) {
        if (amount <= 0L || identity.isEmpty()) return 0L;
        if (insert) {
            ChemicalStack remainder = tank.insert(identity.copyWithAmount(amount), action, AutomationType.INTERNAL);
            return amount - remainder.getAmount();
        }
        ChemicalStack current = tank.getStack();
        if (current.isEmpty() || !ChemicalStack.isSameChemical(current, identity)) return 0L;
        return tank.extract(amount, action, AutomationType.INTERNAL).getAmount();
    }

    private static long moveChemical(IChemicalHandler source, IChemicalHandler destination, long limit,
                                      boolean simulate) {
        long total = 0L;
        for (int slot = 0; slot < source.getChemicalTanks() && total < limit; slot++) {
            ChemicalStack extracted = source.extractChemical(slot, limit - total, Action.SIMULATE);
            if (extracted.isEmpty()) continue;
            long moved = extracted.getAmount() - destination.insertChemical(extracted, Action.SIMULATE).getAmount();
            if (moved <= 0L) continue;
            if (simulate) {
                total += moved;
                continue;
            }
            ChemicalStack committed = source.extractChemical(slot, moved, Action.EXECUTE);
            if (committed.getAmount() != moved) return total;
            total += moved - destination.insertChemical(committed, Action.EXECUTE).getAmount();
        }
        return total;
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
