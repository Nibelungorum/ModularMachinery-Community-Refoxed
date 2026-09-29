package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final automatic IO port behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class AutoIOPortTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        Items.IRON_INGOT.resetDefaultResource();
        Items.GOLD_INGOT.builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        Items.GOLD_INGOT.resetDefaultResource();
    }

    @Test
    void input_ejection_retries_after_a_missing_target_and_completes_the_transfer() {
        ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity target = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        setItems(source, 3L);
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);

        assertThat(source.ejectContents()).isFalse();
        assertThat(source.itemStorage().amount(0)).isEqualTo(3L);

        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));

        assertThat(source.ejectContents()).isTrue();
        assertThat(source.itemStorage().amount(0)).isZero();
        assertThat(target.itemStorage().amount(0)).isEqualTo(3L);
    }

    @Test
    void auto_io_uses_only_enabled_sides_and_publishes_configuration_changes() {
        ItemOutputBusBlockEntity source = RuntimeTestFixtures.itemOutput(BlockPos.ZERO);
        ItemInputBusBlockEntity target = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        setItems(source, 3L);
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));
        int updatesBefore = LevelStub.sentBlockUpdates(level);

        source.setAutoIOEnabled(true);
        source.setAllAutoIOSides(false);
        source.setAutoIOSide(Direction.EAST, true);
        for (int tick = 0; tick < 6; tick++) source.serverTick();

        assertThat(source.autoIOConfig().enabled()).isTrue();
        assertThat(source.autoIOConfig().enabledSides()).containsExactly(Direction.EAST);
        assertThat(source.autoIOCandidateCount()).isEqualTo(1);
        assertThat(source.itemStorage().amount(0)).isZero();
        assertThat(target.itemStorage().amount(0)).isEqualTo(3L);
        assertThat(LevelStub.sentBlockUpdates(level)).isGreaterThan(updatesBefore);
    }

    @Test
    void full_ejection_moves_real_contents_to_the_available_adjacent_port() {
        ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity target = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        setItems(source, 4L);
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));

        assertThat(source.ejectContents()).isTrue();
        assertThat(source.itemStorage().amount(0)).isZero();
        assertThat(target.itemStorage().amount(0)).isEqualTo(4L);
    }

    @Test
    void normal_ejection_moves_only_the_first_resource_while_shift_moves_all_resources() {
        ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity target = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        setItem(source, 0, Items.IRON_INGOT, 3L);
        setItem(source, 1, Items.GOLD_INGOT, 4L);
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));

        assertThat(source.ejectContents(BuiltinCapabilityDefinitions.ITEM_TYPE, false)).isTrue();
        assertThat(source.itemStorage().amount(0)).isZero();
        assertThat(source.itemStorage().amount(1)).isEqualTo(4L);

        assertThat(source.ejectContents(BuiltinCapabilityDefinitions.ITEM_TYPE, true)).isTrue();
        assertThat(source.itemStorage().amount(1)).isZero();
        assertThat(target.itemStorage().amount(0) + target.itemStorage().amount(1)).isEqualTo(7L);
    }

    @Test
    void extended_item_ejection_ignores_the_automatic_io_stack_limit() {
        ExtendedItemBusBlockEntity source = extendedItemBus("extended_item_input_bus_basic", BlockPos.ZERO);
        ExtendedItemBusBlockEntity target = extendedItemBus("extended_item_output_bus_basic", new BlockPos(1, 0, 0));
        setItem(source, 0, Items.IRON_INGOT, 2_100L);
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));

        assertThat(source.ejectContents(BuiltinCapabilityDefinitions.ITEM_TYPE, false)).isTrue();
        assertThat(source.itemStorage().amount(0)).isZero();
        assertThat(target.itemStorage().amount(0)).isEqualTo(2_100L);
    }

    @Test
    void extended_fluid_ejection_uses_the_selected_control_mode() {
        ExtendedFluidHatchBlockEntity source = extendedFluidHatch("extended_fluid_input_hatch_basic", BlockPos.ZERO);
        ExtendedFluidHatchBlockEntity target = extendedFluidHatch("extended_fluid_input_hatch_basic",
                new BlockPos(1, 0, 0));
        source.fluidStorage().setContents(0, new FluidStack(Fluids.WATER, 1), 2_000L);
        source.fluidStorage().setContents(1, new FluidStack(Fluids.LAVA, 1), 3_000L);
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.FLUID_BLOCK, target.getBlockPos(),
                target.nativeFluidHandler());

        assertThat(source.ejectContents(BuiltinCapabilityDefinitions.FLUID_TYPE, false)).isTrue();
        assertThat(source.fluidStorage().amount(0)).isZero();
        assertThat(source.fluidStorage().amount(1)).isEqualTo(3_000L);

        assertThat(source.ejectContents(BuiltinCapabilityDefinitions.FLUID_TYPE, true)).isTrue();
        assertThat(source.fluidStorage().amount(1)).isZero();
        assertThat(target.fluidStorage().amount(0) + target.fluidStorage().amount(1)).isEqualTo(5_000L);
    }

    @Test
    void output_port_rejects_manual_ejection_without_mutating_contents() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(BlockPos.ZERO);
        setItems(output, 2L);

        assertThat(output.ejectContents()).isFalse();
        assertThat(output.itemStorage().amount(0)).isEqualTo(2L);
    }

    @Test
    void auto_io_does_not_eject_a_port_used_by_an_active_factory_lane() throws Exception {
        ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(new BlockPos(0, 0, 1));
        ItemOutputBusBlockEntity target = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 1));
        setItems(source, 3L);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), source);
        Level level = controller.getLevel();
        LevelStub.putBlockEntity(level, target);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));
        source.linkControllerAppearance(controller.getBlockPos(), MMCR.id("test_factory_port"));

        FactoryRuntime factory = factoryRuntime(controller);
        factory.ensureBaseLane(controller);
        factory.tick(List.of(RecipeTestSupport.create(MMCR.id("factory_port_ownership"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1)), 1);

        assertThat(controller.isPortUsedByActiveRecipe(source.getBlockPos())).isTrue();
        assertThat(source.ejectContents()).isFalse();
        assertThat(source.itemStorage().amount(0)).isEqualTo(3L);
    }

    private static void setItems(ItemBusBlockEntity port, long amount) {
        setItem(port, 0, Items.IRON_INGOT, amount);
    }

    private static void setItem(ItemBusBlockEntity port, int slot, net.minecraft.world.item.Item item, long amount) {
        port.itemStorage().forceInsert(slot, new ItemStack(item), amount, false);
    }

    private static ExtendedItemBusBlockEntity extendedItemBus(String id, BlockPos pos) {
        return new ExtendedItemBusBlockEntity(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static ExtendedFluidHatchBlockEntity extendedFluidHatch(String id, BlockPos pos) {
        return new ExtendedFluidHatchBlockEntity(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static FactoryRuntime factoryRuntime(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) field.get(controller);
        return runtime.factoryRuntime();
    }

    @SuppressWarnings("unchecked")
    private static IItemHandler itemHandler(ItemBusBlockEntity port, boolean canInsert, boolean canExtract) {
        try {
            Class<?> type = Class.forName("cn.howxu.mmcr.internal.event.ModCapabilities$DirectionalItemHandler");
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : type.getDeclaredConstructors()) {
                if (candidate.getParameterCount() == 3) {
                    constructor = candidate;
                    break;
                }
            }
            if (constructor == null) throw new NoSuchMethodException("Item capability adapter constructor");
            constructor.setAccessible(true);
            return (IItemHandler) constructor.newInstance(port.nativeItemHandler(), canInsert, canExtract);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create the production item capability adapter", exception);
        }
    }
}
