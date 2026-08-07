package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
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

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerBlockEntityRecipeDelayTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.GOLD_INGOT);
        bindItemComponents(Items.NETHERITE_SCRAP);
    }

    @Test
    void conflictProneSingleInputRecipeDoesNotStartUntilDelayElapses() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.GOLD_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe single = inputRecipe("single_gold", machineId, Items.GOLD_INGOT, 1);
        MachineRecipe multi = inputRecipe("gold_scrap", machineId,
                List.of(itemInput(Items.GOLD_INGOT, 1), itemInput(Items.NETHERITE_SCRAP, 1)));

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 21, 1, List.of(single, multi), pool).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getRecipe()).isEqualTo(single);
        assertThat(result.hasMoreSpecificPendingInputCandidate()).isTrue();

        boolean startedBeforeDelay = invokeShouldDelay(controller, result, 0);
        boolean stillDelayed = invokeShouldDelay(controller, result, 19);
        boolean mayStartAfterDelay = invokeShouldDelay(controller, result, 20);

        assertThat(startedBeforeDelay).isTrue();
        assertThat(stillDelayed).isTrue();
        assertThat(mayStartAfterDelay).isFalse();
    }

    @Test
    void nonConflictRecipeStartsImmediately() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.GOLD_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        MachineRecipe single = inputRecipe("single_gold", machineId, Items.GOLD_INGOT, 1);

        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 22, 1, List.of(single), pool).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.hasMoreSpecificPendingInputCandidate()).isFalse();
        assertThat(invokeShouldDelay(controller, result, 0)).isFalse();
    }

    private static boolean invokeShouldDelay(MachineControllerBlockEntity controller,
                                             RecipeSearchResult result,
                                             long gameTime) throws Exception {
        var method = MachineControllerBlockEntity.class.getDeclaredMethod("shouldDelayConflictProneStart", RecipeSearchResult.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, result, gameTime);
    }

    private static MachineRecipe inputRecipe(String path, Identifier machineId, Item item, int count) {
        return inputRecipe(path, machineId, List.of(itemInput(item, count)));
    }

    private static MachineRecipe inputRecipe(String path, Identifier machineId, List<ItemRequirement> inputs) {
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
                List.copyOf(inputs));
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
