package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.api.config.Actionable;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.MachineStructureBuilder;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.internal.item.PatternBatchData;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static cn.howxu.mmcr.util.IOType.INPUT;
import static cn.howxu.mmcr.api.machine.definition.BlockPredicate.any;
import static cn.howxu.mmcr.api.machine.definition.BlockPredicate.block;

/** Explicit synchronous production rounds and a single native item/fluid request owner.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchRuntimeGameTest {
    private PatternBatchRuntimeGameTest() { }

    public static void exactNormalBatchRejectsPrefixAndFinishesOnce(GameTestHelper helper) { complete(helper, false, 256, false); }
    public static void exactFactoryBatchRejectsPrefixAndFinishesOnce(GameTestHelper helper) { complete(helper, true, 256, false); }
    public static void paidNormalPatternReloadKeepsSnapshot(GameTestHelper helper) { complete(helper, false, 256, true); }
    public static void paidFactoryPatternReloadKeepsSnapshot(GameTestHelper helper) { complete(helper, true, 256, true); }
    public static void exactLongItemAndFluidBatchFinishesOnce(GameTestHelper helper) { complete(helper, false, Integer.MAX_VALUE, false); }
    public static void rememberedNormalPatternFastStartRequiresNewRequest(GameTestHelper helper) { remembered(helper, false); }
    public static void rememberedFactoryPatternFastStartRequiresNewRequest(GameTestHelper helper) { remembered(helper, true); }

    public static void normalStageAndLevelGatesPreserveExactInputs(GameTestHelper helper) {
        var types = MachineLevelRegistry.types();
        var levels = types.stream().flatMap(type -> MachineLevelRegistry.getLevels(type.id()).stream()).toList();
        var typeId = MMCR.id("batch_runtime_gate_coil");
        var low = new MachineLevel(typeId.withSuffix("_low"), typeId, 1, new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
        var high = new MachineLevel(typeId.withSuffix("_high"), typeId, 2, new BlockPredicate.OfBlockState(Blocks.GOLD_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
        var combinedTypes = new ArrayList<>(types);
        combinedTypes.add(new LevelType(typeId, Component.literal("Gate test")));
        var combinedLevels = new ArrayList<>(levels);
        combinedLevels.addAll(List.of(low, high));
        MachineLevelRegistry.installSnapshot(combinedTypes, combinedLevels);
        try (var fixture = new Fixture(helper, false, 1L, false, "eligibility", true)) {
            helper.assertTrue(fixture.controller.currentStructureSnapshot().matchedStage() == 1
                            && high.equals(fixture.controller.currentRuntimeSnapshot().foundLevels().get(typeId)),
                    "Real initial structure has sufficient coil level but only stage one; structure="
                            + fixture.controller.currentStructureSnapshot() + ", levels="
                            + fixture.controller.currentRuntimeSnapshot().foundLevels());
            fixture.assertGateDenied();
            helper.setBlock(new BlockPos(2, 1, 1), Blocks.DIAMOND_BLOCK.defaultBlockState());
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.COPPER_BLOCK.defaultBlockState());
            // Direct GameTest block writes do not emit the player block-change notification.
            MachineControllerBlockEntity.markStructureDirty(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
            fixture.controller.requestImmediateStructureCheck();
            for (int round = 0; round < 32; round++) fixture.round();
            helper.assertTrue(fixture.controller.currentStructureSnapshot().matchedStage() == 2
                            && low.equals(fixture.controller.currentRuntimeSnapshot().foundLevels().get(typeId)),
                    "Physical stage upgrade with a low coil is still level-ineligible; structure="
                            + fixture.controller.currentStructureSnapshot() + ", levels="
                            + fixture.controller.currentRuntimeSnapshot().foundLevels());
            fixture.assertGateDenied();
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.GOLD_BLOCK.defaultBlockState());
            MachineControllerBlockEntity.markStructureDirty(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
            fixture.controller.requestImmediateStructureCheck();
            for (int round = 0; round < 32; round++) fixture.round();
            helper.assertTrue(fixture.controller.currentStructureSnapshot().matchedStage() == 2
                            && high.equals(fixture.controller.currentRuntimeSnapshot().foundLevels().get(typeId)),
                    "Physical gold coil restores the actual high level at stage two; structure="
                            + fixture.controller.currentStructureSnapshot() + ", levels="
                            + fixture.controller.currentRuntimeSnapshot().foundLevels());
            helper.assertTrue(fixture.controller.supportsPatternRecipe(fixture.recipe), "Real stage two and high coil permit publication");
            var request = fixture.request(1L, false);
            try (var reservation = fixture.reserve(1L, request)) {
                helper.assertTrue(reservation.status() == PatternStartReservation.Status.RESERVED, "Eligible request reserves");
                fixture.controller.componentRuntime().replaceLevels(Map.of());
                helper.assertFalse(reservation.commit(request.holder()::clear), "Final live guard rejects loss of eligibility before payment");
                helper.assertTrue(request.holder().get(AEItemKey.of(Items.IRON_INGOT)) == 2L
                                && request.inventory().getAvailableStacks().get(AEFluidKey.of(Fluids.WATER)) == 250L,
                         "Final-guard refusal preserves the complete mixed request");
            }
            MachineControllerBlockEntity.markStructureDirty(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
            fixture.controller.requestImmediateStructureCheck();
            for (int round = 0; round < 32; round++) fixture.round();
            fixture.start(1L);
            for (int round = 0; round < 8; round++) fixture.round();
            fixture.assertOutputs(1L);
        } finally { MachineLevelRegistry.installSnapshot(types, levels); }
        helper.succeed();
    }

    public static void conditionalBatchRunsAllNativeCallbacksAtRequestedParallelism(GameTestHelper helper) {
        complete(helper, false, 4L, false, true);
    }

    public static void probabilisticOutputsAdmitBatchesAndRetainedInputsReturnUnconsumed(GameTestHelper helper) {
        for (float consumeChance : new float[]{0F, 1F}) {
            for (float outputChance : new float[]{0F, 0.5F, 1F}) {
                // Reload coverage changes modifiers; same-state placement would reuse that controller in the next case.
                helper.setBlock(new BlockPos(0, 1, 0), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(1, 1, 0), Blocks.AIR.defaultBlockState());
                try (var fixture = new Fixture(helper, false, 4L, false,
                        "probability_" + (int) (consumeChance * 100) + "_" + (int) (outputChance * 100),
                        false, false, consumeChance, outputChance)) {
                    var definition = PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2L), new GenericStack(AEFluidKey.of(Fluids.WATER), 250L)),
                            List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 3L), new GenericStack(AEFluidKey.of(Fluids.LAVA), 100L)));
                    var original = PatternDetailsHelper.decodePattern(definition, helper.getLevel());
                    var profiles = PatternBatchSupport.inspect(fixture.host, original).stream()
                            .filter(value -> value.binding().recipeId().equals(fixture.recipe.id())).toList();
                    helper.assertFalse(profiles.isEmpty(), "The probability recipe remains bound: input=" + consumeChance
                            + ", output=" + outputChance + ", effective=" + fixture.controller.effectivePatternRecipe(fixture.recipe));
                    var profile = profiles.getFirst();
                    helper.assertTrue(profile.safety() == PatternBatchSupport.Safety.PROVEN_LINEAR && profile.maxMultiplier() == 4L,
                            "Output probability and deterministic input consumption retain batch eligibility");
                    var batch = new PatternBatchDetails(original, new PatternBatchData(1, fixture.machineId, fixture.recipe.id(), 1L, 4L));
                    var inventory = new ListCraftingInventory(key -> {});
                    inventory.insert(AEItemKey.of(Items.IRON_INGOT), 8L, Actionable.MODULATE);
                    inventory.insert(AEFluidKey.of(Fluids.WATER), 1000L, Actionable.MODULATE);
                    var holders = CraftingCpuHelper.extractPatternInputs(batch, inventory, helper.getLevel(), new KeyCounter(), new KeyCounter());
                    helper.assertTrue(holders != null, "Native CPU extracts the complete batch");
                    if (consumeChance == 1F && outputChance == 1F) {
                        var probabilisticInputs = fixture.recipe.requirements().stream().map(requirement -> {
                            if (requirement instanceof ItemRequirement item && item.io() == IOType.INPUT) {
                                return (MachineRequirement) new ItemRequirement(item.io(), item.item(), item.count(), item.stack(),
                                        item.chance(), item.tags(), item.components(), 0.5F);
                            }
                            return requirement;
                        }).toList();
                        fixture.beforeStart = context -> context.setRequirements(probabilisticInputs);
                        helper.assertFalse(fixture.host.craftingMachine().pushPattern(batch, holders, null),
                                "A callback cannot turn an already-scaled job into probabilistic input consumption");
                        fixture.beforeStart = context -> { };
                        var changedRecipe = MachineRecipe.fromCanonical(fixture.recipe.id(), fixture.machineId, 2,
                                probabilisticInputs, fixture.recipe.runtimeMachineOutputs(), List.of(), 0, 1, false, true, false, Set.of());
                        var changedRecipes = new LinkedHashMap<>(RecipeRegistry.dynamicSnapshot());
                        changedRecipes.put(changedRecipe.id(), changedRecipe);
                        RecipeRegistry.replaceDynamic(changedRecipes);
                        helper.assertFalse(PatternBatchSupport.supportsBatchInputs(fixture.controller.effectivePatternRecipe(changedRecipe).requirements()),
                                "A live recipe reload changes input consumption to an intermediate probability");
                        helper.assertFalse(fixture.host.craftingMachine().pushPattern(batch, holders, null),
                                "A recipe reload cannot bypass the probability rule through an old scaled task");
                        helper.assertTrue(fixture.active() == null && fixture.returnedItems() == 0L
                                        && Arrays.stream(holders).mapToLong(counter -> counter.get(AEItemKey.of(Items.IRON_INGOT))).sum() == 8L
                                        && Arrays.stream(holders).mapToLong(counter -> counter.get(AEFluidKey.of(Fluids.WATER))).sum() == 1000L,
                                "Rejected scaled jobs neither activate nor produce output; the extracted holders remain retryable");
                        changedRecipes.put(fixture.recipe.id(), fixture.recipe);
                        RecipeRegistry.replaceDynamic(changedRecipes);
                    }
                    helper.assertTrue(fixture.host.craftingMachine().pushPattern(batch, holders, null),
                            "Native push admits the entire batch with unchanged probabilistic output promises");
                    helper.assertTrue(fixture.active() != null && fixture.active().parallelism() == 4L,
                            "A probability output does not silently reduce execution parallelism");
                    var returns = fixture.host.getLogic().getReturnInv().getAvailableStacks();
                    helper.assertTrue(returns.get(AEItemKey.of(Items.IRON_INGOT)) == (consumeChance == 0F ? 8L : 0L)
                                    && returns.get(AEFluidKey.of(Fluids.WATER)) == (consumeChance == 0F ? 1000L : 0L),
                            "Zero-consumption inputs are returned in full; guaranteed-consumption inputs are paid exactly");
                    if (consumeChance == 0F) fixture.reloadPaid();
                    for (int round = 0; round < 8; round++) fixture.round();
                    helper.assertTrue(fixture.active() == null && fixture.finishParallelisms.equals(List.of(4L)),
                            "The batch completes exactly once without asserting a random output result: input=" + consumeChance
                                    + ", output=" + outputChance + ", state=" + fixture.runtime.craftingRuntime().snapshot()
                                    + ", failure=" + fixture.runtime.craftingRuntime().failure() + ", finishes=" + fixture.finishParallelisms);
                    if (outputChance == 0F || outputChance == 1F) {
                        helper.assertTrue(fixture.returnedItems() == (outputChance == 0F ? 0L : 12L)
                                        && fixture.returnedFluids() == (outputChance == 0F ? 0L : 400L),
                                "Output probability endpoints retain their native never/always production semantics");
                    }
                }
            }
        }
        helper.succeed();
    }

    public static void inspectionDoesNotLoadLinkedControllerChunk(GameTestHelper helper) {
        try (var fixture = new Fixture(helper, false, 256, false, "inspection")) {
            BlockPos unloaded = fixture.controller.getBlockPos().offset(1_000_000, 0, 0);
            helper.assertFalse(helper.getLevel().hasChunkAt(unloaded), "Remote linked controller starts unloaded");
            fixture.host.linkControllerAppearance(unloaded, null);
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2), new GenericStack(AEFluidKey.of(Fluids.WATER), 250)),
                    List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 3), new GenericStack(AEFluidKey.of(Fluids.LAVA), 100))),
                    helper.getLevel());
            helper.assertTrue(pattern != null && !PatternBatchSupport.inspect(fixture.host, pattern).isEmpty(),
                    "A loaded eligible controller still supplies the binding");
            helper.assertFalse(helper.getLevel().hasChunkAt(unloaded), "Read-only inspection never force-loads linked chunks");
            fixture.controller.setRemoved();
            helper.assertTrue(PatternBatchSupport.inspect(fixture.host, pattern).isEmpty(), "Removed controller cannot advertise stale capacity");
        }
        helper.succeed();
    }

    private static void complete(GameTestHelper helper, boolean factory, long copies, boolean reload) {
        complete(helper, factory, copies, reload, false);
    }

    private static void complete(GameTestHelper helper, boolean factory, long copies, boolean reload, boolean guarded) {
        try (var fixture = new Fixture(helper, factory, copies, reload, "execution", false, guarded)) {
            var incomplete = fixture.request(copies, true);
            try (var denied = fixture.reserve(copies, incomplete)) {
                helper.assertTrue(denied.status() == PatternStartReservation.Status.UNAVAILABLE,
                        "One missing item rejects the entire exact single-lane request, never a prefix");
            }
            helper.assertTrue(incomplete.inventory().getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT)) == 2L * copies - 1
                            && incomplete.inventory().getAvailableStacks().get(AEFluidKey.of(Fluids.WATER)) == 250L * copies
                            && incomplete.holder().get(AEItemKey.of(Items.IRON_INGOT)) == 2L * copies - 1,
                    "Rejected native admission leaves both material families and the caller holder untouched");
            fixture.start(copies);
            if (reload) fixture.reloadPaid();
            for (int round = 0; round < 8; round++) fixture.round();
            fixture.assertOutputs(copies);
            helper.assertTrue(fixture.active() == null, "Completed source execution has no active runtime");
            helper.assertTrue(!fixture.tickParallelisms.isEmpty() && fixture.tickParallelisms.stream().allMatch(value -> value == copies)
                            && fixture.finishParallelisms.equals(List.of(copies)),
                    "Native tick and finish callbacks observe the whole batch parallelism and finish exactly once");
            for (int round = 0; round < 3; round++) fixture.round();
            fixture.assertOutputs(copies);
        }
        helper.succeed();
    }

    private static void remembered(GameTestHelper helper, boolean factory) {
        try (var fixture = new Fixture(helper, factory, 256, false, "remembered")) {
            fixture.start(256, PatternStartReservation.RecipeSelection.CATALOG);
            for (int round = 0; round < 8; round++) fixture.round();
            fixture.assertOutputs(256);
            helper.assertTrue(fixture.cachedRecipe() == fixture.recipe,
                    "Real recipe-thread memory contains the validated exact-start recipe");
            for (int round = 0; round < 4; round++) fixture.round();
            helper.assertTrue(fixture.active() == null && fixture.cachedRecipe() == fixture.recipe,
                    "Remembered external recipe stays idle without new request ownership");
            fixture.start(256, PatternStartReservation.RecipeSelection.LAST_RECIPE);
            for (int round = 0; round < 8; round++) fixture.round();
            fixture.assertOutputs(512);
            helper.assertTrue(fixture.active() == null && fixture.cachedRecipe() == fixture.recipe,
                    "A fresh exact request uses retained recipe-thread memory and settles only its own batch");
        }
        helper.succeed();
    }

    /** Scopes the actual server work-mode config, clock and shared registry changes.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final boolean factory;
        private final long originalTime;
        private final MachineWorkMode originalMode;
        private final ResourceLocation machineId;
        private final Map<ResourceLocation, MachineRecipe> previousRecipes;
        private final Map<ResourceLocation, MachineStructureDefinition> previousStructures;
        private final MachineControllerBlockEntity controller;
        private final MachineControllerRuntime runtime;
        private final PatternInterfaceBlockEntity host;
        private final MachineRecipe recipe;
        private final AtomicInteger hooks = new AtomicInteger();
        private final List<Long> tickParallelisms = new ArrayList<>();
        private final List<Long> finishParallelisms = new ArrayList<>();
        private Consumer<RecipeStartContext> beforeStart = context -> { };
        private boolean registered;
        private boolean declarationRegistered;
        private boolean structuresInstalled;

        Fixture(GameTestHelper helper, boolean factory, long copies, boolean reload, String purpose) {
            this(helper, factory, copies, reload, purpose, false);
        }

        Fixture(GameTestHelper helper, boolean factory, long copies, boolean reload, String purpose, boolean gated) {
            this(helper, factory, copies, reload, purpose, gated, false);
        }

        Fixture(GameTestHelper helper, boolean factory, long copies, boolean reload, String purpose, boolean gated, boolean guarded) {
            this(helper, factory, copies, reload, purpose, gated, guarded, 1F, 1F);
        }

        private Fixture(GameTestHelper helper, boolean factory, long copies, boolean reload, String purpose,
                        boolean gated, boolean guarded, float consumeChance, float outputChance) {
            this.helper = helper;
            this.factory = factory;
            originalTime = helper.getLevel().getGameTime();
            originalMode = ServerConfig.machineWorkMode();
            previousRecipes = RecipeRegistry.dynamicSnapshot();
            previousStructures = MachineStructureRegistry.dynamicSnapshot();
            machineId = MMCR.id("batch_runtime_" + purpose + "_" + factory + "_" + copies + "_" + reload);
            BlockPos controllerPos = new BlockPos(0, 1, 0);
            BlockPos hostPos = new BlockPos(1, 1, 0);
            helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                    .setValue(MachineControllerBlock.FACING, Direction.SOUTH).setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH));
            var hostBlock = ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get();
            helper.setBlock(hostPos, hostBlock.defaultBlockState());
            Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
            pattern.put(hostPos.subtract(controllerPos), new BlockPredicate.OfBlock(hostBlock));
            var parallel = ModBlocks.BLOCKS.get((copies <= 256 ? ParallelTier.PRO : ParallelTier.ULTIMATE).idSuffix()).get();
            helper.setBlock(controllerPos.offset(0, 0, 1), parallel.defaultBlockState());
            pattern.put(new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(parallel));
            if (factory) {
                var scheduler = ModBlocks.BLOCKS.get("factory_controller").get();
                helper.setBlock(new BlockPos(2, 1, 0), scheduler.defaultBlockState());
                pattern.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(scheduler));
            }
            var coilPos = new BlockPos(1, 0, 1);
            if (gated) {
                helper.setBlock(controllerPos.offset(coilPos), Blocks.GOLD_BLOCK.defaultBlockState());
                pattern.put(coilPos, new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK),
                        new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK))));
            }
            var stageOne = new BlockArray(pattern, Map.of(), gated ? Map.of(coilPos, 'L') : Map.of());
            Machine machine = new Machine() {
                @Override public ResourceLocation registryName() { return machineId; }
                @Override public BlockArray pattern() { return new BlockArray(pattern); }
                @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(machineId); }
                @Override public boolean parallelizable() { return true; }
                @Override public long maxParallelism() { return copies; }
                @Override public boolean hasFactory() { return factory; }
                @Override public int factoryThreadLimit() { return 4; }
                @Override public RecipeBehavior behavior() {
                    return RecipeBehavior.builder().beforeStart(context -> {
                                hooks.incrementAndGet();
                                beforeStart.accept(context);
                            })
                            .recipeTick(context -> tickParallelisms.add(context.parallelism()))
                            .beforeFinish(context -> finishParallelisms.add(context.effectiveParallelism())).build();
                }
            };
            controller = helper.getBlockEntity(controllerPos);
            host = helper.getBlockEntity(hostPos);
            try {
                var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
                field.setAccessible(true);
                runtime = (MachineControllerRuntime) field.get(controller);
            } catch (ReflectiveOperationException exception) { throw new GameTestAssertException("Cannot access attached runtime: " + exception); }
            List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 3), outputChance),
                    new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 100), outputChance));
            var requirements = new ArrayList<MachineRequirement>(List.of(
                    new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY,
                            1F, List.of(), DataComponentPredicateSet.EMPTY, consumeChance),
                    new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 250, FluidStack.EMPTY,
                            1F, List.of(), consumeChance)));
            outputs.forEach(output -> requirements.add(OutputRegistry.toRequirement(output, List.of())));
            if (gated) requirements.addAll(List.of(new StageRequirement(2),
                    new LevelRequirement(MMCR.id("batch_runtime_gate_coil"), MMCR.id("batch_runtime_gate_coil_high"))));
            if (guarded) requirements.add(new StageRequirement(1));
            recipe = MachineRecipe.fromCanonical(machineId.withSuffix("_recipe"), machineId, 2, requirements,
                    List.of(), List.of(), 0, 1, false, true, false, Set.of());
            try {
                helper.assertFalse(MachineRegistry.containsStatic(machineId), "Fixture machine ID starts clean");
                if (gated) {
                    helper.assertTrue(MachineDefinitions.getRegistration(machineId) == null
                                    && !MachineStructureRegistry.effectiveSnapshot().containsKey(machineId),
                            "Gated fixture declaration and structure IDs start clean");
                    var controllerBlock = controller.getBlockState().getBlock();
                    var typeId = MMCR.id("batch_runtime_gate_coil");
                    var definition = MachineDefinitionConverter.toStructureDefinition(MachineStructureBuilder.structure()
                            .fullStructure(stage -> stage.pattern(builder -> builder.layer("CH ").layer("PL ")
                                            .where('C', block(controllerBlock)).controller('C')
                                            .where('H', block(hostBlock)).where('P', block(parallel))
                                            .where('L', any(block(Blocks.COPPER_BLOCK), block(Blocks.GOLD_BLOCK))))
                                    .requirements(requirementsBuilder -> requirementsBuilder.levelSlot('L', typeId)))
                            .expandStructure(stage -> stage.pattern(builder -> builder.layer("CH ").layer("PLD")
                                            .where('C', block(controllerBlock)).controller('C')
                                            .where('H', block(hostBlock)).where('P', block(parallel))
                                            .where('L', any(block(Blocks.COPPER_BLOCK), block(Blocks.GOLD_BLOCK)))
                                            .where('D', block(Blocks.DIAMOND_BLOCK)))
                                    .requirements(requirementsBuilder -> requirementsBuilder.levelSlot('L', typeId)))
                            .build(machineId));
                    boolean phaseOpen = MachineDefinitions.isRegistryPhaseOpen();
                    MachineDefinitions.beginRegistryPhase();
                    try {
                        MachineDefinitions.register(MachineRegistration.builder(machineId).expandableStructure()
                                .allowParallelism(true).maxParallelAmount(copies).factoryThreadLimit(4).build());
                        declarationRegistered = true;
                    } finally { if (!phaseOpen) MachineDefinitions.freezeRegistryPhase(); }
                    var structures = new LinkedHashMap<>(previousStructures);
                    structures.put(machineId, definition);
                    structuresInstalled = true;
                    MachineStructureRegistry.replaceDynamic(structures);
                    machine = MachineRegistry.getMachine(machineId);
                    helper.assertTrue(definition.levelSlots().equals(Map.of(coilPos, typeId))
                                    && MachineRegistry.getCompiledStages(machineId).size() == 2,
                            "Registered canonical structure compiles both stages and the actual L level slot");
                } else {
                    MachineRegistry.register(machine);
                    registered = true;
                }
                var recipes = new LinkedHashMap<>(previousRecipes);
                recipes.put(recipe.id(), recipe);
                RecipeRegistry.replaceDynamic(recipes);
                ServerConfig.MACHINE_WORK_MODE.clearCache();
                ServerConfig.MACHINE_WORK_MODE.set(MachineWorkMode.SYNC);
                helper.assertTrue(ServerConfig.MACHINE_WORK_MODE.get() == MachineWorkMode.SYNC,
                        "The scoped world-restart config reads back SYNC before ticking");
                controller.setMachine(machine);
                controller.setStructureCheckIntervalForTesting(1);
                var mismatch = StructureMatcher.firstMismatch(stageOne, helper.getLevel(), controller.getBlockPos(), Map.of(), false);
                helper.assertTrue(mismatch.isEmpty(), "Real fixture layout matches the SOUTH-facing base stage: " + mismatch);
                controller.requestImmediateStructureCheck();
                SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(originalTime, true);
                controller.serverTick();
                for (int step = 0; step < 32 && (!controller.currentStructureSnapshot().formed()
                        || controller.resourceDomain() == null); step++) round();
                var structure = controller.currentStructureSnapshot();
                helper.assertTrue(ServerConfig.machineWorkMode() == MachineWorkMode.SYNC
                                && controller.activeWorkMode() == MachineWorkMode.SYNC
                                && structure.formed() && controller.resourceDomain() != null,
                        "Actual server SYNC config forms the real structure and hardware before admission; pos="
                                + controller.getBlockPos() + ", block=" + controller.getBlockState()
                                + ", mode=" + ServerConfig.machineWorkMode() + ", activeMode=" + controller.activeWorkMode()
                                + ", formed=" + structure.formed() + ", stage=" + structure.matchedStage()
                                + ", loaded=" + structure.structureAreaLoaded() + ", mismatch=" + structure.structureMismatchDiagnostic()
                                + ", formationFailure=" + structure.lastFormationFailure()
                                + ", domain=" + controller.resourceDomain() + ", scanCursor=" + controller.structureScanCursorForTesting());
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        Request request(long copies, boolean incomplete) {
            var holder = new KeyCounter();
            holder.add(AEItemKey.of(Items.IRON_INGOT), Math.multiplyExact(2L, copies) - (incomplete ? 1L : 0L));
            holder.add(AEFluidKey.of(Fluids.WATER), Math.multiplyExact(250L, copies));
            return new Request(holder, AE2NativeAdapters.requestInventory(new KeyCounter[]{holder}));
        }

        void assertGateDenied() {
            var request = request(1L, false);
            helper.assertFalse(controller.supportsPatternRecipe(recipe), "Unsatisfied captured eligibility is not published");
            try (var denied = reserve(1L, request)) {
                helper.assertTrue(denied.status() == PatternStartReservation.Status.UNAVAILABLE,
                        "Unsatisfied stage/level rejects exact admission");
            }
            helper.assertTrue(request.holder().get(AEItemKey.of(Items.IRON_INGOT)) == 2L
                            && request.holder().get(AEFluidKey.of(Fluids.WATER)) == 250L
                            && request.inventory().getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT)) == 2L
                            && request.inventory().getAvailableStacks().get(AEFluidKey.of(Fluids.WATER)) == 250L && active() == null,
                    "Rejected eligibility leaves CPU holders and native mixed inventory wholly unpaid");
        }

        PatternStartReservation reserve(long copies, Request request) {
            List<MachineCapability> inputs = List.of(new ItemBusCapability(host, AE2NativeAdapters.items(request.inventory()), INPUT, false),
                    new FluidHatchCapability(host, AE2NativeAdapters.fluids(request.inventory()), INPUT, false));
            return controller.reserveExactPatternStart(host.getBlockPos(), recipe.id(), recipe.runtimeMachineOutputs(), copies, inputs);
        }

        void start(long copies) {
            start(copies, PatternStartReservation.RecipeSelection.CATALOG);
        }

        void start(long copies, PatternStartReservation.RecipeSelection selection) {
            var request = request(copies, false);
            try (var reservation = reserve(copies, request)) {
                helper.assertTrue(reservation.status() == PatternStartReservation.Status.RESERVED && reservation.parallelism() == copies,
                        "Complete mixed request reserves precisely one runtime at the requested long parallelism");
                helper.assertTrue(reservation.recipeSelection() == selection,
                        "Exact admission reports the real catalog or lastRecipe fast-start selection");
                helper.assertTrue(reservation.commit(request.holder()::clear) && reservation.ownershipAccepted(),
                        "Native payment clears caller ownership only after the complete request is accepted");
            }
            var active = active();
            helper.assertTrue(active != null && active.parallelism() == copies && active.requiresPatternRequest()
                            && request.inventory().isEmpty() && request.holder().isEmpty(),
                    "One active request-only runtime owns the entire mixed batch, not duplicated family inventories");
        }

        CraftingRuntime active() {
            return factory ? runtime.factoryRuntime().activeRuntimes().stream().findFirst().orElse(null)
                    : runtime.craftingRuntime().active() ? runtime.craftingRuntime() : null;
        }

        MachineRecipe cachedRecipe() {
            if (factory) return runtime.factoryRuntime().cachedPatternRecipe(recipe.id());
            try {
                var field = MachineControllerBlockEntity.class.getDeclaredField("normalRecipeThread");
                field.setAccessible(true);
                return ((MachineRecipeThread) field.get(controller)).cachedPatternRecipe(recipe.id());
            } catch (ReflectiveOperationException exception) { throw new GameTestAssertException("Cannot inspect actual recipe-thread memory: " + exception); }
        }

        long returnedItems() {
            return host.getLogic().getReturnInv().getAvailableStacks().get(AEItemKey.of(Items.GOLD_INGOT));
        }

        long returnedFluids() {
            return host.getLogic().getReturnInv().getAvailableStacks().get(AEFluidKey.of(Fluids.LAVA));
        }

        void assertOutputs(long copies) {
            var returns = host.getLogic().getReturnInv().getAvailableStacks();
            helper.assertTrue(returns.get(AEItemKey.of(Items.GOLD_INGOT)) == Math.multiplyExact(3L, copies)
                            && returns.get(AEFluidKey.of(Fluids.LAVA)) == Math.multiplyExact(100L, copies),
                    "Actual native return inventory settles the saved mixed output snapshot exactly once");
        }

        void reloadPaid() {
            var registries = helper.getLevel().registryAccess();
            var saved = new CompoundTag();
            if (factory) runtime.factoryRuntime().save(saved, registries); else runtime.craftingRuntime().save(saved, registries);
            controller.componentRuntime().setModifiersAllowed(true);
            controller.componentRuntime().replaceModifiers(Map.of("changed", List.of(
                    MachineModifier.numeric("output", "output", 2D, "multiply", false))));
            if (factory) {
                runtime.factoryRuntime().load(saved.copy(), controller, registries);
                runtime.factoryRuntime().rebindCurrentVersions();
            } else {
                runtime.craftingRuntime().load(saved.copy(), controller.resourceDomain(), registries);
                runtime.craftingRuntime().rebindCurrentVersions();
            }
            helper.assertTrue(active() != null && active().requiresPatternRequest(), "Paid reload retains request-only provenance");
        }

        void round() {
            long next = helper.getLevel().getGameTime() + 1;
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(next);
            SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(next, true);
            controller.serverTick();
            SharedIoEvents.completeLevelTick(helper.getLevel());
        }

        @Override public void close() {
            try {
                runtime.craftingRuntime().invalidate();
                runtime.factoryRuntime().clear();
                SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
                controller.releaseStructureClaims();
            } finally {
                ServerConfig.MACHINE_WORK_MODE.clearCache();
                ServerConfig.MACHINE_WORK_MODE.set(originalMode);
                ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(originalTime);
                try { RecipeRegistry.replaceDynamic(previousRecipes); }
                finally {
                    try { if (structuresInstalled) MachineStructureRegistry.replaceDynamic(previousStructures); }
                    finally { if (registered || declarationRegistered) removeMachine(); }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void removeMachine() {
            synchronized (RuntimeContentVersion.lock()) {
                try {
                    if (declarationRegistered) {
                        var declarations = MachineDefinitions.class.getDeclaredField("STATIC_REGISTRATIONS");
                        declarations.setAccessible(true);
                        ((Map<ResourceLocation, MachineRegistration>) declarations.get(null)).remove(machineId);
                    }
                    for (String name : List.of("STATIC_MACHINES", "COMPILED", "EFFECTIVE_MACHINES")) {
                        var field = MachineRegistry.class.getDeclaredField(name);
                        field.setAccessible(true);
                        var current = (Map<ResourceLocation, Object>) field.get(null);
                        if (name.equals("STATIC_MACHINES")) current.remove(machineId);
                        else {
                            var copy = new LinkedHashMap<>(current);
                            copy.remove(machineId);
                            field.set(null, Map.copyOf(copy));
                        }
                    }
                } catch (ReflectiveOperationException exception) { throw new GameTestAssertException("Cannot remove fixture machine: " + exception); }
            }
        }
    }

    /** One shared native inventory for both families, with separate unaccepted caller counters.
     * @author howxu <dev@howxu.cn>
     */
    private record Request(KeyCounter holder, GenericStackInv inventory) { }
}
