package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeSearchTaskTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
        bindItemComponents(Items.NETHERITE_SCRAP);
    }

    @Test
    void computeReturnsFirstStartableRecipeAndKeepsContextForActiveRecipe() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe blocked = inputRecipe("blocked", machineId, Items.GOLD_INGOT, 1);
        MachineRecipe startable = inputRecipe("startable", machineId, Items.IRON_INGOT, 2);

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 11, 1, List.of(blocked, startable), pool).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getRecipe()).isEqualTo(startable);
        assertThat(result.context()).isNotNull();
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void computeKeepsHighestValidityFailureWithStableTieBreak() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe noMatchingComponent = inputRecipe("gold", machineId, Items.GOLD_INGOT, 1);
        MachineRecipe closerFailure = inputRecipe("iron", machineId, Items.IRON_INGOT, 3);

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 12, 1, List.of(noMatchingComponent, closerFailure), pool).compute();

        assertThat(result.success()).isFalse();
        assertThat(result.failureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(result.requirementFailure()).satisfies(failure -> {
            assertThat(failure.required()).isEqualTo(3);
            assertThat(failure.available()).isEqualTo(1);
        });
    }

    @Test
    void computePrefersMoreSpecificStartableRecipeWhenPriorityMatches() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.GOLD_INGOT.getDefaultInstance());
        bus.getItemStackHandler(null).setStackInSlot(1, Items.NETHERITE_SCRAP.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe single = inputRecipe("aaa_single_gold", machineId, Items.GOLD_INGOT, 1);
        MachineRecipe multi = inputRecipe("zzz_gold_scrap", machineId,
                List.of(itemInput(Items.GOLD_INGOT, 1), itemInput(Items.NETHERITE_SCRAP, 1)));

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 13, 1, List.of(single, multi), pool).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getRecipe()).isEqualTo(multi);
        assertThat(result.hasMoreSpecificPendingInputCandidate()).isFalse();
    }

    @Test
    void computeMarksSingleInputRecipeAsConflictProneWhenMoreSpecificOverlapIsMissingInput() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.GOLD_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe single = inputRecipe("single_gold", machineId, Items.GOLD_INGOT, 1);
        MachineRecipe multi = inputRecipe("gold_scrap", machineId,
                List.of(itemInput(Items.GOLD_INGOT, 1), itemInput(Items.NETHERITE_SCRAP, 1)));

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 14, 1, List.of(single, multi), pool).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getRecipe()).isEqualTo(single);
        assertThat(result.hasMoreSpecificPendingInputCandidate()).isTrue();
    }

    @Test
    void computeChoosesHighestFeasibleParallelizedTier() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe recipe = inputRecipe("parallelized_iron", machineId, List.of(itemInput(Items.IRON_INGOT, 2)), true);

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 15, 16, List.of(recipe), pool).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getMaxParallelism()).isEqualTo(16);
        assertThat(result.activeRecipe().getParallelism()).isEqualTo(4);
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(8);
    }

    @Test
    void computeIncludesExactNonTierMaxParallelismCandidate() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(6));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe recipe = inputRecipe("parallelized_iron_exact", machineId, List.of(itemInput(Items.IRON_INGOT, 2)), true);

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 16, 3, List.of(recipe), pool).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getMaxParallelism()).isEqualTo(3);
        assertThat(result.activeRecipe().getParallelism()).isEqualTo(3);
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(6);
    }

    private static MachineRecipe inputRecipe(String path, Identifier machineId, Item item, int count) {
        return inputRecipe(path, machineId, List.of(itemInput(item, count)));
    }

    private static MachineRecipe inputRecipe(String path, Identifier machineId, List<ItemRequirement> inputs) {
        return inputRecipe(path, machineId, inputs, false);
    }

    private static MachineRecipe inputRecipe(String path, Identifier machineId, List<ItemRequirement> inputs, boolean parallelized) {
        return new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", path),
                machineId,
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.copyOf(inputs),
                parallelized);
    }

    private static ItemRequirement itemInput(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) unsafe.allocateInstance(ItemInputBusBlockEntity.class);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        setField(cn.howxu.mmcr.internal.tile.ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
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

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
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
