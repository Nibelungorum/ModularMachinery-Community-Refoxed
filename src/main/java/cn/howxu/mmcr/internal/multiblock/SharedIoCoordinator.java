package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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
    private final int requestBudget;
    private final int worksetEntries;
    private long budgetGameTime = Long.MIN_VALUE;
    private int remainingRequests;
    private DomainKey domainCursor;
    private long nextTickWorksetId;

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
        COORDINATORS.remove(level);
    }

    public void enqueue(Request request) {
        DomainKey key = new DomainKey(request.domainId(), request.domainGeneration());
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
        return true;
    }

    /** Discards every queued request owned by one controller. */
    public void cancel(BlockPos controllerPos) {
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
        return resolveKnownDomains(knownDomains, true);
    }

    public int resolve(StructureClaimRegistry.ResourceDomain domain) {
        return resolveKnownDomains(Map.of(domain.id(), domain), false);
    }

    int resolveKnownDomainsForTesting(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains) {
        return resolveKnownDomains(knownDomains, true);
    }

    private int resolveKnownDomains(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains,
                                    boolean discardUnknownDomains) {
        discardStaleDomains(knownDomains, discardUnknownDomains);
        int progress = 0;
        Set<Request> attempted = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Request> eligible = Collections.newSetFromMap(new IdentityHashMap<>());
        domains.forEach((key, bucket) -> {
            StructureClaimRegistry.ResourceDomain domain = knownDomains.get(key.id);
            if (domain != null && domain.generation() == key.generation) eligible.addAll(bucket.allRequests());
        });
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            while (remainingRequests > 0) {
                DomainKey key = nextDomainWithWork(attempted, eligible);
                if (key == null) break;
                DomainBucket bucket = domains.get(key);
                Request request = bucket.nextRequest(attempted, eligible);
                attempted.add(request);
                remainingRequests--;
                domainCursor = key;
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
        for (StructureClaimRegistry.ResourceDomain domain : knownDomains.values()) {
            submitTickWorkset(domain, new DomainKey(domain.id(), domain.generation()));
        }
        removeEmptyBuckets();
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

    private DomainKey nextDomainWithWork(Set<Request> attempted, Set<Request> eligible) {
        int domainCount = domains.size();
        if (domainCount == 0) return null;
        DomainKey key = domainCursor == null ? domains.firstKey() : domains.higherKey(domainCursor);
        if (key == null) key = domains.firstKey();
        // Selection invokes no callbacks. Re-read the live topology after each request instead
        // of retaining an iterator across commits, which may add/remove domains or resolve again.
        for (int offset = 0; offset < domainCount; offset++) {
            if (domains.get(key).hasUnattempted(attempted, eligible)) return key;
            key = domains.higherKey(key);
            if (key == null) key = domains.firstKey();
        }
        return null;
    }

    private void includeSpawnedSuccessors(DomainKey key, Request request, Set<Request> eligible) {
        DomainBucket bucket = domains.get(key);
        if (bucket == null) return;
        RequestType successor = request instanceof TickRequest ? RequestType.FINISH
                : request instanceof FinishRequest ? RequestType.START : null;
        if (successor == null) return;
        bucket.requests.get(successor).values().forEach(eligible::addAll);
    }

    private void discardStaleDomains(Map<Long, StructureClaimRegistry.ResourceDomain> knownDomains,
                                     boolean discardUnknownDomains) {
        for (DomainKey key : List.copyOf(domains.keySet())) {
            StructureClaimRegistry.ResourceDomain current = knownDomains.get(key.id);
            if (current != null && current.generation() == key.generation) continue;
            if (current == null && !discardUnknownDomains) continue;
            DomainBucket bucket = domains.remove(key);
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
        private final EnumMap<RequestType, NavigableMap<LaneKey, ArrayDeque<Request>>> requests =
                new EnumMap<>(RequestType.class);
        private final EnumMap<RequestType, LaneKey> cursors = new EnumMap<>(RequestType.class);
        private int nextType;
        private boolean worksetInFlight;

        private DomainBucket() {
            for (RequestType type : RequestType.values()) requests.put(type, new TreeMap<>());
        }

        private Request nextRequest(Set<Request> attempted, Set<Request> eligible) {
            RequestType[] types = RequestType.values();
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

        private boolean hasUnattempted(Set<Request> attempted, Set<Request> eligible) {
            for (NavigableMap<LaneKey, ArrayDeque<Request>> lanes : requests.values()) {
                for (ArrayDeque<Request> queue : lanes.values()) {
                    for (Request request : queue) {
                        if (!attempted.contains(request) && eligible.contains(request)) return true;
                    }
                }
            }
            return false;
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
            for (NavigableMap<LaneKey, ArrayDeque<Request>> lanes : requests.values()) {
                if (!lanes.isEmpty()) return lanes.firstKey().controllerPos();
            }
            return null;
        }

        private boolean isDisposable() {
            return !worksetInFlight && cursors.isEmpty() && requests.values().stream().allMatch(Map::isEmpty);
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
