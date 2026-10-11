package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedEnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capability identity and automatic transfer policy tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityTransferPolicyTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void builtInPoliciesAreSelectedByItemFluidAndEnergyCapabilityIdentity() {
        ItemInputBusBlockEntity item = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        FluidInputHatchBlockEntity fluid = RuntimeTestFixtures.fluidInput(new BlockPos(1, 0, 0));
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(2, 0, 0));

        assertThat(CapabilityTransferPolicies.handlerFor(item.capabilitySnapshot().capabilities().getFirst())).isPresent();
        assertThat(CapabilityTransferPolicies.handlerFor(fluid.capabilitySnapshot().capabilities().getFirst())).isPresent();
        assertThat(CapabilityTransferPolicies.handlerFor(energy.capabilitySnapshot().capabilities().getFirst())).isPresent();
    }

    @Test
    void inputAndOutputWorkPoliciesReflectStoredContents() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        var inputCapability = input.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = output.capabilitySnapshot().capabilities().getFirst();
        var inputHandler = CapabilityTransferPolicies.handlerFor(inputCapability).orElseThrow();
        var outputHandler = CapabilityTransferPolicies.handlerFor(outputCapability).orElseThrow();

        assertThat(inputHandler.hasWork(inputCapability)).isTrue();
        assertThat(outputHandler.hasWork(outputCapability)).isFalse();
    }

    @Test
    void noTargetAndNoWorkReturnStructuredBlockedResults() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        var inputCapability = input.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = output.capabilitySnapshot().capabilities().getFirst();
        var inputHandler = CapabilityTransferPolicies.handlerFor(inputCapability).orElseThrow();
        var outputHandler = CapabilityTransferPolicies.handlerFor(outputCapability).orElseThrow();

        AutoIoResult noTarget = transfer(inputHandler, inputCapability, Direction.NORTH);
        AutoIoResult noWork = transfer(outputHandler, outputCapability, Direction.NORTH);

        assertThat(noTarget.successful()).isFalse();
        assertThat(noTarget.amount()).isZero();
        assertThat(noTarget.failure().reason()).isEqualTo(BuiltinFailureReasons.NO_TARGET);
        assertThat(noWork.failure().reason()).isEqualTo(BuiltinFailureReasons.NO_WORK);
    }

    @Test
    void real_item_fluid_and_energy_handlers_transfer_and_eject_contents() {
        Ports ports = connectedPorts();
        var itemInput = ports.itemInput.capabilitySnapshot().capabilities().getFirst();
        var itemOutput = ports.itemOutput.capabilitySnapshot().capabilities().getFirst();
        var fluidInput = ports.fluidInput.capabilitySnapshot().capabilities().getFirst();
        var fluidOutput = ports.fluidOutput.capabilitySnapshot().capabilities().getFirst();
        var energyInput = ports.energyInput.capabilitySnapshot().capabilities().getFirst();
        var energyOutput = ports.energyOutput.capabilitySnapshot().capabilities().getFirst();

        setItem(ports.itemOutput.itemHandler(), 0, stack(2));
        LevelStub.setCapability(ports.level, Capabilities.ItemHandler.BLOCK, ports.itemOutput.getBlockPos(),
                ports.itemOutput.itemHandler());
        assertThat(transfer(CapabilityTransferPolicies.handlerFor(itemInput).orElseThrow(), itemInput,
                Direction.EAST).amount()).isEqualTo(2);
        assertThat(itemAmount(ports.itemInput.itemHandler(), 0)).isEqualTo(2L);

        setItem(ports.itemOutput.itemHandler(), 0, stack(3));
        LevelStub.setCapability(ports.level, Capabilities.ItemHandler.BLOCK, ports.itemInput.getBlockPos(),
                ports.itemInput.itemHandler());
        assertThat(eject(CapabilityTransferPolicies.handlerFor(itemOutput).orElseThrow(), itemOutput,
                Direction.WEST).amount()).isEqualTo(3);
        assertThat(itemAmount(ports.itemInput.itemHandler(), 0)).isEqualTo(5L);

        ports.fluidOutput.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 400));
        LevelStub.setCapability(ports.level, Capabilities.FluidHandler.BLOCK, ports.fluidOutput.getBlockPos(),
                ports.fluidOutput.fluidHandler(null));
        assertThat(transfer(CapabilityTransferPolicies.handlerFor(fluidInput).orElseThrow(), fluidInput,
                Direction.EAST).amount()).isEqualTo(400);
        assertThat(ports.fluidInput.fluidHandler(null).getAmountAsLong()).isEqualTo(400);

        ports.fluidOutput.fluidHandler(null).setFluid(new FluidStack(Fluids.WATER, 500));
        LevelStub.setCapability(ports.level, Capabilities.FluidHandler.BLOCK, ports.fluidInput.getBlockPos(),
                ports.fluidInput.fluidHandler(null));
        assertThat(eject(CapabilityTransferPolicies.handlerFor(fluidOutput).orElseThrow(), fluidOutput,
                Direction.WEST).amount()).isEqualTo(500);
        assertThat(ports.fluidInput.fluidHandler(null).getAmountAsLong()).isEqualTo(900);

        ports.energyOutput.energyStorage().setAmount(600);
        LevelStub.setCapability(ports.level, Capabilities.EnergyStorage.BLOCK, ports.energyOutput.getBlockPos(),
                ports.energyOutput.getEnergyHandler(null));
        assertThat(transfer(CapabilityTransferPolicies.handlerFor(energyInput).orElseThrow(), energyInput,
                Direction.EAST).amount()).isEqualTo(600);
        assertThat(ports.energyInput.energyStorage().getAmountAsLong()).isEqualTo(600);

        ports.energyOutput.energyStorage().setAmount(700);
        LevelStub.setCapability(ports.level, Capabilities.EnergyStorage.BLOCK, ports.energyInput.getBlockPos(),
                ports.energyInput.getEnergyHandler(null));
        assertThat(eject(CapabilityTransferPolicies.handlerFor(energyOutput).orElseThrow(), energyOutput,
                Direction.WEST).amount()).isEqualTo(700);
        assertThat(ports.energyInput.energyStorage().getAmountAsLong()).isEqualTo(1_300);
    }

    @Test
    void extended_energy_auto_io_transfers_amounts_above_integer_range() {
        ExtendedEnergyHatchBlockEntity input = extendedEnergy(
                "extended_energy_input_hatch_ultimate", BlockPos.ZERO);
        ExtendedEnergyHatchBlockEntity output = extendedEnergy(
                "extended_energy_output_hatch_ultimate", new BlockPos(1, 0, 0));
        Level level = LevelStub.createWithBlockEntities(List.of(input, output));
        input.setLevel(level);
        output.setLevel(level);
        long amount = Long.MAX_VALUE;
        output.energyStorage().setAmount(amount);
        LevelStub.setCapability(level, Capabilities.EnergyStorage.BLOCK, output.getBlockPos(), output.getEnergyHandler(null));

        var capability = input.capabilitySnapshot().capabilities().getFirst();
        AutoIoResult result = transfer(CapabilityTransferPolicies.handlerFor(capability).orElseThrow(), capability,
                Direction.EAST);

        assertThat(result.amount()).isEqualTo(amount);
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(amount);
        assertThat(output.energyStorage().getAmountAsLong()).isZero();
    }

    @Test
    void disabled_or_unavailable_side_does_not_mutate_real_storage() {
        Ports ports = connectedPorts();
        var input = ports.itemInput.capabilitySnapshot().capabilities().getFirst();
        setItem(ports.itemOutput.itemHandler(), 0, stack(2));
        LevelStub.setCapability(ports.level, Capabilities.ItemHandler.BLOCK, ports.itemOutput.getBlockPos(),
                ports.itemOutput.itemHandler());

        AutoIoResult blocked = transfer(CapabilityTransferPolicies.handlerFor(input).orElseThrow(), input,
                Direction.WEST);

        assertThat(blocked.successful()).isFalse();
        assertThat(blocked.failure().reason()).isEqualTo(BuiltinFailureReasons.NO_TARGET);
        assertThat(itemAmount(ports.itemInput.itemHandler(), 0)).isZero();
        assertThat(itemAmount(ports.itemOutput.itemHandler(), 0)).isEqualTo(2L);
    }

    @Test
    void invalidCapabilityAndSideAreBlockedWithoutMutatingStorage() {
        MachineCapability unknown = new MachineCapability() {
            private final CapabilityType capabilityType = new CapabilityType(MMCR.id("unknown"));

            @Override public CapabilityType type() { return capabilityType; }
            @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
            @Override public CapabilityView view() {
                return new CapabilityView() {
                    @Override public CapabilityType type() { return capabilityType; }
                    @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
                };
            }
            @Override public CapabilityOperation prepare(CapabilityRequest request) { return null; }
        };

        assertThat(CapabilityTransferPolicies.handlerFor(unknown)).isEmpty();
        assertThat(CapabilityTransferPolicies.handlerFor(null)).isEmpty();
    }

    @Test
    void operation_only_bidirectional_item_capability_is_excluded_from_auto_io() {
        OperationOnlyItemCapability capability = new OperationOnlyItemCapability();

        assertThat(CapabilityTransferPolicies.handlerFor(capability)).isEmpty();
        assertThat(capability.prepareCalls()).isZero();
    }

    @Test
    void operation_only_capability_is_ignored_by_real_auto_io_port_entries() {
        OperationOnlyItemCapability capability = new OperationOnlyItemCapability();
        OperationOnlyPort port = new OperationOnlyPort(capability);
        Level level = LevelStub.createWithBlockEntities(List.of(port));
        port.setLevel(level);
        int lookupsBefore = LevelStub.capabilityLookups(level);

        assertThat(port.isAutoIOSideExposed(capability.type(), null)).isFalse();
        port.setAutoIOEnabled(true);
        port.setAutoIOSide(Direction.NORTH, false);
        port.runAutoIOCycleForTesting();

        assertThat(capability.prepareCalls()).isZero();
        assertThat(port.autoIOCandidateCount()).isZero();
        assertThat(port.autoIODelay()).isEqualTo(60);
        assertThat(LevelStub.capabilityLookups(level)).isEqualTo(lookupsBefore);

        CompoundTag saved = new CompoundTag();
        port.saveTo(saved);
        assertThat(saved.getCompound("auto_io_capabilities").isEmpty()).isTrue();

        CompoundTag injected = new CompoundTag();
        CompoundTag profiles = new CompoundTag();
        CompoundTag profile = new CompoundTag();
        profile.putBoolean("enabled", true);
        profiles.put(capability.type().id().toString(), profile);
        injected.put("auto_io_capabilities", profiles);
        port.loadFrom(injected);
        CompoundTag restored = new CompoundTag();
        port.saveTo(restored);

        assertThat(restored.getCompound("auto_io_capabilities").isEmpty()).isTrue();
        assertThat(port.ejectContents(capability.type())).isFalse();
        assertThat(port.ejectContents()).isFalse();
        assertThat(port.activeRecipeChecks()).isZero();
        assertThat(capability.prepareCalls()).isZero();
        assertThat(LevelStub.capabilityLookups(level)).isEqualTo(lookupsBefore);
    }

    @Test
    void output_port_ejection_is_rejected_before_transfer_policy_runs() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(BlockPos.ZERO);
        setItem(output.itemHandler(), 0, stack(2));

        assertThat(output.ejectContents()).isFalse();
        assertThat(itemAmount(output.itemHandler(), 0)).isEqualTo(2L);
    }

    @Test
    void dynamic_transfer_declaration_is_rechecked_without_replacing_the_port_snapshot() {
        OperationOnlyItemCapability capability = new OperationOnlyItemCapability();
        OperationOnlyPort port = new OperationOnlyPort(capability);
        CapabilitySnapshot snapshot = port.capabilitySnapshot();

        assertThat(port.isAutoIOSideExposed(capability.type(), null)).isFalse();
        capability.transferEnabled = true;
        assertThat(CapabilityTransferPolicies.handlerFor(capability)).isPresent();
        assertThat(port.isAutoIOSideExposed(capability.type(), null)).isTrue();
        capability.transferEnabled = false;
        assertThat(CapabilityTransferPolicies.handlerFor(capability)).isEmpty();
        assertThat(port.isAutoIOSideExposed(capability.type(), null)).isFalse();
        assertThat(port.capabilitySnapshot()).isSameAs(snapshot);
    }

    @Test
    void dynamic_transfer_disable_clears_configuration_and_candidates_before_reenabling() throws Exception {
        OperationOnlyItemCapability capability = new OperationOnlyItemCapability();
        capability.transferEnabled = true;
        OperationOnlyPort port = new OperationOnlyPort(capability);
        port.setLevel(LevelStub.createWithBlockEntities(List.of(port)));
        AutoIOConfig original = port.autoIOConfig(capability.type());
        original.setEnabled(true);
        original.setSide(Direction.NORTH, false);
        port.runAutoIOCycleForTesting();
        Map<?, ?> states = autoIOStates(port);
        Object originalState = states.get(capability.type());
        assertThat(originalState).isNotNull();

        capability.transferEnabled = false;
        port.runAutoIOCycleForTesting();
        port.runAutoIOCycleForTesting();
        assertThat(states).isEmpty();
        capability.transferEnabled = true;
        AutoIOConfig restored = port.autoIOConfig(capability.type());

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.enabled()).isFalse();
        assertThat(restored.isSideEnabled(Direction.NORTH)).isTrue();
        restored.setEnabled(true);
        port.runAutoIOCycleForTesting();
        assertThat(states.get(capability.type())).isNotNull().isNotSameAs(originalState);
    }

    @Test
    void no_policy_cleanup_preserves_another_capability_configuration_and_state() throws Exception {
        OperationOnlyItemCapability item = new OperationOnlyItemCapability();
        OperationOnlyItemCapability fluid = new OperationOnlyItemCapability(BuiltinCapabilityDefinitions.FLUID_TYPE);
        item.transferEnabled = true;
        fluid.transferEnabled = true;
        OperationOnlyPort port = new OperationOnlyPort(item, fluid);
        port.setLevel(LevelStub.createWithBlockEntities(List.of(port)));
        port.autoIOConfig(item.type()).setEnabled(true);
        AutoIOConfig fluidConfig = port.autoIOConfig(fluid.type());
        fluidConfig.setEnabled(true);
        fluidConfig.setSide(Direction.WEST, false);
        port.runAutoIOCycleForTesting();
        Map<?, ?> states = autoIOStates(port);
        Object fluidState = states.get(fluid.type());
        assertThat(fluidState).isNotNull();

        item.transferEnabled = false;
        port.runAutoIOCycleForTesting();
        port.runAutoIOCycleForTesting();

        assertThat(states.get(item.type())).isNull();
        assertThat(states.get(fluid.type())).isSameAs(fluidState);
        assertThat(port.autoIOConfig(fluid.type())).isSameAs(fluidConfig);
        assertThat(fluidConfig.enabled()).isTrue();
        assertThat(fluidConfig.isSideEnabled(Direction.WEST)).isFalse();
    }

    @Test
    void empty_long_resource_storage_is_safe_for_auto_io_and_handler_projection() {
        Ports ports = connectedPorts();
        setItem(ports.itemOutput.itemHandler(), 0, stack(2));
        LevelStub.setCapability(ports.level, Capabilities.ItemHandler.BLOCK, ports.itemOutput.getBlockPos(),
                ports.itemOutput.itemHandler());

        LongItemStorage storage = new LongItemStorage(2, 100L, () -> {});
        ItemBusCapability capability = new ItemBusCapability(ports.itemInput, storage, IOType.INPUT);
        var handler = CapabilityTransferPolicies.handlerFor(capability).orElseThrow();

        assertThat(handler.hasWork(capability)).isTrue();
        AutoIoResult result = transfer(handler, capability, Direction.EAST);

        assertThat(result.successful()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(2L);
        assertThat(ItemStack.isSameItemSameComponents(storage.resource(0), stack(1))).isTrue();
        assertThat(storage.resource(0).getCount()).isEqualTo(1);
    }

    @Test
    void fluid_policy_scans_all_slots_for_work_and_ejects_slot_one() {
        LongFluidStorage inputStorage = new LongFluidStorage(2, 100L, () -> {});
        inputStorage.setContents(0, new FluidStack(Fluids.WATER, 1), 100L);
        inputStorage.setContents(1, new FluidStack(Fluids.LAVA, 1), 20L);
        FluidHatchCapability input = new FluidHatchCapability(inputStorage, IOType.INPUT);
        var inputHandler = CapabilityTransferPolicies.handlerFor(input).orElseThrow();

        assertThat(inputHandler.hasWork(input)).isTrue();

        Ports ports = connectedPorts();
        LongFluidStorage outputStorage = new LongFluidStorage(2, 100L, () -> {});
        outputStorage.setContents(1, new FluidStack(Fluids.WATER, 1), 40L);
        FluidHatchCapability output = new FluidHatchCapability(ports.fluidOutput, outputStorage, IOType.OUTPUT);
        LevelStub.setCapability(ports.level, Capabilities.FluidHandler.BLOCK, ports.fluidInput.getBlockPos(),
                ports.fluidInput.fluidHandler(null));

        AutoIoResult result = eject(CapabilityTransferPolicies.handlerFor(output).orElseThrow(), output,
                Direction.WEST);

        assertThat(result.successful()).isTrue();
        assertThat(result.amount()).isEqualTo(40L);
        assertThat(outputStorage.amount(1)).isZero();
    }

    private static Ports connectedPorts() {
        ItemInputBusBlockEntity itemInput = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity itemOutput = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        FluidInputHatchBlockEntity fluidInput = RuntimeTestFixtures.fluidInput(new BlockPos(0, 0, 1));
        FluidOutputHatchBlockEntity fluidOutput = RuntimeTestFixtures.fluidOutput(new BlockPos(1, 0, 1));
        EnergyInputHatchBlockEntity energyInput = RuntimeTestFixtures.energyInput(new BlockPos(0, 0, 2));
        EnergyOutputHatchBlockEntity energyOutput = RuntimeTestFixtures.energyOutput(new BlockPos(1, 0, 2));
        List<IOPortBlockEntity> ports = List.of(itemInput, itemOutput, fluidInput, fluidOutput, energyInput, energyOutput);
        Level level = LevelStub.createWithBlockEntities(List.of(itemInput, itemOutput, fluidInput, fluidOutput,
                energyInput, energyOutput));
        ports.forEach(port -> port.setLevel(level));
        return new Ports(level, itemInput, itemOutput, fluidInput, fluidOutput, energyInput, energyOutput);
    }

    private static AutoIoResult transfer(AutoIoHandler handler, MachineCapability capability, Direction side) {
        return handler.transfer(capability, side, null, 0L);
    }

    private static AutoIoResult eject(AutoIoHandler handler, MachineCapability capability, Direction side) {
        return handler.transfer(capability, side, null, Integer.MAX_VALUE);
    }

    private static ExtendedEnergyHatchBlockEntity extendedEnergy(String id, BlockPos position) {
        return (ExtendedEnergyHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(
                position, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static ItemStack stack(int count) {
        return new ItemStack(Items.IRON_INGOT, count);
    }

    private static void setItem(IItemHandler storage, int slot, ItemStack stack) {
        if (storage instanceof LongItemStorage longStorage) longStorage.setContents(slot, stack, stack.getCount());
        else if (storage instanceof net.neoforged.neoforge.items.IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        } else {
            throw new IllegalArgumentException("Test handler must be modifiable");
        }
    }

    private static long itemAmount(IItemHandler storage, int slot) {
        return storage instanceof LongItemStorage longStorage ? longStorage.amount(slot)
                : storage.getStackInSlot(slot).getCount();
    }

    private static Map<?, ?> autoIOStates(IOPortBlockEntity port) throws ReflectiveOperationException {
        Field field = IOPortBlockEntity.class.getDeclaredField("autoIOStates");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(port);
    }

    private static final class OperationOnlyPort extends IOPortBlockEntity {
        private final IOPortKind kind = PortKinds.ITEM_INPUT;
        private final CapabilitySnapshot snapshot;
        private int activeRecipeChecks;

        private OperationOnlyPort(MachineCapability... capabilities) {
            super(ModBlockEntities.BES.get(PortKinds.ITEM_INPUT.id()).get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());
            snapshot = new CapabilitySnapshot(List.of(capabilities));
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }

        @Override
        public IOPortKind kind() {
            return kind;
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return snapshot;
        }

        private void runAutoIOCycleForTesting() {
            runAutoIOCycle();
        }

        private void saveTo(CompoundTag output) {
            saveAdditional(output, HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        }

        private void loadFrom(CompoundTag input) {
            loadAdditional(input, HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        }

        @Override
        protected boolean isUsedByActiveRecipe() {
            activeRecipeChecks++;
            return false;
        }

        private int activeRecipeChecks() {
            return activeRecipeChecks;
        }
    }

    private static final class OperationOnlyItemCapability implements MachineCapability, OperationFacet, TransferFacet {
        private final AtomicInteger prepareCalls = new AtomicInteger();
        private final CapabilityType capabilityType;
        private boolean transferEnabled;

        private OperationOnlyItemCapability() {
            this(BuiltinCapabilityDefinitions.ITEM_TYPE);
        }

        private OperationOnlyItemCapability(CapabilityType capabilityType) {
            this.capabilityType = capabilityType;
        }

        @Override
        public CapabilityType type() {
            return capabilityType;
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.bidirectional();
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return OperationOnlyItemCapability.this.type();
                }

                @Override
                public CapabilityDirections directions() {
                    return CapabilityDirections.bidirectional();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return transferEnabled ? Set.of(OperationFacet.class, TransferFacet.class) : Set.of(OperationFacet.class);
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            prepareCalls.incrementAndGet();
            return CapabilityResult::successful;
        }

        @Override
        public CapabilityOperation prepareOperation(CapabilityRequest request) {
            return prepare(request);
        }

        private int prepareCalls() {
            return prepareCalls.get();
        }

        @Override public Level level() { return null; }
        @Override public BlockPos position() { return BlockPos.ZERO; }
        @Override public long transferLimit() { return 64L; }
    }

    private record Ports(Level level, ItemInputBusBlockEntity itemInput, ItemOutputBusBlockEntity itemOutput,
                         FluidInputHatchBlockEntity fluidInput, FluidOutputHatchBlockEntity fluidOutput,
                         EnergyInputHatchBlockEntity energyInput, EnergyOutputHatchBlockEntity energyOutput) {
    }
}
