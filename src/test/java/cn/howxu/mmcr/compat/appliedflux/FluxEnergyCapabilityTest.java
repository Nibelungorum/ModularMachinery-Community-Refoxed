package cn.howxu.mmcr.compat.appliedflux;

import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyOutputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies local-only Flux capability planning contracts.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluxEnergyCapabilityTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void outputAdmissionCountsExistingPendingEnergyBeforeNewOutput() {
        FluxEnergyBuffer pending = new FluxEnergyBuffer();
        pending.insert(40L);
        FluxEnergyOutputCapability capability = new FluxEnergyOutputCapability(pending);
        capability.refreshAdmissionBudget(100L);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(capability.outputCapacity(reservations)).isEqualTo(60L);
        EnergyOutputAdmissionFacet.OutputPlan partial = capability.planOutput(70L, reservations, true);
        assertThat(partial.accepted()).isZero();
        assertThat(partial.operation()).isNull();
        EnergyOutputAdmissionFacet.OutputPlan plan = capability.planOutput(60L, reservations, true);
        assertThat(plan.accepted()).isEqualTo(60L);
        assertThat(plan.operation().commit().success()).isTrue();

        assertThat(pending.amount()).isEqualTo(100L);
        assertThat(capability.outputCapacity(new PlanningReservations())).isZero();
    }

    @Test
    void inputOperationCommitsAgainstLocalEnergy() {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        buffer.insert(20L);
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input");

        assertThat(capability.prepareOperation(new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                1L, 7L, false)).commit().success()).isTrue();
        assertThat(buffer.amount()).isEqualTo(13L);
    }

    @Test
    void inputOperationLeavesLocalEnergyUntouchedWhenTheRequestIsMissing() {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        buffer.insert(10L);
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input");

        assertThat(capability.prepareOperation(new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                1L, 11L, false)).commit().success()).isFalse();

        assertThat(buffer.amount()).isEqualTo(10L);
    }

    @Test
    void injectedPrefetchImmediatelyMarksLocalEnergyReserved() {
        LongValueStorage network = new LongValueStorage(100L, 100L, null);
        network.setAmount(100L);
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input",
                requested -> Optional.of(() -> {
                    assertThat(network.extract(requested, false)).isEqualTo(requested);
                    return CapabilityResult.successful();
                }));
        var plan = capability.planPrefetch(25L).orElseThrow();

        assertThat(plan.operation().commit().success()).isTrue();
        assertThat(buffer.amount()).isEqualTo(25L);
        assertThat(buffer.reserved()).isEqualTo(25L);
        assertThat(network.amount()).isEqualTo(75L);
    }

    @Test
    void failed_native_prefetch_retains_foreign_bridge_mutation_without_inventing_local_energy() {
        LongValueStorage network = new LongValueStorage(100L, 100L, null);
        network.setAmount(10L);
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input",
                requested -> Optional.of(() -> {
                    assertThat(network.extract(requested, false)).isEqualTo(10L);
                    return CapabilityResult.failure(ExecutionStatus.blocked(
                            BuiltinFailureReasons.MISSING_INPUT.id(), BuiltinFailureReasons.MISSING_INPUT.id(),
                            FailureOccurrence.at(BuiltinFailureReasons.MISSING_INPUT,
                                    BuiltinFailureReasons.MISSING_INPUT.id(),
                                    FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
                }));
        var plan = capability.planPrefetch(25L).orElseThrow();

        assertThat(plan.operation().commit().success()).isFalse();

        assertThat(network.amount()).isZero();
        assertThat(buffer.amount()).isZero();
        assertThat(buffer.reserved()).isZero();
        assertThat(buffer.idleExcess()).isZero();
    }

    @Test
    void prefetched_reservation_consumes_saves_restores_and_releases_without_network_reextraction() {
        LongValueStorage network = new LongValueStorage(100L, 100L, null);
        network.setAmount(100L);
        AtomicLong bridgeCalls = new AtomicLong();
        FluxEnergyInputCapability.ExactPrefetchBridge bridge = requested -> Optional.of(() -> {
            bridgeCalls.incrementAndGet();
            assertThat(network.extract(requested, false)).isEqualTo(requested);
            return CapabilityResult.successful();
        });
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input", bridge);
        var plan = capability.planPrefetch(30L).orElseThrow();
        assertThat(network.amount()).isEqualTo(100L);
        assertThat(buffer.reserved()).isZero();
        assertThat(plan.operation().commit().success()).isTrue();
        assertThat(capability.consumeReservation(7L).success()).isTrue();
        assertThat(buffer.reserved()).isEqualTo(23L);

        CompoundTag output = new CompoundTag();
        HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.empty());
        capability.save(output, registries);
        FluxEnergyBuffer restoredBuffer = new FluxEnergyBuffer();
        FluxEnergyInputCapability restored = new FluxEnergyInputCapability(restoredBuffer, "test-input", bridge);
        restored.load(output, registries);
        restored.restoreReservation(23L);
        assertThat(restoredBuffer.amount()).isEqualTo(23L);
        assertThat(restoredBuffer.reserved()).isEqualTo(23L);
        assertThat(restoredBuffer.idleExcess()).isZero();
        assertThat(restored.consumeReservation(7L).success()).isTrue();
        assertThat(restored.releaseReservation(16L)).isEqualTo(16L);
        assertThat(restoredBuffer.amount()).isEqualTo(16L);
        assertThat(restoredBuffer.reserved()).isZero();
        assertThat(restoredBuffer.idleExcess()).isEqualTo(16L);
        assertThat(restored.releaseReservation(16L)).isZero();
        assertThat(network.amount()).isEqualTo(70L);
        assertThat(bridgeCalls).hasValue(1L);
    }

    @Test
    void native_reservation_group_failure_keeps_consumption_and_the_previous_ordinary_commit() {
        LongValueStorage ordinary = new LongValueStorage(100L, 100L, null);
        ordinary.setAmount(100L);
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input",
                requested -> Optional.of(() -> CapabilityResult.successful()));
        var plan = capability.planPrefetch(20L).orElseThrow();
        assertThat(plan.operation().commit().success()).isTrue();
        assertThat(ordinary.extract(5L, false)).isEqualTo(5L);
        FluxEnergyInputCapability missing = new FluxEnergyInputCapability(new FluxEnergyBuffer(), "missing-input");
        assertThat(capability.consumeReservation(7L).success()).isTrue();
        assertThat(missing.consumeReservation(1L).success()).isFalse();

        assertThat(ordinary.amount()).isEqualTo(95L);
        assertThat(buffer.amount()).isEqualTo(13L);
        assertThat(buffer.reserved()).isEqualTo(13L);
        assertThat(buffer.idleExcess()).isZero();
    }

    @Test
    void offlineInputDoesNotExposeEnergyPrefetchFacet() {
        BooleanSupplier offline = () -> false;
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(new FluxEnergyBuffer(), "test-input",
                requested -> Optional.empty(), offline);

        assertThat(capability.facet(RecipeEnergyPrefetchFacet.class)).isEmpty();
    }

    @Test
    void prefetchDoesNotCallTheBridgeWhenTheLocalBufferCannotFitTheFullAmount() {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        buffer.setAmount(Long.MAX_VALUE - 1L);
        long[] bridgeCalls = {0L};
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input",
                requested -> Optional.of(() -> {
                    bridgeCalls[0]++;
                    return CapabilityResult.successful();
                }));
        var plan = capability.planPrefetch(2L).orElseThrow();

        assertThat(plan.operation().commit().success()).isFalse();

        assertThat(bridgeCalls[0]).isZero();
        assertThat(buffer.amount()).isEqualTo(Long.MAX_VALUE - 1L);
        assertThat(buffer.reserved()).isZero();
    }

    @Test
    void outputOperationUsesAdmissionBudgetAndStoresPendingEnergy() {
        FluxEnergyOutputCapability capability = new FluxEnergyOutputCapability(new FluxEnergyBuffer());
        capability.refreshAdmissionBudget(12L);

        assertThat(capability.prepareOperation(new CapabilityRequests.ValueRequest(capability.type(), IOType.OUTPUT,
                1L, 12L, true)).commit().success()).isTrue();
        assertThat(capability.admissionBudget()).isZero();
        assertThat(capability.storage().amount()).isEqualTo(12L);
    }

    @Test
    void outputLoadKeepsPendingEnergyAndResetsAdmissionBudget() {
        FluxEnergyOutputCapability source = new FluxEnergyOutputCapability(new FluxEnergyBuffer());
        source.refreshAdmissionBudget(35L);
        assertThat(source.prepareOperation(new CapabilityRequests.ValueRequest(source.type(), IOType.OUTPUT,
                1L, 35L, true)).commit().success()).isTrue();
        source.refreshAdmissionBudget(100L);
        CompoundTag output = new CompoundTag();
        HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.empty());
        source.save(output, registries);
        FluxEnergyOutputCapability restored = new FluxEnergyOutputCapability(new FluxEnergyBuffer());
        restored.load(output, registries);

        assertThat(restored.storage().amount()).isEqualTo(35L);
        assertThat(restored.admissionBudget()).isZero();
        assertThat(restored.outputCapacity(new PlanningReservations())).isZero();
        assertThat(restored.prepareOperation(new CapabilityRequests.ValueRequest(restored.type(), IOType.OUTPUT,
                1L, 1L, true)).commit().success()).isFalse();
        assertThat(restored.storage().amount()).isEqualTo(35L);
    }

    @Test
    void capabilitiesDeclareEveryImplementedFacetWithoutTransferExposure() {
        FluxEnergyInputCapability input = new FluxEnergyInputCapability(new FluxEnergyBuffer(), "test-input");
        FluxEnergyOutputCapability output = new FluxEnergyOutputCapability(new FluxEnergyBuffer());

        assertThat(input.view().facets()).containsExactlyInAnyOrder(ScalarFacet.class, ValueFacet.class,
                OperationFacet.class, PresentationFacet.class, PersistenceFacet.class, SyncFacet.class,
                RecipeEnergyPrefetchFacet.class);
        assertThat(output.view().facets()).containsExactlyInAnyOrder(ScalarFacet.class, ValueFacet.class,
                OperationFacet.class, PresentationFacet.class, PersistenceFacet.class, SyncFacet.class,
                EnergyOutputAdmissionFacet.class);
        assertThat(input.facet(RecipeEnergyPrefetchFacet.class)).isPresent();
        assertThat(output.facet(EnergyOutputAdmissionFacet.class)).isPresent();
        assertThat(input.facet(TransferFacet.class)).isEmpty();
        assertThat(output.facet(TransferFacet.class)).isEmpty();
    }

}
