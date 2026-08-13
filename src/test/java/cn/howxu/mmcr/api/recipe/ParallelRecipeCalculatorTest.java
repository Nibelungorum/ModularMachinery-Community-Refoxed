package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class ParallelRecipeCalculatorTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT, Items.OAK_LOG);
    }

    @Test
    void itemInputFastPathComputesSafeParallelUpperBound() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 11));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        MachineRecipe recipe = inputRecipe("parallel_fast_path", Items.IRON_INGOT, 3);

        assertThat(context.maxInputParallelism(recipe, 8)).isEqualTo(3);
        assertThat(ParallelRecipeCalculator.maxStartableParallelism(context, recipe, 8)).isEqualTo(3);
    }

    @Test
    void tagItemInputFallsBackWithoutDereferencingDuringParallelCalculation() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.OAK_LOG, 11));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("parallel_tag_input"),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT,
                        Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.LOGS)), 3, ItemStack.EMPTY)),
                true);

        assertThat(context.maxInputParallelism(recipe, 8)).isEqualTo(-1);
        assertThat(ParallelRecipeCalculator.maxStartableParallelism(context, recipe, 8)).isZero();
    }

    private static MachineRecipe inputRecipe(String path, Item item, int count) {
        return new MachineRecipe(
                MMCR.id(path),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY)),
                true);
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) unsafe.allocateInstance(ItemInputBusBlockEntity.class);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
        setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
        return bus;
    }

    private static MachineControllerBlockEntity controllerWithComponents(BlockEntity... ports) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        var level = LevelStub.createWithBlockEntities(List.of(ports));
        setField(BlockEntity.class, controller, "level", level);
        setField(MachineControllerBlockEntity.class, controller, "components", new ArrayList<ProcessingComponent>());
        for (BlockEntity port : ports) {
            setField(BlockEntity.class, port, "level", level);
            @SuppressWarnings("unchecked")
            List<ProcessingComponent> components = (List<ProcessingComponent>) fieldValue(MachineControllerBlockEntity.class, controller, "components");
            components.add(new ProcessingComponent(
                    new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT),
                    port,
                    port.getBlockPos(),
                    BlockPos.ZERO,
                    (String) null));
        }
        return controller;
    }

    private static void bindItemComponents(Item... items) {
        for (Item item : items) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        }
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object fieldValue(Class<?> declaringClass, Object target, String name) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
