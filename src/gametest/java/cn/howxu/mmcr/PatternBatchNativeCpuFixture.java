package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
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
import appeng.api.networking.energy.IEnergyService;
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
import appeng.me.service.CraftingService;
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
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Real native AE planning, CPU execution and machine ticks; only storage capacity is test-owned.
 * @author howxu <dev@howxu.cn>
 */
final class PatternBatchNativeCpuFixture {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0);
    static final BlockPos PORT = new BlockPos(0, 2, 0);
    static final BlockPos ENERGY = new BlockPos(4, 1, 0);
    static final BlockPos CPU = new BlockPos(4, 1, 3);
    static final BlockPos FE_PORT = new BlockPos(0, 1, 2);
    static final long INT_MAX = 2_147_483_647L;
    static final long DOUBLE_INT_MAX = 4_294_967_294L;

    final GameTestHelper helper;
    final ResourceLocation recipeId;
    final AEKey input;
    final AEKey output;
    final long total;
    final DynamicMachine machine;
    final BlockPos cpuPosition;
    final long feBudget;
    PatternInterfaceBlockEntity host;
    MachineControllerBlockEntity controller;
    CreativeEnergyCellBlockEntity energy;
    LongStorage storage = new LongStorage();
    final CountingProvider provider;
    final EnergyMeter power = new EnergyMeter();
    IGridConnection hostConnection;
    long expectedInputBalance;
    Future<ICraftingPlan> calculation;
    ICraftingPlan plan;

    PatternBatchNativeCpuFixture(GameTestHelper helper, String name, boolean fluid, int unitAmount,
                                 long crafts, ParallelTier tier) {
        this(helper, name, fluid, unitAmount, crafts, tier, CPU, false);
    }

    PatternBatchNativeCpuFixture(GameTestHelper helper, String name, boolean fluid, int unitAmount,
                                 long crafts, ParallelTier tier, BlockPos cpuPosition, boolean machineEnergy) {
        this(helper, name, fluid, unitAmount, crafts, tier, cpuPosition, machineEnergy, 1L, false);
    }

    PatternBatchNativeCpuFixture(GameTestHelper helper, String name, boolean fluid, int unitAmount,
                                 long crafts, ParallelTier tier, BlockPos cpuPosition, boolean machineEnergy,
                                 long craftsPerPattern, boolean dynamicRecipe) {
        this.helper = helper;
        this.cpuPosition = cpuPosition;
        // Ordinary FE admission only checks supply; forty actual recipe ticks pay.
        feBudget = machineEnergy ? Math.multiplyExact(crafts, 40L) : 0L;
        if (!cpuPosition.equals(CPU)) {
            BlockPos end = helper.absolutePos(cpuPosition.offset(1, 0, 0));
            helper.assertTrue(helper.getBounds().contains(helper.absolutePos(CONTROLLER).getCenter())
                            && helper.getBounds().contains(end.getCenter()),
                    "Cross-chunk fixture must own its dedicated 24x4x8 template; bounds=" + helper.getBounds()
                            + ", controller=" + helper.absolutePos(CONTROLLER) + ", CPU far corner=" + end);
            helper.assertTrue(!new ChunkPos(helper.absolutePos(CONTROLLER)).equals(new ChunkPos(end)),
                    "The actual controller and CPU are in different chunks");
        }
        total = Math.multiplyExact(crafts, unitAmount);
        input = fluid ? AEFluidKey.of(Fluids.WATER) : AEItemKey.of(Items.IRON_INGOT);
        output = fluid ? AEFluidKey.of(Fluids.LAVA) : AEItemKey.of(Items.GOLD_INGOT);
        ResourceLocation machineId = MMCR.id("pattern_batch_cpu_" + name);
        recipeId = machineId.withSuffix("_recipe");
        helper.setBlock(CONTROLLER, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH));
        helper.setBlock(PORT, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(PORT.subtract(CONTROLLER), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get()));
        for (BlockPos pos : List.of(CONTROLLER.east(), CONTROLLER.east(2))) {
            var block = ModBlocks.BLOCKS.get("factory_controller").get();
            helper.setBlock(pos, block.defaultBlockState());
            pattern.put(pos.subtract(CONTROLLER), new BlockPredicate.OfBlock(block));
        }
        var parallel = ModBlocks.BLOCKS.get(tier.idSuffix()).get();
        helper.setBlock(CONTROLLER.south(), parallel.defaultBlockState());
        pattern.put(new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(parallel));
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(cpuPosition, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        // The ordinary unit also lets CraftingService discover its exact-class node owner.
        helper.setBlock(cpuPosition.offset(1, 0, 0), AEBlocks.CRAFTING_UNIT.block().defaultBlockState());
        var original = cpuBlock();
        helper.getLevel().removeBlockEntity(original.getBlockPos());
        helper.getLevel().setBlockEntity(new LargeStorageCpu(original.getType(), original.getBlockPos(), original.getBlockState()));
        if (machineEnergy) {
            var block = ModBlocks.BLOCKS.get("extended_energy_input_hatch_ultimate").get();
            helper.setBlock(FE_PORT, block.defaultBlockState());
            pattern.put(FE_PORT.subtract(CONTROLLER), new BlockPredicate.OfBlock(block));
            energyHatch().energyStorage().setAmount(feBudget);
            helper.assertTrue(energyHatch().energyStorage().getAmountAsLong() == feBudget,
                    "The real long FE owner holds the complete start-plus-duration budget");
        }
        machine = new DynamicMachine(machineId, "block.mmcr.test_cube", new BlockArray(pattern),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                tier.maxParallelism(), true, true, 2);
        helper.assertFalse(MachineRegistry.containsStatic(machineId), "Fixture machine ID is unique");
        MachineRegistry.register(machine);
        var ingredient = fluid
                ? new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), unitAmount)
                : new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), unitAmount);
        MachineRequirement product = fluid
                ? MachineRequirement.fluidOutput(new FluidStack(Fluids.LAVA, unitAmount))
                : MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT, unitAmount));
        List<MachineRequirement> requirements = new ArrayList<>(List.of(MachineRequirement.fromInput(ingredient), product));
        if (machineEnergy) requirements.add(MachineRequirement.fromInput(new MachineIngredient.EnergyIngredient(1L)));
        var recipe = MachineRecipe.fromCanonical(recipeId, machineId, 40, requirements,
                List.of(), List.of(), 0, 2, false, true, false, Set.of());
        if (dynamicRecipe) {
            var recipes = new LinkedHashMap<>(RecipeRegistry.dynamicSnapshot());
            recipes.put(recipeId, recipe);
            RecipeRegistry.replaceDynamic(recipes);
        } else RecipeRegistry.registerStatic(recipe);
        controller = helper.getBlockEntity(CONTROLLER);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        host = helper.getBlockEntity(PORT);
        provider = new CountingProvider(host.getLogic());
        // Add services before AE's queued first tick; never manually create managed nodes.
        host.getMainNode().addService(ICraftingProvider.class, provider);
        energy = helper.getBlockEntity(ENERGY);
        energy.getMainNode().addService(IStorageProvider.class, mounts -> mounts.mount(storage, 0));
        storage.setStack(0, new GenericStack(input, total / 2));
        storage.setStack(1, new GenericStack(input, total - total / 2));
        host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                unitAmount == 2 && craftsPerPattern == 1L ? List.of(new GenericStack(input, 1), new GenericStack(input, 1))
                        : List.of(new GenericStack(input, Math.multiplyExact(unitAmount, craftsPerPattern))),
                List.of(new GenericStack(output, Math.multiplyExact(unitAmount, craftsPerPattern)))));
    }

    GameTestSequence calculate() {
        return helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                host.getMainNode().getNode() != null && energy.getMainNode().getNode() != null
                        && cpuBlock().getMainNode().getNode() != null, "Native nodes initialize"))
                .thenExecute(() -> { connectNetwork(true); controller.requestImmediateStructureCheck(); })
                .thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed()
                                && controller.factorySchedulerThreadCount() >= 2 && host.getMainNode().isActive()
                                && cluster() != null && cluster().isActive(),
                        "Real parallel hardware, factory lanes and native CPU are available"))
                .thenExecute(() -> host.getLogic().updatePatterns())
                .thenWaitUntil(() -> helper.assertTrue(grid().getCraftingService().isCraftable(output),
                        "Native crafting service discovers the interface pattern"))
                .thenExecute(() -> calculation = grid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                        new ICraftingSimulationRequester() {
                            @Override public IActionSource getActionSource() { return IActionSource.ofMachine(host); }
                            @Override public IGridNode getGridNode() { return host.getMainNode().getNode(); }
                        }, output, total, CalculationStrategy.REPORT_MISSING_ITEMS))
                .thenWaitUntil(() -> helper.assertTrue(calculation.isDone(), "Asynchronous native plan completes"))
                .thenExecute(() -> {
                    try { plan = calculation.get(); }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new GameTestAssertException("Interrupted obtaining completed plan: " + exception);
                    } catch (ExecutionException exception) {
                        throw new GameTestAssertException("Native planning failed: " + exception.getCause());
                    }
                    helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty()
                                    && plan.usedItems().get(input) == total && plan.emittedItems().isEmpty(),
                            "The unchanged real plan uses exactly the stocked material");
                    helper.assertTrue(storage.operations < 4096, "Planning work is bounded, not per-craft");
                }).thenWaitUntil(() -> {
                    double needed = (double) total / input.getAmountPerOperation();
                    helper.assertTrue(grid().getEnergyService().extractAEPower(needed, Actionable.SIMULATE,
                            PowerMultiplier.CONFIG) >= needed, "Native grid can simulate the entire batch power cost");
                });
    }

    void submit() {
        helper.assertTrue(grid().getCraftingService().submitJob(plan, null, cluster(), false,
                IActionSource.ofMachine(host)).successful(), "Native CPU accepts the actual plan without changing plan.bytes");
        cpu().setJobSuspended(true);
        helper.assertTrue(cpu().getStored(input) == total && stored(input) == 0
                        && cpu().getWaitingFor(output) == 0 && cpu().getPendingOutputs(output) == total,
                "The CPU owns every unpaid input and pending output");
        power.delegate = grid().getEnergyService();
    }

    int dispatch() { return cpu().executeCrafting(1, (CraftingService) grid().getCraftingService(), power.service, helper.getLevel()); }

    void assertOneActiveBatch(long parallelism) {
        var factory = controller.runtimeSnapshot().factory();
        var lanes = factory.lanes().stream().filter(lane -> recipeId.equals(lane.recipeId())).toList();
        helper.assertTrue(factory.activeLaneCount() == 1 && lanes.size() == 1 && lanes.getFirst().parallelism() == parallelism,
                "One standard push starts one actual lane at exact long parallelism");
    }

    void assertSettled(long pushes) {
        helper.assertTrue(!cpu().hasJob() && cpu().getWaitingFor(output) == 0 && cpu().getStored(input) == 0
                        && stored(input) == expectedInputBalance && stored(output) == total
                        && host.getLogic().getReturnInv().isEmpty() && controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                "Real outputs settle the native CPU exactly; job=" + cpu().hasJob() + ", waiting=" + cpu().getWaitingFor(output)
                        + ", input=" + stored(input) + ", output=" + stored(output) + ", returns=" + returnAmount(output)
                        + ", factory=" + controller.runtimeSnapshot().factory());
        helper.assertTrue(provider.accepted == pushes && provider.attempts < 4096 && storage.operations < 16384,
                "Production has bounded work and no duplicate accepted pushes");
    }

    long stored(AEKey key) { return storage.getAvailableStacks().get(key); }
    IGrid grid() { return host.getMainNode().getNode().getGrid(); }
    CraftingBlockEntity cpuBlock() { return helper.getBlockEntity(cpuPosition); }
    CraftingCPUCluster cluster() { return cpuBlock().getCluster(); }
    CraftingCpuLogic cpu() { return cluster().craftingLogic; }
    EnergyHatchBlockEntity energyHatch() { return helper.getBlockEntity(FE_PORT); }
    long returnAmount(AEKey key) { return host.getLogic().getReturnInv().getAvailableStacks().get(key); }
    void connectNetwork(boolean connectHost) {
        GridHelper.createConnection(energy.getMainNode().getNode(), cpuBlock().getMainNode().getNode());
        if (connectHost) reconnectHost();
    }
    void reconnectHost() { hostConnection = GridHelper.createConnection(host.getMainNode().getNode(), energy.getMainNode().getNode()); }
    void disconnectHost() { hostConnection.destroy(); }
    void addSpareMaterial() {
        expectedInputBalance = total;
        helper.assertTrue(grid().getStorageService().getInventory().insert(input, total, Actionable.MODULATE,
                IActionSource.ofMachine(host)) == total, "Spare material exists but has no CPU order");
    }
    void fillReturnSlots(AEKey blocker) {
        storage.blocked.add(blocker);
        var returns = host.getLogic().getReturnInv();
        for (int slot = 0; slot < returns.size(); slot++) returns.setStack(slot, new GenericStack(blocker, 1));
        host.saveChanges();
    }
    void clearReturnBlocker(AEKey blocker) {
        var returns = host.getLogic().getReturnInv();
        for (int slot = 0; slot < returns.size(); slot++) if (blocker.equals(returns.getKey(slot))) returns.setStack(slot, null);
        storage.blocked.remove(blocker);
        host.saveChanges();
    }

    List<CompoundTag> savedLanes() {
        var factory = controller.saveCustomOnly(helper.getLevel().registryAccess()).getCompound("factory_runtime");
        List<CompoundTag> lanes = new ArrayList<>();
        for (int lane = 0; lane < factory.getInt("lane_count"); lane++) lanes.add(factory.getCompound("lane_" + lane).getCompound("runtime"));
        return lanes;
    }

    /** Recreate real owners using native registry-aware NBT; never unload a shared GameTest chunk. */
    void rebuildPaidOwners() {
        var registries = helper.getLevel().registryAccess();
        List<BlockEntity> owners = List.of(energyHatch(), host,
                helper.getBlockEntity(CONTROLLER.east()), helper.getBlockEntity(CONTROLLER.east(2)),
                helper.getBlockEntity(CONTROLLER.south()), controller, cpuBlock(),
                helper.getBlockEntity(cpuPosition.offset(1, 0, 0)), energy);
        Map<BlockEntity, CompoundTag> saved = new LinkedHashMap<>();
        for (var owner : owners) saved.put(owner, owner.saveWithFullMetadata(registries));
        var balances = new CompoundTag();
        storage.writeToChildTag(balances, "balances", registries);
        var blocked = Set.copyOf(storage.blocked);
        long operations = storage.operations;
        controller.onChunkUnloaded();
        host.onChunkUnloaded();
        for (var owner : owners) helper.getLevel().removeBlockEntity(owner.getBlockPos());
        storage = new LongStorage();
        storage.readFromChildTag(balances, "balances", registries);
        storage.blocked.addAll(blocked);
        storage.operations = operations;
        for (var old : owners) {
            BlockEntity restored = old instanceof LargeStorageCpu
                    ? new LargeStorageCpu(old.getType(), old.getBlockPos(), old.getBlockState())
                    : old.getType().create(old.getBlockPos(), old.getBlockState());
            helper.assertTrue(restored != null && restored != old, "Every owner is a genuinely new block entity");
            restored.setLevel(helper.getLevel());
            if (restored instanceof MachineControllerBlockEntity replacement) {
                controller = replacement;
                replacement.setMachine(machine);
                replacement.setStructureCheckIntervalForTesting(1);
            }
            if (restored instanceof PatternInterfaceBlockEntity replacement) {
                host = replacement;
                provider.delegate = replacement.getLogic();
                replacement.getMainNode().addService(ICraftingProvider.class, provider);
            }
            if (restored instanceof CreativeEnergyCellBlockEntity replacement) {
                energy = replacement;
                replacement.getMainNode().addService(IStorageProvider.class, mounts -> mounts.mount(storage, 0));
            }
            // Replaying the same load before ticking must not duplicate any balance or payment.
            restored.loadWithComponents(saved.get(old).copy(), registries);
            restored.loadWithComponents(saved.get(old).copy(), registries);
            helper.getLevel().setBlockEntity(restored);
        }
        controller.requestImmediateStructureCheck();
    }

    /** Capacity only: native cluster, unchanged plan.bytes, accounting, cancellation and NBT.
     * Not evidence that a stock 64K CPU can hold a multi-gigabyte plan.
     * @author howxu <dev@howxu.cn>
     */
    private static final class LargeStorageCpu extends CraftingBlockEntity {
        LargeStorageCpu(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public long getStorageBytes() { return 1L << 40; }
    }

    /** Fixed-slot long ME storage; never manufactures recipe outputs.
     * @author howxu <dev@howxu.cn>
     */
    static final class LongStorage extends GenericStackInv {
        long operations;
        final Set<AEKey> blocked = new HashSet<>();
        LongStorage() { super(null, 4); }
        @Override public long getMaxAmount(AEKey key) { return Long.MAX_VALUE; }
        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            operations++;
            if (blocked.contains(what)) return 0;
            return super.insert(what, Math.min(amount, Long.MAX_VALUE - getAvailableStacks().get(what)), mode, source);
        }
        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            operations++;
            return super.extract(what, amount, mode, source);
        }
    }

    /** Transparent observations of standard CPU provider calls.
     * @author howxu <dev@howxu.cn>
     */
    static final class CountingProvider implements ICraftingProvider {
        private ICraftingProvider delegate;
        long attempts;
        long accepted;
        boolean holdAfterFirstPush;
        final Map<Long, Long> acceptedMultipliers = new LinkedHashMap<>();
        KeyCounter lastInputs = new KeyCounter();
        CountingProvider(ICraftingProvider delegate) { this.delegate = delegate; }
        @Override public List<IPatternDetails> getAvailablePatterns() { return delegate.getAvailablePatterns(); }
        @Override public int getPatternPriority() { return delegate.getPatternPriority(); }
        @Override public Set<AEKey> getEmitableItems() { return delegate.getEmitableItems(); }
        @Override public boolean isBusy() { return holdAfterFirstPush && accepted > 0L || delegate.isBusy(); }
        @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
            attempts++;
            lastInputs = new KeyCounter();
            for (var counter : inputs) lastInputs.addAll(counter);
            boolean accepted = delegate.pushPattern(details, inputs);
            if (accepted) {
                this.accepted++;
                if (details instanceof PatternBatchDetails batch) acceptedMultipliers.merge(batch.data().multiplier(), 1L, Long::sum);
            }
            return accepted;
        }
    }

    /** Native grid delegation with a deterministic zero-power refusal gate.
     * @author howxu <dev@howxu.cn>
     */
    static final class EnergyMeter {
        IEnergyService delegate;
        boolean blocked;
        double charged;
        long charges;
        final IEnergyService service = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[]{IEnergyService.class}, (proxy, method, args) -> {
                    boolean extraction = method.getName().equals("extractAEPower");
                    if (extraction && blocked) return 0D;
                    Object result;
                    try { result = method.invoke(delegate, args); }
                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                    if (extraction && args[1] == Actionable.MODULATE) { charged += (double) result; charges++; }
                    return result;
                });
    }
}
