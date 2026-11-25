package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** A stock 64K crafting CPU, the real interface, and observable long-count ME storage.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
final class NativePatternCpuFixture {
    static final BlockPos PORT = new BlockPos(0, 1, 0);
    static final BlockPos ENERGY = new BlockPos(4, 0, 0);
    static final BlockPos CPU = new BlockPos(4, 0, 3);
    static final BlockPos BUS = new BlockPos(0, 0, 2);

    final GameTestHelper helper;
    final ResourceLocation recipeId;
    final AEKey input;
    final AEKey output;
    final long total;
    final DynamicMachine machine;
    PatternInterfaceBlockEntity host;
    MachineControllerBlockEntity controller;
    CreativeEnergyCellBlockEntity energy;
    final LongStorage storage = new LongStorage();
    final CountingProvider provider;
    IGridConnection hostConnection;
    Future<ICraftingPlan> calculation;
    ICraftingPlan plan;

    NativePatternCpuFixture(GameTestHelper helper, String name, boolean fluid, int unitAmount,
                            long crafts, ParallelTier tier) {
        this(helper, name, fluid, unitAmount, crafts, tier, true);
    }

    NativePatternCpuFixture(GameTestHelper helper, String name, boolean fluid, int unitAmount,
                            long crafts, ParallelTier tier, boolean countPushes) {
        this.helper = helper;
        total = Math.multiplyExact(crafts, unitAmount);
        input = fluid ? AEFluidKey.of(Fluids.WATER) : AEItemKey.of(Items.IRON_INGOT);
        output = fluid ? AEFluidKey.of(Fluids.LAVA) : AEItemKey.of(Items.GOLD_INGOT);
        ResourceLocation machineId = MMCR.id("hosted_cpu_" + name);
        recipeId = MMCR.id("hosted_cpu_" + name + "_recipe");
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(PORT, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(1, 0, 0), ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 0, 0), ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        helper.setBlock(new BlockPos(0, 0, 1), ModBlocks.BLOCKS.get(tier.idSuffix()).get().defaultBlockState());
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());

        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>(Map.of(
                PORT, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get()),
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()),
                new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(tier.idSuffix()).get())));
        var bus = ModBlocks.BLOCKS.get("item_input_bus").get();
        helper.setBlock(BUS, bus.defaultBlockState());
        pattern.put(BUS, new BlockPredicate.OfBlock(bus));
        machine = new DynamicMachine(machineId, "block.mmcr.test_cube", new BlockArray(pattern),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                tier.maxParallelism(), true, true, 2);
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        if (!RecipeRegistry.containsStatic(recipeId)) {
            var ingredient = fluid
                    ? new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), unitAmount)
                    : new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), unitAmount);
            MachineRequirement product = fluid
                    ? MachineRequirement.fluidOutput(new FluidStack(Fluids.LAVA, unitAmount))
                    : MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT, unitAmount));
            var requirements = List.of(MachineRequirement.fromInput(ingredient), product);
            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 40,
                    requirements, List.of(), List.of(),
                    0, 2, false, true, false, Set.of()));
        }
        controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        host = helper.getBlockEntity(PORT);
        provider = new CountingProvider(host.getLogic());
        // Install before ready-time node creation, retaining the real MMCR host and logic.
        if (countPushes) host.getMainNode().addService(ICraftingProvider.class, provider);
        energy = helper.getBlockEntity(ENERGY);
        energy.getMainNode().addService(IStorageProvider.class, mounts -> mounts.mount(storage, 0));
        // Two same-key slots prove aggregation without building an enormous ItemStack.
        if (total > 1) storage.setStack(0, new GenericStack(input, total / 2));
        storage.setStack(1, new GenericStack(input, total - total / 2));
        host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                unitAmount == 2 ? List.of(new GenericStack(input, 1), new GenericStack(input, 1))
                        : List.of(new GenericStack(input, 1)),
                List.of(new GenericStack(output, unitAmount))));
    }

    GameTestSequence ready() {
        return helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                host.getMainNode().getNode() != null && energy.getMainNode().getNode() != null
                        && cpuBlock().getMainNode().getNode() != null, "Native AE2 nodes initialize"))
                .thenExecute(() -> {
                    GridHelper.createConnection(energy.getMainNode().getNode(), cpuBlock().getMainNode().getNode());
                    reconnectHost();
                    controller.requestImmediateStructureCheck();
                }).thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed()
                                && controller.factorySchedulerThreadCount() >= 2
                                && host.getMainNode().isActive() && cluster() != null && cluster().isActive(),
                        "The actual factory, two lanes and native crafting CPU become available"))
                .thenExecute(() -> host.getLogic().updatePatterns())
                .thenWaitUntil(() -> helper.assertTrue(grid().getCraftingService().isCraftable(output),
                        "The grid discovers the real interface pattern"));
    }

    GameTestSequence calculate() {
        return ready()
                .thenExecute(() -> calculation = grid().getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), new ICraftingSimulationRequester() {
                            @Override public IActionSource getActionSource() { return IActionSource.ofMachine(host); }
                            @Override public IGridNode getGridNode() { return host.getMainNode().getNode(); }
                        }, output, total, CalculationStrategy.REPORT_MISSING_ITEMS))
                .thenWaitUntil(() -> helper.assertTrue(calculation.isDone(), "Asynchronous native plan completes"))
                .thenExecute(() -> {
                    try {
                        plan = calculation.get(); // isDone was observed; never blocks the server for planning.
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted obtaining completed crafting plan", exception);
                    } catch (ExecutionException exception) {
                        throw new AssertionError("Native crafting calculation failed", exception.getCause());
                    }
                    helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty()
                                    && plan.usedItems().get(input) == total && plan.emittedItems().isEmpty(),
                            "The actual plan uses all and only the stocked material without emitted substitutes");
                });
    }

    void submit() {
        helper.assertTrue(grid().getCraftingService().submitJob(plan, null, cluster(), false,
                        IActionSource.ofMachine(host)).successful(), "Native CPU accepts the actual calculated plan");
        cpu().setJobSuspended(true);
        helper.assertTrue(cpu().getStored(input) == total && stored(input) == 0
                        && cpu().getWaitingFor(output) == 0 && cpu().getPendingOutputs(output) == total,
                "The CPU, not the interface, owns every un-dispatched unit");
    }

    void assertSettled(long pushes) {
        assertSettled();
        helper.assertTrue(provider.accepted == pushes, "Only the planned standard deliveries were accepted");
    }

    void assertSettled() {
        helper.assertTrue(!cpu().hasJob() && cpu().getWaitingFor(output) == 0
                        && cpu().getStored(input) == 0 && stored(input) == 0 && stored(output) == total
                        && host.hostedPatterns().isEmpty() && host.getLogic().getReturnInv().isEmpty()
                        && controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                "Actual recipe output drains through the native return inventory and settles the CPU exactly; job="
                        + cpu().hasJob() + ", waiting=" + cpu().getWaitingFor(output) + ", input=" + stored(input)
                        + ", output=" + stored(output) + ", returns=" + returnAmount(output)
                        + ", factory=" + controller.runtimeSnapshot().factory());
    }

    long stored(AEKey key) { return storage.getAvailableStacks().get(key); }
    IGrid grid() { return host.getMainNode().getNode().getGrid(); }
    CraftingBlockEntity cpuBlock() { return helper.getBlockEntity(CPU); }
    CraftingCPUCluster cluster() { return cpuBlock().getCluster(); }
    CraftingCpuLogic cpu() { return cluster().craftingLogic; }

    void reconnectHost() {
        hostConnection = GridHelper.createConnection(host.getMainNode().getNode(), energy.getMainNode().getNode());
    }

    void disconnectHost() { hostConnection.destroy(); }

    long returnAmount(AEKey key) { return host.getLogic().getReturnInv().getAvailableStacks().get(key); }

    /** Fixed-slot, long-count external ME storage, never generates recipe outputs itself.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    static final class LongStorage extends GenericStackInv {
        long insertionLimit = Long.MAX_VALUE;
        LongStorage() { super(null, 4); }
        @Override public long getMaxAmount(AEKey key) { return Long.MAX_VALUE; }
        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return super.insert(what, Math.min(amount, Math.max(0, insertionLimit - getAvailableStacks().get(what))), mode, source);
        }
    }

    /** Transparent node-service proxy; counts standard CPU calls and delegates real acceptance.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    static final class CountingProvider implements ICraftingProvider {
        private final ICraftingProvider delegate;
        long accepted;
        CountingProvider(ICraftingProvider delegate) { this.delegate = delegate; }
        @Override public List<IPatternDetails> getAvailablePatterns() { return delegate.getAvailablePatterns(); }
        @Override public int getPatternPriority() { return delegate.getPatternPriority(); }
        @Override public Set<AEKey> getEmitableItems() { return delegate.getEmitableItems(); }
        @Override public boolean isBusy() { return delegate.isBusy(); }
        @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
            boolean result = delegate.pushPattern(details, inputs);
            if (result) accepted++;
            return result;
        }
    }

}
