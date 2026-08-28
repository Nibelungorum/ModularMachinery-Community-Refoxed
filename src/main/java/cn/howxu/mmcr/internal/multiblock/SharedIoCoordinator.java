package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

/**
 * Resolves shared multiblock IO requests once at the end of each server-level tick.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SharedIoCoordinator {
    private static final Map<ServerLevel, SharedIoCoordinator> COORDINATORS = new WeakHashMap<>();
    private static final Comparator<Request> REQUEST_ORDER = Comparator.comparing(Request::laneKey);
    private final List<Request> pending = new ArrayList<>();
    private final Map<Long, LaneKey> startCursors = new HashMap<>();
    private final Map<Long, LaneKey> tickCursors = new HashMap<>();
    private final Map<Long, LaneKey> finishCursors = new HashMap<>();

    public static synchronized SharedIoCoordinator get(ServerLevel level) {
        return COORDINATORS.computeIfAbsent(level, ignored -> new SharedIoCoordinator());
    }

    public static synchronized void discard(ServerLevel level) {
        COORDINATORS.remove(level);
    }

    public void enqueue(Request request) {
        pending.add(request);
    }

    public void resolve(ServerLevel level) {
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        Map<StructureClaimRegistry.ResourceDomain, List<Request>> requestsByDomain = new HashMap<>();
        for (Request request : List.copyOf(pending)) {
            StructureClaimRegistry.ResourceDomain domain = registry.domainFor(request.laneKey().controllerPos());
            if (domain != null && request.domainId() == domain.id() && request.domainGeneration() == domain.generation()) {
                requestsByDomain.computeIfAbsent(domain, ignored -> new ArrayList<>()).add(request);
            } else {
                request.discard();
            }
        }
        pending.clear();
        for (var entry : requestsByDomain.entrySet()) {
            pending.addAll(resolveDomain(entry.getKey(), entry.getValue()));
        }
    }

    public void resolve(StructureClaimRegistry.ResourceDomain domain) {
        List<Request> requests = pending.stream()
                .filter(request -> request.domainId() == domain.id()
                        && request.domainGeneration() == domain.generation())
                .toList();
        pending.removeAll(requests);
        pending.addAll(resolveDomain(domain, requests));
    }

    public LaneKey nextStartLane(long domainId) {
        return startCursors.get(domainId);
    }

    private List<Request> resolveDomain(StructureClaimRegistry.ResourceDomain domain, List<Request> requests) {
        List<Request> current = new ArrayList<>(requests.size());
        List<StartRequest> startRequests = new ArrayList<>();
        List<TickRequest> tickRequests = new ArrayList<>();
        List<FinishRequest> currentFinishRequests = new ArrayList<>();
        for (Request request : requests) {
            if (request.domainId() != domain.id()
                    || request.domainGeneration() != domain.generation()
                    || !request.isStillValid()) {
                continue;
            }
            current.add(request);
            if (request instanceof StartRequest startRequest) {
                startRequests.add(startRequest);
            } else if (request instanceof TickRequest tickRequest) {
                tickRequests.add(tickRequest);
            } else if (request instanceof FinishRequest finishRequest) {
                currentFinishRequests.add(finishRequest);
            }
        }
        current.sort(REQUEST_ORDER);
        startRequests.sort(REQUEST_ORDER);
        tickRequests.sort(REQUEST_ORDER);
        currentFinishRequests.sort(REQUEST_ORDER);
        Set<Request> successful = Collections.newSetFromMap(new IdentityHashMap<>());
        resolveRoundRobin(startRequests, startCursors, domain.id(), successful);
        resolveRoundRobin(tickRequests, tickCursors, domain.id(), successful);
        Set<StartRequest> pendingStartsBeforeFinish = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Request request : pending) {
            if (request instanceof StartRequest startRequest
                    && startRequest.domainId() == domain.id()
                    && startRequest.domainGeneration() == domain.generation()) {
                pendingStartsBeforeFinish.add(startRequest);
            }
        }
        List<FinishRequest> finishRequests = new ArrayList<>(currentFinishRequests);
        finishRequests.addAll(takePendingFinishRequests(domain));
        resolveRoundRobin(finishRequests, finishCursors, domain.id(), successful);
        List<StartRequest> finishSpawnedStarts = takePendingStartRequests(domain, pendingStartsBeforeFinish);
        resolveRoundRobin(finishSpawnedStarts, startCursors, domain.id(), successful);
        List<Request> unresolved = new ArrayList<>(current.size());
        for (Request request : current) {
            if (!successful.contains(request)) unresolved.add(request);
        }
        for (FinishRequest request : finishRequests) {
            if (!current.contains(request) && !successful.contains(request)) unresolved.add(request);
        }
        for (StartRequest request : finishSpawnedStarts) {
            if (!successful.contains(request)) unresolved.add(request);
        }
        return unresolved;
    }

    private List<FinishRequest> takePendingFinishRequests(StructureClaimRegistry.ResourceDomain domain) {
        List<FinishRequest> result = new ArrayList<>();
        for (Request request : pending) {
            if (request instanceof FinishRequest finishRequest
                    && finishRequest.domainId() == domain.id()
                    && finishRequest.domainGeneration() == domain.generation()) {
                result.add(finishRequest);
            }
        }
        pending.removeAll(result);
        return result;
    }

    private List<StartRequest> takePendingStartRequests(StructureClaimRegistry.ResourceDomain domain, Set<StartRequest> excluded) {
        List<StartRequest> matching = new ArrayList<>();
        for (Request request : pending) {
            if (request instanceof StartRequest startRequest
                    && startRequest.domainId() == domain.id()
                    && startRequest.domainGeneration() == domain.generation()
                    && !excluded.contains(startRequest)) {
                matching.add(startRequest);
            }
        }
        pending.removeAll(matching);
        matching.removeIf(request -> !request.isStillValid());
        matching.sort(REQUEST_ORDER);
        return matching;
    }

    private <T extends Request> void resolveRoundRobin(List<T> requests, Map<Long, LaneKey> cursors, long domainId, Set<Request> successful) {
        if (requests.isEmpty()) return;
        LaneKey cursor = cursors.get(domainId);
        int start = 0;
        if (cursor != null) {
            while (start < requests.size()
                    && compareControllerPos(requests.get(start).laneKey().controllerPos(), cursor.controllerPos()) <= 0) start++;
            if (start == requests.size()) start = 0;
        }
        for (int offset = 0; offset < requests.size(); offset++) {
            T request = requests.get((start + offset) % requests.size());
            if (!request.isStillValid()) {
                successful.add(request);
                continue;
            }
            if (request.tryCommit()) {
                request.onCommitted();
                cursors.put(domainId, request.laneKey());
                successful.add(request);
            }
        }
    }

    private static int compareControllerPos(BlockPos first, BlockPos second) {
        int result = Integer.compare(first.getX(), second.getX());
        if (result != 0) return result;
        result = Integer.compare(first.getY(), second.getY());
        return result != 0 ? result : Integer.compare(first.getZ(), second.getZ());
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
            boolean valid = controllerStructureVersion() == controllerStructureVersionSupplier().getAsLong()
                    && controllerStateVersion() == controllerStateVersionSupplier().getAsLong()
                    && validator().getAsBoolean();
            if (!valid) discard();
            return valid;
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
                               int maximumParallelism, IntUnaryOperator transaction, IntConsumer committer,
                               BooleanSupplier validator, LongSupplier controllerStructureVersionSupplier,
                               LongSupplier controllerStateVersionSupplier, long catalogVersion,
                               LongSupplier catalogVersionSupplier, Runnable commitNotifier) implements Request {

        public StartRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                            long controllerStructureVersion, long controllerStateVersion,
                             int maximumParallelism, IntUnaryOperator transaction, IntConsumer committer,
                             BooleanSupplier validator, LongSupplier controllerStructureVersionSupplier,
                             LongSupplier controllerStateVersionSupplier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, maximumParallelism,
                    transaction, committer, validator, controllerStructureVersionSupplier,
                    controllerStateVersionSupplier, Long.MIN_VALUE, () -> Long.MIN_VALUE, () -> { });
        }

        public StartRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                            long controllerStructureVersion, long controllerStateVersion,
                            int maximumParallelism, IntUnaryOperator transaction, IntConsumer committer,
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
            int granted = transaction.applyAsInt(maximumParallelism);
            if (granted <= 0) return false;
            committer.accept(granted);
            return true;
        }

        @Override public void onCommitted() { commitNotifier.run(); }
    }

    public record TickRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                              long controllerStructureVersion, long controllerStateVersion,
                              BooleanSupplier transaction, BooleanSupplier validator,
                              LongSupplier controllerStructureVersionSupplier,
                              LongSupplier controllerStateVersionSupplier, Runnable commitNotifier) implements Request {
        public TickRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                           long controllerStructureVersion, long controllerStateVersion,
                           BooleanSupplier transaction, BooleanSupplier validator,
                           LongSupplier controllerStructureVersionSupplier,
                           LongSupplier controllerStateVersionSupplier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, transaction, validator,
                    controllerStructureVersionSupplier, controllerStateVersionSupplier, () -> { });
        }

        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean tryCommit() { return transaction.getAsBoolean(); }
        @Override public void onCommitted() { commitNotifier.run(); }
    }

    public record FinishRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                                long controllerStructureVersion, long controllerStateVersion,
                                BooleanSupplier transaction, BooleanSupplier validator,
                                LongSupplier controllerStructureVersionSupplier,
                                LongSupplier controllerStateVersionSupplier, Runnable commitNotifier) implements Request {
        public FinishRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey,
                             long controllerStructureVersion, long controllerStateVersion,
                             BooleanSupplier transaction, BooleanSupplier validator,
                             LongSupplier controllerStructureVersionSupplier,
                             LongSupplier controllerStateVersionSupplier) {
            this(domain, laneKey, controllerStructureVersion, controllerStateVersion, transaction, validator,
                    controllerStructureVersionSupplier, controllerStateVersionSupplier, () -> { });
        }

        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean tryCommit() { return transaction.getAsBoolean(); }
        @Override public void onCommitted() { commitNotifier.run(); }
    }
}
