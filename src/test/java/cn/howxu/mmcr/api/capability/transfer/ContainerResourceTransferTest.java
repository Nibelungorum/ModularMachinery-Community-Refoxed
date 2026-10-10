package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Directed native transfers preserve resources during simulation and partial acceptance.
 * @author howxu <dev@howxu.cn> */
class ContainerResourceTransferTest {
    @BeforeAll
    static void setup() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void input_and_output_move_only_in_the_ports_direction() {
        for (IOType direction : IOType.values()) {
            var container = new LongFluidStorage(4_000, () -> {});
            var port = new LongFluidStorage(4_000, () -> {});
            var source = direction == IOType.INPUT ? container : port;
            var destination = direction == IOType.INPUT ? port : container;
            source.setFluid(new FluidStack(Fluids.WATER, 2_000));
            assertThat(ContainerResourceTransfer.transfer(direction, container, port, false)).isEqualTo(2_000);
            assertThat(source.isEmpty()).isTrue();
            assertThat(destination.getAmountAsLong()).isEqualTo(2_000);
            assertThat(ContainerResourceTransfer.transfer(direction, container, port, false)).isZero();
            assertThat(destination.getAmountAsLong()).isEqualTo(2_000);
        }
    }

    @Test
    void simulation_preserves_both_sides_then_commit_retains_unaccepted_remainder() {
        for (IOType direction : IOType.values()) {
            var container = new LongFluidStorage(4_000, () -> {});
            var port = new LongFluidStorage(4_000, () -> {});
            var source = direction == IOType.INPUT ? container : port;
            var destination = direction == IOType.INPUT ? port : container;
            source.setFluid(new FluidStack(Fluids.WATER, 2_000));
            destination.setFluid(new FluidStack(Fluids.WATER, 3_000));
            assertThat(ContainerResourceTransfer.transfer(direction, container, port, true)).isEqualTo(1_000);
            assertThat(source.getAmountAsLong()).isEqualTo(2_000);
            assertThat(destination.getAmountAsLong()).isEqualTo(3_000);
            assertThat(ContainerResourceTransfer.transfer(direction, container, port, false)).isEqualTo(1_000);
            assertThat(source.getAmountAsLong()).isEqualTo(1_000);
            assertThat(destination.getAmountAsLong()).isEqualTo(4_000);
        }
    }

    @Test
    void incompatible_fluids_and_components_leave_both_sides_unchanged() {
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        FluidStack namedWater = water.copy();
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Other water"));
        for (FluidStack other : new FluidStack[]{new FluidStack(Fluids.LAVA, 1_000), namedWater}) {
            var container = new LongFluidStorage(4_000, () -> {});
            var port = new LongFluidStorage(4_000, () -> {});
            container.setFluid(water);
            port.setFluid(other);
            for (IOType direction : IOType.values()) {
                assertThat(ContainerResourceTransfer.transfer(direction, container, port, false)).isZero();
                assertThat(FluidStack.matches(container.getFluidStack(), water)).isTrue();
                assertThat(FluidStack.matches(port.getFluidStack(), other)).isTrue();
            }
        }
    }

