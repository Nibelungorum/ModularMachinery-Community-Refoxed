package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
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
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.level.LevelMismatch;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.LevelInsufficientFailure;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.PlayerInventoryStructureItemSink;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.SmartInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewBuilder;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewPredicates;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.internal.recipe.RecipeStartDelay;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;

import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.StructureRuntime.StructureWorkSnapshot;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

public class MachineControllerBlockEntity extends BlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger(MachineControllerBlockEntity.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    private static final Set<MachineControllerBlockEntity> FORMED_CONTROLLERS = ConcurrentHashMap.newKeySet();
    private static final int PREVIEW_RECEIVER_WINDOW_TICKS = 8 * 20;
    private final int instanceId = INSTANCE_COUNTER.incrementAndGet();

    private @Nullable Runnable structureDiagnosticCallbackForTesting;
    private int matcherInvocationCountForTesting;
    private int scanBatchCountForTesting;
    private final Map<Long, Integer> scanBatchesPerTickForTesting = new LinkedHashMap<>();
    private @Nullable Integer structureCheckIntervalOverrideForTesting;
    private @Nullable Integer structureScanBatchesOverrideForTesting;
    private @Nullable Integer buildBlocksPerTickOverrideForTesting;
    private boolean clientActive;
    private @Nullable Identifier clientRecipeId;
    private @Nullable PktMachineStatePayload lastBroadcastState;
    private @Nullable Identifier lockedRecipeId;
    private @Nullable String lastFailureUnloc;
    private @Nullable LevelInsufficientFailure recipeFailure;
    private boolean redstonePaused;
    private @Nullable FactoryRecipeScheduler factoryScheduler;
    private int recipeSearchRetryCounter;
    private long recipeSearchAttemptCounter;
    private long lastRecipeSearchRegistryVersion = Long.MIN_VALUE;
    private long cachedCandidatesReloadVersion = Long.MIN_VALUE;
    private int cachedDatapackRecipeCount = -1;
    private @Nullable Identifier cachedCandidatesMachineId;
    private List<MachineRecipe> cachedCandidates = List.of();
    private RecipeStartDelay recipeStartDelay = new RecipeStartDelay();
    private @Nullable MachineRecipe lastRecipe;
    private long lastRecipeStructureVersion = Long.MIN_VALUE;
    private long lastRecipeCapabilityVersion = Long.MIN_VALUE;
    private long lastRecipeModifierSnapshotVersion = Long.MIN_VALUE;
    private boolean recipeDirty = true;
    private boolean sharedStartPending;
    private @Nullable MachineRecipe pendingSharedStartRecipe;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingSharedStartDomain;
    private long pendingSharedStartStructureVersion = Long.MIN_VALUE;
    private long pendingSharedStartCapabilityVersion = Long.MIN_VALUE;
    private long pendingSharedStartModifierVersion = Long.MIN_VALUE;
    private long nextSharedStartToken;
    private long pendingSharedStartToken;
    private boolean sharedTickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingSharedTickDomain;
    private long nextSharedTickToken;
    private long pendingSharedTickToken;
    private boolean syncedRuntimeActive;
    private Map<UUID, Long> previewReceivers = new LinkedHashMap<>();
    private @Nullable Runnable factoryCapacityInvalidationCallbackForTesting;
    private @Nullable Runnable structureCheckCallbackForTesting;
    private @Nullable ValueInput pendingFactoryRuntimeInput;
    private @Nullable MultiblockAssemblyService.BuildTaskRegistry buildTasks;
    private @Nullable ServerPlayer buildTaskOwner;
    private int buildTaskAge;
    private final Map<Long, Integer> buildTaskPlacementsPerTickForTesting = new LinkedHashMap<>();
    private final transient MachineControllerRuntime runtime;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.controllerFor(machineIdFromState(state)).get(), pos, state);
        runtime = new MachineControllerRuntime(this);
    }

    public ControllerRuntimeSnapshot runtimeSnapshot() {
        return runtime.snapshot();
    }

    public StructureSnapshot structureSnapshot() {
        return runtimeSnapshot().structure();
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
        if (runtimeSnapshot().structure().configuredMachine() == null) bindDefaultMachine();
        invalidateForControllerRotation();
        if (shouldCheckStructure()) checkStructure();
    }

    private void publishRuntimeState() {
        if (level != null && level.isClientSide()) return;
        runtime.publishSnapshot();
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        runtime.publishStructureState(isStructureAreaLoaded(current.structure()), current.structure().formed(),
                current.structure().configuredMachine(), current.structure().matchedStage());
        runtime.publishComponentState(current.components(), current.foundModifiers(),
                current.foundLevels(), current.linkedPortPositions());
        ControllerRuntimeSnapshot published = runtimeSnapshot();
        StructureSnapshot structure = published.structure();
        boolean activeState = published.crafting().recipeId() != null || published.factory().active();
        Identifier recipeId = published.crafting().recipeId();
        CraftingStatus status = !structure.formed()
                ? CraftingStatus.MISSING_STRUCTURE
                : !structure.structureAreaLoaded() ? CraftingStatus.CHUNK_UNLOADED
                : activeState
                ? redstonePaused ? CraftingStatus.paused() : CraftingStatus.working()
                : lastFailureUnloc == null ? CraftingStatus.IDLE : CraftingStatus.failure(lastFailureUnloc);
        runtime.publishCraftingState(recipeId, status, craftingFailureStatus());
    }

    private @Nullable ExecutionStatus craftingFailureStatus() {
        if (lastFailureUnloc == null) return null;
        ExecutionStatus runtimeFailure = runtime.craftingRuntime().failure();
        return runtimeFailure == null
                ? new ExecutionStatus(MMCR.id("crafting_failure"), StatusSeverity.FAILURE,
                MMCR.id("crafting"), Map.of("message", lastFailureUnloc))
                : runtimeFailure;
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

    public void setMachine(Machine m) {
        stopFactoryController();
        invalidateStructureScan(StructureMatcher.InvalidationReason.PATTERN);
        clearFoundModifiers();
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        runtime.publishStructureState(isStructureAreaLoaded(current.structure()), current.structure().formed(), m,
                current.structure().matchedStage());
        runtime.publishComponentState(current.components(), current.foundModifiers(), Map.of(),
                current.linkedPortPositions());
        markRecipeDirty();
        setChanged();
        publishRuntimeState();
    }

    public void invalidateFormedStructure() {
        resetMachine();
        publishRuntimeState();
    }

    public void requestImmediateStructureCheck() {
        requestImmediateStructureCheck(null);
    }

    public void requestImmediateStructureCheck(@Nullable ServerPlayer diagnosticPlayer) {
        runtime.requestStructureCheck();
        if (diagnosticPlayer != null) {
            publishStructureWork(state -> state.withDiagnostic(true, diagnosticPlayer.getUUID(),
                    level instanceof ServerLevel serverLevel ? serverLevel.dimension() : null));
        }
        publishRuntimeState();
    }

    public int matcherInvocationCountForTesting() { return matcherInvocationCountForTesting; }
    public int scanBatchCountForTesting() { return scanBatchCountForTesting; }
    public Map<Long, Integer> scanBatchesPerTickForTesting() { return Map.copyOf(scanBatchesPerTickForTesting); }
    public boolean isStructureDiagnosticRequestedForTesting() { return structureWorkSnapshot().diagnosticRequested(); }
    public void setStructureDiagnosticCallbackForTesting(@Nullable Runnable callback) {
        structureDiagnosticCallbackForTesting = callback;
    }
    public boolean isPendingStructureInvalidationForTesting() { return structureWorkSnapshot().pendingInvalidation(); }
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

    public MachineRecipe getActiveRecipe() { return runtime.craftingRuntime().recipe(); }

    public int getTickCounter() { return runtime.craftingRuntime().tickCount(); }

    public ActiveMachineRecipe getActive() { return runtime.craftingRuntime().activeRecipe(); }

    public @Nullable String getLastFailureUnloc() { return lastFailureUnloc; }

    public @Nullable LevelInsufficientFailure getRecipeFailure() { return recipeFailure; }

    public @Nullable PortRequirementSpec.Failure getLastFormationFailure() {
        return structureSnapshot().lastFormationFailure();
    }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        if (level instanceof ServerLevel serverLevel) {
            return StructureClaimRegistry.get(serverLevel).domainFor(getBlockPos());
        }
        return null;
    }

    public void refreshModuleConnectionState() {
        runtime.refreshModuleConnectionState();
    }

    public void onStructureBlockChanged(BlockPos changedPos) {
        runtime.onStructureBlockChanged(changedPos);
    }

    public void handleStructureBlockChanged(BlockPos changedPos) {
        if (structureWorkSnapshot().scan() != null) publishStructureWork(state -> state.withPendingInvalidation(true));
        StructureSnapshot structure = runtimeSnapshot().structure();
        if (!structure.formed()) {
            if (structure.configuredMachine() != null) requestImmediateStructureCheck();
            publishRuntimeState();
            return;
        }
        if (structure.pattern() == null || structure.facing() == null) {
            publishRuntimeState();
            return;
        }
        if (!isInsideCompiledBounds(changedPos)) {
            publishRuntimeState();
            return;
        }
        runtime.requestStructureCheck();
        if (structureWorkSnapshot().scan() != null) publishStructureWork(state -> state.withPendingInvalidation(true));
        if (level instanceof ServerLevel serverLevel) ModuleConnectionCoordinator.enqueueCouplers(serverLevel, this);
        markRecipeDirty();
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
        if (level == null || level.isClientSide()) return;
        FORMED_CONTROLLERS.removeIf(controller -> controller.isRemoved() || controller.level == null);
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (MachineControllerBlockEntity controller : FORMED_CONTROLLERS) {
            if (controller.level == level && controller.structureSnapshot().criticalChunks().contains(chunkPos)) {
                controller.onStructureChunkChanged(serverLevel);
            }
        }
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
                                 @Nullable ExecutionStatus failure, boolean structureAreaLoaded) {
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
        runtime.publishCraftingState(clientRecipeId, craftingStatus, failure);
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
        return isRuntimeActive(runtimeSnapshot());
    }

    private boolean isRuntimeActive(ControllerRuntimeSnapshot state) {
        if (!state.structure().formed() || redstonePaused || !state.structure().structureAreaLoaded()) return false;
        return state.crafting().recipeId() != null || state.factory().active();
    }

    public int currentParallelism() {
        return runtime.craftingRuntime().parallelism();
    }

    public int activeFactoryThreadCount() {
        return hasFactoryController() ? runtime.factoryRuntime().activeLaneCount() : 0;
    }

    public boolean isPortUsedByActiveRecipe(BlockPos pos) {
        return runtime.craftingRuntime().active()
                && runtimeSnapshot().components().stream().anyMatch(component -> component.getPos().equals(pos));
    }

    public void markRecipeDirty() {
        recipeDirty = true;
    }

    public void resetLinkedPortAppearances() {
        unlinkLinkedPorts();
    }

    public int getMaxParallelism() {
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        return runtime.maxParallelism(configuredMachine);
    }

    public int parallelControllerCount() {
        int count = 0;
        for (ProcessingComponent component : runtimeSnapshot().components()) {
            if (component.getContainer() instanceof ParallelControllerBlockEntity) count++;
        }
        return count;
    }

    public int maxParallelControllerCount() {
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (configuredMachine == null || !configuredMachine.parallelizable()) return 0;
        int maxParallelism = Math.max(1, configuredMachine.maxParallelism());
        return maxParallelism == Integer.MAX_VALUE ? Integer.MAX_VALUE : maxParallelism;
    }

    public @Nullable FactorySchedulerBlockEntity getFactoryController() {
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (configuredMachine == null || !configuredMachine.hasFactory()) return null;
        return factoryComponents().stream().findFirst().orElse(null);
    }

    public boolean toggleFactoryRecipeLock(int threadIndex) {
        if (hasFactoryController()) {
            boolean toggled = runtime.factoryRuntime().toggleRecipeLock(threadIndex);
            if (toggled) setChanged();
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
        player.connection.send(new ClientboundCustomPayloadPacket(machineStatePayload(isRuntimeActive())));
    }

    public boolean hasFactoryController() {
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (configuredMachine == null || !configuredMachine.hasFactory()) return false;
        return runtimeSnapshot().components().stream().anyMatch(component -> component.getContainer() instanceof FactorySchedulerBlockEntity);
    }

    public int effectiveFactoryThreadLimit() {
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (configuredMachine == null || !configuredMachine.hasFactory()) return 1;
        int aggregatedThreads = factorySchedulerThreadCount();
        int levelBonus = runtimeSnapshot().foundLevels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .map(Map.Entry::getValue)
                .mapToInt(foundLevel -> foundLevel.modifier().factoryThreadBonus())
                .sum();
        long effective = Math.max(1, aggregatedThreads) + levelBonus;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, effective));
    }

    public FactoryRecipeScheduler factoryScheduler() {
        if (factoryScheduler == null && !hasFactoryController()) {
            throw new IllegalStateException("Factory scheduler requested without a formed factory controller");
        }
        if (factoryScheduler == null) {
            factoryScheduler = new FactoryRecipeScheduler(1, runtime.factoryRuntime());
            if (pendingFactoryRuntimeInput != null) {
                runtime.factoryRuntime().load(pendingFactoryRuntimeInput, this);
                pendingFactoryRuntimeInput = null;
            }
        }
        runtime.factoryRuntime().ensureBaseLane(this);
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        if (configuredMachine != null && configuredMachine.hasFactory()) {
            factoryScheduler.setThreadLimit(effectiveFactoryThreadLimit());
        }
        return factoryScheduler;
    }

    public List<FactoryRuntime.ThreadSnapshot> factoryThreadSnapshots() {
        return hasFactoryController()
                ? runtime.factoryRuntime().threadSnapshots()
                : List.of(FactoryRuntime.ThreadSnapshot.idleBase());
    }

    public FactoryControllerSnapshot factoryControllerSnapshot() {
        ControllerRuntimeSnapshot state = runtimeSnapshot();
        Machine configuredMachine = state.structure().configuredMachine();
        boolean factoryController = configuredMachine != null && configuredMachine.hasFactory()
                && state.components().stream().anyMatch(component -> component.getContainer() instanceof FactorySchedulerBlockEntity);
        if (!factoryController) return FactoryControllerSnapshot.empty(getBlockPos());
        FactorySnapshot factory = state.factory();
        StructureSnapshot structure = state.structure();
        int activeThreadCount = runtime.factoryRuntime().activeLaneCount();
        int maxParallelism = runtime.maxParallelism(configuredMachine);
        int parallelSlots = (int) state.components().stream()
                .filter(component -> component.getContainer() instanceof ParallelControllerBlockEntity)
                .count();
        String factoryFailure = failureUnloc(factory.failure());
        if (factoryFailure.isEmpty()) factoryFailure = lastFailureUnloc == null ? "" : lastFailureUnloc;
        return new FactoryControllerSnapshot(getBlockPos(), structure.formed(), isRedstonePaused(),
                activeThreadCount, factory.laneLimit(), factory.activeParallelism(), maxParallelism,
                structure.machine() == null ? "" : structure.machine().displayNameKey(), parallelSlots,
                factoryFailure, factoryThreadSnapshots());
    }

    private static String failureUnloc(@Nullable ExecutionStatus failure) {
        if (failure == null) return "";
        return switch (failure.details().getOrDefault("reason", "")) {
            case "module_connection" -> "gui.mmcr.controller.failure.module_connection";
            case "no_output_capacity" -> "gui.mmcr.controller.failure.missing_output";
            case "insufficient_energy" -> "gui.mmcr.controller.failure.missing_energy";
            default -> "gui.mmcr.controller.failure.missing_input";
        };
    }

    public void sendFactoryControllerSnapshot(@Nullable ServerPlayer player) {
        if (player != null) {
            player.connection.send(new ClientboundCustomPayloadPacket(
                    new PktFactoryControllerStatePayload(factoryControllerSnapshot())));
        }
    }

    public int factorySchedulerThreadCount() {
        long total = 0;
        for (ProcessingComponent component : runtimeSnapshot().components()) {
            if (component.getContainer() instanceof FactorySchedulerBlockEntity scheduler) {
                total += scheduler.threadCount();
                if (total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    List<FactorySchedulerBlockEntity> factoryComponents() {
        return runtimeSnapshot().components().stream()
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
        if (factoryComponents().isEmpty()) return;
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
        ControllerRuntimeSnapshot tickState = runtimeSnapshot();
        boolean activeBefore = isRuntimeActive(tickState);
        try {
        if (advanceBuildTask()) return;

        // 1.21+ exposes the old strong-power query through SignalGetter's direct signal helper.
        boolean powered = level.getDirectSignalTo(getBlockPos()) > 0;
        if (powered) {
            redstonePaused = true;
            runtime.pauseCrafting();
            if (hasFactoryController()) {
                syncOpenFactoryControllerMenus();
            }
            syncRuntimeStateIfChanged();
            setActiveState(false);
            syncRuntimeStateIfChanged();
            setChanged();
            return;
        }
        redstonePaused = false;
        runtime.resumeCrafting();
        if (hasFactoryController()) {
            syncOpenFactoryControllerMenus();
        }
        syncRuntimeStateIfChanged();
        if (tickState.crafting().recipeId() != null || tickState.factory().active()) setActiveState(true);
        if (tickState.structure().formed() && tickState.structure().structureAreaLoaded()) {
            if (hasFactoryController()) {
                tickFactoryRecipes();
            } else {
                tickSingleActiveRecipe();
            }
        }
        } finally {
            publishRuntimeState();
            broadcastStateIfChanged(activeBefore);
        }
    }

    public boolean hasActiveBuildTask() {
        return buildTaskRegistry().hasActiveTask(getBlockPos());
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
            runtime.requestStructureCheck();
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
    }

    private void tickFactoryRecipes() {
        int maxParallelism = getMaxParallelism();
        List<MachineRecipe> candidates = recipesForMachine();
        FactoryRecipeScheduler scheduler = factoryScheduler();
        scheduler.setThreadLimit(effectiveFactoryThreadLimit());
        StructureSnapshot structure = runtimeSnapshot().structure();
        FactoryRuntime factory = runtime.factoryRuntime();
        factory.syncCoreLanes(this, structure.machine(), candidates);
        factory.tick(scheduler.availableCandidates(candidates), maxParallelism, this::playFinishSound);
        setActiveState(factory.activeLaneCount() > 0);
        syncRuntimeStateIfChanged();
        if (factory.activeLaneCount() > 0) {
            lastFailureUnloc = null;
            recipeFailure = null;
        }
        syncOpenFactoryControllerMenus();
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
        StructureSnapshot structure = runtimeSnapshot().structure();
        if (structureCheckCallbackForTesting != null) structureCheckCallbackForTesting.run();
        publishStructureWork(state -> state.withDirty(false).withCheckCounter(0)
                .withNextCheckTick(level.getGameTime() + structureCheckIntervalTicks()));
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
                resetMachine(runtimeSnapshot().structure().lastFormationFailure() == null);
                return;
            }
        }
        checkAllPatterns(facing);
        if (structureWorkSnapshot().scan() != null) return;
        StructureSnapshot finalStructure = runtimeSnapshot().structure();
        if (!finalStructure.formed()) resetMachine(finalStructure.lastFormationFailure() == null);
    }

    private boolean shouldCheckStructure() {
        StructureSnapshot structure = runtimeSnapshot().structure();
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (structure.dirty()) return true;
        if (!structure.formed() && work.nextCheckTick() < 0L) return true;
        publishStructureWork(state -> state.withCheckCounter(work.checkCounter() + 1));
        return level.getGameTime() >= work.nextCheckTick();
    }

    private void invalidateForControllerRotation() {
        StructureSnapshot structure = runtimeSnapshot().structure();
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
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
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
        StructureSnapshot structure = runtimeSnapshot().structure();
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
        MMCR.LOG.debug("Structure mismatch at world position {} (relative {}): expected={}, actual={}",
                pos, mismatch.relativePos(), describeExpectedForLog(mismatch.expected()), describeActualForLog(mismatch.actualState()));
        player.sendSystemMessage(Component.translatable(
                "message.mmcr.multiblock_mismatch",
                styledPosition(pos),
                describeExpected(mismatch.expected()),
                mismatch.actualState().getBlock().getName().copy().withStyle(ChatFormatting.RED)));
        PacketDistributor.sendToPlayer(player, new PktMultiblockMismatchHighlightPayload(level.dimension(), pos));
    }

    private static String describeExpectedForLog(BlockPredicate expected) {
        return switch (expected) {
            case BlockPredicate.OfBlock ofBlock -> "block=" + BuiltInRegistries.BLOCK.getKey(ofBlock.block());
            case BlockPredicate.DeferredBlock deferredBlock ->
                    "block=" + BuiltInRegistries.BLOCK.getKey(deferredBlock.supplier().get());
            case BlockPredicate.OfBlockState ofState -> "state=" + describeActualForLog(ofState.state());
            case BlockPredicate.OfTag ofTag -> "tag=#" + ofTag.tag().location();
            case BlockPredicate.AnyOf anyOf -> "any_of=" + anyOf.children().stream()
                    .map(MachineControllerBlockEntity::describeExpectedForLog).toList();
            case BlockPredicate.Air ignored -> "air";
            case BlockPredicate.Any ignored -> "any block";
            case BlockPredicate.MachineCoupler ignored -> "machine coupler";
        };
    }

    private static String describeActualForLog(BlockState state) {
        return "block=" + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ", state=" + state;
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
        if (stageCompiled != null && hasCompiledFacing(stageCompiled, facing)
                && !StructureMatcher.isAreaLoaded(stageCompiled, facing, level, getBlockPos())) return false;
        if (work.scanSteppedTick() == level.getGameTime()) return false;
        StructureMatcher.ScanOptions options = StructureMatcher.ScanOptions.of(structureScanBatches(),
                structureSentinelEnabled(), structureSentinelCount());
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(runtimeSnapshot().structure().version(), facing, candidatePattern.rollFacing(),
                candidatePattern.stageNumber(), rotatedPattern, rotatedPattern, replacements, stateSensitive,
                options, rotatedPattern == work.previousMismatchPattern() ? work.previousMismatch() : null);
        runtime.startStructureScan(scan, candidate, candidatePattern, level.getGameTime(), level.getGameTime());
        if (invalidateActiveStructureScanIfIdentityChanged()) {
            clearStructureScan();
            return false;
        }
        scanBatchCountForTesting++;
        scanBatchesPerTickForTesting.merge(level.getGameTime(), 1, Integer::sum);
        StructureMatcher.ScanResult scanResult = runtime.stepStructureScan(level, getBlockPos());
        if (scanResult.inProgress()) {
            publishStructureWork(state -> state.withNextCheckTick(level.getGameTime() + 1L));
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
            } else {
                recordStructureMismatch(candidate, facing, rotatedPattern, replacements, stateSensitive);
            }
            clearStructureScan();
            publishStructureWork(state -> state.withPendingInvalidation(false));
            if (runtimeSnapshot().structure().formed()) resetMachine(true, true, false);
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
        StructureMatcher.ScanResult scanResult = runtime.stepStructureScan(level, getBlockPos());
        if (scanResult.inProgress()) {
            publishStructureWork(state -> state.withNextCheckTick(level.getGameTime() + 1L));
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
            } else {
                recordStructureMismatch(candidate, getBlockState().getValue(MachineControllerBlock.FACING),
                        candidatePattern.pattern(), replacements, candidatePattern.compiled() != null && candidatePattern.compiled().stateSensitive());
            }
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

        if (level instanceof ServerLevel serverLevel) {
            StructureClaimRegistry.ClaimResult result = StructureClaimRegistry.get(serverLevel)
                    .claim(getBlockPos(), componentClaims(rotatedPattern, stageCompiled, facing));
            if (!result.accepted()) {
                StructureClaimRegistry.Conflict conflict = result.conflict();
                publishStructureWork(state -> state.withFormationFailure(new PortRequirementSpec.Failure(
                        "component_claim_conflict component=" + conflict.componentPos() + " owner=" + conflict.ownerPos(),
                        0, 1, OptionalInt.empty(), PortRequirementSpec.FailureReason.MISSING)));
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
        runtime.clearStructureScan();
    }

    private void sendRequestedStructureDiagnostic(@Nullable StructureMatcher.Mismatch mismatch) {
        StructureWorkSnapshot work = structureWorkSnapshot();
        if (!work.diagnosticRequested()) return;
        StructureSnapshot structure = runtimeSnapshot().structure();
        UUID playerId = work.diagnosticPlayerId();
        ResourceKey<Level> dimension = work.diagnosticDimension();
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
        } else if (structure.lastFormationFailure() != null) {
            if (playerId != null && dimension != null && level instanceof ServerLevel serverLevel
                    && serverLevel.dimension().equals(dimension)) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && player.level().dimension().equals(dimension)) {
                    sendFormationFailureDiagnostic(player, structure.lastFormationFailure());
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
        boolean loaded = candidatePattern == null || candidatePattern.compiled() == null
                || !hasCompiledFacing(candidatePattern.compiled(), facing)
                || StructureMatcher.isAreaLoaded(candidatePattern.compiled(), facing, level, getBlockPos());
        boolean changed = work.scan().version() != runtimeSnapshot().structure().version()
                || work.scan().facing() != facing
                || work.scan().rollFacing() != normalizedRoll
                || candidatePattern == null
                || work.scan().stage() != candidatePattern.stageNumber()
                || work.scan().pattern() != candidatePattern.pattern()
                || !loaded;
        if (!changed) return false;
        StructureMatcher.InvalidationReason reason = !loaded
                ? StructureMatcher.InvalidationReason.UNLOADED
                : work.scan().version() != runtimeSnapshot().structure().version()
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
        if (candidate == null) return;
        String diagnostic = structureMismatchDiagnostic(candidate, facing, rotatedPattern, level, getBlockPos(), replacements,
                stateSensitive);
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
        if (rotatedPattern.isEmpty()) {
            return "machine=" + candidate.registryName()
                    + " facing=" + facing.name()
                    + " controllerPos=" + ctrlPos
                    + " reason=emptyPattern";
        }

        var mismatch = StructureMatcher.firstMismatch(rotatedPattern, level, ctrlPos, replacements, stateSensitive);
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
        clearStructureDiagnosticRequest();
        StructureSnapshot previousStructure = runtimeSnapshot().structure();
        Machine previousMachine = previousStructure.machine();
        boolean structureChanged = runtime.publishFormationState(matchedMachine, rotatedPattern, compiledPattern,
                facing, rollFacing, compiledPattern == null ? 1 : compiledPattern.stageNumber());
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        List<RecipeModifier> beforeModifiers = current.foundModifiers().values().stream().flatMap(List::stream).toList();
        Map<String, List<RecipeModifier>> foundModifiers = collectFoundModifiers(replacements);
        runtime.publishComponentState(current.components(), foundModifiers, levels, current.linkedPortPositions());
        List<RecipeModifier> afterModifiers = foundModifiers.values().stream().flatMap(List::stream).toList();
        if (!beforeModifiers.equals(afterModifiers)) markRecipeDirty();
        refreshCriticalStructureChunks();
        FORMED_CONTROLLERS.add(this);
        if (level instanceof ServerLevel serverLevel) ModuleConnectionCoordinator.enqueueCouplers(serverLevel, this);
        if ((previousMachine != null && previousMachine != matchedMachine) || structureChanged) {
            stopFactoryController();
        }
        publishStructureWork(state -> state.withDirty(false));
        if (!physicalFormed()) {
            updatePhysicalFormedState(true);
            notifyPreviewReceiversStructureFormed();
        }
        updateComponents();
        resumePausedRecipeAfterStructureCheck();
        clearCandidateCache();
        publishStructureWork(state -> state.withFormationFailure(null).withLastStructureError(null));
        setChanged();
        syncLevelState();
        publishRuntimeState();
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
                    if (replacement.getReplacement().matches(actual)) {
                        nextModifiers.putIfAbsent(replacement.getModifierName(), replacement.getModifiers());
                    }
                }
            }
        }
        return nextModifiers;
    }

    private void clearFoundModifiers() {
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        if (current.foundModifiers().isEmpty()) return;
        runtime.publishComponentState(current.components(), Map.of(), current.foundLevels(),
                current.linkedPortPositions());
        markRecipeDirty();
    }

    private void updateComponents() {
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        StructureSnapshot structure = current.structure();
        Machine matchedMachine = structure.machine();
        BlockArray matchedPattern = structure.pattern();
        List<FactorySchedulerBlockEntity> previousFactories = factoryComponents();
        for (FactorySchedulerBlockEntity factory : previousFactories) factory.bindOwner(null);
        List<SmartInterfaceBlockEntity> previousSmartInterfaces = runtimeSnapshot().components().stream()
                .map(ProcessingComponent::getContainer)
                .filter(SmartInterfaceBlockEntity.class::isInstance)
                .map(SmartInterfaceBlockEntity.class::cast)
                .toList();
        if (level == null || matchedMachine == null || matchedPattern == null) {
            runtime.publishComponentState(List.of(), current.foundModifiers(), current.foundLevels(), Set.of());
            return;
        }

        unlinkLinkedPorts();
        List<ProcessingComponent> nextComponents = new ArrayList<>();
        Set<BlockPos> nextLinkedPortPositions = new HashSet<>();

        List<SmartInterfaceBlockEntity> smartInterfaces = new ArrayList<>();
        for (BlockPos relativePos : componentPositions()) {
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

        for (BlockPos relativePos : componentPositions()) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
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
                Identifier formedTexture = matchedMachine.appearance().formedPortBaseTexture();
                port.linkControllerAppearance(getBlockPos(), formedTexture);
                nextLinkedPortPositions.add(worldPos.immutable());
            }
            var component = tile.provideComponent();
            if (!(tile instanceof BlockEntity container)) continue;
            nextComponents.add(new ProcessingComponent(component, container, worldPos, relativePos, matchedPattern.tagsAt(relativePos)));
        }
        current = runtimeSnapshot();
        runtime.publishComponentState(nextComponents, current.foundModifiers(),
                current.foundLevels(), nextLinkedPortPositions);
        invalidateFactoryCapacity();
    }

    private void unlinkLinkedPorts() {
        ControllerRuntimeSnapshot current = runtimeSnapshot();
        Set<BlockPos> linkedPortPositions = current.linkedPortPositions();
        if (level == null) {
            runtime.publishComponentState(current.components(), current.foundModifiers(), current.foundLevels(), Set.of());
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
        runtime.publishComponentState(current.components(), current.foundModifiers(), current.foundLevels(), Set.of());
    }

    private List<BlockPos> componentPositions() {
        StructureSnapshot structure = runtimeSnapshot().structure();
        if (hasCompiledFacing(structure.compiledPattern(), structure.facing())) {
            return structure.compiledPattern().componentPositions(structure.facing());
        }
        return new ArrayList<>(structure.pattern().pattern().keySet());
    }

    private static List<BlockPos> componentPositions(BlockArray pattern, @Nullable CompiledMachinePattern compiled, Direction facing) {
        return hasCompiledFacing(compiled, facing) ? compiled.componentPositions(facing) : new ArrayList<>(pattern.pattern().keySet());
    }

    private PortRequirementSpec.PortCounts countPorts(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (level == null || rotatedPattern == null) return PortRequirementSpec.PortCounts.empty();

        List<BlockPos> positions = hasCompiledFacing(compiledPattern, facing)
                ? compiledPattern.portPositions(facing)
                : new ArrayList<>(rotatedPattern.pattern().keySet());
        for (BlockPos relativePos : positions) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (!(level.getBlockEntity(worldPos) instanceof IOPortBlockEntity port)) continue;
            counts.merge(port.kind().id(), 1, Integer::sum);
            String baseId = basePortId(port.kind());
            if (!baseId.equals(port.kind().id())) counts.merge(baseId, 1, Integer::sum);
        }
        return PortRequirementSpec.PortCounts.of(counts);
    }

    private static String basePortId(IOPortKind kind) {
        String family = kind.itemBusSize().isPresent() ? "item"
                : kind.fluidHatchSize().isPresent() ? "fluid"
                : kind.energyHatchSize().isPresent() ? "energy" : null;
        if (family == null) return kind.id();
        String direction = kind.ioType() == IOType.INPUT ? "input" : "output";
        String suffix = family.equals("item") ? "bus" : "hatch";
        return family + "_" + direction + "_" + suffix;
    }

    private List<IOPortKind> portKinds(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        if (level == null || rotatedPattern == null) return List.of();

        List<BlockPos> positions = hasCompiledFacing(compiledPattern, facing)
                ? compiledPattern.portPositions(facing)
                : new ArrayList<>(rotatedPattern.pattern().keySet());
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

    private static boolean contains(BoundingBox box, BlockPos relative) {
        return relative.getX() >= box.minX()
                && relative.getX() <= box.maxX()
                && relative.getY() >= box.minY()
                && relative.getY() <= box.maxY()
                && relative.getZ() >= box.minZ()
                && relative.getZ() <= box.maxZ();
    }

    private void onStructureChunkStateChanged() {
        StructureSnapshot structure = runtimeSnapshot().structure();
        if (structureWorkSnapshot().scan() != null) invalidateStructureScan(StructureMatcher.InvalidationReason.UNLOADED);
        boolean wasLoaded = structure.structureAreaLoaded();
        boolean loaded = isStructureAreaLoaded(structure);
        if (!structure.formed() || structure.pattern() == null || structure.facing() == null) {
            runtime.publishStructureState(loaded, structure.formed(), structure.configuredMachine(), structure.matchedStage());
            publishRuntimeState();
            return;
        }
        if (wasLoaded == loaded) {
            publishRuntimeState();
            return;
        }
        runtime.publishStructureState(loaded, structure.formed(), structure.configuredMachine(), structure.matchedStage());
        runtime.requestStructureCheck();
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
        runtime.publishSnapshot();
        boolean next = isRuntimeActive();
        if (next == syncedRuntimeActive) return;
        syncedRuntimeActive = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void refreshCriticalStructureChunks() {
        StructureSnapshot structure = runtimeSnapshot().structure();
        Set<ChunkPos> criticalChunks = new HashSet<>();
        criticalChunks.add(new ChunkPos(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4));
        if (level == null || structure.pattern() == null || structure.facing() == null) {
            runtime.publishCriticalStructureChunks(criticalChunks);
            return;
        }
        for (BlockPos relativePos : componentPositions()) {
            BlockEntity blockEntity = level.getBlockEntity(getBlockPos().offset(relativePos));
            if (blockEntity instanceof IOPortBlockEntity
                    || blockEntity instanceof SmartInterfaceBlockEntity
                    || blockEntity instanceof ModuleCouplerBlockEntity) {
                BlockPos blockPos = blockEntity.getBlockPos();
                criticalChunks.add(new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4));
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
        for (ProcessingComponent processingComponent : runtimeSnapshot().components()) {
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
        invalidateStructureScan(StructureMatcher.InvalidationReason.VERSION);
        Machine configuredMachine = structure.configuredMachine();
        PortRequirementSpec.Failure previousFormationFailure = structure.lastFormationFailure();
        Object previousStructureError = structure.lastStructureError();
        boolean wasFormed = structure.formed() || physicalFormed();
        if (wasFormed && level instanceof ServerLevel serverLevel) ModuleConnectionCoordinator.clearConnectionsFor(serverLevel, this);
        boolean hadActive = runtime.craftingRuntime().active();
        releaseStructureClaims();
        unbindSmartInterfaces();
        unlinkLinkedPorts();
        stopFactoryController();
        for (FactorySchedulerBlockEntity factory : factoryComponents()) factory.bindOwner(null);
        runtime.resetStructure(configuredMachine, wasFormed || hadActive);
        if (!clearFormationFailure) {
            publishStructureWork(state -> state.withFormationFailure(previousFormationFailure)
                    .withLastStructureError(previousStructureError));
        }
        FORMED_CONTROLLERS.remove(this);
        runtime.publishComponentState(List.of(), Map.of(), Map.of(), Set.of());
        if (wasFormed && invalidateScheduledCheck) publishStructureWork(state -> state.withNextCheckTick(-1L));
        runtime.craftingRuntime().invalidate();
        setActiveState(false);
        clearPendingSharedStart();
        clearSharedTickPending();
        clearPendingConflictStart();
        lastFailureUnloc = null;
        recipeFailure = null;
        redstonePaused = false;
        markRecipeDirty();
        clearCandidateCache();
        if (wasFormed && updateBlockState) updatePhysicalFormedState(false);
        setChanged();
        syncRuntimeStateIfChanged();
        publishRuntimeState();
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
        FactoryControllerSnapshot next = factoryControllerSnapshot();
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof FactoryControllerMenu menu
                    && menu.controllerPos().equals(getBlockPos())) {
                menu.applySnapshot(next);
                menu.markSnapshotSent(next);
                player.connection.send(new ClientboundCustomPayloadPacket(
                        new PktFactoryControllerStatePayload(next)));
            }
        }
    }

    private void broadcastStateIfChanged(boolean activeBeforeTick) {
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        boolean formed = runtimeState.structure().formed();
        boolean activeNow = isRuntimeActive(runtimeState);
        var thread = hasFactoryController() ? factoryThreadSnapshots().getFirst() : null;
        boolean recipeLocked = thread == null ? lockedRecipeId != null : thread.locked();
        String lockedRecipe = thread == null ? lockedRecipeId == null ? "" : lockedRecipeId.toString() : thread.lockedRecipeId();
        Identifier machineId = machineId();
        String machineIdValue = machineId == null ? "" : machineId.toString();
        int controllerRole = MachineControllerMenu.controllerRoleSyncValue(this);
        ModuleConnectionStatus moduleStatus = runtimeState.moduleConnectionStatus();
        int installedModuleCount = runtimeState.installedModuleCount();
        boolean moduleConnected = moduleStatus.connected();
        String connectedHostIdValue = moduleConnected ? moduleStatus.connectedHostId().toString() : "";
        var packet = machineStatePayload(activeNow, formed, recipeLocked, lockedRecipe, machineIdValue, controllerRole,
                installedModuleCount, moduleConnected, connectedHostIdValue);
        if (lastBroadcastState != null && !PktMachineStatePayload.stateChanged(packet, lastBroadcastState)
                && activeBeforeTick == activeNow) {
            return;
        }
        lastBroadcastState = packet;
        if (!(level instanceof ServerLevel sl)) return;
        for (var player : sl.getPlayers(p -> p.distanceToSqr(getBlockPos().getCenter()) < 64 * 64)) {
            ((ServerPlayer) player).connection.send(new ClientboundCustomPayloadPacket(packet));
        }
    }

    private PktMachineStatePayload machineStatePayload(boolean activeNow) {
        var thread = hasFactoryController() ? factoryThreadSnapshots().getFirst() : null;
        boolean recipeLocked = thread == null ? lockedRecipeId != null : thread.locked();
        String lockedRecipe = thread == null ? lockedRecipeId == null ? "" : lockedRecipeId.toString() : thread.lockedRecipeId();
        Identifier machineId = machineId();
        String machineIdValue = machineId == null ? "" : machineId.toString();
        int controllerRole = MachineControllerMenu.controllerRoleSyncValue(this);
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        int installedModuleCount = runtimeState.installedModuleCount();
        ModuleConnectionStatus moduleStatus = runtimeState.moduleConnectionStatus();
        boolean moduleConnected = moduleStatus.connected();
        String connectedHostIdValue = moduleConnected ? moduleStatus.connectedHostId().toString() : "";
        return machineStatePayload(activeNow, runtimeState.structure().formed(), recipeLocked, lockedRecipe, machineIdValue, controllerRole,
                installedModuleCount, moduleConnected, connectedHostIdValue);
    }

    private PktMachineStatePayload machineStatePayload(boolean activeNow, boolean formed, boolean recipeLocked, String lockedRecipe,
                                                       String machineId, int controllerRole, int installedModuleCount,
                                                       boolean moduleConnected, String connectedHostId) {
        String name = runtime.craftingRuntime().recipe() == null ? "" : runtime.craftingRuntime().recipe().id().toString();
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        var crafting = runtimeState.crafting();
        return new PktMachineStatePayload(getBlockPos(), name, formed, activeNow,
                runtimeState.foundLevels().values().stream().map(foundLevel -> foundLevel.id().toString()).toList(),
                recipeLocked, lockedRecipe,
                machineId, controllerRole, installedModuleCount, moduleConnected, connectedHostId,
                crafting.status().getStatus(), crafting.status().getUnlocMessage(), crafting.failure(),
                runtimeState.structure().structureAreaLoaded());
    }

    private boolean tryStartNewRecipe() {
        if (!shouldSearchRecipe()) return false;
        recipeSearchAttemptCounter++;
        Machine matchedMachine = runtimeSnapshot().structure().machine();
        Identifier machineId = matchedMachine == null ? null : matchedMachine.registryName();
        if (machineId == null) return false;
        if (tryRestartLastRecipe(machineId)) return true;
        List<MachineRecipe> candidates = recipesForMachine();
        int maxParallelism = getMaxParallelism();
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(runtimeSnapshot(), machineId, runtimeSnapshot().structure().version(),
                    maxParallelism, candidates, lockedRecipeId).compute();
        } catch (RuntimeException e) {
            LOG.warn("[Ctrl#{}] tryStartNewRecipe: recipe search failed at pos={}; retrying later", instanceId, getBlockPos(), e);
            clearPendingConflictStart();
            recipeSearchRetryCounter++;
            lastFailureUnloc = "gui.mmcr.controller.failure.recipe_search_exception";
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
    }

    private boolean applySearchResult(RecipeSearchResult result, int candidateCount) {
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
        rememberLastRecipe(next);
        recipeSearchRetryCounter = 0;
        lastFailureUnloc = null;
        recipeFailure = null;
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
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        Machine matchedMachine = runtimeState.structure().machine();
        return runtimeState.structure().formed()
                && matchedMachine != null
                && matchedMachine.registryName().equals(result.machineId())
                && runtimeState.structure().version() == result.structureVersion()
                && runtimeState.capabilityVersion() == result.capabilityVersion()
                && runtimeState.modifierVersion() == result.modifierVersion()
                && !runtime.craftingRuntime().active();
    }

    private boolean tryRestartLastRecipe(Identifier machineId) {
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        if (recipeDirty || lastRecipe == null || runtime.craftingRuntime().active() || runtimeState.structure().machine() == null) return false;
        if (!machineId.equals(lastRecipe.machineId())) return false;
        if (lockedRecipeId != null && !lockedRecipeId.equals(lastRecipe.id())) return false;
        if (lastRecipeStructureVersion != runtimeState.structure().version()
                || lastRecipeCapabilityVersion != runtimeState.capabilityVersion()
                || lastRecipeModifierSnapshotVersion != runtimeState.modifierVersion()) return false;
        if (usesSharedIoCoordinator()) {
            requestSharedStart(lastRecipe);
            return true;
        }
        CraftingStatus state = runtime.craftingRuntime().start(lastRecipe, getMaxParallelism());
        if (!state.isCrafting()) {
            recipeDirty = true;
            lastFailureUnloc = runtime.craftingRuntime().failureUnloc();
            return false;
        }
        setActiveState(true);
        syncRuntimeStateIfChanged();
        recipeSearchRetryCounter = 0;
        lastFailureUnloc = null;
        recipeFailure = null;
        setChanged();
        return true;
    }

    private void rememberLastRecipe(MachineRecipe recipe) {
        lastRecipe = recipe;
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        lastRecipeStructureVersion = runtimeState.structure().version();
        lastRecipeCapabilityVersion = runtimeState.capabilityVersion();
        lastRecipeModifierSnapshotVersion = runtimeState.modifierVersion();
        recipeDirty = false;
    }

    private boolean isSearchResultCurrentForFactory(RecipeSearchResult result) {
        ControllerRuntimeSnapshot runtimeState = runtimeSnapshot();
        Machine matchedMachine = runtimeState.structure().machine();
        return runtimeState.structure().formed()
                && matchedMachine != null
                && matchedMachine.registryName().equals(result.machineId())
                && runtimeState.structure().version() == result.structureVersion()
                && runtimeState.capabilityVersion() == result.capabilityVersion()
                && runtimeState.modifierVersion() == result.modifierVersion();
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
                recipeFailure = null;
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
        ControllerRuntimeSnapshot snapshot = runtimeSnapshot();
        sharedStartPending = true;
        pendingSharedStartRecipe = next;
        pendingSharedStartDomain = domain;
        pendingSharedStartStructureVersion = snapshot.structure().version();
        pendingSharedStartCapabilityVersion = snapshot.capabilityVersion();
        pendingSharedStartModifierVersion = snapshot.modifierVersion();
        long token = ++nextSharedStartToken;
        pendingSharedStartToken = token;
        long runtimeStructureVersion = snapshot.structure().version();
        SharedIoCoordinator.get(serverLevel).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), runtimeStructureVersion,
                getMaxParallelism(),
                requested -> {
                    if (!isPendingSharedStart(token, next, domain) || runtime.craftingRuntime().active()) return 0;
                    CraftingStatus state = runtime.craftingRuntime().start(next, requested);
                    if (!state.isCrafting()) {
                        clearPendingSharedStart();
                        recipeSearchRetryCounter++;
                        lastFailureUnloc = runtime.craftingRuntime().failureUnloc();
                        return 0;
                    }
                    return runtime.craftingRuntime().parallelism();
                },
                granted -> {
                    if (!isPendingSharedStart(token, next, domain) || !runtime.craftingRuntime().active()) return;
                    clearPendingSharedStart();
                    setActiveState(true);
                    syncRuntimeStateIfChanged();
                    rememberLastRecipe(next);
                    recipeSearchRetryCounter = 0;
                    lastFailureUnloc = null;
                    setChanged();
                },
                  () -> isPendingSharedStart(token, next, domain),
                  () -> runtimeSnapshot().structure().version()
        ));
    }

    private boolean isPendingSharedStart(long token, MachineRecipe next,
                                         StructureClaimRegistry.ResourceDomain domain) {
        if (!sharedStartPending || pendingSharedStartToken != token || pendingSharedStartRecipe != next
                || pendingSharedStartDomain == null || !pendingSharedStartDomain.equals(domain)) return false;
        if (!isCurrentSharedDomain(domain)) {
            clearPendingSharedStart();
            return false;
        }
        ControllerRuntimeSnapshot snapshot = runtimeSnapshot();
        if (snapshot.structure().version() != pendingSharedStartStructureVersion
                || snapshot.capabilityVersion() != pendingSharedStartCapabilityVersion
                || snapshot.modifierVersion() != pendingSharedStartModifierVersion) {
            clearPendingSharedStart();
            recipeSearchRetryCounter++;
            return false;
        }
        return true;
    }

    private void clearPendingSharedStart() {
        sharedStartPending = false;
        pendingSharedStartRecipe = null;
        pendingSharedStartDomain = null;
        pendingSharedStartStructureVersion = Long.MIN_VALUE;
        pendingSharedStartCapabilityVersion = Long.MIN_VALUE;
        pendingSharedStartModifierVersion = Long.MIN_VALUE;
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
        long runtimeStructureVersion = runtimeSnapshot().structure().version();
        SharedIoCoordinator.get(serverLevel).enqueue(new SharedIoCoordinator.TickRequest(
                domain, new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), runtimeStructureVersion,
                () -> {
                    if (!validateSharedRuntime(token, domain)) return false;
                    boolean wasActive = runtime.craftingRuntime().active();
                    runtime.craftingRuntime().tick();
                    if (runtime.craftingRuntime().finishPending()) {
                        requestSharedFinish(serverLevel, domain, token);
                        return true;
                    }
                    completeSharedRuntime(wasActive);
                    return true;
                },
                 () -> validateSharedRuntime(token, domain),
                 () -> runtimeSnapshot().structure().version()
        ));
    }

    private void requestSharedFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token) {
        long runtimeStructureVersion = runtimeSnapshot().structure().version();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.FinishRequest(
                domain, new SharedIoCoordinator.LaneKey(getBlockPos(), "base"), runtimeStructureVersion,
                () -> {
                    if (!validateSharedRuntime(token, domain)) return false;
                    boolean wasActive = runtime.craftingRuntime().active();
                    runtime.craftingRuntime().finish();
                    completeSharedRuntime(wasActive);
                    return true;
                },
                 () -> validateSharedRuntime(token, domain),
                 () -> runtimeSnapshot().structure().version()
        ));
    }

    private boolean isCurrentSharedRuntime(StructureClaimRegistry.ResourceDomain domain) {
        return runtime.craftingRuntime().active() && runtime.craftingRuntime().versionsCurrent()
                && isCurrentSharedDomain(domain);
    }

    private boolean validateSharedRuntime(long token, @Nullable StructureClaimRegistry.ResourceDomain domain) {
        if (!sharedTickPending || pendingSharedTickToken != token) return false;
        if (isCurrentSharedRuntime(domain)) return true;
        if (pendingSharedTickDomain == null || pendingSharedTickDomain.equals(domain)) {
            if (runtime.craftingRuntime().active()) {
                if (runtime.craftingRuntime().versionsCurrent()) runtime.craftingRuntime().invalidate();
                else runtime.craftingRuntime().tick();
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
        lastFailureUnloc = runtime.craftingRuntime().failureUnloc();
        if (wasActive && !runtime.craftingRuntime().active()) {
            boolean finished = runtime.craftingRuntime().failure() == null;
            lastFailureUnloc = finished ? null : runtime.craftingRuntime().failureUnloc();
            if (finished) playFinishSound();
            setActiveState(false);
            syncRuntimeStateIfChanged();
        }
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

    private void setActiveState(boolean activeState) {
        if (level == null || level.isClientSide() || isRemoved()) return;
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != activeState) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, activeState), 3);
        }
    }

    @Override
    public void setRemoved() {
        invalidateStructureScan(StructureMatcher.InvalidationReason.REMOVED);
        if (level != null && !level.isClientSide()) cancelBuildTask();
        structureCheckIntervalOverrideForTesting = null;
        structureScanBatchesOverrideForTesting = null;
        buildBlocksPerTickOverrideForTesting = null;
        super.setRemoved();
        if (level != null && !level.isClientSide()) resetMachine(true, false);
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
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        Identifier machineId = configuredMachine == null ? null : configuredMachine.registryName();
        if (machineId == null) return List.of();
        int datapackCount = datapackRecipeCount();
        long contentVersion = RuntimeContentVersion.current();
        if (machineId.equals(cachedCandidatesMachineId)
                && cachedCandidatesReloadVersion == contentVersion
                && cachedDatapackRecipeCount == datapackCount) {
            return cachedCandidates;
        }
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        for (MachineRecipe recipe : RecipeRegistry.byMachine(configuredMachine)) {
            recipes.put(recipe.id(), recipe);
        }
        if (level instanceof ServerLevel sl) {
            for (RecipeHolder<?> holder : sl.recipeAccess().getRecipes()) {
                if (holder.value() instanceof MachineRecipe recipe
                        && recipe.machineId().equals(configuredMachine.registryName())) {
                    Identifier holderId = holder.id().identifier();
                    MachineRecipe holderRecipe = holderId.equals(recipe.id()) ? recipe : recipe.withId(holderId);
                    recipes.putIfAbsent(holderId, holderRecipe);
                }
            }
        }
        cachedCandidatesMachineId = machineId;
        cachedCandidatesReloadVersion = contentVersion;
        cachedDatapackRecipeCount = datapackCount;
        cachedCandidates = List.copyOf(recipes.values());
        return cachedCandidates;
    }

    private int datapackRecipeCount() {
        if (!(level instanceof ServerLevel sl)) return 0;
        Machine configuredMachine = runtimeSnapshot().structure().configuredMachine();
        int count = 0;
        for (RecipeHolder<?> holder : sl.recipeAccess().getRecipes()) {
            if (holder.value() instanceof MachineRecipe recipe
                    && configuredMachine != null
                    && recipe.machineId().equals(configuredMachine.registryName())) {
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("matched_structure_stage", runtimeSnapshot().structure().matchedStage());
        ValueOutput.TypedOutputList<String> levels = output.list("found_levels", Codec.STRING);
        for (MachineLevel foundLevel : runtimeSnapshot().foundLevels().values()) {
            levels.add(foundLevel.id().toString());
        }
        if (runtime.craftingRuntime().active()) {
            output.putString("recipe_state", "active");
            output.putBoolean("has_active_runtime", true);
            runtime.craftingRuntime().save(output.child("crafting_runtime"));
        }
        if (lockedRecipeId != null) output.putString("locked_recipe", lockedRecipeId.toString());
        if (hasFactoryController()) runtime.factoryRuntime().save(output.child("factory_runtime"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            pendingFactoryRuntimeInput = input.childOrEmpty("factory_runtime");
            StructureSnapshot currentStructure = runtimeSnapshot().structure();
            runtime.publishStructureState(currentStructure.structureAreaLoaded(), currentStructure.formed(),
                    currentStructure.configuredMachine(), Math.max(0, input.getIntOr("matched_structure_stage", 0)));
            runtime.requestStructureCheck();
            setChanged();
            redstonePaused = false;
            lockedRecipeId = null;
            runtime.craftingRuntime().invalidate();
            Map<Identifier, MachineLevel> restoredLevels = new LinkedHashMap<>();
            input.listOrEmpty("found_levels", Codec.STRING).forEach(id -> {
                MachineLevel foundLevel = MachineLevelRegistry.getLevel(Identifier.parse(id));
                if (foundLevel != null) restoredLevels.put(foundLevel.typeId(), foundLevel);
            });
            ControllerRuntimeSnapshot current = runtimeSnapshot();
            runtime.publishComponentState(current.components(), current.foundModifiers(), restoredLevels,
                    current.linkedPortPositions());
            lastFailureUnloc = null;
            String lockedRecipeName = input.getStringOr("locked_recipe", "");
            if (!lockedRecipeName.isEmpty()) {
                Identifier restoredLock = Identifier.parse(lockedRecipeName);
                if (RecipeRegistry.getRecipe(restoredLock) != null) lockedRecipeId = restoredLock;
            }
            if (input.getBooleanOr("has_active_runtime", false)) {
                runtime.craftingRuntime().load(input.childOrEmpty("crafting_runtime"), resourceDomain());
            }
            runtime.requestStructureCheck();
            setChanged();
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
