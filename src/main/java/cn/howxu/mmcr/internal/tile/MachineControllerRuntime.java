package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.controller.ControllerScreenText;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.controller.JadeText;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehaviorContext;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.JadeTextSnapshot;
import cn.howxu.mmcr.internal.runtime.JadeTextSupport;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the authoritative runtime state and publishes immutable controller snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerRuntime {
    private final MachineControllerBlockEntity controller;
    private final StructureRuntime structure;
    private final ComponentRuntime components = new ComponentRuntime();
    private final CraftingRuntime craftingRuntime;
    private final FactoryRuntime factoryRuntime;
    private final ControllerScreenTextState screenText = new ControllerScreenTextState();
    private final JadeText jadeText = JadeTextSupport.create();
    private final Map<String, ControllerScreenTextState> recipeScreenTexts = new LinkedHashMap<>();
    private Map<BlockPos, DataStorage> dataStorages = Map.of();
    private @Nullable DataStorage primaryDataStorage;
    private Map<String, DataValue> clientDataStorageValues = Map.of();
    private long dataStorageStateEpoch;
    private long workingDataStorageStateEpoch = Long.MIN_VALUE;
    private long publishedDataStorageStateEpoch = Long.MIN_VALUE;
    private CraftingStateSnapshot craftingState = CraftingStateSnapshot.empty(0L, 0L, 0L);
    private ControllerRuntimeSnapshot publishedSnapshot;
    private @Nullable ControllerRuntimeSnapshot workingSnapshot;
    private @Nullable ResourceLocation workingMachineId;
    private @Nullable ResourceLocation publishedMachineId;
    private long workingStructureEpoch = Long.MIN_VALUE;
    private long workingCapabilityVersion = Long.MIN_VALUE;
    private long workingCapabilityPresentationEpoch = Long.MIN_VALUE;
    private long workingModifierVersion = Long.MIN_VALUE;
    private long workingComponentStateVersion = Long.MIN_VALUE;
    private long workingFactoryEpoch = Long.MIN_VALUE;
    private long workingCraftingEpoch = Long.MIN_VALUE;
    private int snapshotBatchDepth;
    private boolean snapshotDirty = true;
    private long publishedStructureEpoch = Long.MIN_VALUE;
    private long publishedCapabilityVersion = Long.MIN_VALUE;
    private long publishedCapabilityPresentationEpoch = Long.MIN_VALUE;
    private long publishedModifierVersion = Long.MIN_VALUE;
    private long publishedComponentStateVersion = Long.MIN_VALUE;
    private long publishedFactoryEpoch = Long.MIN_VALUE;
    private long craftingStateEpoch;
    private long publishedCraftingEpoch = Long.MIN_VALUE;
    private long cachedFoundLevelEpoch = Long.MIN_VALUE;
    private List<String> cachedFoundLevelIds = List.of();
    private int snapshotBuildCountForTesting;
    private int behaviorContextBuildCountForTesting;

    MachineControllerRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.structure = new StructureRuntime(controller);
        this.craftingRuntime = new CraftingRuntime(controller, components);
        this.factoryRuntime = new FactoryRuntime();
        publishSnapshot();
    }

    public void serverTick(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            throw new IllegalArgumentException("Controller runtime tick requires a level and controller position");
        }
        if (controller.getLevel() != null && controller.getLevel() != level) {
            throw new IllegalArgumentException("Controller runtime level does not match the controller");
        }
        if (controller.getBlockPos() != null && !controller.getBlockPos().equals(controllerPos)) {
            throw new IllegalArgumentException("Controller runtime position does not match the controller");
        }
        beginUpdateBatch();
        try {
            controller.flushPendingStructureChanges();
            structure.tick(level, controllerPos);
            controller.tickRuntimeWork(level, controllerPos);
        } finally {
            endUpdateBatch();
        }
    }

    public ControllerRuntimeSnapshot snapshot() {
        return publishedSnapshot;
    }

    public ControllerScreenTextState screenText() {
        return screenText;
    }

    public JadeTextSnapshot jadeTextSnapshot() {
        return JadeTextSupport.snapshot(jadeText);
    }

    public ControllerRuntimeContext runtimeContext() {
        Machine configuredMachine = structure.snapshot().configuredMachine();
        if (configuredMachine == null) {
            throw new IllegalStateException("Controller runtime context requires a configured machine");
        }
        return new ControllerRuntimeContext(configuredMachine.registryName(), controller.getBlockPos(), screenText);
    }

    public MachineBehaviorContext behaviorContext() {
        return behaviorContext(new CapabilitySnapshot(components.capabilities()));
    }

    public MachineBehaviorContext behaviorContext(ControllerScreenText recipeScreenText) {
        return behaviorContext(new CapabilitySnapshot(components.capabilities()), recipeScreenText);
    }

    public TickBehaviorContext tickBehaviorContext() {
        CapabilitySnapshot capabilitySnapshot = new CapabilitySnapshot(components.capabilities());
        StructureSnapshot structureSnapshot = structure.snapshot();
        Machine machine = structureSnapshot.machine() == null
                ? structureSnapshot.configuredMachine() : structureSnapshot.machine();
        int factoryThreadCount = machine == null || !machine.hasFactory()
                ? 1 : controller.effectiveFactoryThreadLimit();
        long parallelism = components.maxParallelism(machine);
        return new TickBehaviorContext(behaviorContext(capabilitySnapshot), capabilitySnapshot,
                factoryThreadCount, parallelism);
    }

    MachineBehaviorContext behaviorContext(CapabilitySnapshot capabilitySnapshot) {
        return behaviorContext(capabilitySnapshot, screenText);
    }

    MachineBehaviorContext behaviorContext(CapabilitySnapshot capabilitySnapshot,
                                           ControllerScreenText screenText) {
        StructureSnapshot snapshot = structure.snapshot();
        Machine machine = snapshot.machine() == null ? snapshot.configuredMachine() : snapshot.machine();
        if (machine == null) throw new IllegalStateException("Machine behavior context requires a configured machine");
        Level currentLevel = controller.getLevel();
        ServerLevel level = currentLevel instanceof ServerLevel serverLevel ? serverLevel : null;
        long gameTime = currentLevel == null ? 0L : currentLevel.getGameTime();
        behaviorContextBuildCountForTesting++;
        return new MachineBehaviorContext(controller, level, controller.getBlockPos(), machine.registryName(), gameTime,
                screenText, primaryDataStorage,
                new MachineIoView(capabilitySnapshot), components.upgradeItems(), jadeText);
    }

    int behaviorContextBuildCountForTesting() {
        return behaviorContextBuildCountForTesting;
    }

    public ControllerScreenTextState recipeScreenText(String laneId) {
        Objects.requireNonNull(laneId, "laneId");
        return recipeScreenTexts.computeIfAbsent(laneId, ignored -> new ControllerScreenTextState());
    }

    public void clearRecipeScreenText(String laneId) {
        ControllerScreenTextState state = recipeScreenTexts.get(laneId);
        if (state == null) return;
        state.clear(ControllerScreenTextScope.CONTROLLER);
        state.clear(ControllerScreenTextScope.OPERATION);
    }

    public void clearOperationText() {
        screenText.clear(ControllerScreenTextScope.OPERATION);
        recipeScreenTexts.values().forEach(state -> state.clear(ControllerScreenTextScope.OPERATION));
    }

    public void clearAllText() {
        screenText.clear(ControllerScreenTextScope.CONTROLLER);
        screenText.clear(ControllerScreenTextScope.OPERATION);
        recipeScreenTexts.values().forEach(state -> {
            state.clear(ControllerScreenTextScope.CONTROLLER);
            state.clear(ControllerScreenTextScope.OPERATION);
        });
        jadeText.clear();
    }

    ControllerRuntimeSnapshot currentSnapshot() {
        if (workingSnapshot != null && workingEpochsUnchanged()) return workingSnapshot;
        StructureSnapshot structureSnapshot = structure.snapshot();
        FactorySnapshot factorySnapshot = factoryRuntime.snapshot();
        ControllerRecipePresentation recipePresentation = craftingRuntime.recipePresentation();
        Machine machine = structureSnapshot.machine() != null ? structureSnapshot.machine() : structureSnapshot.configuredMachine();
        long maxParallelism = components.maxParallelism(machine);
        if (workingSnapshot != null && workingStaticStateUnchanged()) {
            workingSnapshot = workingSnapshot.withRuntimeState(craftingState, factorySnapshot,
                    components.capabilityPresentations(), maxParallelism, recipePresentation);
        } else {
            boolean recipeBehavior = machine != null && machine.behavior() instanceof RecipeBehavior;
            boolean factorySupported = recipeBehavior && machine.hasFactory();
            boolean factoryControllerPresent = false;
            int parallelControllerCount = 0;
            for (ProcessingComponent component : components.components()) {
                if (component.getContainer() instanceof FactorySchedulerBlockEntity) factoryControllerPresent = factorySupported;
                if (component.getContainer() instanceof ParallelControllerBlockEntity) parallelControllerCount++;
            }
            long maxParallelControllerCount = machine != null && machine.parallelizable()
                    ? Math.max(1L, machine.maxParallelism()) : 0L;
            int controllerRole = machine == null ? 0 : machine.isHost() ? 1 : machine.isModule() ? 2 : 0;
            ResourceLocation machineId = machine == null ? null : machine.registryName();
            workingSnapshot = new ControllerRuntimeSnapshot(structureSnapshot, components.capabilityVersion(),
                    components.modifierVersion(), components.stateVersion(), components.foundModifiers(), components.foundLevels(),
                    components.linkedPortPositions(), components.moduleConnectionStatus(), components.installedModuleCount(),
                    craftingState, factorySnapshot, components.componentPresentations(),
                    components.capabilityPresentations(), foundLevelIds(), machine == null ? "" : machineId.toString(),
                     machine == null ? "" : machine.displayNameKey(), controllerRole, factorySupported, factoryControllerPresent,
                     parallelControllerCount, maxParallelControllerCount, maxParallelism,
                     components.upgradeItems(), components.upgradeContentRevision(), currentDataStorageValues(),
                     recipePresentation);
            workingMachineId = machineId;
        }
        workingStructureEpoch = structure.stateEpoch();
        workingCapabilityVersion = components.capabilityVersion();
        workingCapabilityPresentationEpoch = components.capabilityPresentationEpoch();
        workingModifierVersion = components.modifierVersion();
        workingComponentStateVersion = components.stateVersion();
        workingFactoryEpoch = factoryRuntime.stateEpoch();
        workingCraftingEpoch = craftingStateEpoch;
        workingDataStorageStateEpoch = dataStorageStateEpoch;
        return workingSnapshot;
    }

    private Map<String, DataValue> currentDataStorageValues() {
        if (controller.getLevel() != null && controller.getLevel().isClientSide()) {
            return clientDataStorageValues;
        }
        return primaryDataStorage == null ? Map.of() : primaryDataStorage.values();
    }

    StructureSnapshot currentStructureSnapshot() {
        return structure.snapshot();
    }

    public void beginUpdateBatch() {
        if (snapshotBatchDepth++ == 0) snapshotBuildCountForTesting = 0;
    }

    public void endUpdateBatch() {
        if (snapshotBatchDepth <= 0) throw new IllegalStateException("No controller runtime update batch is active");
        if (--snapshotBatchDepth == 0) {
            try {
                publishSnapshot();
                controller.publishRuntimeStateAfterSnapshotBatch();
            } finally {
                controller.flushRuntimePersistenceChanges();
            }
        }
    }

    boolean updateBatchActive() {
        return snapshotBatchDepth > 0;
    }

    int snapshotBuildCountForTesting() {
        return snapshotBuildCountForTesting;
    }

    boolean structureStateChangedSincePublication() {
        return publishedStructureEpoch != structure.stateEpoch();
    }

    void publishSnapshot() {
        controller.ensureFactoryRuntimeLoaded();
        if (controller.getLevel() == null || !controller.getLevel().isClientSide()) refreshCraftingStateFromRuntime();
        if (!snapshotDirty && epochsUnchanged()) return;
        if (snapshotBatchDepth > 0) {
            snapshotDirty = true;
            return;
        }
        flushSnapshot();
    }

    private void flushSnapshot() {
        publishedSnapshot = currentSnapshot();
        publishedMachineId = workingMachineId;
        publishedStructureEpoch = structure.stateEpoch();
        publishedCapabilityVersion = components.capabilityVersion();
        publishedCapabilityPresentationEpoch = components.capabilityPresentationEpoch();
        publishedModifierVersion = components.modifierVersion();
        publishedComponentStateVersion = components.stateVersion();
        publishedFactoryEpoch = factoryRuntime.stateEpoch();
        publishedCraftingEpoch = craftingStateEpoch;
        publishedDataStorageStateEpoch = dataStorageStateEpoch;
        snapshotDirty = false;
        snapshotBuildCountForTesting++;
    }

    private boolean workingEpochsUnchanged() {
        return workingStructureEpoch == structure.stateEpoch()
                && workingCapabilityVersion == components.capabilityVersion()
                && workingCapabilityPresentationEpoch == components.capabilityPresentationEpoch()
                && workingModifierVersion == components.modifierVersion()
                && workingComponentStateVersion == components.stateVersion()
                && workingFactoryEpoch == factoryRuntime.stateEpoch()
                && workingCraftingEpoch == craftingStateEpoch
                && workingDataStorageStateEpoch == dataStorageStateEpoch
                && workingSnapshot.capabilityPresentations() == components.capabilityPresentations()
                && workingSnapshot.upgradeContentRevision() == components.upgradeContentRevision()
                && machineStaticStateUnchanged(workingSnapshot, workingMachineId)
                && workingSnapshot.recipePresentation() == craftingRuntime.recipePresentation()
                && workingSnapshot.maxParallelism() == components.maxParallelism(workingSnapshot.structure().machine() != null
                        ? workingSnapshot.structure().machine() : workingSnapshot.structure().configuredMachine());
    }

    private boolean workingStaticStateUnchanged() {
        return workingStructureEpoch == structure.stateEpoch()
                && workingCapabilityVersion == components.capabilityVersion()
                && workingModifierVersion == components.modifierVersion()
                && workingComponentStateVersion == components.stateVersion()
                && workingDataStorageStateEpoch == dataStorageStateEpoch
                && workingSnapshot.upgradeContentRevision() == components.upgradeContentRevision()
                && machineStaticStateUnchanged(workingSnapshot, workingMachineId);
    }

    private boolean machineStaticStateUnchanged(ControllerRuntimeSnapshot snapshot, @Nullable ResourceLocation machineId) {
        StructureSnapshot current = structure.snapshot();
        Machine machine = current.machine() != null ? current.machine() : current.configuredMachine();
        Machine previous = snapshot.structure().machine() != null
                ? snapshot.structure().machine() : snapshot.structure().configuredMachine();
        return machine == previous
                && Objects.equals(machineId, machine == null ? null : machine.registryName())
                && Objects.equals(snapshot.machineName(), machine == null ? "" : machine.displayNameKey())
                && snapshot.factorySupported() == (machine != null && machine.behavior() instanceof RecipeBehavior && machine.hasFactory())
                && snapshot.controllerRole() == (machine == null ? 0 : machine.isHost() ? 1 : machine.isModule() ? 2 : 0)
                && snapshot.maxParallelControllerCount() == (machine != null && machine.parallelizable()
                        ? Math.max(1L, machine.maxParallelism()) : 0L);
    }

    private boolean epochsUnchanged() {
        return publishedStructureEpoch == structure.stateEpoch()
                && publishedCapabilityVersion == components.capabilityVersion()
                && publishedCapabilityPresentationEpoch == components.capabilityPresentationEpoch()
                && publishedModifierVersion == components.modifierVersion()
                && publishedComponentStateVersion == components.stateVersion()
                && publishedFactoryEpoch == factoryRuntime.stateEpoch()
                && publishedCraftingEpoch == craftingStateEpoch
                && publishedDataStorageStateEpoch == dataStorageStateEpoch
                && publishedSnapshot != null
                && publishedSnapshot.capabilityPresentations() == components.capabilityPresentations()
                && publishedSnapshot.upgradeContentRevision() == components.upgradeContentRevision()
                && machineStaticStateUnchanged(publishedSnapshot, publishedMachineId)
                && publishedSnapshot.recipePresentation() == craftingRuntime.recipePresentation()
                && publishedSnapshot.maxParallelism() == components.maxParallelism(publishedSnapshot.structure().machine() != null
                        ? publishedSnapshot.structure().machine() : publishedSnapshot.structure().configuredMachine());
    }

    private void updateCraftingState(CraftingStateSnapshot nextCrafting) {
        if (Objects.equals(craftingState, nextCrafting)) return;
        craftingState = nextCrafting;
        craftingStateEpoch++;
        snapshotDirty = true;
    }

    private CraftingStateSnapshot currentCraftingState() {
        CraftingStateSnapshot current = craftingRuntime.snapshot();
        return new CraftingStateSnapshot(current.recipeId(), current.status(), current.failure(), structure.version(),
                components.capabilityVersion(), components.modifierVersion(), current.tick(), current.totalTick(),
                current.parallelism(), current.maxParallelism());
    }

    private void refreshCraftingStateFromRuntime() {
        CraftingStateSnapshot current = craftingRuntime.snapshot();
        boolean contentChanged = !Objects.equals(craftingState.recipeId(), current.recipeId())
                || !Objects.equals(craftingState.failure(), current.failure())
                || craftingState.tick() != current.tick()
                || craftingState.totalTick() != current.totalTick()
                || craftingState.parallelism() != current.parallelism()
                || craftingState.maxParallelism() != current.maxParallelism()
                || (current.recipeId() != null || current.failure() != null)
                && !Objects.equals(craftingState.status(), current.status());
        if (contentChanged) updateCraftingState(currentCraftingState());
    }

    void refreshCraftingState() {
        refreshCraftingStateFromRuntime();
        publishSnapshot();
    }

    private List<String> foundLevelIds() {
        long epoch = components.levelVersion();
        if (cachedFoundLevelEpoch == epoch) return cachedFoundLevelIds;
        cachedFoundLevelIds = components.foundLevels().values().stream()
                .map(level -> level.id().toString()).toList();
        cachedFoundLevelEpoch = epoch;
        return cachedFoundLevelIds;
    }

    List<ProcessingComponent> components() {
        return components.components();
    }

    Set<BlockPos> upgradeBusPositions() {
        return components.upgradeBusPositions();
    }

    ComponentRuntime componentRuntime() {
        return components;
    }

    public CraftingRuntime craftingRuntime() {
        return craftingRuntime;
    }

    public FactoryRuntime factoryRuntime() {
        return factoryRuntime;
    }

    void pauseCrafting() {
        craftingRuntime.pause();
        factoryRuntime.pause();
    }

    void resumeCrafting() {
        craftingRuntime.resume();
        factoryRuntime.resume();
    }

    void publishStructureState(boolean structureAreaLoaded, boolean formed,
                               @Nullable Machine configuredMachine, int matchedStage) {
        structure.setStructureAreaLoaded(structureAreaLoaded);
        structure.setFormed(formed);
        structure.setMachine(configuredMachine);
        structure.setMatchedStructureStage(matchedStage);
        publishSnapshot();
    }

    void publishRuntimeState(boolean structureAreaLoaded, boolean formed,
                             @Nullable Machine configuredMachine, int matchedStage,
                             @Nullable ResourceLocation recipeId, CraftingStatus status,
                             @Nullable ExecutionStatus failure, int tick, int totalTick,
                               long parallelism, long maxParallelism) {
        structure.setStructureAreaLoaded(structureAreaLoaded);
        structure.setFormed(formed);
        structure.setMachine(configuredMachine);
        structure.setMatchedStructureStage(matchedStage);

        CraftingStateSnapshot nextCrafting = new CraftingStateSnapshot(recipeId, status, failure,
                structure.version(), components.capabilityVersion(), components.modifierVersion(),
                tick, totalTick, parallelism, maxParallelism);
        updateCraftingState(nextCrafting);
        publishSnapshot();
    }

    void requestStructureCheck() {
        structure.requestCheck();
    }

    void requestStructureCheck(StructureRuntime.CheckReason reason) {
        structure.requestCheck(reason);
    }

    long structureChunkStateEpoch() {
        return structure.chunkStateEpoch();
    }

    void markStructureChunkStateChanged() {
        structure.markChunkStateChanged();
    }

    void restoreStructureVersion(long version) {
        structure.restoreVersion(version);
    }

    void onStructureBlockChanged(BlockPos changedPos) {
        structure.onBlockChanged(changedPos);
    }

    void onStructureChunkChanged(ServerLevel level, BlockPos controllerPos) {
        structure.onChunkStateChanged(level, controllerPos);
    }

    StructureRuntime.StructureWorkSnapshot structureWorkSnapshot() {
        return structure.workSnapshot();
    }

    void publishStructureWork(StructureRuntime.StructureWorkSnapshot state) {
        structure.publishWork(state);
    }

    void startStructureScan(StructureMatcher.ScanState scan, Machine scanMachine,
                            Object scanCandidate, long steppedTick, long startedTick) {
        structure.setScan(scan);
        structure.setScanMachine(scanMachine);
        structure.setScanCandidate(scanCandidate);
        structure.setScanSteppedTick(steppedTick);
        structure.setScanStartedTick(startedTick);
    }

    StructureMatcher.ScanResult stepStructureScan(ServerLevel level, BlockPos controllerPos) {
        return structure.stepScan(level, controllerPos);
    }

    StructureMatcher.ScanBatch captureStructureScan(ServerLevel level, BlockPos controllerPos) {
        return structure.captureScan(level, controllerPos);
    }

    void applyStructureScanResult(StructureMatcher.ScanIdentity identity, StructureMatcher.ScanResult result) {
        structure.applyScanResult(identity, result);
    }

    void clearStructureScan() {
        structure.clearScan();
    }

    void invalidateStructureScan(StructureMatcher.InvalidationReason reason) {
        structure.invalidateScan(reason);
    }

    boolean publishFormationState(Machine machine, BlockArray pattern,
                                  @Nullable CompiledMachinePattern compiledPattern,
                                  Direction facing, Direction rollFacing, int matchedStage) {
        boolean changed = structure.publishFormationState(machine, pattern, compiledPattern, facing, rollFacing, matchedStage,
                countStructureBlocks(pattern));
        publishSnapshot();
        return changed;
    }

    long countStructureBlocks(Block block) {
        return structure.countStructureBlocks(block);
    }

    private Map<Block, Long> countStructureBlocks(BlockArray pattern) {
        Level level = controller.getLevel();
        if (!(level instanceof ServerLevel)) return Map.of();
        Map<Block, Long> counts = new LinkedHashMap<>();
        for (BlockPos relativePos : pattern.pattern().keySet()) {
            Block block = level.getBlockState(controller.getBlockPos().offset(relativePos)).getBlock();
            counts.merge(block, 1L, Long::sum);
        }
        return Map.copyOf(counts);
    }

    boolean formationIdentityMatches(Machine machine, BlockArray pattern,
                                     @Nullable CompiledMachinePattern compiledPattern,
                                     Direction facing, Direction rollFacing, int matchedStage) {
        return structure.formationIdentityMatches(machine, pattern, compiledPattern, facing, rollFacing, matchedStage);
    }

    boolean publishClientStructureState(@Nullable Machine machine, boolean formed,
                                        boolean structureAreaLoaded) {
        boolean changed = structure.publishClientState(machine, formed, structureAreaLoaded);
        publishSnapshot();
        return changed;
    }

    void resetStructure(@Nullable Machine configuredMachine, boolean forceVersion) {
        dataStorages = Map.of();
        primaryDataStorage = null;
        structure.reset(configuredMachine, forceVersion);
        publishSnapshot();
    }

    void publishCriticalStructureChunks(Set<ChunkPos> criticalChunks) {
        structure.setCriticalChunks(criticalChunks);
        publishSnapshot();
    }

    long maxParallelism(@Nullable Machine machine) {
        return components.maxParallelism(machine);
    }

    void publishClientComponentState(Map<ResourceLocation, MachineLevel> levels,
                                     ModuleConnectionStatus status, int installedModuleCount) {
        components.replaceLevels(levels);
        components.replaceModuleConnectionState(status, installedModuleCount);
    }

    void publishClientDataStorageState(Map<String, DataValue> values) {
        Map<String, DataValue> next = Map.copyOf(values == null ? Map.of() : values);
        if (!clientDataStorageValues.equals(next)) {
            clientDataStorageValues = next;
            dataStorageStateEpoch++;
            snapshotDirty = true;
        }
        publishSnapshot();
    }

    void publishComponentState(List<ProcessingComponent> nextComponents,
                               Map<String, List<MachineModifier>> modifiers,
                               Map<ResourceLocation, MachineLevel> levels,
                               Set<BlockPos> linkedPositions) {
        components.replaceComponents(nextComponents);
        components.replaceModifiers(modifiers);
        components.replaceLevels(levels);
        components.replaceLinkedPortPositions(linkedPositions);
        publishSnapshot();
    }

    void publishUpgradeBusState(List<ComponentRuntime.UpgradeBusSnapshot> buses) {
        components.replaceUpgradeBuses(buses);
        publishSnapshot();
    }

    void refreshUpgradeBusState(List<ComponentRuntime.UpgradeBusSnapshot> buses) {
        components.refreshUpgradeBuses(buses);
        publishSnapshot();
    }

    void setModifiersAllowed(boolean allowed) {
        components.setModifiersAllowed(allowed);
        publishSnapshot();
    }

    void publishDataStorages(Map<BlockPos, DataStorage> nextDataStorages) {
        dataStorages = Map.copyOf(nextDataStorages == null ? Map.of() : nextDataStorages);
        DataStorage nextPrimaryDataStorage = dataStorages.isEmpty() ? null : dataStorages.values().iterator().next();
        if (primaryDataStorage != nextPrimaryDataStorage) {
            dataStorageStateEpoch++;
            snapshotDirty = true;
        }
        primaryDataStorage = nextPrimaryDataStorage;
        publishSnapshot();
    }

    void onDataStorageChanged(DataStorage storage) {
        if (storage == primaryDataStorage) {
            dataStorageStateEpoch++;
            snapshotDirty = true;
            publishSnapshot();
        }
    }

    Set<BlockPos> dataStoragePositions() {
        return dataStorages.keySet();
    }

    void publishModuleConnectionState(ModuleConnectionStatus status, int installedModuleCount) {
        components.replaceModuleConnectionState(status, installedModuleCount);
        publishSnapshot();
    }

    void refreshModuleConnectionState() {
        if (!(controller.getLevel() instanceof ServerLevel)) {
            publishModuleConnectionState(ModuleConnectionStatus.notRequired(), 0);
            return;
        }
        publishModuleConnectionState(ModuleConnectionCoordinator.connectionStatus(controller),
                ModuleConnectionCoordinator.installedModuleCount(controller));
    }

    void publishCraftingState(@Nullable ResourceLocation recipeId, CraftingStatus status,
                              @Nullable ExecutionStatus failure, int tick, int totalTick,
                              long parallelism, long maxParallelism) {
        CraftingStateSnapshot nextCrafting = new CraftingStateSnapshot(recipeId, status, failure,
                structure.version(), components.capabilityVersion(), components.modifierVersion(),
                tick, totalTick, parallelism, maxParallelism);
        updateCraftingState(nextCrafting);
        publishSnapshot();
    }

    void publishClientProgress(int tick, int totalTick) {
        updateCraftingState(new CraftingStateSnapshot(craftingState.recipeId(), craftingState.status(),
                craftingState.failure(), craftingState.structureVersion(), craftingState.capabilityVersion(),
                craftingState.modifierVersion(), tick, totalTick, craftingState.parallelism(), craftingState.maxParallelism()));
        publishSnapshot();
    }
}
