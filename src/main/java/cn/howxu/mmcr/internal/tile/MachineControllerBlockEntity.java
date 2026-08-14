package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.level.LevelMismatch;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.LevelInsufficientFailure;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeCandidateIndex;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.SmartInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewBuilder;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewPredicates;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.internal.recipe.RecipeStartDelay;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MachineControllerBlockEntity extends BlockEntity implements FactorySchedulerBlockEntity.SyncListener {

    private static final Logger LOG = LoggerFactory.getLogger(MachineControllerBlockEntity.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    private static final Set<MachineControllerBlockEntity> FORMED_CONTROLLERS = ConcurrentHashMap.newKeySet();
    private static final int PREVIEW_RECEIVER_WINDOW_TICKS = 8 * 20;
    private final int instanceId = INSTANCE_COUNTER.incrementAndGet();

    private Machine machine;
    private Machine foundMachine;
    private BlockArray foundPattern;
    private CompiledMachinePattern foundCompiledPattern;
    private Direction controllerFacing;
    private Direction matchedRollFacing = Direction.SOUTH;
    private ActiveMachineRecipe active;
    private RecipeCraftingContext context;
    private final List<ProcessingComponent> components = new ArrayList<>();
    private final Map<String, List<RecipeModifier>> foundModifiers = new LinkedHashMap<>();
    private Map<Identifier, MachineLevel> foundLevels = Map.of();
    private long structureVersion;
    private long modifierSnapshotVersion;
    private int structureCheckCounter;
    private boolean structureDirty = true;
    private boolean clientActive;
    private Boolean lastBroadcastFormed;
    private boolean lastBroadcastActive;
    private @Nullable String lastFailureUnloc;
    private @Nullable LevelInsufficientFailure recipeFailure;
    private @Nullable PortRequirementSpec.Failure lastFormationFailure;
    private @Nullable String lastStructureMismatchDiagnostic;
    private @Nullable Object lastStructureError;
    private boolean redstonePaused;
    private @Nullable ActiveMachineRecipe pausedActive;
    private @Nullable RecipeCraftingContext pausedContext;
    private boolean restoredRecipeContext;
    private RecipeCraftingContextPool contextPool = RecipeCraftingContextPool.global();
    private Set<BlockPos> linkedPortPositions = new HashSet<>();
    private int recipeSearchRetryCounter;
    private long recipeSearchAttemptCounter;
    private long lastRecipeSearchRegistryVersion = Long.MIN_VALUE;
    private long cachedCandidatesReloadVersion = Long.MIN_VALUE;
    private int cachedDatapackRecipeCount = -1;
    private @Nullable Identifier cachedCandidatesMachineId;
    private List<MachineRecipe> cachedCandidates = List.of();
    private RecipeCandidateIndex cachedCandidateIndex = RecipeCandidateIndex.empty();
    private RecipeStartDelay recipeStartDelay = new RecipeStartDelay();
    private @Nullable MachineRecipe lastRecipe;
    private long lastRecipeStructureVersion = Long.MIN_VALUE;
    private long lastRecipeModifierSnapshotVersion = Long.MIN_VALUE;
    private @Nullable Identifier lockedRecipeId;
    private boolean recipeDirty = true;
    private @Nullable StructureClaimRegistry.ResourceDomain resourceDomain;
    private boolean sharedStartPending;
    private @Nullable RecipeCraftingContext pendingSharedStartContext;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingSharedStartDomain;
    private boolean sharedTickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingSharedTickDomain;
    private boolean syncedRuntimeActive;
    private Map<UUID, Long> previewReceivers = new LinkedHashMap<>();

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.controllerFor(machineIdFromState(state)).get(), pos, state);
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
        foundLevels = Map.of();
        this.machine = m;
        markRecipeDirty();
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

    public Map<Identifier, MachineLevel> getFoundLevels() {
        return foundLevels;
    }

    public @Nullable Object getLastStructureError() {
        return lastStructureError;
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
        if (f) notifyPreviewReceiversStructureFormed();
    }

    public MachineRecipe getActiveRecipe() { return active == null ? null : active.getRecipe(); }

    public int getTickCounter() { return active == null ? 0 : active.getTick(); }

    public ActiveMachineRecipe getActive() { return active; }

    public long getStructureVersion() { return structureVersion; }

    public long getModifierSnapshotVersion() { return modifierSnapshotVersion; }

    public @Nullable String getLastFailureUnloc() { return lastFailureUnloc; }

    public @Nullable LevelInsufficientFailure getRecipeFailure() { return recipeFailure; }

    public @Nullable PortRequirementSpec.Failure getLastFormationFailure() { return lastFormationFailure; }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        if (level instanceof ServerLevel serverLevel) {
            return StructureClaimRegistry.get(serverLevel).domainFor(getBlockPos());
        }
        return resourceDomain;
    }

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

    public void applyClientState(String recipeName, boolean formed, boolean active, List<String> foundLevelIds) {
        if (level == null || !level.isClientSide()) return;
        if (isFormed() != formed) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, formed), 3);
        }
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != active) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, active), 3);
        }
        this.clientActive = active;
        Map<Identifier, MachineLevel> levels = new LinkedHashMap<>();
        for (String id : foundLevelIds) {
            MachineLevel foundLevel = cn.howxu.mmcr.api.machine.level.MachineLevelRegistry.getLevel(Identifier.parse(id));
            if (foundLevel != null) levels.put(foundLevel.typeId(), foundLevel);
        }
        this.foundLevels = Map.copyOf(levels);
    }

    public boolean hasClientActiveRecipe() { return clientActive; }

    public boolean isRuntimeActive() {
        if (level != null && level.isClientSide()) return clientActive || getBlockState().getValue(MachineControllerBlock.ACTIVE);
        if (!isFormed() || redstonePaused || !isStructureAreaLoaded()) return false;
        if (active != null) return true;
        FactorySchedulerBlockEntity factory = getFactoryController();
        return factory != null && factory.activeLaneCount() > 0;
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
        unlinkLinkedPorts();
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
            }
        }
        int base = Math.max(1, (int) max);
        int levelBonus = foundLevels == null ? 0 : foundLevels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Identifier::toString)))
                .map(Map.Entry::getValue)
                .mapToInt(foundLevel -> foundLevel.modifier().parallelismBonus())
                .sum();
        return Math.max(1, Math.min(machine.maxParallelism(), base + levelBonus));
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
        int levelBonus = foundLevels == null ? 0 : foundLevels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Identifier::toString)))
                .map(Map.Entry::getValue)
                .mapToInt(foundLevel -> foundLevel.modifier().factoryThreadBonus())
                .sum();
        long effective = Math.max(1, aggregatedThreads) + levelBonus;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, effective));
    }

    public int factorySchedulerThreadCount() {
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof FactorySchedulerBlockEntity scheduler) {
                return scheduler.threadCount();
            }
        }
        return 0;
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
            syncRuntimeStateIfChanged();
            if (active != null && context != null) {
                pausedActive = active;
                pausedContext = context;
                active = null;
                context = null;
                setActiveState(false);
                syncRuntimeStateIfChanged();
                broadcastStateIfChanged(true);
            } else if (active != null || context != null) {
                active = null;
                context = null;
                syncRuntimeStateIfChanged();
            }
            structureDirty = true;
            if (shouldCheckStructure()) checkStructure();
            setChanged();
            broadcastStateIfChanged(activeBefore);
            return;
        }
        redstonePaused = false;
        FactorySchedulerBlockEntity factory = getFactoryController();
        if (factory != null) factory.resume();
        syncRuntimeStateIfChanged();
        if (shouldCheckStructure()) checkStructure();
        if (active == null && pausedActive != null && pausedContext != null) {
            active = pausedActive;
            context = pausedContext;
            context.refreshController(this);
            pausedActive = null;
            pausedContext = null;
            structureDirty = true;
            markRecipeDirty();
            setActiveState(true);
            syncRuntimeStateIfChanged();
        } else if (pausedActive != null || pausedContext != null) {
            pausedActive = null;
            pausedContext = null;
        }

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
        if (sharedStartPending && !isCurrentSharedDomain(pendingSharedStartDomain)) {
            RecipeCraftingContext pendingContext = pendingSharedStartContext;
            clearPendingSharedStart();
            returnContext(pendingContext);
        }
        if (active == null && !sharedStartPending) {
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
        syncRuntimeStateIfChanged();
        if (factory.activeThreadCount() > 0) {
            lastFailureUnloc = null;
            recipeFailure = null;
        }
    }

    @Override
    public void syncFactoryScheduler() {
        syncRuntimeStateIfChanged();
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
            var replacements = replacementsFor(foundMachine, foundCompiledPattern, facing, foundPattern, matchedRollFacing);
            boolean stillMatches = foundCompiledPattern == null || !replacements.isEmpty()
                    ? StructureMatcher.matchesRotated(foundPattern, level, getBlockPos(), replacements)
                    : StructureMatcher.matchesCompiled(foundCompiledPattern, facing, matchedRollFacing, level, getBlockPos());
            if (stillMatches) {
                var levels = resolveLevels(foundMachine, facing, matchedRollFacing);
                if (levels.mismatch() != null) {
                    recordLevelMismatch(levels.mismatch());
                    resetMachine(false);
                    return;
                }
                collectFoundModifiers(replacements);
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
                failure = validateFactoryControllerCount(foundMachine, foundPattern, foundCompiledPattern, facing);
                if (failure.isPresent()) {
                    recordFormationFailure(foundMachine, failure.get());
                    resetMachine(false);
                    return;
                }
                if (!isFormed()) setFormed(true);
                foundLevels = levels.foundLevels();
                updateComponents();
                resumePausedRecipeAfterStructureCheck();
                return;
            }
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

        for (CandidatePattern candidatePattern : candidatePatterns(candidate, facing)) {
            if (tryFormMachine(candidate, facing, candidatePattern)) return true;
        }
        return false;
    }

    public boolean diagnoseFirstStructureMismatch(ServerPlayer player) {
        if (level == null || level.isClientSide() || isFormed()) return false;
        if (machine == null) bindDefaultMachine();
        if (machine == null) return false;

        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        for (CandidatePattern candidatePattern : candidatePatterns(machine, facing)) {
            BlockArray rotatedPattern = candidatePattern.pattern();
            CompiledMachinePattern compiled = compiledFor(machine, rotatedPattern, facing);
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = replacementsFor(machine, compiled, facing, rotatedPattern, candidatePattern.rollFacing());
            var mismatch = StructureMatcher.firstMismatch(rotatedPattern, level, getBlockPos(), replacements);
            if (mismatch.isPresent()) {
                sendStructureMismatchDiagnostic(player, mismatch.get());
                return true;
            }
        }
        if (lastFormationFailure != null) {
            sendFormationFailureDiagnostic(player, lastFormationFailure);
            return true;
        }
        return false;
    }

    public Optional<MultiblockPreviewSnapshot> createStructurePreviewSnapshot(int maxEntries) {
        if (level == null || level.isClientSide() || isFormed()) return Optional.empty();
        if (machine == null) bindDefaultMachine();
        if (machine == null) return Optional.empty();

        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        for (CandidatePattern candidatePattern : candidatePatterns(machine, facing)) {
            MultiblockPreviewSnapshot snapshot = MultiblockPreviewBuilder.build(level, getBlockPos(), candidatePattern.pattern(), maxEntries);
            if (!snapshot.isEmpty()) return Optional.of(snapshot);
        }
        return Optional.empty();
    }

    public Optional<Machine> boundMachine() {
        if (machine == null) bindDefaultMachine();
        return Optional.ofNullable(machine);
    }

    public BlockArray assemblyPattern(Machine candidate) {
        if (foundPattern != null) return foundPattern;
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        return candidatePatterns(candidate, facing).getFirst().pattern();
    }

    public boolean sendStructurePreview(ServerPlayer player) {
        Optional<MultiblockPreviewSnapshot> snapshot = createStructurePreviewSnapshot(PktMultiblockPreviewPayload.MAX_ENTRIES);
        if (snapshot.isEmpty()) return false;
        PacketDistributor.sendToPlayer(player, new PktMultiblockPreviewPayload(snapshot.get()));
        rememberPreviewReceiver(player.getUUID(), level.getGameTime(), PREVIEW_RECEIVER_WINDOW_TICKS);
        return true;
    }

    void rememberPreviewReceiverForTesting(UUID playerId, long now, int durationTicks) {
        rememberPreviewReceiver(playerId, now, durationTicks);
    }

    Set<UUID> consumeActivePreviewReceiverIdsForTesting(long now) {
        return consumeActivePreviewReceiverIds(now);
    }

    private void rememberPreviewReceiver(UUID playerId, long now, int durationTicks) {
        previewReceivers().put(playerId, now + Math.max(1, durationTicks));
    }

    private Set<UUID> consumeActivePreviewReceiverIds(long now) {
        Set<UUID> activeReceivers = new HashSet<>();
        Iterator<Map.Entry<UUID, Long>> iterator = previewReceivers().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() > now) activeReceivers.add(entry.getKey());
            iterator.remove();
        }
        return activeReceivers;
    }

    private Map<UUID, Long> previewReceivers() {
        if (previewReceivers == null) previewReceivers = new LinkedHashMap<>();
        return previewReceivers;
    }

    private void notifyPreviewReceiversStructureFormed() {
        if (!(level instanceof ServerLevel serverLevel)) {
            previewReceivers().clear();
            return;
        }
        if (previewReceivers().isEmpty()) return;
        Set<UUID> receiverIds = consumeActivePreviewReceiverIds(serverLevel.getGameTime());
        if (receiverIds.isEmpty()) return;
        PktMultiblockPreviewPayload clearPayload = PktMultiblockPreviewPayload.clear(serverLevel.dimension(), getBlockPos());
        for (UUID receiverId : receiverIds) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(receiverId);
            if (player != null) PacketDistributor.sendToPlayer(player, clearPayload);
        }
    }

    private void sendStructureMismatchDiagnostic(ServerPlayer player, StructureMatcher.Mismatch mismatch) {
        BlockPos pos = mismatch.worldPos();
        player.sendSystemMessage(Component.translatable(
                "message.mmcr.multiblock_mismatch",
                styledPosition(pos),
                describeExpected(mismatch.expected()),
                mismatch.actualState().getBlock().getName().copy().withStyle(ChatFormatting.RED)));
        PacketDistributor.sendToPlayer(player, new PktMultiblockMismatchHighlightPayload(level.dimension(), pos));
    }

    private void sendFormationFailureDiagnostic(ServerPlayer player, PortRequirementSpec.Failure failure) {
        player.sendSystemMessage(describeFormationFailure(failure));
    }

    private static Component describeFormationFailure(PortRequirementSpec.Failure failure) {
        Component port = describeRequiredPort(failure.portId());
        if (failure.reason() == PortRequirementSpec.FailureReason.TOO_MANY && failure.requiredMax().isPresent()) {
            return Component.translatable("message.mmcr.multiblock_requirement.maximum", failure.requiredMax().getAsInt(), port);
        }
        if (failure.requiredMax().isPresent() && failure.requiredMin() == failure.requiredMax().getAsInt()) {
            return Component.translatable("message.mmcr.multiblock_requirement.exact", failure.requiredMin(), port);
        }
        return Component.translatable("message.mmcr.multiblock_requirement.minimum", failure.requiredMin(), port);
    }

    private static Component describeRequiredPort(String portId) {
        String[] tierRequirement = portId.split(">=", 2);
        String baseId = tierRequirement[0];
        Component base = describePortBase(baseId);
        if (tierRequirement.length == 2) {
            return Component.translatable("message.mmcr.port_requirement.minimum_tier", describePortTier(tierRequirement[1]), base);
        }
        return base;
    }

    private static Component describePortBase(String portId) {
        if (portId.startsWith("item_input_bus")) return Component.translatable("message.mmcr.port_requirement.item_input");
        if (portId.startsWith("item_output_bus")) return Component.translatable("message.mmcr.port_requirement.item_output");
        if (portId.startsWith("fluid_input_hatch")) return Component.translatable("message.mmcr.port_requirement.fluid_input");
        if (portId.startsWith("fluid_output_hatch")) return Component.translatable("message.mmcr.port_requirement.fluid_output");
        if (portId.startsWith("energy_input_hatch")) return Component.translatable("message.mmcr.port_requirement.energy_input");
        if (portId.startsWith("energy_output_hatch")) return Component.translatable("message.mmcr.port_requirement.energy_output");
        if (portId.equals("factory_controller")) return Component.translatable("message.mmcr.port_requirement.factory_controller");
        return Component.literal(portId);
    }

    private static Component describePortTier(String tierId) {
        return switch (tierId) {
            case "tiny" -> Component.translatable("message.mmcr.port_tier.tiny");
            case "small" -> Component.translatable("message.mmcr.port_tier.small");
            case "normal" -> Component.translatable("message.mmcr.port_tier.normal");
            case "reinforced" -> Component.translatable("message.mmcr.port_tier.reinforced");
            case "big" -> Component.translatable("message.mmcr.port_tier.big");
            case "huge" -> Component.translatable("message.mmcr.port_tier.huge");
            case "ludicrous" -> Component.translatable("message.mmcr.port_tier.ludicrous");
            case "vacuum" -> Component.translatable("message.mmcr.port_tier.vacuum");
            case "ultimate" -> Component.translatable("message.mmcr.port_tier.ultimate");
            default -> Component.literal(tierId);
        };
    }

    private static Component describeExpected(BlockPredicate expected) {
        return rawExpectedDescription(expected).copy().withStyle(ChatFormatting.GREEN);
    }

    private static Component rawExpectedDescription(BlockPredicate expected) {
        return switch (expected) {
            case BlockPredicate.OfBlock ofBlock -> ofBlock.block().getName();
            case BlockPredicate.OfBlockState ofState -> ofState.state().getBlock().getName();
            case BlockPredicate.OfTag ofTag -> Component.literal("#" + ofTag.tag().location());
            case BlockPredicate.AnyOf anyOf -> anyOf.children().isEmpty()
                    ? Component.literal("<empty>")
                    : MultiblockPreviewPredicates.representativeValue(expected,
                            predicate -> Optional.of(rawExpectedDescription(predicate)))
                    .orElseGet(() -> rawExpectedDescription(anyOf.children().getFirst()));
            case BlockPredicate.Air ignored -> Component.translatable("block.minecraft.air");
            case BlockPredicate.Any ignored -> Component.literal("any block");
        };
    }

    private static Component styledPosition(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN);
    }

    private List<CandidatePattern> candidatePatterns(Machine candidate, Direction facing) {
        if (!facing.getAxis().isVertical()) {
            return List.of(new CandidatePattern(BlockArrayCache.get(candidate.pattern(), facing), Direction.SOUTH));
        }

        Direction rollFacing = BlockRotator.normalizedRoll(facing, getBlockState().getValue(MachineControllerBlock.ROLL_FACING));
        if (!candidate.controller().fullyRotationallySymmetric()) {
            return List.of(new CandidatePattern(BlockArrayCache.get(candidate.pattern(), facing, rollFacing), rollFacing));
        }

        List<CandidatePattern> patterns = new ArrayList<>(4);
        for (Direction candidateRoll : Direction.Plane.HORIZONTAL) {
            patterns.add(new CandidatePattern(BlockArrayCache.get(candidate.pattern(), facing, candidateRoll), candidateRoll));
        }
        return patterns;
    }

    private boolean tryFormMachine(Machine candidate, Direction facing, CandidatePattern candidatePattern) {
        BlockArray rotatedPattern = candidatePattern.pattern();
        var compiled = compiledFor(candidate, rotatedPattern, facing);
        var replacements = replacementsFor(candidate, compiled, facing, rotatedPattern, candidatePattern.rollFacing());
        boolean matches = compiled == null
                ? StructureMatcher.matchesRotated(rotatedPattern, level, getBlockPos(), replacements)
                : StructureMatcher.matchesCompiled(compiled, facing, candidatePattern.rollFacing(), level, getBlockPos());
        if (!matches) {
            recordStructureMismatch(candidate, facing, rotatedPattern, replacements);
            return false;
        }

        var levels = resolveLevels(candidate, facing, candidatePattern.rollFacing());
        if (levels.mismatch() != null) {
            recordLevelMismatch(levels.mismatch());
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

        failure = validateFactoryControllerCount(candidate, rotatedPattern, compiled, facing);
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            StructureClaimRegistry.ClaimResult result = StructureClaimRegistry.get(serverLevel)
                    .claim(getBlockPos(), componentClaims(rotatedPattern, compiled, facing));
            if (!result.accepted()) {
                StructureClaimRegistry.Conflict conflict = result.conflict();
                lastFormationFailure = new PortRequirementSpec.Failure(
                        "component_claim_conflict component=" + conflict.componentPos() + " owner=" + conflict.ownerPos(),
                        0, 1, java.util.OptionalInt.empty(), PortRequirementSpec.FailureReason.MISSING);
                return false;
            }
        }

        lastFormationFailure = null;
        lastStructureMismatchDiagnostic = null;
        onStructureFormed(candidate, rotatedPattern, compiled, facing, candidatePattern.rollFacing(), replacements, levels.foundLevels());
        return true;
    }

    private Map<BlockPos, List<SingleBlockModifierReplacement>> replacementsFor(
            Machine candidate, CompiledMachinePattern compiled, Direction facing, BlockArray rotatedPattern, Direction rollFacing) {
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
    }

    private @Nullable CompiledMachinePattern compiledFor(Machine candidate, BlockArray rotatedPattern, Direction facing) {
        if (facing.getAxis().isVertical()) return null;
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

    static String formationFailureDiagnostic(Machine candidate, Direction facing, BlockPos ctrlPos,
                                             PortRequirementSpec.Failure failure) {
        return "machine=" + candidate.registryName()
                + " facing=" + facing.name()
                + " controllerPos=" + ctrlPos
                + " reason=portRequirementMismatch"
                + " portId=" + failure.portId()
                + " actual=" + failure.actual()
                + " requiredMin=" + failure.requiredMin()
                + " requiredMax=" + (failure.requiredMax().isPresent() ? failure.requiredMax().getAsInt() : "unbounded")
                + " failureReason=" + failure.reason();
    }

    private void recordFormationFailure(Machine candidate, PortRequirementSpec.Failure failure) {
        if (failure.equals(lastFormationFailure)) return;
        lastFormationFailure = failure;
        String diagnostic = formationFailureDiagnostic(candidate,
                getBlockState().getValue(MachineControllerBlock.FACING), getBlockPos(), failure);
        lastStructureMismatchDiagnostic = diagnostic;
    }

    private StructureMatcher.LevelResolution resolveLevels(Machine candidate, Direction facing, Direction rollFacing) {
        MachineStructureDefinition definition = MachineStructureRegistry.dynamicSnapshot().get(candidate.registryName());
        if (definition == null || definition.levelSlots().isEmpty()) {
            return new StructureMatcher.LevelResolution(Map.of(), null);
        }
        Map<BlockPos, Identifier> slots = new LinkedHashMap<>();
        Direction normalizedRoll = BlockRotator.normalizedRoll(facing, rollFacing);
        for (var entry : definition.levelSlots().entrySet()) {
            slots.put(BlockRotator.rotateSouthTo(entry.getKey(), facing, normalizedRoll), entry.getValue());
        }
        return StructureMatcher.resolveLevels(slots, level, getBlockPos());
    }

    private void recordLevelMismatch(LevelMismatch mismatch) {
        lastStructureError = mismatch;
        lastFormationFailure = null;
        lastStructureMismatchDiagnostic = null;
    }

    private void onStructureFormed(Machine matchedMachine, BlockArray rotatedPattern, CompiledMachinePattern compiledPattern,
                                   Direction facing, Direction rollFacing, Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                   Map<Identifier, MachineLevel> levels) {
        foundMachine = matchedMachine;
        foundPattern = rotatedPattern;
        foundCompiledPattern = compiledPattern;
        controllerFacing = facing;
        matchedRollFacing = rollFacing;
        machine = matchedMachine;
        if (level instanceof ServerLevel serverLevel) {
            resourceDomain = StructureClaimRegistry.get(serverLevel).domainFor(getBlockPos());
        }
        foundLevels = levels;
        collectFoundModifiers(replacements);
        FORMED_CONTROLLERS.add(this);
        structureVersion++;
        structureDirty = false;
        structureCheckCounter = 0;
        if (!isFormed()) setFormed(true);
        updateComponents();
        resumePausedRecipeAfterStructureCheck();
        clearCandidateCache();
        lastFormationFailure = null;
        lastStructureError = null;
        setChanged();
        syncLevelState();
    }

    private List<StructureClaimRegistry.Claim> componentClaims(BlockArray pattern,
                                                                 @Nullable CompiledMachinePattern compiled,
                                                                 Direction facing) {
        List<StructureClaimRegistry.Claim> claims = new ArrayList<>();
        if (level == null) return claims;
        for (BlockPos relativePos : componentPositions(pattern, compiled, facing)) {
            BlockEntity entity = level.getBlockEntity(getBlockPos().offset(relativePos));
            if (entity instanceof MachineComponentTile tile) {
                claims.add(new StructureClaimRegistry.Claim(entity.getBlockPos(), tile.claimPolicy()));
            } else if (entity instanceof ParallelControllerBlockEntity || entity instanceof FactorySchedulerBlockEntity) {
                claims.add(new StructureClaimRegistry.Claim(entity.getBlockPos(), ComponentClaimPolicy.EXCLUSIVE));
            }
        }
        return claims;
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
        List<SmartInterfaceBlockEntity> previousSmartInterfaces = components.stream()
                .map(ProcessingComponent::getContainer)
                .filter(SmartInterfaceBlockEntity.class::isInstance)
                .map(SmartInterfaceBlockEntity.class::cast)
                .toList();
        components.clear();
        if (level == null || foundMachine == null || foundPattern == null) return;

        unlinkLinkedPorts();

        List<SmartInterfaceBlockEntity> smartInterfaces = new ArrayList<>();
        for (BlockPos relativePos : componentPositions()) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof SmartInterfaceBlockEntity smartInterface) {
                smartInterfaces.add(smartInterface);
            }
        }
        var registration = MachineDefinitions.getRegistration(foundMachine.registryName());
        if (registration != null) {
            new SmartInterfaceBindingCoordinator(Map.of()).unbindAll(this, previousSmartInterfaces.stream()
                    .filter(smartInterface -> !smartInterfaces.contains(smartInterface))
                    .toList());
            new SmartInterfaceBindingCoordinator(registration.smartInterfaceTypes(), registration.shareSmartInterfaces())
                    .reconcile(this, smartInterfaces);
        }

        for (BlockPos relativePos : componentPositions()) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (level.getBlockEntity(worldPos) instanceof SmartInterfaceBlockEntity smartInterface) {
                if (smartInterface.hasController(getBlockPos())) {
                    linkedPortPositions().add(worldPos.immutable());
                }
                components.add(new ProcessingComponent(null, smartInterface, worldPos, relativePos, foundPattern.tagsAt(relativePos), null));
                continue;
            }
            if (level.getBlockEntity(worldPos) instanceof ParallelControllerBlockEntity parallel) {
                parallel.linkControllerAppearance(getBlockPos(), foundMachine.appearance().formedPortBaseTexture());
                linkedPortPositions().add(worldPos.immutable());
                components.add(new ProcessingComponent(null, parallel, worldPos, relativePos, foundPattern.tagsAt(relativePos), null));
                continue;
            }
            if (level.getBlockEntity(worldPos) instanceof FactorySchedulerBlockEntity scheduler) {
                scheduler.linkControllerAppearance(getBlockPos(), foundMachine.appearance().formedPortBaseTexture());
                linkedPortPositions().add(worldPos.immutable());
                components.add(new ProcessingComponent(null, scheduler, worldPos, relativePos, foundPattern.tagsAt(relativePos), null));
                continue;
            }
            if (!(level.getBlockEntity(worldPos) instanceof MachineComponentTile tile)) continue;

            if (tile instanceof IOPortBlockEntity port) {
                Identifier formedTexture = foundMachine.appearance().formedPortBaseTexture();
                port.linkControllerAppearance(getBlockPos(), formedTexture);
                linkedPortPositions().add(worldPos.immutable());
            }
            var component = tile.provideComponent();
            if (!(tile instanceof BlockEntity container)) continue;
            components.add(new ProcessingComponent(component, container, worldPos, relativePos, foundPattern.tagsAt(relativePos)));
        }
    }

    private void unlinkLinkedPorts() {
        Set<BlockPos> linkedPortPositions = linkedPortPositions();
        if (level == null) {
            linkedPortPositions.clear();
            return;
        }
        if (foundPattern != null) {
            for (BlockPos relativePos : componentPositions()) {
                BlockEntity entity = level.getBlockEntity(getBlockPos().offset(relativePos));
                if (entity instanceof LinkedAppearanceBlockEntity linkedAppearance) {
                    linkedAppearance.unlinkControllerAppearance(getBlockPos());
                }
            }
        }
        for (BlockPos portPos : linkedPortPositions) {
            if (level.getBlockEntity(portPos) instanceof LinkedAppearanceBlockEntity linkedAppearance) {
                linkedAppearance.unlinkControllerAppearance(getBlockPos());
            }
        }
        linkedPortPositions.clear();
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

    private static List<BlockPos> componentPositions(BlockArray pattern, @Nullable CompiledMachinePattern compiled, Direction facing) {
        return compiled == null ? new ArrayList<>(pattern.pattern().keySet()) : compiled.componentPositions(facing);
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

    private java.util.Optional<PortRequirementSpec.Failure> validateFactoryControllerCount(
            Machine candidate, BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        int count = countFactoryControllers(rotatedPattern, compiledPattern, facing);
        if (count <= 1) return java.util.Optional.empty();
        return java.util.Optional.of(new PortRequirementSpec.Failure(
                "factory_controller",
                count,
                0,
                java.util.OptionalInt.of(1),
                PortRequirementSpec.FailureReason.TOO_MANY));
    }

    private int countFactoryControllers(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        if (level == null || rotatedPattern == null) return 0;

        List<BlockPos> positions = compiledPattern == null ? new ArrayList<>(rotatedPattern.pattern().keySet()) : compiledPattern.componentPositions(facing);
        int count = 0;
        for (BlockPos relativePos : positions) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof FactorySchedulerBlockEntity && ++count > 1) {
                return count;
            }
        }
        return count;
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
        syncLevelState();
    }

    private void syncLevelState() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    void syncRuntimeStateIfChanged() {
        if (getBlockState() == null) return;
        boolean next = isRuntimeActive();
        if (next == syncedRuntimeActive) return;
        syncedRuntimeActive = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
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
        if (active == null) {
            syncRuntimeStateIfChanged();
            return;
        }
        pausedActive = active;
        pausedContext = context;
        active = null;
        context = null;
        setActiveState(false);
        syncRuntimeStateIfChanged();
    }

    private void resumePausedRecipeAfterStructureCheck() {
        if (active != null || pausedActive == null || pausedContext == null || redstonePaused) return;
        active = pausedActive;
        context = pausedContext;
        context.refreshController(this);
        pausedActive = null;
        pausedContext = null;
        setActiveState(true);
        syncRuntimeStateIfChanged();
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

    private record CandidatePattern(BlockArray pattern, Direction rollFacing) { }

    private void resetMachine() {
        resetMachine(true);
    }

    private void resetMachine(boolean clearFormationFailure) {
        boolean wasFormed = isFormed();
        Identifier dropped = foundMachine == null ? null : foundMachine.registryName();
        boolean hadActive = active != null;
        Identifier activeRecipe = hadActive ? active.getRecipe().id() : null;
        releaseStructureClaims();
        unbindSmartInterfaces();
        unlinkLinkedPorts();
        stopFactoryController();
        foundMachine = null;
        foundPattern = null;
        foundCompiledPattern = null;
        controllerFacing = null;
        matchedRollFacing = Direction.SOUTH;
        foundModifiers.clear();
        foundLevels = Map.of();
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
        returnContext(pendingSharedStartContext);
        clearPendingSharedStart();
        sharedTickPending = false;
        pendingSharedTickDomain = null;
        clearPendingConflictStart();
        pausedActive = null;
        returnContext(pausedContext);
        pausedContext = null;
        lastFailureUnloc = null;
        recipeFailure = null;
        if (clearFormationFailure) lastFormationFailure = null;
        if (clearFormationFailure) lastStructureError = null;
        redstonePaused = false;
        if (dropped != null || wasFormed || hadActive) structureVersion++;
        markRecipeDirty();
        clearCandidateCache();
        if (wasFormed) setFormed(false);
        setChanged();
        syncRuntimeStateIfChanged();
    }

    private void unbindSmartInterfaces() {
        if (level == null || foundPattern == null) return;
        List<SmartInterfaceBlockEntity> smartInterfaces = new ArrayList<>();
        for (BlockPos relativePos : componentPositions()) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof SmartInterfaceBlockEntity smartInterface) {
                smartInterfaces.add(smartInterface);
            }
        }
        new SmartInterfaceBindingCoordinator(Map.of()).unbindAll(this, smartInterfaces);
    }

    public void releaseStructureClaims() {
        if (level instanceof ServerLevel serverLevel) {
            StructureClaimRegistry.get(serverLevel).release(getBlockPos());
        }
        resourceDomain = null;
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
        var pkt = new PktMachineStatePayload(getBlockPos(), name, formed, activeNow,
                foundLevels.values().stream().map(foundLevel -> foundLevel.id().toString()).toList());
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
        candidates = lockedRecipeCandidates(candidates);
        RecipeSearchResult result;
        try {
            if (lockedRecipeId == null) {
                result = new RecipeSearchTask(this, machineId, structureVersion, getMaxParallelism(), candidates,
                        contextPool(), cachedCandidateIndex).compute();
            } else {
                result = new RecipeSearchTask(this, machineId, structureVersion, getMaxParallelism(), candidates,
                        contextPool()).compute();
            }
        } catch (RuntimeException e) {
            LOG.warn("[Ctrl#{}] tryStartNewRecipe: recipe search failed at pos={}; retrying later", instanceId, getBlockPos(), e);
            clearPendingConflictStart();
            recipeSearchRetryCounter++;
            lastFailureUnloc = RecipeCraftingContext.FAILURE_SEARCH_EXCEPTION;
            recipeFailure = null;
            return false;
        }
        if (result.success()) {
            return applySearchResult(result, candidates.size());
        }
        clearPendingConflictStart();
        recipeSearchRetryCounter++;
        lastFailureUnloc = result.failureUnloc();
        recipeFailure = result.levelFailure();
        if (recipeFailure != null) {
            lastFailureUnloc = "gui.mmcr.controller.failure.level_insufficient";
        }
        return false;
    }

    private List<MachineRecipe> lockedRecipeCandidates(List<MachineRecipe> candidates) {
        if (lockedRecipeId == null || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        return candidates.stream().filter(recipe -> lockedRecipeId.equals(recipe.id())).toList();
    }

    private boolean shouldSearchRecipe() {
        long registryVersion = RecipeRegistry.registryVersion();
        if (lastRecipeSearchRegistryVersion != registryVersion) {
            lastRecipeSearchRegistryVersion = registryVersion;
            recipeSearchRetryCounter = 0;
            return true;
        }
        if (recipeSearchRetryCounter <= 0) return true;
        return recipeSearchClock() % nextRecipeSearchDelay() == 0;
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
        if (usesSharedIoCoordinator()) {
            requestSharedStart(next, nextContext);
            return true;
        }
        active = next;
        context = nextContext;
        int granted = nextContext.commitStart(next, next.getMaxParallelism());
        if (granted <= 0) {
            active = null;
            context = null;
            returnContext(nextContext);
            clearPendingConflictStart();
            recipeSearchRetryCounter++;
            lastFailureUnloc = nextContext.getLastFailureUnloc();
            return false;
        }
        next.refreshTotalTick(nextContext);
        setActiveState(true);
        syncRuntimeStateIfChanged();
        rememberLastRecipe(next.getRecipe());
        recipeSearchRetryCounter = 0;
        lastFailureUnloc = null;
        recipeFailure = null;
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
        return recipeSearchClock();
    }

    private long recipeSearchClock() {
        if (level == null) return recipeSearchAttemptCounter;
        try {
            long gameTime = level.getGameTime();
            return gameTime == 0L && recipeSearchAttemptCounter > 0 ? recipeSearchAttemptCounter : gameTime;
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
        if (lockedRecipeId != null && !lockedRecipeId.equals(lastRecipe.id())) return false;
        if (lastRecipeStructureVersion != structureVersion || lastRecipeModifierSnapshotVersion != modifierSnapshotVersion) return false;
        ActiveMachineRecipe next = new ActiveMachineRecipe(lastRecipe, getMaxParallelism());
        RecipeCraftingContext nextContext = contextPool().borrow(next, this);
        try {
            if (!next.canStartCrafting(nextContext)) return false;
            if (usesSharedIoCoordinator()) {
                requestSharedStart(next, nextContext);
                return true;
            }
            active = next;
            context = nextContext;
            int granted = nextContext.commitStart(next, next.getMaxParallelism());
            if (granted <= 0) {
                active = null;
                context = null;
                recipeDirty = true;
                return false;
            }
            next.refreshTotalTick(nextContext);
            setActiveState(true);
            syncRuntimeStateIfChanged();
            recipeSearchRetryCounter = 0;
            lastFailureUnloc = null;
            recipeFailure = null;
            setChanged();
            return true;
        } finally {
            if (active != next && pendingSharedStartContext != nextContext) returnContext(nextContext);
        }
    }

    private void rememberLastRecipe(MachineRecipe recipe) {
        lastRecipe = recipe;
        lockedRecipeId = recipe.id();
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
            if (restoredRecipeContext) {
                context.refreshStructureVersion();
                restoredRecipeContext = false;
            } else if (context.isStructureVersionOnlyCurrent()) {
                context.refreshModifierSnapshot(foundModifierList());
                active.refreshTotalTick(context);
            } else {
                context = new RecipeCraftingContext(this);
                context.setStructureModifiers(foundModifierList());
            }
        }
        if (usesSharedIoCoordinator()) {
            tickSharedRecipe();
            return;
        }
        int gameTime = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, currentGameTime()));
        if (active.isFinishPending()) {
            if (!active.shouldRetryFinish(gameTime)) return;
            ActiveMachineRecipe.TickStatus status = active.applyTickGrant(true,
                    context.commitSynchronousOutputs(active.getRecipe(), active.getParallelism()), gameTime);
            if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
                lastFailureUnloc = null;
                playFinishSound();
                returnContext(context);
                active = null;
                context = null;
                setActiveState(false);
                syncRuntimeStateIfChanged();
            } else {
                lastFailureUnloc = context.getLastFailureUnloc();
            }
            setChanged();
            return;
        }
        boolean finalTick = active.needsFinishCommit();
        if (finalTick && !context.simulateOutputs(active.getRecipe(), active.getParallelism())) {
            active.applyTickGrant(true, false, gameTime);
            lastFailureUnloc = context.getLastFailureUnloc();
            setChanged();
            return;
        }
        boolean resourcesGranted = context.commitSynchronousIoTick(active.getRecipe(), active.getParallelism(), active.inputConsumptionPlan());
        boolean outputsCommitted = resourcesGranted && finalTick
                && context.commitSynchronousOutputs(active.getRecipe(), active.getParallelism());
        ActiveMachineRecipe.TickStatus status = active.applyTickGrant(resourcesGranted, outputsCommitted, gameTime);
        if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
            lastFailureUnloc = null;
            recipeFailure = null;
            playFinishSound();
            returnContext(context);
            active = null;
            context = null;
            setActiveState(false);
            syncRuntimeStateIfChanged();
        } else if (status == ActiveMachineRecipe.TickStatus.CANCELLED) {
            lastFailureUnloc = context.getLastFailureUnloc();
            returnContext(context);
            active = null;
            context = null;
            setActiveState(false);
            syncRuntimeStateIfChanged();
        } else if (status == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = context.getLastFailureUnloc();
            if (active.getRecipe().doesCancelRecipeOnPerTickFailure()) {
                returnContext(context);
                active = null;
                context = null;
                setActiveState(false);
                syncRuntimeStateIfChanged();
            }
        } else {
            lastFailureUnloc = null;
            recipeFailure = null;
        }
        setChanged();
    }

    private boolean usesSharedIoCoordinator() {
        StructureClaimRegistry.ResourceDomain domain = resourceDomain();
        return level instanceof ServerLevel && domain != null && domain.controllers().size() > 1;
    }

    private void requestSharedStart(ActiveMachineRecipe next, RecipeCraftingContext nextContext) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        StructureClaimRegistry.ResourceDomain domain = resourceDomain();
        if (domain == null) return;
        sharedStartPending = true;
        pendingSharedStartContext = nextContext;
        pendingSharedStartDomain = domain;
        SharedIoCoordinator.get(serverLevel).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), structureVersion,
                next.getMaxParallelism(),
                requested -> {
                    if (!isPendingSharedStart(next, nextContext, domain)) return 0;
                    int granted = nextContext.commitStart(next, requested);
                    if (granted <= 0) {
                        clearPendingSharedStart();
                        returnContext(nextContext);
                        recipeSearchRetryCounter++;
                        lastFailureUnloc = nextContext.getLastFailureUnloc();
                    }
                    return granted;
                },
                granted -> {
                    if (!isPendingSharedStart(next, nextContext, domain)) return;
                    clearPendingSharedStart();
                    next.refreshTotalTick(nextContext);
                    active = next;
                    context = nextContext;
                    setActiveState(true);
                    syncRuntimeStateIfChanged();
                    rememberLastRecipe(next.getRecipe());
                    recipeSearchRetryCounter = 0;
                    lastFailureUnloc = null;
                    setChanged();
                },
                () -> isPendingSharedStart(next, nextContext, domain), this::getStructureVersion
        ));
    }

    private boolean isPendingSharedStart(ActiveMachineRecipe next, RecipeCraftingContext nextContext,
                                         StructureClaimRegistry.ResourceDomain domain) {
        return sharedStartPending && pendingSharedStartContext == nextContext
                && pendingSharedStartDomain != null && pendingSharedStartDomain.equals(domain)
                && active == null && isCurrentSharedDomain(domain);
    }

    private void clearPendingSharedStart() {
        sharedStartPending = false;
        pendingSharedStartContext = null;
        pendingSharedStartDomain = null;
    }

    private void tickSharedRecipe() {
        if (!(level instanceof ServerLevel serverLevel) || active == null || context == null) return;
        StructureClaimRegistry.ResourceDomain domain = resourceDomain();
        if (domain == null) return;
        if (sharedTickPending && !isCurrentSharedDomain(pendingSharedTickDomain)) {
            sharedTickPending = false;
            pendingSharedTickDomain = null;
        }
        if (sharedTickPending) return;
        int gameTime = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, currentGameTime()));
        ActiveMachineRecipe recipe = active;
        RecipeCraftingContext recipeContext = context;
        if (recipe.isFinishPending()) {
            if (!recipe.shouldRetryFinish(gameTime)) return;
            sharedTickPending = true;
            pendingSharedTickDomain = domain;
            requestSharedFinish(serverLevel, domain, recipe, recipeContext, gameTime);
            return;
        }
        sharedTickPending = true;
        pendingSharedTickDomain = domain;
        SharedIoCoordinator.get(serverLevel).enqueue(new SharedIoCoordinator.TickRequest(
                domain, new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), structureVersion,
                () -> {
                    if (!isActiveSharedRecipe(recipe, recipeContext, domain)) return false;
                    if (recipe.needsFinishCommit() && !recipeContext.simulateOutputs(recipe.getRecipe(), recipe.getParallelism())) {
                        applySharedTick(recipe, recipeContext, false, false, gameTime);
                        return false;
                    }
                    if (!recipeContext.coordinatorIoTick(recipe.getRecipe(), recipe.getParallelism(), recipe.inputConsumptionPlan()).getAsBoolean()) {
                        applySharedTick(recipe, recipeContext, false, false, gameTime);
                        return false;
                    }
                    if (recipe.needsFinishCommit()) {
                        recipe.beginFinishCommit();
                        requestSharedFinish(serverLevel, domain, recipe, recipeContext, gameTime);
                    } else {
                        applySharedTick(recipe, recipeContext, true, false, gameTime);
                    }
                    return true;
                },
                () -> isActiveSharedRecipe(recipe, recipeContext, domain), this::getStructureVersion
        ));
    }

    private void requestSharedFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                     ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext, int gameTime) {
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.FinishRequest(
                domain, new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), structureVersion,
                () -> {
                    if (!isActiveSharedRecipe(recipe, recipeContext, domain)) return false;
                    applySharedTick(recipe, recipeContext, true,
                            recipeContext.coordinatorOutputs(recipe.getRecipe(), recipe.getParallelism()).getAsBoolean(), gameTime);
                    return true;
                },
                () -> isActiveSharedRecipe(recipe, recipeContext, domain), this::getStructureVersion
        ));
    }

    private boolean isActiveSharedRecipe(ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext,
                                         StructureClaimRegistry.ResourceDomain domain) {
        return active == recipe && context == recipeContext && isCurrentSharedDomain(domain);
    }

    private void applySharedTick(ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext,
                                 boolean resourcesGranted, boolean outputsCommitted, int gameTime) {
        sharedTickPending = false;
        pendingSharedTickDomain = null;
        ActiveMachineRecipe.TickStatus status = recipe.applyTickGrant(resourcesGranted, outputsCommitted, gameTime);
        if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
            lastFailureUnloc = null;
            playFinishSound();
            returnContext(recipeContext);
            active = null;
            context = null;
            setActiveState(false);
            syncRuntimeStateIfChanged();
        } else if (status == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = recipeContext.getLastFailureUnloc();
            if (recipe.getRecipe().doesCancelRecipeOnPerTickFailure()) {
                returnContext(recipeContext);
                active = null;
                context = null;
                setActiveState(false);
                syncRuntimeStateIfChanged();
            }
        } else {
            lastFailureUnloc = null;
        }
        setChanged();
    }

    void playFinishSound() {
        if (!(level instanceof ServerLevel serverLevel) || foundMachine == null) return;
        MachineRegistration registration = MachineDefinitions.getRegistration(foundMachine.registryName());
        SoundEvent sound = registration == null ? null : MachineSoundRegistry.get(registration.finishSoundId());
        if (sound != null) {
            serverLevel.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private boolean isCurrentSharedDomain(@Nullable StructureClaimRegistry.ResourceDomain domain) {
        return domain != null && domain.equals(resourceDomain());
    }

    private void setActiveState(boolean activeState) {
        if (level == null || level.isClientSide()) return;
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != activeState) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, activeState), 3);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) resetMachine();
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
        setMachine(resolved);
    }

    private List<MachineRecipe> recipesForMachine() {
        Identifier machineId = machine == null ? null : machine.registryName();
        if (machineId == null) return List.of();
        int datapackCount = datapackRecipeCount();
        long registryVersion = RecipeRegistry.registryVersion();
        if (machineId.equals(cachedCandidatesMachineId)
                && cachedCandidatesReloadVersion == registryVersion
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
        cachedCandidatesReloadVersion = registryVersion;
        cachedDatapackRecipeCount = datapackCount;
        cachedCandidates = List.copyOf(recipes.values());
        cachedCandidateIndex = RecipeCandidateIndex.build(cachedCandidates);
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
        cachedCandidateIndex = RecipeCandidateIndex.empty();
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
        ValueOutput.TypedOutputList<String> levels = output.list("found_levels", com.mojang.serialization.Codec.STRING);
        for (MachineLevel foundLevel : (foundLevels == null ? Map.<Identifier, MachineLevel>of() : foundLevels).values()) {
            levels.add(foundLevel.id().toString());
        }
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
        if (lockedRecipeId != null) {
            output.putString("locked_recipe", lockedRecipeId.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pausedActive = null;
        pausedContext = null;
        restoredRecipeContext = false;
        redstonePaused = false;
        active = null;
        context = null;
        String lockedRecipeName = input.getStringOr("locked_recipe", "");
        lockedRecipeId = lockedRecipeName.isEmpty() ? null : Identifier.parse(lockedRecipeName);
        Map<Identifier, MachineLevel> restoredLevels = new LinkedHashMap<>();
        input.listOrEmpty("found_levels", com.mojang.serialization.Codec.STRING).forEach(id -> {
            MachineLevel foundLevel = cn.howxu.mmcr.api.machine.level.MachineLevelRegistry.getLevel(Identifier.parse(id));
            if (foundLevel != null) restoredLevels.put(foundLevel.typeId(), foundLevel);
        });
        foundLevels = Map.copyOf(restoredLevels);
        lastFailureUnloc = null;
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
        restored.refreshTotalTick(pausedContext == null ? context : pausedContext);
        restoredRecipeContext = true;
        structureDirty = true;
        setChanged();
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
