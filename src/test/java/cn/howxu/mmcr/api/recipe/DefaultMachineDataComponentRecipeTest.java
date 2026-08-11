package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMachineDataComponentRecipeTest {

    private static final List<Identifier> DEFAULT_MACHINES = List.of(
            Identifier.parse("mmcr:blast_furnace"),
            Identifier.parse("mmcr:alloy_furnace"),
            Identifier.parse("mmcr:cracker"),
            Identifier.parse("mmcr:reactor"),
            Identifier.parse("mmcr:thermal_smelting_furnace")
    );

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_INGOT, Items.EMERALD, Items.REDSTONE);
    }

    static Stream<Arguments> defaultMachineScenarios() {
        return DEFAULT_MACHINES.stream().flatMap(machineId -> Stream.of(Scenario.values())
                .map(scenario -> Arguments.of(machineId, scenario)));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("defaultMachineScenarios")
    void executesDataComponentRecipeThroughSearchAndCrafting(Identifier machineId, Scenario scenario) throws Exception {
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(input, output);
        Identifier recipeId = Identifier.fromNamespaceAndPath("mmcr", "component_" + machineId.getPath() + "_" + scenario.name().toLowerCase());
        MachineRecipe recipe = scenario.recipe(machineId, recipeId);
        scenario.installInputs(input.getItemStackHandler(null));
        RecipeRegistry.register(recipe);

        RecipeCandidateIndex index = RecipeCandidateIndex.build(List.of(recipe));
        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 1L, 1,
                index.allCandidates(), new RecipeCraftingContextPool(), index).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getRecipe()).isEqualTo(recipe);
        ActiveMachineRecipe active = result.activeRecipe();
        RecipeCraftingContext context = result.context();

        assertThat(active.start(context)).isTrue();
        scenario.assertInputsAfterStart(input.getItemStackHandler(null), active.inputConsumptionPlan());
<<<<<<< HEAD
        assertThat(active.tick(context)).isEqualTo(ActiveMachineRecipe.TickStatus.FINISHED);
=======
        assertThat(context.commitSynchronousOutputs(active.getRecipe(), active.getParallelism())).isTrue();
        assertThat(active.applyTickGrant(true, true, 100)).isEqualTo(ActiveMachineRecipe.TickStatus.FINISHED);
