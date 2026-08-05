package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    void simulateInputsAggregatesMatchingItemsAcrossMultipleInputBuses() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity first = itemInputBus(new BlockPos(1, 0, 0));
        ItemInputBusBlockEntity second = itemInputBus(new BlockPos(2, 0, 0));
        first.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(3));
        second.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        MachineControllerBlockEntity controller = controllerWithComponents(first, second);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "multi_bus_input"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 5)),
                List.of()
        );

        assertThat(new RecipeCraftingContext(controller).simulateInputs(recipe)).isTrue();
    }

    @Test
    void simulateInputsFailsWhenDuplicateIngredientsExceedCombinedInput() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(5));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "duplicate_input"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 5),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 5)
                ),
                List.of()
        );

        assertThat(new RecipeCraftingContext(controller).simulateInputs(recipe)).isFalse();
    }

    @Test
    void simulateOutputsReturnsFalseWhenOutputBusHasNoRoom() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "blocked_output"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(Items.IRON_INGOT.getDefaultInstance())
        );

        assertThat(new RecipeCraftingContext(controller).simulateOutputs(recipe)).isFalse();
    }

    @Test
    void simulateOutputsFailsWhenDuplicateOutputsExceedCombinedRoom() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "duplicate_output"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(
                        Items.IRON_INGOT.getDefaultInstance().copyWithCount(64),
                        Items.IRON_INGOT.getDefaultInstance()
                )
        );

        assertThat(new RecipeCraftingContext(controller).simulateOutputs(recipe)).isFalse();
    }

    @Test
    void legacyFindAndCheckHelpersStayRemoved() {
        assertThat(Arrays.stream(RecipeCraftingContext.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("findAndCheck")))
                .isEmpty();
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) {
        return allocateItemBus(ItemInputBusBlockEntity.class, pos);
    }

    private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) {
        return allocateItemBus(ItemOutputBusBlockEntity.class, pos);
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @SuppressWarnings({"removal", "unchecked"})
    private static <T extends BlockEntity> T allocateItemBus(Class<T> type, BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            T bus = (T) unsafe.allocateInstance(type);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", pos);
            setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(cn.howxu.mmcr.internal.tile.ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
            return bus;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate item bus for crafting context test", e);
        }
    }

    private static MachineControllerBlockEntity controllerWithComponents(net.minecraft.world.level.block.entity.BlockEntity... ports) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
            var level = LevelStub.createWithBlockEntities(List.of(ports));
            setBlockEntityLevel(controller, level);
            for (net.minecraft.world.level.block.entity.BlockEntity port : ports) {
                setBlockEntityLevel(port, level);
            }
            Field components = MachineControllerBlockEntity.class.getDeclaredField("components");
            components.setAccessible(true);
            components.set(controller, new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<ProcessingComponent> list = (List<ProcessingComponent>) components.get(controller);
            list.clear();
            for (net.minecraft.world.level.block.entity.BlockEntity port : ports) {
                var component = port instanceof ItemInputBusBlockEntity
                        ? new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT)
                        : new MachineComponent(PortKinds.ITEM_OUTPUT, cn.howxu.mmcr.util.IOType.OUTPUT);
                list.add(new ProcessingComponent(component, port, port.getBlockPos(), BlockPos.ZERO, null));
            }
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate MachineControllerBlockEntity for crafting context test", e);
        }
    }

    private static void setBlockEntityLevel(net.minecraft.world.level.block.entity.BlockEntity blockEntity, net.minecraft.world.level.Level level)
            throws ReflectiveOperationException {
        setField(net.minecraft.world.level.block.entity.BlockEntity.class, blockEntity, "level", level);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
