package cn.howxu.mmcr.compat.appmek;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Native CPU fixture shared by chemical and mirror tests; contains no optional-mod class references.
 * @author howxu <dev@howxu.cn>
 */
public final class CompatPatternCpuFixture {
    public static final BlockPos PORT = new BlockPos(0, 1, 0);
    private static final BlockPos ENERGY = new BlockPos(4, 0, 0);
    private static final BlockPos CPU = new BlockPos(4, 0, 3);
    public final GameTestHelper helper;
    public final PatternInterfaceBlockEntity host;
    public final MachineControllerBlockEntity controller;
    public final ResourceLocation recipeId;
    public final LongStorage storage = new LongStorage();
    public final CountingProvider provider;
    public ICraftingPlan plan;
    private Future<ICraftingPlan> calculation;
    private IGridConnection hostConnection;

    public CompatPatternCpuFixture(GameTestHelper helper, String name, String portId, ParallelTier tier,
                                   List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        this(helper, name, portId, tier, requirements, outputs, false);
    }

    public CompatPatternCpuFixture(GameTestHelper helper, String name, String portId, ParallelTier tier,
                                   List<MachineRequirement> requirements, List<MachineOutput> outputs,
                                   boolean ordinaryInput) {
        this(helper, name, portId, tier, requirements, outputs, ordinaryInput ? "item_input_bus" : null);
    }