    @Test
    void consumable_and_replacement_container_results_keep_native_counts() {
        var component = DataComponentType.<SimpleFluidContent>builder().persistent(SimpleFluidContent.CODEC).build();
        for (boolean consumable : new boolean[]{true, false}) {
            ItemStack carried = new ItemStack(Items.POTION, 2);
            ItemStack prepared = carried.copyWithCount(1);
            var handler = consumable ? new FluidHandlerItemStack.Consumable(() -> component, prepared, 1_000)
                    : new FluidHandlerItemStack.SwapEmpty(() -> component, prepared, new ItemStack(Items.GLASS_BOTTLE, 2), 1_000);
            handler.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandlerItem.FluidAction.EXECUTE);
            var port = new LongFluidStorage(4_000, () -> {});
            var result = ContainerResourceTransfer.transferCarried(IOType.INPUT, carried, handler, port, false);
            assertThat(result.amount()).isEqualTo(2_000);
            assertThat(carried.getCount()).isEqualTo(2);
            if (consumable) assertThat(result.carried().isEmpty()).isTrue();
            else {
                assertThat(result.carried().is(Items.GLASS_BOTTLE)).isTrue();
                assertThat(result.carried().getCount()).isEqualTo(4);
            }
            assertThat(port.getAmountAsLong()).isEqualTo(2_000);
        }
    }

    @Test
    void multi_tank_container_skips_incompatible_first_resource() {
        var contents = new LongFluidStorage(2, 4_000, () -> {});
        contents.setContents(0, new FluidStack(Fluids.LAVA, 1), 1_000);
        contents.setContents(1, new FluidStack(Fluids.WATER, 1), 2_000);
        var port = new LongFluidStorage(4_000, () -> {});
        port.setFluid(new FluidStack(Fluids.WATER, 3_000));
        ItemStack carried = new ItemStack(Items.BUCKET);
        var container = fluidContainer(contents, carried);
        var result = ContainerResourceTransfer.transferCarried(IOType.INPUT, carried, container, port, false);
        assertThat(result.amount()).isEqualTo(1_000);
        assertThat(contents.amount(0)).isEqualTo(1_000);
        assertThat(contents.resource(0).is(Fluids.LAVA)).isTrue();
        assertThat(contents.amount(1)).isEqualTo(1_000);
        assertThat(port.getAmountAsLong()).isEqualTo(4_000);
    }

    @Test
    void multi_tank_container_transfers_all_compatible_source_tanks() {
        var contents = new LongFluidStorage(2, 4_000, () -> {});
        contents.setContents(0, new FluidStack(Fluids.WATER, 1), 1_000);
        contents.setContents(1, new FluidStack(Fluids.WATER, 1), 2_000);
        var port = new LongFluidStorage(4_000, () -> {});
        ItemStack carried = new ItemStack(Items.BUCKET);
        var result = ContainerResourceTransfer.transferCarried(IOType.INPUT, carried,
                fluidContainer(contents, carried), port, false);
        assertThat(result.amount()).isEqualTo(3_000);
        assertThat(contents.amount(0)).isZero();
        assertThat(contents.amount(1)).isZero();
        assertThat(port.getAmountAsLong()).isEqualTo(3_000);
    }

    private static IFluidHandlerItem fluidContainer(LongFluidStorage contents, ItemStack carried) {
        IFluidHandlerItem container = new IFluidHandlerItem() {
            public ItemStack getContainer() { return carried.copy(); }
            public int getTanks() { return contents.getTanks(); }
            public FluidStack getFluidInTank(int tank) { return contents.getFluidInTank(tank); }
            public int getTankCapacity(int tank) { return contents.getTankCapacity(tank); }
            public boolean isFluidValid(int tank, FluidStack stack) { return contents.isFluidValid(tank, stack); }
            public int fill(FluidStack stack, FluidAction action) { return contents.fill(stack, action); }
            public FluidStack drain(FluidStack stack, FluidAction action) { return contents.drain(stack, action); }
            public FluidStack drain(int amount, FluidAction action) { return contents.drain(amount, action); }
        };
        return container;
    }

    @Test
    void missing_direction_or_handler_leaves_existing_contents_unchanged() {
        var container = new LongFluidStorage(4_000, () -> {});
        var port = new LongFluidStorage(4_000, () -> {});
        container.setFluid(new FluidStack(Fluids.WATER, 2_000));
        port.setFluid(new FluidStack(Fluids.WATER, 1_000));
        assertThat(ContainerResourceTransfer.transfer(null, container, port, false)).isZero();
        assertThat(ContainerResourceTransfer.transfer(IOType.INPUT, null, port, false)).isZero();
        assertThat(ContainerResourceTransfer.transfer(IOType.OUTPUT, container, null, false)).isZero();
        assertThat(container.getAmountAsLong()).isEqualTo(2_000);
        assertThat(port.getAmountAsLong()).isEqualTo(1_000);
    }
}
