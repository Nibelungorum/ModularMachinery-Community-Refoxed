package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.recipe.RecipeStartDelay;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerBlockEntityRecipeDelayTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.GOLD_INGOT);
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.NETHERITE_SCRAP);
        bindItemComponents(Items.DIAMOND_SWORD);
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

    @Test
    void inputBusChangeClearsRecipeSearchRetryDelay() throws Exception {
        MachineControllerBlockEntity controller = formedController(Identifier.fromNamespaceAndPath("mmcr", "machine"));
        setField(MachineControllerBlockEntity.class, controller, "recipeSearchRetryCounter", 3);

        controller.onRecipeInputsChanged();

        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "recipeSearchRetryCounter")).isEqualTo(0);
    }

    @Test
    void repeatedConflictProneRecipeStartsImmediatelyAfterInitialDelayHasElapsed() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        MachineRecipe recipe = inputRecipe("single_gold", machineId, List.of());
        RecipeSearchResult firstResult = startableConflictResult(machineId, recipe, 31);
        MachineControllerBlockEntity controller = formedController(machineId);

        assertThat(invokeShouldDelay(controller, firstResult, 0)).isTrue();
        setField(MachineControllerBlockEntity.class, controller, "recipeSearchAttemptCounter", 20L);
        assertThat(invokeApplySearchResult(controller, firstResult, 1)).isTrue();
        setField(MachineControllerBlockEntity.class, controller, "active", null);
        setField(MachineControllerBlockEntity.class, controller, "context", null);

        RecipeSearchResult secondResult = startableConflictResult(machineId, recipe, 31);

        assertThat(invokeShouldDelay(controller, secondResult, 21)).isFalse();
    }

    @Test
    void restartedParallelRecipeRecalculatesParallelism() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 16));
        MachineControllerBlockEntity controller = formedController(machineId, bus);
        DynamicMachine machine = new DynamicMachine(
                machineId, "Parallel Machine", new BlockArray(java.util.Map.of()),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(),
                List.of(), java.util.Map.of(), 16, true, false, 1);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        setField(MachineControllerBlockEntity.class, controller, "foundMachine", machine);
        addParallelComponent(controller, ParallelTier.PLUS);
        MachineRecipe recipe = parallelizedInputRecipe("parallel_gold", machineId, Items.GOLD_INGOT, 1);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipe", recipe);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeStructureVersion", 31L);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeModifierSnapshotVersion", 0L);
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);

        assertThat(controller.getMaxParallelism()).isEqualTo(16);
        assertThat(new ActiveMachineRecipe(recipe, controller.getMaxParallelism())
                .canStartCrafting(new RecipeCraftingContext(controller))).isTrue();
        assertThat(invokeTryRestartLastRecipe(controller, machineId)).isTrue();
        assertThat(controller.getActive()).isNotNull();
        assertThat(controller.getActive().getParallelism()).isEqualTo(16);
    }

    @Test
    void completedRecipeRestartsImmediatelyWithoutTickingReplacementUntilNextTick() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 2));
        MachineControllerBlockEntity controller = formedController(machineId, bus);
        MachineRecipe recipe = inputRecipe("continuous_gold", machineId, Items.GOLD_INGOT, 1);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipe", recipe);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeStructureVersion", 31L);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeModifierSnapshotVersion", 0L);
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);
        assertThat(invokeTryRestartLastRecipe(controller, machineId)).isTrue();
        ((RecipeCraftingContext) fieldValue(MachineControllerBlockEntity.class, controller, "context")).refreshStructureVersion();
        setField(ActiveMachineRecipe.class, controller.getActive(), "totalTick", 1);
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);
        setField(MachineControllerBlockEntity.class, controller, "recipeSearchRetryCounter", 0);

        invokeTickSingleActiveRecipe(controller);

        assertThat(controller.getActive()).isNotNull();
        assertThat(controller.getActive().getRecipe()).isSameAs(recipe);
        assertThat(controller.getActive().getTick()).isZero();
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();

        invokeTickSingleActiveRecipe(controller);

        assertThat(controller.getActive().getTick()).isEqualTo(1);
    }

    @Test
    void sharedControllerInstallsFinishedRecipeReplacementBeforeTheNextSharedTick() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "shared_continuation");
        BlockPos firstControllerPos = BlockPos.ZERO;
        BlockPos secondControllerPos = new BlockPos(4, 0, 0);
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 2));
        MachineControllerBlockEntity controller = formedController(machineId, bus);
        MachineControllerBlockEntity peer = formedController(machineId, bus);
        setField(BlockEntity.class, peer, "worldPosition", secondControllerPos);
        ServerLevel level = serverLevel(List.of(controller, peer, bus));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, peer, "level", level);
        setField(BlockEntity.class, bus, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(firstControllerPos, List.of(new StructureClaimRegistry.Claim(bus.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)));
        registry.claim(secondControllerPos, List.of(new StructureClaimRegistry.Claim(bus.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(firstControllerPos);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "shared_continuous_gold"),
                machineId,
                2,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(itemInput(Items.GOLD_INGOT, 1)));
        setField(MachineControllerBlockEntity.class, controller, "lastRecipe", recipe);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeStructureVersion", 31L);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeModifierSnapshotVersion", 0L);
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);

        invokeTickSingleActiveRecipe(controller);
        SharedIoCoordinator.get(level).resolve(domain);
        assertThat(controller.getActive()).isNotNull();
        assertThat(controller.getActive().getTick()).isZero();
        controller.getActive().setTick(1);

        invokeTickSingleActiveRecipe(controller);
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(controller.getActive()).isNotNull();
        assertThat(controller.getActive().getRecipe()).isSameAs(recipe);
        assertThat(controller.getActive().getTick()).isZero();
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();

        invokeTickSingleActiveRecipe(controller);
        assertThat(controller.getActive().getTick()).isZero();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(controller.getActive().getTick()).isEqualTo(1);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void completedRecipeDoesNotReplaceActiveRecipeWhenRestartInputIsUnavailable() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.GOLD_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = formedController(machineId, bus);
        MachineRecipe recipe = inputRecipe("continuous_gold", machineId, Items.GOLD_INGOT, 1);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipe", recipe);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeStructureVersion", 31L);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeModifierSnapshotVersion", 0L);
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);
        assertThat(invokeTryRestartLastRecipe(controller, machineId)).isTrue();
        ((RecipeCraftingContext) fieldValue(MachineControllerBlockEntity.class, controller, "context")).refreshStructureVersion();
        setField(ActiveMachineRecipe.class, controller.getActive(), "totalTick", 1);
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);

        invokeTickSingleActiveRecipe(controller);

        assertThat(controller.getActive()).isNull();
    }

    @Test
    void blockedFinalOutputKeepsTheOldRecipeWaitingWithoutRestarting() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        setField(ItemBusBlockEntity.class, output, "handler", new ItemStackHandler(6));
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        MachineControllerBlockEntity controller = formedController(machineId, output);
        MachineRecipe recipe = outputRecipe("blocked_continuous_gold", machineId, Items.GOLD_INGOT);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipe", recipe);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeStructureVersion", 31L);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeModifierSnapshotVersion", 0L);
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        context.refreshStructureVersion();
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", context);
        setField(ActiveMachineRecipe.class, active, "totalTick", 1);

        invokeTickSingleActiveRecipe(controller);

        assertThat(controller.getActive()).isSameAs(active);
        assertThat(active.isFinishPending()).isTrue();
        assertThat(active.getTick()).isZero();

        invokeTickSingleActiveRecipe(controller);

        assertThat(controller.getActive()).isSameAs(active);
        assertThat(active.isFinishPending()).isTrue();
        assertThat(active.getTick()).isZero();
    }

    @Test
    void enchantedInputRecipeRejectsWrongRuntimeEnchantments() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, enchantedSword("minecraft:sharpness", 1));
        MachineControllerBlockEntity controller = formedController(machineId, bus);
        MachineRecipe recipe = inputRecipe("sharpness_two", machineId, List.of(enchantedInput(2)));

        assertThat(new ActiveMachineRecipe(recipe).canStartCrafting(new RecipeCraftingContext(controller))).isFalse();

        bus.getItemStackHandler(null).setStackInSlot(0, enchantedSword("minecraft:unbreaking", 3));

        assertThat(new ActiveMachineRecipe(recipe).canStartCrafting(new RecipeCraftingContext(controller))).isFalse();
    }

    @Test
    void enchantedInputRecipeAcceptsSharpnessWithoutRepairCost() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, enchantedSword("minecraft:sharpness", 2));
        MachineControllerBlockEntity controller = formedController(machineId, bus);
        MachineRecipe recipe = inputRecipe("sharpness_two", machineId, List.of(enchantedInput(2)));

        assertThat(new ActiveMachineRecipe(recipe).canStartCrafting(new RecipeCraftingContext(controller))).isTrue();
    }

    @Test
    void sharedDelayHelperDelaysOnlyConflictProneRecipeWithinWindow() {
        RecipeStartDelay delay = new RecipeStartDelay();
        Identifier recipe = Identifier.fromNamespaceAndPath("mmcr", "single_gold");

        assertThat(delay.shouldDelay(recipe, true, 100)).isTrue();
        assertThat(delay.shouldDelay(recipe, true, 119)).isTrue();
        assertThat(delay.shouldDelay(recipe, true, 120)).isFalse();
        assertThat(delay.shouldDelay(recipe, false, 121)).isFalse();
        assertThat(delay.shouldDelay(recipe, true, 122)).isTrue();
    }

    private static boolean invokeShouldDelay(MachineControllerBlockEntity controller,
                                             RecipeSearchResult result,
                                             long gameTime) throws Exception {
        var method = MachineControllerBlockEntity.class.getDeclaredMethod("shouldDelayConflictProneStart", RecipeSearchResult.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, result, gameTime);
    }

    private static boolean invokeApplySearchResult(MachineControllerBlockEntity controller,
                                                   RecipeSearchResult result,
                                                   int candidateCount) throws Exception {
        var method = MachineControllerBlockEntity.class.getDeclaredMethod("applySearchResult", RecipeSearchResult.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, result, candidateCount);
    }

    private static boolean invokeTryRestartLastRecipe(MachineControllerBlockEntity controller, Identifier machineId) throws Exception {
        var method = MachineControllerBlockEntity.class.getDeclaredMethod("tryRestartLastRecipe", Identifier.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, machineId);
    }

    private static void invokeTickSingleActiveRecipe(MachineControllerBlockEntity controller) throws Exception {
        var method = MachineControllerBlockEntity.class.getDeclaredMethod("tickSingleActiveRecipe");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static RecipeSearchResult startableConflictResult(Identifier machineId, MachineRecipe recipe, long structureVersion) throws Exception {
        MachineControllerBlockEntity contextController = formedController(machineId);
        ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(recipe, 1);
        RecipeCraftingContext context = new RecipeCraftingContext(contextController);
        return RecipeSearchResult.success(activeRecipe, context, machineId, structureVersion, true);
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

    private static MachineRecipe outputRecipe(String path, Identifier machineId, Item output) {
        return new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", path),
                machineId,
                1,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output.getDefaultInstance())));
    }

    private static MachineRecipe parallelizedInputRecipe(String path, Identifier machineId, Item item, int count) {
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
                List.of(itemInput(item, count)),
                true);
    }

    private static ItemRequirement itemInput(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemRequirement enchantedInput(int sharpnessLevel) {
        var enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", sharpnessLevel);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup()), enchantments))));
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.DIAMOND_SWORD), 1,
                ItemStack.EMPTY, 1F, List.of(), predicates, 0F);
    }

    private static ItemStack enchantedSword(String enchantmentId, int level) {
        var lookup = VanillaRegistries.createLookup();
        var enchantment = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, Identifier.parse(enchantmentId)));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return sword;
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) unsafe.allocateInstance(ItemInputBusBlockEntity.class);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        setField(ItemInputBusBlockEntity.class, bus, "kind", cn.howxu.mmcr.registry.PortKinds.ITEM_INPUT);
        setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
        return bus;
    }

    private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ItemOutputBusBlockEntity bus = (ItemOutputBusBlockEntity) unsafe.allocateInstance(ItemOutputBusBlockEntity.class);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        setField(ItemOutputBusBlockEntity.class, bus, "kind", cn.howxu.mmcr.registry.PortKinds.ITEM_OUTPUT);
        setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
        return bus;
    }

    private static void addParallelComponent(MachineControllerBlockEntity controller, ParallelTier tier) throws Exception {
        ParallelControllerBlockEntity parallel = parallelController(tier, new BlockPos(2, 0, 0));
        @SuppressWarnings("unchecked")
        List<ProcessingComponent> components = (List<ProcessingComponent>) fieldValue(MachineControllerBlockEntity.class, controller, "components");
        components.add(new ProcessingComponent(null, parallel, parallel.getBlockPos(), BlockPos.ZERO, List.of(), null));
    }

    private static ParallelControllerBlockEntity parallelController(ParallelTier tier, BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ParallelControllerBlockEntity parallel = (ParallelControllerBlockEntity) unsafe.allocateInstance(ParallelControllerBlockEntity.class);
        setField(BlockEntity.class, parallel, "type", null);
        setField(BlockEntity.class, parallel, "worldPosition", pos);
        setField(BlockEntity.class, parallel, "blockState", Blocks.IRON_BLOCK.defaultBlockState());
        setField(ParallelControllerBlockEntity.class, parallel, "tier", tier);
        return parallel;
    }

    private static MachineControllerBlockEntity formedController(Identifier machineId) throws Exception {
        return formedController(machineId, new BlockEntity[0]);
    }

    private static MachineControllerBlockEntity formedController(Identifier machineId, BlockEntity... ports) throws Exception {
        MachineControllerBlockEntity controller = controllerWithComponents(ports);
        DynamicMachine machine = new DynamicMachine(machineId, "Machine", new BlockArray(java.util.Map.of()));
        setField(MachineControllerBlockEntity.class, controller, "foundMachine", machine);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        setField(MachineControllerBlockEntity.class, controller, "structureVersion", 31L);
        MachineControllerBlock controllerBlock = testControllerBlock(machineId);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        setField(BlockEntity.class, controller, "blockState", controllerBlock.defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, true)
                .setValue(MachineControllerBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MachineControllerBlock.ROLL_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MachineControllerBlock.ACTIVE, false));
        List<BlockEntity> blockEntities = new ArrayList<>(List.of(ports));
        blockEntities.add(controller);
        var level = LevelStub.create(java.util.Map.of(BlockPos.ZERO, controllerBlock), blockEntities);
        setField(BlockEntity.class, controller, "level", level);
        for (BlockEntity port : ports) {
            setField(BlockEntity.class, port, "level", level);
        }
        return controller;
    }

    private static MachineControllerBlock testControllerBlock(Identifier machineId) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlock block = (MachineControllerBlock) unsafe.allocateInstance(MachineControllerBlock.class);
        setField(MachineControllerBlock.class, block, "machineId", machineId);
        setField(net.minecraft.world.level.block.state.BlockBehaviour.class, block, "properties", Blocks.IRON_BLOCK.properties());
        var builder = new net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState>(block);
        builder.add(MachineControllerBlock.FACING, MachineControllerBlock.ROLL_FACING, MachineControllerBlock.FORMED, MachineControllerBlock.ACTIVE);
        var stateDefinition = builder.create(Block::defaultBlockState, net.minecraft.world.level.block.state.BlockState::new);
        setField(Block.class, block, "stateDefinition", stateDefinition);
        setField(Block.class, block, "defaultBlockState", stateDefinition.any()
                .setValue(MachineControllerBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MachineControllerBlock.ROLL_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.ACTIVE, false));
        return block;
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
                    new MachineComponent(port instanceof ItemOutputBusBlockEntity ? PortKinds.ITEM_OUTPUT : PortKinds.ITEM_INPUT,
                            port instanceof ItemOutputBusBlockEntity ? cn.howxu.mmcr.util.IOType.OUTPUT : cn.howxu.mmcr.util.IOType.INPUT),
                    port,
                    port.getBlockPos(),
                    BlockPos.ZERO,
                    (String) null));
        }
        return controller;
    }

    private static ServerLevel serverLevel(List<BlockEntity> blockEntities) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestServerLevel level = (TestServerLevel) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(TestServerLevel.class);
        setField(TestServerLevel.class, level, "blocks", new HashMap<>());
        setField(TestServerLevel.class, level, "blockEntities", blockEntities.stream()
                .collect(java.util.stream.Collectors.toMap(BlockEntity::getBlockPos, entity -> entity)));
        return level;
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

    private static final class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockState> blocks;
        private Map<BlockPos, BlockEntity> blockEntities;

        private TestServerLevel() {
            super(null, null, null, null, null, null, false, 0L, List.of(), false);
        }

        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
        @Override public void blockEntityChanged(BlockPos pos) { }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) { blocks.put(pos, state); return true; }
        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
        @Override public boolean hasChunk(int chunkX, int chunkZ) { return true; }
        @Override public void invalidateCapabilities(BlockPos pos) { }
    }
}