    CompatPatternCpuFixture(GameTestHelper helper, String name, String portId, ParallelTier tier,
                            List<MachineRequirement> requirements, List<MachineOutput> outputs, String ordinaryInputId) {
        this.helper = helper;
        ResourceLocation machineId = MMCR.id(name);
        recipeId = MMCR.id(name + "_recipe");
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(PORT, ModBlocks.BLOCKS.get(portId).get().defaultBlockState());
        BlockPos parallel = new BlockPos(0, 0, 1);
        helper.setBlock(parallel, ModBlocks.BLOCKS.get(tier.idSuffix()).get().defaultBlockState());
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_64K.block());
        helper.setBlock(CPU.east(), AEBlocks.CRAFTING_UNIT.block());
        CraftingBlockEntity original = helper.getBlockEntity(CPU);
        helper.getLevel().removeBlockEntity(original.getBlockPos());
        helper.getLevel().setBlockEntity(new CapacityCpu(original.getType(), original.getBlockPos(), original.getBlockState()));
        Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>(Map.of(
                PORT, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(portId).get()),
                parallel, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(tier.idSuffix()).get())));
        if (ordinaryInputId != null) {
            BlockPos ordinary = new BlockPos(2, 0, 0);
            var block = ModBlocks.BLOCKS.get(ordinaryInputId).get();
            helper.setBlock(ordinary, block.defaultBlockState());
            blocks.put(ordinary, new BlockPredicate.OfBlock(block));
        }
        DynamicMachine machine = new DynamicMachine(machineId, "block.mmcr.test_cube", new BlockArray(blocks),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                tier.maxParallelism(), true, false, 1);
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 4,
                requirements, outputs, List.of(), 0, 1, false, true, false, Set.of()));
        controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        host = helper.getBlockEntity(PORT);
        provider = new CountingProvider(host.getLogic());
        host.getMainNode().addService(ICraftingProvider.class, provider);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(ENERGY);
        energy.getMainNode().addService(IStorageProvider.class, mounts -> mounts.mount(storage, 0));
    }

    public GameTestSequence ready() {
        return helper.startSequence().thenWaitUntil(() -> helper.assertTrue(host.getMainNode().getNode() != null
                        && energy().getMainNode().getNode() != null && cpuBlock().getMainNode().getNode() != null,
                "Native interface, storage owner and CPU nodes initialize"))
                .thenExecute(() -> {
                    GridHelper.createConnection(energy().getMainNode().getNode(), cpuBlock().getMainNode().getNode());
                    reconnect();
                    controller.requestImmediateStructureCheck();
                }).thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed()
                                && host.getMainNode().isActive() && cpuBlock().getCluster() != null
                                && cpuBlock().getCluster().isActive(),
                        "Real single-lane machine, parallel hardware and native CPU become available"))
                .thenExecute(() -> host.getLogic().updatePatterns());
    }

    public void begin(AEKey output, long amount) {
        calculation = grid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                () -> IActionSource.ofMachine(host), output, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public void awaitPlan() {
        helper.assertTrue(calculation.isDone(), "Native asynchronous calculation completes");
        try {
            plan = calculation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GameTestAssertException("Interrupted native calculation: " + exception);
        } catch (ExecutionException exception) {
            throw new GameTestAssertException("Actual native crafting calculation failed: " + exception.getCause());
        }
        helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty() && plan.emittedItems().isEmpty(),
                "Actual calculated plan has real material and no missing/emitted substitutes");
    }

    public void submit() {
        helper.assertTrue(grid().getCraftingService().submitJob(plan, null, cpuBlock().getCluster(), false,
                IActionSource.ofMachine(host)).successful(), "Native CPU accepts the unmodified calculated plan");
        cpu().setJobSuspended(true);
    }

    public int dispatch() {
        return cpu().executeCrafting(1, (CraftingService) grid().getCraftingService(),
                grid().getEnergyService(), helper.getLevel());
    }

    public void assertActive(long parallelism) {
        var crafting = controller.runtimeSnapshot().crafting();
        helper.assertTrue(recipeId.equals(crafting.recipeId()) && crafting.parallelism() == parallelism
                        && !controller.hasFactoryController(),
                "One standard CPU push synchronously starts only one local lane at exact parallelism");
    }

    public void disconnect() { hostConnection.destroy(); }
    public void reconnect() {
        hostConnection = GridHelper.createConnection(host.getMainNode().getNode(), energy().getMainNode().getNode());
    }
    public long stored(AEKey key) { return storage.getAvailableStacks().get(key); }
    public IGrid grid() { return host.getMainNode().getNode().getGrid(); }
    public CraftingCpuLogic cpu() { return cpuBlock().getCluster().craftingLogic; }
    private CraftingBlockEntity cpuBlock() { return helper.getBlockEntity(CPU); }
    private CreativeEnergyCellBlockEntity energy() { return helper.getBlockEntity(ENERGY); }

    /** Capacity-only override, not evidence a stock 64K CPU can hold a huge plan.
     * @author howxu <dev@howxu.cn>
     */
    private static final class CapacityCpu extends CraftingBlockEntity {
        CapacityCpu(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public long getStorageBytes() { return 1L << 40; }
    }

    /** Fixed physical inventory shared by all mounted resource types; never manufactures outputs.
     * @author howxu <dev@howxu.cn>
     */
    public static final class LongStorage extends GenericStackInv {
        public final Set<AEKey> blocked = new HashSet<>();
        LongStorage() { super(null, 16); }
        @Override public long getMaxAmount(AEKey key) { return Long.MAX_VALUE; }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (blocked.contains(key)) return 0L;
            return super.insert(key, Math.min(amount, Long.MAX_VALUE - getAvailableStacks().get(key)), mode, source);
        }
    }

    /** Transparent observation of actual native provider dispatches.
     * @author howxu <dev@howxu.cn>
     */
    public static final class CountingProvider implements ICraftingProvider {
        private final ICraftingProvider delegate;
        public long accepted;
        public final Map<Long, Long> multipliers = new LinkedHashMap<>();
        CountingProvider(ICraftingProvider delegate) { this.delegate = delegate; }
        @Override public List<IPatternDetails> getAvailablePatterns() { return delegate.getAvailablePatterns(); }
        @Override public int getPatternPriority() { return delegate.getPatternPriority(); }
        @Override public Set<AEKey> getEmitableItems() { return delegate.getEmitableItems(); }
        @Override public boolean isBusy() { return delegate.isBusy(); }
        @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
            boolean success = delegate.pushPattern(details, inputs);
            if (success) {
                accepted++;
                multipliers.merge(details instanceof PatternBatchDetails batch ? batch.data().multiplier() : 1L,
                        1L, Long::sum);
            }
            return success;
        }
    }
}
