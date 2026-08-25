package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.transfer.TransferResult;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.event.ModCapabilities;
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
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

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

        TransferResult noTarget = inputPolicy.transfer(inputCapability, Direction.NORTH);
        TransferResult noWork = outputPolicy.transfer(outputCapability, Direction.NORTH);

        assertThat(noTarget.successful()).isFalse();
        assertThat(noTarget.amount()).isZero();
        assertThat(noTarget.failure().details()).containsEntry("reason", "no_target");
        assertThat(noWork.failure().details()).containsEntry("reason", "no_work");
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

        ports.itemOutput.getItemStackHandler(null).setStackInSlot(0, stack(2));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemOutput.getBlockPos(),
                itemHandler(ports.itemOutput, false, true));
        assertThat(CapabilityTransferPolicies.policyFor(itemInput).orElseThrow()
                .transfer(itemInput, Direction.EAST).amount()).isEqualTo(2);
        assertThat(ports.itemInput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);

        ports.itemOutput.getItemStackHandler(null).setStackInSlot(0, stack(3));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemInput.getBlockPos(),
                itemHandler(ports.itemInput, true, false));
        assertThat(CapabilityTransferPolicies.policyFor(itemOutput).orElseThrow()
                .eject(itemOutput, Direction.WEST).amount()).isEqualTo(3);
        assertThat(ports.itemInput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(5);

        ports.fluidOutput.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 400));
        LevelStub.setCapability(ports.level, ModCapabilities.FLUID_BLOCK, ports.fluidOutput.getBlockPos(),
                ports.fluidOutput.getResourceHandler(null));
        assertThat(CapabilityTransferPolicies.policyFor(fluidInput).orElseThrow()
                .transfer(fluidInput, Direction.EAST).amount()).isEqualTo(400);
        assertThat(ports.fluidInput.fluidStorage().getAmountAsLong()).isEqualTo(400);

        ports.fluidOutput.fluidStorage().setFluid(new FluidStack(Fluids.WATER, 500));
        LevelStub.setCapability(ports.level, ModCapabilities.FLUID_BLOCK, ports.fluidInput.getBlockPos(),
                ports.fluidInput.getResourceHandler(null));
        assertThat(CapabilityTransferPolicies.policyFor(fluidOutput).orElseThrow()
                .eject(fluidOutput, Direction.WEST).amount()).isEqualTo(500);
        assertThat(ports.fluidInput.fluidStorage().getAmountAsLong()).isEqualTo(900);

        ports.energyOutput.energyStorage().setAmount(600);
        LevelStub.setCapability(ports.level, ModCapabilities.ENERGY_BLOCK, ports.energyOutput.getBlockPos(),
                ports.energyOutput.getEnergyHandler(null));
        assertThat(CapabilityTransferPolicies.policyFor(energyInput).orElseThrow()
                .transfer(energyInput, Direction.EAST).amount()).isEqualTo(600);
        assertThat(ports.energyInput.energyStorage().getAmountAsLong()).isEqualTo(600);

        ports.energyOutput.energyStorage().setAmount(700);
        LevelStub.setCapability(ports.level, ModCapabilities.ENERGY_BLOCK, ports.energyInput.getBlockPos(),
                ports.energyInput.getEnergyHandler(null));
        assertThat(CapabilityTransferPolicies.policyFor(energyOutput).orElseThrow()
                .eject(energyOutput, Direction.WEST).amount()).isEqualTo(700);
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
        long amount = (long) Integer.MAX_VALUE + 1L;
        output.energyStorage().setAmount(amount);
        LevelStub.setCapability(level, ModCapabilities.ENERGY_BLOCK, output.getBlockPos(), output.getEnergyHandler(null));

        var capability = input.capabilitySnapshot().capabilities().getFirst();
        TransferResult result = CapabilityTransferPolicies.policyFor(capability).orElseThrow()
                .transfer(capability, Direction.EAST);

        assertThat(result.amount()).isEqualTo(amount);
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(amount);
        assertThat(output.energyStorage().getAmountAsLong()).isZero();
    }

    @Test
    void disabled_or_unavailable_side_does_not_mutate_real_storage() {
        Ports ports = connectedPorts();
        var input = ports.itemInput.capabilitySnapshot().capabilities().getFirst();
        ports.itemOutput.getItemStackHandler(null).setStackInSlot(0, stack(2));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemOutput.getBlockPos(),
                itemHandler(ports.itemOutput, false, true));

        TransferResult blocked = CapabilityTransferPolicies.policyFor(input).orElseThrow()
                .transfer(input, Direction.WEST);

        assertThat(blocked.successful()).isFalse();
        assertThat(blocked.failure().details()).containsEntry("reason", "no_target");
        assertThat(ports.itemInput.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
        assertThat(ports.itemOutput.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void invalidCapabilityAndSideAreBlockedWithoutMutatingStorage() {
        MachineCapability unknown = new MachineCapability() {
            private final CapabilityType capabilityType = new CapabilityType(MMCR.id("unknown"));

            @Override public CapabilityType type() { return capabilityType; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public cn.howxu.mmcr.api.capability.CapabilityView view() {
                return new cn.howxu.mmcr.api.capability.CapabilityView() {
                    @Override public CapabilityType type() { return capabilityType; }
                    @Override public IOType ioType() { return IOType.INPUT; }
                };
            }
            @Override public CapabilityOperation prepare(cn.howxu.mmcr.api.capability.CapabilityRequest request) { return null; }
        };

        assertThat(CapabilityTransferPolicies.policyFor(unknown)).isEmpty();
        assertThat(CapabilityTransferPolicies.policyFor(null)).isEmpty();
    }

    @Test
    void output_port_ejection_is_rejected_before_transfer_policy_runs() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(BlockPos.ZERO);
        output.getItemStackHandler(null).setStackInSlot(0, stack(2));

        assertThat(output.ejectContents()).isFalse();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
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
        ports.itemOutput.getItemStackHandler(null).setStackInSlot(0, stack(2));
        LevelStub.setCapability(ports.level, ModCapabilities.ITEM_BLOCK, ports.itemOutput.getBlockPos(),
                itemHandler(ports.itemOutput, false, true));

        LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 2, 100L, resource -> resource.isEmpty(), () -> {});
        ItemBusCapability capability = new ItemBusCapability(ports.itemInput, storage, IOType.INPUT);
        var policy = CapabilityTransferPolicies.policyFor(capability).orElseThrow();

        assertThatCode(() -> policy.hasWork(capability)).doesNotThrowAnyException();
        TransferResult result = policy.transfer(capability, Direction.EAST);

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

        TransferResult result = CapabilityTransferPolicies.policyFor(output).orElseThrow()
                .eject(output, Direction.WEST);

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

    private static ExtendedEnergyHatchBlockEntity extendedEnergy(String id, BlockPos position) {
        return (ExtendedEnergyHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(
                position, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static ItemStack stack(int count) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    @SuppressWarnings("unchecked")
    private static ResourceHandler<ItemResource> itemHandler(ItemBusBlockEntity port, boolean canInsert,
                                                              boolean canExtract) {
        try {
            Class<?> type = Class.forName("cn.howxu.mmcr.internal.event.ModCapabilities$ItemStackResourceHandler");
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : type.getDeclaredConstructors()) {
                if (candidate.getParameterCount() == 3) {
                    constructor = candidate;
                    break;
                }
            }
            if (constructor == null) throw new NoSuchMethodException("Item capability adapter constructor");
            constructor.setAccessible(true);
            return (ResourceHandler<ItemResource>) constructor.newInstance(port.getItemStackHandler(null), canInsert,
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
