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
    void staleGenerationNeverCallsTheCommitter() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        AtomicBoolean committed = new AtomicBoolean();
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(new StructureClaimRegistry.ResourceDomain(2L, 3L, Set.of(A)),
                new SharedIoCoordinator.LaneKey(A, "base"), 7L, 4, ignored -> 4, ignored -> committed.set(true)));

        coordinator.resolve(new StructureClaimRegistry.ResourceDomain(2L, 4L, Set.of(A)));

        assertThat(committed).isFalse();
    }
}
