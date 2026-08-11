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
            }
        }
        pending.clear();
        for (var entry : requestsByDomain.entrySet()) {
            pending.addAll(resolveDomain(entry.getKey(), entry.getValue()));
        }
    }

    public void resolve(StructureClaimRegistry.ResourceDomain domain) {
        List<Request> requests = pending.stream()
                .filter(request -> request.domainId() == domain.id())
                .toList();
        pending.removeAll(requests);
        pending.addAll(resolveDomain(domain, requests));
    }

    public LaneKey nextStartLane(long domainId) {
        return startCursors.get(domainId);
    }

    private List<Request> resolveDomain(StructureClaimRegistry.ResourceDomain domain, List<Request> requests) {
        List<Request> current = requests.stream()
                .filter(request -> request.domainId() == domain.id())
                .filter(request -> request.domainGeneration() == domain.generation())
                .filter(Request::isStillValid)
                .sorted(Comparator.comparing(Request::laneKey))
                .toList();
        Set<Request> successful = Collections.newSetFromMap(new IdentityHashMap<>());
        resolveRoundRobin(current.stream().filter(StartRequest.class::isInstance).map(StartRequest.class::cast).toList(), startCursors, domain.id(), successful);
        resolveRoundRobin(current.stream().filter(TickRequest.class::isInstance).map(TickRequest.class::cast).toList(), tickCursors, domain.id(), successful);
        List<FinishRequest> finishRequests = new ArrayList<>(current.stream()
                .filter(FinishRequest.class::isInstance).map(FinishRequest.class::cast).toList());
        finishRequests.addAll(takePendingFinishRequests(domain));
        resolveRoundRobin(finishRequests, finishCursors, domain.id(), successful);
        List<Request> unresolved = new ArrayList<>(current.stream().filter(request -> !successful.contains(request)).toList());
        for (FinishRequest request : finishRequests) {
            if (!current.contains(request) && !successful.contains(request)) unresolved.add(request);
        }
        return unresolved;
    }

    private List<FinishRequest> takePendingFinishRequests(StructureClaimRegistry.ResourceDomain domain) {
        List<FinishRequest> result = pending.stream()
                .filter(FinishRequest.class::isInstance)
                .map(FinishRequest.class::cast)
                .filter(request -> request.domainId() == domain.id() && request.domainGeneration() == domain.generation())
                .toList();
        pending.removeAll(result);
        return result;
    }

    private <T extends Request> void resolveRoundRobin(List<T> requests, Map<Long, LaneKey> cursors, long domainId, Set<Request> successful) {
        if (requests.isEmpty()) return;
        LaneKey cursor = cursors.get(domainId);
        int start = 0;
        if (cursor != null) {
            while (start < requests.size() && requests.get(start).laneKey().compareTo(cursor) <= 0) start++;
            if (start == requests.size()) start = 0;
        }
        for (int offset = 0; offset < requests.size(); offset++) {
            T request = requests.get((start + offset) % requests.size());
            if (request.tryCommit()) {
                cursors.put(domainId, request.laneKey());
                successful.add(request);
            }
        }
    }

    public sealed interface Request permits StartRequest, TickRequest, FinishRequest {
        long domainId();

        long domainGeneration();

        LaneKey laneKey();

        long controllerStructureVersion();

        LongSupplier controllerStructureVersionSupplier();

        BooleanSupplier validator();

        default boolean isStillValid() {
            return controllerStructureVersion() == controllerStructureVersionSupplier().getAsLong()
                    && validator().getAsBoolean();
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

    public record StartRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey, long controllerStructureVersion,
                               int maximumParallelism, IntUnaryOperator transaction, IntConsumer committer,
                               BooleanSupplier validator, LongSupplier controllerStructureVersionSupplier) implements Request {

        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean tryCommit() {
            int granted = transaction.applyAsInt(maximumParallelism);
            if (granted <= 0) return false;
            committer.accept(granted);
            return true;
        }
    }

    public record TickRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey, long controllerStructureVersion,
                              BooleanSupplier transaction, BooleanSupplier validator,
                              LongSupplier controllerStructureVersionSupplier) implements Request {
        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean tryCommit() { return transaction.getAsBoolean(); }
    }

    public record FinishRequest(StructureClaimRegistry.ResourceDomain domain, LaneKey laneKey, long controllerStructureVersion,
                                BooleanSupplier transaction, BooleanSupplier validator,
                                LongSupplier controllerStructureVersionSupplier) implements Request {
        @Override public long domainId() { return domain.id(); }
        @Override public long domainGeneration() { return domain.generation(); }
        @Override public boolean tryCommit() { return transaction.getAsBoolean(); }
    }
}
