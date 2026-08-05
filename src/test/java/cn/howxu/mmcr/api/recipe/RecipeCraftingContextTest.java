package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCraftingContextTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void doesNotCacheControllerLevelBecauseRestoredContextsMayBeCreatedBeforeLevelBinding() {
        boolean cachesLevel = Arrays.stream(RecipeCraftingContext.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("level"));

        assertThat(cachesLevel).isFalse();
    }

    @Test
    void item_input_requirement_aggregates_across_multiple_buses() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        Holder<net.minecraft.world.item.Item> iron = Holder.direct(Items.IRON_INGOT, DataComponentMap.EMPTY);
        TestItemBus first = itemInputBus(new BlockPos(1, 0, 0), stack(iron, 1));
        TestItemBus second = itemInputBus(new BlockPos(2, 0, 0), stack(iron, 2));
        MachineControllerBlockEntity controller = controller(controllerPos, List.of(component(first), component(second)), Map.of(
                first.getBlockPos(), first,
                second.getBlockPos(), second
        ));
        var context = new RecipeCraftingContext(controller);
        var recipe = recipe(new ItemRequirement(Ingredient.of(HolderSet.direct(iron)), 3, null, IOType.INPUT));

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.startCrafting(recipe)).isTrue();
        assertThat(first.getItemHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(second.getItemHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void item_requirement_uses_selector_tag_when_components_are_tagged() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        Holder<net.minecraft.world.item.Item> iron = Holder.direct(Items.IRON_INGOT, DataComponentMap.EMPTY);
        TestItemBus south = itemInputBus(new BlockPos(1, 0, 0), stack(iron, 1));
        TestItemBus north = itemInputBus(new BlockPos(2, 0, 0), stack(iron, 1));
        MachineControllerBlockEntity controller = controller(controllerPos, List.of(component(south, "south"), component(north, "north")), Map.of(
                south.getBlockPos(), south,
                north.getBlockPos(), north
        ));
        var context = new RecipeCraftingContext(controller);
        var recipe = recipe(new ItemRequirement(Ingredient.of(HolderSet.direct(iron)), 1, "north", IOType.INPUT));

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.startCrafting(recipe)).isTrue();
        assertThat(south.getItemHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(north.getItemHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void successful_simulate_clears_previous_failure() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        Holder<net.minecraft.world.item.Item> iron = Holder.direct(Items.IRON_INGOT, DataComponentMap.EMPTY);
        TestItemBus bus = itemInputBus(new BlockPos(1, 0, 0), stack(iron, 1));
        MachineControllerBlockEntity controller = controller(controllerPos, List.of(component(bus)), Map.of(bus.getBlockPos(), bus));
        var context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe(new ItemRequirement(Ingredient.of(HolderSet.direct(iron)), 2, null, IOType.INPUT)))).isFalse();
        assertThat(context.lastFailure().isSuccess()).isFalse();
        assertThat(context.simulateInputs(recipe(new ItemRequirement(Ingredient.of(HolderSet.direct(iron)), 1, null, IOType.INPUT)))).isTrue();
        assertThat(context.lastFailure()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftCheck.success());
    }

    @Test
    void fluid_input_requirement_aggregates_across_multiple_hatches() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        Holder<Fluid> water = Holder.direct(Fluids.WATER, DataComponentMap.EMPTY);
        TestFluidHatch first = fluidInputHatch(new BlockPos(1, 0, 0), water, 400);
        TestFluidHatch second = fluidInputHatch(new BlockPos(2, 0, 0), water, 700);
        MachineControllerBlockEntity controller = controller(controllerPos, List.of(component(first), component(second)), Map.of(
                first.getBlockPos(), first,
                second.getBlockPos(), second
        ));
        var context = new RecipeCraftingContext(controller);
        var recipe = recipe(new FluidRequirement(FluidIngredient.of(HolderSet.direct(water)), 1000, null, IOType.INPUT));

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.startCrafting(recipe)).isTrue();
        assertThat(first.getFluidHandler(null).getFluidInTank(0).getAmount()).isZero();
        assertThat(second.getFluidHandler(null).getFluidInTank(0).getAmount()).isEqualTo(100);
    }

    @Test
    void fluid_input_requirement_commits_only_matching_fluid() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        Holder<Fluid> water = Holder.direct(Fluids.WATER, DataComponentMap.EMPTY);
        Holder<Fluid> lava = Holder.direct(Fluids.LAVA, DataComponentMap.EMPTY);
        TestMultiFluidHatch hatch = multiFluidInputHatch(new BlockPos(1, 0, 0),
                new FluidStack(lava, 1000),
                new FluidStack(water, 1000));
        MachineControllerBlockEntity controller = controller(controllerPos, List.of(component(hatch)), Map.of(hatch.getBlockPos(), hatch));
        var context = new RecipeCraftingContext(controller);
        var recipe = recipe(new FluidRequirement(FluidIngredient.of(HolderSet.direct(water)), 700, null, IOType.INPUT));

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.startCrafting(recipe)).isTrue();
        assertThat(hatch.handler.tanks[0].getFluid()).isEqualTo(Fluids.LAVA);
        assertThat(hatch.handler.tanks[0].getAmount()).isEqualTo(1000);
        assertThat(hatch.handler.tanks[1].getFluid()).isEqualTo(Fluids.WATER);
        assertThat(hatch.handler.tanks[1].getAmount()).isEqualTo(300);
    }

    private static MachineRecipe recipe(MachineRequirement requirement) {
        return new MachineRecipe(
                cn.howxu.mmcr.MMCR.id("requirement_test"),
                cn.howxu.mmcr.MMCR.id("test_cube"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(requirement)
        );
    }

    private static TestItemBus itemInputBus(BlockPos pos, ItemStack stack) {
        var bus = new TestItemBus(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        bus.handler.stack = stack;
        return bus;
    }

    private static ItemStack stack(Holder<net.minecraft.world.item.Item> item, int count) {
        return new ItemStack(item, count);
    }

    private static TestFluidHatch fluidInputHatch(BlockPos pos, Holder<Fluid> fluid, int amount) {
        var hatch = new TestFluidHatch(pos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
        hatch.getFluidTank(null).setFluid(new FluidStack(fluid, amount));
        return hatch;
    }

    private static TestMultiFluidHatch multiFluidInputHatch(BlockPos pos, FluidStack... stacks) {
        return new TestMultiFluidHatch(pos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), stacks);
    }

    private static ProcessingComponent component(MachineComponentTile tile) {
        return component(tile, (String) null);
    }

    private static ProcessingComponent component(MachineComponentTile tile, String tag) {
        var blockEntity = (net.minecraft.world.level.block.entity.BlockEntity) tile;
        return new ProcessingComponent(tile.provideComponent(), blockEntity, blockEntity.getBlockPos(), blockEntity.getBlockPos(), tag);
    }

    private static MachineControllerBlockEntity controller(BlockPos pos, List<ProcessingComponent> components, Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> liveComponents) throws Exception {
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe().allocateInstance(MachineControllerBlockEntity.class);
        setField(net.minecraft.world.level.block.entity.BlockEntity.class, controller, "worldPosition", pos);
        setField(net.minecraft.world.level.block.entity.BlockEntity.class, controller, "level", LevelStub.createWithBlockEntities(liveComponents));
        setField(MachineControllerBlockEntity.class, controller, "components", new java.util.ArrayList<>(components));
        return controller;
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class TestItemBus extends ItemBusBlockEntity {
        private final SimpleItemHandler handler = new SimpleItemHandler();

        private TestItemBus(BlockPos pos, BlockState state) {
            super(BlockEntityType.CHEST, pos, state);
        }

        @Override public IItemHandler getItemHandler(net.minecraft.core.Direction side) { return handler; }

        @Override public IOType ioType() { return IOType.INPUT; }

        @Override public IOPortKind kind() { return PortKinds.ITEM_INPUT; }

        @Override public void setChanged() {}
    }

    private static final class SimpleItemHandler implements IItemHandler {
        private ItemStack stack = ItemStack.EMPTY;

        @Override public int getSlots() { return 1; }

        @Override public ItemStack getStackInSlot(int slot) { return stack; }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int extracted = Math.min(amount, stack.getCount());
            if (extracted <= 0) return ItemStack.EMPTY;
            ItemStack out = stack.copyWithCount(extracted);
            if (!simulate) stack.shrink(extracted);
            return out;
        }

        @Override public int getSlotLimit(int slot) { return 64; }

        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private static final class TestFluidHatch extends FluidHatchBlockEntity {
        private TestFluidHatch(BlockPos pos, BlockState state) {
            super(BlockEntityType.BARREL, pos, state);
        }

        @Override public IOType ioType() { return IOType.INPUT; }

        @Override public IOPortKind kind() { return PortKinds.FLUID_INPUT; }

        @Override public void setChanged() {}
    }

    private static final class TestMultiFluidHatch extends FluidHatchBlockEntity {
        private final SimpleFluidHandler handler;

        private TestMultiFluidHatch(BlockPos pos, BlockState state, FluidStack... stacks) {
            super(BlockEntityType.BARREL, pos, state);
            this.handler = new SimpleFluidHandler(stacks);
        }

        @Override public IFluidHandler getFluidHandler(net.minecraft.core.Direction side) { return handler; }

        @Override public IOType ioType() { return IOType.INPUT; }

        @Override public IOPortKind kind() { return PortKinds.FLUID_INPUT; }

        @Override public void setChanged() {}
    }

    private static final class SimpleFluidHandler implements IFluidHandler {
        private final FluidStack[] tanks;

        private SimpleFluidHandler(FluidStack... tanks) {
            this.tanks = java.util.Arrays.stream(tanks).map(FluidStack::copy).toArray(FluidStack[]::new);
        }

        @Override public int getTanks() { return tanks.length; }

        @Override public FluidStack getFluidInTank(int tank) { return tanks[tank]; }

        @Override public int getTankCapacity(int tank) { return 8000; }

        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }

        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            for (FluidStack tank : tanks) {
                if (!tank.isEmpty() && FluidStack.isSameFluidSameComponents(tank, resource)) {
                    return drainFromTank(tank, resource.getAmount(), action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            for (FluidStack tank : tanks) {
                if (!tank.isEmpty()) return drainFromTank(tank, maxDrain, action);
            }
            return FluidStack.EMPTY;
        }

        private static FluidStack drainFromTank(FluidStack tank, int maxDrain, FluidAction action) {
            int drained = Math.min(maxDrain, tank.getAmount());
            FluidStack out = tank.copyWithAmount(drained);
            if (action.execute()) tank.shrink(drained);
            return out;
        }
    }
}
