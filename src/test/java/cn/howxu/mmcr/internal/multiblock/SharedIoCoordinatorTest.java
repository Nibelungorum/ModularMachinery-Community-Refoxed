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

    @Test
    void startRequestsUseRotatingOrderAndMayReceivePartialParallelism() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 17L, 8,
                maximum -> Math.min(maximum, 8), granted -> committed.add("A:" + granted), () -> true, () -> 17L));
        coordinator.enqueue(start(domain, B, 21L, 8,
                maximum -> Math.min(maximum, 2), granted -> committed.add("B:" + granted), () -> true, () -> 21L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("A:8", "B:2");
        assertThat(coordinator.nextStartLane(domain.id()))
                .isEqualTo(new SharedIoCoordinator.LaneKey(B, "base"));
    }

    @Test
    void staleStructureAndStateVersionsNeverInvokeTransactions() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong structureVersion = new AtomicLong(1L);
        AtomicLong stateVersion = new AtomicLong(1L);
        AtomicBoolean invoked = new AtomicBoolean();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 1L,
                () -> {
                    invoked.set(true);
                    return true;
                }, () -> true, structureVersion::get, stateVersion::get));
        structureVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(invoked).isFalse();
    }

    @Test
    void stateVersionInvalidationAlsoDiscardsPendingRequests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong stateVersion = new AtomicLong(1L);
        AtomicBoolean invoked = new AtomicBoolean();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 1L,
                () -> {
                    invoked.set(true);
                    return true;
                }, () -> true, () -> 1L, stateVersion::get));
        stateVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(invoked).isFalse();
    }

    @Test
    void finishRequestCanInstallAReplacementStartInTheSameDomainPass() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger starts = new AtomicInteger();

        coordinator.enqueue(finish(domain, A, 1L, () -> {
            coordinator.enqueue(start(domain, A, 1L, 1,
                    ignored -> 1, ignored -> starts.incrementAndGet(), () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(starts).hasValue(1);
    }

    @Test
    void invalidValidatorPreventsAStaleLaneFromCommittingAfterEarlierLaneChangesState() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicBoolean staleInvoked = new AtomicBoolean();
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(tick(domain, A, 1L,
                () -> {
                    committed.add("A");
                    valid.set(false);
                    return true;
                }, valid::get, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L,
                () -> {
                    staleInvoked.set(true);
                    committed.add("B");
                    return true;
                }, valid::get, () -> 1L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("A");
        assertThat(staleInvoked).isFalse();
    }

    private static StructureClaimRegistry.ResourceDomain domain(BlockPos... positions) {
        return new StructureClaimRegistry.ResourceDomain(1L, 1L, Set.of(positions));
    }

    private static SharedIoCoordinator.LaneKey lane(BlockPos position) {
        return new SharedIoCoordinator.LaneKey(position, "base");
    }

    private static SharedIoCoordinator.StartRequest start(StructureClaimRegistry.ResourceDomain domain,
                                                           BlockPos position, long structureVersion,
                                                           int maximumParallelism,
                                                           java.util.function.IntUnaryOperator transaction,
                                                           java.util.function.IntConsumer committer,
                                                           java.util.function.BooleanSupplier validator,
                                                           java.util.function.LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.StartRequest(domain, lane(position), structureVersion, 0L,
                maximumParallelism, transaction, committer, validator, structureVersionSupplier, () -> 0L);
    }

    private static SharedIoCoordinator.TickRequest tick(StructureClaimRegistry.ResourceDomain domain,
                                                         BlockPos position, long structureVersion,
                                                         java.util.function.BooleanSupplier transaction,
                                                         java.util.function.BooleanSupplier validator,
                                                         java.util.function.LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.TickRequest(domain, lane(position), structureVersion, 0L,
                transaction, validator, structureVersionSupplier, () -> 0L);
    }

    private static SharedIoCoordinator.FinishRequest finish(StructureClaimRegistry.ResourceDomain domain,
                                                             BlockPos position, long structureVersion,
                                                             java.util.function.BooleanSupplier transaction,
                                                             java.util.function.BooleanSupplier validator,
                                                             java.util.function.LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.FinishRequest(domain, lane(position), structureVersion, 0L,
                transaction, validator, structureVersionSupplier, () -> 0L);
    }
}
