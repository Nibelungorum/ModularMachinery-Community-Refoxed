package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bulk recipe debits must not repeat the external energy transfer operation per batch.
 *
 * @author howxu <dev@howxu.cn>
 */
class EnergyHatchCapabilityTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void nativeInputDebitsMaximumParallelismWithOneStorageChange() {
        long amount = 1_000_000_000L * Integer.MAX_VALUE;
        AtomicInteger changes = new AtomicInteger();
        LongEnergyStorage storage = new LongEnergyStorage(Long.MAX_VALUE, 1_000_000_000L, () -> {
            if (changes.incrementAndGet() > 1) throw new AssertionError("Recipe energy debit was split into batches");
        });
        storage.setAmount(amount);
        changes.set(0);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        var request = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                Integer.MAX_VALUE, amount, false);

        assertThat(capability.prepareOperation(request).commit().success()).isTrue();
        assertThat(storage.getAmountAsLong()).isZero();
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void nativeInputRetainsParallelScaledTransferAdmission() {
        LongEnergyStorage storage = new LongEnergyStorage(100L, 4L, null);
        storage.setAmount(100L);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        var rejected = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, 2L, 9L, false);
        var accepted = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, 2L, 8L, false);

        assertThat(capability.prepareOperation(rejected).commit().success()).isFalse();
        assertThat(storage.getAmountAsLong()).isEqualTo(100L);
        assertThat(capability.prepareOperation(accepted).commit().success()).isTrue();
        assertThat(storage.getAmountAsLong()).isEqualTo(92L);
        assertThat(storage.extractLong(8L, true)).isEqualTo(4L);
        assertThat(storage.insertLong(8L, true)).isEqualTo(4L);
        assertThat(storage.getAmountAsLong()).isEqualTo(92L);
    }

    @Test
    void asyncGroupsPreserveEachCompressedTransferBudget() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicInteger changes = new AtomicInteger();
            LongEnergyStorage storage = new LongEnergyStorage(10_000_000_000L, 1_000_000_000L, changes::incrementAndGet);
            storage.setAmount(10_000_000_000L);
            changes.set(0);
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
            AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
            var first = new AsyncCapabilityOperation.Scalar(capability.type().id(), 4_000_000_000L, false, 4L);
            var second = new AsyncCapabilityOperation.Scalar(capability.type().id(), 2_000_000_000L, false, 2L);

            assertThat(facet.commit(new AsyncCapabilityOperation.Group(List.of(first, second))).success()).isTrue();
            assertThat(storage.getAmountAsLong()).isEqualTo(4_000_000_000L);
            assertThat(changes.get()).isEqualTo(1);
            assertThat(facet.commit(new AsyncCapabilityOperation.Scalar(capability.type().id(),
                    2_000_000_000L, false)).success()).isFalse();
            assertThat(storage.getAmountAsLong()).isEqualTo(4_000_000_000L);
            assertThat(changes.get()).isEqualTo(1);
        }
    }

    @Test
    void plannedAsyncInputCommitsTheWholeAdmittedAmount() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            long amount = 1_000_000_000L * Integer.MAX_VALUE;
            LongEnergyStorage storage = new LongEnergyStorage(Long.MAX_VALUE, 1_000_000_000L, null);
            storage.setAmount(amount);
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
            AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
            var request = new AsyncCapabilityRequest.Scalar(capability.type().id(), Integer.MAX_VALUE, amount, false);
            var planned = facet.workerPlanner().plan(facet.captureSnapshot(), request).orElseThrow();

            assertThat(facet.commit(planned).success()).isTrue();
            assertThat(storage.getAmountAsLong()).isZero();
        }
    }

    @Test
    void zeroTransferBudgetStillRejectsNativeInput() {
        LongEnergyStorage storage = new LongEnergyStorage(100L, 0L, null);
        storage.setAmount(100L);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        var request = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, Integer.MAX_VALUE, 1L, false);

        assertThat(capability.prepareOperation(request).commit().success()).isFalse();
        assertThat(storage.getAmountAsLong()).isEqualTo(100L);
    }

    @Test
    void insufficientNativeInputDoesNotDebitStorage() {
        AtomicInteger changes = new AtomicInteger();
        LongEnergyStorage storage = new LongEnergyStorage(100L, 4L, changes::incrementAndGet);
        storage.setAmount(7L);
        changes.set(0);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        var request = new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, 2L, 8L, false);

        assertThat(capability.prepareOperation(request).commit().success()).isFalse();
        assertThat(storage.getAmountAsLong()).isEqualTo(7L);
        assertThat(changes.get()).isZero();
    }
}
