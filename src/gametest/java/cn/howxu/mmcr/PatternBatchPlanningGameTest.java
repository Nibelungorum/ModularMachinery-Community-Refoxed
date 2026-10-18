package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning.BatchLimit;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning.Snapshot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport.Safety;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appmek.AppMekGameTestFixtures;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.MirrorPatternInterfaceLogic;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.internal.item.PatternBatchData;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity.MasterLocation;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Native AE planning/extraction plus real host publication; no runtime CPU bookkeeping changes.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchPlanningGameTest {
    private PatternBatchPlanningGameTest() { }

    public static void onlyTheThreeMmcrHostsPublishBatchIdentity(GameTestHelper helper) {
        String[] kinds = {"ae2_me_pattern_interface", "eae_me_extended_pattern_interface", "eaep_me_mirror_pattern_interface"};
        List<PatternInterfaceBlockEntity> hosts = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++) {
            var position = new BlockPos(2 * i, 0, 0);
            helper.setBlock(position, ModBlocks.BLOCKS.get(kinds[i]).get().defaultBlockState());
            hosts.add(helper.getBlockEntity(position));
        }
        helper.setBlock(new BlockPos(6, 0, 0), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        PatternProviderBlockEntity vanilla = helper.getBlockEntity(new BlockPos(6, 0, 0));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(hosts.stream().allMatch(host -> host.getMainNode().getNode() != null)
                        && vanilla.getMainNode().getNode() != null, "Native first-tick initialization creates every node exactly once"))
                .thenExecute(() -> verifyPublication(helper, hosts, vanilla)).thenSucceed();
    }

    private static void verifyPublication(GameTestHelper helper, List<PatternInterfaceBlockEntity> hosts, PatternProviderBlockEntity vanilla) {
        var recipeId = MMCR.id("batch_planning_publication");
        if (RecipeRegistry.getRecipe(recipeId) == null) RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(
                recipeId, MMCR.id("test_cube"), 20,
                List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.PAPER), 1)),
                        MachineRequirement.itemOutput(new ItemStack(Items.PRISMARINE_SHARD))),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.PRISMARINE_SHARD), 1F)),
                List.of(), 0, 1, false, true, false, Set.of()));
        List<MachineControllerBlockEntity> controllers = new ArrayList<>();
        for (int i = 0; i < hosts.size(); i++) {
            controllers.add(bindController(helper, hosts.get(i), new BlockPos(2 * i, 0, 2), i == 1 ? 256 : 64));
            if (i > 0) GridHelper.createConnection(hosts.get(0).getMainNode().getNode(), hosts.get(i).getMainNode().getNode());
        }
        GridHelper.createConnection(hosts.get(0).getMainNode().getNode(), vanilla.getMainNode().getNode());
        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(stack(Items.PAPER, 1)), List.of(stack(Items.PRISMARINE_SHARD, 1)));
        hosts.get(0).getLogic().getPatternInv().setItemDirect(0, encoded.copy());
        hosts.get(1).getLogic().getPatternInv().setItemDirect(35, encoded.copy());
        vanilla.getLogic().getPatternInv().setItemDirect(0, encoded.copy());
        hosts.get(0).getLogic().updatePatterns();
        hosts.get(1).getLogic().updatePatterns();
        var mirrorLogic = (MirrorPatternInterfaceLogic) hosts.get(2).getLogic();
        helper.assertTrue(mirrorLogic.bindToMaster(new MasterLocation(helper.getLevel().dimension(), hosts.get(0).getBlockPos(), null)),
                "Mirror binds to the physical master rather than copying a virtual definition");
        hosts.get(2).getLogic().updatePatterns();
        vanilla.getLogic().updatePatterns();
        vanilla.getLogic().setPriority(10_000);
        var bare = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        var unit = hosts.get(0).getLogic().getAvailablePatterns().stream()
                .filter(pattern -> pattern instanceof PatternBatchDetails batch && batch.data().recipeId().equals(recipeId))
                .map(pattern -> (PatternBatchDetails) pattern).findFirst().orElseThrow();
        for (var host : hosts) helper.assertTrue(PatternBatchPlanning.isBatchHost(host)
                        && host.getLogic().getAvailablePatterns().contains(unit)
                        && PatternBatchPlanning.contains(host, List.of(bare), unit.scaled(256))
                        && !PatternBatchPlanning.contains(host, List.of(bare), bare),
                "Each MMCR kind publishes only the exact bound unit identity, never an implicit bare route");
        helper.assertTrue(!PatternBatchPlanning.isBatchHost(vanilla) && vanilla.getLogic().getAvailablePatterns().equals(List.of(bare)),
                "Native AE provider stays bare");
        var grid = hosts.get(0).getMainNode().getGrid();
        var captured = PatternBatchPlanning.capture(grid);
        helper.assertTrue(captured.limits().get(unit.getDefinition()).maxMultiplier() == 256,
                "Heterogeneous providers advertise the maximum single lane, never their sum or thread product");
        List<ICraftingProvider> providers = new ArrayList<>();
        ((CraftingService) grid.getCraftingService()).getProviders(unit.scaled(256)).forEach(providers::add);
        helper.assertTrue(providers.size() == 3 && hosts.stream().allMatch(host -> providers.contains(host.getLogic()))
                        && !providers.contains(vanilla.getLogic()), "Scaled lookup excludes a high-priority native provider");
        controllers.get(1).setMachine(parallelMachine(64));
        controllers.get(1).setFormed(true);
        hosts.get(1).refreshBatchPatterns();
        helper.assertTrue(PatternBatchPlanning.capture(grid).limits().get(unit.getDefinition()).maxMultiplier() == 64
                        && captured.limits().get(unit.getDefinition()).maxMultiplier() == 256,
                "New capability publication cannot modify an already captured plan");
        mirrorLogic.unbindFromMaster();
        helper.assertTrue(!PatternBatchPlanning.contains(hosts.get(2), List.of(), unit.scaled(64)), "Unbound mirror loses its routing identity");
        for (var host : hosts) host.onChunkUnloaded();
        helper.assertTrue(PatternBatchPlanning.capture(grid).limits().isEmpty(), "Unload removes all profiles from the live grid");
    }

    public static void fullRemainderAndSingleHugeBatchPreserveTotals(GameTestHelper helper) {
        var unit = unit(helper, "totals", List.of(stack(Items.IRON_INGOT, 3), new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)),
                List.of(stack(Items.GOLD_INGOT, 2), new GenericStack(AEFluidKey.of(Fluids.LAVA), 500)));
        var other = unit(helper, "other_binding", List.of(stack(Items.IRON_INGOT, 3), new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)),
                List.of(stack(Items.GOLD_INGOT, 2), new GenericStack(AEFluidKey.of(Fluids.LAVA), 500)));
        var tasks = Map.<IPatternDetails, Long>of(unit, 10_000L, other, 10_000L);
        var rewritten = PatternBatchPlanning.rewrite(tasks, snapshot(256, unit, other));
        helper.assertTrue(rewritten.equals(Map.of(unit.scaled(256), 39L, unit.scaled(16), 1L,
                        other.scaled(256), 39L, other.scaled(16), 1L)), "10000/256 keeps distinct bindings, 39 full tasks and one exact tail each");
        conserve(helper, tasks, rewritten);
        var huge = Map.<IPatternDetails, Long>of(unit, 2_147_483_647L);
        var hugePlan = PatternBatchPlanning.rewrite(huge, snapshot(2_147_483_647L, unit));
        helper.assertTrue(hugePlan.equals(Map.of(unit.scaled(2_147_483_647L), 1L)), "Huge plans do not expand per craft or truncate to int");
        conserve(helper, huge, hugePlan);
        helper.succeed();
    }

    public static void runtimeConditionsAndHooksPublishAndCompressBulkInputs(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        PatternInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(host.getMainNode().getNode() != null,
                        "Provider initializes through its native first tick"))
                .thenExecute(() -> {
                    var callbacks = new AtomicInteger();
                    var controller = bindController(helper, host, new BlockPos(0, 0, 2), 4);
                    var base = parallelMachine(4L);
                    controller.setMachine(new Machine() {
                        @Override public ResourceLocation registryName() { return base.registryName(); }
                        @Override public BlockArray pattern() { return base.pattern(); }
                        @Override public MachineControllerSpec controller() { return base.controller(); }
                        @Override public boolean parallelizable() { return true; }
                        @Override public long maxParallelism() { return 4L; }
                        @Override public RecipeBehavior behavior() {
                            return RecipeBehavior.builder().beforeStart(context -> callbacks.incrementAndGet())
                                    .recipeTick(context -> callbacks.incrementAndGet())
                                    .beforeFinish(context -> callbacks.incrementAndGet()).build();
                        }
                    });
                    controller.setFormed(true);
                    var level = MachineLevelRegistry.levelsForType(MachineLevelRegistry.types().getFirst().id()).getFirst();
                    controller.componentRuntime().replaceLevels(Map.of(level.typeId(), level));
                    var recipeId = MMCR.id("batch_conditions_and_hooks");
                    RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, MMCR.id("test_cube"), 20,
                            List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                                    new LevelRequirement(level.typeId(), level.id()), new StageRequirement(1),
                                    SmartInterfaceRequirement.input("temperature", 10F, 20F),
                                    SmartInterfaceRequirement.output("temperature", 42F),
                                    MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND, 9))),
                            List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 9), 1F)),
                            List.of(), 0, 1, false, true, false, Set.of()));
                    host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                            List.of(stack(Items.IRON_INGOT, 2)), List.of(stack(Items.DIAMOND, 9))));
                    host.getLogic().updatePatterns();
                    var published = host.getLogic().getAvailablePatterns().stream()
                            .filter(pattern -> pattern instanceof PatternBatchDetails batch && batch.data().recipeId().equals(recipeId))
                            .map(pattern -> (PatternBatchDetails) pattern).toList();
                    helper.assertFalse(published.isEmpty(), "The eligible level/stage recipe publishes its batch binding");
                    var unit = published.getFirst();
                    var snapshot = PatternBatchPlanning.capture(host.getMainNode().getGrid());
                    helper.assertTrue(snapshot.limits().get(unit.getDefinition()).maxMultiplier() == 4L,
                            "Runtime conditions and registered callbacks retain the actual single-lane parallel limit");
                    var materials = new KeyCounter();
                    materials.add(AEItemKey.of(Items.IRON_INGOT), 24L);
                    var plan = simulate(helper, List.of(unit), materials, stack(Items.DIAMOND, 100L), snapshot);
                    helper.assertFalse(plan.simulation(), "All 12 recipe operations have their inputs");
                    helper.assertTrue(plan.patternTimes().equals(Map.of(unit.scaled(4L), 3L)),
                            "100 diamonds are planned as three batches of four, not twelve unit tasks");
                    helper.assertTrue(callbacks.get() == 0, "Read-only publication and AE planning never execute runtime callbacks");
                    settle(helper, plan);
                }).thenSucceed();
    }

    public static void lightningBatchesPreserveNativePlanningAndAdmission(GameTestHelper helper) {
        lightningBatchesPreserveNativePlanningAndAdmission(helper, 1F);
    }

    public static void probabilisticLightningOutputsPreserveNativePlanningAndAdmission(GameTestHelper helper) {
        lightningBatchesPreserveNativePlanningAndAdmission(helper, 0.5F);
    }

    private static void lightningBatchesPreserveNativePlanningAndAdmission(GameTestHelper helper, float outputChance) {
        helper.assertTrue(LightningBridge.get().available(), "The optional native lightning bridge is loaded");
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 0, 0), ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        PatternInterfaceBlockEntity highHost = helper.getBlockEntity(BlockPos.ZERO);
        PatternInterfaceBlockEntity extremeHost = helper.getBlockEntity(new BlockPos(2, 0, 0));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                        highHost.getMainNode().getNode() != null && extremeHost.getMainNode().getNode() != null,
                        "Lightning providers initialize through their native first-tick lifecycle"))
                .thenExecute(() -> {
                    verifyLightningBatch(helper, highHost, new BlockPos(0, 0, 2), LightningTier.HIGH_VOLTAGE,
                            LightningTier.EXTREME_HIGH_VOLTAGE, outputChance);
                    verifyLightningBatch(helper, extremeHost, new BlockPos(2, 0, 2), LightningTier.EXTREME_HIGH_VOLTAGE,
                            LightningTier.HIGH_VOLTAGE, outputChance);
                }).thenSucceed();
    }

    public static void onlyIntermediateInputProbabilityKeepsNativeUnitTasks(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        PatternInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(host.getMainNode().getNode() != null,
                        "Provider initializes through its native first tick"))
                .thenExecute(() -> {
                    bindController(helper, host, new BlockPos(0, 0, 2), 4);
                    for (float consumeChance : new float[]{0F, 0.5F, 1F}) {
                        for (float outputChance : new float[]{0F, 0.5F, 1F}) {
                            var recipeId = MMCR.id("batch_probability_" + (int) (consumeChance * 100) + "_" + (int) (outputChance * 100));
                            var output = new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), outputChance);
                            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, MMCR.id("test_cube"), 20,
                                    List.of(new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY,
                                                    1F, List.of(), DataComponentPredicateSet.EMPTY, consumeChance),
                                            OutputRegistry.toRequirement(output, List.of())), List.of(output),
                                    List.of(), 0, 1, false, true, false, Set.of()));
                            host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                                    List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1))));
                            host.getLogic().updatePatterns();
                            var unit = host.getLogic().getAvailablePatterns().stream()
                                    .filter(pattern -> pattern instanceof PatternBatchDetails batch && batch.data().recipeId().equals(recipeId))
                                    .map(pattern -> (PatternBatchDetails) pattern).findFirst().orElseThrow();
                            var limits = PatternBatchPlanning.capture(host.getMainNode().getGrid());
                            var materials = new KeyCounter();
                            materials.add(AEItemKey.of(Items.IRON_INGOT), 8L);
                            var plan = simulate(helper, List.of(unit), materials, stack(Items.GOLD_INGOT, 8L), limits);
                            helper.assertFalse(plan.simulation(), "Native AE has all nominal materials");
                            var expected = consumeChance == 0.5F ? Map.of(unit, 8L) : Map.of(unit.scaled(4L), 2L);
                            helper.assertTrue(plan.patternTimes().equals(expected),
                                    "Only intermediate input consumption rolls back to unit tasks: input="
                                            + consumeChance + ", output=" + outputChance);
                            settle(helper, plan);
                        }
                    }
                }).thenSucceed();
    }

    private static void verifyLightningBatch(GameTestHelper helper, PatternInterfaceBlockEntity host,
                                             BlockPos controllerPos, LightningTier inputTier, LightningTier outputTier, float outputChance) {
        var recipeId = MMCR.id("batch_planning_lightning_" + inputTier.requirementId().getPath() + "_" + (int) (outputChance * 100));
        var inputKey = LoadedLightningBridge.key(inputTier);
        var outputKey = LoadedLightningBridge.key(outputTier);
        helper.assertTrue(!inputKey.equals(outputKey)
                        && LightningBridge.get().patternOutput(inputKey, 1L).orElseThrow() instanceof LightningOutput input
                        && input.tier() == inputTier
                        && LightningBridge.get().patternOutput(outputKey, 1L).orElseThrow() instanceof LightningOutput output
                        && output.tier() == outputTier,
                "The native key adapter preserves distinct HV/EHV identities through the optional bridge");
        List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 3), 1F),
                new LightningOutput(outputTier, 1_000_000_000L, outputChance));
        var requirements = new ArrayList<MachineRequirement>(List.of(
                MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                LightningRequirement.input(inputTier, Integer.MAX_VALUE)));
        outputs.forEach(output -> requirements.add(OutputRegistry.toRequirement(output, List.of())));
        var recipe = MachineRecipe.fromCanonical(recipeId, MMCR.id("test_cube"), 20, requirements, outputs,
                List.of(), 0, 1, false, true, false, Set.of());
        RecipeRegistry.registerStatic(recipe);
        var controller = bindController(helper, host, controllerPos, 256);
        var components = new ArrayList<>(controller.componentRuntime().components());
        components.add(new ProcessingComponent(null, host, host.getBlockPos(),
                host.getBlockPos().subtract(controller.getBlockPos()), List.of()));
        controller.componentRuntime().replaceComponents(components);
        helper.assertTrue(StructureClaimRegistry.get(helper.getLevel()).claim(controller.getBlockPos(), List.of(
                        new StructureClaimRegistry.Claim(host.getBlockPos(), ComponentClaimPolicy.EXCLUSIVE))).accepted(),
                "The admission fixture owns a real controller resource domain and attached pattern component");
        long patternInput = Math.multiplyExact((long) Integer.MAX_VALUE, 2L);
        long patternOutput = Math.multiplyExact(1_000_000_000L, 2L);
        var physical = PatternDetailsHelper.encodeProcessingPattern(
                List.of(stack(Items.IRON_INGOT, 4L), new GenericStack(inputKey, patternInput)),
                List.of(stack(Items.GOLD_INGOT, 6L), new GenericStack(outputKey, patternOutput)));
        host.getLogic().getPatternInv().setItemDirect(0, physical);
        host.getLogic().updatePatterns();
        var unit = host.getLogic().getAvailablePatterns().stream()
                .filter(pattern -> pattern instanceof PatternBatchDetails batch && batch.data().recipeId().equals(recipeId))
                .map(pattern -> (PatternBatchDetails) pattern).findFirst().orElseThrow();
        var limits = PatternBatchPlanning.capture(host.getMainNode().getGrid());
        var limit = limits.limits().get(unit.getDefinition());
        helper.assertTrue(unit.data().recipeCraftsPerPattern() == 2L && limit != null
                        && limit.safety() == Safety.PROVEN_LINEAR && limit.maxMultiplier() == 128L,
                "Deterministic lightning binds two recipe crafts per physical pattern and one 256-parallel lane");
        var materials = new KeyCounter();
        materials.add(AEItemKey.of(Items.IRON_INGOT), Math.multiplyExact(4L, 10_000L));
        materials.add(inputKey, Math.multiplyExact(patternInput, 10_000L));
        var requested = stack(Items.GOLD_INGOT, Math.multiplyExact(6L, 10_000L));
        var normal = simulate(helper, List.of(unit), materials, requested, Snapshot.EMPTY);
        var batched = simulate(helper, List.of(unit), materials, requested, limits);
        helper.assertTrue(!normal.simulation() && !batched.simulation()
                        && normal.patternTimes().equals(Map.of(unit, 10_000L))
                        && batched.patternTimes().equals(Map.of(unit.scaled(128L), 78L, unit.scaled(16L), 1L)),
                "Native lightning planning compresses 10000 tasks to 78 full batches and one exact tail with k=2");
        conserve(helper, normal.patternTimes(), batched.patternTimes());
        helper.assertTrue(counter(normal.usedItems()).equals(counter(batched.usedItems()))
                        && normal.bytes() == batched.bytes() && normal.finalOutput().equals(batched.finalOutput())
                        && counter(normal.emittedItems()).equals(counter(batched.emittedItems()))
                        && counter(normal.missingItems()).equals(counter(batched.missingItems())),
                "Compression preserves native lightning materials, bytes, emissions, missing items and final output");
        settle(helper, batched);
        var batch = unit.scaled(128L);
        var saved = batch.getDefinition().toTag(helper.getLevel().registryAccess());
        var restoredKey = AEItemKey.fromTag(helper.getLevel().registryAccess(), saved);
        var restored = PatternDetailsHelper.decodePattern(restoredKey, helper.getLevel());
        helper.assertTrue(batch.equals(restored) && restored.getOutputs().equals(batch.getOutputs())
                        && restored.getOutputs().contains(new GenericStack(outputKey, 256_000_000_000L)),
                "Registry-aware saved definition decoding restores the exact long lightning protocol");
        var inventory = new ListCraftingInventory(key -> { });
        inventory.insert(AEItemKey.of(Items.IRON_INGOT), Math.multiplyExact(4L, 128L), Actionable.MODULATE);
        inventory.insert(inputKey, Math.multiplyExact(patternInput, 128L), Actionable.MODULATE);
        var expectedOutputs = new KeyCounter();
        var holders = CraftingCpuHelper.extractPatternInputs(restored, inventory, helper.getLevel(), expectedOutputs, new KeyCounter());
        helper.assertTrue(holders != null && inventory.list.isEmpty(), "Native CPU extraction consumes the entire long-amount request");
        var extracted = new KeyCounter();
        for (var holder : holders) extracted.addAll(holder);
        helper.assertTrue(extracted.get(inputKey) == 549_755_813_632L && extracted.get(outputKey) == 0L
                        && extracted.get(AEItemKey.of(Items.IRON_INGOT)) == 512L
                        && expectedOutputs.get(outputKey) == 256_000_000_000L && expectedOutputs.get(inputKey) == 0L,
                "Native extraction preserves exact input/output tier identities and quantities beyond int");
        SharedIoCoordinator.get(helper.getLevel()).markControllerEligible(controller, helper.getLevel().getGameTime());
        // The target host builds one GenericStackInv shared by item/fluid and LightningBridge request capabilities.
        helper.assertTrue(host.craftingMachine().pushPattern(restored, holders, null),
                "Real controller admission pays one complete long-amount lightning batch through native shared request ownership");
        for (var holder : holders) helper.assertTrue(holder.isEmpty(), "Successful admission claims each CPU input holder exactly once");
        helper.assertTrue(controller.runtimeSnapshot().crafting().parallelism() == 256L
                        && recipeId.equals(controller.runtimeSnapshot().crafting().recipeId())
                        && host.pendingPatterns().isEmpty() && host.getLogic().getReturnInv().isEmpty(),
                "Two crafts per pattern execute on one exact 256-parallel lane without a private queue or spurious leftovers");
    }

    public static void reusedMoldAndCyclicDependencyRemainExecutable(GameTestHelper helper) {
        var mold = unit(helper, "mold", List.of(stack(Items.IRON_INGOT, 1), stack(Items.STICK, 1)),
                List.of(stack(Items.GOLD_INGOT, 1), stack(Items.STICK, 1)));
        var downstream = unit(helper, "mold_downstream", List.of(stack(Items.GOLD_INGOT, 1)), List.of(stack(Items.DIAMOND, 1)));
        var materials = new KeyCounter();
        materials.add(AEItemKey.of(Items.IRON_INGOT), 10);
        materials.add(AEItemKey.of(Items.STICK), 1);
        var plan = simulate(helper, List.of(mold, downstream), materials, stack(Items.DIAMOND, 10), snapshot(256, mold, downstream));
        helper.assertTrue(!plan.simulation() && plan.patternTimes().equals(Map.of(mold, 10L, downstream, 10L)),
                "One reused mold remains executable at unit scale throughout its connected component");
        settle(helper, plan);
        var a = unit(helper, "cycle_a", List.of(stack(Items.COAL, 1)), List.of(stack(Items.REDSTONE, 10), stack(Items.CHARCOAL, 1)));
        var b = unit(helper, "cycle_b", List.of(stack(Items.CHARCOAL, 1)), List.of(stack(Items.LAPIS_LAZULI, 10), stack(Items.COAL, 1)));
        var neighbor = unit(helper, "cycle_neighbor", List.of(stack(Items.REDSTONE, 1), stack(Items.LAPIS_LAZULI, 1)), List.of(stack(Items.EMERALD, 1)));
        var seeds = new KeyCounter();
        seeds.add(AEItemKey.of(Items.COAL), 1);
        seeds.add(AEItemKey.of(Items.CHARCOAL), 1);
        var cycle = simulate(helper, List.of(a, b, neighbor), seeds, stack(Items.EMERALD, 10), snapshot(256, a, b, neighbor));
        helper.assertTrue(!cycle.simulation() && cycle.patternTimes().equals(Map.of(a, 1L, b, 1L, neighbor, 10L)),
                "Seeded byproduct cycles and connected consumers stay at unit scale");
        settle(helper, cycle);
        helper.succeed();
    }

    public static void acyclicUpstreamAndByproductsKeepUsedItems(GameTestHelper helper) {
        var a = unit(helper, "dag_a", List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1), stack(Items.GOLD_NUGGET, 1)));
        var b = unit(helper, "dag_b", List.of(stack(Items.GOLD_INGOT, 1), stack(Items.GOLD_NUGGET, 1)),
                List.of(stack(Items.DIAMOND, 1), stack(Items.EMERALD, 1)));
        var materials = new KeyCounter();
        materials.add(AEItemKey.of(Items.IRON_INGOT), 10_000);
        var normal = simulate(helper, List.of(a, b), materials, stack(Items.DIAMOND, 10_000), Snapshot.EMPTY);
        var batched = simulate(helper, List.of(a, b), materials, stack(Items.DIAMOND, 10_000), snapshot(256, a, b));
        helper.assertTrue(!normal.simulation() && !batched.simulation() && normal.bytes() == batched.bytes()
                        && counter(normal.usedItems()).equals(counter(batched.usedItems()))
                        && counter(normal.emittedItems()).equals(counter(batched.emittedItems()))
                        && normal.finalOutput().equals(batched.finalOutput()) && batched.patternTimes().size() == 4,
                "Actual native plan builder changes only tasks, not bytes, usedItems, emissions or final output");
        conserve(helper, normal.patternTimes(), batched.patternTimes());
        settle(helper, batched);
        helper.succeed();
    }

    public static void unknownRequirementsPoisonOnlyEnumerableComponents(GameTestHelper helper) {
        var unknown = unit(helper, "unknown", List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
        var neighbor = unit(helper, "neighbor", List.of(stack(Items.GOLD_INGOT, 1)), List.of(stack(Items.DIAMOND, 1)));
        var isolated = unit(helper, "isolated", List.of(stack(Items.COAL, 1)), List.of(stack(Items.EMERALD, 1)));
        var limits = new HashMap<>(snapshot(256, unknown, neighbor, isolated).limits());
        limits.put(unknown.getDefinition(), new BatchLimit(256, Safety.UNPROVEN));
        var tasks = Map.<IPatternDetails, Long>of(unknown, 1000L, neighbor, 1000L, isolated, 1000L);
        var rewritten = PatternBatchPlanning.rewrite(tasks, new Snapshot(limits));
        helper.assertTrue(rewritten.equals(Map.of(unknown, 1000L, neighbor, 1000L, isolated.scaled(256), 3L, isolated.scaled(232), 1L)),
                "Unproven semantics poison only the enumerable connected component");
        conserve(helper, tasks, rewritten);
        IPatternDetails opaque = new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return unknown.original().getDefinition(); }
            @Override public IInput[] getInputs() { return unknown.original().getInputs(); }
            @Override public List<GenericStack> getOutputs() { return unknown.original().getOutputs(); }
        };
        var opaqueTasks = Map.<IPatternDetails, Long>of(opaque, 10L, isolated, 1000L);
        helper.assertTrue(PatternBatchPlanning.rewrite(opaqueTasks, new Snapshot(limits)).equals(opaqueTasks),
                "Opaque custom input protocols cannot prove a complete dependency graph");
        helper.succeed();
    }

    public static void planningDoesNotTouchLiveWorldOrInvokeHooks(GameTestHelper helper) {
        var unit = unit(helper, "concurrent", List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
        var mutable = new HashMap<>(snapshot(256, unit).limits());
        var captured = new Snapshot(mutable);
        mutable.put(unit.getDefinition(), new BatchLimit(64, Safety.UNPROVEN));
        var tasks = Map.<IPatternDetails, Long>of(unit, 10_000L);
        var first = CompletableFuture.supplyAsync(() -> PatternBatchPlanning.rewrite(tasks, captured));
        var second = CompletableFuture.supplyAsync(() -> PatternBatchPlanning.rewrite(tasks, new Snapshot(mutable)));
        helper.assertTrue(first.join().equals(Map.of(unit.scaled(256), 39L, unit.scaled(16), 1L)) && second.join().equals(tasks),
                "Concurrent workers use immutable data-only snapshots and never consult a live host");
        helper.succeed();
    }

    public static void mixedProvidersNeverDispatchBatchToVanilla(GameTestHelper helper) {
        var unit = unit(helper, "routing", List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
        var other = unit(helper, "other_routing", List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
        helper.assertTrue(PatternBatchPlanning.providerKey(unit.scaled(256)).equals(unit)
                        && !PatternBatchPlanning.providerKey(unit.scaled(256)).equals(unit.original())
                        && !PatternBatchPlanning.providerKey(unit.scaled(256)).equals(other)
                        && PatternBatchPlanning.providerKey(unit.original()) == unit.original(),
                "Scaled tasks route to their exact MMCR unit binding while bare native lookups remain unchanged");
        helper.succeed();
    }

    public static void exactChemicalsCompressWithoutMergingDifferentIds(GameTestHelper helper) {
        helper.assertTrue(AppMekBridge.get().available(), "AppMek bridge is loaded for chemical analysis");
        var oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1));
        var hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1));
        var chlorine = MekanismKey.of(AppMekGameTestFixtures.chemical("chlorine", 1));
        ResourceLocation oxygenId = ResourceLocation.parse("mekanism:oxygen");
        ResourceLocation hydrogenId = ResourceLocation.parse("mekanism:hydrogen");
        ResourceLocation chlorineId = ResourceLocation.parse("mekanism:chlorine");
        var input = LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygenId, 5));
        List<MachineOutput> outputs = List.of(new LoadedChemicalOutput(hydrogenId, 3, 1F), new LoadedChemicalOutput(chlorineId, 2, 1F));
        var recipe = MachineRecipe.fromCanonical(MMCR.id("chemical_batch_analysis"), MMCR.id("test_cube"), 20,
                List.of(input), outputs, List.of(), 0, 1, false, true, false, Set.of());
        var effective = new EffectiveRecipe(recipe, List.of(input), outputs, 20, 256, 1, 1, true, 0, 0, 0, 0);
        var unit = unit(helper, "chemical", List.of(new GenericStack(oxygen, 5)),
                List.of(new GenericStack(hydrogen, 3), new GenericStack(chlorine, 2)));
        var profile = PatternBatchSupport.inspectRecipe(recipe, effective, unit.original());
        helper.assertTrue(profile != null && profile.safety() == Safety.PROVEN_LINEAR,
                "Exact deterministic chemical IDs can prove linear even with two different chemical outputs");
        var tasks = Map.<IPatternDetails, Long>of(unit, 10_000L);
        var rewritten = PatternBatchPlanning.rewrite(tasks, snapshot(profile.maxMultiplier(), unit));
        helper.assertTrue(rewritten.equals(Map.of(unit.scaled(256), 39L, unit.scaled(16), 1L)),
                "An acyclic exact chemical order uses the same full/tail compression protocol");
        conserve(helper, tasks, rewritten);
        var wrong = unit(helper, "wrong_chemical", List.of(new GenericStack(hydrogen, 5)), unit.original().getOutputs());
        helper.assertTrue(PatternBatchSupport.inspectRecipe(recipe, effective, wrong.original()) == null,
                "The bridge matches the actual chemical input identity, not a shared type or empty aggregation key");
        var mismatched = unit(helper, "wrong_output", List.of(new GenericStack(oxygen, 5)),
                List.of(new GenericStack(hydrogen, 2), new GenericStack(chlorine, 3)));
        helper.assertTrue(PatternBatchSupport.inspectRecipe(recipe, effective, mismatched.original()) == null,
                "Different chemical quantities cannot be merged to disguise conflicting output ratios");
        var tag = LoadedChemicalRequirement.input(ChemicalIngredient.tag(MMCR.id("unresolved_chemical_tag"), 5));
        var tagged = PatternBatchSupport.inspectRecipe(recipe,
                new EffectiveRecipe(recipe, List.of(tag), outputs, 20, 256, 1, 1, true, 0, 0, 0, 0), unit.original());
        helper.assertTrue(tagged != null && tagged.safety() == Safety.PROVEN_LINEAR,
                "Chemical tag matching stays with the actual runtime handler rather than disabling bulk delivery");
        for (float consumeChance : new float[]{0F, 0.5F, 1F}) {
            for (float outputChance : new float[]{0F, 0.5F, 1F}) {
                var probabilistic = new LoadedChemicalRequirement(IOType.INPUT, input.ingredient(), 1F, List.of(), consumeChance);
                var chanceOutputs = outputs.stream().map(output -> output.withChance(outputChance)).toList();
                var view = new EffectiveRecipe(recipe, List.of(probabilistic), chanceOutputs, 20, 256, 1, 1, true, 0, 0, 0, 0);
                var fallback = PatternBatchSupport.inspectRecipe(recipe, view, unit.original());
                var safety = consumeChance == 0.5F ? Safety.UNPROVEN : Safety.PROVEN_LINEAR;
                helper.assertTrue(fallback != null && fallback.safety() == safety,
                        "Only intermediate chemical consumption disables compression; output probability does not");
                var limits = new Snapshot(Map.of(unit.getDefinition(), new BatchLimit(fallback.maxMultiplier(), safety)));
                helper.assertTrue(PatternBatchPlanning.rewrite(tasks, limits).equals(consumeChance == 0.5F ? tasks : rewritten),
                        "Native chemical task totals use the probability-aware batch profile");
                helper.assertTrue(CraftingRuntime.samePromisedOutputs(chanceOutputs, chanceOutputs, 256L)
                                && !CraftingRuntime.samePromisedOutputs(chanceOutputs,
                                List.of(new LoadedChemicalOutput(hydrogenId, 3, outputChance == 1F ? 0.5F : 1F),
                                        new LoadedChemicalOutput(chlorineId, 2, outputChance)), 256L)
                                && !CraftingRuntime.samePromisedOutputs(chanceOutputs,
                                List.of(new LoadedChemicalOutput(oxygenId, 3, outputChance),
                                        new LoadedChemicalOutput(chlorineId, 2, outputChance)), 256L),
                        "Batch promises retain chemical identity and probability after callbacks");
            }
        }
        helper.succeed();
    }

    private static MachineControllerBlockEntity bindController(GameTestHelper helper, PatternInterfaceBlockEntity host, BlockPos position, int parallelism) {
        helper.setBlock(position, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(position);
        controller.setMachine(parallelMachine(parallelism));
        var parallelPos = position.above();
        helper.setBlock(parallelPos, ModBlocks.BLOCKS.get("parallel_controller_pro").get().defaultBlockState());
        ParallelControllerBlockEntity parallel = helper.getBlockEntity(parallelPos);
        parallel.setCurrentParallelism(parallelism);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, parallel, parallelPos, BlockPos.ZERO, List.of())));
        host.linkControllerAppearance(controller.getBlockPos(), null);
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(host.getBlockPos()));
        controller.setFormed(true);
        return controller;
    }

    private static DynamicMachine parallelMachine(long parallelism) {
        var id = MMCR.id("test_cube");
        return new DynamicMachine(id, "runtime test", new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(id),
                MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), parallelism, true, false, 1);
    }

    private static GenericStack stack(Item item, long amount) { return new GenericStack(AEItemKey.of(item), amount); }

    private static PatternBatchDetails unit(GameTestHelper helper, String id, List<GenericStack> inputs, List<GenericStack> outputs) {
        var original = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs, outputs), helper.getLevel());
        helper.assertTrue(original != null, "Native processing pattern decodes");
        return new PatternBatchDetails(original, new PatternBatchData(1, MMCR.id("batch_planning_test"), MMCR.id(id), 1, 1));
    }

    private static Snapshot snapshot(long maximum, PatternBatchDetails... units) {
        Map<AEItemKey, BatchLimit> limits = new HashMap<>();
        for (var unit : units) limits.put(unit.getDefinition(), new BatchLimit(maximum, Safety.PROVEN_LINEAR));
        return new Snapshot(limits);
    }

    private static void conserve(GameTestHelper helper, Map<IPatternDetails, Long> before, Map<IPatternDetails, Long> after) {
        helper.assertTrue(weighted(before, false).equals(weighted(after, false)) && weighted(before, true).equals(weighted(after, true)),
                "Every resource's weighted input and promised output is exactly conserved");
    }

    private static Map<AEKey, Long> weighted(Map<IPatternDetails, Long> tasks, boolean outputs) {
        Map<AEKey, Long> totals = new HashMap<>();
        tasks.forEach((pattern, count) -> {
            if (outputs) for (var output : pattern.getOutputs()) totals.merge(output.what(), Math.multiplyExact(output.amount(), count), Math::addExact);
            else for (var input : pattern.getInputs()) {
                var template = input.getPossibleInputs()[0];
                totals.merge(template.what(), Math.multiplyExact(Math.multiplyExact(template.amount(), input.getMultiplier()), count), Math::addExact);
            }
        });
        return totals;
    }

    private static Map<AEKey, Long> counter(KeyCounter values) {
        Map<AEKey, Long> result = new HashMap<>();
        for (var entry : values) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }

    private static CraftingPlan simulate(GameTestHelper helper, List<? extends IPatternDetails> patterns, KeyCounter materials, GenericStack output, Snapshot snapshot) {
        var crafting = (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(), new Class<?>[]{ICraftingService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCraftingFor" -> patterns.stream().filter(pattern -> pattern.getPrimaryOutput().what().equals(args[0])).toList();
                    case "canEmitFor" -> false;
                    case "getFuzzyCraftable" -> null;
                    default -> throw new IllegalStateException("Unexpected simulation crafting call: " + method);
                });
        var storage = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(), new Class<?>[]{IStorageService.class}, (proxy, method, args) -> {
            if (method.getName().equals("getCachedInventory")) return materials;
            throw new IllegalStateException("Unexpected simulation storage call: " + method);
        });
        var grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getStorageService" -> storage;
            case "getCraftingService" -> crafting;
            default -> throw new IllegalStateException("Unexpected live grid call: " + method);
        });
        var node = (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class}, (proxy, method, args) -> {
            if (method.getName().equals("getGrid")) return grid;
            throw new IllegalStateException("Unexpected live node call: " + method);
        });
        var requester = new ICraftingSimulationRequester() {
            @Override public IActionSource getActionSource() { return null; }
            @Override public IGridNode getGridNode() { return node; }
        };
        var calculation = new TestCalculation(helper.getLevel(), grid, requester, output, snapshot);
        Field vanillaRoute = null;
        boolean previousRoute = false;
        try {
            // These tests explicitly exercise AE's vanilla tree builder (including cycles/molds),
            // not Thunderbolt's alternate engines. Select only this disposable calculation;
            // never change a global mod/config route and restore even when native planning fails.
            if (ModList.get().isLoaded("thunderbolt")) {
                vanillaRoute = CraftingCalculation.class.getDeclaredField("thunderbolt$activeVanilla");
                vanillaRoute.setAccessible(true);
                previousRoute = vanillaRoute.getBoolean(calculation);
                vanillaRoute.setBoolean(calculation, true);
            }
            var increment = CraftingCalculation.class.getDeclaredField("incTime");
            increment.setAccessible(true);
            increment.setInt(calculation, Integer.MIN_VALUE);
            var compute = CraftingCalculation.class.getDeclaredMethod("computePlan");
            compute.setAccessible(true);
            return (CraftingPlan) compute.invoke(calculation);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Native AE calculation failed: "
                    + (exception.getCause() == null ? exception : exception.getCause()));
        } finally {
            if (vanillaRoute != null) {
                try { vanillaRoute.setBoolean(calculation, previousRoute); }
                catch (IllegalAccessException exception) { throw new GameTestAssertException("Cannot restore vanilla route: " + exception); }
            }
        }
    }

    private static void settle(GameTestHelper helper, CraftingPlan plan) {
        var inventory = new ListCraftingInventory(key -> { });
        for (var entry : plan.usedItems()) inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        var remaining = new LinkedHashMap<>(plan.patternTimes());
        while (!remaining.isEmpty()) {
            boolean progress = false;
            for (var pattern : new ArrayList<>(remaining.keySet())) {
                var outputs = new KeyCounter();
                var containers = new KeyCounter();
                if (CraftingCpuHelper.extractPatternInputs(pattern, inventory, helper.getLevel(), outputs, containers) == null) continue;
                for (var entry : outputs) inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                for (var entry : containers) inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                long count = remaining.get(pattern) - 1;
                if (count == 0) remaining.remove(pattern);
                else remaining.put(pattern, count);
                progress = true;
            }
            helper.assertTrue(progress, "Remaining tasks execute using only original usedItems");
        }
        helper.assertTrue(inventory.extract(plan.finalOutput().what(), plan.finalOutput().amount(), Actionable.SIMULATE) == plan.finalOutput().amount(),
                "All native simulated tasks settle the exact final output");
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class TestCalculation extends CraftingCalculation implements PatternBatchPlanning.SnapshotSource {
        private final Snapshot snapshot;
        private TestCalculation(Level level, IGrid grid, ICraftingSimulationRequester requester, GenericStack output, Snapshot snapshot) {
            super(level, grid, requester, output, CalculationStrategy.REPORT_MISSING_ITEMS);
            this.snapshot = snapshot;
        }
        @Override public Snapshot mmcr$batchSnapshot() { return snapshot; }
    }
}