>>>>>>> feat/shared-multiblock-io
        scenario.assertOutputs(output.getItemStackHandler(null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defaultMachines")
    void candidateIndexStillSelectsComponentMatchingRecipe(Identifier machineId) throws Exception {
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, named(Items.DIAMOND, "selected"));
        MachineControllerBlockEntity controller = controllerWithComponents(input, output);
        MachineRecipe rejected = recipe(machineId, "component_index_rejected_" + machineId.getPath(),
                List.of(componentInput(Items.DIAMOND, "rejected", 1F)), List.of(new ItemStack(Items.REDSTONE)));
        MachineRecipe selected = recipe(machineId, "component_index_selected_" + machineId.getPath(),
                List.of(componentInput(Items.DIAMOND, "selected", 1F)), List.of(new ItemStack(Items.EMERALD)));

        RecipeCandidateIndex index = RecipeCandidateIndex.build(List.of(rejected, selected));
        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 1L, 1,
                index.allCandidates(), new RecipeCraftingContextPool(), index).compute();

        assertThat(result.success()).isTrue();
        assertThat(result.activeRecipe().getRecipe()).isEqualTo(selected);
    }

    static Stream<Identifier> defaultMachines() {
        return DEFAULT_MACHINES.stream();
    }

    private enum Scenario {
        CHANCED_COMPONENT_INPUT {
            @Override
            MachineRecipe recipe(Identifier machineId, Identifier recipeId) {
                return DefaultMachineDataComponentRecipeTest.recipe(machineId, recipeId.getPath(), List.of(componentInput(Items.DIAMOND, "chance", 0.5F)), List.of(new ItemStack(Items.EMERALD)));
            }

            @Override
            void installInputs(ItemStackHandler input) {
                input.setStackInSlot(0, named(Items.DIAMOND, "chance"));
            }

            @Override
            void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan) {
                assertThat(plan.consumedBatches(0)).isBetween(0, 1);
                assertThat(input.getStackInSlot(0).getCount()).isEqualTo(1 - plan.consumedBatches(0));
            }

            @Override
            void assertOutputs(ItemStackHandler output) {
                assertPlain(output.getStackInSlot(0), Items.EMERALD);
            }
        },
        NON_CONSUMABLE_COMPONENT_INPUT {
            @Override
            MachineRecipe recipe(Identifier machineId, Identifier recipeId) {
                return DefaultMachineDataComponentRecipeTest.recipe(machineId, recipeId.getPath(), List.of(componentInput(Items.DIAMOND, "keep", 0F)), List.of(new ItemStack(Items.EMERALD)));
            }

            @Override
            void installInputs(ItemStackHandler input) {
                input.setStackInSlot(0, named(Items.DIAMOND, "keep"));
            }

            @Override
            void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan) {
                assertThat(plan.consumedBatches(0)).isZero();
                assertNamed(input.getStackInSlot(0), Items.DIAMOND, "keep");
            }

            @Override
            void assertOutputs(ItemStackHandler output) {
                assertPlain(output.getStackInSlot(0), Items.EMERALD);
            }
        },
        COMPONENT_INPUT_TO_PLAIN_OUTPUT {
            @Override
            MachineRecipe recipe(Identifier machineId, Identifier recipeId) {
                return DefaultMachineDataComponentRecipeTest.recipe(machineId, recipeId.getPath(), List.of(componentInput(Items.DIAMOND, "input-only", 1F)), List.of(new ItemStack(Items.EMERALD)));
            }

            @Override
            void installInputs(ItemStackHandler input) {
                input.setStackInSlot(0, named(Items.DIAMOND, "input-only"));
            }

            @Override
            void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan) {
                assertThat(input.getStackInSlot(0).isEmpty()).isTrue();
            }

            @Override
            void assertOutputs(ItemStackHandler output) {
                assertPlain(output.getStackInSlot(0), Items.EMERALD);
            }
        },
        PLAIN_INPUT_TO_COMPONENT_OUTPUT {
            @Override
            MachineRecipe recipe(Identifier machineId, Identifier recipeId) {
                return DefaultMachineDataComponentRecipeTest.recipe(machineId, recipeId.getPath(), List.of(plainInput(Items.IRON_INGOT)), List.of(named(Items.GOLD_INGOT, "output-only")));
            }

            @Override
            void installInputs(ItemStackHandler input) {
                input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            }

            @Override
            void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan) {
                assertThat(input.getStackInSlot(0).isEmpty()).isTrue();
            }

            @Override
            void assertOutputs(ItemStackHandler output) {
                assertNamed(output.getStackInSlot(0), Items.GOLD_INGOT, "output-only");
            }
        },
        COMPONENT_INPUT_TO_COMPONENT_OUTPUT {
            @Override
            MachineRecipe recipe(Identifier machineId, Identifier recipeId) {
                return DefaultMachineDataComponentRecipeTest.recipe(machineId, recipeId.getPath(), List.of(componentInput(Items.DIAMOND, "input", 1F)), List.of(named(Items.GOLD_INGOT, "output")));
            }

            @Override
            void installInputs(ItemStackHandler input) {
                input.setStackInSlot(0, named(Items.DIAMOND, "input"));
            }

            @Override
            void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan) {
                assertThat(input.getStackInSlot(0).isEmpty()).isTrue();
            }

            @Override
            void assertOutputs(ItemStackHandler output) {
                assertNamed(output.getStackInSlot(0), Items.GOLD_INGOT, "output");
            }
        },
        MIXED_COMPONENT_INPUTS {
            @Override
            MachineRecipe recipe(Identifier machineId, Identifier recipeId) {
                return DefaultMachineDataComponentRecipeTest.recipe(machineId, recipeId.getPath(), List.of(componentInput(Items.DIAMOND, "named", 1F), plainInput(Items.IRON_INGOT)), List.of(new ItemStack(Items.EMERALD)));
            }

            @Override
            void installInputs(ItemStackHandler input) {
                input.setStackInSlot(0, named(Items.DIAMOND, "named"));
                input.setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
            }

            @Override
            void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan) {
                assertThat(input.getStackInSlot(0).isEmpty()).isTrue();
                assertThat(input.getStackInSlot(1).isEmpty()).isTrue();
            }

            @Override
            void assertOutputs(ItemStackHandler output) {
                assertPlain(output.getStackInSlot(0), Items.EMERALD);
            }
        },
        MIXED_COMPONENT_OUTPUTS {
            @Override
            MachineRecipe recipe(Identifier machineId, Identifier recipeId) {
                return DefaultMachineDataComponentRecipeTest.recipe(machineId, recipeId.getPath(), List.of(plainInput(Items.IRON_INGOT)), List.of(named(Items.GOLD_INGOT, "named-output"), new ItemStack(Items.EMERALD)));
            }

            @Override
            void installInputs(ItemStackHandler input) {
                input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            }

            @Override
            void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan) {
                assertThat(input.getStackInSlot(0).isEmpty()).isTrue();
            }

            @Override
            void assertOutputs(ItemStackHandler output) {
                assertNamed(output.getStackInSlot(0), Items.GOLD_INGOT, "named-output");
                assertPlain(output.getStackInSlot(1), Items.EMERALD);
            }
        };

        abstract MachineRecipe recipe(Identifier machineId, Identifier recipeId);

        abstract void installInputs(ItemStackHandler input);

        abstract void assertInputsAfterStart(ItemStackHandler input, ActiveMachineRecipe.InputConsumptionPlan plan);

        abstract void assertOutputs(ItemStackHandler output);
    }

    private static MachineRecipe recipe(Identifier machineId, String path, List<MachineIngredient> inputs, List<ItemStack> outputs) {
        return new MachineRecipe(Identifier.fromNamespaceAndPath("mmcr", path), machineId, 1, inputs, outputs);
    }

    private static MachineIngredient.ItemIngredient componentInput(Item item, String name, float consumeChance) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(item), 1, namedPredicate(name), consumeChance);
    }

    private static MachineIngredient.ItemIngredient plainInput(Item item) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(item), 1);
    }

    private static DataComponentPredicateSet namedPredicate(String name) {
        return DataComponentPredicateSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"minecraft:custom_name":{"type":"text","value":{"text":"%s"},"mode":"plain"}}
                """.formatted(name))).getOrThrow();
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static void assertNamed(ItemStack stack, Item item, String name) {
        assertThat(stack.is(item)).isTrue();
        assertThat(stack.get(DataComponents.CUSTOM_NAME)).isEqualTo(Component.literal(name));
    }

    private static void assertPlain(ItemStack stack, Item item) {
        assertThat(stack.is(item)).isTrue();
        assertThat(stack.get(DataComponents.CUSTOM_NAME)).isNull();
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) {
        return allocateItemBus(ItemInputBusBlockEntity.class, pos);
    }

    private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) {
        return allocateItemBus(ItemOutputBusBlockEntity.class, pos);
    }

    private static void bindItemComponents(Item... items) {
        for (Item item : items) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        }
    }

    @SuppressWarnings({"removal", "unchecked"})
    private static <T extends BlockEntity> T allocateItemBus(Class<T> type, BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            T bus = (T) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(type);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", pos);
            setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
            return bus;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate item bus", e);
        }
    }

    @SuppressWarnings({"removal", "unchecked"})
    private static MachineControllerBlockEntity controllerWithComponents(BlockEntity... ports) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            MachineControllerBlockEntity controller = (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(MachineControllerBlockEntity.class);
            var level = LevelStub.createWithBlockEntities(List.of(ports));
            setField(BlockEntity.class, controller, "level", level);
            setField(MachineControllerBlockEntity.class, controller, "components", new ArrayList<ProcessingComponent>());
            @SuppressWarnings("unchecked")
            List<ProcessingComponent> components = (List<ProcessingComponent>) fieldValue(MachineControllerBlockEntity.class, controller, "components");
            for (BlockEntity port : ports) {
                setField(BlockEntity.class, port, "level", level);
                components.add(new ProcessingComponent(componentFor(port), port, port.getBlockPos(), BlockPos.ZERO, (String) null));
            }
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate machine controller", e);
        }
    }

    private static MachineComponent componentFor(BlockEntity port) {
        if (port instanceof ItemInputBusBlockEntity) return new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        if (port instanceof ItemOutputBusBlockEntity) return new MachineComponent(PortKinds.ITEM_OUTPUT, cn.howxu.mmcr.util.IOType.OUTPUT);
        throw new IllegalArgumentException("Unknown port " + port.getClass().getName());
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object fieldValue(Class<?> type, Object target, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
