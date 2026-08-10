package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
                maximum -> Math.min(maximum, 8), granted -> committed.add("A:" + granted)));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(B, "base"), 21L, 8,
                maximum -> Math.min(maximum, 2), granted -> committed.add("B:" + granted)));

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
                ignored -> 1, ignored -> committed.add("A")));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(B, "base"), 1L, 1,
                ignored -> 0, ignored -> committed.add("B")));
        coordinator.resolve(domain);
        committed.clear();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(A, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("A")));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(B, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("B")));
        coordinator.resolve(domain);

        assertThat(committed).containsExactly("B", "A");
    }

    @Test
    void startTickAndFinishRequestsUseIndependentCursors() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = new StructureClaimRegistry.ResourceDomain(6L, 1L, Set.of(A, B, C));
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, new SharedIoCoordinator.LaneKey(A, "base"), 1L, 1,
                ignored -> 1, ignored -> committed.add("initial-start")));
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, new SharedIoCoordinator.LaneKey(B, "base"), 1L,
                () -> { committed.add("initial-tick"); return true; }, () -> true));
        coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, new SharedIoCoordinator.LaneKey(C, "base"), 1L,
                () -> { committed.add("initial-finish"); return true; }, () -> true));
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
    void staleGenerationNeverCallsTheCommitter() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        AtomicBoolean committed = new AtomicBoolean();
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(new StructureClaimRegistry.ResourceDomain(2L, 3L, Set.of(A)),
                new SharedIoCoordinator.LaneKey(A, "base"), 7L, 4, ignored -> 4, ignored -> committed.set(true)));

        coordinator.resolve(new StructureClaimRegistry.ResourceDomain(2L, 4L, Set.of(A)));

        assertThat(committed).isFalse();
    }

    private static void enqueueAllRequestTypes(SharedIoCoordinator coordinator, StructureClaimRegistry.ResourceDomain domain,
                                                List<String> committed) {
        for (BlockPos pos : List.of(A, B, C)) {
            String lane = pos.equals(A) ? "A" : pos.equals(B) ? "B" : "C";
            SharedIoCoordinator.LaneKey laneKey = new SharedIoCoordinator.LaneKey(pos, "base");
            coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, laneKey, 1L, 1,
                    ignored -> 1, ignored -> committed.add("start:" + lane)));
            coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, laneKey, 1L,
                    () -> { committed.add("tick:" + lane); return true; }, () -> true));
            coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, laneKey, 1L,
                    () -> { committed.add("finish:" + lane); return true; }, () -> true));
        }
    }
}
