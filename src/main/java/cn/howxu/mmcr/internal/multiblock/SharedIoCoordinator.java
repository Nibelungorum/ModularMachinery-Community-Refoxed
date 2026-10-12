package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.VersionStamp;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.InstalledRecipeProgram;
import cn.howxu.mmcr.internal.runtime.RecipeCommitLedger;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState.Receipt;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState.Acknowledgement;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongUnaryOperator;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Resolves shared multiblock IO requests once at the end of each server-level tick.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SharedIoCoordinator {
    private static final Map<ServerLevel, SharedIoCoordinator> COORDINATORS = new WeakHashMap<>();
    private final NavigableMap<DomainKey, DomainBucket> domains = new TreeMap<>();
    private final Map<BlockPos, Set<Request>> requestsByController = new HashMap<>();
    private final Map<DomainKey, ArrayDeque<TickWorkEntry>> pendingTickWork = new HashMap<>();
    private final Map<Long, TickWorkset> submittedTickWork = new HashMap<>();
    private final Long2ObjectOpenHashMap<ControllerPermit> controllerPermits = new Long2ObjectOpenHashMap<>();
    private final Map<LaneKey, LaneRecords> executionRecords = new HashMap<>();
    private final Long2ObjectOpenHashMap<Map<String, LaneRecords>> laneBindings = new Long2ObjectOpenHashMap<>();
    private final Map<CraftingRuntime, ExecutionSlot> activeExecutions = new IdentityHashMap<>();
    private final Map<ExecutionIdentity, ExecutionSlot> receiptSlots = new HashMap<>();
    private final Set<LaneRecords> retirementCandidates = new HashSet<>();
    private final Long2ObjectOpenHashMap<Set<LaneRecords>> executionsByController = new Long2ObjectOpenHashMap<>();
    private final Map<BlockPos, Integer> activeProgramsByController = new HashMap<>();
    private final Map<BlockPos, Set<ExecutionSlot>> waitingByController = new HashMap<>();
    private final Set<BlockPos> plannedSynchronousControllers = new HashSet<>();
    private final NavigableMap<Long, Set<ExecutionSlot>> waitingExecutions = new TreeMap<>();
    private long installedAttempts;
    private boolean closed;
    private int synchronousGrants;
    private final int requestBudget;
    private final int worksetEntries;
    private long budgetGameTime = Long.MIN_VALUE;
    private int remainingRequests;
    private DomainKey domainCursor;
    private long nextTickWorksetId;
    private long executionRoundId;
    private long executionGameTime = Long.MIN_VALUE;
    private boolean executionRunning;
    private long schedulingRevision;
    private long postRound = Long.MIN_VALUE;
    private long postGameTime = Long.MIN_VALUE;
    private long postSchedulingRevision = Long.MIN_VALUE;
    private long postTopologyRevision = Long.MIN_VALUE;
    private final Set<DomainKey> changedExecutionDomains = new HashSet<>();
    private final Set<DomainKey> changedRequestDomains = new HashSet<>();
    private final Set<DomainKey> postDirtyDomains = new HashSet<>();
    private final Set<ExecutionSlot> dirtyTerminations = Collections.newSetFromMap(new IdentityHashMap<>());
    private final NavigableMap<DomainKey, DomainBucket> postCandidates = new TreeMap<>();
    private final Map<Long, StructureClaimRegistry.ResourceDomain> postKnownDomains = new HashMap<>();
    private long lastDrainRound = Long.MIN_VALUE;
    private long lastDrainTime = Long.MIN_VALUE;

    public SharedIoCoordinator() {
        this(ServerConfig.asyncSharedIoRequestsPerLevelTick(), ServerConfig.asyncSharedIoWorksetEntries());
    }

    SharedIoCoordinator(int requestBudget, int worksetEntries) {
        if (requestBudget < 1 || worksetEntries < 1) throw new IllegalArgumentException("Budgets must be positive");
        this.requestBudget = requestBudget;
        this.worksetEntries = worksetEntries;
        this.remainingRequests = requestBudget;
    }

    private SharedIoCoordinator(boolean production) {
        this.requestBudget = ServerConfig.asyncSharedIoRequestsPerLevelTick();
        this.worksetEntries = ServerConfig.asyncSharedIoWorksetEntries();
        this.remainingRequests = production ? 0 : requestBudget;
    }

    public static synchronized SharedIoCoordinator get(ServerLevel level) {
        return COORDINATORS.computeIfAbsent(level, ignored -> new SharedIoCoordinator(true));
    }

    public static synchronized void discard(ServerLevel level) {
        SharedIoCoordinator coordinator = COORDINATORS.remove(level);
        if (coordinator == null) return;
        coordinator.closed = true;
        for (ExecutionSlot slot : coordinator.activeExecutions.values()) slot.execution.cancel();
        coordinator.executionRecords.clear();
        coordinator.laneBindings.clear();
        coordinator.activeExecutions.clear();
        coordinator.receiptSlots.clear();
        coordinator.retirementCandidates.clear();
        coordinator.executionsByController.clear();
        coordinator.activeProgramsByController.clear();
        coordinator.waitingByController.clear();
        coordinator.plannedSynchronousControllers.clear();
        coordinator.waitingExecutions.clear();
        coordinator.domains.clear();
        coordinator.postCandidates.clear();
        coordinator.postKnownDomains.clear();
        coordinator.postDirtyDomains.clear();
        coordinator.changedExecutionDomains.clear();
        coordinator.changedRequestDomains.clear();
        coordinator.dirtyTerminations.clear();
        coordinator.controllerPermits.values().forEach(ControllerPermit::invalidate);
        coordinator.controllerPermits.clear();
        coordinator.executionRoundId = 0L;
        coordinator.executionGameTime = Long.MIN_VALUE;
        coordinator.executionRunning = false;
    }

    public long beginExecutionRound(long gameTime, boolean running) {
        long previousRound = executionRoundId;
        executionRoundId++;
        executionGameTime = gameTime;
        executionRunning = running;
        plannedSynchronousControllers.clear();
        synchronousGrants = 0;
        beginLevelTick(gameTime);
        if (running) {
            // Minecraft may advance gameTime after Pre. Readiness is provisional; actual time is gated below.
            wakeDueExecutions(gameTime + 1L);
            grantSynchronousRound(previousRound);
        }
        return executionRoundId;
    }

    /** Reserve a fair lane window before BE order can exhaust the shared budget. No resource is prepaid. */
    private void grantSynchronousRound(long previousRound) {
        NavigableMap<DomainKey, List<ExecutionSlot>> ready = new TreeMap<>();
        domains.forEach((key, bucket) -> {
            for (ExecutionSlot slot : bucket.installed.values()) {
                InstalledRecipeProgram execution = slot.execution;
                MachineControllerBlockEntity controller = execution.owner().controller();
                if (controller.activeWorkMode() != MachineWorkMode.SYNC) continue;
                ControllerPermit permit = controllerPermits.get(controller.getBlockPos().asLong());
                if (permit != slot.permit
                        || permit.eligibleRound != previousRound && permit.eligibleRound != executionRoundId
                        || permit.controller == null || permit.controller.get() != controller) continue;
                if (!(controller.getLevel() instanceof ServerLevel level) || !validExecution(slot, level)
                        || controller.isRedstonePaused() || slot.waiting || slot.synchronousRound == executionRoundId
                        || slot.retryAt > level.getGameTime() + 1L || slot.lane.lastAttemptRound == executionRoundId
                        || execution.ledger().state().outputPaid()
                        || execution.ledger().state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED
                        || execution.owner().finishPending() && !execution.finishAttemptReady()) continue;
                plannedSynchronousControllers.add(controller.getBlockPos());
                ready.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
            }
        });
        int available = Math.min(remainingRequests, requestBudget - synchronousGrants);
        while (available-- > 0 && !ready.isEmpty()) {
            DomainKey key = domainCursor == null ? ready.firstKey() : ready.higherKey(domainCursor);
            if (key == null) key = ready.firstKey();
            DomainBucket bucket = domains.get(key);
            List<ExecutionSlot> lanes = ready.get(key);
            ExecutionSlot slot = lanes.stream().min((first, second) ->
                    bucket.laneOrder(first.lane.key, second.lane.key, RequestType.TICK)).orElseThrow();
            slot.synchronousRound = executionRoundId;
            synchronousGrants++;
            domainCursor = key;
            bucket.markAttempted(RequestType.TICK, slot.lane.key);
            lanes.remove(slot);
            if (lanes.isEmpty()) ready.remove(key);
        }
    }

    public long executionRoundId() {
        return executionRoundId;
    }

    public ControllerPermit controllerPermit(BlockPos controllerPos) {
        return controllerPermits.computeIfAbsent(controllerPos.asLong(), ignored -> new ControllerPermit());
    }

    public void markControllerEligible(BlockPos controllerPos, long gameTime) {
        if (executionRoundId == 0L || !executionRunning) return;
        markControllerEligible(controllerPos, controllerPermit(controllerPos), gameTime);
    }

    private void markControllerEligible(BlockPos controllerPos, ControllerPermit permit, long gameTime) {
        if (executionRoundId == 0L || !executionRunning) return;
        boolean newlyEligible = !permit.eligible(executionRoundId, gameTime, true);
        // ServerLevel advances gameTime between the Pre event and block-entity tickers.
        executionGameTime = gameTime;
        beginLevelTick(gameTime);
        permit.markEligible(executionRoundId, gameTime);
        // Ordinary BE ticks precede the round's candidate reset. Only a late ticker must
        // restore candidates that an earlier drain rejected for lacking real eligibility.
        if (!newlyEligible || lastDrainRound != executionRoundId || lastDrainTime != gameTime) return;
        Set<LaneRecords> lanes = executionsByController.get(controllerPos.asLong());
        if (lanes != null) for (LaneRecords lane : lanes) for (ExecutionSlot slot : lane.slots) {
            DomainBucket bucket = slot.domain == null ? null : domains.get(slot.domain);
            if (slot.execution != null && !slot.waiting && bucket != null) {
                bucket.addPending(slot);
                changedExecutionDomains.add(slot.domain);
                postDirtyDomains.add(slot.domain);
            }
        }
        schedulingRevision++;
    }

    public void markControllerEligible(MachineControllerBlockEntity controller, long gameTime) {
        if (controller.isRemoved()) return;
        BlockPos controllerPos = controller.getBlockPos();
        ControllerPermit permit = controllerPermits.get(controllerPos.asLong());
        if (permit == null || permit.controller == null || permit.controller.get() != controller) {
            if (permit != null) permit.invalidate();
            permit = new ControllerPermit();
            permit.controller = new WeakReference<>(controller);
            controllerPermits.put(controllerPos.asLong(), permit);
        }
        markControllerEligible(controllerPos, permit, gameTime);
        Set<ExecutionSlot> waiting = waitingByController.get(controllerPos);
        if (waiting != null) for (ExecutionSlot slot : List.copyOf(waiting)) {
            if (slot.execution != null && slot.execution.ledger().state().phase() != RecipeCommitLedger.Phase.ERROR_PAUSED
                    && (slot.retryAt <= gameTime
                    || slot.sourceRevision != controller.componentRuntime().capabilityContentRevision())) wakeExecution(slot);
        }
        // A returning ticker (including the first resumed round) had no previous-round hint.
        if (controller.activeWorkMode() == MachineWorkMode.SYNC && executionRunning
                && permit.eligible(executionRoundId, gameTime, true)
                && plannedSynchronousControllers.add(controllerPos)) {
            grantSynchronousRound(executionRoundId - 1L);
        }
    }

    /** Reserves terminal delivery capacity BEFORE the caller starts its input transaction. */
    public boolean reserveExecution(CraftingRuntime owner, String laneId) {
        if (closed) return false;
        ExecutionSlot active = activeExecutions.get(owner);
        if (active != null && active.execution.current()) return true;
        LaneKey key = new LaneKey(owner.controller().getBlockPos(), laneId);
        Set<LaneRecords> controllerLanes = executionsByController.get(key.controllerPos().asLong());
        if (controllerLanes != null) for (LaneRecords candidate : List.copyOf(controllerLanes)) {
            if (candidate.controller != null && candidate.controller.get() != owner.controller()) {
                candidate.retired = true;
                removeLaneBinding(candidate);
                for (ExecutionSlot slot : candidate.slots) {
                    slot.reservedOwner = null;
                    if (slot.acceptedOwner != null) terminateExecution(slot);
                }
                retireIfAcknowledged(candidate);
            }
        }
        LaneRecords records = laneBinding(key.controllerPos(), laneId);
        if (records == null) {
            // Reuse the lowest vacant logical lane, even while its terminal deliveries await ACK.
            // Changing a thread name or replacing the BE at this position cannot buy new capacity.
            int firstLane = laneId.equals("base") ? 0 : 1;
            int laneEnd = laneId.equals("base") ? 1 : FactoryRuntime.MAX_LANES;
            for (int lane = firstLane; lane < laneEnd; lane++) {
                LaneKey logicalKey = new LaneKey(key.controllerPos(), Integer.toString(lane));
                LaneRecords candidate = executionRecords.get(logicalKey);
                if (candidate == null) {
                    records = new LaneRecords(logicalKey, key);
                    executionRecords.put(logicalKey, records);
                    break;
                }
                if (candidate.retired) {
                    records = candidate;
                    break;
                }
            }
            if (records == null) return false;
        }
        executionsByController.computeIfAbsent(key.controllerPos().asLong(), ignored -> new HashSet<>()).add(records);
        for (ExecutionSlot slot : records.slots) if (slot.reservedOwner == owner) return true;
        for (ExecutionSlot slot : records.slots) {
            if (slot.acceptedOwner != null) {
                if (slot.pendingAcceptance) {
                    if (slot.cancelRequested) continue;
                    return slot.acceptedOwner == owner;
                }
                if (slot.ledger.state().outputPaid() || slot.acceptedOwner.ledger() != slot.ledger
                        || slot.acceptedOwner.activeRecipe() != slot.recipe
                        || slot.acceptedOwner.controller().isRemoved()
                        || slot.identity.lifecycleEpoch() != slot.acceptedOwner.controller().lifecycleEpoch()
                        || slot.execution != null && !slot.execution.current()) terminateExecution(slot);
                else if (slot.acceptedOwner == owner) return true;
                else return false;
            }
        }
        for (ExecutionSlot slot : records.slots) {
            if (slot.receipt == null && slot.reservedOwner == null) {
                slot.resetExecutionState();
                slot.reservedOwner = owner;
                records.key = key;
                records.retired = false;
                records.controller = new WeakReference<>(owner.controller());
                laneBindings.computeIfAbsent(key.controllerPos().asLong(), ignored -> new HashMap<>()).put(laneId, records);
                return true;
            }
        }
        return false;
    }

    public void releaseExecutionReservation(CraftingRuntime owner, String laneId) {
        LaneRecords records = laneBinding(owner.controller().getBlockPos(), laneId);
        if (records == null) return;
        for (ExecutionSlot slot : records.slots) {
            if (slot.acceptedOwner == owner) terminateExecution(slot);
            else if (slot.reservedOwner == owner) slot.reservedOwner = null;
        }
        retireIfAcknowledged(records);
    }

    public ExecutionIdentity acceptedIdentity(CraftingRuntime owner, String laneId) {
        LaneRecords records = laneBinding(owner.controller().getBlockPos(), laneId);
        if (records == null) return null;
        for (ExecutionSlot slot : records.slots) {
            if (slot.acceptedOwner == owner && slot.ledger == owner.ledger() && slot.recipe == owner.activeRecipe()) {
                return slot.identity;
            }
        }
        return null;
    }

    /** Binds confirmed native start facts before compilation can be queued. */
    public boolean acceptExecution(CraftingRuntime owner, String laneId, ExecutionIdentity identity,
                                   RecipeCommitLedger ledger, ActiveMachineRecipe recipe) {
        ExecutionSlot slot = receiptSlots.get(identity);
        if (slot == null) {
            if (!beginExecutionAdmission(owner, laneId, identity, ledger, recipe)) return false;
            slot = receiptSlots.get(identity);
        }
        if (slot.acceptedOwner != owner || slot.ledger != ledger || slot.recipe != recipe) return false;
        slot.pendingAcceptance = false;
        if (slot.cancelRequested || owner.ledger() != ledger || owner.controller().isRemoved()
                || identity.lifecycleEpoch() != owner.controller().lifecycleEpoch()) {
            terminateExecution(slot);
            return false;
        }
        publishExecution(slot, false);
        return true;
    }

    /** Retains the admitted identity through native notification cancellation, without confirming payment. */
    public boolean beginExecutionAdmission(CraftingRuntime owner, String laneId, ExecutionIdentity identity,
                                           RecipeCommitLedger ledger, ActiveMachineRecipe recipe) {
        ExecutionSlot pending = receiptSlots.get(identity);
        if (pending != null) return pending.pendingAcceptance && !pending.cancelRequested
                && pending.acceptedOwner == owner && pending.ledger == ledger;
        if (closed || !(owner.controller().getLevel() instanceof ServerLevel level)) return false;
        LaneRecords records = laneBinding(owner.controller().getBlockPos(), laneId);
        StructureClaimRegistry.ResourceDomain domain = owner.controller().resourceDomain();
        if (records == null || domain == null) return false;
        for (ExecutionSlot slot : records.slots) {
            if (slot.reservedOwner != owner) continue;
            slot.reservedOwner = null;
            slot.acceptedOwner = owner;
            slot.identity = identity;
            slot.ledger = ledger;
            slot.recipe = recipe;
            slot.domain = new DomainKey(domain.id(), domain.generation());
            slot.receiptDomain = new MachineAsyncCoordinator.ExecutionDomain(domain.id(), domain.generation());
            slot.level = new WeakReference<>(level);
            slot.pendingAcceptance = true;
            slot.cancelRequested = false;
            slot.revision = ++records.revision;
            records.latestIdentity = identity;
            receiptSlots.put(identity, slot);
            return true;
        }
        return false;
    }

    /** Unwinds a failed/cancelled native admission using only its old identity and payment facts. */
    public void endExecutionAdmission(ExecutionIdentity identity) {
        ExecutionSlot slot = receiptSlots.get(identity);
        if (slot == null || !slot.pendingAcceptance) return;
        slot.pendingAcceptance = false;
        if (slot.ledger.state().phase() == RecipeCommitLedger.Phase.READY) closeExecution(slot);
        else terminateExecution(slot);
    }

    /** Refreshes a stable binding without reserving or installing another delivery slot. */
    public boolean refreshExecution(InstalledRecipeProgram execution, ControllerPermit permit) {
        if (closed) return false;
        ExecutionSlot active = activeExecutions.get(execution.owner());
        return active != null && active.execution == execution && refreshExecution(active, permit);
    }

    private boolean refreshExecution(ExecutionSlot active, ControllerPermit permit) {
        MachineControllerBlockEntity controller = active.execution.owner().controller();
        if (!(controller.getLevel() instanceof ServerLevel level)
                || !currentPermit(controller, permit, level) || !validExecution(active, level)) return false;
        active.permit = permit;
        return true;
    }

    public boolean install(InstalledRecipeProgram execution, ControllerPermit permit) {
        if (closed) return false;
        ExecutionSlot active = activeExecutions.get(execution.owner());
        if (active != null && active.execution == execution) {
            return refreshExecution(active, permit);
        }
        LaneRecords records = laneBinding(execution.owner().controller().getBlockPos(), execution.identity().laneId());
        return records != null && install(execution, permit, records.revision, records.commandSequence + 1L);
    }

    /** Worker command admission: expected revision and strictly monotonic lane command sequence. */
    public boolean install(InstalledRecipeProgram execution, ControllerPermit permit,
                           long expectedRevision, long commandSequence) {
        if (closed) return false;
        CraftingRuntime owner = execution.owner();
        MachineControllerBlockEntity controller = owner.controller();
        if (!(controller.getLevel() instanceof ServerLevel level) || !owner.active()
                || owner.installedProgram() != execution || !currentPermit(controller, permit, level)) return false;
        LaneRecords records = laneBinding(controller.getBlockPos(), execution.identity().laneId());
        if (records == null || records.revision != expectedRevision || commandSequence <= records.commandSequence
                || !execution.current()) return false;
        ExecutionIdentity previous = records.latestIdentity;
        boolean accepted = execution.identity().equals(acceptedIdentity(owner, execution.identity().laneId()));
        if (previous != null && previous.controllerInstance() == execution.identity().controllerInstance()
                && (previous.lifecycleEpoch() > execution.identity().lifecycleEpoch()
                || previous.lifecycleEpoch() == execution.identity().lifecycleEpoch()
                && (previous.executionId() > execution.identity().executionId()
                || previous.executionId() == execution.identity().executionId() && !accepted))) return false;
        for (ExecutionSlot slot : records.slots) {
            boolean restored = slot.reservedOwner == owner && slot.acceptedOwner == null;
            if (!restored && (slot.acceptedOwner != owner || slot.pendingAcceptance || slot.cancelRequested
                    || slot.execution != null || !execution.identity().equals(slot.identity)
                    || slot.ledger != execution.ledger() || slot.recipe != owner.activeRecipe())) continue;
            VersionStamp versions = execution.program().versions();
            StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
            if (domain == null || domain.id() != versions.domainId()
                    || domain.generation() != versions.domainGeneration()) return false;
            slot.reservedOwner = null;
            slot.acceptedOwner = owner;
            slot.identity = execution.identity();
            slot.ledger = execution.ledger();
            slot.recipe = owner.activeRecipe();
            slot.execution = execution;
            slot.permit = permit;
            slot.domain = new DomainKey(domain.id(), domain.generation());
            slot.receiptDomain = new MachineAsyncCoordinator.ExecutionDomain(domain.id(), domain.generation());
            slot.level = new WeakReference<>(level);
            slot.revision = ++records.revision;
            records.commandSequence = commandSequence;
            records.latestIdentity = execution.identity();
            activeExecutions.put(owner, slot);
            activeProgramsByController.merge(controller.getBlockPos(), 1, Integer::sum);
            receiptSlots.put(execution.identity(), slot);
            DomainBucket bucket = domains.computeIfAbsent(slot.domain, ignored -> new DomainBucket());
            bucket.putInstalled(slot);
            schedulingRevision++;
            changedExecutionDomains.add(slot.domain);
            postDirtyDomains.add(slot.domain);
            slot.synchronousRound = Long.MIN_VALUE;
            publishExecution(slot, false);
            if (controller.activeWorkMode() == MachineWorkMode.SYNC) grantSynchronousRound(executionRoundId);
            return true;
        }
        return false;
    }

    /** Post arbitration includes legacy boundaries in the SAME original request budget. */
    public int executeInstalled(ServerLevel level) {
        long before = installedAttempts;
        resolve(level);
        return (int) (installedAttempts - before);
    }

    /** SYNC stays at the BE boundary and does not wait for receipt workers. */
    public boolean executeInstalled(InstalledRecipeProgram execution, ServerLevel level) {
        if (execution.owner().controller().activeWorkMode() != MachineWorkMode.SYNC) return false;
        ExecutionSlot slot = activeExecutions.get(execution.owner());
        return slot != null && slot.execution == execution && remainingRequests > 0
                && eligibleExecution(slot, level, true) && attemptExecution(slot, level);
    }

    private boolean currentPermit(MachineControllerBlockEntity controller, ControllerPermit permit, ServerLevel level) {
        return !controller.isRemoved() && controller.getLevel() == level
                && level.getBlockEntity(controller.getBlockPos()) == controller && !controller.isRedstonePaused()
                && controllerPermits.get(controller.getBlockPos().asLong()) == permit && permit.controller != null
                && permit.controller.get() == controller && permit.eligible(executionRoundId, level.getGameTime(),
                executionRunning && level.tickRateManager().runsNormally());
    }

    private boolean qualifiedRequest(Request request, ServerLevel level) {
        // Direct legacy/debug fixtures have no Pre round. A real round always requires a real ticker.
        if (executionRoundId == 0L) return true;
        ControllerPermit permit = controllerPermits.get(request.laneKey().controllerPos().asLong());
        MachineControllerBlockEntity controller = permit == null || permit.controller == null ? null : permit.controller.get();
        return controller != null && currentPermit(controller, permit, level);
    }

    private boolean validExecution(ExecutionSlot slot, ServerLevel level) {
        InstalledRecipeProgram execution = slot.execution;
        if (execution == null) return false;
        CraftingRuntime owner = execution.owner();
        MachineControllerBlockEntity controller = owner.controller();
        VersionStamp versions = execution.program().versions();
        return controller.getLevel() == level && !controller.isRemoved() && owner.active()
                && execution.current() && controller.lifecycleEpoch() == execution.identity().lifecycleEpoch()
                && controller.currentStructureSnapshot().formed() && controller.currentStructureSnapshot().structureAreaLoaded()
                && controller.currentStructureSnapshot().version() == versions.structure()
                && controller.componentRuntime().capabilityVersion() == versions.capability()
                && owner.activeRecipe().effectiveExecutionRevision() == versions.effective()
                // current() already checks the live recipe pool/catalog and captured resource domain.
                && versions.domainId() == slot.domain.id && versions.domainGeneration() == slot.domain.generation;
    }

    private boolean eligibleExecution(ExecutionSlot slot, ServerLevel level, boolean sync) {
        if (slot.execution == null || slot.execution.cancelled()
                || slot.execution.owner().installedProgram() != slot.execution || !slot.execution.owner().active()
                || slot.execution.ledger().state().outputPaid()) {
            terminateExecution(slot);
            return false;
        }
        LaneRecords lane = slot.lane;
        long gameTime = level.getGameTime();
        // Scans revisit paid lanes: reject scheduling fences before touching live domains/world state.
        if (lane.lastAttemptRound == executionRoundId || lane.lastAttemptTime == gameTime
                || slot.waiting || gameTime < slot.retryAt
                || (slot.execution.owner().controller().activeWorkMode() == MachineWorkMode.SYNC) != sync
                || sync && slot.synchronousRound != executionRoundId) return false;
        if (!validExecution(slot, level)) {
            terminateExecution(slot);
            return false;
        }
        if (slot.execution.ledger().state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED) return false;
        if (slot.execution.owner().finishPending() && !slot.execution.finishAttemptReady()) return false;
        return currentPermit(slot.execution.owner().controller(), slot.permit, level);
    }

    private boolean attemptExecution(ExecutionSlot slot, ServerLevel level) {
        InstalledRecipeProgram execution = slot.execution;
        slot.lane.lastAttemptRound = executionRoundId;
        slot.lane.lastAttemptTime = level.getGameTime();
        if (!execution.tryAttempt(executionRoundId, level.getGameTime())) return false;
        remainingRequests--;
        installedAttempts++;
        DomainBucket bucket = domains.get(slot.domain);
        if (bucket != null) bucket.removePending(slot);
        // SYNC cursors already advanced in grant order, independently of the actual BE order.
        if (execution.owner().controller().activeWorkMode() != MachineWorkMode.SYNC) {
            domainCursor = slot.domain;
            if (bucket != null) bucket.markAttempted(RequestType.TICK, slot.lane.key);
        }
        long sourceRevision = execution.owner().controller().componentRuntime().capabilityContentRevision();
        boolean advanced = false;
        try {
            advanced = execution.advance();
        } catch (RuntimeException exception) {
            // The runtime handles ordinary rejection itself. An escaped callback/notification error
            // must neither abort other lanes nor replay an uncertain resource/lifecycle phase.
            execution.ledger().pauseAfterCommitFailure();
            MMCR.LOG.error("Installed execution paused after an unexpected boundary failure: identity={}",
                    execution.identity(), exception);
        } finally {
            if (slot.execution == execution) {
                boolean terminal = !execution.owner().active() || execution.ledger().state().outputPaid();
                publishExecution(slot, terminal);
                if (terminal) closeExecution(slot);
            }
        }
        if (slot.execution == execution && !advanced && !execution.program().requiresMainThreadPreparation()) {
            slot.sourceRevision = sourceRevision;
            slot.waiting = true;
            slot.retryAt = execution.ledger().state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED
                    ? Long.MAX_VALUE : level.getGameTime() + 1L; // Preserve ordinary per-tick failure policy.
            if (bucket != null) bucket.removeInstalled(slot);
            waitingExecutions.computeIfAbsent(slot.retryAt, ignored -> new HashSet<>()).add(slot);
            waitingByController.computeIfAbsent(slot.lane.key.controllerPos(), ignored -> new HashSet<>()).add(slot);
            // Register then recheck: a commit notification may have changed the source while blocking.
            if (execution.ledger().state().phase() != RecipeCommitLedger.Phase.ERROR_PAUSED
                    && sourceRevision != execution.owner().controller().componentRuntime().capabilityContentRevision()) wakeExecution(slot);
        }
        return advanced;
    }

    private void wakeExecution(ExecutionSlot slot) {
        if (!slot.waiting || slot.execution == null) return;
        Set<ExecutionSlot> waiting = waitingExecutions.get(slot.retryAt);
        if (waiting != null) {
            waiting.remove(slot);
            if (waiting.isEmpty()) waitingExecutions.remove(slot.retryAt);
        }
        slot.waiting = false;
        removeWaitingOwner(slot);
        DomainBucket bucket = domains.computeIfAbsent(slot.domain, ignored -> new DomainBucket());
        bucket.putInstalled(slot);
        schedulingRevision++;
        changedExecutionDomains.add(slot.domain);
        postDirtyDomains.add(slot.domain);
    }

    private void wakeDueExecutions(long gameTime) {
        while (!waitingExecutions.isEmpty() && waitingExecutions.firstKey() <= gameTime) {
            Set<ExecutionSlot> due = waitingExecutions.pollFirstEntry().getValue();
            for (ExecutionSlot slot : due) wakeExecution(slot);
        }
    }

    private void removeWaitingOwner(ExecutionSlot slot) {
        waitingByController.computeIfPresent(slot.lane.key.controllerPos(), (ignored, slots) -> {
            slots.remove(slot);
            return slots.isEmpty() ? null : slots;
        });
    }

    private void publishExecution(ExecutionSlot slot, boolean terminal) {
        RecipeCommitLedger.State state = slot.ledger.state();
        boolean ownsCurrentState = slot.acceptedOwner.ledger() == slot.ledger;
        ExecutionStatus failure = ownsCurrentState ? slot.acceptedOwner.failure()
                : slot.receipt == null ? null : slot.receipt.failure();
        slot.receipt = new Receipt(slot.identity, slot.revision,
                slot.receipt == null ? 0L : slot.receipt.sequence() + 1L,
                slot.recipe.getTick(),
                state.paidAttempts(), terminal, failure);
        ServerLevel level = slot.level.get();
        if (level != null) MachineAsyncCoordinator.get(level).publishReceipt(slot.receiptDomain, slot.receipt);
    }

    private void terminateExecution(ExecutionSlot slot) {
        if (slot.acceptedOwner == null) return;
        if (slot.pendingAcceptance) {
            slot.cancelRequested = true;
            return;
        }
        // Record first: cancelling live bindings must not erase the old terminal facts.
        publishExecution(slot, true);
        if (slot.execution != null) slot.execution.cancel();
        closeExecution(slot);
    }

    private void closeExecution(ExecutionSlot slot) {
        if (slot.execution != null && activeExecutions.remove(slot.execution.owner(), slot)) {
            activeProgramsByController.computeIfPresent(slot.lane.key.controllerPos(),
                    (ignored, count) -> count == 1 ? null : count - 1);
        }
        DomainBucket bucket = domains.get(slot.domain);
        if (bucket != null) bucket.removeInstalled(slot);
        if (bucket != null && bucket.isDisposable()) {
            domains.remove(slot.domain, bucket);
            postCandidates.remove(slot.domain);
        }
        if (slot.waiting) {
            Set<ExecutionSlot> waiting = waitingExecutions.get(slot.retryAt);
            if (waiting != null) {
                waiting.remove(slot);
                if (waiting.isEmpty()) waitingExecutions.remove(slot.retryAt);
            }
        }
        slot.waiting = false;
        removeWaitingOwner(slot);
        slot.execution = null;
        dirtyTerminations.remove(slot);
        if (slot.receipt == null && slot.identity != null) receiptSlots.remove(slot.identity, slot);
        slot.reservedOwner = null;
        slot.acceptedOwner = null;
        slot.identity = null;
        slot.ledger = null;
        slot.cancelRequested = false;
        slot.recipe = null;
        slot.permit = null;
        slot.resetExecutionState();
    }

    public boolean acknowledgeReceipt(Acknowledgement acknowledgement) {
        ExecutionSlot slot = receiptSlots.get(acknowledgement.identity());
        if (slot == null) return false;
        Receipt receipt = slot.receipt;
        if (receipt == null || !receipt.terminal() || receipt.installedRevision() != acknowledgement.installedRevision()
                || receipt.sequence() != acknowledgement.sequence()) return false;
        slot.receipt = null;
        receiptSlots.remove(acknowledgement.identity(), slot);
        retireIfAcknowledged(slot.lane);
        return true;
    }

    public void forgetExecutionLane(CraftingRuntime owner, String laneId) {
        LaneRecords records = laneBinding(owner.controller().getBlockPos(), laneId);
        if (records == null) return;
        for (ExecutionSlot slot : records.slots) {
            if (slot.acceptedOwner != null && slot.acceptedOwner != owner
                    || slot.reservedOwner != null && slot.reservedOwner != owner) return;
        }
        records.retired = true;
        removeLaneBinding(records);
        for (ExecutionSlot slot : records.slots) {
            if (slot.reservedOwner == owner) slot.reservedOwner = null;
            if (slot.acceptedOwner == owner) terminateExecution(slot);
        }
        retireIfAcknowledged(records);
    }

    private void retireIfAcknowledged(LaneRecords records) {
        MachineControllerBlockEntity controller = records.controller == null ? null : records.controller.get();
        if (controller == null || controller.isRemoved()) records.retired = true;
        if (!records.retired) return;
        for (ExecutionSlot slot : records.slots) {
            if (slot.receipt != null || slot.acceptedOwner != null || slot.reservedOwner != null) return;
        }
        // An ACK must not remove the lane's same-gameTime fence before a replacement starts.
        if (records.lastAttemptTime != Long.MIN_VALUE && records.lastAttemptTime == executionGameTime) {
            retirementCandidates.add(records);
            return;
        }
        executionRecords.remove(records.logicalKey, records);
        removeLaneBinding(records);
        retirementCandidates.remove(records);
        executionsByController.computeIfPresent(records.key.controllerPos().asLong(), (ignored, lanes) -> {
            lanes.remove(records);
            return lanes.isEmpty() ? null : lanes;
        });
    }

    private LaneRecords laneBinding(BlockPos controllerPos, String laneId) {
        Map<String, LaneRecords> lanes = laneBindings.get(controllerPos.asLong());
        return lanes == null ? null : lanes.get(laneId);
    }

    private void removeLaneBinding(LaneRecords records) {
        long position = records.key.controllerPos().asLong();
        Map<String, LaneRecords> lanes = laneBindings.get(position);
        if (lanes == null) return;
        lanes.remove(records.key.laneId(), records);
        if (lanes.isEmpty()) laneBindings.remove(position);
    }

    public long installedRevision(CraftingRuntime owner, String laneId) {
        LaneRecords records = laneBinding(owner.controller().getBlockPos(), laneId);
        return records == null ? 0L : records.revision;
    }

    /** A delayed worker cancel cannot affect a replacement or already terminal execution. */
    public boolean cancelExecution(ExecutionIdentity identity, long expectedRevision, long commandSequence) {
        ExecutionSlot slot = receiptSlots.get(identity);
        if (slot == null || slot.acceptedOwner == null || slot.revision != expectedRevision
                || commandSequence <= slot.lane.commandSequence) return false;
        slot.lane.commandSequence = commandSequence;
        terminateExecution(slot);
        return true;
    }

    /** Rule replacement keeps the same main-thread ledger and the original execution slot. */
    public boolean replaceExecution(InstalledRecipeProgram execution, ControllerPermit permit,
                                    long expectedRevision, long commandSequence) {
        ExecutionSlot slot = receiptSlots.get(execution.identity());
        if (slot == null || slot.execution == null || slot.revision != expectedRevision
                || commandSequence <= slot.lane.commandSequence
                || slot.execution.owner() != execution.owner() || slot.execution.ledger() != execution.ledger()
                || slot.recipe != execution.owner().activeRecipe()
                || execution.owner().installedProgram() != execution || !execution.current()
                || !(execution.owner().controller().getLevel() instanceof ServerLevel level)
                || !currentPermit(execution.owner().controller(), permit, level)) return false;
        VersionStamp versions = execution.program().versions();
        if (versions.domainId() != slot.domain.id || versions.domainGeneration() != slot.domain.generation
                || execution.ledger().state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID) return false;
        slot.execution.cancel();
        slot.execution = execution;
        slot.permit = permit;
        slot.revision = ++slot.lane.revision;
        slot.lane.commandSequence = commandSequence;
        publishExecution(slot, false);
        return true;
    }

    List<Receipt> receiptsForTesting(CraftingRuntime owner, String laneId) {
        Set<LaneRecords> records = executionsByController.get(owner.controller().getBlockPos().asLong());
        if (records == null) return List.of();
        List<Receipt> result = new ArrayList<>(2);
        for (LaneRecords lane : records) for (ExecutionSlot slot : lane.slots) {
            if (slot.receipt != null && slot.receipt.identity().laneId().equals(laneId)) result.add(slot.receipt);
        }
        return List.copyOf(result);
    }

    public void drainReceiptAcknowledgements(ServerLevel level) {
        Acknowledgement acknowledgement;
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        while ((acknowledgement = async.pollReceiptAcknowledgement()) != null) acknowledgeReceipt(acknowledgement);
        for (LaneRecords records : List.copyOf(retirementCandidates)) {
            retirementCandidates.remove(records);
            retireIfAcknowledged(records);
        }
    }

    /**
     * Two delivery slots per stable controller-position/lane-number, including unacknowledged terminals.
     * The current thread name is only a live binding; it does not define historical capacity.
     * @author howxu <dev@howxu.cn>
     */
    private static final class LaneRecords {
        private final LaneKey logicalKey;
        private LaneKey key;
        private final ExecutionSlot[] slots = {new ExecutionSlot(this), new ExecutionSlot(this)};
        private long revision;
        private long commandSequence;
        private boolean retired;
        private WeakReference<MachineControllerBlockEntity> controller;
        private ExecutionIdentity latestIdentity;
        private long lastAttemptRound = Long.MIN_VALUE;
        private long lastAttemptTime = Long.MIN_VALUE;

        private LaneRecords(LaneKey logicalKey, LaneKey key) {
            this.logicalKey = logicalKey;
            this.key = key;
        }
    }

    /**
     * Main-thread live binding and its coalesced immutable delivery record.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ExecutionSlot {
        private final LaneRecords lane;
        private CraftingRuntime reservedOwner;
        private CraftingRuntime acceptedOwner;
        private ExecutionIdentity identity;
        private RecipeCommitLedger ledger;
        private boolean pendingAcceptance;
        private boolean cancelRequested;
        private InstalledRecipeProgram execution;
        private ActiveMachineRecipe recipe;
        private ControllerPermit permit;
        private DomainKey domain;
        private MachineAsyncCoordinator.ExecutionDomain receiptDomain;
        private WeakReference<ServerLevel> level;
        private long revision;
        private Receipt receipt;
        private boolean waiting;
        private long retryAt;
        private long sourceRevision;
        private long synchronousRound = Long.MIN_VALUE;
        private int pendingIndex;
        private long consumedRound = Long.MIN_VALUE;
        private long consumedTime = Long.MIN_VALUE;

        private ExecutionSlot(LaneRecords lane) { this.lane = lane; }

        private void resetExecutionState() {
            waiting = false;
            retryAt = 0L;
            sourceRevision = 0L;
            synchronousRound = Long.MIN_VALUE;
            consumedRound = Long.MIN_VALUE;
            consumedTime = Long.MIN_VALUE;
            pendingAcceptance = false;
            cancelRequested = false;
            domain = null;
            receiptDomain = null;
            level = null;
        }
    }

    /**
     * Main-thread qualification granted only by a controller's real server ticker.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class ControllerPermit {
        private long eligibleRound = Long.MIN_VALUE;
        private long eligibleGameTime = Long.MIN_VALUE;
        private WeakReference<MachineControllerBlockEntity> controller;

        public void markEligible(long roundId, long gameTime) {
            eligibleRound = roundId;
            eligibleGameTime = gameTime;
        }

        public boolean eligible(long roundId, long gameTime, boolean running) {
            return running && eligibleRound == roundId && eligibleGameTime == gameTime;
        }

        private void invalidate() {
            eligibleRound = Long.MIN_VALUE;
            eligibleGameTime = Long.MIN_VALUE;
            controller = null;
        }
    }

    public void enqueue(Request request) {
        schedulingRevision++;
        DomainKey key = new DomainKey(request.domainId(), request.domainGeneration());
        changedRequestDomains.add(key);
        postDirtyDomains.add(key);
        DomainBucket bucket = domains.computeIfAbsent(key, ignored -> new DomainBucket());
        bucket.requests.get(requestType(request))
                .computeIfAbsent(request.laneKey(), ignored -> new ArrayDeque<>()).add(request);
        requestsByController.computeIfAbsent(request.laneKey().controllerPos(),
                ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(request);
    }

    public boolean enqueueTickWork(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                   MachineAsyncCoordinator.TaskKey laneTaskKey,
                                   AsyncRequirementPlanner.PreparedPlan preparedPlan,
                                   Predicate<AsyncRequirementPlanner.PlanResult> committer, Runnable discard) {
        if (level == null || domain == null || laneTaskKey == null || preparedPlan == null
                || committer == null || discard == null) return false;
        DomainKey key = new DomainKey(domain.id(), domain.generation());
        domains.computeIfAbsent(key, ignored -> new DomainBucket());
        pendingTickWork.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                .add(new TickWorkEntry(level, laneTaskKey, preparedPlan, committer, discard));
        schedulingRevision++;
        postDirtyDomains.add(key);
        return true;
    }

    /** Discards every queued request owned by one controller. */
    public void cancel(BlockPos controllerPos) {
        schedulingRevision++;
        ControllerPermit permit = controllerPermits.remove(controllerPos.asLong());
        if (permit != null) permit.invalidate();
        Set<LaneRecords> executions = executionsByController.get(controllerPos.asLong());
        if (executions != null) for (LaneRecords lane : List.copyOf(executions)) {
            retirementCandidates.add(lane);
            for (ExecutionSlot slot : lane.slots) {
                slot.reservedOwner = null;
                if (slot.acceptedOwner != null && slot.acceptedOwner.controller().isRemoved()) lane.retired = true;
                terminateExecution(slot);
            }
            retireIfAcknowledged(lane);
        }
        Set<Request> owned = requestsByController.get(controllerPos);
        if (owned != null) {
            for (Request request : List.copyOf(owned)) {
                request.discard();
                removeRequest(request);
            }
        }
        pendingTickWork.values().forEach(entries -> entries.removeIf(entry -> {
            if (!entry.laneTaskKey().controllerPos().equals(controllerPos)) return false;
            entry.discardWork();
            return true;
        }));
        pendingTickWork.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        submittedTickWork.values().removeIf(workset -> {
            if (workset.entries().stream().noneMatch(entry ->
                    entry.laneTaskKey().controllerPos().equals(controllerPos))) return false;
            TickWorkEntry first = workset.entries().getFirst();
            if (first.level() != null) MachineAsyncCoordinator.get(first.level()).cancel(workset.taskKey());
            workset.entries().forEach(TickWorkEntry::discardWork);
            DomainBucket bucket = domains.get(workset.domainKey());
            if (bucket != null) bucket.worksetInFlight = false;
            return true;
        });
        removeEmptyBuckets();
    }

    public void beginLevelTick(long gameTime) {
        if (budgetGameTime == gameTime) return;
        budgetGameTime = gameTime;
        remainingRequests = requestBudget;
    }

    public int resolve(ServerLevel level) {
        if (closed) return 0;
        wakeDueExecutions(level.getGameTime());
        return resolveKnownDomains(collectKnownDomains(level), true, level);
    }

    private Map<Long, StructureClaimRegistry.ResourceDomain> collectKnownDomains(ServerLevel level) {
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains = new HashMap<>();
        for (Map.Entry<DomainKey, DomainBucket> entry : domains.entrySet()) {
            BlockPos owner = entry.getValue().firstController();
            if (owner == null) {
                ArrayDeque<TickWorkEntry> work = pendingTickWork.get(entry.getKey());
                if (work != null && !work.isEmpty()) owner = work.getFirst().laneTaskKey().controllerPos();
            }
            StructureClaimRegistry.ResourceDomain domain = owner == null ? null : registry.domainFor(owner);
            if (domain != null) knownDomains.put(domain.id(), domain);
        }
        for (TickWorkset workset : submittedTickWork.values()) {
            StructureClaimRegistry.ResourceDomain domain = registry.domainFor(
                    workset.entries().getFirst().laneTaskKey().controllerPos());
            if (domain != null) knownDomains.put(domain.id(), domain);
        }
        for (ExecutionSlot slot : activeExecutions.values()) if (slot.waiting) {
            StructureClaimRegistry.ResourceDomain domain = registry.domainFor(slot.lane.key.controllerPos());
            if (domain != null) knownDomains.put(domain.id(), domain);
        }
        return knownDomains;
    }

    /** Reuses an unchanged Post drain while preserving newly admitted continuation work. */
    public int resolvePost(ServerLevel level) {
        if (closed) return 0;
        long topology = StructureClaimRegistry.get(level).topologyRevision();
        long gameTime = level.getGameTime();
        boolean rebuild = postRound != executionRoundId || postGameTime != gameTime || postTopologyRevision != topology;
        if (postRound == executionRoundId && postGameTime == gameTime
                && postSchedulingRevision == schedulingRevision && postTopologyRevision == topology
                && requestsByController.isEmpty() && pendingTickWork.isEmpty()
                && dirtyTerminations.isEmpty()) return 0;
        // Capture before callbacks: work admitted during a drain must reach the next continuation.
        postRound = executionRoundId;
        postGameTime = gameTime;
        postSchedulingRevision = schedulingRevision;
        postTopologyRevision = topology;
        wakeDueExecutions(gameTime);
        if (rebuild) {
            postKnownDomains.clear();
            postKnownDomains.putAll(collectKnownDomains(level));
            discardStaleDomains(postKnownDomains, true);
            reconcileExecutions(level);
            postCandidates.clear();
            postCandidates.putAll(domains);
            domains.values().forEach(bucket -> bucket.preparePending(executionRoundId, gameTime));
            postDirtyDomains.clear();
        } else {
            drainDirtyTerminations();
            for (DomainKey key : List.copyOf(postDirtyDomains)) {
                DomainBucket bucket = domains.get(key);
                if (bucket == null) {
                    postCandidates.remove(key);
                    continue;
                }
                BlockPos controller = bucket.firstController();
                if (controller == null) {
                    ArrayDeque<TickWorkEntry> pending = pendingTickWork.get(key);
                    if (pending != null && !pending.isEmpty()) controller = pending.getFirst().laneTaskKey().controllerPos();
                }
                StructureClaimRegistry.ResourceDomain domain = controller == null ? null
                        : StructureClaimRegistry.get(level).domainFor(controller);
                if (domain == null || domain.id() != key.id || domain.generation() != key.generation) continue;
                postKnownDomains.put(domain.id(), domain);
                bucket.preparePending(executionRoundId, gameTime);
                postCandidates.put(key, bucket);
            }
            postDirtyDomains.clear();
            // Boundary failures retain their existing retry opportunity on a later drain.
            for (Set<Request> requests : requestsByController.values()) for (Request request : requests) {
                DomainKey key = new DomainKey(request.domainId(), request.domainGeneration());
                DomainBucket bucket = domains.get(key);
                if (bucket != null) postCandidates.put(key, bucket);
            }
        }
        return drainDomains(postKnownDomains, level, postCandidates);
    }

    /** Main-thread cancellation or committed terminal payment wakes Post without global polling. */
    public static void executionChanged(InstalledRecipeProgram execution) {
        if (!(execution.owner().controller().getLevel() instanceof ServerLevel level)) return;
        SharedIoCoordinator coordinator = COORDINATORS.get(level);
        if (coordinator == null) return;
        ExecutionSlot slot = coordinator.activeExecutions.get(execution.owner());
        if (slot != null && slot.execution == execution) {
            coordinator.dirtyTerminations.add(slot);
            coordinator.schedulingRevision++;
        }
    }

    private void drainDirtyTerminations() {
        for (ExecutionSlot slot : List.copyOf(dirtyTerminations)) {
            if (slot.execution != null && (slot.execution.cancelled()
                    || slot.execution.ledger().state().outputPaid())) terminateExecution(slot);
        }
        dirtyTerminations.clear();
    }

    private void reconcileExecutions(ServerLevel level) {
        for (ExecutionSlot slot : List.copyOf(activeExecutions.values())) {
            if (slot.execution == null || slot.execution.cancelled()
                    || slot.execution.owner().installedProgram() != slot.execution
                    || !slot.execution.owner().active() || slot.execution.ledger().state().outputPaid()) {
                terminateExecution(slot);
            }
        }
        drainDirtyTerminations();
    }

    public int resolve(StructureClaimRegistry.ResourceDomain domain) {
        return resolveKnownDomains(Map.of(domain.id(), domain), false);
    }

    int resolveKnownDomainsForTesting(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains) {
        return resolveKnownDomains(knownDomains, true);
    }

    private int resolveKnownDomains(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains,
                                     boolean discardUnknownDomains) {
        return resolveKnownDomains(knownDomains, discardUnknownDomains, null);
    }

    private int resolveKnownDomains(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains,
                                    boolean discardUnknownDomains, ServerLevel level) {
        discardStaleDomains(knownDomains, discardUnknownDomains);
        if (level != null) {
            reconcileExecutions(level);
            for (DomainBucket bucket : domains.values()) {
                bucket.preparePending(executionRoundId, level.getGameTime());
            }
        }
        return drainDomains(knownDomains, level, new TreeMap<>(domains));
    }

    private int drainDomains(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains, ServerLevel level,
                              NavigableMap<DomainKey, DomainBucket> candidates) {
        if (level != null) {
            lastDrainRound = executionRoundId;
            lastDrainTime = level.getGameTime();
        }
        int progress = 0;
        Set<Request> attempted = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Request> eligible = Collections.newSetFromMap(new IdentityHashMap<>());
        candidates.forEach((key, bucket) -> {
            StructureClaimRegistry.ResourceDomain domain = knownDomains.get(key.id);
            if (domain != null && domain.generation() == key.generation) eligible.addAll(bucket.allRequests());
        });
        WorkSelection selection = new WorkSelection();
        changedExecutionDomains.clear();
        changedRequestDomains.clear();
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            while (remainingRequests > 0) {
                if (!selectNextWork(attempted, eligible, level, selection, candidates)) break;
                DomainKey key = selection.domain;
                Object next = selection.work;
                if (next instanceof ExecutionSlot slot) {
                    if (attemptExecution(slot, level)) progress++;
                    continue;
                }
                DomainBucket bucket = domains.get(key);
                Request request = (Request) next;
                attempted.add(request);
                remainingRequests--;
                domainCursor = key;
                if (level != null) bucket.markAttempted(requestType(request), request.laneKey());
                boolean valid = request.isStillValid();
                if (!valid || request.tryCommit()) {
                    if (valid) {
                        bucket.markCommitted(request);
                        request.onCommitted();
                    }
                    removeRequest(request);
                    if (valid) includeSpawnedSuccessors(key, request, eligible);
                    progress++;
                }
            }
        }
        for (DomainKey key : List.copyOf(pendingTickWork.keySet())) {
            StructureClaimRegistry.ResourceDomain domain = knownDomains.get(key.id);
            if (domain != null && domain.generation() == key.generation) submitTickWorkset(domain, key);
        }
        return progress;
    }

    public LaneKey nextStartLane(long domainId) {
        return domains.entrySet().stream().filter(entry -> entry.getKey().id == domainId)
                .map(entry -> entry.getValue().cursors.get(RequestType.START)).findFirst().orElse(null);
    }

    int pendingRequestCountForTesting() {
        return requestsByController.values().stream().mapToInt(Set::size).sum();
    }

    int indexedControllerCountForTesting() {
        return requestsByController.size();
    }

    private boolean selectNextWork(Set<Request> attempted, Set<Request> eligible, ServerLevel level,
                                   WorkSelection selection, NavigableMap<DomainKey, DomainBucket> candidates) {
        if (!changedExecutionDomains.isEmpty()) for (DomainKey key : changedExecutionDomains) {
            DomainBucket bucket = domains.get(key);
            if (bucket != null) candidates.put(key, bucket);
        }
        changedExecutionDomains.clear();
        // Only admissions can revive an exhausted domain; unqualified backlog cannot.
        if (!changedRequestDomains.isEmpty()) for (DomainKey key : changedRequestDomains) {
            DomainBucket bucket = domains.get(key);
            if (bucket != null) candidates.putIfAbsent(key, bucket);
        }
        changedRequestDomains.clear();
        int domainCount = candidates.size();
        if (domainCount == 0) return false;
        DomainKey key = domainCursor == null ? candidates.firstKey() : candidates.higherKey(domainCursor);
        if (key == null) key = candidates.firstKey();
        // Selection invokes no callbacks. Re-read the live topology after each request instead
        // of retaining an iterator across commits, which may add/remove domains or resolve again.
        for (int offset = 0; offset < domainCount; offset++) {
            DomainBucket bucket = domains.get(key);
            Object work = bucket == null ? null : bucket.nextWork(attempted, eligible, level, this);
            if (work != null) {
                selection.domain = key;
                selection.work = work;
                return true;
            }
            candidates.remove(key);
            if (candidates.isEmpty()) return false;
            key = candidates.higherKey(key);
            if (key == null) key = candidates.firstKey();
        }
        return false;
    }

    /** Invocation-local candidate, consumed before callbacks. @author howxu <dev@howxu.cn> */
    private static final class WorkSelection {
        private DomainKey domain;
        private Object work;
    }

    private void includeSpawnedSuccessors(DomainKey key, Request request, Set<Request> eligible) {
        DomainBucket bucket = domains.get(key);
        if (bucket == null) return;
        RequestType successor = request instanceof TickRequest ? RequestType.FINISH
                : request instanceof FinishRequest ? RequestType.START : null;
        if (successor == null) return;
        bucket.requests.get(successor).values().forEach(eligible::addAll);
        changedRequestDomains.add(key);
    }

    private void discardStaleDomains(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains,
                                     boolean discardUnknownDomains) {
        for (DomainKey key : List.copyOf(domains.keySet())) {
            StructureClaimRegistry.ResourceDomain current = knownDomains.get(key.id);
            if (current != null && current.generation() == key.generation) continue;
            if (current == null && !discardUnknownDomains) continue;
            DomainBucket bucket = domains.remove(key);
            for (ExecutionSlot slot : List.copyOf(activeExecutions.values())) {
                if (key.equals(slot.domain)) terminateExecution(slot);
            }
            postCandidates.remove(key);
            if (bucket != null) for (Request request : bucket.allRequests()) {
                request.discard();
                removeOwnerIndex(request);
            }
            ArrayDeque<TickWorkEntry> work = pendingTickWork.remove(key);
            if (work != null) work.forEach(TickWorkEntry::discardWork);
        }
    }

    private void removeRequest(Request request) {
        DomainBucket bucket = domains.get(new DomainKey(request.domainId(), request.domainGeneration()));
        if (bucket != null) bucket.remove(request);
        removeOwnerIndex(request);
    }

    private void removeOwnerIndex(Request request) {
        requestsByController.computeIfPresent(request.laneKey().controllerPos(), (ignored, requests) -> {
            requests.remove(request);
            return requests.isEmpty() ? null : requests;
        });
    }

    private void removeEmptyBuckets() {
        domains.entrySet().removeIf(entry -> entry.getValue().isDisposable()
                && !pendingTickWork.containsKey(entry.getKey()));
    }

    private static RequestType requestType(Request request) {
        if (request instanceof StartRequest) return RequestType.START;
        if (request instanceof TickRequest) return RequestType.TICK;
        return RequestType.FINISH;
    }

    private record DomainKey(long id, long generation) implements Comparable<DomainKey> {
        @Override
        public int compareTo(DomainKey other) {
            int idOrder = Long.compare(id, other.id);
            return idOrder != 0 ? idOrder : Long.compare(generation, other.generation);
        }
    }

    private enum RequestType { START, TICK, FINISH }

    private static final class DomainBucket {
        private static final RequestType[] REQUEST_TYPES = RequestType.values();
        private final EnumMap<RequestType, NavigableMap<LaneKey, ArrayDeque<Request>>> requests =
                new EnumMap<>(RequestType.class);
        private final EnumMap<RequestType, LaneKey> cursors = new EnumMap<>(RequestType.class);
        private final NavigableMap<LaneKey, ExecutionSlot> installed = new TreeMap<>();
        private final NavigableMap<BlockPos, ControllerCandidates> installedByController =
                 new TreeMap<>(DomainBucket::compareControllers);
        private final List<ControllerCandidates> pendingInstalled = new ArrayList<>();
        private final BitSet pendingControllers = new BitSet();
        private boolean controllersChanged;
        private boolean pendingAdditions;
        private long pendingRound = Long.MIN_VALUE;
        private long pendingTime = Long.MIN_VALUE;
        private final EnumMap<RequestType, Long2ObjectOpenHashMap<String>> controllerLaneCursors = new EnumMap<>(RequestType.class);
        private int nextType;
        private boolean worksetInFlight;

        private DomainBucket() {
            for (RequestType type : REQUEST_TYPES) {
                requests.put(type, new TreeMap<>());
                controllerLaneCursors.put(type, new Long2ObjectOpenHashMap<>());
            }
        }

        private Object nextWork(Set<Request> attempted, Set<Request> eligible, ServerLevel level,
                                SharedIoCoordinator coordinator) {
            if (level != null) return nextLiveWork(attempted, eligible, level, coordinator);
            RequestType[] types = REQUEST_TYPES;
            for (int typeOffset = 0; typeOffset < types.length; typeOffset++) {
                int typeIndex = (nextType + typeOffset) % types.length;
                RequestType type = types[typeIndex];
                NavigableMap<LaneKey, ArrayDeque<Request>> lanes = requests.get(type);
                if (lanes.isEmpty()) continue;
                List<LaneKey> keys = new ArrayList<>(lanes.keySet());
                LaneKey cursor = cursors.get(type);
                int start = 0;
                if (cursor != null) {
                    while (start < keys.size() && keys.get(start).compareTo(cursor) <= 0) start++;
                    if (start == keys.size()) start = 0;
                }
                for (int laneOffset = 0; laneOffset < keys.size(); laneOffset++) {
                    LaneKey lane = keys.get((start + laneOffset) % keys.size());
                    for (Request request : lanes.get(lane)) {
                        if (attempted.contains(request) || !eligible.contains(request)) continue;
                        nextType = (typeIndex + 1) % types.length;
                        return request;
                    }
                }
            }
            return null;
        }

        private Object nextLiveWork(Set<Request> attempted, Set<Request> eligible, ServerLevel level,
                                    SharedIoCoordinator coordinator) {
            if (requests.get(RequestType.START).isEmpty() && requests.get(RequestType.TICK).isEmpty()
                    && requests.get(RequestType.FINISH).isEmpty()) {
                ExecutionSlot execution = nextInstalled(level, coordinator);
                if (execution != null) nextType = (RequestType.TICK.ordinal() + 1) % REQUEST_TYPES.length;
                return execution;
            }
            RequestType[] types = REQUEST_TYPES;
            for (int offset = 0; offset < types.length; offset++) {
                int index = (nextType + offset) % types.length;
                RequestType type = types[index];
                Request selected = null;
                for (ArrayDeque<Request> queue : requests.get(type).values()) for (Request request : queue) {
                    if (attempted.contains(request) || !eligible.contains(request) || !coordinator.qualifiedRequest(request, level)) continue;
                    if (selected == null || laneOrder(request.laneKey(), selected.laneKey(), type) < 0) selected = request;
                }
                ExecutionSlot execution = type == RequestType.TICK ? nextInstalled(level, coordinator) : null;
                if (selected == null && execution == null) continue;
                nextType = (index + 1) % types.length;
                return execution != null && (selected == null
                        || laneOrder(execution.lane.key, selected.laneKey(), type) < 0) ? execution : selected;
            }
            return null;
        }

        private int laneOrder(LaneKey first, LaneKey second, RequestType type) {
            LaneKey cursor = cursors.get(type);
            int controllerOrder = compareControllers(first.controllerPos(), second.controllerPos());
            if (controllerOrder != 0) {
                if (cursor != null) {
                    boolean firstWraps = compareControllers(first.controllerPos(), cursor.controllerPos()) <= 0;
                    boolean secondWraps = compareControllers(second.controllerPos(), cursor.controllerPos()) <= 0;
                    if (firstWraps != secondWraps) return firstWraps ? 1 : -1;
                }
                return controllerOrder;
            }
            String laneCursor = controllerLaneCursors.get(type).get(first.controllerPos().asLong());
            if (laneCursor != null) {
                boolean firstWraps = first.laneId().compareTo(laneCursor) <= 0;
                boolean secondWraps = second.laneId().compareTo(laneCursor) <= 0;
                if (firstWraps != secondWraps) return firstWraps ? 1 : -1;
            }
            return first.laneId().compareTo(second.laneId());
        }

        private static int compareControllers(BlockPos first, BlockPos second) {
            int order = Integer.compare(first.getX(), second.getX());
            if (order == 0) order = Integer.compare(first.getY(), second.getY());
            return order == 0 ? Integer.compare(first.getZ(), second.getZ()) : order;
        }

        private void markAttempted(RequestType type, LaneKey lane) {
            cursors.put(type, lane);
            controllerLaneCursors.get(type).put(lane.controllerPos().asLong(), lane.laneId());
        }

        private void putInstalled(ExecutionSlot slot) {
            LaneKey key = slot.lane.key;
            installed.put(key, slot);
            ControllerCandidates controller = installedByController.get(key.controllerPos());
            if (controller == null) {
                controller = new ControllerCandidates(key.controllerPos());
                installedByController.put(key.controllerPos(), controller);
                controllersChanged = true;
            }
            controller.lanes.put(key.laneId(), slot);
            controller.changed = true;
            addPending(slot);
        }

        private void preparePending(long round, long gameTime) {
            if (pendingRound == round && pendingTime == gameTime && !pendingAdditions) return;
            pendingRound = round;
            pendingTime = gameTime;
            pendingAdditions = false;
            if (controllersChanged) {
                pendingInstalled.clear();
                for (ControllerCandidates controller : installedByController.values()) pendingInstalled.add(controller);
                controllersChanged = false;
            }
            pendingControllers.clear();
            for (int index = 0; index < pendingInstalled.size(); index++) {
                ControllerCandidates controller = pendingInstalled.get(index);
                controller.index = index;
                controller.prepare(round, gameTime);
                if (!controller.pending.isEmpty()) pendingControllers.set(index);
            }
        }

        private void addPending(ExecutionSlot slot) {
            LaneKey key = slot.lane.key;
            ControllerCandidates controller = installedByController.get(key.controllerPos());
            if (controller == null) return;
            // Install/wake admissions are batched before the next selection, not sorted per lane.
            if (controller.changed) pendingAdditions = true;
            if (pendingRound != Long.MIN_VALUE && (slot.lane.lastAttemptRound == pendingRound
                    || slot.lane.lastAttemptTime == pendingTime)) return;
            slot.consumedRound = Long.MIN_VALUE;
            slot.consumedTime = Long.MIN_VALUE;
            if (!controller.changed) {
                controller.pending.set(slot.pendingIndex);
                pendingControllers.set(controller.index);
            }
        }

        private void removePending(ExecutionSlot slot) {
            slot.consumedRound = pendingRound;
            slot.consumedTime = pendingTime;
            ControllerCandidates controller = installedByController.get(slot.lane.key.controllerPos());
            if (controller == null || controller.lanes.get(slot.lane.key.laneId()) != slot) return;
            controller.pending.clear(slot.pendingIndex);
            if (controller.pending.isEmpty()) pendingControllers.clear(controller.index);
        }

        private void removeInstalled(ExecutionSlot slot) {
            LaneKey key = slot.lane.key;
            installed.remove(key, slot);
            removePending(slot);
            ControllerCandidates controller = installedByController.get(key.controllerPos());
            if (controller == null || !controller.lanes.remove(key.laneId(), slot)) return;
            controller.changed = true;
            if (controller.lanes.isEmpty()) {
                installedByController.remove(key.controllerPos());
                controllersChanged = true;
            }
            // Keep this round's ordinals stable. The next round (or admission) compacts once.
        }

        private ExecutionSlot nextInstalled(ServerLevel level, SharedIoCoordinator coordinator) {
            preparePending(coordinator.executionRoundId, level.getGameTime());
            if (pendingControllers.isEmpty()) return null;
            LaneKey cursor = cursors.get(RequestType.TICK);
            int low = 0, high = pendingInstalled.size();
            if (cursor != null) while (low < high) {
                int middle = (low + high) >>> 1;
                if (compareControllers(pendingInstalled.get(middle).position, cursor.controllerPos()) <= 0) low = middle + 1;
                else high = middle;
            }
            int index = pendingControllers.nextSetBit(low);
            if (index < 0) index = pendingControllers.nextSetBit(0);
            while (index >= 0) {
                ControllerCandidates controller = pendingInstalled.get(index);
                int laneStart = controller.after(controllerLaneCursors.get(RequestType.TICK).get(controller.position.asLong()));
                int laneIndex = controller.pending.nextSetBit(laneStart);
                if (laneIndex < 0) laneIndex = controller.pending.nextSetBit(0);
                while (laneIndex >= 0) {
                    ExecutionSlot slot = controller.ordered.get(laneIndex);
                    if (slot.execution.owner().controller().activeWorkMode() == MachineWorkMode.SYNC) {
                        controller.pending.clear();
                        break;
                    }
                    if (coordinator.eligibleExecution(slot, level, false)) return slot;
                    removePending(slot);
                    laneIndex = controller.pending.nextSetBit(laneStart);
                    if (laneIndex < 0) laneIndex = controller.pending.nextSetBit(0);
                }
                pendingControllers.clear(index);
                index = pendingControllers.nextSetBit(index + 1);
                if (index < 0) index = pendingControllers.nextSetBit(0);
            }
            return null;
        }

        /** Membership-sorted buffers and reusable per-round eligibility bits. @author howxu <dev@howxu.cn> */
        private static final class ControllerCandidates {
            private final BlockPos position;
            private final NavigableMap<String, ExecutionSlot> lanes = new TreeMap<>();
            private final List<ExecutionSlot> ordered = new ArrayList<>();
            private final List<String> orderedLaneIds = new ArrayList<>();
            private final BitSet pending = new BitSet();
            private int index;
            private boolean changed = true;
            private long round = Long.MIN_VALUE;
            private long time = Long.MIN_VALUE;

            private ControllerCandidates(BlockPos position) { this.position = position; }

            private void prepare(long round, long time) {
                if (!changed && this.round == round && this.time == time) return;
                if (changed) {
                    ordered.clear();
                    orderedLaneIds.clear();
                    // A retired logical lane can be renamed before its old receipt is ACKed.
                    // Keep the sorted key stable even while a tombstone remains in this buffer.
                    for (Map.Entry<String, ExecutionSlot> entry : lanes.entrySet()) {
                        ordered.add(entry.getValue());
                        orderedLaneIds.add(entry.getKey());
                    }
                    changed = false;
                }
                this.round = round;
                this.time = time;
                pending.clear();
                for (int index = 0; index < ordered.size(); index++) {
                    ExecutionSlot slot = ordered.get(index);
                    slot.pendingIndex = index;
                    if (slot.lane.lastAttemptRound != round && slot.lane.lastAttemptTime != time
                            && !(slot.consumedRound == round && slot.consumedTime == time)) pending.set(index);
                }
            }

            private int after(String cursor) {
                int low = 0, high = ordered.size();
                if (cursor != null) while (low < high) {
                    int middle = (low + high) >>> 1;
                    if (orderedLaneIds.get(middle).compareTo(cursor) <= 0) low = middle + 1;
                    else high = middle;
                }
                return low;
            }
        }

        private void remove(Request request) {
            NavigableMap<LaneKey, ArrayDeque<Request>> lanes = requests.get(requestType(request));
            ArrayDeque<Request> queue = lanes.get(request.laneKey());
            if (queue == null) return;
            queue.removeFirstOccurrence(request);
            if (queue.isEmpty()) lanes.remove(request.laneKey());
        }

        private void markCommitted(Request request) {
            cursors.put(requestType(request), request.laneKey());
        }

        private List<Request> allRequests() {
            List<Request> result = new ArrayList<>();
            requests.values().forEach(lanes -> lanes.values().forEach(result::addAll));
            return result;
        }

        private BlockPos firstController() {
            if (!installed.isEmpty()) return installed.firstKey().controllerPos();
            for (NavigableMap<LaneKey, ArrayDeque<Request>> lanes : requests.values()) {
                if (!lanes.isEmpty()) return lanes.firstKey().controllerPos();
            }
            return null;
        }

        private boolean isDisposable() {
            return !worksetInFlight && installed.isEmpty() && cursors.isEmpty() && requests.values().stream().allMatch(Map::isEmpty);
        }
    }

    private void submitTickWorkset(StructureClaimRegistry.ResourceDomain domain, DomainKey domainKey) {
        TickWorkset workset = takeTickWorkset(domain, domainKey);
        if (workset == null) return;
        TickWorkEntry first = workset.entries().getFirst();
        submitTickWorkset(MachineAsyncCoordinator.get(first.level()), workset, new TickWorksetContinuation(workset));
    }

    private TickWorkset takeTickWorkset(StructureClaimRegistry.ResourceDomain domain, DomainKey domainKey) {
        DomainBucket bucket = domains.get(domainKey);
        ArrayDeque<TickWorkEntry> pendingEntries = pendingTickWork.get(domainKey);
        if (bucket == null || bucket.worksetInFlight || pendingEntries == null || pendingEntries.isEmpty()) return null;
        List<TickWorkEntry> entries = new ArrayList<>(Math.min(worksetEntries, pendingEntries.size()));
        while (entries.size() < worksetEntries && !pendingEntries.isEmpty()) entries.add(pendingEntries.removeFirst());
        if (pendingEntries.isEmpty()) pendingTickWork.remove(domainKey);
        bucket.worksetInFlight = true;
        long worksetId = ++nextTickWorksetId;
        TickWorkEntry first = entries.getFirst();
        MachineAsyncCoordinator.TaskKey laneKey = first.laneTaskKey();
        MachineAsyncCoordinator.TaskKey worksetKey = new MachineAsyncCoordinator.TaskKey(
                laneKey.controllerPos(), laneKey.gameTime(), laneKey.workMode(),
                "domain-tick/" + domain.id() + "/" + worksetId, laneKey.lifecycleEpoch());
        return new TickWorkset(worksetId, domainKey, worksetKey, List.copyOf(entries));
    }

    private void submitTickWorkset(MachineAsyncCoordinator coordinator, TickWorkset workset,
                                   AsyncContinuation continuation) {
        submittedTickWork.put(workset.id(), workset);
        MachineAsyncCoordinator.SubmissionResult submission = coordinator.submitDetailed(
                workset.taskKey(), continuation, this::executeTickWorksetStep,
                new MachineAsyncCoordinator.TaskHooks(
                        () -> domainStillCurrent(workset.domainKey()) && submittedTickWork.get(workset.id()) == workset,
                        (ignored, outcome) -> finishWorkset(workset.id(), outcome)));
        if (submission == MachineAsyncCoordinator.SubmissionResult.ACCEPTED) return;
        submittedTickWork.remove(workset.id(), workset);
        workset.entries().forEach(TickWorkEntry::discardWork);
        DomainBucket bucket = domains.get(workset.domainKey());
        if (bucket != null) bucket.worksetInFlight = false;
    }

    private boolean domainStillCurrent(DomainKey domainKey) {
        return domains.containsKey(domainKey);
    }

    private void finishWorkset(long worksetId, MachineAsyncCoordinator.TaskOutcome outcome) {
        TickWorkset workset = submittedTickWork.remove(worksetId);
        if (workset == null) return;
        if (!(outcome instanceof MachineAsyncCoordinator.TaskOutcome.Succeeded)) {
            workset.entries().forEach(TickWorkEntry::discardWork);
        }
        DomainBucket bucket = domains.get(workset.domainKey());
        if (bucket != null) bucket.worksetInFlight = false;
    }

    private MainThreadStep.Result executeTickWorksetStep(MachineAsyncCoordinator.TaskKey ignored,
                                                          MainThreadStep step) {
        if (!(step instanceof MainThreadStep.TickWorkset(
                long worksetId, List<AsyncRequirementPlanner.PlanResult> intents
        ))) {
            return MainThreadStep.Result.failure(new IllegalArgumentException("Unexpected domain tick workset step"));
        }
        TickWorkset workset = submittedTickWork.get(worksetId);
        if (workset == null || workset.entries().size() != intents.size()) {
            return MainThreadStep.Result.failure(new IllegalStateException("Domain tick workset became stale"));
        }
        Throwable firstFailure = null;
        IdentityHashMap<AsyncCapabilitySnapshot, AsyncCapabilitySnapshot> committedSnapshots = new IdentityHashMap<>();
        for (int index = 0; index < workset.entries().size(); index++) {
            TickWorkEntry entry = workset.entries().get(index);
            AsyncRequirementPlanner.PlanResult intent = intents.get(index);
            try {
                if (!entry.commit(intent)) continue;
                for (AsyncRequirementPlanner.PlannedOperation operation : intent.operations()) {
                    AsyncCapabilitySnapshot identity = entry.preparedPlan().capabilities()
                            .get(operation.capabilityIndex()).snapshot();
                    AsyncCapabilitySnapshot current = committedSnapshots.getOrDefault(identity, identity);
                    committedSnapshots.put(identity, AsyncRequirementPlanner.apply(current, operation.operation()));
                }
            } catch (Throwable throwable) {
                entry.failCommit();
                if (firstFailure == null) firstFailure = throwable;
                else firstFailure.addSuppressed(throwable);
                MMCR.LOG.error("Shared IO tick work commit failed: key={}", entry.laneTaskKey(), throwable);
            }
        }
        rebasePendingTickWork(workset.domainKey(), committedSnapshots);
        return firstFailure == null ? MainThreadStep.Result.success() : MainThreadStep.Result.failure(firstFailure);
    }

    private void rebasePendingTickWork(DomainKey domainKey,
                                       IdentityHashMap<AsyncCapabilitySnapshot, AsyncCapabilitySnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        ArrayDeque<TickWorkEntry> pending = pendingTickWork.get(domainKey);
        if (pending != null) pending.forEach(entry -> entry.rebaseSnapshots(snapshots));
    }

    private static List<AsyncRequirementPlanner.PlanResult> planTickWorkset(TickWorkset workset) {
        return planTickWorksetForTesting(workset.entries().stream().map(TickWorkEntry::preparedPlan).toList());
    }

    static List<AsyncRequirementPlanner.PlanResult> planTickWorksetForTesting(
            List<AsyncRequirementPlanner.PreparedPlan> preparedPlans) {
        IdentityHashMap<AsyncCapabilitySnapshot, AsyncCapabilitySnapshot> virtualSnapshots = new IdentityHashMap<>();
        List<AsyncRequirementPlanner.PlanResult> results = new ArrayList<>(preparedPlans.size());
        for (AsyncRequirementPlanner.PreparedPlan prepared : preparedPlans) {
            List<AsyncRequirementPlanner.Capability> capabilities = prepared.capabilities().stream()
                    .map(capability -> new AsyncRequirementPlanner.Capability(capability.planner(),
                            virtualSnapshots.getOrDefault(capability.snapshot(), capability.snapshot()),
                            capability.directions()))
                    .toList();
            AsyncRequirementPlanner.PlanResult result = new AsyncRequirementPlanner.PreparedPlan(
                    prepared.requirements(), capabilities, prepared.initialMainThreadRequirements()).plan();
            results.add(result);
            if (!result.mainThreadRequirements().isEmpty()) continue;
            for (AsyncRequirementPlanner.PlannedOperation operation : result.operations()) {
                AsyncCapabilitySnapshot identity = prepared.capabilities().get(operation.capabilityIndex()).snapshot();
                AsyncCapabilitySnapshot current = virtualSnapshots.getOrDefault(identity, identity);
                virtualSnapshots.put(identity, AsyncRequirementPlanner.apply(current, operation.operation()));
            }
        }
        return List.copyOf(results);
    }

    private static final class TickWorkEntry {
        private final ServerLevel level;
        private final MachineAsyncCoordinator.TaskKey laneTaskKey;
        private AsyncRequirementPlanner.PreparedPlan preparedPlan;
        private final Predicate<AsyncRequirementPlanner.PlanResult> committer;
        private final Runnable discard;
        private final AtomicBoolean terminal = new AtomicBoolean();

        private TickWorkEntry(ServerLevel level, MachineAsyncCoordinator.TaskKey laneTaskKey,
                              AsyncRequirementPlanner.PreparedPlan preparedPlan,
                              Predicate<AsyncRequirementPlanner.PlanResult> committer, Runnable discard) {
            this.level = level;
            this.laneTaskKey = laneTaskKey;
            this.preparedPlan = preparedPlan;
            this.committer = committer;
            this.discard = discard;
        }

        private ServerLevel level() { return level; }
        private MachineAsyncCoordinator.TaskKey laneTaskKey() { return laneTaskKey; }
        private AsyncRequirementPlanner.PreparedPlan preparedPlan() { return preparedPlan; }

        private void rebaseSnapshots(IdentityHashMap<AsyncCapabilitySnapshot, AsyncCapabilitySnapshot> snapshots) {
            List<AsyncRequirementPlanner.Capability> capabilities = preparedPlan.capabilities().stream()
                    .map(capability -> new AsyncRequirementPlanner.Capability(capability.planner(),
                            snapshots.getOrDefault(capability.snapshot(), capability.snapshot()),
                            capability.directions()))
                    .toList();
            preparedPlan = new AsyncRequirementPlanner.PreparedPlan(preparedPlan.requirements(), capabilities,
                    preparedPlan.initialMainThreadRequirements());
        }

        private synchronized boolean commit(AsyncRequirementPlanner.PlanResult intent) {
            if (terminal.get()) return false;
            boolean committed = committer.test(intent);
            terminal.set(true);
            return committed;
        }

        private synchronized void failCommit() {
            if (!terminal.compareAndSet(false, true)) return;
            discard.run();
        }

        private synchronized void discardWork() {
            if (!terminal.compareAndSet(false, true)) return;
            discard.run();
        }
    }

    private record TickWorkset(long id, DomainKey domainKey, MachineAsyncCoordinator.TaskKey taskKey,
                               List<TickWorkEntry> entries) { }

    void submitTickWorksetForTesting(MachineAsyncCoordinator coordinator, AsyncContinuation continuation,
                                     int entryCount, Runnable discard) {
        List<Consumer<AsyncRequirementPlanner.PlanResult>> committers = new ArrayList<>(entryCount);
        List<Runnable> discards = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            committers.add(ignored -> { });
            discards.add(discard);
        }
        submitTickWorksetForTesting(coordinator, continuation, committers, discards);
    }

    void submitTickWorksetForTesting(MachineAsyncCoordinator coordinator,
                                     List<Consumer<AsyncRequirementPlanner.PlanResult>> committers,
                                     List<Runnable> discards) {
        submitTickWorksetForTesting(coordinator, null, committers, discards);
    }

    private void submitTickWorksetForTesting(MachineAsyncCoordinator coordinator,
                                             AsyncContinuation continuation,
                                             List<Consumer<AsyncRequirementPlanner.PlanResult>> committers,
                                             List<Runnable> discards) {
        if (committers.size() != discards.size()) throw new IllegalArgumentException("Mismatched test workset entries");
        long worksetId = ++nextTickWorksetId;
        MachineAsyncCoordinator.TaskKey key = new MachineAsyncCoordinator.TaskKey(
                BlockPos.ZERO, worksetId, MachineWorkMode.ASYNC,
                "test-domain-tick/" + worksetId);
        DomainKey domainKey = new DomainKey(worksetId, 1L);
        domains.put(domainKey, new DomainBucket());
        domains.get(domainKey).worksetInFlight = true;
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), List.of());
        List<TickWorkEntry> entries = new ArrayList<>(committers.size());
        for (int index = 0; index < committers.size(); index++) {
            Consumer<AsyncRequirementPlanner.PlanResult> committer = committers.get(index);
            entries.add(new TickWorkEntry(null, key, plan, intent -> {
                committer.accept(intent);
                return true;
            }, discards.get(index)));
        }
        TickWorkset workset = new TickWorkset(worksetId, domainKey, key, List.copyOf(entries));
        submitTickWorkset(coordinator, workset,
                continuation == null ? new TickWorksetContinuation(workset) : continuation);
    }

    int submittedTickWorkCountForTesting() {
        return submittedTickWork.size();
    }

    void enqueueTickWorkForTesting(StructureClaimRegistry.ResourceDomain domain,
                                   MachineAsyncCoordinator.TaskKey laneTaskKey,
                                   Consumer<AsyncRequirementPlanner.PlanResult> committer, Runnable discard) {
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), List.of());
        enqueueTickWorkForTesting(domain, laneTaskKey, plan, committer, discard);
    }

    void enqueueTickWorkForTesting(StructureClaimRegistry.ResourceDomain domain,
                                   MachineAsyncCoordinator.TaskKey laneTaskKey,
                                   AsyncRequirementPlanner.PreparedPlan plan,
                                   Consumer<AsyncRequirementPlanner.PlanResult> committer, Runnable discard) {
        DomainKey domainKey = new DomainKey(domain.id(), domain.generation());
        domains.computeIfAbsent(domainKey, ignored -> new DomainBucket());
        pendingTickWork.computeIfAbsent(domainKey, ignored -> new ArrayDeque<>())
                .add(new TickWorkEntry(null, laneTaskKey, plan, intent -> {
                    committer.accept(intent);
                    return true;
                }, discard));
    }

    void submitPendingTickWorksetForTesting(StructureClaimRegistry.ResourceDomain domain,
                                            MachineAsyncCoordinator coordinator) {
        TickWorkset workset = takeTickWorkset(domain, new DomainKey(domain.id(), domain.generation()));
        if (workset != null) submitTickWorkset(coordinator, workset, new TickWorksetContinuation(workset));
    }

    List<Integer> submittedTickWorkEntryCountsForTesting() {
        return submittedTickWork.values().stream().map(workset -> workset.entries().size()).toList();
    }

    int pendingTickWorkCountForTesting() {
        return pendingTickWork.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    private record TickWorksetContinuation(TickWorkset workset) implements AsyncContinuation {
        @Override
        public Yield advance(AsyncExecutionContext context) {
            List<AsyncRequirementPlanner.PlanResult> intents = planTickWorkset(workset);
            return Yield.mainThread(new MainThreadStep.TickWorkset(workset.id(), intents),
                    ignored -> ignoredContext -> Yield.complete());
        }
    }

    public sealed interface Request permits StartRequest, TickRequest, FinishRequest {
        long domainId();

        long domainGeneration();

        LaneKey laneKey();

        long controllerStructureVersion();

        long controllerStateVersion();

        LongSupplier controllerStructureVersionSupplier();

        LongSupplier controllerStateVersionSupplier();

        BooleanSupplier validator();

        default void onCommitted() {
        }

        default boolean isStillValid() {
            boolean versionsCurrent = controllerStructureVersion() == controllerStructureVersionSupplier().getAsLong()
                    && controllerStateVersion() == controllerStateVersionSupplier().getAsLong();
            boolean runtimeValid = validator().getAsBoolean();
            return versionsCurrent && runtimeValid;
        }

        default void discard() {
            validator().getAsBoolean();
        }

        boolean tryCommit();
    }

    public record LaneKey(BlockPos controllerPos, String laneId) implements Comparable<LaneKey> {
        public LaneKey {
            controllerPos = controllerPos.immutable();
        }

        @Override
        public int compareTo(LaneKey other) {
            int result = Integer.compare(controllerPos.getX(), other.controllerPos.getX());
            if (result != 0) return result;
            result = Integer.compare(controllerPos.getY(), other.controllerPos.getY());
            if (result != 0) return result;
            result = Integer.compare(controllerPos.getZ(), other.controllerPos.getZ());
            return result != 0 ? result : laneId.compareTo(other.laneId);
        }
    }

    public record StartRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                               long controllerStructureVersion, long controllerStateVersion,
                               long maximumParallelism, LongUnaryOperator transaction, LongConsumer committer,
                               BooleanSupplier validator, LongSupplier controllerStructureVersionSupplier,
                               LongSupplier controllerStateVersionSupplier, long catalogVersion,
                               LongSupplier catalogVersionSupplier, Runnable commitNotifier) implements Request {

        public StartRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                            long controllerStructureVersion, long controllerStateVersion,
                              long maximumParallelism, LongUnaryOperator transaction, LongConsumer committer,
                             BooleanSupplier validator, LongSupplier controllerStructureVersionSupplier,
                             LongSupplier controllerStateVersionSupplier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, maximumParallelism,
                    transaction, committer, validator, controllerStructureVersionSupplier,
                    controllerStateVersionSupplier, Long.MIN_VALUE, () -> Long.MIN_VALUE, () -> { });
        }

        public StartRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                            long controllerStructureVersion, long controllerStateVersion,
                             long maximumParallelism, LongUnaryOperator transaction, LongConsumer committer,
                            BooleanSupplier validator, LongSupplier controllerStructureVersionSupplier,
                            LongSupplier controllerStateVersionSupplier, long catalogVersion,
                            LongSupplier catalogVersionSupplier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, maximumParallelism,
                    transaction, committer, validator, controllerStructureVersionSupplier,
                    controllerStateVersionSupplier, catalogVersion, catalogVersionSupplier, () -> { });
        }

        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean isStillValid() {
            if (catalogVersion != catalogVersionSupplier.getAsLong()) {
                discard();
                return false;
            }
            return Request.super.isStillValid();
        }
        @Override public boolean tryCommit() {
            long granted = transaction.applyAsLong(maximumParallelism);
            if (granted <= 0L) return false;
            committer.accept(granted);
            return true;
        }

        @Override public void onCommitted() { commitNotifier.run(); }
    }

    public record TickRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                               long controllerStructureVersion, long controllerStateVersion,
                               BooleanSupplier transaction, BooleanSupplier validator,
                               LongSupplier controllerStructureVersionSupplier,
                               LongSupplier controllerStateVersionSupplier, long catalogVersion,
                               LongSupplier catalogVersionSupplier, Runnable commitNotifier) implements Request {
        public TickRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                           long controllerStructureVersion, long controllerStateVersion,
                           BooleanSupplier transaction, BooleanSupplier validator,
                           LongSupplier controllerStructureVersionSupplier,
                           LongSupplier controllerStateVersionSupplier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, transaction, validator,
                    controllerStructureVersionSupplier, controllerStateVersionSupplier,
                    Long.MIN_VALUE, () -> Long.MIN_VALUE, () -> { });
        }

        public TickRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                           long controllerStructureVersion, long controllerStateVersion,
                           BooleanSupplier transaction, BooleanSupplier validator,
                           LongSupplier controllerStructureVersionSupplier,
                           LongSupplier controllerStateVersionSupplier, Runnable commitNotifier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, transaction, validator,
                    controllerStructureVersionSupplier, controllerStateVersionSupplier,
                    Long.MIN_VALUE, () -> Long.MIN_VALUE, commitNotifier);
        }

        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean isStillValid() {
            if (catalogVersion != catalogVersionSupplier.getAsLong()) {
                discard();
                return false;
            }
            return Request.super.isStillValid();
        }
        @Override public boolean tryCommit() { return transaction.getAsBoolean(); }
        @Override public void onCommitted() { commitNotifier.run(); }
    }

    public record FinishRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                                 long controllerStructureVersion, long controllerStateVersion,
                                 BooleanSupplier transaction, BooleanSupplier validator,
                                 LongSupplier controllerStructureVersionSupplier,
                                 LongSupplier controllerStateVersionSupplier, long catalogVersion,
                                 LongSupplier catalogVersionSupplier, Runnable commitNotifier) implements Request {
        public FinishRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                             long controllerStructureVersion, long controllerStateVersion,
                             BooleanSupplier transaction, BooleanSupplier validator,
                             LongSupplier controllerStructureVersionSupplier,
                             LongSupplier controllerStateVersionSupplier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, transaction, validator,
                    controllerStructureVersionSupplier, controllerStateVersionSupplier,
                    Long.MIN_VALUE, () -> Long.MIN_VALUE, () -> { });
        }

        public FinishRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                             long controllerStructureVersion, long controllerStateVersion,
                             BooleanSupplier transaction, BooleanSupplier validator,
                             LongSupplier controllerStructureVersionSupplier,
                             LongSupplier controllerStateVersionSupplier, Runnable commitNotifier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, transaction, validator,
                    controllerStructureVersionSupplier, controllerStateVersionSupplier,
                    Long.MIN_VALUE, () -> Long.MIN_VALUE, commitNotifier);
        }

        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean isStillValid() {
            if (catalogVersion != catalogVersionSupplier.getAsLong()) {
                discard();
                return false;
            }
            return Request.super.isStillValid();
        }
        @Override public boolean tryCommit() { return transaction.getAsBoolean(); }
        @Override public void onCommitted() { commitNotifier.run(); }
    }
}
