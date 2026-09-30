package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.ItemDefinition;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.items.misc.MissingContentItem;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternInterfaceCraftingMachine;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the AE2 crafting-machine bridge for MMCR pattern interfaces.
 *
 * @author howxu <dev@howxu.cn>
 */
class PatternInterfaceCraftingMachineTest {
    private static final BlockPos PATTERN_PORT = new BlockPos(3, 0, 0);

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        AE2TestFixtures.ensureAE2KeyTypesInitialized();
        bindMissingContentItem();
        bindTestPatternInterfaceEntityType();
        bindTestPatternInterfaceBlock();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void rejects_unlinked_and_output_incompatible_patterns_without_taking_requested_material() {
        PatternInterfaceBlockEntity host = patternHost();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        formForPattern(controller, false);
        RecipeRegistry.registerStatic(recipe("bridge_reject", Items.IRON_NUGGET, 2));
        KeyCounter request = counter(Items.IRON_INGOT, 5L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(
                processingPattern(Items.IRON_INGOT, Items.GOLD_NUGGET, 2), new KeyCounter[]{request}, null)).isFalse();

        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
        assertThat(host.getLogic().getReturnInv().isEmpty()).isTrue();
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);

        host.linkControllerAppearance(controller.getBlockPos(), null);
        formForPattern(controller, true);
        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(
                processingPattern(Items.IRON_INGOT, Items.GOLD_NUGGET, 2), new KeyCounter[]{request}, null)).isFalse();
        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
        assertThat(host.getLogic().getReturnInv().isEmpty()).isTrue();
    }

    @Test
    void commits_recipe_used_request_material_and_returns_the_exact_excess() {
        PatternInterfaceBlockEntity host = patternHost();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        host.linkControllerAppearance(controller.getBlockPos(), null);
        formForPattern(controller, true);
        MachineRecipe recipe = recipe("bridge_accept", Items.IRON_NUGGET, 2);
        RecipeRegistry.registerStatic(recipe);
        KeyCounter request = counter(Items.IRON_INGOT, 5L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(
                processingPattern(Items.IRON_INGOT, Items.IRON_NUGGET, 2), new KeyCounter[]{request}, null)).isTrue();

        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isZero();
        assertThat(host.getLogic().getReturnInv().getStack(0))
                .isEqualTo(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3L));
    }

    @Test
    void partitions_one_ae2_input_counter_across_linked_controller_batch() {
        PatternInterfaceBlockEntity host = patternHost();
        List<MachineControllerBlockEntity> controllers = linkedControllers(host);
        MachineControllerBlockEntity first = controllers.get(0);
        MachineControllerBlockEntity second = controllers.get(1);
        host.linkControllerAppearance(first.getBlockPos(), null);
        host.linkControllerAppearance(second.getBlockPos(), null);
        formForPattern(first, true);
        formForPattern(second, true);
        MachineRecipe recipe = recipe("bridge_batch", Items.IRON_NUGGET, 2);
        RecipeRegistry.registerStatic(recipe);
        KeyCounter request = counter(Items.IRON_INGOT, 10L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushBatchPattern(
                processingPattern(Items.IRON_INGOT, Items.IRON_NUGGET, 2), new KeyCounter[]{request}, 2L, null)).isTrue();

        assertThat(first.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(second.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isZero();
        assertThat(host.getLogic().getReturnInv().getStack(0))
                .isEqualTo(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 6L));
    }

    @Test
    void rejects_non_processing_patterns_without_taking_requested_material() {
        PatternInterfaceBlockEntity host = patternHost();
        KeyCounter request = counter(Items.IRON_INGOT, 5L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                return AEItemKey.of(Items.IRON_INGOT);
            }

            @Override
            public IInput[] getInputs() {
                return new IInput[0];
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of();
            }
        }, new KeyCounter[]{request}, null)).isFalse();

        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
        assertThat(host.getLogic().getReturnInv().isEmpty()).isTrue();
    }

    @Test
    void rejects_unknown_patterns_without_taking_requested_material() {
        PatternInterfaceBlockEntity host = patternHost();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        host.linkControllerAppearance(controller.getBlockPos(), null);
        formForPattern(controller, true);
        KeyCounter request = counter(Items.IRON_INGOT, 5L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(
                processingPattern(Items.IRON_INGOT, Items.IRON_NUGGET, 2), new KeyCounter[]{request}, null)).isFalse();

        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
        assertThat(host.getLogic().getReturnInv().isEmpty()).isTrue();
    }

    @Test
    void rejects_unformed_linked_controllers_without_taking_requested_material() {
        PatternInterfaceBlockEntity host = patternHost();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        host.linkControllerAppearance(controller.getBlockPos(), null);
        KeyCounter request = counter(Items.IRON_INGOT, 5L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(
                processingPattern(Items.IRON_INGOT, Items.IRON_NUGGET, 2), new KeyCounter[]{request}, null)).isFalse();

        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
        assertThat(host.getLogic().getReturnInv().isEmpty()).isTrue();
    }

    @Test
    void rejects_when_the_only_normal_controller_is_already_active() {
        PatternInterfaceBlockEntity host = patternHost();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        host.linkControllerAppearance(controller.getBlockPos(), null);
        formForPattern(controller, true);
        RecipeRegistry.registerStatic(recipe("bridge_busy", Items.IRON_NUGGET, 2));
        PatternInterfaceCraftingMachine machine = new PatternInterfaceCraftingMachine(host);

        assertThat(machine.pushPattern(processingPattern(Items.IRON_INGOT, Items.IRON_NUGGET, 2),
                new KeyCounter[]{counter(Items.IRON_INGOT, 5L)}, null)).isTrue();
        KeyCounter rejected = counter(Items.IRON_INGOT, 5L);
        assertThat(machine.pushPattern(processingPattern(Items.IRON_INGOT, Items.IRON_NUGGET, 2),
                new KeyCounter[]{rejected}, null)).isFalse();

        assertThat(rejected.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
    }

    @Test
    void rejects_insufficient_native_return_capacity_without_taking_requested_material() {
        PatternInterfaceBlockEntity host = patternHost();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        host.linkControllerAppearance(controller.getBlockPos(), null);
        formForPattern(controller, true);
        RecipeRegistry.registerStatic(recipe("bridge_return_capacity", Items.IRON_NUGGET, 2));
        for (int slot = 0; slot < host.getLogic().getReturnInv().size(); slot++) {
            host.getLogic().getReturnInv().setStack(slot,
                    new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 64L));
        }
        KeyCounter request = counter(Items.IRON_INGOT, 5L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(
                processingPattern(Items.IRON_INGOT, Items.IRON_NUGGET, 2), new KeyCounter[]{request}, null)).isFalse();

        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
        for (int slot = 0; slot < host.getLogic().getReturnInv().size(); slot++) {
            assertThat(host.getLogic().getReturnInv().getStack(slot))
                    .isEqualTo(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 64L));
        }
        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isNull();
    }

    @Test
    void preflights_cumulative_mixed_return_capacity_before_starting_recipe() {
        PatternInterfaceBlockEntity host = patternHost();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        host.linkControllerAppearance(controller.getBlockPos(), null);
        formForPattern(controller, true);
        RecipeRegistry.registerStatic(mixedRecipe("bridge_mixed_return_capacity"));
        for (int slot = 1; slot < host.getLogic().getReturnInv().size(); slot++) {
            host.getLogic().getReturnInv().setStack(slot,
                    new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 64L));
        }
        KeyCounter request = counter(Items.IRON_INGOT, 5L);
        request.add(AEFluidKey.of(Fluids.WATER), 1_000L);

        assertThat(new PatternInterfaceCraftingMachine(host).pushPattern(
                mixedProcessingPattern(), new KeyCounter[]{request}, null)).isFalse();

        assertThat(request.get(AEItemKey.of(Items.IRON_INGOT))).isEqualTo(5L);
        assertThat(request.get(AEFluidKey.of(Fluids.WATER))).isEqualTo(1_000L);
        assertThat(host.getLogic().getReturnInv().getStack(0)).isNull();
        for (int slot = 1; slot < host.getLogic().getReturnInv().size(); slot++) {
            assertThat(host.getLogic().getReturnInv().getStack(slot))
                    .isEqualTo(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 64L));
        }
        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isNull();
    }

    private static PatternInterfaceBlockEntity patternHost() {
        return PatternInterfaceKind.INSTANCE.entityFactory().create(PATTERN_PORT, Blocks.IRON_BLOCK.defaultBlockState());
    }

    private static List<MachineControllerBlockEntity> linkedControllers(PatternInterfaceBlockEntity host) {
        MachineControllerBlockEntity first = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        MachineControllerBlockEntity second = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), new BlockPos(1, 0, 0));
        Level level = LevelStub.create(Map.of(
                first.getBlockPos(), ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                second.getBlockPos(), ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                host.getBlockPos(), ModBlocks.BLOCKS.get(host.kind().id()).get()), List.of(first, second, host));
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "runtime test", new BlockArray(Map.of()));
        for (MachineControllerBlockEntity controller : List.of(first, second)) {
            controller.setLevel(level);
            controller.setMachine(machine);
            controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                    new MachineComponent(host.kind(), host.ioType()), host, host.getBlockPos(), host.getBlockPos(), (String) null)));
            controller.refreshModuleConnectionState();
        }
        host.setLevel(level);
        return List.of(first, second);
    }

    @SuppressWarnings("unchecked")
    private static void bindMissingContentItem() throws ReflectiveOperationException {
        MappedRegistry<Item> registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        registry.unfreeze();
        try {
            Item item = registry.get(AEItems.MISSING_CONTENT.id());
            if (item == null) {
                item = new MissingContentItem(new Item.Properties());
                Registry.register(registry, AEItems.MISSING_CONTENT.id(), item);
            }
            Field itemField = ItemDefinition.class.getDeclaredField("item");
            itemField.setAccessible(true);
            DeferredItem<?> deferredItem = (DeferredItem<?>) itemField.get(AEItems.MISSING_CONTENT);
            Field holder = DeferredHolder.class.getDeclaredField("holder");
            holder.setAccessible(true);
            holder.set(deferredItem, Holder.direct(item));
        } finally {
            registry.freeze();
        }
    }

    private static void bindTestPatternInterfaceEntityType() {
        AE2TestFixtures.bindEntityType(PatternInterfaceKind.INSTANCE.id(), PatternInterfaceKind.INSTANCE.entityFactory());
    }

    @SuppressWarnings("unchecked")
    private static void bindTestPatternInterfaceBlock() {
        ResourceLocation id = MMCR.id(PatternInterfaceKind.INSTANCE.id());
        MappedRegistry<Block> registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id, new IOPortBlock(PatternInterfaceKind.INSTANCE,
                        () -> ModBlockEntities.BES.get(PatternInterfaceKind.INSTANCE.id()).get(),
                        Blocks.IRON_BLOCK.properties()));
            }
        } finally {
            registry.freeze();
        }
        ModBlocks.BLOCKS.put(PatternInterfaceKind.INSTANCE.id(), DeferredHolder.create(Registries.BLOCK, id));
    }

    private static void formForPattern(MachineControllerBlockEntity controller, boolean linked) {
        controller.setFormed(true);
        controller.componentRuntime().replaceLinkedPortPositions(linked ? Set.of(PATTERN_PORT) : Set.of());
        RuntimeTestFixtures.republish(controller);
    }

    private static MachineRecipe recipe(String path, Item output, int amount) {
        MachineOutput machineOutput = new MachineOutput.ItemOutput(stack(output, amount), 1F);
        return MachineRecipe.fromCanonical(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                        ItemStack.EMPTY), OutputRegistry.tryToRequirement(machineOutput, List.of())),
                List.of(machineOutput), List.of(), 0, 1, false, false, false, Set.of());
    }

    private static MachineRecipe mixedRecipe(String path) {
        MachineOutput output = new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 2), 1F);
        return MachineRecipe.fromCanonical(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                                ItemStack.EMPTY),
                        OutputRegistry.tryToRequirement(output, List.of())),
                List.of(output), List.of(), 0, 1, false, false, false, Set.of());
    }

    private static AEProcessingPattern processingPattern(Item input, Item output, int amount) {
        ItemStack definition = new ItemStack(Items.PAPER);
        AEProcessingPattern.encode(definition,
                List.of(new GenericStack(AEItemKey.of(input), 1L)),
                List.of(new GenericStack(AEItemKey.of(output), amount)));
        return new AEProcessingPattern(AEItemKey.of(definition));
    }

    private static AEProcessingPattern mixedProcessingPattern() {
        ItemStack definition = new ItemStack(Items.PAPER);
        AEProcessingPattern.encode(definition,
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L),
                        new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 2L)));
        return new AEProcessingPattern(AEItemKey.of(definition));
    }

    private static KeyCounter counter(Item item, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(AEItemKey.of(item), amount);
        return counter;
    }

    private static ItemStack stack(Item item, int amount) {
        ItemStack stack = new ItemStack(item, amount);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

}
