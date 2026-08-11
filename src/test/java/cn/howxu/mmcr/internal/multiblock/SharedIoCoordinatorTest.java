package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SharedIoCoordinatorTest {

    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final BlockPos B = new BlockPos(4, 64, 0);
    private static final BlockPos C = new BlockPos(8, 64, 0);

    @Test
    void startRequestsUseRotatingOrderAndMayReceivePartialParallelism() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(4L, 9L, Set.of(A, B));
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(A, "base"), 17L, 8,
                maximum -> Math.min(maximum, 8), granted -> committed.add("A:" + granted), () -> true, () -> 17L));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(B, "base"), 21L, 8,
                maximum -> Math.min(maximum, 2), granted -> committed.add("B:" + granted), () -> true, () -> 21L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("A:8", "B:2");
        assertThat(coordinator.nextStartLane(domain.id())).isEqualTo(new SharedIoCoordinator.LaneKey(B, "base"));
    }

    @Test
    void requeuedStartRequestsResumeAfterTheLastSuccessfulLane() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(5L, 1L, Set.of(A, B));
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(A, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("A"), () -> true, () -> 1L));
        coordinator.resolve(domain);
        committed.clear();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(A, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("A"), () -> true, () -> 1L));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(B, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("B"), () -> true, () -> 1L));
        coordinator.resolve(domain);

        assertThat(committed).containsExactly("B", "A");
    }

    @Test
    void baseLaneStartsBeforeOtherLanesOfTheSameController() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(10L, 1L, Set.of(A));
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("base"), () -> true, () -> 1L));
        coordinator.resolve(domain);
        committed.clear();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("base"), () -> true, () -> 1L));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "factory-0"), 1L, 1,
                ignored -> 1, ignored -> committed.add("factory-0"), () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("base", "factory-0");
    }

    @Test
    void startTickAndFinishRequestsUseIndependentCursors() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(6L, 1L, Set.of(A, B, C));
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(A, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("initial-start"), () -> true, () -> 1L));
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, new SharedIoCoordinator.LaneKey(B, "base"), 1L,
                () -> { committed.add("initial-tick"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, new SharedIoCoordinator.LaneKey(C, "base"), 1L,
                () -> { committed.add("initial-finish"); return true; }, () -> true, () -> 1L));
        coordinator.resolve(domain);
        committed.clear();

        enqueueAllRequestTypes(coordinator, domain, committed);
        coordinator.resolve(domain);

        assertThat(committed).containsExactly(
                "start:B", "start:C", "start:A",
                "tick:C", "tick:A", "tick:B",
                "finish:A", "finish:B", "finish:C");
    }

    @Test
    void finiteSharedEnergyAdvancesOnlyTheLaneGrantedFullEnergyAndRotatesNextTick() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(8L, 1L, Set.of(A, B));
        AtomicInteger energy = new AtomicInteger(20);
        List<String> advanced = new ArrayList<>();

        enqueueEnergyTick(coordinator, domain, A, energy, advanced);
        enqueueEnergyTick(coordinator, domain, B, energy, advanced);
        coordinator.resolve(domain);
        energy.addAndGet(15);
        enqueueEnergyTick(coordinator, domain, A, energy, advanced);
        enqueueEnergyTick(coordinator, domain, B, energy, advanced);
        coordinator.resolve(domain);

        assertThat(advanced).containsExactly("A", "B");
    }

    @Test
    void finishEnqueuedByTickCommitRunsInTheSameDomainPass() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(9L, 1L, Set.of(A));
        List<String> committed = new ArrayList<>();
        SharedIoCoordinator.LaneKey lane = new SharedIoCoordinator.LaneKey(A, "base");

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane, 1L, () -> {
            committed.add("tick");
            coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, lane, 1L,
                    () -> { committed.add("finish"); return true; }, () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("tick", "finish");
    }

    @Test
    void staleGenerationNeverCallsTheCommitter() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        AtomicBoolean committed = new AtomicBoolean();
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(new StructureClaimRegistry.ResourceDomain(2L, 3L, Set.of(A)),
                new SharedIoCoordinator.LaneKey(A, "base"), 7L, 4, ignored -> 4, ignored -> committed.set(true), () -> true, () -> 7L));

        coordinator.resolve(new StructureClaimRegistry.ResourceDomain(2L, 4L, Set.of(A)));

        assertThat(committed).isFalse();
    }

    @Test
    void staleStructureVersionNeverCallsTheCommitter() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(7L, 1L, Set.of(A));
        AtomicBoolean transactionInvoked = new AtomicBoolean();
        AtomicBoolean committed = new AtomicBoolean();
        AtomicLong currentStructureVersion = new AtomicLong(3L);
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(A, "base"), 3L, 1,
                ignored -> { transactionInvoked.set(true); return 1; }, ignored -> committed.set(true), () -> true, currentStructureVersion::get));
        currentStructureVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(transactionInvoked).isFalse();
        assertThat(committed).isFalse();
    }

    private static void enqueueAllRequestTypes(SharedIoCoordinator coordinator, StructureClaimRegistry.ResourceDomain domain,
                                                List<String> committed) {
        for (BlockPos pos : List.of(A, B, C)) {
            String lane = pos.equals(A) ? "A" : pos.equals(B) ? "B" : "C";
            SharedIoCoordinator.LaneKey laneKey = new SharedIoCoordinator.LaneKey(pos, "base");
            coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, laneKey, 1L, 1,
                    ignored -> 1, ignored -> committed.add("start:" + lane), () -> true, () -> 1L));
            coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, laneKey, 1L,
                    () -> { committed.add("tick:" + lane); return true; }, () -> true, () -> 1L));
            coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, laneKey, 1L,
                    () -> { committed.add("finish:" + lane); return true; }, () -> true, () -> 1L));
        }
    }

    private static void enqueueEnergyTick(SharedIoCoordinator coordinator, StructureClaimRegistry.ResourceDomain domain,
                                          BlockPos position, AtomicInteger energy, List<String> advanced) {
        String lane = position.equals(A) ? "A" : "B";
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, new SharedIoCoordinator.LaneKey(position, "base"), 1L,
                () -> {
                    if (energy.get() < 15) return false;
                    energy.addAndGet(-15);
                    advanced.add(lane);
                    return true;
                }, () -> true, () -> 1L));
    }
}
