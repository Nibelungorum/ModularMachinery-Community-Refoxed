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
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.transfer.TransferContext;
import cn.howxu.mmcr.api.capability.transfer.TransferPolicy;
import cn.howxu.mmcr.api.capability.transfer.TransferResult;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedEnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ProblemReporter;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

        assertThat(CapabilityTransferPolicies.policyFor(item.capabilitySnapshot().capabilities().getFirst())).isPresent();
        assertThat(CapabilityTransferPolicies.policyFor(fluid.capabilitySnapshot().capabilities().getFirst())).isPresent();
        assertThat(CapabilityTransferPolicies.policyFor(energy.capabilitySnapshot().capabilities().getFirst())).isPresent();
    }

    @Test
    void inputAndOutputWorkPoliciesReflectStoredContents() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        var inputCapability = input.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = output.capabilitySnapshot().capabilities().getFirst();
        var inputPolicy = CapabilityTransferPolicies.policyFor(inputCapability).orElseThrow();
        var outputPolicy = CapabilityTransferPolicies.policyFor(outputCapability).orElseThrow();

        assertThat(inputPolicy.hasWork(inputCapability)).isTrue();
        assertThat(outputPolicy.hasWork(outputCapability)).isFalse();
    }

    @Test
    void noTargetAndNoWorkReturnStructuredBlockedResults() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        var inputCapability = input.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = output.capabilitySnapshot().capabilities().getFirst();
        var inputPolicy = CapabilityTransferPolicies.policyFor(inputCapability).orElseThrow();
        var outputPolicy = CapabilityTransferPolicies.policyFor(outputCapability).orElseThrow();

        TransferResult noTarget = simulate(inputPolicy, inputCapability, Direction.NORTH);
        TransferResult noWork = simulate(outputPolicy, outputCapability, Direction.NORTH);

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

        setItem(ports.itemOutput.itemStorage(), 0, stack(2));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemOutput.getBlockPos(),
                itemHandler(ports.itemOutput, false, true));
        assertThat(transfer(CapabilityTransferPolicies.policyFor(itemInput).orElseThrow(), itemInput,
                Direction.EAST).amount()).isEqualTo(2);
        assertThat(ports.itemInput.itemStorage().amount(0)).isEqualTo(2L);

        setItem(ports.itemOutput.itemStorage(), 0, stack(3));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemInput.getBlockPos(),
                itemHandler(ports.itemInput, true, false));
        assertThat(eject(CapabilityTransferPolicies.policyFor(itemOutput).orElseThrow(), itemOutput,
                Direction.WEST).amount()).isEqualTo(3);
        assertThat(ports.itemInput.itemStorage().amount(0)).isEqualTo(5L);

        ports.fluidOutput.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 400));
        LevelStub.setCapability(ports.level, ModCapabilities.FLUID_BLOCK, ports.fluidOutput.getBlockPos(),
                ports.fluidOutput.getResourceHandler(null));
        assertThat(transfer(CapabilityTransferPolicies.policyFor(fluidInput).orElseThrow(), fluidInput,
                Direction.EAST).amount()).isEqualTo(400);
        assertThat(ports.fluidInput.fluidStorage().getAmountAsLong()).isEqualTo(400);

        ports.fluidOutput.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 500));
        LevelStub.setCapability(ports.level, ModCapabilities.FLUID_BLOCK, ports.fluidInput.getBlockPos(),
                ports.fluidInput.getResourceHandler(null));
        assertThat(eject(CapabilityTransferPolicies.policyFor(fluidOutput).orElseThrow(), fluidOutput,
                Direction.WEST).amount()).isEqualTo(500);
        assertThat(ports.fluidInput.fluidStorage().getAmountAsLong()).isEqualTo(900);

        ports.energyOutput.energyStorage().setAmount(600);
        LevelStub.setCapability(ports.level, ModCapabilities.ENERGY_BLOCK, ports.energyOutput.getBlockPos(),
                ports.energyOutput.getEnergyHandler(null));
        assertThat(transfer(CapabilityTransferPolicies.policyFor(energyInput).orElseThrow(), energyInput,
                Direction.EAST).amount()).isEqualTo(600);
        assertThat(ports.energyInput.energyStorage().getAmountAsLong()).isEqualTo(600);

        ports.energyOutput.energyStorage().setAmount(700);
        LevelStub.setCapability(ports.level, ModCapabilities.ENERGY_BLOCK, ports.energyInput.getBlockPos(),
                ports.energyInput.getEnergyHandler(null));
        assertThat(eject(CapabilityTransferPolicies.policyFor(energyOutput).orElseThrow(), energyOutput,
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
        LevelStub.setCapability(level, ModCapabilities.ENERGY_BLOCK, output.getBlockPos(), output.getEnergyHandler(null));

        var capability = input.capabilitySnapshot().capabilities().getFirst();
        TransferResult result = transfer(CapabilityTransferPolicies.policyFor(capability).orElseThrow(), capability,
                Direction.EAST);

        assertThat(result.amount()).isEqualTo(amount);
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(amount);
        assertThat(output.energyStorage().getAmountAsLong()).isZero();
    }

    @Test
    void disabled_or_unavailable_side_does_not_mutate_real_storage() {
        Ports ports = connectedPorts();
        var input = ports.itemInput.capabilitySnapshot().capabilities().getFirst();
        setItem(ports.itemOutput.itemStorage(), 0, stack(2));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemOutput.getBlockPos(),
                itemHandler(ports.itemOutput, false, true));

        TransferResult blocked = simulate(CapabilityTransferPolicies.policyFor(input).orElseThrow(), input,
                Direction.WEST);

        assertThat(blocked.successful()).isFalse();
        assertThat(blocked.failure().reason()).isEqualTo(BuiltinFailureReasons.NO_TARGET);
        assertThat(ports.itemInput.itemStorage().amount(0)).isZero();
        assertThat(ports.itemOutput.itemStorage().amount(0)).isEqualTo(2L);
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

        assertThat(CapabilityTransferPolicies.policyFor(unknown)).isEmpty();
        assertThat(CapabilityTransferPolicies.policyFor(null)).isEmpty();
    }

    @Test
    void operation_only_bidirectional_item_capability_is_excluded_from_auto_io() {
        OperationOnlyItemCapability capability = new OperationOnlyItemCapability();

        assertThat(CapabilityTransferPolicies.policyFor(capability)).isEmpty();
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

        TagValueOutput saved = tagOutput();
        port.saveTo(saved);
        assertThat(saved.buildResult().getCompound("auto_io_capabilities")).isEmpty();

        TagValueOutput injected = tagOutput();
        injected.child("auto_io_capabilities").child(capability.type().id().toString())
                .putBoolean("enabled", true);
        port.loadFrom(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), injected.buildResult()));
        TagValueOutput restored = tagOutput();
        port.saveTo(restored);

        assertThat(restored.buildResult().getCompound("auto_io_capabilities")).isEmpty();
        assertThat(port.ejectContents(capability.type())).isFalse();
        assertThat(port.ejectContents()).isFalse();
        assertThat(port.activeRecipeChecks()).isZero();
        assertThat(capability.prepareCalls()).isZero();
        assertThat(LevelStub.capabilityLookups(level)).isEqualTo(lookupsBefore);
    }

    @Test
    void output_port_ejection_is_rejected_before_transfer_policy_runs() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(BlockPos.ZERO);
        setItem(output.itemStorage(), 0, stack(2));

        assertThat(output.ejectContents()).isFalse();
        assertThat(output.itemStorage().amount(0)).isEqualTo(2L);
    }

    @Test
    void dynamic_transfer_declaration_is_rechecked_without_replacing_the_port_snapshot() {
        OperationOnlyItemCapability capability = new OperationOnlyItemCapability();
        OperationOnlyPort port = new OperationOnlyPort(capability);
        CapabilitySnapshot snapshot = port.capabilitySnapshot();

        assertThat(port.isAutoIOSideExposed(capability.type(), null)).isFalse();
        capability.transferEnabled = true;
        assertThat(CapabilityTransferPolicies.policyFor(capability)).isPresent();
        assertThat(port.isAutoIOSideExposed(capability.type(), null)).isTrue();
        capability.transferEnabled = false;
        assertThat(CapabilityTransferPolicies.policyFor(capability)).isEmpty();
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
    void configurable_endpoint_skips_unusable_occurrences_and_tick_keeps_the_shared_profile() {
        var unavailable = new OperationOnlyItemCapability();
        var eligible = new OperationOnlyItemCapability();
        eligible.transferEnabled = true;
        var wrongDirection = new ItemBusCapability(new LongResourceStorage<>(
                ItemResource.class, 1, 64L, ItemResource::isEmpty, () -> {}), IOType.OUTPUT);
        var port = new OperationOnlyPort(wrongDirection, unavailable, eligible, unavailable);
        port.setLevel(LevelStub.createWithBlockEntities(List.of(port)));
        assertThat(port.configurableAutoIOIds()).containsExactly(eligible.type().id());
        assertThat(port.configurableAutoIOCapability(eligible.type())).isSameAs(eligible);
        port.setAutoIOEnabled(eligible.type(), true);
        port.runAutoIOCycleForTesting();
        assertThat(port.autoIOConfig(eligible.type()).enabled()).isTrue();
        eligible.transferEnabled = false;
        assertThat(port.configurableAutoIOIds()).isEmpty();
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
    void longValueStorageKeepsLongAmountsAndHonorsTransactionRollback() {
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, () -> {});
        long amount = (long) Integer.MAX_VALUE + 17L;

        try (Transaction transaction = Transaction.openRoot()) {
            storage.updateSnapshots(transaction);
            assertThat(storage.insert(amount, false)).isEqualTo(amount);
        }

        assertThat(storage.amount()).isZero();
        assertThat(new TransferResult(true, amount, null).amount()).isEqualTo(amount);
    }

    @Test
    void empty_long_resource_storage_is_safe_for_auto_io_and_handler_projection() {
        Ports ports = connectedPorts();
        setItem(ports.itemOutput.itemStorage(), 0, stack(2));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemOutput.getBlockPos(),
                itemHandler(ports.itemOutput, false, true));

        LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 2, 100L, resource -> resource.isEmpty(), () -> {});
        ItemBusCapability capability = new ItemBusCapability(ports.itemInput, storage, IOType.INPUT);
        var policy = CapabilityTransferPolicies.policyFor(capability).orElseThrow();

        assertThatCode(() -> policy.hasWork(capability)).doesNotThrowAnyException();
        TransferResult result = transfer(policy, capability, Direction.EAST);

        assertThat(result.successful()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(2L);
        assertThat(storage.resource(0)).isNotNull();
    }

    @Test
    void fluid_policy_scans_all_slots_for_work_and_ejects_slot_one() {
        LongFluidStorage inputStorage = new LongFluidStorage(2, 100L, () -> {});
        inputStorage.setContents(0, FluidResource.of(Fluids.WATER), 100L);
        inputStorage.setContents(1, FluidResource.of(Fluids.LAVA), 20L);
        FluidHatchCapability input = new FluidHatchCapability(inputStorage, IOType.INPUT);
        var inputPolicy = CapabilityTransferPolicies.policyFor(input).orElseThrow();

        assertThat(inputPolicy.hasWork(input)).isTrue();

        Ports ports = connectedPorts();
        LongFluidStorage outputStorage = new LongFluidStorage(2, 100L, () -> {});
        outputStorage.setContents(1, FluidResource.of(Fluids.WATER), 40L);
        FluidHatchCapability output = new FluidHatchCapability(ports.fluidOutput, outputStorage, IOType.OUTPUT);
        LevelStub.setCapability(ports.level, ModCapabilities.FLUID_BLOCK, ports.fluidInput.getBlockPos(),
                ports.fluidInput.getResourceHandler(null));

        TransferResult result = eject(CapabilityTransferPolicies.policyFor(output).orElseThrow(), output,
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

    private static TransferResult simulate(TransferPolicy policy, MachineCapability capability, Direction side) {
        return policy.transfer(TransferContext.simulate(capability, side, 1L));
    }

    private static TransferResult transfer(TransferPolicy policy, MachineCapability capability, Direction side) {
        try (Transaction transaction = Transaction.openRoot()) {
            TransferResult result = policy.transfer(TransferContext.commit(capability, side, 1L, transaction));
            if (result.successful()) transaction.commit();
            return result;
        }
    }

    private static TransferResult eject(TransferPolicy policy, MachineCapability capability, Direction side) {
        try (Transaction transaction = Transaction.openRoot()) {
            TransferResult result = policy.eject(TransferContext.commit(capability, side, 1L, transaction));
            if (result.successful()) transaction.commit();
            return result;
        }
    }

    private static ExtendedEnergyHatchBlockEntity extendedEnergy(String id, BlockPos position) {
        return (ExtendedEnergyHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(
                position, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static ItemStack stack(int count) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static void setItem(ResourceStorage<ItemResource> storage, int slot, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource current = storage.resource(slot);
            if (current != null && !current.isEmpty()) {
                storage.extract(slot, current, storage.amount(slot), transaction);
            }
            storage.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
        }
    }

    private static TagValueOutput tagOutput() {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
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

        private void saveTo(TagValueOutput output) {
            saveAdditional(output);
        }

        private void loadFrom(ValueInput input) {
            loadAdditional(input);
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
            return transaction -> CapabilityResult.successful();
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

    @SuppressWarnings("unchecked")
    private static ResourceHandler<ItemResource> itemHandler(ItemBusBlockEntity port, boolean canInsert,
                                                              boolean canExtract) {
        try {
            Class<?> type = Class.forName("cn.howxu.mmcr.internal.event.ModCapabilities$ResourceStorageHandler");
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : type.getDeclaredConstructors()) {
                if (candidate.getParameterCount() == 3) {
                    constructor = candidate;
                    break;
                }
            }
            if (constructor == null) throw new NoSuchMethodException("Item capability adapter constructor");
            constructor.setAccessible(true);
            return (ResourceHandler<ItemResource>) constructor.newInstance(port.itemStorage(), canInsert,
                    canExtract);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create the production item capability adapter", exception);
        }
    }

    private record Ports(Level level, ItemInputBusBlockEntity itemInput, ItemOutputBusBlockEntity itemOutput,
                         FluidInputHatchBlockEntity fluidInput, FluidOutputHatchBlockEntity fluidOutput,
                         EnergyInputHatchBlockEntity energyInput, EnergyOutputHatchBlockEntity energyOutput) {
    }
}
