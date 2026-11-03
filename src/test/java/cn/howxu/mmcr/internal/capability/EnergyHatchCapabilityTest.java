package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recipe debits retain parallel-scaled admission and caller-owned transaction semantics.
 *
 * @author howxu <dev@howxu.cn>
 */
class EnergyHatchCapabilityTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void nativeInputDebitsExtremeParallelismWithOneCommitNotificationAndSupportsRollback() {
        long amount = 1_000_000_000L * Integer.MAX_VALUE;
        AtomicInteger changes = new AtomicInteger();
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, 1_000_000_000L, changes::incrementAndGet);
        storage.setAmount(amount);
        changes.set(0);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        var request = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                Integer.MAX_VALUE, amount, false);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(capability.prepareOperation(request).commit(transaction).success()).isTrue();
            assertThat(storage.amount()).isZero();
            assertThat(changes.get()).isZero();
        }
        assertThat(storage.amount()).isEqualTo(amount);
        assertThat(changes.get()).isZero();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(capability.prepareOperation(request).commit(transaction).success()).isTrue();
            transaction.commit();
        }
        assertThat(storage.amount()).isZero();
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void nativeInputRetainsParallelScaledAdmissionAndExternalTransferLimit() {
        LongValueStorage storage = new LongValueStorage(100L, 4L, null);
        storage.setAmount(100L);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        var rejected = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, 2L, 9L, false);
        var accepted = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, 2L, 8L, false);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(capability.prepareOperation(rejected).commit(transaction).success()).isFalse();
            transaction.commit();
        }
        assertThat(storage.amount()).isEqualTo(100L);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(capability.prepareOperation(accepted).commit(transaction).success()).isTrue();
            transaction.commit();
        }
        assertThat(storage.amount()).isEqualTo(92L);
        assertThat(storage.extract(8L, true)).isEqualTo(4L);
        assertThat(storage.insert(8L, true)).isEqualTo(4L);
        assertThat(storage.amount()).isEqualTo(92L);
    }

    @Test
    void plannedAsyncInputCommitsTheWholeAdmittedAmount() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            long amount = 1_000_000_000L * Integer.MAX_VALUE;
            LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, 1_000_000_000L, null);
            storage.setAmount(amount);
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
            AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
            var request = new AsyncCapabilityRequest.Scalar(capability.type().id(), Integer.MAX_VALUE, amount, false);
            var planned = facet.workerPlanner().plan(facet.captureSnapshot(), request).orElseThrow();

            try (Transaction transaction = Transaction.openRoot()) {
                assertThat(facet.commit(planned, transaction).success()).isTrue();
                transaction.commit();
            }
            assertThat(storage.amount()).isZero();
        }
    }

    @Test
    void asyncGroupsPreserveCompressedBudgetsAndRollbackEarlierChildrenOnFailure() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicInteger changes = new AtomicInteger();
            LongValueStorage storage = new LongValueStorage(10_000_000_000L, 1_000_000_000L, changes::incrementAndGet);
            storage.setAmount(10_000_000_000L);
            changes.set(0);
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
            AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
            var first = new AsyncCapabilityOperation.Scalar(capability.type().id(), 4_000_000_000L, false, 4L);
            var second = new AsyncCapabilityOperation.Scalar(capability.type().id(), 2_000_000_000L, false, 2L);
            var invalid = new AsyncCapabilityOperation.Scalar(capability.type().id(), 2_000_000_000L, false);

            try (Transaction transaction = Transaction.openRoot()) {
                assertThat(facet.commit(new AsyncCapabilityOperation.Group(List.of(first, invalid)), transaction)
                        .success()).isFalse();
                transaction.commit();
            }
            assertThat(storage.amount()).isEqualTo(10_000_000_000L);
            assertThat(changes.get()).isZero();
            try (Transaction transaction = Transaction.openRoot()) {
                assertThat(facet.commit(new AsyncCapabilityOperation.Group(List.of(first, second)), transaction)
                        .success()).isTrue();
                transaction.commit();
            }
            assertThat(storage.amount()).isEqualTo(4_000_000_000L);
            assertThat(changes.get()).isEqualTo(1);
        }
    }

    @Test
    void insufficientStorageAndZeroTransferBudgetRejectWithoutDebiting() {
        for (long limit : new long[]{0L, 4L}) {
            LongValueStorage storage = new LongValueStorage(100L, limit, null);
            storage.setAmount(7L);
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
            var request = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, 2L, 8L, false);
            try (Transaction transaction = Transaction.openRoot()) {
                assertThat(capability.prepareOperation(request).commit(transaction).success()).isFalse();
                transaction.commit();
            }
            assertThat(storage.amount()).isEqualTo(7L);
        }
    }
}
