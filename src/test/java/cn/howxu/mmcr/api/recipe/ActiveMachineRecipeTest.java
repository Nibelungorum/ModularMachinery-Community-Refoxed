package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveMachineRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT);
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void setParallelismClampsToActiveMaximumAndPersistsClampedValue() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("active_parallel_clamp"),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of());
        RecipeRegistry.register(recipe);

        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);
        active.setParallelism(64);
        assertThat(active.getParallelism()).isEqualTo(16);
        active.setMaxParallelism(4);
        assertThat(active.getParallelism()).isEqualTo(4);
        active.setMaxParallelism(16);
        active.setParallelism(0);
        assertThat(active.getParallelism()).isEqualTo(1);

        active.setParallelism(64);
        ActiveMachineRecipe fromNbt = new ActiveMachineRecipe(active.serialize());
        assertThat(fromNbt.getMaxParallelism()).isEqualTo(16);
        assertThat(fromNbt.getParallelism()).isEqualTo(16);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        active.serialize(output);
        CompoundTag tag = output.buildResult();
        ActiveMachineRecipe fromValueInput = ActiveMachineRecipe.from(TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()), tag));

        assertThat(fromValueInput.getMaxParallelism()).isEqualTo(16);
        assertThat(fromValueInput.getParallelism()).isEqualTo(16);
    }

    @Test
    void startPromotesParallelismToHighestFeasibleCraftAmount() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        MachineRecipe recipe = inputRecipe("active_parallel_start", MMCR.id("blast_furnace"), Items.IRON_INGOT, 2);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);

        boolean started = active.start(new RecipeCraftingContext(controller));

        assertThat(started).isTrue();
        assertThat(active.getParallelism()).isEqualTo(4);
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
    }

    private static MachineRecipe inputRecipe(String path, net.minecraft.resources.Identifier machineId, Item item, int count) {
        return new MachineRecipe(
                MMCR.id(path),
                machineId,
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
