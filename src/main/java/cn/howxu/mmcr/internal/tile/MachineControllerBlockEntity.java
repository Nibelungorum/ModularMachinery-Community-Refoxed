package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.machine.level.LevelMismatch;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.PlayerInventoryStructureItemSink;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.DataStorageBindingCoordinator;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.multiblock.NetworkInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.SmartInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.MachineReferenceHasher;
import cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewBuilder;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewPredicates;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.internal.recipe.RecipeStartDelay;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;

import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehaviorContext;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryTickResult;
import cn.howxu.mmcr.internal.runtime.JadeTextSnapshot;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.StructureRuntime.StructureWorkSnapshot;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.OptionalInt;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

public class MachineControllerBlockEntity extends BlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger(MachineControllerBlockEntity.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    private static final Set<MachineControllerBlockEntity> FORMED_CONTROLLERS = ConcurrentHashMap.newKeySet();
    private static final Set<MachineControllerBlockEntity> ACTIVE_STRUCTURE_SCANS = ConcurrentHashMap.newKeySet();
    private static final String SHARED_COMPONENT_CONFLICT = "shared_component_conflict";
    private static final int PREVIEW_RECEIVER_WINDOW_TICKS = 8 * 20;
    private static final int STRUCTURE_SAFETY_INTERVAL_TICKS = 120;
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();
    private final int instanceId = INSTANCE_COUNTER.incrementAndGet();
    private boolean chunkUnloaded;

    private @Nullable Runnable structureDiagnosticCallbackForTesting;
    private int matcherInvocationCountForTesting;
    private int scanBatchCountForTesting;
    private int structureSafetyCheckCountForTesting;
    private final Map<Long, Integer> scanBatchesPerTickForTesting = new LinkedHashMap<>();
    private @Nullable Integer structureCheckIntervalOverrideForTesting;
    private @Nullable Integer structureScanBatchesOverrideForTesting;
    private @Nullable Integer buildBlocksPerTickOverrideForTesting;
    private boolean clientActive;
    private @Nullable Identifier clientRecipeId;
    private @Nullable PktMachineStatePayload lastBroadcastState;
    private @Nullable Identifier lockedRecipeId;
    private @Nullable String lastFailureUnloc;
    private boolean redstonePaused;
    private @Nullable FactoryRecipeScheduler factoryScheduler;
    private int recipeSearchRetryCounter;
    private long recipeSearchAttemptCounter;
    private long lastRecipeSearchRegistryVersion = Long.MIN_VALUE;
    private @Nullable Identifier cachedCandidatesMachineId;
    private long cachedCandidatesCatalogVersion = Long.MIN_VALUE;
    private List<MachineRecipe> cachedCandidates = List.of();
    private @Nullable MachineReference cachedMachineReference;
    private long cachedMachineReferenceStructureVersion = Long.MIN_VALUE;
    private RecipeStartDelay recipeStartDelay = new RecipeStartDelay();
    private boolean sharedStartPending;
    private @Nullable MachineRecipe pendingSharedStartRecipe;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingSharedStartDomain;
    private long pendingSharedStartStructureVersion = Long.MIN_VALUE;
    private long pendingSharedStartCapabilityVersion = Long.MIN_VALUE;
    private long pendingSharedStartModifierVersion = Long.MIN_VALUE;
    private long pendingSharedStartComponentStateVersion = Long.MIN_VALUE;
    private long pendingSharedStartCatalogVersion = Long.MIN_VALUE;
    private long nextSharedStartToken;
    private long pendingSharedStartToken;
    private boolean sharedTickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingSharedTickDomain;
    private long nextSharedTickToken;
    private long pendingSharedTickToken;
    private boolean syncedRuntimeActive;
    private long lastSentControllerScreenTextRevision = -1L;
    private final Map<String, Long> lastSentRecipeScreenTextRevisions = new LinkedHashMap<>();
    private final Map<String, Long> removedRecipeScreenTextRevisions = new LinkedHashMap<>();
    private boolean runtimeStateBroadcastPending;
    private boolean factoryMenuSyncPending;
    private Map<UUID, Long> previewReceivers = new LinkedHashMap<>();
    private @Nullable Runnable factoryCapacityInvalidationCallbackForTesting;
    private @Nullable Runnable structureCheckCallbackForTesting;
    private final ResourceAvailabilityNotifier resourceAvailabilityNotifier = this::notifyResourceAvailability;
    private long resourceAvailabilityEpoch;
    private long lastResourceAvailabilityTick = Long.MIN_VALUE;
    private @Nullable ValueInput pendingFactoryRuntimeInput;
    private boolean restoringFactoryRuntime;
    private @Nullable MultiblockAssemblyService.BuildTaskRegistry buildTasks;
    private @Nullable ServerPlayer buildTaskOwner;
    private int buildTaskAge;
    private final Map<Long, Integer> buildTaskPlacementsPerTickForTesting = new LinkedHashMap<>();
    private final transient MachineControllerRuntime runtime;
    private List<UpgradeBusBlockEntity> boundUpgradeBuses = List.of();
    private Set<BlockPos> activeNetworkInterfacePositions = Set.of();
    private final Runnable upgradeBusChangeListener = this::onUpgradeBusContentsChanged;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.controllerFor(machineIdFromState(state)).get(), pos, state);
        runtime = new MachineControllerRuntime(this);
    }

    public ControllerRuntimeSnapshot runtimeSnapshot() {
        ensureFactoryRuntimeLoaded();
        return runtime.snapshot();
    }

    public ControllerRuntimeSnapshot currentRuntimeSnapshot() {
        ensureFactoryRuntimeLoaded();
        return runtime.currentSnapshot();
    }

    public ComponentRuntime componentRuntime() {
        return runtime.componentRuntime();
    }

    public MachineBehaviorContext behaviorContext() {
        return runtime.behaviorContext();
    }

    public MachineBehaviorContext behaviorContext(ControllerScreenText screenText) {
        return runtime.behaviorContext(screenText);
    }

    public long countStructureBlocks(Block block) {
        return runtime.countStructureBlocks(block);
    }

    public JadeTextSnapshot jadeTextSnapshot() {
        return runtime.jadeTextSnapshot();
    }

    public ControllerScreenTextState recipeScreenText(String laneId) {
        return runtime.recipeScreenText(laneId);
    }

    public void clearRecipeScreenText(String laneId) {
        runtime.clearRecipeScreenText(laneId);
    }

    int snapshotBuildCountForTesting() {
        return runtime.snapshotBuildCountForTesting();
    }

    void ensureFactoryRuntimeLoaded() {
        if (pendingFactoryRuntimeInput == null) return;
        ValueInput input = pendingFactoryRuntimeInput;
        pendingFactoryRuntimeInput = null;
        runtime.factoryRuntime().load(input, this);
        restoringFactoryRuntime = true;
        runtime.publishSnapshot();
    }

    public StructureSnapshot structureSnapshot() {
        return runtimeSnapshot().structure();
    }

    public StructureSnapshot currentStructureSnapshot() {
        return runtime.currentStructureSnapshot();
    }

    private StructureWorkSnapshot structureWorkSnapshot() {
        return runtime.structureWorkSnapshot();
    }

    private void publishStructureWork(UnaryOperator<StructureWorkSnapshot> update) {
        runtime.publishStructureWork(update.apply(structureWorkSnapshot()));
    }

    public void tickStructure(ServerLevel level, BlockPos controllerPos) {
        validateRuntimeBoundary(level, controllerPos);
        if (level.isClientSide() || isRemoved()) return;
        if (currentRuntimeSnapshot().structure().configuredMachine() == null) bindDefaultMachine();
        invalidateForControllerRotation();
        if (shouldCheckStructure()) checkStructure();
    }

    private void publishRuntimeState() {
        publishRuntimeState(null, null);
    }

    private void publishRuntimeState(@Nullable FactoryTickResult factoryTickResult,
                                     @Nullable Integer activeFactoryLaneCount) {
        if (level != null && level.isClientSide()) return;
        StructureSnapshot structure = runtime.currentStructureSnapshot();
        CraftingRuntime craftingRuntime = runtime.craftingRuntime();
        var publishedCrafting = runtime.snapshot().crafting();
        if (publishedCrafting.recipeId() != null || publishedCrafting.failure() != null
                || craftingRuntime.active() || craftingRuntime.failure() != null) {
            ExecutionStatus runtimeFailure = craftingRuntime.failure();
            lastFailureUnloc = runtimeFailure == null ? null : failureUnloc(runtimeFailure);
        }
        boolean tickMachine = hasTickBehavior(structure);
        int factoryActiveCount = tickMachine ? 0 : factoryTickResult != null ? factoryTickResult.activeLaneCount()
                : activeFactoryLaneCount != null ? activeFactoryLaneCount : runtime.factoryRuntime().activeLaneCount();
        boolean activeState = craftingRuntime.active() || factoryActiveCount > 0;
        boolean tickPaused = redstonePaused && tickMachine;
        Identifier recipeId = tickMachine || craftingRuntime.recipe() == null ? null : craftingRuntime.recipe().id();
        CraftingStatus status = !structure.formed()
                ? CraftingStatus.MISSING_STRUCTURE
                : !structure.structureAreaLoaded() ? CraftingStatus.CHUNK_UNLOADED
                : activeState
                ? redstonePaused ? CraftingStatus.paused() : CraftingStatus.working()
                : tickPaused ? CraftingStatus.paused()
                : lastFailureUnloc == null ? CraftingStatus.IDLE : CraftingStatus.failure(lastFailureUnloc);
        runtime.publishRuntimeState(structure.structureAreaLoaded(), structure.formed(),
                structure.configuredMachine(), structure.matchedStage(), recipeId, status,
                craftingFailureStatus(), craftingRuntime.tickCount(),
                craftingRuntime.totalTick(), craftingRuntime.parallelism(), craftingRuntime.maxParallelism());
        if (runtime.updateBatchActive()) {
            runtimeStateBroadcastPending = true;
            if (hasFactoryControllerCurrent()) factoryMenuSyncPending = true;
        } else {
            broadcastStateIfChanged();
        }
    }

    void publishRuntimeStateAfterSnapshotBatch() {
        if (runtimeStateBroadcastPending) {
            runtimeStateBroadcastPending = false;
            broadcastStateIfChanged();
        }
        if (factoryMenuSyncPending) {
            factoryMenuSyncPending = false;
            syncOpenFactoryControllerMenus();
        }
    }

    private @Nullable ExecutionStatus craftingFailureStatus() {
        if (lastFailureUnloc == null) return null;
        ExecutionStatus runtimeFailure = runtime.craftingRuntime().failure();
        return runtimeFailure == null
                ? new ExecutionStatus(MMCR.id("crafting_failure"), StatusSeverity.FAILURE,
                MMCR.id("crafting"), Map.of("message", lastFailureUnloc))
                : runtimeFailure;
    }

    private void syncCraftingFailure() {
        runtime.refreshCraftingState();
        ExecutionStatus runtimeFailure = runtime.craftingRuntime().failure();
        lastFailureUnloc = runtimeFailure == null ? null : failureUnloc(runtimeFailure);
    }

    public void syncFactoryFailure(@Nullable ExecutionStatus factoryFailure) {
        lastFailureUnloc = factoryFailure == null ? null : failureUnloc(factoryFailure);
    }

    public void syncRecipeRuntimeFailure(CraftingRuntime recipeRuntime) {
        if (recipeRuntime == runtime.craftingRuntime()) {
            syncCraftingFailure();
            syncRuntimeStateIfChanged();
            setChanged();
            return;
        }
        if (!runtime.factoryRuntime().contains(recipeRuntime)) return;
        runtime.factoryRuntime().markLaneRuntimeChanged(recipeRuntime);
        runtime.factoryRuntime().recomputeFailure();
        runtime.publishSnapshot();
        syncRuntimeStateIfChanged();
        setChanged();
    }

    void onSmartInterfaceValueChanged() {
        if (level == null || level.isClientSide()) return;
        CraftingRuntime craftingRuntime = runtime.craftingRuntime();
        craftingRuntime.invalidateForSmartInterfaceChange();
        runtime.factoryRuntime().invalidateForSmartInterfaceChange();
        if (!craftingRuntime.active()) {
            clearPendingSharedStart();
            clearSharedTickPending();
        }
        if (craftingRuntime.failure() != null) syncCraftingFailure();
        setActiveState(craftingRuntime.active() || runtime.factoryRuntime().activeLaneCount() > 0);
        syncRuntimeStateIfChanged();
        setChanged();
    }

    private void validateRuntimeBoundary(ServerLevel runtimeLevel, BlockPos runtimePos) {
        if (runtimeLevel == null || runtimePos == null) {
            throw new IllegalArgumentException("Controller runtime requires a level and controller position");
        }
        if (level != null && level != runtimeLevel) {
            throw new IllegalArgumentException("Controller runtime level does not match the controller");
        }
        if (getBlockPos() != null && !getBlockPos().equals(runtimePos)) {
            throw new IllegalArgumentException("Controller runtime position does not match the controller");
        }
    }

    private static Identifier machineIdFromState(BlockState state) {
        if (state.getBlock() instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        throw new IllegalArgumentException("MachineControllerBlockEntity requires a MachineControllerBlock state");
    }

    public @Nullable Identifier machineId() {
        Machine currentMachine = runtimeSnapshot().structure().configuredMachine();
        return currentMachine == null ? null : currentMachine.registryName();
    }

    public @Nullable MachineReference machineReference() {
        StructureSnapshot structure = runtime.currentStructureSnapshot();
        if (!(level instanceof ServerLevel serverLevel) || !structure.formed() || structure.machine() == null) {
            cachedMachineReference = null;
            cachedMachineReferenceStructureVersion = Long.MIN_VALUE;
            return null;
        }
        Machine machine = structure.machine();
        long structureVersion = structure.version();
        if (cachedMachineReference != null
                && cachedMachineReferenceStructureVersion == structureVersion
                && cachedMachineReference.type().equals(machine.registryName())) {
            return cachedMachineReference;
        }
        Identifier type = machine.registryName();
        cachedMachineReference = new MachineReference(type,
                MachineReferenceHasher.hashForController(serverLevel.dimension().identifier(), type, getBlockPos()));
        cachedMachineReferenceStructureVersion = structureVersion;
        return cachedMachineReference;
    }

    public Set<BlockPos> activeNetworkInterfacePositions() {
        return activeNetworkInterfacePositions;
    }

    public boolean hasActiveNetworkInterface(BlockPos position) {
        return position != null && activeNetworkInterfacePositions.contains(position);
    }

    public void setMachine(Machine m) {
        StructureSnapshot current = runtimeSnapshot().structure();
        boolean bindingRestoredMachine = restoringFactoryRuntime
                && current.configuredMachine() == null
                && m != null;
        if (m == null) runtime.clearAllText();
        if (!bindingRestoredMachine) stopFactoryController();
        invalidateStructureScan(StructureMatcher.InvalidationReason.PATTERN);
        clearFoundModifiers();
        runtime.setModifiersAllowed(allowsModifiers(m));
        ControllerRuntimeSnapshot currentState = runtimeSnapshot();
        runtime.publishStructureState(isStructureAreaLoaded(currentState.structure()), currentState.structure().formed(), m,
                currentState.structure().matchedStage());
        runtime.publishComponentState(runtime.components(), currentState.foundModifiers(), Map.of(),
                currentState.linkedPortPositions());
        refreshModuleConnectionState();
        setChanged();
        publishRuntimeState();
        syncOpenControllerScreenText();
    }

    public void invalidateFormedStructure() {
        resetMachine();
        publishRuntimeState();
    }

    public void requestImmediateStructureCheck() {
        requestImmediateStructureCheck(null);
    }

    public void requestImmediateStructureCheck(@Nullable ServerPlayer diagnosticPlayer) {
        runtime.requestStructureCheck(diagnosticPlayer == null
                ? StructureRuntime.CheckReason.DIRTY_EVENT : StructureRuntime.CheckReason.DIAGNOSTIC);
        if (diagnosticPlayer != null) {
            publishStructureWork(state -> state.withDiagnostic(true, diagnosticPlayer.getUUID(),
                    level instanceof ServerLevel serverLevel ? serverLevel.dimension() : null));
        }
        publishRuntimeState();
    }

    public int matcherInvocationCountForTesting() { return matcherInvocationCountForTesting; }
    public int scanBatchCountForTesting() { return scanBatchCountForTesting; }
    public int structureSafetyCheckCountForTesting() { return structureSafetyCheckCountForTesting; }
    public Map<Long, Integer> scanBatchesPerTickForTesting() { return Map.copyOf(scanBatchesPerTickForTesting); }
    public boolean isStructureDiagnosticRequestedForTesting() { return structureWorkSnapshot().diagnosticRequested(); }
    public void setStructureDiagnosticCallbackForTesting(@Nullable Runnable callback) {
        structureDiagnosticCallbackForTesting = callback;
    }
    public boolean isPendingStructureInvalidationForTesting() { return structureWorkSnapshot().pendingInvalidation(); }
    StructureWorkSnapshot structureWorkSnapshotForTesting() { return structureWorkSnapshot(); }
    public int structureScanCursorForTesting() {
        StructureWorkSnapshot work = structureWorkSnapshot();
        return work.scan() == null ? -1 : work.scan().cursor();
    }
    public void setStructureCheckIntervalForTesting(@Nullable Integer interval) {
        structureCheckIntervalOverrideForTesting = interval;
    }
    public void setStructureScanBatchesForTesting(@Nullable Integer batches) {
        structureScanBatchesOverrideForTesting = batches;
    }
    public void setBuildBlocksPerTickForTesting(@Nullable Integer budget) {
        buildBlocksPerTickOverrideForTesting = budget;
    }
    public int buildBlocksPerTick() {
        if (buildBlocksPerTickOverrideForTesting != null) return buildBlocksPerTickOverrideForTesting;
        return Config.BUILD_BLOCKS_PER_TICK.get();
    }

    public void onMachineDestroyed() {
        notifyPreviewReceiversCleared();
        resetMachine(true, false);
    }

    public void setFormed(boolean f) {
        boolean before = physicalFormed();
        StructureSnapshot current = runtimeSnapshot().structure();
        runtime.publishStructureState(isStructureAreaLoaded(current), f, current.configuredMachine(), current.matchedStage());
        if (before == f) {
            publishRuntimeState();
            return;
        }
        updatePhysicalFormedState(f);
        if (f) notifyPreviewReceiversStructureFormed();
        publishRuntimeState();
    }

    public void handleStructureChunkChanged(ServerLevel changedLevel, BlockPos controllerPos) {
        validateRuntimeBoundary(changedLevel, controllerPos);
        onStructureChunkStateChanged();
    }

    public void onStructureChunkChanged(ServerLevel changedLevel) {
        validateRuntimeBoundary(changedLevel, getBlockPos());
        runtime.onStructureChunkChanged(changedLevel, getBlockPos());
    }

    public void onStructureChunkUnloaded(ServerLevel changedLevel, ChunkPos unloadedChunk) {
        validateRuntimeBoundary(changedLevel, getBlockPos());
        onStructureChunkStateChanged(unloadedChunk);
    }

    private boolean physicalFormed() {
        BlockState state = getBlockState();
        return state != null && state.hasProperty(MachineControllerBlock.FORMED)
                && state.getValue(MachineControllerBlock.FORMED);
    }

    private void updatePhysicalFormedState(boolean formed) {
        if (level == null || getBlockPos() == null) return;
        BlockState state = getBlockState();
        if (state != null && state.hasProperty(MachineControllerBlock.FORMED)
                && state.getValue(MachineControllerBlock.FORMED) != formed) {
            level.setBlock(getBlockPos(), state.setValue(MachineControllerBlock.FORMED, formed), 3);
        }
    }

    public @Nullable PortRequirementSpec.Failure getLastFormationFailure() {
        return structureSnapshot().lastFormationFailure();
    }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        if (level instanceof ServerLevel serverLevel) {
            return StructureClaimRegistry.get(serverLevel).domainFor(getBlockPos());
        }
        return null;
    }

    public ResourceAvailabilityNotifier resourceAvailabilityNotifier() {
        return resourceAvailabilityNotifier;
    }

    public long resourceAvailabilityEpoch() {
        return resourceAvailabilityEpoch;
    }

    public void notifyResourceAvailability(ResourceAvailabilityNotifier.Reason reason, @Nullable Object resource) {
        if (reason == null) return;
        runtime.componentRuntime().markCapabilityPresentationChanged();
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (lastResourceAvailabilityTick != gameTime) {
            lastResourceAvailabilityTick = gameTime;
            resourceAvailabilityEpoch++;
        }
        runtime.factoryRuntime().wakeSearches(reason, resource);
    }

    public void notifyCapabilityPresentationChanged() {
        runtime.componentRuntime().markCapabilityPresentationChanged();
    }

    public void refreshModuleConnectionState() {
        ModuleConnectionStatus beforeStatus = runtime.componentRuntime().moduleConnectionStatus();
        int beforeInstalledModuleCount = runtime.componentRuntime().installedModuleCount();
        runtime.refreshModuleConnectionState();
        ModuleConnectionStatus afterStatus = runtime.componentRuntime().moduleConnectionStatus();
        int afterInstalledModuleCount = runtime.componentRuntime().installedModuleCount();
        if (!beforeStatus.equals(afterStatus) || beforeInstalledModuleCount != afterInstalledModuleCount) {
            notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.MODULE_CONNECTION,
                    afterStatus);
        }
    }

    public void onStructureBlockChanged(BlockPos changedPos) {
        runtime.onStructureBlockChanged(changedPos);
    }

    public void handleStructureBlockChanged(BlockPos changedPos) {
        if (getBlockPos().equals(changedPos)) return;
        if (structureWorkSnapshot().scan() != null) publishStructureWork(state -> state.withPendingInvalidation(true));
        StructureSnapshot structure = currentRuntimeSnapshot().structure();
        if (!structure.formed()) {
            if (structure.configuredMachine() != null) requestImmediateStructureCheck();
            return;
        }
        if (structure.pattern() == null || structure.facing() == null) {
            return;
        }
        boolean insideStructure = isInsideCompiledBounds(changedPos);
        if (!insideStructure) {
            return;
        }
        boolean componentChanged = isInsideComponentPositions(changedPos, structure);
        runtime.requestStructureCheck(StructureRuntime.CheckReason.DIRTY_EVENT);
        publishStructureWork(state -> state.withPendingInvalidation(state.scan() != null)
                .withComponentRefreshRequired(state.componentRefreshRequired() || componentChanged || insideStructure));
        if (level instanceof ServerLevel serverLevel) ModuleConnectionCoordinator.enqueueCouplers(serverLevel, this);
        setChanged();
        publishRuntimeState();
    }

    public static void markStructureDirty(LevelAccessor level, BlockPos changedPos) {
        if (level == null || level.isClientSide()) return;
        FORMED_CONTROLLERS.removeIf(controller -> controller.isRemoved() || controller.level == null);
        for (MachineControllerBlockEntity controller : FORMED_CONTROLLERS) {
            if (controller.level == level) controller.onStructureBlockChanged(changedPos);
        }
    }

    public static void markStructureChunkDirty(LevelAccessor level, ChunkPos chunkPos) {
        markStructureChunkDirty(level, chunkPos, false);
    }

    public static void markStructureChunkUnloaded(LevelAccessor level, ChunkPos chunkPos) {
        markStructureChunkDirty(level, chunkPos, true);
    }

    private static void markStructureChunkDirty(LevelAccessor level, ChunkPos chunkPos, boolean unloading) {
        if (level == null || level.isClientSide()) return;
        FORMED_CONTROLLERS.removeIf(controller -> controller.isRemoved() || controller.level == null);
        ACTIVE_STRUCTURE_SCANS.removeIf(controller -> controller.isRemoved() || controller.level == null);
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (MachineControllerBlockEntity controller : FORMED_CONTROLLERS) {
            if (controller.level == level && controller.structureSnapshot().criticalChunks().contains(chunkPos)) {
                if (unloading) controller.onStructureChunkUnloaded(serverLevel, chunkPos);
                else controller.onStructureChunkChanged(serverLevel);
            }
        }
        for (MachineControllerBlockEntity controller : ACTIVE_STRUCTURE_SCANS) {
            if (!FORMED_CONTROLLERS.contains(controller) && controller.level == level
                    && controller.isChunkRelevantToActiveStructureScan(chunkPos)) {
                if (unloading) controller.onStructureChunkUnloaded(serverLevel, chunkPos);
                else controller.onStructureChunkChanged(serverLevel);
            }
        }
    }

    private boolean isChunkRelevantToActiveStructureScan(ChunkPos chunkPos) {
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (work.scan() == null) return false;
        if (!(work.scan().pattern() instanceof BlockArray pattern)) return true;
        BoundingBox box = boundingBox(pattern);
        int minChunkX = (getBlockPos().getX() + box.minX()) >> 4;
        int maxChunkX = (getBlockPos().getX() + box.maxX()) >> 4;
        int minChunkZ = (getBlockPos().getZ() + box.minZ()) >> 4;
        int maxChunkZ = (getBlockPos().getZ() + box.maxZ()) >> 4;
        return chunkPos.x() >= minChunkX && chunkPos.x() <= maxChunkX
                && chunkPos.z() >= minChunkZ && chunkPos.z() <= maxChunkZ;
    }

    public void setLastFailureUnloc(@Nullable String key) {
        this.lastFailureUnloc = key;
    }

    public void clearLastFailureOnRecipeStart() {
        this.lastFailureUnloc = null;
    }

    public boolean isRedstonePaused() { return redstonePaused; }

    public void applyClientState(String recipeName, boolean formed, boolean active, List<String> foundLevelIds,
                                 boolean recipeLocked, String lockedRecipeId, @Nullable Identifier machineId,
                                 int controllerRole, int installedModuleCount, boolean moduleConnected,
                                  @Nullable Identifier connectedHostId, CraftingStatus craftingStatus,
                                  @Nullable ExecutionStatus failure, boolean structureAreaLoaded,
                                   int tick, int totalTick, long parallelism, long maxParallelism,
                                   Map<String, DataValue> dataStorageValues) {
        if (level == null || !level.isClientSide()) return;
        if (physicalFormed() != formed) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, formed), 3);
        }
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != active) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, active), 3);
        }
        this.clientActive = active;
        this.clientRecipeId = recipeName == null || recipeName.isEmpty() ? null : Identifier.parse(recipeName);
        Map<Identifier, MachineLevel> levels = new LinkedHashMap<>();
        for (String id : foundLevelIds) {
            MachineLevel foundLevel = MachineLevelRegistry.getLevel(Identifier.parse(id));
            if (foundLevel != null) levels.put(foundLevel.typeId(), foundLevel);
        }
        Machine resolvedMachine = machineId == null ? null : MachineRegistry.effectiveSnapshot().get(machineId);
        runtime.publishClientStructureState(resolvedMachine, formed, structureAreaLoaded);
        boolean module = controllerRole == 2 || (resolvedMachine != null && resolvedMachine.isModule());
        ModuleConnectionStatus moduleStatus = module
                ? (moduleConnected && connectedHostId != null
                ? ModuleConnectionStatus.connected(connectedHostId)
                : ModuleConnectionStatus.disconnected())
                : ModuleConnectionStatus.notRequired();
        runtime.publishClientComponentState(levels, moduleStatus, installedModuleCount);
        this.clientRecipeLocked = recipeLocked;
        this.clientLockedRecipeId = recipeLocked && lockedRecipeId != null ? lockedRecipeId : "";
        runtime.publishClientDataStorageState(dataStorageValues);
        runtime.publishCraftingState(clientRecipeId, craftingStatus, failure,
                tick, totalTick, parallelism, maxParallelism);
    }

    public boolean hasClientActiveRecipe() { return clientActive; }

    private boolean clientRecipeLocked;
    private String clientLockedRecipeId = "";

    public boolean hasClientRecipeLock() { return clientRecipeLocked; }
    public String clientLockedRecipeId() { return clientLockedRecipeId; }

    public boolean recipeLocked() { return lockedRecipeId != null; }
    public @Nullable Identifier lockedRecipeId() { return lockedRecipeId; }

    public boolean isRuntimeActive() {
        if (level != null && level.isClientSide()) return clientActive || getBlockState().getValue(MachineControllerBlock.ACTIVE);
        StructureSnapshot structure = runtime.currentStructureSnapshot();
        if (!structure.formed() || !structure.structureAreaLoaded()
                || redstonePaused || runtime.factoryRuntime().isPaused()) return false;
        return runtime.craftingRuntime().active() || runtime.factoryRuntime().activeLaneCount() > 0
                || hasTickBehavior(structure);
    }

    private boolean isRuntimeActive(ControllerRuntimeSnapshot state) {
        return SYNC_RUNTIME.active(state);
    }

    public long currentParallelism() {
        ControllerRuntimeSnapshot state = runtimeSnapshot();
        return SYNC_RUNTIME.machineState(state).parallelism();
    }

    public int activeFactoryThreadCount() {
        ControllerRuntimeSnapshot state = runtimeSnapshot();
        return SYNC_RUNTIME.machineState(state).activeFactoryThreadCount();
    }

    public boolean isPortUsedByActiveRecipe(BlockPos pos) {
        return (runtime.craftingRuntime().active() || !runtime.factoryRuntime().activeRuntimes().isEmpty())
                && runtime.components().stream().anyMatch(component -> component.getPos().equals(pos));
    }

    public void resetLinkedPortAppearances() {
        unlinkLinkedPorts();
    }

    public long getMaxParallelism() {
        return SYNC_RUNTIME.machineState(currentRuntimeSnapshot()).maxParallelism();
    }

    public int parallelControllerCount() {
        return SYNC_RUNTIME.machineState(runtimeSnapshot()).parallelControllerCount();
    }

    public long maxParallelControllerCount() {
        return SYNC_RUNTIME.machineState(runtimeSnapshot()).maxParallelControllerCount();
    }

    public @Nullable FactorySchedulerBlockEntity getFactoryController() {
        Machine configuredMachine = currentRuntimeSnapshot().structure().configuredMachine();
        if (configuredMachine == null || !configuredMachine.hasFactory()
                || configuredMachine.behavior() instanceof TickBehavior) return null;
        return factoryComponents().stream().findFirst().orElse(null);
    }

    public boolean toggleFactoryRecipeLock(int threadIndex) {
        if (hasFactoryController()) {
            boolean toggled = runtime.factoryRuntime().toggleRecipeLock(threadIndex);
            if (toggled) {
                syncRuntimeStateIfChanged();
                setChanged();
            }
            return toggled;
        }
        if (threadIndex != 0) return false;
        if (lockedRecipeId != null) {
            lockedRecipeId = null;
            setChanged();
            return true;
        }
        MachineRecipe recipe = runtime.craftingRuntime().recipe();
        if (recipe == null) return false;
        lockedRecipeId = recipe.id();
        setChanged();
        return true;
    }

    public void sendRecipeLockState(ServerPlayer player) {
        if (player == null) return;
        runtime.publishSnapshot();
        player.connection.send(new ClientboundCustomPayloadPacket(PktMachineStatePayload.from(getBlockPos(), runtimeSnapshot())));
        sendControllerScreenTextOnMenuOpen(player);
    }

    public boolean hasFactoryController() {
        return isFactoryController(currentRuntimeSnapshot());
    }

    private static boolean isFactoryController(ControllerRuntimeSnapshot state) {
        return SYNC_RUNTIME.factoryControllerPresent(state);
    }

    public int effectiveFactoryThreadLimit() {
        StructureSnapshot structure = runtime.currentStructureSnapshot();
        Machine machine = structure.machine() == null ? structure.configuredMachine() : structure.machine();
        if (machine == null || !machine.hasFactory()) return 1;
        int aggregatedThreads = factorySchedulerThreadCount();
        int levelBonus = runtime.componentRuntime().foundLevels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .map(Map.Entry::getValue)
                .mapToInt(foundLevel -> foundLevel.modifier().factoryThreadBonus())
                .sum();
        long extraThreads = Math.max(0L, (long) aggregatedThreads - 1L);
        long effective = Math.max(1, machine.factoryThreadLimit()) + extraThreads + levelBonus;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, effective));
    }

    public FactoryRecipeScheduler factoryScheduler() {
        if (hasTickBehavior(runtime.currentStructureSnapshot())) {
            throw new IllegalStateException("Factory scheduler requested for tick machine");
        }
        ensureFactoryRuntimeLoaded();
        if (factoryScheduler == null && !hasFactoryControllerCurrent()) {
            throw new IllegalStateException("Factory scheduler requested without a formed factory controller");
        }
        if (factoryScheduler == null) {
            factoryScheduler = new FactoryRecipeScheduler(1, runtime.factoryRuntime());
        }
        runtime.factoryRuntime().ensureBaseLane(this);
        Machine configuredMachine = runtime.currentStructureSnapshot().configuredMachine();
        if (configuredMachine != null && configuredMachine.hasFactory()) {
            factoryScheduler.setThreadLimit(effectiveFactoryThreadLimit());
        }
        return factoryScheduler;
    }

    private boolean hasFactoryControllerCurrent() {
        StructureSnapshot structure = runtime.currentStructureSnapshot();
        Machine machine = structure.machine() == null ? structure.configuredMachine() : structure.machine();
        if (machine == null || !machine.hasFactory() || machine.behavior() instanceof TickBehavior) return false;
        return runtime.components().stream()
                .anyMatch(component -> component.getContainer() instanceof FactorySchedulerBlockEntity);
    }

    public List<FactoryRuntime.ThreadSnapshot> factoryThreadSnapshots() {
        ControllerRuntimeSnapshot state = runtimeSnapshot();
        return isFactoryController(state)
                ? state.factory().presentationLanes()
                : List.of(FactoryRuntime.ThreadSnapshot.idleBase());
    }

    private static String failureUnloc(@Nullable ExecutionStatus failure) {
        if (failure == null) return "";
        return switch (failure.details().getOrDefault("reason", "")) {
            case "module_connection" -> "gui.mmcr.controller.failure.module_connection";
            case "no_output_capacity" -> "gui.mmcr.controller.failure.missing_output";
            case "insufficient_energy" -> "gui.mmcr.controller.failure.missing_energy";
            case "level_insufficient" -> "gui.mmcr.controller.failure.level_insufficient";
            case "version_invalidated" -> "gui.mmcr.controller.failure.structure_changed";
            case "smart_interface_changed" -> "gui.mmcr.controller.failure.smart_interface_changed";
            default -> "gui.mmcr.controller.failure.missing_input";
        };
    }

    public void sendFactoryControllerState(@Nullable ServerPlayer player) {
        if (player != null) {
            ControllerRuntimeSnapshot state = runtimeSnapshot();
            player.connection.send(new ClientboundCustomPayloadPacket(
                    new PktFactoryControllerStatePayload(getBlockPos(), SYNC_RUNTIME.factoryState(state))));
            sendControllerScreenTextOnMenuOpen(player);
            sendFactoryControllerScreenText(player);
        }
    }

    public void sendControllerScreenText(ServerPlayer player) {
        if (player == null) return;
        ControllerScreenTextSnapshot snapshot = runtime.screenText().snapshot();
        player.connection.send(new ClientboundCustomPayloadPacket(new PktControllerScreenTextPayload(
                getBlockPos(), snapshot.revision(), snapshot.lines())));
        lastSentControllerScreenTextRevision = snapshot.revision();
        if (player.containerMenu instanceof FactoryControllerMenu menu
                && menu.controllerPos().equals(getBlockPos())) {
            sendFactoryControllerScreenText(player);
        }
    }

    private void sendFactoryControllerScreenText(ServerPlayer player) {
        Map<String, ControllerScreenTextSnapshot> laneSnapshots = runtime.factoryRuntime().screenTextSnapshots();
        for (Map.Entry<String, ControllerScreenTextSnapshot> entry : laneSnapshots.entrySet()) {
            ControllerScreenTextSnapshot laneSnapshot = entry.getValue();
            player.connection.send(new ClientboundCustomPayloadPacket(new PktControllerScreenTextPayload(
                    getBlockPos(), entry.getKey(), laneSnapshot.revision(), laneSnapshot.lines())));
            lastSentRecipeScreenTextRevisions.put(entry.getKey(), laneSnapshot.revision());
        }
        for (Map.Entry<String, Long> entry : removedRecipeScreenTextRevisions.entrySet()) {
            if (laneSnapshots.containsKey(entry.getKey())) continue;
            player.connection.send(new ClientboundCustomPayloadPacket(new PktControllerScreenTextPayload(
                    getBlockPos(), entry.getKey(), entry.getValue(), List.of())));
        }
    }

    public void markRecipeScreenTextRemoved(String laneId, long revision) {
        if (laneId == null || laneId.isEmpty()) return;
        long tombstoneRevision = Math.max(revision, lastSentRecipeScreenTextRevisions.getOrDefault(laneId, -1L)) + 1L;
        removedRecipeScreenTextRevisions.merge(laneId, tombstoneRevision, Math::max);
    }

    private void sendControllerScreenTextOnMenuOpen(ServerPlayer player) {
        if ((player.containerMenu instanceof MachineControllerMenu menu
                && menu.controllerPos().equals(getBlockPos()))
                || (player.containerMenu instanceof FactoryControllerMenu factoryMenu
                && factoryMenu.controllerPos().equals(getBlockPos()))) {
            return;
        }
        sendControllerScreenText(player);
    }

    public int factorySchedulerThreadCount() {
        long total = 0;
        for (ProcessingComponent component : runtime.components()) {
            if (component.getContainer() instanceof FactorySchedulerBlockEntity scheduler) {
                total += scheduler.threadCount();
                if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    List<FactorySchedulerBlockEntity> factoryComponents() {
        return runtime.components().stream()
                .map(ProcessingComponent::getContainer)
                .filter(FactorySchedulerBlockEntity.class::isInstance)
                .map(FactorySchedulerBlockEntity.class::cast)
                .toList();
    }

    void setFactoryCapacityInvalidationCallbackForTesting(Runnable callback) {
        factoryCapacityInvalidationCallbackForTesting = callback;
    }

    void setStructureCheckCallbackForTesting(Runnable callback) {
        structureCheckCallbackForTesting = callback;
    }

    void invalidateFactoryCapacity() {
        if (factoryComponents().isEmpty() || hasTickBehavior(runtime.currentStructureSnapshot())) return;
        int threadLimit = effectiveFactoryThreadLimit();
        factoryScheduler().setThreadLimit(threadLimit);
        setChanged();
        syncRuntimeStateIfChanged();
        syncOpenFactoryControllerMenus();
        if (factoryCapacityInvalidationCallbackForTesting != null) {
            factoryCapacityInvalidationCallbackForTesting.run();
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || isRemoved()) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) runtime.serverTick(serverLevel, getBlockPos());
    }

    public void tickRuntimeWork(ServerLevel runtimeLevel, BlockPos controllerPos) {
        validateRuntimeBoundary(runtimeLevel, controllerPos);
        if (level == null || level.isClientSide() || isRemoved()) {
            return;
        }
        ControllerRuntimeSnapshot tickState = currentRuntimeSnapshot();
        boolean tickMachine = hasTickBehavior(tickState.structure());
        boolean factoryController = !tickMachine && isFactoryController(tickState);
        int initialFactoryActiveLaneCount = factoryController ? runtime.factoryRuntime().activeLaneCount() : 0;
        boolean hadActiveWork = hasActiveRuntimeWork(null, initialFactoryActiveLaneCount);
        boolean hadActiveOperation = hasActiveOperation();
        boolean wasRedstonePaused = redstonePaused;
        String previousFailureUnloc = lastFailureUnloc;
        ExecutionStatus previousCraftingFailure = runtime.craftingRuntime().failure();
        FactoryTickResult factoryTickResult = null;
        try {
            if (!advanceBuildTask()) {
                // 1.21+ exposes the old strong-power query through SignalGetter's direct signal helper.
                boolean powered = level.getDirectSignalTo(getBlockPos()) > 0;
                if (powered) {
                    redstonePaused = true;
                    runtime.pauseCrafting();
                    setActiveState(false);
                    setChanged();
                } else {
                    redstonePaused = false;
                    runtime.resumeCrafting();
                    if (runtime.craftingRuntime().active()
                            || (!factoryController && initialFactoryActiveLaneCount > 0)) setActiveState(true);
                    if (tickState.structure().formed() && tickState.structure().structureAreaLoaded()) {
                        Machine machine = tickState.structure().machine() == null
                                ? tickState.structure().configuredMachine() : tickState.structure().machine();
                        MachineBehavior behavior = machine == null ? null : machine.behavior();
                        if (behavior instanceof TickBehavior tickBehavior) {
                            setActiveState(true);
                            try {
                                TickBehaviorContext context = runtime.tickBehaviorContext();
                                var tickResult = runtime.craftingRuntime().handleCapabilityTickResult(
                                        runtime.componentRuntime().executeTickPhase(
                                                context.capabilityTickContext(tickBehavior.capabilityTickPhase())));
                                if (tickResult.failure() == null) {
                                    tickBehavior.serverTick().accept(context);
                                } else {
                                    setActiveState(false);
                                }
                            } catch (RuntimeException exception) {
                                logBehaviorCallbackFailure("serverTick", tickState, null, exception);
                            }
                        } else {
                            RecipeBehavior recipeBehavior = behavior instanceof RecipeBehavior recipe ? recipe : null;
                            if (recipeBehavior != null) {
                                invokeServerTickCallback("preServerTick", recipeBehavior.preServerTick(), tickState);
                            }
                            try {
                                boolean idleBefore = !hasActiveOperation();
                                if (recipeBehavior != null && idleBefore) {
                                    invokeIdleCallback("idleStart", recipeBehavior.idleStart(), tickState);
                                }
                                if (factoryController) {
                                    factoryTickResult = tickFactoryRecipes();
                                } else {
                                    tickSingleActiveRecipe();
                                }
                                if (recipeBehavior != null && !hasActiveOperation()) {
                                    invokeIdleCallback("idleEnd", recipeBehavior.idleEnd(), tickState);
                                }
                            } finally {
                                if (recipeBehavior != null) {
                                    invokeServerTickCallback("postServerTick", recipeBehavior.postServerTick(), tickState);
                                }
                            }
                        }
                    }
                }
            }
            applyControllerScreenText();
        } finally {
            if (hadActiveOperation && !hasActiveOperation()) runtime.clearOperationText();
            int finalFactoryActiveLaneCount = factoryTickResult == null
                    ? initialFactoryActiveLaneCount : factoryTickResult.activeLaneCount();
            boolean publish = hadActiveWork || hasActiveRuntimeWork(factoryTickResult, finalFactoryActiveLaneCount)
                    || wasRedstonePaused != redstonePaused
                    || !Objects.equals(previousFailureUnloc, lastFailureUnloc)
                    || previousCraftingFailure != runtime.craftingRuntime().failure()
                    || factoryTickResult != null
                    && (factoryTickResult.snapshotChanged() || factoryTickResult.laneStateChanged());
            if (publish) {
                publishRuntimeState(factoryTickResult, finalFactoryActiveLaneCount);
                if (factoryController && !runtime.updateBatchActive()) syncOpenFactoryControllerMenus();
            }
            syncOpenControllerScreenText();
        }
    }

    public boolean hasActiveBuildTask() {
        return buildTasks != null && buildTasks.hasActiveTask(getBlockPos());
    }

    private boolean hasActiveRuntimeWork(@Nullable FactoryTickResult factoryTickResult,
                                         int activeFactoryLaneCount) {
        return hasActiveBuildTask()
                || !hasTickBehavior(runtime.currentStructureSnapshot()) && runtime.craftingRuntime().active()
                || !hasTickBehavior(runtime.currentStructureSnapshot())
                && (factoryTickResult == null ? activeFactoryLaneCount : factoryTickResult.activeLaneCount()) > 0
                || hasTickBehavior(runtime.currentStructureSnapshot());
    }

    private boolean hasTickBehavior(StructureSnapshot structure) {
        if (!structure.formed() || !structure.structureAreaLoaded()) return false;
        Machine machine = structure.machine() == null ? structure.configuredMachine() : structure.machine();
        return machine != null && machine.behavior() instanceof TickBehavior;
    }

    private boolean hasActiveOperation() {
        if (hasTickBehavior(runtime.currentStructureSnapshot())) return false;
        return runtime.craftingRuntime().active() || runtime.factoryRuntime().activeLaneCount() > 0;
    }

    private void applyControllerScreenText() {
        if (currentRuntimeSnapshot().structure().configuredMachine() != null) {
            ControllerScreenTextRegistry.apply(runtime.runtimeContext());
            runtime.screenText().flushReplacements();
        }
    }

    private void invokeIdleCallback(String phase, MachineBehavior.MachineCallback callback,
                                    ControllerRuntimeSnapshot snapshot) {
        try {
            callback.accept(runtime.behaviorContext());
        } catch (RuntimeException exception) {
            logBehaviorCallbackFailure(phase, snapshot, null, exception);
        }
    }

    private void invokeServerTickCallback(String phase, MachineBehavior.MachineCallback callback,
                                          ControllerRuntimeSnapshot snapshot) {
        try {
            callback.accept(runtime.behaviorContext());
        } catch (RuntimeException exception) {
            logBehaviorCallbackFailure(phase, snapshot, null, exception);
        }
    }

    private void logBehaviorCallbackFailure(String phase, ControllerRuntimeSnapshot snapshot,
                                             @org.jetbrains.annotations.Nullable MachineRecipe recipe,
                                             RuntimeException exception) {
        MMCR.LOG.warn("Machine behavior callback failed: phase={} machine={} recipe={} controller={}", phase,
                snapshot.machineId(), recipe == null ? "" : recipe.id(), getBlockPos(), exception);
    }

    public boolean startBuildTask(MultiblockAssemblyService.BuildTask task, ServerPlayer owner) {
        if (!buildTaskRegistry().submit(task)) return false;
        buildTaskOwner = owner;
        buildTaskAge = 0;
        buildTaskPlacementsPerTickForTesting.clear();
        return true;
    }

    public Map<Long, Integer> buildTaskPlacementsPerTickForTesting() {
        return Map.copyOf(buildTaskPlacementsPerTickForTesting);
    }

    private boolean advanceBuildTask() {
        if (!hasActiveBuildTask()) return false;
        buildTaskAge++;
        ServerPlayer owner = buildTaskOwner;
        if (owner == null || owner.isRemoved() || owner.level() != level
                || buildTaskAge > Config.BUILD_TASK_TIMEOUT_TICKS.get()) {
            buildTaskPlacementsPerTickForTesting.merge(level.getGameTime(), 0, Integer::sum);
            cancelBuildTask();
            return true;
        }
        int placedBefore = buildTaskRegistry().placedCount(getBlockPos());
        buildTaskRegistry().advance(getBlockPos(), placement -> level.getBlockState(placement.pos()).isAir()
                && level.setBlock(placement.pos(), placement.state(), 3));
        int placedThisTick = buildTaskRegistry().placedCount(getBlockPos()) - placedBefore;
        buildTaskPlacementsPerTickForTesting.merge(level.getGameTime(), placedThisTick, Integer::sum);
        var task = buildTaskRegistry().cancel(getBlockPos());
        if (task != null && !task.isComplete()) {
            buildTaskRegistry().submit(task);
        } else if (task != null) {
            task.refundRequirements().forEach(stack -> new PlayerInventoryStructureItemSink(owner).accept(stack));
            if (owner.connection != null) task.takeCompletionReport().ifPresent(owner::sendSystemMessage);
            buildTaskOwner = null;
            buildTaskAge = 0;
            requestImmediateStructureCheck(owner);
        }
        return true;
    }

    private void cancelBuildTask() {
        var task = buildTaskRegistry().cancel(getBlockPos());
        if (task == null) return;
        ServerPlayer owner = buildTaskOwner;
        if (owner != null && !owner.isRemoved() && owner.level() == level) {
            PlayerInventoryStructureItemSink sink = new PlayerInventoryStructureItemSink(owner);
            task.refundRequirements().forEach(sink::accept);
        } else {
            task.refundRequirements().forEach(stack -> {
                if (!stack.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack));
            });
        }
        buildTaskOwner = null;
        buildTaskAge = 0;
    }

    private MultiblockAssemblyService.BuildTaskRegistry buildTaskRegistry() {
        if (buildTasks == null) buildTasks = new MultiblockAssemblyService.BuildTaskRegistry();
        return buildTasks;
    }

    private void tickSingleActiveRecipe() {
        boolean startedThisTick = false;
        if (sharedStartPending && !isPendingSharedStart(pendingSharedStartToken,
                pendingSharedStartRecipe, pendingSharedStartDomain)) {
            clearPendingSharedStart();
        }
        if (!runtime.craftingRuntime().active() && !sharedStartPending) {
            startedThisTick = tryStartNewRecipe();
        }
        if (runtime.craftingRuntime().active() && !startedThisTick && tickActiveRecipe()) tryStartNewRecipe();
        if (!runtime.craftingRuntime().active()) runtime.craftingRuntime().tickIdle();
    }

    private FactoryTickResult tickFactoryRecipes() {
        ControllerRuntimeSnapshot current = currentRuntimeSnapshot();
        StructureSnapshot structure = current.structure();
        long maxParallelism = runtime.maxParallelism(structure.machine());
        List<MachineRecipe> candidates = recipesForMachine();
        FactoryRecipeScheduler scheduler = factoryScheduler();
        scheduler.setThreadLimit(effectiveFactoryThreadLimit());
        FactoryRuntime factory = runtime.factoryRuntime();
        factory.syncCoreLanes(this, structure.machine(), candidates);
        FactorySearchContext context = factory.createSearchContext(current, candidates, maxParallelism,
                level.getGameTime());
        FactoryTickResult result = factory.tick(context, this::playFinishSound);
        setChanged();
        boolean active = result.activeLaneCount() > 0;
        setActiveState(active);
        if (active) {
            lastFailureUnloc = null;
        }
        return result;
    }

    private void checkStructure() {
        runStructureCheckPass();
    }

    private void runStructureCheckPass() {
        publishStructureWork(state -> state.withCheckActive(true));
        try {
            checkStructurePass();
        } finally {
            publishStructureWork(state -> state.withCheckActive(false));
            publishRuntimeState();
        }
    }

    private void checkStructurePass() {
        StructureSnapshot structure = currentRuntimeSnapshot().structure();
        if (structureCheckCallbackForTesting != null) structureCheckCallbackForTesting.run();
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (work.checkReason() == StructureRuntime.CheckReason.SAFETY_CHECK && structure.formed()
                && work.scan() == null) {
            publishStructureWork(state -> state.withDirty(false).withCheckCounter(0)
                    .withNextCheckTick(level.getGameTime() + STRUCTURE_SAFETY_INTERVAL_TICKS));
            runStructureSafetyCheck(structure);
            return;
        }
        publishStructureWork(state -> state.withDirty(false).withCheckCounter(0)
                .withNextCheckTick(level.getGameTime() + (structure.formed()
                        ? STRUCTURE_SAFETY_INTERVAL_TICKS : structureCheckIntervalTicks())));
        if (structureWorkSnapshot().scan() != null) {
            advanceStructureScan();
            return;
        }
        publishStructureWork(state -> state.withFormationFailure(null));
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        Machine configuredMachine = structure.configuredMachine();
        if (facing.getAxis().isVertical() && configuredMachine != null && !configuredMachine.controller().allowVerticalFacing()) {
            resetMachine();
            return;
        }
        Machine matchedMachine = structure.machine();
        BlockArray matchedPattern = structure.pattern();
        if (matchedMachine != null && matchedPattern != null && structure.facing() == facing) {
            if (structure.matchedStage() > 0) {
                for (CandidatePattern candidatePattern : candidatePatterns(matchedMachine, facing)) {
                    if (candidatePattern.stageNumber() > structure.matchedStage()
                            && tryFormMachine(matchedMachine, facing, candidatePattern)) return;
                    if (structureWorkSnapshot().scan() != null) return;
                }
            }
            CandidatePattern currentPattern = new CandidatePattern(structure.compiledPattern(), matchedPattern,
                    structure.rollFacing());
            if (tryFormMachine(matchedMachine, facing, currentPattern)) return;
            if (structureWorkSnapshot().scan() != null) return;
            boolean compiledAreaLoaded = !hasCompiledFacing(structure.compiledPattern(), facing)
                    || StructureMatcher.isAreaLoaded(structure.compiledPattern(), facing, level, getBlockPos());
            if (!compiledAreaLoaded) {
                pauseActiveForUnloadedStructure();
                return;
            }
            if (structure.formed()) {
                Machine retryMachine = matchedMachine != null ? matchedMachine : configuredMachine;
                resetMachine(true, true, false);
                if (retryMachine != null) tryFormMachine(retryMachine, facing);
            }
            return;
        }

        if (configuredMachine != null) {
            if (tryFormMachine(configuredMachine, facing)) {
                return;
            }
            if (structureWorkSnapshot().scan() != null) return;
            Identifier stateMachineId = machineIdFromState(getBlockState());
            if (configuredMachine.registryName().equals(stateMachineId) || configuredMachine.controller().id().equals(stateMachineId)) {
                resetMachine(currentRuntimeSnapshot().structure().lastFormationFailure() == null);
                return;
            }
        }
        checkAllPatterns(facing);
        if (structureWorkSnapshot().scan() != null) return;
        StructureSnapshot finalStructure = currentRuntimeSnapshot().structure();
        if (!finalStructure.formed()) resetMachine(finalStructure.lastFormationFailure() == null);
    }

    private boolean shouldCheckStructure() {
        StructureSnapshot structure = currentRuntimeSnapshot().structure();
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (structure.dirty()) return true;
        if (!structure.formed() && work.nextCheckTick() < 0L) return true;
        publishStructureWork(state -> state.withCheckCounter(work.checkCounter() + 1));
        if (work.scan() != null && level.getGameTime() >= work.nextCheckTick()) return true;
        if (structure.formed() && level.getGameTime() >= work.nextCheckTick()) {
            publishStructureWork(state -> state.withCheckReason(StructureRuntime.CheckReason.SAFETY_CHECK));
            return true;
        }
        return level.getGameTime() >= work.nextCheckTick();
    }

    private void runStructureSafetyCheck(StructureSnapshot structure) {
        structureSafetyCheckCountForTesting++;
        if (!structureSentinelEnabled() || structureSentinelCount() <= 0) {
            runtime.requestStructureCheck(StructureRuntime.CheckReason.DIRTY_EVENT);
            return;
        }
        if (structure.pattern() == null || structure.facing() == null) return;
        Machine machine = structure.machine() == null ? structure.configuredMachine() : structure.machine();
        if (machine == null) return;
        CompiledMachinePattern compiled = structure.compiledPattern();
        CompiledMachinePattern.ScanPlan plan = hasCompiledFacing(compiled, structure.facing())
                ? compiled.scanPlan(structure.facing(), structureSentinelCount())
                : CompiledMachinePattern.ScanPlan.forPattern(structure.pattern(), structureSentinelCount());
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = replacementsFor(
                machine, compiled, structure.facing(), structure.pattern(), structure.rollFacing());
        Optional<StructureMatcher.Mismatch> mismatch = StructureMatcher.firstSentinelMismatch(
                structure.version(), structure.facing(), structure.rollFacing(), structure.matchedStage(),
                structure.pattern(), plan, replacements, compiled != null && compiled.stateSensitive(), level, getBlockPos());
        if (mismatch.isEmpty()) return;
        publishStructureWork(state -> state.withPreviousMismatch(mismatch.get(), structure.pattern()));
        runtime.requestStructureCheck(StructureRuntime.CheckReason.DIRTY_EVENT);
    }

    private void invalidateForControllerRotation() {
        StructureSnapshot structure = currentRuntimeSnapshot().structure();
        if (structure.facing() == null || level == null) return;
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        Direction rollFacing = getBlockState().getValue(MachineControllerBlock.ROLL_FACING);
        Direction normalizedRoll = BlockRotator.normalizedRoll(facing, rollFacing);
        if (structure.facing() != facing
                || (facing.getAxis().isVertical() && structure.rollFacing() != normalizedRoll)) {
            invalidateStructureScan(StructureMatcher.InvalidationReason.ORIENTATION);
            runtime.requestStructureCheck();
        }
    }

    private int structureCheckIntervalTicks() {
        if (structureCheckIntervalOverrideForTesting != null) return structureCheckIntervalOverrideForTesting;
        try {
            return Config.MACHINE_CHECK_INTERVAL_TICKS.get();
        } catch (IllegalStateException ignored) {
            return Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS;
        }
    }

    private void checkAllPatterns(Direction facing) {
        Machine configuredMachine = currentRuntimeSnapshot().structure().configuredMachine();
        for (Machine candidate : MachineRegistry.effectiveSnapshot().values()) {
            if (candidate == configuredMachine) continue;
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
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (level == null || level.isClientSide() || runtimeSnapshot().structure().formed()) return false;
        if (configuredMachine == null) bindDefaultMachine();
        configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (configuredMachine == null) return false;

        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        for (CandidatePattern candidatePattern : candidatePatterns(configuredMachine, facing)) {
            BlockArray rotatedPattern = candidatePattern.pattern();
            CompiledMachinePattern compiled = compiledFor(configuredMachine, rotatedPattern, facing);
            Machine validationMachine = compiled == null ? configuredMachine : compiled.machine();
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = replacementsFor(validationMachine, compiled, facing, rotatedPattern, candidatePattern.rollFacing());
            boolean stateSensitive = compiled != null && compiled.stateSensitive();
            matcherInvocationCountForTesting++;
            var mismatch = StructureMatcher.firstMismatch(rotatedPattern, level, getBlockPos(), replacements, stateSensitive);
            if (mismatch.isPresent()) {
                sendStructureMismatchDiagnostic(player, mismatch.get());
                return true;
            }
        }
        PortRequirementSpec.Failure formationFailure = runtimeSnapshot().structure().lastFormationFailure();
        if (formationFailure != null) {
            sendFormationFailureDiagnostic(player, formationFailure);
            return true;
        }
        return false;
    }

    public Optional<MultiblockPreviewSnapshot> createStructurePreviewSnapshot(int maxEntries) {
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (level == null || level.isClientSide() || runtimeSnapshot().structure().formed()) return Optional.empty();
        if (configuredMachine == null) bindDefaultMachine();
        configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (configuredMachine == null) return Optional.empty();

        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        for (CandidatePattern candidatePattern : candidatePatterns(configuredMachine, facing)) {
            MultiblockPreviewSnapshot snapshot = MultiblockPreviewBuilder.build(level, getBlockPos(), candidatePattern.pattern(), maxEntries);
            if (!snapshot.isEmpty()) return Optional.of(snapshot);
        }
        return Optional.empty();
    }

    public Optional<Machine> boundMachine() {
        if (runtimeSnapshot().structure().configuredMachine() == null) bindDefaultMachine();
        return Optional.ofNullable(runtimeSnapshot().structure().configuredMachine());
    }

    public BlockArray assemblyPattern(Machine candidate) {
        StructureSnapshot structure = currentRuntimeSnapshot().structure();
        if (structure.pattern() != null) return structure.pattern();
        return assemblyPattern(candidate, structure.formed() && structure.matchedStage() > 0
                ? structure.matchedStage() : 1);
    }

    public BlockArray assemblyPattern(Machine candidate, int stageNumber) {
        if (stageNumber < 1) throw new IllegalArgumentException("stageNumber must be >= 1");
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        return candidatePatterns(candidate, facing).stream()
                .filter(pattern -> pattern.stageNumber() == stageNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown structure stage " + stageNumber
                        + " for machine " + candidate.registryName()))
                .pattern();
    }

    public boolean assemblyStateSensitive(Machine candidate) {
        StructureSnapshot structure = runtimeSnapshot().structure();
        int stageNumber = structure.formed() && structure.matchedStage() > 0
                ? structure.matchedStage() : 1;
        return candidate.structureStages().stream()
                .filter(stage -> stage.number() == stageNumber)
                .findFirst()
                .map(MachineStructureStage::stateSensitive)
                .orElse(false);
    }

    public boolean sendStructurePreview(ServerPlayer player) {
        Optional<MultiblockPreviewSnapshot> snapshot = createStructurePreviewSnapshot(PktMultiblockPreviewPayload.MAX_ENTRIES);
        if (snapshot.isEmpty()) return false;
        PacketDistributor.sendToPlayer(player, new PktMultiblockPreviewPayload(snapshot.get()));
        rememberPreviewReceiver(player.getUUID(), level.getGameTime(), PREVIEW_RECEIVER_WINDOW_TICKS);
        return true;
    }

    public void clearStructurePreview(ServerPlayer player) {
        previewReceivers().remove(player.getUUID());
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayer(player, PktMultiblockPreviewPayload.clear(serverLevel.dimension(), getBlockPos()));
        }
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
        notifyPreviewReceiversCleared();
    }

    private void notifyPreviewReceiversCleared() {
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

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        invalidateStructureScan(StructureMatcher.InvalidationReason.REMOVED);
        if (level != null && !level.isClientSide()) notifyPreviewReceiversCleared();
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
        if (failure.portId().startsWith(SHARED_COMPONENT_CONFLICT)) {
            BlockPos componentPos = sharedConflictComponentPosition(failure.portId());
            Component componentName = componentPos == null || level == null
                    ? Component.literal("?").withStyle(ChatFormatting.RED)
                    : level.getBlockState(componentPos).getBlock().getName().copy().withStyle(ChatFormatting.RED);
            MutableComponent message = Component.empty();
            if (componentPos != null) message.append(styledPosition(componentPos)).append(" ");
            message.append(Component.translatable("message.mmcr.multiblock_shared_component_conflict", componentName));
            player.sendSystemMessage(message);
            return;
        }
        player.sendSystemMessage(describeFormationFailure(failure));
    }

    private static @Nullable BlockPos sharedConflictComponentPosition(String failureId) {
        String prefix = SHARED_COMPONENT_CONFLICT + " component=BlockPos{x=";
        if (!failureId.startsWith(prefix)) return null;
        int xEnd = failureId.indexOf(", y=", prefix.length());
        int yEnd = failureId.indexOf(", z=", xEnd + 4);
        int zEnd = failureId.indexOf('}', yEnd + 4);
        if (xEnd < 0 || yEnd < 0 || zEnd < 0) return null;
        try {
            return new BlockPos(Integer.parseInt(failureId.substring(prefix.length(), xEnd)),
                    Integer.parseInt(failureId.substring(xEnd + 4, yEnd)),
                    Integer.parseInt(failureId.substring(yEnd + 4, zEnd)));
        } catch (NumberFormatException ignored) {
            return null;
        }
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
            case BlockPredicate.DeferredBlock deferredBlock -> deferredBlock.supplier().get().getName();
            case BlockPredicate.OfBlockState ofState -> ofState.state().getBlock().getName();
            case BlockPredicate.OfTag ofTag -> Component.literal("#" + ofTag.tag().location());
            case BlockPredicate.AnyOf anyOf -> anyOf.children().isEmpty()
                    ? Component.literal("<empty>")
                    : MultiblockPreviewPredicates.representativeValue(expected,
                            predicate -> Optional.of(rawExpectedDescription(predicate)))
                    .orElseGet(() -> rawExpectedDescription(anyOf.children().getFirst()));
            case BlockPredicate.Air ignored -> Component.translatable("block.minecraft.air");
            case BlockPredicate.Any ignored -> Component.literal("any block");
            case BlockPredicate.MachineCoupler ignored -> Component.literal("machine coupler");
        };
    }

    private static Component styledPosition(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN);
    }

    List<Integer> candidateStageNumbers(Machine candidate, Direction facing) {
        return candidatePatterns(candidate, facing).stream().map(CandidatePattern::stageNumber).toList();
    }

    private List<CandidatePattern> candidatePatterns(Machine candidate, Direction facing) {
        List<CompiledMachinePattern> stages = MachineRegistry.getCompiledStages(candidate.registryName());
        if (stages.isEmpty()) {
            if (!facing.getAxis().isVertical()) {
                return List.of(new CandidatePattern(null, BlockArrayCache.get(candidate.pattern(), facing), Direction.SOUTH));
            }
            Direction rollFacing = BlockRotator.normalizedRoll(facing, getBlockState().getValue(MachineControllerBlock.ROLL_FACING));
            if (!candidate.controller().fullyRotationallySymmetric()) {
                return List.of(new CandidatePattern(null, BlockArrayCache.get(candidate.pattern(), facing, rollFacing), rollFacing));
            }
            List<CandidatePattern> fallback = new ArrayList<>(4);
            for (Direction candidateRoll : Direction.Plane.HORIZONTAL) {
                fallback.add(new CandidatePattern(null,
                        BlockArrayCache.get(candidate.pattern(), facing, candidateRoll), candidateRoll));
            }
            return fallback;
        }
        List<CandidatePattern> patterns = new ArrayList<>();
        for (int index = stages.size() - 1; index >= 0; index--) {
            CompiledMachinePattern compiled = stages.get(index);
            if (compiled == null) continue;
            if (!facing.getAxis().isVertical()) {
                patterns.add(new CandidatePattern(compiled, compiled.rotatedPattern(facing), Direction.SOUTH));
                continue;
            }
            Direction rollFacing = BlockRotator.normalizedRoll(facing, getBlockState().getValue(MachineControllerBlock.ROLL_FACING));
            if (!candidate.controller().fullyRotationallySymmetric()) {
                patterns.add(new CandidatePattern(compiled, BlockArrayCache.get(compiled.machine().pattern(), facing, rollFacing), rollFacing));
            } else {
                for (Direction candidateRoll : Direction.Plane.HORIZONTAL) {
                    patterns.add(new CandidatePattern(compiled,
                            BlockArrayCache.get(compiled.machine().pattern(), facing, candidateRoll), candidateRoll));
                }
            }
        }
        return patterns;
    }

    private boolean tryFormMachine(Machine candidate, Direction facing, CandidatePattern candidatePattern) {
        BlockArray rotatedPattern = candidatePattern.pattern();
        CompiledMachinePattern stageCompiled = candidatePattern.compiled();
        Machine validationMachine = stageCompiled == null ? candidate : stageCompiled.machine();
        var replacements = replacementsFor(validationMachine, stageCompiled, facing, rotatedPattern, candidatePattern.rollFacing());
        boolean stateSensitive = stageCompiled != null && stageCompiled.stateSensitive();
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (!work.checkActive() || (rotatedPattern.pattern().size() <= structureScanBatches()
                && !work.diagnosticRequested())) {
            matcherInvocationCountForTesting++;
            boolean matches = stageCompiled != null && hasCompiledFacing(stageCompiled, facing)
                    ? StructureMatcher.matchesCompiled(stageCompiled, facing, candidatePattern.rollFacing(), level,
                    getBlockPos(), replacements, stateSensitive)
                    : StructureMatcher.matchesRotated(rotatedPattern, level, getBlockPos(), replacements, stateSensitive);
            if (!matches) {
                recordStructureMismatch(candidate, facing, rotatedPattern, replacements, stateSensitive);
                return false;
            }
            return validateAndFormMachine(candidate, facing, candidatePattern, validationMachine, rotatedPattern,
                    stageCompiled, replacements);
        }
        if (!isPatternAreaLoaded(rotatedPattern)) return false;
        if (work.scanSteppedTick() == level.getGameTime()) return false;
        StructureMatcher.ScanOptions options = StructureMatcher.ScanOptions.of(structureScanBatches(),
                structureSentinelEnabled(), structureSentinelCount());
        int scanBatchSize = rotatedPattern.isEmpty() ? 0
                : (rotatedPattern.pattern().size() + options.batchCount() - 1) / options.batchCount();
        CompiledMachinePattern.ScanPlan scanPlan = stageCompiled == null || !hasCompiledFacing(stageCompiled, facing)
                ? null : stageCompiled.scanPlan(facing, Math.min(options.sentinelCount(), scanBatchSize));
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(currentRuntimeSnapshot().structure().version(), facing, candidatePattern.rollFacing(),
                candidatePattern.stageNumber(), rotatedPattern, rotatedPattern, replacements, stateSensitive,
                options, rotatedPattern == work.previousMismatchPattern() ? work.previousMismatch() : null,
                scanPlan, runtime.structureChunkStateEpoch());
        runtime.startStructureScan(scan, candidate, candidatePattern, level.getGameTime(), level.getGameTime());
        ACTIVE_STRUCTURE_SCANS.add(this);
        if (invalidateActiveStructureScanIfIdentityChanged()) {
            clearStructureScan();
            return false;
        }
        scanBatchCountForTesting++;
        scanBatchesPerTickForTesting.merge(level.getGameTime(), 1, Integer::sum);
        StructureMatcher.ScanResult scanResult = runtime.stepStructureScan(serverLevel(), getBlockPos());
        if (scanResult.inProgress()) {
            publishStructureWork(state -> state.withNextCheckTick(level.getGameTime() + 1L)
                    .withCheckReason(StructureRuntime.CheckReason.SCAN_CONTINUATION));
            return false;
        }
        if (scanResult.status() == StructureMatcher.ScanStatus.INVALIDATED) {
            clearStructureDiagnosticRequest();
            clearStructureScan();
            publishStructureWork(state -> state.withPendingInvalidation(false));
            return false;
        }
        if (scanResult.status() == StructureMatcher.ScanStatus.MISMATCH) {
            publishStructureWork(state -> state.withPreviousMismatch(scanResult.mismatch().orElse(null), rotatedPattern));
            if (scanResult.mismatch().isPresent()) {
                sendRequestedStructureDiagnostic(scanResult.mismatch().get());
            }
            recordStructureMismatch(candidate, facing, rotatedPattern, replacements, stateSensitive,
                    scanResult.mismatch().orElse(null));
            clearStructureScan();
            publishStructureWork(state -> state.withPendingInvalidation(false));
            if (currentRuntimeSnapshot().structure().formed()) resetMachine(true, true, false);
            publishStructureWork(state -> state.withNextCheckTick(level.getGameTime() + structureCheckIntervalTicks()));
            return false;
        }
        if (structureWorkSnapshot().pendingInvalidation()) {
            clearStructureScan();
            publishStructureWork(state -> state.withPendingInvalidation(false));
            runtime.requestStructureCheck();
            return false;
        }
        clearStructureScan();
        publishStructureWork(state -> state.withPendingInvalidation(false).withPreviousMismatch(null, null));
        return validateAndFormMachine(candidate, facing, candidatePattern, validationMachine, rotatedPattern,
                stageCompiled, replacements);
    }

    private void advanceStructureScan() {
        publishStructureWork(state -> state.withScanSteppedTick(level.getGameTime()));
        StructureWorkSnapshot work = structureWorkSnapshot();
        boolean identityChanged = invalidateActiveStructureScanIfIdentityChanged();
        if (!identityChanged && level.getGameTime() - work.scanStartedTick() > structureScanTimeoutTicks()) {
            runtime.invalidateStructureScan(StructureMatcher.InvalidationReason.TIMEOUT);
        }
        scanBatchCountForTesting++;
        scanBatchesPerTickForTesting.merge(level.getGameTime(), 1, Integer::sum);
        StructureMatcher.ScanResult scanResult = runtime.stepStructureScan(serverLevel(), getBlockPos());
        if (scanResult.inProgress()) {
            publishStructureWork(state -> state.withNextCheckTick(level.getGameTime() + 1L)
                    .withCheckReason(StructureRuntime.CheckReason.SCAN_CONTINUATION));
            return;
        }
        CandidatePattern candidatePattern = work.scanCandidate() instanceof CandidatePattern candidate ? candidate : null;
        Machine candidate = work.scanMachine();
        if (scanResult.status() == StructureMatcher.ScanStatus.INVALIDATED || candidate == null || candidatePattern == null) {
            clearStructureScan();
            publishStructureWork(state -> state.withPendingInvalidation(false));
            runtime.requestStructureCheck();
            return;
        }
        if (scanResult.status() == StructureMatcher.ScanStatus.MISMATCH) {
            clearStructureScan();
            publishStructureWork(state -> state.withPreviousMismatch(scanResult.mismatch().orElse(null), candidatePattern.pattern()));
            var validationMachine = candidatePattern.compiled() == null ? candidate : candidatePattern.compiled().machine();
            var replacements = replacementsFor(validationMachine, candidatePattern.compiled(),
                    getBlockState().getValue(MachineControllerBlock.FACING), candidatePattern.pattern(), candidatePattern.rollFacing());
            if (scanResult.mismatch().isPresent()) {
                sendRequestedStructureDiagnostic(scanResult.mismatch().get());
            }
            recordStructureMismatch(candidate, getBlockState().getValue(MachineControllerBlock.FACING),
                    candidatePattern.pattern(), replacements, candidatePattern.compiled() != null && candidatePattern.compiled().stateSensitive(),
                    scanResult.mismatch().orElse(null));
            if (currentRuntimeSnapshot().structure().formed()) resetMachine(true, true, false);
            publishStructureWork(state -> state.withPendingInvalidation(false)
                    .withNextCheckTick(level.getGameTime() + structureCheckIntervalTicks()));
            return;
        }
        if (scanResult.status() == StructureMatcher.ScanStatus.VALID
                && invalidateActiveStructureScanIfIdentityChanged()) {
            clearStructureScan();
            publishStructureWork(state -> state.withPendingInvalidation(false));
            runtime.requestStructureCheck();
            return;
        }
        if (structureWorkSnapshot().pendingInvalidation()) {
            clearStructureScan();
            publishStructureWork(state -> state.withPendingInvalidation(false));
            runtime.requestStructureCheck();
            return;
        }
        publishStructureWork(state -> state.withPreviousMismatch(null, null).withPendingInvalidation(false));
        clearStructureScan();
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        CompiledMachinePattern compiled = candidatePattern.compiled();
        Machine validationMachine = compiled == null ? candidate : compiled.machine();
        var replacements = replacementsFor(validationMachine, compiled, facing, candidatePattern.pattern(), candidatePattern.rollFacing());
        validateAndFormMachine(candidate, facing, candidatePattern, validationMachine, candidatePattern.pattern(), compiled, replacements);
    }

    private ServerLevel serverLevel() {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new IllegalStateException("Structure scan requires a server level");
    }

    private boolean validateAndFormMachine(Machine candidate, Direction facing, CandidatePattern candidatePattern,
                                           Machine validationMachine, BlockArray rotatedPattern,
                                           CompiledMachinePattern stageCompiled,
                                           Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {

        var levels = resolveLevels(validationMachine, facing, candidatePattern.rollFacing());
        if (levels.mismatch() != null) {
            recordLevelMismatch(levels.mismatch());
            return false;
        }

        var failure = validationMachine.portRequirements().validate(countPorts(rotatedPattern, stageCompiled, facing));
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            sendRequestedStructureDiagnostic(null);
            return false;
        }

        failure = validatePortTiers(validationMachine, rotatedPattern, stageCompiled, facing);
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            sendRequestedStructureDiagnostic(null);
            return false;
        }

        failure = validateFactoryControllerCount(validationMachine, rotatedPattern, stageCompiled, facing);
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            sendRequestedStructureDiagnostic(null);
            return false;
        }

        StructureSnapshot currentStructure = currentRuntimeSnapshot().structure();
        boolean stableFormation = currentStructure.formed()
                && runtime.formationIdentityMatches(candidate, rotatedPattern, stageCompiled, facing,
                candidatePattern.rollFacing(), candidatePattern.stageNumber())
                && !structureWorkSnapshot().componentRefreshRequired();
        if (level instanceof ServerLevel serverLevel && !stableFormation) {
            StructureClaimRegistry.ClaimResult result = StructureClaimRegistry.get(serverLevel)
                    .claim(getBlockPos(), componentClaims(rotatedPattern, stageCompiled, facing));
            if (!result.accepted()) {
                StructureClaimRegistry.Conflict conflict = result.conflict();
                publishStructureWork(state -> state.withFormationFailure(new PortRequirementSpec.Failure(
                        SHARED_COMPONENT_CONFLICT + " component=" + conflict.componentPos()
                                + " owner=" + conflict.ownerPos(),
                        0, 1, OptionalInt.empty(), PortRequirementSpec.FailureReason.MISSING)));
                sendRequestedStructureDiagnostic(null);
                return false;
            }
            if (ModuleConnectionCoordinator.blocksHostFormation(serverLevel, candidate, stageCompiled, facing, getBlockPos())) {
                StructureClaimRegistry.get(serverLevel).release(getBlockPos());
                return false;
            }
        }

        publishStructureWork(state -> state.withFormationFailure(null).withMismatchDiagnostic(null));
        onStructureFormed(candidate, rotatedPattern, stageCompiled, facing, candidatePattern.rollFacing(), replacements, levels.foundLevels());
        return true;
    }

    private void clearStructureScan() {
        ACTIVE_STRUCTURE_SCANS.remove(this);
        runtime.clearStructureScan();
    }

    private void sendRequestedStructureDiagnostic(@Nullable StructureMatcher.Mismatch mismatch) {
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (!work.diagnosticRequested()) return;
        UUID playerId = work.diagnosticPlayerId();
        ResourceKey<Level> dimension = work.diagnosticDimension();
        PortRequirementSpec.Failure formationFailure = work.formationFailure();
        clearStructureDiagnosticRequest();
        if (mismatch != null) {
            if (structureDiagnosticCallbackForTesting != null) {
                structureDiagnosticCallbackForTesting.run();
            } else if (playerId != null && dimension != null && level instanceof ServerLevel serverLevel
                    && serverLevel.dimension().equals(dimension)) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && player.level().dimension().equals(dimension)) {
                    sendStructureMismatchDiagnostic(player, mismatch);
                }
            }
        } else if (formationFailure != null) {
            if (playerId != null && dimension != null && level instanceof ServerLevel serverLevel
                    && serverLevel.dimension().equals(dimension)) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && player.level().dimension().equals(dimension)) {
                    sendFormationFailureDiagnostic(player, formationFailure);
                }
            }
        }
    }

    private void clearStructureDiagnosticRequest() {
        publishStructureWork(state -> state.withDiagnostic(false, null, null));
    }

    private void invalidateStructureScan(StructureMatcher.InvalidationReason reason) {
        clearStructureDiagnosticRequest();
        runtime.invalidateStructureScan(reason);
    }

    private boolean invalidateActiveStructureScanIfIdentityChanged() {
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (work.scan() == null) return false;
        if (isRemoved()) {
            runtime.invalidateStructureScan(StructureMatcher.InvalidationReason.REMOVED);
            return true;
        }
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        Direction rollFacing = getBlockState().getValue(MachineControllerBlock.ROLL_FACING);
        Direction normalizedRoll = BlockRotator.normalizedRoll(facing, rollFacing);
        CandidatePattern candidatePattern = work.scanCandidate() instanceof CandidatePattern candidate ? candidate : null;
        long currentStructureVersion = currentRuntimeSnapshot().structure().version();
        boolean changed = work.scan().version() != currentStructureVersion
                || work.scan().facing() != facing
                || work.scan().rollFacing() != normalizedRoll
                || candidatePattern == null
                || work.scan().stage() != candidatePattern.stageNumber()
                || work.scan().pattern() != candidatePattern.pattern()
                || work.scan().chunkStateEpoch() != runtime.structureChunkStateEpoch();
        if (!changed) return false;
        StructureMatcher.InvalidationReason reason = work.scan().chunkStateEpoch() != runtime.structureChunkStateEpoch()
                ? StructureMatcher.InvalidationReason.UNLOADED
                : work.scan().version() != currentStructureVersion
                ? StructureMatcher.InvalidationReason.VERSION
                : work.scan().facing() != facing || work.scan().rollFacing() != normalizedRoll
                ? StructureMatcher.InvalidationReason.ORIENTATION
                : work.scan().stage() != candidatePattern.stageNumber()
                ? StructureMatcher.InvalidationReason.STAGE
                : StructureMatcher.InvalidationReason.PATTERN;
        runtime.invalidateStructureScan(reason);
        return true;
    }

    private int structureScanBatches() {
        if (structureScanBatchesOverrideForTesting != null) return structureScanBatchesOverrideForTesting;
        try { return Config.STRUCTURE_SCAN_BATCHES.get(); }
        catch (IllegalStateException ignored) { return Config.DEFAULT_STRUCTURE_SCAN_BATCHES; }
    }

    private long structureScanTimeoutTicks() {
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (work.scan() == null) return (long) structureCheckIntervalTicks() * structureScanBatches();
        int batchSize = work.scan().batchSize();
        int sentinelBudget = Math.min(structureSentinelCount(), Math.max(0, batchSize - 1));
        int entriesPerTick = Math.max(1, batchSize - sentinelBudget);
        long scanTicks = (work.scan().entryCount() + entriesPerTick - 1L) / entriesPerTick;
        return (long) structureCheckIntervalTicks() * Math.max(structureScanBatches(), (int) scanTicks);
    }

    private static int structureSentinelCount() {
        try { return Config.STRUCTURE_SENTINEL_COUNT.get(); }
        catch (IllegalStateException ignored) { return Config.DEFAULT_STRUCTURE_SENTINEL_COUNT; }
    }

    private static boolean structureSentinelEnabled() {
        try { return Config.STRUCTURE_SENTINEL_ENABLED.get(); }
        catch (IllegalStateException ignored) { return true; }
    }

    private boolean isPatternAreaLoaded(BlockArray pattern) {
        if (level == null || pattern == null || pattern.isEmpty()) return true;
        BoundingBox box = boundingBox(pattern);
        int minChunkX = (getBlockPos().getX() + box.minX()) >> 4;
        int maxChunkX = (getBlockPos().getX() + box.maxX()) >> 4;
        int minChunkZ = (getBlockPos().getZ() + box.minZ()) >> 4;
        int maxChunkZ = (getBlockPos().getZ() + box.maxZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private Map<BlockPos, List<SingleBlockModifierReplacement>> replacementsFor(
            Machine candidate, CompiledMachinePattern compiled, Direction facing, BlockArray rotatedPattern, Direction rollFacing) {
        if (compiled != null && compiled.stageNumber() == 1 && candidate instanceof DynamicMachine dynamic) {
            return dynamic.rotatedModifierReplacements(facing, rollFacing);
        }
        if (compiled != null) {
            return compiled.modifierReplacements(facing, rollFacing);
        }
        if (candidate instanceof DynamicMachine dynamic) {
            return dynamic.rotatedModifierReplacements(facing, rollFacing);
        }
        return Map.of();
    }

    private void recordStructureMismatch(Machine candidate, Direction facing, BlockArray rotatedPattern,
                                         Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                         boolean stateSensitive) {
        recordStructureMismatch(candidate, facing, rotatedPattern, replacements, stateSensitive, null);
    }

    private void recordStructureMismatch(Machine candidate, Direction facing, BlockArray rotatedPattern,
                                         Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                         boolean stateSensitive, @Nullable StructureMatcher.Mismatch mismatch) {
        if (candidate == null) return;
        String diagnostic = structureMismatchDiagnostic(candidate, facing, rotatedPattern, level, getBlockPos(), replacements,
                stateSensitive, mismatch);
        if (diagnostic.equals(structureWorkSnapshot().mismatchDiagnostic())) return;
        publishStructureWork(state -> state.withMismatchDiagnostic(diagnostic));
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
        return structureMismatchDiagnostic(candidate, facing, rotatedPattern, level, ctrlPos, replacements, true);
    }

    static String structureMismatchDiagnostic(Machine candidate, Direction facing, BlockArray rotatedPattern, Level level, BlockPos ctrlPos,
                                               Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                               boolean stateSensitive) {
        return structureMismatchDiagnostic(candidate, facing, rotatedPattern, level, ctrlPos, replacements, stateSensitive, null);
    }

    static String structureMismatchDiagnostic(Machine candidate, Direction facing, BlockArray rotatedPattern, Level level, BlockPos ctrlPos,
                                               Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                               boolean stateSensitive, @Nullable StructureMatcher.Mismatch mismatch) {
        if (rotatedPattern.isEmpty()) {
            return "machine=" + candidate.registryName()
                    + " facing=" + facing.name()
                    + " controllerPos=" + ctrlPos
                    + " reason=emptyPattern";
        }

        if (mismatch == null) {
            mismatch = StructureMatcher.firstMismatch(rotatedPattern, level, ctrlPos, replacements, stateSensitive).orElse(null);
        }
        if (mismatch != null) {
            StructureMatcher.Mismatch first = mismatch;
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
        if (failure.portId().startsWith(SHARED_COMPONENT_CONFLICT)) {
            return "machine=" + candidate.registryName()
                    + " facing=" + facing.name()
                    + " controllerPos=" + ctrlPos
                    + " reason=sharedComponentConflict"
                    + " details=" + failure.portId();
        }
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
        if (failure.equals(structureWorkSnapshot().formationFailure())) return;
        String diagnostic = formationFailureDiagnostic(candidate,
                getBlockState().getValue(MachineControllerBlock.FACING), getBlockPos(), failure);
        publishStructureWork(state -> state.withFormationFailure(failure).withMismatchDiagnostic(diagnostic));
    }

    private StructureMatcher.LevelResolution resolveLevels(Machine candidate, Direction facing, Direction rollFacing) {
        MachineStructureDefinition definition = MachineStructureRegistry.effectiveSnapshot().get(candidate.registryName());
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
        publishStructureWork(state -> state.withLastStructureError(mismatch)
                .withFormationFailure(null).withMismatchDiagnostic(null));
    }

    private void onStructureFormed(Machine matchedMachine, BlockArray rotatedPattern, CompiledMachinePattern compiledPattern,
                                   Direction facing, Direction rollFacing, Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
        Map<Identifier, MachineLevel> levels) {
        runtime.beginUpdateBatch();
        try {
            clearStructureDiagnosticRequest();
            ControllerRuntimeSnapshot previousSnapshot = currentRuntimeSnapshot();
            StructureSnapshot previousStructure = previousSnapshot.structure();
            Set<BlockPos> previousLinkedPortPositions = previousSnapshot.linkedPortPositions();
            Machine previousMachine = previousStructure.machine();
            StructureWorkSnapshot previousWork = structureWorkSnapshot();
            boolean preserveRestoredFactory = restoringFactoryRuntime
                    && previousStructure.configuredMachine() != null
                    && previousStructure.configuredMachine().registryName().equals(matchedMachine.registryName());
            boolean structureChanged = runtime.publishFormationState(matchedMachine, rotatedPattern, compiledPattern,
                    facing, rollFacing, compiledPattern == null ? 1 : compiledPattern.stageNumber());

            boolean refreshComponents = !previousStructure.formed() || structureChanged
                    || previousWork.componentRefreshRequired();
            if (!refreshComponents) {
                publishStructureWork(state -> state.withDirty(false).withComponentRefreshRequired(false)
                        .withCheckReason(StructureRuntime.CheckReason.SAFETY_CHECK)
                        .withNextCheckTick(level.getGameTime() + STRUCTURE_SAFETY_INTERVAL_TICKS)
                        .withFormationFailure(null).withLastStructureError(null));
                FORMED_CONTROLLERS.add(this);
                restoringFactoryRuntime = false;
                resumePausedRecipeAfterStructureCheck();
                setChanged();
                publishRuntimeState();
                return;
            }

            long previousCapabilityVersion = previousSnapshot.capabilityVersion();
            refreshModuleConnectionState();
            boolean modifiersAllowed = allowsModifiers(matchedMachine);
            runtime.setModifiersAllowed(modifiersAllowed);
            Map<String, List<RecipeModifier>> foundModifiers = collectFoundModifiers(replacements);
            runtime.publishComponentState(runtime.components(), foundModifiers, levels, previousLinkedPortPositions);
            refreshCriticalStructureChunks(rotatedPattern, compiledPattern, facing);
            if (level instanceof ServerLevel serverLevel) ModuleConnectionCoordinator.enqueueCouplers(serverLevel, this);
            boolean componentsChanged = componentsNeedRefresh(rotatedPattern, compiledPattern, facing);
            boolean modifiersChanged = !previousSnapshot.foundModifiers().equals(foundModifiers);
            boolean capabilityTopologyChanged = previousCapabilityVersion != currentRuntimeSnapshot().capabilityVersion();
            if (((previousMachine != null && previousMachine != matchedMachine) || structureChanged || componentsChanged)
                    && (!preserveRestoredFactory || componentsChanged)) {
                stopFactoryController();
            }
            publishStructureWork(state -> state.withDirty(false).withComponentRefreshRequired(false)
                    .withCheckReason(StructureRuntime.CheckReason.SAFETY_CHECK)
                    .withNextCheckTick(level.getGameTime() + STRUCTURE_SAFETY_INTERVAL_TICKS));
            if (!physicalFormed()) {
                updatePhysicalFormedState(true);
                notifyPreviewReceiversStructureFormed();
            }
            FORMED_CONTROLLERS.add(this);
            if (structureChanged || componentsChanged) {
                updateComponents(previousStructure, matchedMachine, rotatedPattern, compiledPattern, facing,
                        previousLinkedPortPositions, foundModifiers, levels);
                if (level instanceof ServerLevel serverLevel) {
                    NetworkInterfaceBindingCoordinator.reconcile(serverLevel.getServer(), this);
                }
            }
            if (preserveRestoredFactory) {
                runtime.craftingRuntime().rebindCurrentVersions();
                runtime.factoryRuntime().rebindCurrentVersions();
            }
            restoringFactoryRuntime = false;
            resumePausedRecipeAfterStructureCheck();
            if (structureChanged || componentsChanged || modifiersChanged || capabilityTopologyChanged) {
                clearCandidateCache();
            }
            publishStructureWork(state -> state.withFormationFailure(null).withLastStructureError(null));
            setChanged();
            syncLevelState();
            publishRuntimeState();
        } finally {
            runtime.endUpdateBatch();
        }
    }

    private List<StructureClaimRegistry.Claim> componentClaims(BlockArray pattern,
                                                                 @Nullable CompiledMachinePattern compiled,
                                                                 Direction facing) {
        List<StructureClaimRegistry.Claim> claims = new ArrayList<>();
        if (level == null) return claims;
        for (BlockPos relativePos : componentPositions(pattern, compiled, facing)) {
            BlockEntity entity = level.getBlockEntity(getBlockPos().offset(relativePos));
            if (entity instanceof SmartInterfaceBlockEntity) {
                claims.add(new StructureClaimRegistry.Claim(entity.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED));
            } else if (entity instanceof MachineComponentTile tile) {
                claims.add(new StructureClaimRegistry.Claim(entity.getBlockPos(), tile.claimPolicy()));
            } else if (entity instanceof ParallelControllerBlockEntity || entity instanceof FactorySchedulerBlockEntity) {
                ComponentClaimPolicy policy = entity instanceof FactorySchedulerBlockEntity
                        ? ComponentClaimPolicy.SHARED_CAPACITY
                        : ComponentClaimPolicy.EXCLUSIVE;
                claims.add(new StructureClaimRegistry.Claim(entity.getBlockPos(), policy));
            }
        }
        return claims;
    }

    private Map<String, List<RecipeModifier>> collectFoundModifiers(
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        Map<String, List<RecipeModifier>> nextModifiers = new LinkedHashMap<>();
        if (level != null) {
            for (var entry : replacements.entrySet()) {
                BlockState actual = level.getBlockState(getBlockPos().offset(entry.getKey()));
                for (SingleBlockModifierReplacement replacement : entry.getValue()) {
                    boolean matched = replacement.getReplacement().matches(actual);
                    var registeredDefinition = ModifierRegistry.get(replacement.getModifierId());
                    List<RecipeModifier> modifiers = replacement.getModifiers();
                    if (modifiers.isEmpty() && registeredDefinition != null) {
                        modifiers = registeredDefinition.modifiers();
                    }
                    if (matched) {
                        nextModifiers.putIfAbsent(replacement.getModifierName(), modifiers);
                    }
                }
            }
        }
        return nextModifiers;
    }

    private static boolean allowsModifiers(@Nullable Machine machine) {
        if (machine == null) return false;
        MachineRegistration registration = MachineDefinitions.getRegistration(machine.registryName());
        return registration != null && registration.allowModifiers();
    }

    private void clearFoundModifiers() {
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        if (current.foundModifiers().isEmpty()) return;
        runtime.publishComponentState(runtime.components(), Map.of(), current.foundLevels(),
                current.linkedPortPositions());
    }

    private void updateComponents(StructureSnapshot previousStructure, Machine matchedMachine,
                                  BlockArray matchedPattern, @Nullable CompiledMachinePattern compiledPattern,
                                  Direction facing, Set<BlockPos> previousLinkedPortPositions,
                                  Map<String, List<RecipeModifier>> foundModifiers,
                                  Map<Identifier, MachineLevel> foundLevels) {
        List<FactorySchedulerBlockEntity> previousFactories = factoryComponents();
        for (FactorySchedulerBlockEntity factory : previousFactories) factory.bindOwner(null);
        unbindUpgradeBuses();
        List<DataStorageBlockEntity> previousDataStorages = dataStorageComponents(previousStructure);
        List<SmartInterfaceBlockEntity> previousSmartInterfaces = runtime.components().stream()
                .map(ProcessingComponent::getContainer)
                .filter(SmartInterfaceBlockEntity.class::isInstance)
                .map(SmartInterfaceBlockEntity.class::cast)
                .toList();
        Set<BlockPos> previousNetworkInterfacePositions = activeNetworkInterfacePositions;
        if (level == null || matchedMachine == null || matchedPattern == null) {
            unbindNetworkInterfaces(previousNetworkInterfacePositions);
            activeNetworkInterfacePositions = Set.of();
            new DataStorageBindingCoordinator().unbind(this, previousDataStorages);
            runtime.publishDataStorages(Map.of());
            runtime.publishUpgradeBusState(List.of());
            runtime.publishComponentState(List.of(), foundModifiers, foundLevels, Set.of());
            return;
        }

        unlinkLinkedPorts(previousStructure, previousLinkedPortPositions);
        List<DataStorageBlockEntity> dataStorages = dataStorageComponents(matchedPattern, compiledPattern, facing);
        DataStorageBindingCoordinator dataStorageCoordinator = new DataStorageBindingCoordinator();
        dataStorageCoordinator.unbindMissing(this, previousDataStorages, dataStorages);
        dataStorageCoordinator.reconcile(this, dataStorages);
        Map<BlockPos, DataStorage> boundDataStorages = new LinkedHashMap<>();
        for (DataStorageBlockEntity storage : dataStorages) {
            if (getBlockPos().equals(storage.controllerPosition().orElse(null))) {
                boundDataStorages.put(storage.getBlockPos().immutable(), storage.storage());
            }
        }
        runtime.publishDataStorages(boundDataStorages);
        List<UpgradeBusBlockEntity> upgradeBuses = upgradeBusComponents(matchedPattern);
        bindUpgradeBuses(upgradeBuses);
        runtime.publishUpgradeBusState(upgradeBusSnapshots(upgradeBuses));
        Identifier formedTexture = matchedMachine.appearance().formedPortBaseTexture();
        List<ProcessingComponent> nextComponents = new ArrayList<>();
        Set<BlockPos> nextLinkedPortPositions = new HashSet<>();
        nextLinkedPortPositions.addAll(boundDataStorages.keySet());

        List<SmartInterfaceBlockEntity> smartInterfaces = new ArrayList<>();
        for (BlockPos relativePos : componentPositions(matchedPattern, compiledPattern, facing)) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof SmartInterfaceBlockEntity smartInterface) {
                smartInterfaces.add(smartInterface);
            }
        }
        var registration = MachineDefinitions.effectiveSnapshot().get(matchedMachine.registryName());
        if (registration != null) {
            new SmartInterfaceBindingCoordinator(Map.of()).unbindAll(this, previousSmartInterfaces.stream()
                    .filter(smartInterface -> !smartInterfaces.contains(smartInterface))
                    .toList());
            new SmartInterfaceBindingCoordinator(registration.smartInterfaceTypes(), registration.shareSmartInterfaces())
                    .reconcile(this, smartInterfaces);
        }

        GlobalPos networkOwner = GlobalPos.of(level.dimension(), getBlockPos());
        CompiledMachinePattern networkInterfacePattern = compiledPattern == null
                ? MachinePatternCompiler.compile(matchedMachine)
                : compiledPattern;
        List<BlockPos> actualNetworkInterfacePositions = networkInterfacePattern.networkInterfacePositions(facing).stream()
                .map(relativePos -> getBlockPos().offset(relativePos))
                .filter(worldPos -> level.getBlockEntity(worldPos) instanceof NetworkInterfaceBlockEntity)
                .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ))
                .toList();
        Set<BlockPos> nextNetworkInterfacePositions = new HashSet<>();
        for (BlockPos worldPos : actualNetworkInterfacePositions.stream()
                .limit(matchedMachine.networkInterface().maxCount()).toList()) {
            if (level.getBlockEntity(worldPos) instanceof NetworkInterfaceBlockEntity networkInterface
                    && networkInterface.claimOwner(networkOwner)) {
                networkInterface.linkControllerAppearance(getBlockPos(), formedTexture);
                nextNetworkInterfacePositions.add(worldPos.immutable());
            }
        }
        unbindNetworkInterfaces(previousNetworkInterfacePositions.stream()
                .filter(position -> !nextNetworkInterfacePositions.contains(position))
                .collect(java.util.stream.Collectors.toSet()));
        activeNetworkInterfacePositions = Set.copyOf(nextNetworkInterfacePositions);

        for (BlockPos relativePos : componentPositions(matchedPattern, compiledPattern, facing)) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (level.getBlockEntity(worldPos) instanceof UpgradeBusBlockEntity bus) {
                bus.linkControllerAppearance(getBlockPos(), formedTexture);
                nextLinkedPortPositions.add(worldPos.immutable());
                continue;
            }
            if (level.getBlockEntity(worldPos) instanceof SmartInterfaceBlockEntity smartInterface) {
                if (smartInterface.hasController(getBlockPos())) {
                    nextLinkedPortPositions.add(worldPos.immutable());
                }
                nextComponents.add(new ProcessingComponent(null, smartInterface, worldPos, relativePos, matchedPattern.tagsAt(relativePos), null));
                continue;
            }
            if (level.getBlockEntity(worldPos) instanceof ParallelControllerBlockEntity parallel) {
                parallel.linkControllerAppearance(getBlockPos(), matchedMachine.appearance().formedPortBaseTexture());
                nextLinkedPortPositions.add(worldPos.immutable());
                nextComponents.add(new ProcessingComponent(null, parallel, worldPos, relativePos, matchedPattern.tagsAt(relativePos), null));
                continue;
            }
            if (level.getBlockEntity(worldPos) instanceof FactorySchedulerBlockEntity scheduler) {
                scheduler.bindOwner(this);
                scheduler.linkControllerAppearance(getBlockPos(), matchedMachine.appearance().formedPortBaseTexture());
                nextLinkedPortPositions.add(worldPos.immutable());
                nextComponents.add(new ProcessingComponent(null, scheduler, worldPos, relativePos, matchedPattern.tagsAt(relativePos), null));
                continue;
            }
            if (!(level.getBlockEntity(worldPos) instanceof MachineComponentTile tile)) continue;

            if (tile instanceof IOPortBlockEntity port) {
                port.linkControllerAppearance(getBlockPos(), formedTexture);
                nextLinkedPortPositions.add(worldPos.immutable());
            }
            var component = tile.provideComponent();
            if (!(tile instanceof BlockEntity container)) continue;
            nextComponents.add(new ProcessingComponent(component, container, worldPos, relativePos, matchedPattern.tagsAt(relativePos)));
        }
        runtime.publishComponentState(nextComponents, foundModifiers, foundLevels, nextLinkedPortPositions);
        invalidateFactoryCapacity();
    }

    private List<UpgradeBusBlockEntity> upgradeBusComponents(BlockArray pattern) {
        if (level == null || pattern == null) return List.of();
        List<UpgradeBusBlockEntity> buses = new ArrayList<>();
        for (BlockPos relativePos : MachinePatternCompiler.positionsExcludingNetworkInterfaces(pattern)) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof UpgradeBusBlockEntity bus) {
                buses.add(bus);
            }
        }
        return List.copyOf(buses);
    }

    private List<ComponentRuntime.UpgradeBusSnapshot> upgradeBusSnapshots(
            List<UpgradeBusBlockEntity> buses) {
        List<ComponentRuntime.UpgradeBusSnapshot> snapshots = new ArrayList<>();
        for (UpgradeBusBlockEntity bus : buses) {
            snapshots.add(new ComponentRuntime.UpgradeBusSnapshot(
                    bus.getBlockPos().subtract(getBlockPos()), bus.itemSnapshot()));
        }
        return List.copyOf(snapshots);
    }

    private void bindUpgradeBuses(List<UpgradeBusBlockEntity> buses) {
        for (UpgradeBusBlockEntity bus : buses) bus.addControllerChangeListener(upgradeBusChangeListener);
        boundUpgradeBuses = List.copyOf(buses);
    }

    private void unbindUpgradeBuses() {
        for (UpgradeBusBlockEntity bus : boundUpgradeBuses) bus.removeControllerChangeListener(upgradeBusChangeListener);
        boundUpgradeBuses = List.of();
    }

    private void onUpgradeBusContentsChanged() {
        if (level == null || level.isClientSide() || boundUpgradeBuses.isEmpty()) return;
        runtime.refreshUpgradeBusState(upgradeBusSnapshots(boundUpgradeBuses));
        setChanged();
        syncRuntimeStateIfChanged();
    }

    private boolean componentsNeedRefresh(BlockArray pattern, @Nullable CompiledMachinePattern compiled,
                                          Direction facing) {
        if (level == null) return false;
        Set<BlockPos> nextDataStoragePositions = new HashSet<>();
        Set<BlockPos> nextUpgradeBusPositions = new HashSet<>();
        List<BlockEntity> nextContainers = new ArrayList<>();
        for (BlockPos relativePos : MachinePatternCompiler.positionsExcludingNetworkInterfaces(pattern)) {
            BlockEntity entity = level.getBlockEntity(getBlockPos().offset(relativePos));
            if (entity instanceof DataStorageBlockEntity) nextDataStoragePositions.add(entity.getBlockPos().immutable());
            if (entity instanceof UpgradeBusBlockEntity) nextUpgradeBusPositions.add(relativePos.immutable());
            if (entity instanceof SmartInterfaceBlockEntity
                    || entity instanceof ParallelControllerBlockEntity
                    || entity instanceof FactorySchedulerBlockEntity
                    || entity instanceof MachineComponentTile) {
                nextContainers.add(entity);
            }
        }
        List<BlockEntity> currentContainers = runtime.components().stream()
                .map(ProcessingComponent::getContainer)
                .toList();
        return !currentContainers.equals(nextContainers)
                || !runtime.dataStoragePositions().equals(nextDataStoragePositions)
                || !runtime.upgradeBusPositions().equals(nextUpgradeBusPositions);
    }

    private List<DataStorageBlockEntity> dataStorageComponents(StructureSnapshot structure) {
        if (level == null || structure.pattern() == null || structure.facing() == null) return List.of();
        return dataStorageComponents(structure.pattern(), structure.compiledPattern(), structure.facing());
    }

    private List<DataStorageBlockEntity> dataStorageComponents(BlockArray pattern,
                                                               @Nullable CompiledMachinePattern compiled,
                                                               Direction facing) {
        if (level == null || pattern == null || facing == null) return List.of();
        List<DataStorageBlockEntity> storages = new ArrayList<>();
        List<BlockPos> positions = componentPositions(pattern, compiled, facing);
        for (BlockPos relativePos : positions) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof DataStorageBlockEntity storage) {
                storages.add(storage);
            }
        }
        return storages;
    }

    private void unlinkLinkedPorts() {
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        Set<BlockPos> linkedPortPositions = current.linkedPortPositions();
        if (level == null) {
            runtime.publishComponentState(runtime.components(), current.foundModifiers(), current.foundLevels(), Set.of());
            return;
        }
        if (runtimeSnapshot().structure().pattern() != null) {
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
        runtime.publishComponentState(runtime.components(), current.foundModifiers(), current.foundLevels(), Set.of());
    }

    private void unlinkLinkedPorts(StructureSnapshot previousStructure, Set<BlockPos> linkedPortPositions) {
        if (level == null) return;
        if (previousStructure.pattern() != null && previousStructure.facing() != null) {
            for (BlockPos relativePos : componentPositions(previousStructure)) {
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
    }

    private List<BlockPos> componentPositions() {
        StructureSnapshot structure = runtimeSnapshot().structure();
        if (hasCompiledFacing(structure.compiledPattern(), structure.facing())) {
            return structure.compiledPattern().componentPositions(structure.facing());
        }
        return MachinePatternCompiler.positionsExcludingNetworkInterfaces(structure.pattern());
    }

    private static List<BlockPos> componentPositions(StructureSnapshot structure) {
        return componentPositions(structure.pattern(), structure.compiledPattern(), structure.facing());
    }

    private static List<BlockPos> componentPositions(BlockArray pattern, @Nullable CompiledMachinePattern compiled, Direction facing) {
        return hasCompiledFacing(compiled, facing)
                ? compiled.componentPositions(facing)
                : MachinePatternCompiler.positionsExcludingNetworkInterfaces(pattern);
    }

    private PortRequirementSpec.PortCounts countPorts(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (level == null || rotatedPattern == null) return PortRequirementSpec.PortCounts.empty();

        List<BlockPos> positions = hasCompiledFacing(compiledPattern, facing)
                ? compiledPattern.portPositions(facing)
                : MachinePatternCompiler.positionsExcludingNetworkInterfaces(rotatedPattern);
        for (BlockPos relativePos : positions) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (!(level.getBlockEntity(worldPos) instanceof IOPortBlockEntity port)) continue;
            IOPortKind kind = port.kind();
            counts.merge(kind.id(), 1, Integer::sum);
            for (PortFamilyDescriptor family : kind.families()) {
                for (String alias : family.countAliases()) {
                    if (!alias.equals(kind.id())) counts.merge(alias, 1, Integer::sum);
                }
            }
        }
        return PortRequirementSpec.PortCounts.of(counts);
    }

    private List<IOPortKind> portKinds(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        if (level == null || rotatedPattern == null) return List.of();

        List<BlockPos> positions = hasCompiledFacing(compiledPattern, facing)
                ? compiledPattern.portPositions(facing)
                : MachinePatternCompiler.positionsExcludingNetworkInterfaces(rotatedPattern);
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
                        OptionalInt.empty(),
                        PortRequirementSpec.FailureReason.MISSING));
    }

    private java.util.Optional<PortRequirementSpec.Failure> validateFactoryControllerCount(
            Machine candidate, BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        if (candidate.behavior() instanceof TickBehavior) return java.util.Optional.empty();
        int count = countFactoryControllers(rotatedPattern, compiledPattern, facing);
        if (count == 0 || candidate.hasFactory()) return java.util.Optional.empty();
        return java.util.Optional.of(new PortRequirementSpec.Failure(
                "factory_controller",
                count,
                0,
                OptionalInt.of(0),
                PortRequirementSpec.FailureReason.TOO_MANY));
    }

    private int countFactoryControllers(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        if (level == null || rotatedPattern == null) return 0;

        List<BlockPos> positions = componentPositions(rotatedPattern, compiledPattern, facing);
        int count = 0;
        for (BlockPos relativePos : positions) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof FactorySchedulerBlockEntity && ++count > 1) {
                return count;
            }
        }
        return count;
    }

    private boolean isInsideCompiledBounds(BlockPos worldPos) {
        StructureSnapshot structure = runtimeSnapshot().structure();
        if (structure.facing() == null) return false;
        Machine candidate = structure.machine() == null ? structure.configuredMachine() : structure.machine();
        BlockPos relative = worldPos.subtract(getBlockPos());
        if (candidate != null) {
            for (CandidatePattern pattern : candidatePatterns(candidate, structure.facing())) {
                if (contains(boundingBox(pattern.pattern()), relative)) return true;
            }
        }
        BoundingBox box = hasCompiledFacing(structure.compiledPattern(), structure.facing())
                ? structure.compiledPattern().boundingBox(structure.facing())
                : boundingBox(structure.pattern());
        return contains(box, relative);
    }

    private boolean isInsideComponentPositions(BlockPos worldPos, StructureSnapshot structure) {
        if (structure.pattern() == null || structure.facing() == null) return false;
        BlockPos relative = worldPos.subtract(getBlockPos());
        return componentPositions(structure).contains(relative);
    }

    private static boolean contains(BoundingBox box, BlockPos relative) {
        return relative.getX() >= box.minX()
                && relative.getX() <= box.maxX()
                && relative.getY() >= box.minY()
                && relative.getY() <= box.maxY()
                && relative.getZ() >= box.minZ()
                && relative.getZ() <= box.maxZ();
    }

    private void onStructureChunkStateChanged() {
        onStructureChunkStateChanged(null);
    }

    private void onStructureChunkStateChanged(@Nullable ChunkPos unloadedChunk) {
        runtime.markStructureChunkStateChanged();
        boolean scanActive = structureWorkSnapshot().scan() != null;
        StructureSnapshot structure = currentRuntimeSnapshot().structure();
        if (scanActive) invalidateStructureScan(StructureMatcher.InvalidationReason.UNLOADED);
        boolean wasLoaded = structure.structureAreaLoaded();
        ChunkPos controllerChunk = new ChunkPos(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4);
        boolean loaded = structure.formed() && unloadedChunk != null && !controllerChunk.equals(unloadedChunk)
                ? false : isStructureAreaLoaded(structure);
        if (!structure.formed() || structure.pattern() == null || structure.facing() == null) {
            runtime.publishStructureState(loaded, structure.formed(), structure.configuredMachine(), structure.matchedStage());
            publishRuntimeState();
            return;
        }
        if (wasLoaded == loaded) {
            if (scanActive) runtime.requestStructureCheck(StructureRuntime.CheckReason.DIRTY_EVENT);
            publishRuntimeState();
            return;
        }
        runtime.publishStructureState(loaded, structure.formed(), structure.configuredMachine(), structure.matchedStage());
        runtime.requestStructureCheck(StructureRuntime.CheckReason.DIRTY_EVENT);
        if (level instanceof ServerLevel serverLevel) ModuleConnectionCoordinator.enqueueCouplers(serverLevel, this);
        if (!loaded) pauseActiveForUnloadedStructure();
        setChanged();
        syncLevelState();
        publishRuntimeState();
    }

    private void syncLevelState() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    void syncRuntimeStateIfChanged() {
        if (getBlockState() == null) return;
        if (!hasActiveOperation()) runtime.clearOperationText();
        runtime.publishSnapshot();
        boolean next = isRuntimeActive();
        if (next == syncedRuntimeActive) return;
        syncedRuntimeActive = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    void onDataStorageChanged(DataStorage storage) {
        if (level == null || level.isClientSide()) return;
        runtime.onDataStorageChanged(storage);
        if (runtime.updateBatchActive()) {
            runtimeStateBroadcastPending = true;
        } else {
            broadcastStateIfChanged();
        }
    }

    private void refreshCriticalStructureChunks(BlockArray pattern, @Nullable CompiledMachinePattern compiled,
                                                Direction facing) {
        Set<ChunkPos> criticalChunks = new HashSet<>();
        criticalChunks.add(new ChunkPos(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4));
        if (level == null || pattern == null || facing == null) {
            runtime.publishCriticalStructureChunks(criticalChunks);
            return;
        }
        BoundingBox box = boundingBox(pattern);
        int minChunkX = (getBlockPos().getX() + box.minX()) >> 4;
        int maxChunkX = (getBlockPos().getX() + box.maxX()) >> 4;
        int minChunkZ = (getBlockPos().getZ() + box.minZ()) >> 4;
        int maxChunkZ = (getBlockPos().getZ() + box.maxZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                criticalChunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        runtime.publishCriticalStructureChunks(criticalChunks);
    }

    private boolean isStructureAreaLoaded(StructureSnapshot structure) {
        if (level == null) return true;
        ChunkPos controllerChunk = new ChunkPos(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4);
        for (ChunkPos chunkPos : structure.criticalChunks()) {
            if (chunkPos.equals(controllerChunk)) continue;
            if (!level.hasChunk(chunkPos.x(), chunkPos.z())) return false;
        }
        return true;
    }

    private static boolean hasCompiledFacing(@Nullable CompiledMachinePattern compiled, @Nullable Direction facing) {
        return compiled != null && facing != null && compiled.rotatedPattern(facing) != null && compiled.boundingBox(facing) != null;
    }

    private static BoundingBox boundingBox(BlockArray pattern) {
        if (pattern == null || pattern.isEmpty()) return new BoundingBox(0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : pattern.pattern().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void pauseActiveForUnloadedStructure() {
        stopFactoryController();
        if (!runtime.craftingRuntime().active()) {
            syncRuntimeStateIfChanged();
            return;
        }
        setActiveState(false);
        syncRuntimeStateIfChanged();
    }

    private void resumePausedRecipeAfterStructureCheck() {
        if (!runtime.craftingRuntime().active() || redstonePaused) return;
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
        for (ProcessingComponent processingComponent : runtime.components()) {
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

    private record CandidatePattern(CompiledMachinePattern compiled, BlockArray pattern, Direction rollFacing) {
        int stageNumber() { return compiled == null ? 1 : compiled.stageNumber(); }
    }

    private void resetMachine() {
        resetMachine(true);
    }

    private void resetMachine(boolean clearFormationFailure) {
        resetMachine(clearFormationFailure, true, true);
    }

    private void resetMachine(boolean clearFormationFailure, boolean updateBlockState) {
        resetMachine(clearFormationFailure, updateBlockState, true);
    }

    private void resetMachine(boolean clearFormationFailure, boolean updateBlockState, boolean invalidateScheduledCheck) {
        StructureSnapshot structure = runtimeSnapshot().structure();
        StructureWorkSnapshot work = structureWorkSnapshot();
        invalidateStructureScan(StructureMatcher.InvalidationReason.VERSION);
        ACTIVE_STRUCTURE_SCANS.remove(this);
        Machine configuredMachine = structure.configuredMachine();
        PortRequirementSpec.Failure previousFormationFailure = structure.lastFormationFailure();
        Object previousStructureError = structure.lastStructureError();
        boolean wasFormed = structure.formed() || physicalFormed();
        if (wasFormed && level instanceof ServerLevel serverLevel) {
            ModuleConnectionCoordinator.clearConnectionsFor(serverLevel, this);
            NetworkInterfaceBindingCoordinator.clearConnectionsFor(serverLevel.getServer(), this);
        }
        boolean hadActive = runtime.craftingRuntime().active();
        unbindNetworkInterfaces(activeNetworkInterfacePositions);
        activeNetworkInterfacePositions = Set.of();
        releaseStructureClaims();
        unbindSmartInterfaces();
        unbindDataStorages();
        unlinkLinkedPorts();
        stopFactoryController();
        for (FactorySchedulerBlockEntity factory : factoryComponents()) factory.bindOwner(null);
        runtime.clearAllText();
        unbindUpgradeBuses();
        cachedMachineReference = null;
        cachedMachineReferenceStructureVersion = Long.MIN_VALUE;
        runtime.resetStructure(configuredMachine, wasFormed || hadActive);
        if (!clearFormationFailure) {
            publishStructureWork(state -> state.withFormationFailure(previousFormationFailure)
                    .withLastStructureError(previousStructureError));
        }
        if (!wasFormed || !invalidateScheduledCheck) {
            publishStructureWork(state -> state.withNextCheckTick(work.nextCheckTick()));
        }
        FORMED_CONTROLLERS.remove(this);
        runtime.publishUpgradeBusState(List.of());
        runtime.publishComponentState(List.of(), Map.of(), Map.of(), Set.of());
        if (wasFormed && invalidateScheduledCheck) publishStructureWork(state -> state.withNextCheckTick(-1L));
        runtime.craftingRuntime().invalidate();
        setActiveState(false);
        clearPendingSharedStart();
        clearSharedTickPending();
        clearPendingConflictStart();
        lastFailureUnloc = null;
        redstonePaused = false;
        clearCandidateCache();
        if (wasFormed && updateBlockState) updatePhysicalFormedState(false);
        setChanged();
        syncRuntimeStateIfChanged();
        publishRuntimeState();
        syncOpenControllerScreenText();
    }

    private void unbindSmartInterfaces() {
        if (level == null || runtimeSnapshot().structure().pattern() == null) return;
        List<SmartInterfaceBlockEntity> smartInterfaces = new ArrayList<>();
        for (BlockPos relativePos : componentPositions()) {
            if (level.getBlockEntity(getBlockPos().offset(relativePos)) instanceof SmartInterfaceBlockEntity smartInterface) {
                smartInterfaces.add(smartInterface);
            }
        }
        new SmartInterfaceBindingCoordinator(Map.of()).unbindAll(this, smartInterfaces);
    }

    private void unbindNetworkInterfaces(Set<BlockPos> positions) {
        if (level == null || positions.isEmpty()) return;
        GlobalPos owner = GlobalPos.of(level.dimension(), getBlockPos());
        for (BlockPos position : positions) {
            if (level.getBlockEntity(position) instanceof NetworkInterfaceBlockEntity networkInterface) {
                networkInterface.releaseOwner(owner);
            }
        }
    }

    private void unbindDataStorages() {
        DataStorageBindingCoordinator coordinator = new DataStorageBindingCoordinator();
        coordinator.unbind(this, dataStorageComponents(runtimeSnapshot().structure()));
        runtime.publishDataStorages(Map.of());
    }

    public void releaseStructureClaims() {
        if (level instanceof ServerLevel serverLevel) {
            StructureClaimRegistry.get(serverLevel).release(getBlockPos());
        }
    }

    private void stopFactoryController() {
        runtime.factoryRuntime().clear();
    }

    private void syncOpenFactoryControllerMenus() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (runtime.updateBatchActive()) {
            factoryMenuSyncPending = true;
            return;
        }
        runtime.publishSnapshot();
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        if (!SYNC_RUNTIME.factoryControllerPresent(runtimeState)) return;
        FactorySnapshot next = SYNC_RUNTIME.factoryState(runtimeState);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof FactoryControllerMenu menu
                    && menu.controllerPos().equals(getBlockPos())) {
                menu.applySnapshot(next);
                menu.markSnapshotSent(next);
                player.connection.send(new ClientboundCustomPayloadPacket(
                        new PktFactoryControllerStatePayload(getBlockPos(), next)));
            }
        }
    }

    private void syncOpenControllerScreenText() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        ControllerScreenTextSnapshot snapshot = runtime.screenText().snapshot();
        Map<String, ControllerScreenTextSnapshot> laneSnapshots = runtime.factoryRuntime().screenTextSnapshots();
        lastSentRecipeScreenTextRevisions.keySet().retainAll(laneSnapshots.keySet());
        boolean globalChanged = snapshot.revision() != lastSentControllerScreenTextRevision;
        Map<String, ControllerScreenTextSnapshot> changedLanes = new LinkedHashMap<>();
        for (Map.Entry<String, ControllerScreenTextSnapshot> entry : laneSnapshots.entrySet()) {
            removedRecipeScreenTextRevisions.remove(entry.getKey());
            if (!Objects.equals(lastSentRecipeScreenTextRevisions.get(entry.getKey()), entry.getValue().revision())) {
                changedLanes.put(entry.getKey(), entry.getValue());
            }
        }
        if (!globalChanged && changedLanes.isEmpty() && removedRecipeScreenTextRevisions.isEmpty()) return;
        PktControllerScreenTextPayload globalPacket = new PktControllerScreenTextPayload(
                getBlockPos(), snapshot.revision(), snapshot.lines());
        Map<String, PktControllerScreenTextPayload> lanePackets = new LinkedHashMap<>();
        for (Map.Entry<String, ControllerScreenTextSnapshot> entry : changedLanes.entrySet()) {
            ControllerScreenTextSnapshot laneSnapshot = entry.getValue();
            lanePackets.put(entry.getKey(), new PktControllerScreenTextPayload(
                    getBlockPos(), entry.getKey(), laneSnapshot.revision(), laneSnapshot.lines()));
        }
        boolean globalSent = false;
        Set<String> sentLanes = new HashSet<>();
        for (ServerPlayer player : serverLevel.players()) {
            boolean ordinaryMenu = player.containerMenu instanceof MachineControllerMenu menu
                    && menu.controllerPos().equals(getBlockPos());
            boolean factoryMenu = player.containerMenu instanceof FactoryControllerMenu factory
                    && factory.controllerPos().equals(getBlockPos());
            if (!ordinaryMenu && !factoryMenu) continue;
            if (globalChanged) {
                player.connection.send(new ClientboundCustomPayloadPacket(globalPacket));
                globalSent = true;
            }
            if (factoryMenu) {
                for (Map.Entry<String, PktControllerScreenTextPayload> entry : lanePackets.entrySet()) {
                    player.connection.send(new ClientboundCustomPayloadPacket(entry.getValue()));
                    sentLanes.add(entry.getKey());
                }
                for (Map.Entry<String, Long> entry : removedRecipeScreenTextRevisions.entrySet()) {
                    player.connection.send(new ClientboundCustomPayloadPacket(new PktControllerScreenTextPayload(
                            getBlockPos(), entry.getKey(), entry.getValue(), List.of())));
                    sentLanes.add(entry.getKey());
                }
            }
        }
        if (globalSent) lastSentControllerScreenTextRevision = snapshot.revision();
        for (String laneId : sentLanes) {
            ControllerScreenTextSnapshot changed = changedLanes.get(laneId);
            if (changed != null) lastSentRecipeScreenTextRevisions.put(laneId, changed.revision());
            else lastSentRecipeScreenTextRevisions.put(laneId, removedRecipeScreenTextRevisions.get(laneId));
        }
        sentLanes.forEach(removedRecipeScreenTextRevisions::remove);
    }

    private void broadcastStateIfChanged() {
        PktMachineStatePayload packet = PktMachineStatePayload.from(getBlockPos(), runtimeSnapshot());
        if (lastBroadcastState != null && !PktMachineStatePayload.stateChanged(packet, lastBroadcastState)) {
            return;
        }
        lastBroadcastState = packet;
        if (!(level instanceof ServerLevel sl)) return;
        for (var player : sl.getPlayers(p -> p.distanceToSqr(getBlockPos().getCenter()) < 64 * 64
                || (p.containerMenu instanceof MachineControllerMenu menu
                && menu.controllerPos().equals(getBlockPos())))) {
            ((ServerPlayer) player).connection.send(new ClientboundCustomPayloadPacket(packet));
        }
    }

    private boolean tryStartNewRecipe() {
        if (!shouldSearchRecipe()) return false;
        recipeSearchAttemptCounter++;
        ControllerRuntimeSnapshot current = currentRuntimeSnapshot();
        Machine matchedMachine = current.structure().machine();
        Identifier machineId = matchedMachine == null ? null : matchedMachine.registryName();
        if (machineId == null) return false;
        List<MachineRecipe> candidates = recipesForMachine();
        long maxParallelism = getMaxParallelism();
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(current, machineId, current.structure().version(),
                    maxParallelism, candidates, lockedRecipeId, componentRuntime().capabilities(),
                    componentRuntime().modifierList()).compute();
        } catch (RuntimeException e) {
            LOG.warn("[Ctrl#{}] tryStartNewRecipe: recipe search failed at pos={}; retrying later", instanceId, getBlockPos(), e);
            clearPendingConflictStart();
            recipeSearchRetryCounter++;
            lastFailureUnloc = "gui.mmcr.controller.failure.recipe_search_exception";
            return false;
        }
        if (result.success()) {
            return applySearchResult(result);
        }
        clearPendingConflictStart();
        recipeSearchRetryCounter++;
        runtime.craftingRuntime().recordSearchFailure(result.failure());
        lastFailureUnloc = result.failureUnloc();
        if (result.levelFailure() != null) {
            lastFailureUnloc = "gui.mmcr.controller.failure.level_insufficient";
        }
        return false;
    }

    private boolean shouldSearchRecipe() {
        long contentVersion = RuntimeContentVersion.current();
        if (lastRecipeSearchRegistryVersion != contentVersion) {
            lastRecipeSearchRegistryVersion = contentVersion;
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
        runtime.componentRuntime().markCapabilityPresentationChanged();
    }

    private boolean applySearchResult(RecipeSearchResult result) {
        if (!isSearchResultCurrent(result)) {
            return false;
        }
        MachineRecipe next = result.recipe();
        if (shouldDelayConflictProneStart(result)) {
            return false;
        }
        if (usesSharedIoCoordinator()) {
            requestSharedStart(next);
            return true;
        }
        CraftingStatus state = runtime.craftingRuntime().start(next, getMaxParallelism());
        if (!state.isCrafting()) {
            clearPendingConflictStart();
            recipeSearchRetryCounter++;
            lastFailureUnloc = runtime.craftingRuntime().failureUnloc();
            return false;
        }
        setActiveState(true);
        syncRuntimeStateIfChanged();
        recipeSearchRetryCounter = 0;
        lastFailureUnloc = null;
        setChanged();
        return true;
    }

    public boolean shouldDelayConflictProneStart(RecipeSearchResult result) {
        return recipeStartDelay().shouldDelay(result.recipe().id(), result.hasMoreSpecificPendingInputCandidate(), currentGameTime());
    }

    public void clearPendingConflictStart() {
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
        ControllerRuntimeSnapshot runtimeState = currentRuntimeSnapshot();
        Machine matchedMachine = runtimeState.structure().machine();
        return runtimeState.structure().formed()
                && matchedMachine != null
                && matchedMachine.registryName().equals(result.machineId())
                && runtimeState.structure().version() == result.structureVersion()
                && runtimeState.capabilityVersion() == result.capabilityVersion()
                && runtimeState.modifierVersion() == result.modifierVersion()
                && !runtime.craftingRuntime().active();
    }

    private boolean tickActiveRecipe() {
        if (!runtime.craftingRuntime().active()) return false;
        if (usesSharedIoCoordinator()) {
            tickSharedRecipe();
            return false;
        }
        boolean wasActive = runtime.craftingRuntime().active();
        runtime.craftingRuntime().tick();
        if (runtime.craftingRuntime().finishPending()) runtime.craftingRuntime().finish();
        lastFailureUnloc = runtime.craftingRuntime().failureUnloc();
        if (wasActive && !runtime.craftingRuntime().active()) {
            boolean finished = runtime.craftingRuntime().failure() == null;
            lastFailureUnloc = finished ? null : runtime.craftingRuntime().failureUnloc();
            if (finished) {
                playFinishSound();
            }
            setActiveState(false);
            syncRuntimeStateIfChanged();
        }
        setChanged();
        return wasActive && !runtime.craftingRuntime().active() && runtime.craftingRuntime().failure() == null;
    }

    private boolean usesSharedIoCoordinator() {
        StructureClaimRegistry.ResourceDomain domain = resourceDomain();
        return level instanceof ServerLevel && domain != null && domain.controllers().size() > 1;
    }

    private void requestSharedStart(MachineRecipe next) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        StructureClaimRegistry.ResourceDomain domain = resourceDomain();
        if (domain == null) return;
        ControllerRuntimeSnapshot snapshot = currentRuntimeSnapshot();
        sharedStartPending = true;
        pendingSharedStartRecipe = next;
        pendingSharedStartDomain = domain;
        pendingSharedStartStructureVersion = snapshot.structure().version();
        pendingSharedStartCapabilityVersion = snapshot.capabilityVersion();
        pendingSharedStartModifierVersion = snapshot.modifierVersion();
        pendingSharedStartComponentStateVersion = snapshot.stateVersion();
        pendingSharedStartCatalogVersion = currentCatalogVersion();
        long token = ++nextSharedStartToken;
        pendingSharedStartToken = token;
        long runtimeStructureVersion = snapshot.structure().version();
        SharedIoCoordinator.get(serverLevel).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), runtimeStructureVersion,
                snapshot.stateVersion(),
                getMaxParallelism(),
                requested -> {
                    if (!isPendingSharedStart(token, next, domain) || runtime.craftingRuntime().active()) return 0L;
                    CraftingStatus state = runtime.craftingRuntime().start(next, requested);
                    if (!state.isCrafting()) {
                        clearPendingSharedStart();
                        recipeSearchRetryCounter++;
                        syncCraftingFailure();
                        syncRuntimeStateIfChanged();
                        return 0L;
                    }
                    return runtime.craftingRuntime().parallelism();
                },
                granted -> {
                    if (!isPendingSharedStart(token, next, domain) || !runtime.craftingRuntime().active()) return;
                    clearPendingSharedStart();
                    setActiveState(true);
                    syncRuntimeStateIfChanged();
                    recipeSearchRetryCounter = 0;
                    syncCraftingFailure();
                    setChanged();
                },
                  () -> isPendingSharedStart(token, next, domain),
                  () -> runtimeSnapshot().structure().version(),
                  () -> runtimeSnapshot().stateVersion(),
                  pendingSharedStartCatalogVersion,
                  this::currentCatalogVersion
         ));
    }

    private boolean isPendingSharedStart(long token, MachineRecipe next,
                                         StructureClaimRegistry.ResourceDomain domain) {
        if (!sharedStartPending || pendingSharedStartToken != token || pendingSharedStartRecipe != next
                || pendingSharedStartDomain == null || !pendingSharedStartDomain.equals(domain)) return false;
        if (redstonePaused) {
            invalidatePendingSharedStart();
            return false;
        }
        if (!isCurrentSharedDomain(domain)) {
            invalidatePendingSharedStart();
            return false;
        }
        ControllerRuntimeSnapshot snapshot = runtimeSnapshot();
        if (snapshot.structure().version() != pendingSharedStartStructureVersion
                || snapshot.capabilityVersion() != pendingSharedStartCapabilityVersion
                || snapshot.modifierVersion() != pendingSharedStartModifierVersion
                || snapshot.stateVersion() != pendingSharedStartComponentStateVersion) {
            invalidatePendingSharedStart();
            recipeSearchRetryCounter++;
            return false;
        }
        if (currentCatalogVersion() != pendingSharedStartCatalogVersion) {
            invalidatePendingSharedStart();
            recipeSearchRetryCounter = 0;
            return false;
        }
        return true;
    }

    private void invalidatePendingSharedStart() {
        clearPendingSharedStart();
        runtime.craftingRuntime().invalidate();
        syncCraftingFailure();
        syncRuntimeStateIfChanged();
    }

    private void clearPendingSharedStart() {
        sharedStartPending = false;
        pendingSharedStartRecipe = null;
        pendingSharedStartDomain = null;
        pendingSharedStartStructureVersion = Long.MIN_VALUE;
        pendingSharedStartCapabilityVersion = Long.MIN_VALUE;
        pendingSharedStartModifierVersion = Long.MIN_VALUE;
        pendingSharedStartComponentStateVersion = Long.MIN_VALUE;
        pendingSharedStartCatalogVersion = Long.MIN_VALUE;
        pendingSharedStartToken = 0L;
    }

    private void tickSharedRecipe() {
        if (!(level instanceof ServerLevel serverLevel) || !runtime.craftingRuntime().active()) return;
        StructureClaimRegistry.ResourceDomain domain = resourceDomain();
        if (domain == null) return;
        if (sharedTickPending && !validateSharedRuntime(pendingSharedTickToken, pendingSharedTickDomain)) {
            clearSharedTickPending();
        }
        if (sharedTickPending) return;
        if (runtime.craftingRuntime().finishPending()) {
            if (!runtime.craftingRuntime().shouldRetryFinish()) return;
            sharedTickPending = true;
            pendingSharedTickDomain = domain;
            pendingSharedTickToken = ++nextSharedTickToken;
            requestSharedFinish(serverLevel, domain, pendingSharedTickToken);
            return;
        }
        sharedTickPending = true;
        pendingSharedTickDomain = domain;
        long token = ++nextSharedTickToken;
        pendingSharedTickToken = token;
        ControllerRuntimeSnapshot snapshot = runtimeSnapshot();
        long runtimeStructureVersion = snapshot.structure().version();
        SharedIoCoordinator.get(serverLevel).enqueue(new SharedIoCoordinator.TickRequest(
                domain, new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), runtimeStructureVersion,
                snapshot.stateVersion(),
                () -> {
                    if (!validateSharedRuntime(token, domain)) return false;
                    boolean wasActive = runtime.craftingRuntime().active();
                    runtime.craftingRuntime().tick();
                    if (runtime.craftingRuntime().finishPending()) {
                        if (runtime.craftingRuntime().shouldRetryFinish()) {
                            requestSharedFinish(serverLevel, domain, token);
                        } else {
                            clearSharedTickPending();
                        }
                        return true;
                    }
                    completeSharedRuntime(wasActive);
                    return true;
                 },
                 () -> validateSharedRuntime(token, domain),
                 () -> runtimeSnapshot().structure().version(),
                 () -> runtimeSnapshot().stateVersion()
         ));
    }

    private void requestSharedFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token) {
        ControllerRuntimeSnapshot snapshot = runtimeSnapshot();
        long runtimeStructureVersion = snapshot.structure().version();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.FinishRequest(
                domain, new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), runtimeStructureVersion,
                snapshot.stateVersion(),
                () -> {
                    if (!validateSharedRuntime(token, domain)) return false;
                    boolean wasActive = runtime.craftingRuntime().active();
                    runtime.craftingRuntime().finish();
                    completeSharedRuntime(wasActive);
                    return true;
                 },
                 () -> validateSharedRuntime(token, domain),
                 () -> runtimeSnapshot().structure().version(),
                 () -> runtimeSnapshot().stateVersion(),
                 () -> notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, null)
         ));
    }

    private boolean isCurrentSharedRuntime(StructureClaimRegistry.ResourceDomain domain) {
        return !redstonePaused && runtime.craftingRuntime().active() && runtime.craftingRuntime().versionsCurrent()
                && isCurrentSharedDomain(domain);
    }

    private boolean validateSharedRuntime(long token, @Nullable StructureClaimRegistry.ResourceDomain domain) {
        if (!sharedTickPending || pendingSharedTickToken != token) return false;
        if (redstonePaused) {
            clearSharedTickPending();
            return false;
        }
        if (isCurrentSharedRuntime(domain)) return true;
        if (pendingSharedTickDomain == null || pendingSharedTickDomain.equals(domain)) {
            if (runtime.craftingRuntime().active()) {
                if (runtime.craftingRuntime().versionsCurrent()) {
                    runtime.craftingRuntime().invalidate();
                    syncCraftingFailure();
                } else {
                    runtime.craftingRuntime().tick();
                    syncCraftingFailure();
                }
            }
            clearSharedTickPending();
            setActiveState(false);
            syncRuntimeStateIfChanged();
        }
        return false;
    }

    private void clearSharedTickPending() {
        sharedTickPending = false;
        pendingSharedTickDomain = null;
        pendingSharedTickToken = 0L;
    }

    private void completeSharedRuntime(boolean wasActive) {
        clearSharedTickPending();
        syncCraftingFailure();
        if (wasActive && !runtime.craftingRuntime().active()) {
            boolean finished = runtime.craftingRuntime().failure() == null;
            lastFailureUnloc = finished ? null : runtime.craftingRuntime().failureUnloc();
            if (finished) playFinishSound();
            setActiveState(false);
        }
        syncRuntimeStateIfChanged();
        setChanged();
    }

    void playFinishSound() {
        Machine matchedMachine = runtimeSnapshot().structure().machine();
        if (!(level instanceof ServerLevel serverLevel) || matchedMachine == null) return;
        MachineRegistration registration = MachineDefinitions.effectiveSnapshot().get(matchedMachine.registryName());
        SoundEvent sound = registration == null ? null : MachineSoundRegistry.get(registration.finishSoundId());
        if (sound != null) {
            serverLevel.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private boolean isCurrentSharedDomain(@Nullable StructureClaimRegistry.ResourceDomain domain) {
        return domain != null && domain.equals(resourceDomain());
    }

    private long currentCatalogVersion() {
        ControllerRuntimeSnapshot snapshot = runtimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        return RecipeRegistry.catalog(machine == null ? null : machine.registryName()).version();
    }

    private void setActiveState(boolean activeState) {
        if (level == null || level.isClientSide() || isRemoved()) return;
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != activeState) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, activeState), 3);
        }
    }

    @Override
    public void setRemoved() {
        invalidateStructureScan(StructureMatcher.InvalidationReason.REMOVED);
        ACTIVE_STRUCTURE_SCANS.remove(this);
        if (level != null && !level.isClientSide()) cancelBuildTask();
        structureCheckIntervalOverrideForTesting = null;
        structureScanBatchesOverrideForTesting = null;
        buildBlocksPerTickOverrideForTesting = null;
        super.setRemoved();
        if (level != null && !level.isClientSide() && !chunkUnloaded) resetMachine(true, false);
    }

    @Override
    public void onChunkUnloaded() {
        chunkUnloaded = true;
    }

    void bindDefaultMachine() {
        bindDefaultMachine(machineIdFromState(getBlockState()));
    }

    void bindDefaultMachine(Identifier machineId) {
        Machine resolved = MachineRegistry.effectiveSnapshot().get(machineId);
        if (resolved == null) {
            for (Machine candidate : MachineRegistry.effectiveSnapshot().values()) {
                if (candidate.controller().id().equals(machineId)) {
                    resolved = candidate;
                    break;
                }
            }
        }
        setMachine(resolved);
    }

    private List<MachineRecipe> recipesForMachine() {
        Machine configuredMachine = currentRuntimeSnapshot().structure().configuredMachine();
        Identifier machineId = configuredMachine == null ? null : configuredMachine.registryName();
        if (machineId == null) return List.of();
        MachineRecipeCatalog catalog = RecipeRegistry.catalog(machineId);
        if (machineId.equals(cachedCandidatesMachineId)
                && cachedCandidatesCatalogVersion == catalog.version()) {
            return cachedCandidates;
        }
        cachedCandidatesMachineId = machineId;
        cachedCandidatesCatalogVersion = catalog.version();
        cachedCandidates = catalog.recipes();
        return cachedCandidates;
    }

    private void clearCandidateCache() {
        cachedCandidatesMachineId = null;
        cachedCandidatesCatalogVersion = Long.MIN_VALUE;
        cachedCandidates = List.of();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        ensureFactoryRuntimeLoaded();
        super.saveAdditional(output);
        output.putInt("matched_structure_stage", runtimeSnapshot().structure().matchedStage());
        output.putLong("structure_runtime_version", runtimeSnapshot().structure().version());
        ValueOutput.TypedOutputList<String> levels = output.list("found_levels", Codec.STRING);
        for (MachineLevel foundLevel : runtimeSnapshot().foundLevels().values()) {
            levels.add(foundLevel.id().toString());
        }
        runtime.craftingRuntime().save(output.child("crafting_runtime"));
        if (lockedRecipeId != null) output.putString("locked_recipe", lockedRecipeId.toString());
        if (hasFactoryController() || runtime.factoryRuntime().laneCount() > 0) {
            runtime.factoryRuntime().save(output.child("factory_runtime"));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            ValueInput factoryRuntimeInput = input.childOrEmpty("factory_runtime");
            StructureSnapshot currentStructure = runtimeSnapshot().structure();
            runtime.publishStructureState(currentStructure.structureAreaLoaded(), currentStructure.formed(),
                    currentStructure.configuredMachine(), Math.max(0, input.getIntOr("matched_structure_stage", 0)));
            runtime.requestStructureCheck();
            redstonePaused = false;
            lockedRecipeId = null;
            runtime.craftingRuntime().invalidate();
            Map<Identifier, MachineLevel> restoredLevels = new LinkedHashMap<>();
            input.listOrEmpty("found_levels", Codec.STRING).forEach(id -> {
                MachineLevel foundLevel = MachineLevelRegistry.getLevel(Identifier.parse(id));
                if (foundLevel != null) restoredLevels.put(foundLevel.typeId(), foundLevel);
            });
            ControllerRuntimeSnapshot current = runtimeSnapshot();
            runtime.publishComponentState(runtime.components(), current.foundModifiers(), restoredLevels,
                    current.linkedPortPositions());
            lastFailureUnloc = null;
            String lockedRecipeName = input.getStringOr("locked_recipe", "");
            if (!lockedRecipeName.isEmpty()) {
                Identifier restoredLock = Identifier.parse(lockedRecipeName);
                if (RecipeRegistry.getRecipe(restoredLock) != null) lockedRecipeId = restoredLock;
            }
            if (factoryRuntimeInput.getIntOr("lane_count", -1) >= 0) {
                pendingFactoryRuntimeInput = factoryRuntimeInput;
                ensureFactoryRuntimeLoaded();
            }
            runtime.craftingRuntime().load(input.childOrEmpty("crafting_runtime"), resourceDomain());
            runtime.requestStructureCheck();
            runtime.restoreStructureVersion(input.getLongOr("structure_runtime_version", 0L));
        } finally {
            publishRuntimeState();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
