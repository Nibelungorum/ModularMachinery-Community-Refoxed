package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.recipe.RecipeStartDelay;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MachineControllerBlockEntity extends BlockEntity implements FactorySchedulerBlockEntity.SyncListener {

    private static final Logger LOG = LoggerFactory.getLogger(MachineControllerBlockEntity.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    private static final Set<MachineControllerBlockEntity> FORMED_CONTROLLERS = ConcurrentHashMap.newKeySet();
    private final int instanceId = INSTANCE_COUNTER.incrementAndGet();

    private Machine machine;
    private Machine foundMachine;
    private BlockArray foundPattern;
    private CompiledMachinePattern foundCompiledPattern;
    private Direction controllerFacing;
    private ActiveMachineRecipe active;
    private RecipeCraftingContext context;
    private final List<ProcessingComponent> components = new ArrayList<>();
    private final Map<String, List<RecipeModifier>> foundModifiers = new LinkedHashMap<>();
    private long structureVersion;
    private long modifierSnapshotVersion;
    private int structureCheckCounter;
    private boolean structureDirty = true;
    private boolean clientActive;
    private Boolean lastBroadcastFormed;
    private boolean lastBroadcastActive;
    private @Nullable String lastFailureUnloc;
    private @Nullable PortRequirementSpec.Failure lastFormationFailure;
    private @Nullable String lastStructureMismatchDiagnostic;
    private boolean redstonePaused;
    private @Nullable ActiveMachineRecipe pausedActive;
    private @Nullable RecipeCraftingContext pausedContext;
    private RecipeCraftingContextPool contextPool = RecipeCraftingContextPool.global();
    private Set<BlockPos> linkedPortPositions = new HashSet<>();
    private int recipeSearchRetryCounter;
    private long recipeSearchAttemptCounter;
    private long cachedCandidatesReloadVersion = Long.MIN_VALUE;
    private int cachedDatapackRecipeCount = -1;
    private @Nullable Identifier cachedCandidatesMachineId;
    private List<MachineRecipe> cachedCandidates = List.of();
    private RecipeStartDelay recipeStartDelay = new RecipeStartDelay();
    private @Nullable MachineRecipe lastRecipe;
    private long lastRecipeStructureVersion = Long.MIN_VALUE;
    private long lastRecipeModifierSnapshotVersion = Long.MIN_VALUE;
    private boolean recipeDirty = true;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.controllerFor(machineIdFromState(state)).get(), pos, state);
        LOG.info("Controller BE spawned: instance=#{} pos={} blockStateMachineId={}", instanceId, pos, machineIdFromState(state));
    }

    private static Identifier machineIdFromState(BlockState state) {
        if (state.getBlock() instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        throw new IllegalArgumentException("MachineControllerBlockEntity requires a MachineControllerBlock state");
    }

    public Machine getMachine() { return machine; }
    public void setMachine(Machine m) {
        Identifier before = this.machine == null ? null : this.machine.registryName();
        stopFactoryController();
        clearFoundModifiers();
        this.machine = m;
        markRecipeDirty();
        LOG.info("[Ctrl#{}] setMachine: {} → {} at pos={}", instanceId, before, m == null ? null : m.registryName(), getBlockPos());
        setChanged();
    }

    public Machine getFoundMachine() { return foundMachine; }
    public BlockArray getFoundPattern() { return foundPattern; }

    public Map<String, List<RecipeModifier>> getFoundModifiers() {
        if (foundModifiers == null) return Map.of();
        Map<String, List<RecipeModifier>> snapshot = new LinkedHashMap<>();
        for (var entry : foundModifiers.entrySet()) {
            snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(snapshot);
    }

    public List<RecipeModifier> foundModifierList() {
        if (foundModifiers == null) return List.of();
        return foundModifiers.values().stream().flatMap(List::stream).toList();
    }

    public boolean isFormed() { return getBlockState().getValue(MachineControllerBlock.FORMED); }
    public void setFormed(boolean f) {
        boolean before = isFormed();
        if (before == f) return;
        level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, f), 3);
        LOG.info("[Ctrl#{}] setFormed: {} → {} at pos={}", instanceId, before, f, getBlockPos());
    }

    public MachineRecipe getActiveRecipe() { return active == null ? null : active.getRecipe(); }

    public int getTickCounter() { return active == null ? 0 : active.getTick(); }

    public ActiveMachineRecipe getActive() { return active; }

    public long getStructureVersion() { return structureVersion; }

    public long getModifierSnapshotVersion() { return modifierSnapshotVersion; }

    public @Nullable String getLastFailureUnloc() { return lastFailureUnloc; }

    public @Nullable PortRequirementSpec.Failure getLastFormationFailure() { return lastFormationFailure; }

    public void onStructureBlockChanged(BlockPos changedPos) {
        if (!isFormed() || foundCompiledPattern == null || controllerFacing == null) return;
        if (!isInsideCompiledBounds(changedPos)) return;
        structureDirty = true;
        markRecipeDirty();
        setChanged();
    }

    public static void markStructureDirty(LevelAccessor level, BlockPos changedPos) {
        if (level == null || level.isClientSide()) return;
        FORMED_CONTROLLERS.removeIf(controller -> controller.isRemoved() || controller.level == null);
        for (MachineControllerBlockEntity controller : FORMED_CONTROLLERS) {
            if (controller.level == level) controller.onStructureBlockChanged(changedPos);
        }
    }

    public static void markStructureChunkDirty(LevelAccessor level, ChunkPos chunkPos) {
        if (level == null || level.isClientSide()) return;
        FORMED_CONTROLLERS.removeIf(controller -> controller.isRemoved() || controller.level == null);
        for (MachineControllerBlockEntity controller : FORMED_CONTROLLERS) {
            if (controller.level == level) controller.onStructureChunkUnloaded(chunkPos);
        }
    }

    public void setLastFailureUnloc(@Nullable String key) {
        this.lastFailureUnloc = key;
    }

    public void clearLastFailureOnRecipeStart() {
        this.lastFailureUnloc = null;
    }

    public boolean isRedstonePaused() { return redstonePaused; }

    public void applyClientState(String recipeName, boolean formed, boolean active) {
        if (level == null || !level.isClientSide()) return;
        if (isFormed() != formed) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, formed), 3);
        }
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != active) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, active), 3);
        }
        this.clientActive = active;
    }

    public boolean hasClientActiveRecipe() { return clientActive; }

    public boolean isRuntimeActive() {
        if (active != null) return true;
        FactorySchedulerBlockEntity factory = getFactoryController();
        return factory != null && factory.activeThreadCount() > 0;
    }

    public int currentParallelism() {
        return active == null ? 0 : active.getParallelism();
    }

    public int activeFactoryThreadCount() {
        FactorySchedulerBlockEntity factory = getFactoryController();
        return factory == null ? 0 : factory.activeLaneCount();
    }

    public List<ProcessingComponent> getComponents() { return List.copyOf(components); }

    public void markRecipeDirty() {
        recipeDirty = true;
    }

    public boolean hasLinkedPort(BlockPos portPos) {
        return linkedPortPositions != null && linkedPortPositions.contains(portPos);
    }

    public void resetLinkedPortAppearances() {
        resetLinkedPorts();
    }

    public long totalStoredEnergy() {
        long total = 0;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof EnergyInputHatchBlockEntity hatch) {
                total += hatch.getEnergyStorage(null).getEnergyStored();
            } else if (component.getContainer() instanceof EnergyOutputHatchBlockEntity hatch) {
                total += hatch.getEnergyStorage(null).getEnergyStored();
            }
        }
        return total;
    }

    public long totalCapacityEnergy() {
        long total = 0;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof EnergyInputHatchBlockEntity hatch) {
                total += hatch.getEnergyStorage(null).getMaxEnergyStored();
            } else if (component.getContainer() instanceof EnergyOutputHatchBlockEntity hatch) {
                total += hatch.getEnergyStorage(null).getMaxEnergyStored();
            }
        }
        return total;
    }

    public FluidStack primaryFluid() {
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof FluidInputHatchBlockEntity hatch) {
                FluidStack stack = hatch.getFluidTank(null).getFluid();
                if (!stack.isEmpty()) return stack.copy();
            }
        }
        return FluidStack.EMPTY;
    }

    public FluidStack primaryOutputFluid() {
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof FluidOutputHatchBlockEntity hatch) {
                FluidStack stack = hatch.getFluidTank(null).getFluid();
                if (!stack.isEmpty()) return stack.copy();
            }
        }
        return FluidStack.EMPTY;
    }

    public int getMaxParallelism() {
        if (machine == null || !machine.parallelizable()) return 1;
        long max = 0;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof ParallelControllerBlockEntity parallel) {
                max += parallel.currentParallelism();
                if (max >= machine.maxParallelism()) return machine.maxParallelism();
            }
        }
        return Math.max(1, (int) max);
    }

    public int parallelControllerCount() {
        int count = 0;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof ParallelControllerBlockEntity) count++;
        }
        return count;
    }

    public int maxParallelControllerCount() {
        if (machine == null || !machine.parallelizable()) return 0;
        int maxParallelism = Math.max(1, machine.maxParallelism());
        return maxParallelism == Integer.MAX_VALUE ? Integer.MAX_VALUE : maxParallelism;
    }

    public @Nullable FactorySchedulerBlockEntity getFactoryController() {
        if (machine == null || !machine.hasFactory()) return null;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof FactorySchedulerBlockEntity factory) {
                int threadLimit = effectiveFactoryThreadLimit();
                if (factory.threadLimit() != threadLimit) factory.setThreadLimit(threadLimit);
                return factory;
            }
        }
        return null;
    }

    public boolean hasFactoryController() {
        if (machine == null || !machine.hasFactory()) return false;
        return components.stream().anyMatch(component -> component.getContainer() instanceof FactorySchedulerBlockEntity);
    }

    public int effectiveFactoryThreadLimit() {
        if (machine == null || !machine.hasFactory()) return 1;
        int aggregatedThreads = factorySchedulerThreadCount();
        return aggregatedThreads <= 0 ? 1 : aggregatedThreads;
    }

    public int factorySchedulerThreadCount() {
        long total = 0L;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof FactorySchedulerBlockEntity scheduler) {
                total += scheduler.threadCount();
                if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        boolean activeBefore = isRuntimeActive();
        if (machine == null) bindDefaultMachine();

        // 1.21+ exposes the old strong-power query through SignalGetter's direct signal helper.
        boolean powered = level.getDirectSignalTo(getBlockPos()) > 0;
        if (powered) {
            redstonePaused = true;
            FactorySchedulerBlockEntity factory = getFactoryController();
            if (factory != null) factory.pause();
            if (active != null && context != null) {
                pausedActive = active;
                pausedContext = context;
                active = null;
                context = null;
                setActiveState(false);
                broadcastStateIfChanged(true);
            } else if (active != null || context != null) {
                active = null;
                context = null;
            }
            structureDirty = true;
            setChanged();
            return;
        }
        redstonePaused = false;
        FactorySchedulerBlockEntity factory = getFactoryController();
        if (factory != null) factory.resume();
        if (active == null && pausedActive != null && pausedContext != null) {
            active = pausedActive;
            context = pausedContext;
            pausedActive = null;
            pausedContext = null;
            structureDirty = true;
            markRecipeDirty();
            setActiveState(true);
        } else if (pausedActive != null || pausedContext != null) {
            pausedActive = null;
            pausedContext = null;
        }

        if (shouldCheckStructure()) checkStructure();
        if (isFormed() && isStructureAreaLoaded()) {
            if (factory != null) {
                tickFactoryRecipes(factory);
            } else {
                tickSingleActiveRecipe();
            }
        }
        broadcastStateIfChanged(activeBefore);
    }

    private void tickSingleActiveRecipe() {
        boolean startedThisTick = false;
        if (active == null) {
            startedThisTick = tryStartNewRecipe();
        }
        if (active != null && !startedThisTick) tickActiveRecipe();
    }

    private void tickFactoryRecipes(FactorySchedulerBlockEntity factory) {
        int maxParallelism = getMaxParallelism();
        List<MachineRecipe> candidates = recipesForMachine();
        RecipeCraftingContextPool pool = contextPool();
        factory.syncCoreThreads(this, machine, candidates, pool);
        factory.tickScheduler(this, candidates, structureVersion, maxParallelism, pool);
        setActiveState(factory.activeThreadCount() > 0);
    }

    @Override
    public void syncFactoryScheduler() {
        setChanged();
    }

    private void checkStructure() {
        structureDirty = false;
        structureCheckCounter = 0;
        lastFormationFailure = null;
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        if (facing.getAxis().isVertical() && machine != null && !machine.controller().allowVerticalFacing()) {
            resetMachine();
            return;
        }
        if (foundMachine != null && foundPattern != null && controllerFacing == facing) {
            if (foundCompiledPattern != null && !StructureMatcher.isAreaLoaded(foundCompiledPattern, facing, level, getBlockPos())) {
                pauseActiveForUnloadedStructure();
                structureDirty = true;
                return;
            }
            var replacements = replacementsFor(foundMachine, foundCompiledPattern, facing, foundPattern);
            boolean stillMatches = foundCompiledPattern == null || !replacements.isEmpty()
                    ? StructureMatcher.matchesRotated(foundPattern, level, getBlockPos(), replacements)
                    : StructureMatcher.matchesCompiled(foundCompiledPattern, facing, getBlockState().getValue(MachineControllerBlock.ROLL_FACING), level, getBlockPos());
            if (stillMatches) {
                collectFoundModifiers(replacements);
                resumePausedRecipeAfterStructureCheck();
                var failure = foundMachine.portRequirements().validate(countPorts(foundPattern, foundCompiledPattern, facing));
                if (failure.isPresent()) {
                    recordFormationFailure(foundMachine, failure.get());
                    resetMachine(false);
                    return;
                }
                failure = validatePortTiers(foundMachine, foundPattern, foundCompiledPattern, facing);
                if (failure.isPresent()) {
                    recordFormationFailure(foundMachine, failure.get());
                    resetMachine(false);
                    return;
                }
                if (!isFormed()) setFormed(true);
                updateComponents();
                return;
            }
            LOG.info("[Ctrl#{}] checkStructure: cached pattern no longer matches → reset; {}",
                    instanceId, structureMismatchDiagnostic(foundMachine, facing, foundPattern, level, getBlockPos(), replacements));
            resetMachine();
            return;
        }

        if (machine != null) {
            if (tryFormMachine(machine, facing)) {
                return;
            }
            Identifier stateMachineId = machineIdFromState(getBlockState());
            if (machine.registryName().equals(stateMachineId) || machine.controller().id().equals(stateMachineId)) {
                resetMachine(lastFormationFailure == null);
                return;
            }
        }
        checkAllPatterns(facing);
        if (!isFormed()) resetMachine(lastFormationFailure == null);
    }

    private boolean shouldCheckStructure() {
        if (structureDirty) return true;
        if (!isFormed()) return true;
        structureCheckCounter++;
        return structureCheckCounter >= structureCheckIntervalTicks();
    }

    private boolean isStructureAreaLoaded() {
        if (foundCompiledPattern == null || controllerFacing == null || level == null) return true;
        return StructureMatcher.isAreaLoaded(foundCompiledPattern, controllerFacing, level, getBlockPos());
    }

    private static int structureCheckIntervalTicks() {
        try {
            return Math.min(Config.MACHINE_CHECK_INTERVAL_TICKS.get(), Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS);
        } catch (IllegalStateException ignored) {
            return Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS;
        }
    }

    private void checkAllPatterns(Direction facing) {
        for (Machine candidate : MachineRegistry.getAll().values()) {
            if (candidate == machine) continue;
            if (tryFormMachine(candidate, facing)) {
                return;
            }
        }
    }

    private boolean tryFormMachine(Machine candidate, Direction facing) {
        if (!facing.getAxis().isVertical() && candidate.controller().requireVerticalFacing()) return false;
        if (facing.getAxis().isVertical() && !candidate.controller().allowVerticalFacing()) return false;

        for (BlockArray rotatedPattern : candidatePatterns(candidate, facing)) {
            if (tryFormMachine(candidate, facing, rotatedPattern)) return true;
        }
        return false;
    }

    private List<BlockArray> candidatePatterns(Machine candidate, Direction facing) {
        if (!facing.getAxis().isVertical()) {
            return List.of(BlockArrayCache.get(candidate.pattern(), facing));
        }

        Direction rollFacing = getBlockState().getValue(MachineControllerBlock.ROLL_FACING);
        if (!candidate.controller().fullyRotationallySymmetric()) {
            return List.of(BlockArrayCache.get(candidate.pattern(), facing, rollFacing));
        }

        List<BlockArray> patterns = new ArrayList<>(4);
        for (Direction candidateRoll : Direction.Plane.HORIZONTAL) {
            patterns.add(BlockArrayCache.get(candidate.pattern(), facing, candidateRoll));
        }
        return patterns;
    }

    private boolean tryFormMachine(Machine candidate, Direction facing, BlockArray rotatedPattern) {
        var compiled = compiledFor(candidate, rotatedPattern, facing);
        var replacements = replacementsFor(candidate, compiled, facing, rotatedPattern);
        boolean matches = compiled == null
                ? StructureMatcher.matchesRotated(rotatedPattern, level, getBlockPos(), replacements)
                : StructureMatcher.matchesCompiled(compiled, facing, getBlockState().getValue(MachineControllerBlock.ROLL_FACING), level, getBlockPos());
        if (!matches) {
            recordStructureMismatch(candidate, facing, rotatedPattern, replacements);
            return false;
        }

        var failure = candidate.portRequirements().validate(countPorts(rotatedPattern, compiled, facing));
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            return false;
        }

        failure = validatePortTiers(candidate, rotatedPattern, compiled, facing);
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            return false;
        }

        lastFormationFailure = null;
        lastStructureMismatchDiagnostic = null;
        onStructureFormed(candidate, rotatedPattern, compiled, facing, replacements);
        return true;
    }

    private Map<BlockPos, List<SingleBlockModifierReplacement>> replacementsFor(
            Machine candidate, CompiledMachinePattern compiled, Direction facing, BlockArray rotatedPattern) {
        Direction rollFacing = getBlockState().getValue(MachineControllerBlock.ROLL_FACING);
        if (compiled != null && compiled.rotatedPattern(facing) == rotatedPattern) {
            return compiled.modifierReplacements(facing, rollFacing);
        }
        if (candidate instanceof DynamicMachine dynamic) {
            return dynamic.rotatedModifierReplacements(facing, rollFacing);
        }
        return Map.of();
    }

    private void recordStructureMismatch(Machine candidate, Direction facing, BlockArray rotatedPattern,
                                         Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        String diagnostic = structureMismatchDiagnostic(candidate, facing, rotatedPattern, level, getBlockPos(), replacements);
        if (diagnostic.equals(lastStructureMismatchDiagnostic)) return;
        lastStructureMismatchDiagnostic = diagnostic;
        LOG.info("[Ctrl#{}] formation rejected: {}", instanceId, diagnostic);
    }

    private @Nullable CompiledMachinePattern compiledFor(Machine candidate, BlockArray rotatedPattern, Direction facing) {
        CompiledMachinePattern compiled = MachineRegistry.getCompiled(candidate.registryName());
        if (compiled == null || compiled.rotatedPattern(facing) != rotatedPattern) return null;
        return compiled;
    }

    static String structureMismatchDiagnostic(Machine candidate, Direction facing, BlockArray rotatedPattern, Level level, BlockPos ctrlPos) {
        return structureMismatchDiagnostic(candidate, facing, rotatedPattern, level, ctrlPos, Map.of());
    }

    static String structureMismatchDiagnostic(Machine candidate, Direction facing, BlockArray rotatedPattern, Level level, BlockPos ctrlPos,
                                              Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        if (rotatedPattern.isEmpty()) {
            return "machine=" + candidate.registryName()
                    + " facing=" + facing.name()
                    + " controllerPos=" + ctrlPos
                    + " reason=emptyPattern";
        }

        var mismatch = StructureMatcher.firstMismatch(rotatedPattern, level, ctrlPos, replacements);
        if (mismatch.isPresent()) {
            StructureMatcher.Mismatch first = mismatch.get();
            BlockEntity actualBlockEntity = level.getBlockEntity(first.worldPos());
            return "machine=" + candidate.registryName()
                    + " facing=" + facing.name()
                    + " controllerPos=" + ctrlPos
                    + " reason=blockMismatch"
                    + " relativePos=" + first.relativePos()
                    + " worldPos=" + first.worldPos()
                    + " expected=" + first.expected()
                    + " actualState=" + first.actualState()
                    + " actualBlock=" + first.actualState().getBlock().builtInRegistryHolder().key().identifier()
                    + " actualBlockEntity=" + (actualBlockEntity == null ? "none" : actualBlockEntity.getClass().getSimpleName());
        }

        return "machine=" + candidate.registryName()
                + " facing=" + facing.name()
                + " controllerPos=" + ctrlPos
                + " reason=unknownMismatch";
    }

    private void recordFormationFailure(Machine candidate, PortRequirementSpec.Failure failure) {
        if (failure.equals(lastFormationFailure)) return;
        lastFormationFailure = failure;
        lastStructureMismatchDiagnostic = null;
        String max = failure.requiredMax().isPresent() ? Integer.toString(failure.requiredMax().getAsInt()) : "unbounded";
        LOG.info("[Ctrl#{}] formation rejected: pos={} machine={} port={} actual={} requiredMin={} requiredMax={} reason={}",
                instanceId, getBlockPos(), candidate.registryName(), failure.portId(), failure.actual(), failure.requiredMin(), max, failure.reason());
    }

    private void onStructureFormed(Machine matchedMachine, BlockArray rotatedPattern, CompiledMachinePattern compiledPattern,
                                   Direction facing, Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        foundMachine = matchedMachine;
        foundPattern = rotatedPattern;
        foundCompiledPattern = compiledPattern;
        controllerFacing = facing;
        machine = matchedMachine;
        collectFoundModifiers(replacements);
        FORMED_CONTROLLERS.add(this);
        structureVersion++;
        structureDirty = false;
        structureCheckCounter = 0;
        if (!isFormed()) setFormed(true);
        updateComponents();
        clearCandidateCache();
        ComponentCounts counts = componentCounts();
        LOG.info("[Ctrl#{}] onStructureFormed: pos={} machine={} facing={} components=itemIn:{} itemOut:{} fluidIn:{} fluidOut:{} energyIn:{} energyOut:{}",
                instanceId, getBlockPos(), matchedMachine.registryName(), facing,
                counts.itemInputs(), counts.itemOutputs(), counts.fluidInputs(), counts.fluidOutputs(), counts.energyInputs(), counts.energyOutputs());
        lastFormationFailure = null;
        setChanged();
    }

    private void collectFoundModifiers(Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        List<RecipeModifier> before = foundModifierList();
        foundModifiers.clear();
        if (level != null) {
            for (var entry : replacements.entrySet()) {
                BlockState actual = level.getBlockState(getBlockPos().offset(entry.getKey()));
                for (SingleBlockModifierReplacement replacement : entry.getValue()) {
                    if (replacement.getReplacement().matches(actual)) {
                        foundModifiers.putIfAbsent(replacement.getModifierName(), replacement.getModifiers());
                    }
                }
            }
        }
        if (!before.equals(foundModifierList())) {
            modifierSnapshotVersion++;
            markRecipeDirty();
        }
    }

    private void clearFoundModifiers() {
        if (foundModifiers.isEmpty()) return;
        foundModifiers.clear();
        modifierSnapshotVersion++;
        markRecipeDirty();
    }

    private void updateComponents() {
        components.clear();
        if (level == null || foundMachine == null || foundPattern == null) return;

        resetLinkedPorts();

        for (BlockPos relativePos : componentPositions()) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (level.getBlockEntity(worldPos) instanceof ParallelControllerBlockEntity parallel) {
                components.add(new ProcessingComponent(null, parallel, worldPos, relativePos, foundPattern.tagsAt(relativePos), null));
                continue;
            }
            if (level.getBlockEntity(worldPos) instanceof FactorySchedulerBlockEntity scheduler) {
                components.add(new ProcessingComponent(null, scheduler, worldPos, relativePos, foundPattern.tagsAt(relativePos), null));
                continue;
            }
            if (!(level.getBlockEntity(worldPos) instanceof MachineComponentTile tile)) continue;

            if (tile instanceof IOPortBlockEntity port) {
                Identifier formedTexture = foundMachine.appearance().formedPortBaseTexture();
                port.bindControllerAppearance(getBlockPos(), formedTexture);
                linkedPortPositions().add(worldPos.immutable());
            }
            var component = tile.provideComponent();
            if (!(tile instanceof BlockEntity container)) continue;
            components.add(new ProcessingComponent(component, container, worldPos, relativePos, foundPattern.tagsAt(relativePos)));
        }
    }

    private void resetLinkedPorts() {
        Set<BlockPos> linkedPortPositions = linkedPortPositions();
        if (level == null) {
            linkedPortPositions.clear();
            return;
        }
        resetPortsAtCurrentStructurePositions();
        for (BlockPos portPos : linkedPortPositions) {
            if (level.getBlockEntity(portPos) instanceof IOPortBlockEntity port) {
                port.resetAppearanceBaseTexture();
            }
        }
        linkedPortPositions.clear();
    }

    private void resetPortsAtCurrentStructurePositions() {
        if (level == null || foundPattern == null) return;
        for (BlockPos relativePos : componentPositions()) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (level.getBlockEntity(worldPos) instanceof IOPortBlockEntity port) {
                port.resetAppearanceBaseTexture();
            }
        }
    }

    private Set<BlockPos> linkedPortPositions() {
        if (linkedPortPositions == null) linkedPortPositions = new HashSet<>();
        return linkedPortPositions;
    }

    private List<BlockPos> componentPositions() {
        if (foundCompiledPattern != null && controllerFacing != null) {
            return foundCompiledPattern.componentPositions(controllerFacing);
        }
        return new ArrayList<>(foundPattern.pattern().keySet());
    }

    private PortRequirementSpec.PortCounts countPorts(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (level == null || rotatedPattern == null) return PortRequirementSpec.PortCounts.empty();

        List<BlockPos> positions = compiledPattern == null ? new ArrayList<>(rotatedPattern.pattern().keySet()) : compiledPattern.portPositions(facing);
        for (BlockPos relativePos : positions) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (!(level.getBlockEntity(worldPos) instanceof IOPortBlockEntity port)) continue;
            counts.merge(port.kind().id(), 1, Integer::sum);
        }
        return PortRequirementSpec.PortCounts.of(counts);
    }

    private List<IOPortKind> portKinds(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        if (level == null || rotatedPattern == null) return List.of();

        List<BlockPos> positions = compiledPattern == null ? new ArrayList<>(rotatedPattern.pattern().keySet()) : compiledPattern.portPositions(facing);
        List<IOPortKind> kinds = new ArrayList<>();
        for (BlockPos relativePos : positions) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (level.getBlockEntity(worldPos) instanceof IOPortBlockEntity port) {
                kinds.add(port.kind());
            }
        }
        return List.copyOf(kinds);
    }

    private java.util.Optional<PortRequirementSpec.Failure> validatePortTiers(Machine candidate, BlockArray rotatedPattern,
                                                                              @Nullable CompiledMachinePattern compiledPattern,
                                                                              Direction facing) {
        return candidate.portTierRequirements().validate(portKinds(rotatedPattern, compiledPattern, facing))
                .map(failure -> new PortRequirementSpec.Failure(
                        failure.requirement().id(),
                        failure.actualPortIds().size(),
                        1,
                        java.util.OptionalInt.empty(),
                        PortRequirementSpec.FailureReason.MISSING));
    }

    private boolean isInsideCompiledBounds(BlockPos worldPos) {
        if (foundCompiledPattern == null || controllerFacing == null) return false;
        BoundingBox box = foundCompiledPattern.boundingBox(controllerFacing);
        BlockPos relative = worldPos.subtract(getBlockPos());
        return relative.getX() >= box.minX()
                && relative.getX() <= box.maxX()
                && relative.getY() >= box.minY()
                && relative.getY() <= box.maxY()
                && relative.getZ() >= box.minZ()
                && relative.getZ() <= box.maxZ();
    }

    private void onStructureChunkUnloaded(ChunkPos chunkPos) {
        if (!isFormed() || foundCompiledPattern == null || controllerFacing == null) return;
        if (!compiledBoundsTouchesChunk(chunkPos)) return;
        structureDirty = true;
        pauseActiveForUnloadedStructure();
        setChanged();
    }

    private boolean compiledBoundsTouchesChunk(ChunkPos chunkPos) {
        BoundingBox box = foundCompiledPattern.boundingBox(controllerFacing);
        int minChunkX = (getBlockPos().getX() + box.minX()) >> 4;
        int maxChunkX = (getBlockPos().getX() + box.maxX()) >> 4;
        int minChunkZ = (getBlockPos().getZ() + box.minZ()) >> 4;
        int maxChunkZ = (getBlockPos().getZ() + box.maxZ()) >> 4;
        return chunkPos.x() >= minChunkX && chunkPos.x() <= maxChunkX && chunkPos.z() >= minChunkZ && chunkPos.z() <= maxChunkZ;
    }

    private void pauseActiveForUnloadedStructure() {
        stopFactoryController();
        if (active == null) return;
        pausedActive = active;
        pausedContext = context;
        active = null;
        context = null;
        setActiveState(false);
    }

    private void resumePausedRecipeAfterStructureCheck() {
        if (active != null || pausedActive == null || pausedContext == null || redstonePaused) return;
        active = pausedActive;
        context = pausedContext;
        pausedActive = null;
        pausedContext = null;
        setActiveState(true);
    }

    private ComponentCounts componentCounts() {
        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        int energyInputs = 0;
        int energyOutputs = 0;
        for (ProcessingComponent processingComponent : components) {
            var component = processingComponent.getComponent();
            if (component == null || component.kind() == null) continue;
            switch (component.kind().id()) {
                case "item_input_bus" -> itemInputs++;
                case "item_output_bus" -> itemOutputs++;
                case "fluid_input_hatch" -> fluidInputs++;
                case "fluid_output_hatch" -> fluidOutputs++;
                case "energy_input_hatch" -> energyInputs++;
                case "energy_output_hatch" -> energyOutputs++;
                default -> { }
            }
        }
        return new ComponentCounts(itemInputs, itemOutputs, fluidInputs, fluidOutputs, energyInputs, energyOutputs);
    }

    private record ComponentCounts(int itemInputs, int itemOutputs, int fluidInputs, int fluidOutputs, int energyInputs, int energyOutputs) { }

    private void resetMachine() {
        resetMachine(true);
    }

    private void resetMachine(boolean clearFormationFailure) {
        boolean wasFormed = isFormed();
        Identifier dropped = foundMachine == null ? null : foundMachine.registryName();
        boolean hadActive = active != null;
        Identifier activeRecipe = hadActive ? active.getRecipe().id() : null;
        resetLinkedPorts();
        stopFactoryController();
        foundMachine = null;
        foundPattern = null;
        foundCompiledPattern = null;
        controllerFacing = null;
        foundModifiers.clear();
        FORMED_CONTROLLERS.remove(this);
        components.clear();
        structureDirty = true;
        structureCheckCounter = 0;
        if (active != null) {
            returnContext(context);
            active = null;
            context = null;
            setActiveState(false);
        }
        clearPendingConflictStart();
        pausedActive = null;
        returnContext(pausedContext);
        pausedContext = null;
        lastFailureUnloc = null;
        if (clearFormationFailure) lastFormationFailure = null;
        redstonePaused = false;
        if (dropped != null || wasFormed || hadActive) structureVersion++;
        markRecipeDirty();
        clearCandidateCache();
        if (wasFormed) setFormed(false);
        if (dropped != null || hadActive) {
            LOG.info("[Ctrl#{}] resetMachine: pos={} dropped={} clearedActiveRecipe={} wasFormed={}", instanceId, getBlockPos(), dropped, activeRecipe, wasFormed);
        }
        setChanged();
    }

    private void stopFactoryController() {
        FactorySchedulerBlockEntity factory = getFactoryController();
        if (factory != null) factory.stopAll();
    }

    private void broadcastStateIfChanged(boolean activeBeforeTick) {
        boolean formed = isFormed();
        boolean activeNow = isRuntimeActive();
        if (lastBroadcastFormed != null && lastBroadcastFormed == formed && lastBroadcastActive == activeNow && activeBeforeTick == activeNow) {
            return;
        }
        lastBroadcastFormed = formed;
        lastBroadcastActive = activeNow;
        if (!(level instanceof ServerLevel sl)) return;
        String name = active == null ? "" : active.getRecipe().id().toString();
        var pkt = new PktMachineStatePayload(getBlockPos(), name, formed, activeNow);
        for (var player : sl.getPlayers(p -> p.distanceToSqr(getBlockPos().getCenter()) < 64 * 64)) {
            ((ServerPlayer) player).connection.send(new ClientboundCustomPayloadPacket(pkt));
        }
    }

    private boolean tryStartNewRecipe() {
        if (!shouldSearchRecipe()) return false;
        recipeSearchAttemptCounter++;
        Identifier machineId = foundMachine == null ? null : foundMachine.registryName();
        if (machineId == null) return false;
        if (tryRestartLastRecipe(machineId)) return true;
        List<MachineRecipe> candidates = recipesForMachine();
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(this, machineId, structureVersion, getMaxParallelism(), candidates, contextPool()).compute();
        } catch (RuntimeException e) {
            LOG.warn("[Ctrl#{}] tryStartNewRecipe: recipe search failed at pos={}; retrying later", instanceId, getBlockPos(), e);
            clearPendingConflictStart();
            recipeSearchRetryCounter++;
            lastFailureUnloc = RecipeCraftingContext.FAILURE_SEARCH_EXCEPTION;
            return false;
        }
        if (result.success()) {
            return applySearchResult(result, candidates.size());
        }
        clearPendingConflictStart();
        recipeSearchRetryCounter++;
        lastFailureUnloc = result.failureUnloc();
        return false;
    }

    private boolean shouldSearchRecipe() {
        if (recipeSearchRetryCounter <= 0) return true;
        long ticks = recipeSearchAttemptCounter;
        if (level != null) {
            try {
                ticks = level.getGameTime();
            } catch (NullPointerException ignored) {
                ticks = recipeSearchAttemptCounter;
            }
        }
        return ticks % nextRecipeSearchDelay() == 0;
    }

    private int nextRecipeSearchDelay() {
        if (recipeSearchRetryCounter <= 0) return 1;
        return Math.min(100, 5 + recipeSearchRetryCounter * 5);
    }

    void onRecipeInputsChanged() {
        recipeSearchRetryCounter = 0;
    }

    private boolean applySearchResult(RecipeSearchResult result, int candidateCount) {
        if (!isSearchResultCurrent(result)) {
            returnContext(result.context());
            return false;
        }
        ActiveMachineRecipe next = result.activeRecipe();
        RecipeCraftingContext nextContext = result.context();
        if (shouldDelayConflictProneStart(result, currentGameTime())) {
            returnContext(nextContext);
            return false;
        }
        active = next;
        context = nextContext;
        if (!next.start(nextContext)) {
            active = null;
            context = null;
            returnContext(nextContext);
            clearPendingConflictStart();
            recipeSearchRetryCounter++;
            lastFailureUnloc = nextContext.getLastFailureUnloc();
            return false;
        }
        setActiveState(true);
        rememberLastRecipe(next.getRecipe());
        recipeSearchRetryCounter = 0;
        lastFailureUnloc = null;
        setChanged();
        return true;
    }

    private boolean shouldDelayConflictProneStart(RecipeSearchResult result, long gameTime) {
        return recipeStartDelay().shouldDelay(result.activeRecipe().getRecipe().id(), result.hasMoreSpecificPendingInputCandidate(), gameTime);
    }

    private void clearPendingConflictStart() {
        recipeStartDelay().clear();
    }

    private RecipeStartDelay recipeStartDelay() {
        if (recipeStartDelay == null) recipeStartDelay = new RecipeStartDelay();
        return recipeStartDelay;
    }

    private long currentGameTime() {
        if (level == null) return recipeSearchAttemptCounter;
        try {
            return level.getGameTime();
        } catch (NullPointerException ignored) {
            return recipeSearchAttemptCounter;
        }
    }

    private boolean isSearchResultCurrent(RecipeSearchResult result) {
        return isFormed()
                && foundMachine != null
                && foundMachine.registryName().equals(result.machineId())
                && structureVersion == result.structureVersion()
                && active == null;
    }

    private boolean tryRestartLastRecipe(Identifier machineId) {
        if (recipeDirty || lastRecipe == null || active != null || foundMachine == null) return false;
        if (!machineId.equals(lastRecipe.machineId())) return false;
        if (lastRecipeStructureVersion != structureVersion || lastRecipeModifierSnapshotVersion != modifierSnapshotVersion) return false;
        ActiveMachineRecipe next = new ActiveMachineRecipe(lastRecipe, getMaxParallelism());
        RecipeCraftingContext nextContext = contextPool().borrow(next, this);
        try {
            if (!next.canStartCrafting(nextContext)) return false;
            active = next;
            context = nextContext;
            if (!next.start(nextContext)) {
                active = null;
                context = null;
                recipeDirty = true;
                return false;
            }
            setActiveState(true);
            recipeSearchRetryCounter = 0;
            lastFailureUnloc = null;
            setChanged();
            return true;
        } finally {
            if (active != next) returnContext(nextContext);
        }
    }

    private void rememberLastRecipe(MachineRecipe recipe) {
        lastRecipe = recipe;
        lastRecipeStructureVersion = structureVersion;
        lastRecipeModifierSnapshotVersion = modifierSnapshotVersion;
        recipeDirty = false;
    }

    private boolean isSearchResultCurrentForFactory(RecipeSearchResult result) {
        return isFormed()
                && foundMachine != null
                && foundMachine.registryName().equals(result.machineId())
                && structureVersion == result.structureVersion();
    }

    private void tickActiveRecipe() {
        if (active == null || context == null) return;
        if (!context.isStructureVersionCurrent()) {
            if (context.isStructureVersionOnlyCurrent()) {
                context.refreshModifierSnapshot(foundModifierList());
                active.refreshTotalTick(context);
            } else {
                context = new RecipeCraftingContext(this);
                context.setStructureModifiers(foundModifierList());
            }
        }
        ActiveMachineRecipe.TickStatus status = active.tick(context, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, currentGameTime())));
        if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
            returnContext(context);
            active = null;
            context = null;
            setActiveState(false);
        } else if (status == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = context.getLastFailureUnloc();
            if (active.getRecipe().doesCancelRecipeOnPerTickFailure()) {
                returnContext(context);
                active = null;
                context = null;
                setActiveState(false);
            }
        }
        setChanged();
    }

    private void setActiveState(boolean activeState) {
        if (level == null || level.isClientSide()) return;
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != activeState) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, activeState), 3);
        }
    }

    @Override
    public void setRemoved() {
        stopFactoryController();
        super.setRemoved();
    }

    void bindDefaultMachine() {
        bindDefaultMachine(machineIdFromState(getBlockState()));
    }

    void bindDefaultMachine(Identifier machineId) {
        Machine resolved = cn.howxu.mmcr.api.machine.MachineRegistry.getMachine(machineId);
        if (resolved == null) {
            for (Machine candidate : cn.howxu.mmcr.api.machine.MachineRegistry.getAll().values()) {
                if (candidate.controller().id().equals(machineId)) {
                    resolved = candidate;
                    break;
                }
            }
        }
        LOG.info("[Ctrl#{}] bindDefaultMachine: resolving state-bound machineId={} → resolved={}", instanceId, machineId, resolved == null ? null : resolved.registryName());
        setMachine(resolved);
    }

    private List<MachineRecipe> recipesForMachine() {
        Identifier machineId = machine == null ? null : machine.registryName();
        if (machineId == null) return List.of();
        int datapackCount = datapackRecipeCount();
        long reloadVersion = RecipeRegistry.reloadVersion();
        if (machineId.equals(cachedCandidatesMachineId)
                && cachedCandidatesReloadVersion == reloadVersion
                && cachedDatapackRecipeCount == datapackCount) {
            return cachedCandidates;
        }
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        for (MachineRecipe recipe : RecipeRegistry.byMachine(machine)) {
            recipes.put(recipe.id(), recipe);
        }
        if (level instanceof ServerLevel sl) {
            for (RecipeHolder<?> holder : sl.recipeAccess().getRecipes()) {
                if (holder.value() instanceof MachineRecipe recipe
                        && recipe.machineId().equals(machine.registryName())) {
                    recipes.putIfAbsent(recipe.id(), recipe);
                }
            }
        }
        cachedCandidatesMachineId = machineId;
        cachedCandidatesReloadVersion = reloadVersion;
        cachedDatapackRecipeCount = datapackCount;
        cachedCandidates = List.copyOf(recipes.values());
        return cachedCandidates;
    }

    private int datapackRecipeCount() {
        if (!(level instanceof ServerLevel sl)) return 0;
        int count = 0;
        for (RecipeHolder<?> holder : sl.recipeAccess().getRecipes()) {
            if (holder.value() instanceof MachineRecipe recipe
                    && machine != null
                    && recipe.machineId().equals(machine.registryName())) {
                count++;
            }
        }
        return count;
    }

    private void clearCandidateCache() {
        cachedCandidatesMachineId = null;
        cachedCandidatesReloadVersion = Long.MIN_VALUE;
        cachedDatapackRecipeCount = -1;
        cachedCandidates = List.of();
        markRecipeDirty();
    }

    private void returnContext(@Nullable RecipeCraftingContext returnedContext) {
        contextPool().returnContext(returnedContext);
    }

    private RecipeCraftingContextPool contextPool() {
        if (contextPool == null) contextPool = RecipeCraftingContextPool.global();
        return contextPool;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (active != null && context != null) {
            output.putString("recipe_state", "active");
            output.putBoolean("has_recipe_context", true);
            active.serialize(output.child("active_recipe"));
            context.serialize(output.child("active_context"));
        } else if (pausedActive != null && pausedContext != null) {
            output.putString("recipe_state", "paused");
            output.putBoolean("has_recipe_context", true);
            pausedActive.serialize(output.child("active_recipe"));
            pausedContext.serialize(output.child("active_context"));
        }
        if (lastFailureUnloc != null) output.putString("last_failure_unloc", lastFailureUnloc);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pausedActive = null;
        pausedContext = null;
        redstonePaused = false;
        active = null;
        context = null;
        String savedFailure = input.getStringOr("last_failure_unloc", "");
        lastFailureUnloc = savedFailure.isEmpty() ? null : savedFailure;
        String recipeState = input.getStringOr("recipe_state", input.getBooleanOr("has_active", false) ? "active" : "");
        if (recipeState.isEmpty()) return;
        if (!input.getBooleanOr("has_recipe_context", false)) {
            LOG.warn("[Ctrl#{}] loadAdditional: stored recipe state {} has no context; clearing slot", instanceId, recipeState);
            return;
        }
        ActiveMachineRecipe restored = ActiveMachineRecipe.from(input.childOrEmpty("active_recipe"));
        if (restored.getRecipe() == null) {
            Identifier missing = restored.getRegistryName() == null ? null : Identifier.parse(restored.getRegistryName());
            LOG.warn("[Ctrl#{}] loadAdditional: stored recipe {} not found in registry; clearing slot", instanceId, missing);
            return;
        }
        if ("paused".equals(recipeState)) {
            pausedActive = restored;
            pausedContext = RecipeCraftingContext.from(this, input.childOrEmpty("active_context"));
        } else if ("active".equals(recipeState)) {
            active = restored;
            context = RecipeCraftingContext.from(this, input.childOrEmpty("active_context"));
        } else {
            LOG.warn("[Ctrl#{}] loadAdditional: stored recipe state {} is invalid; clearing slot", instanceId, recipeState);
            return;
        }
        structureDirty = true;
        LOG.info("[Ctrl#{}] loadAdditional: pos={} restored {} recipe={} tick={}/{}", instanceId, getBlockPos(), recipeState, restored.getRecipe().id(), restored.getTick(), restored.getTotalTick());
        setChanged();
    }
}
