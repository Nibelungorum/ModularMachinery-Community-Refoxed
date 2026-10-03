package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPointHandler;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises native Point cycle accounting and the existing recipe prefetch contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluxNetworksPrefetchTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
    }

    @Test
    void repeatedSearchHintsAndMultipleProvidersShareOneCycleLimit() {
        AtomicLong time = new AtomicLong(1L);
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, time::get, () -> {});
        handler.setLimit(10L);
        handler.requestWarmup(30L);
        handler.requestWarmup(30L);
        handler.onCycleStart();
        handler.addToBuffer(6L);
        assertThat(handler.getRequest()).isEqualTo(4L);
        handler.addToBuffer(4L);
        assertThat(handler.getRequest()).isZero();
        assertThat(handler.storage().amount()).isEqualTo(10L);
        handler.onCycleEnd();
        assertThat(handler.getChange()).isZero();

        time.incrementAndGet();
        handler.onCycleStart();
        assertThat(handler.getRequest()).isZero();
        handler.requestWarmup(30L);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isEqualTo(10L);
    }

    @Test
    void searchHintsUseAHighWaterTargetIncludingHeldEnergyInsteadOfAddingRepeatedRequests() {
        FluxNetworkPointHandler handler = handler(100L);
        handler.requestWarmup(30L);
        handler.requestWarmup(30L);
        handler.requestWarmup(20L);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isEqualTo(30L);
        handler.addToBuffer(30L);
        assertThat(handler.reserveCandidate(20L)).isTrue();
        handler.requestWarmup(30L);
        handler.requestWarmup(30L);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isEqualTo(20L);
        handler.addToBuffer(20L);
        assertThat(handler.availableForPrefetch()).isEqualTo(30L);
        handler.restoreCandidateOrSavedReservation(20L);
        assertThat(handler.availableForPrefetch()).isEqualTo(50L);
    }

    @Test
    void consumptionAndRepeatedCycleStartCannotReopenUsedNetworkAllowance() {
        AtomicLong time = new AtomicLong(1L);
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, time::get, () -> {});
        handler.setLimit(10L);
        handler.requestWarmup(30L);
        handler.onCycleStart();
        handler.addToBuffer(10L);
        assertThat(handler.consumeLocal(7L)).isTrue();
        assertThat(handler.getRequest()).isZero();
        handler.onCycleStart();
        assertThat(handler.getRequest()).isZero();
        assertThatThrownBy(() -> handler.addToBuffer(1L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(handler.getBuffer()).isEqualTo(3L);
        handler.onCycleEnd();
        assertThat(handler.getChange()).isEqualTo(-7L);

        time.incrementAndGet();
        handler.requestWarmup(30L);
        handler.onCycleStart();
        handler.addToBuffer(10L);
        handler.onCycleEnd();
        assertThat(handler.getBuffer()).isEqualTo(13L);
        assertThat(handler.getChange()).isZero();
        assertThat(handler.storage().amount()).isEqualTo(13L);
    }

    @Test
    void zeroLimitBlocksReceiptsAndNativeDisableLimitAllowsRealFlow() {
        AtomicLong time = new AtomicLong(1L);
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, time::get, () -> {});
        handler.setLimit(0L);
        handler.requestWarmup(40L);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isZero();
        assertThatThrownBy(() -> handler.addToBuffer(1L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(handler.getBuffer()).isZero();

        time.incrementAndGet();
        handler.setLimit(5L);
        handler.setDisableLimit(true);
        handler.requestWarmup(40L);
        handler.onCycleStart();
        handler.addToBuffer(17L);
        handler.addToBuffer(23L);
        assertThat(handler.getRequest()).isZero();
        assertThat(handler.storage().amount()).isEqualTo(40L);
    }

    @Test
    void discardedCandidateAndCommittedRecipeDoNotDoubleDebitTheNetworkBuffer() {
        FluxNetworkPointHandler handler = handler(100L);
        FluxNetworkInputCapability capability = capability(handler);
        assertThat(capability.planPrefetch(50L)).isEmpty();
        handler.onCycleStart();
        handler.addToBuffer(handler.getRequest());
        var discarded = capability.planPrefetch(50L).orElseThrow();
        assertThat(capability.planPrefetch(1L)).isEmpty();
        capability.restoreReservation(discarded.amount());
        var selected = capability.planPrefetch(50L).orElseThrow();
        assertThat(selected.operation()).isInstanceOf(NativeCapabilityOperation.class);
        assertThat(selected.operation().commit().success()).isTrue();
        assertThat(selected.operation().commit().success()).isFalse();
        assertThat(handler.getBuffer()).isEqualTo(50L);
        assertThat(handler.reserved()).isEqualTo(50L);
        assertThat(capability.consumeReservation(10L).success()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(40L);
        assertThat(handler.reserved()).isEqualTo(40L);
        assertThat(capability.releaseReservation(40L)).isEqualTo(40L);
        assertThat(capability.releaseReservation(40L)).isZero();
        assertThat(handler.reserved()).isZero();
        assertThat(handler.availableForPrefetch()).isEqualTo(40L);
        assertThat(handler.storage().amount()).isEqualTo(40L);
    }

    @Test
    void warmupAccumulatesAcrossCyclesBeforeTheWholeRecipeCanBeReserved() {
        AtomicLong time = new AtomicLong(1L);
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, time::get, () -> {});
        handler.setLimit(10L);
        FluxNetworkInputCapability capability = capability(handler);
        for (int cycle = 0; cycle < 3; cycle++) {
            assertThat(capability.planPrefetch(25L)).isEmpty();
            handler.onCycleStart();
            handler.addToBuffer(handler.getRequest());
            handler.onCycleEnd();
            assertThat(handler.storage().amount()).isEqualTo(handler.getBuffer());
            time.incrementAndGet();
        }
        assertThat(handler.getBuffer()).isEqualTo(25L);
        assertThat(capability.planPrefetch(25L).orElseThrow().operation().commit().success()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(25L);
        assertThat(handler.reserved()).isEqualTo(25L);
        assertThat(capability.consumeReservation(25L).success()).isTrue();
        assertThat(handler.storage().amount()).isZero();
        assertThat(handler.reserved()).isZero();
    }

    @Test
    void longReservationsAndSaturatingDemandConserveEnergyWithoutIntNarrowing() {
        AtomicLong time = new AtomicLong(1L);
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, time::get, () -> {});
        handler.setDisableLimit(true);
        FluxNetworkInputCapability capability = capability(handler);
        long amount = (long) Integer.MAX_VALUE + 100L;
        handler.requestWarmup(amount);
        handler.onCycleStart();
        handler.addToBuffer(amount);
        var plan = capability.planPrefetch(amount).orElseThrow();
        assertThat(plan.operation().commit().success()).isTrue();

        time.incrementAndGet();
        handler.requestWarmup(Long.MAX_VALUE);
        handler.onCycleStart();
        handler.addToBuffer(handler.getRequest());
        assertThat(handler.getBuffer()).isEqualTo(Long.MAX_VALUE);
        assertThat(handler.getRequest()).isZero();
        assertThat(capability.consumeReservation(amount).success()).isTrue();
        assertThat(handler.reserved()).isZero();
        assertThat(handler.storage().amount()).isEqualTo(Long.MAX_VALUE - amount);
        assertThat(handler.getRequest()).isZero();
    }

    @Test
    void tentativePlansProtectEnergyAndRollbackLeavesCommittedEnergyReusable() {
        FluxNetworkPointHandler handler = handler(100L);
        handler.requestWarmup(100L);
        handler.onCycleStart();
        handler.addToBuffer(100L);
        FluxNetworkInputCapability capability = capability(handler);
        var first = capability.planPrefetch(60L).orElseThrow();
        var second = capability.planPrefetch(40L).orElseThrow();
        assertThat(capability.planPrefetch(1L)).isEmpty();
        assertThat(capability.consumeReservation(1L).success()).isFalse();
        assertThatThrownBy(() -> capability.restoreReservation(101L)).isInstanceOf(IllegalStateException.class);
        assertThat(handler.availableForPrefetch()).isZero();
        assertThat(first.operation().commit().success()).isTrue();
        assertThat(capability.consumeReservation(61L).success()).isFalse();
        assertThat(handler.getBuffer()).isEqualTo(100L);

        // The controller releases committed plans and restores uncommitted plans on start failure.
        assertThat(capability.releaseReservation(first.amount())).isEqualTo(60L);
        capability.restoreReservation(second.amount());
        assertThat(handler.reserved()).isZero();
        assertThat(handler.availableForPrefetch()).isEqualTo(100L);
        assertThat(handler.storage().amount()).isEqualTo(100L);
    }

    @Test
    void savedRestoreValidatesExistingReservationsWithoutReservingAgain() {
        FluxNetworkPointHandler handler = handler(100L);
        handler.requestWarmup(80L);
        handler.onCycleStart();
        handler.addToBuffer(80L);
        FluxNetworkInputCapability capability = capability(handler);
        assertThat(capability.planPrefetch(80L).orElseThrow().operation().commit().success()).isTrue();
        capability.restoreReservation(80L);
        assertThat(handler.reserved()).isEqualTo(80L);
        assertThat(handler.getBuffer()).isEqualTo(80L);
        assertThat(capability.consumeReservation(30L).success()).isTrue();
        capability.restoreReservation(50L);
        assertThat(handler.reserved()).isEqualTo(50L);
        assertThatThrownBy(() -> capability.restoreReservation(51L)).isInstanceOf(IllegalStateException.class);
        assertThat(handler.storage().amount()).isEqualTo(50L);
    }

    @Test
    void disconnectedFacetRemainsDiscoverableButDoesNotRequestNetworkEnergy() {
        AtomicBoolean active = new AtomicBoolean(true);
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(active::get, () -> 1L, () -> {});
        handler.setLimit(100L);
        handler.requestWarmup(50L);
        handler.onCycleStart();
        handler.addToBuffer(50L);
        FluxNetworkInputCapability capability = capability(handler);
        var plan = capability.planPrefetch(30L).orElseThrow();
        assertThat(plan.operation().commit().success()).isTrue();
        handler.requestWarmup(50L);
        handler.onCycleStart();
        active.set(false);
        assertThat(handler.getRequest()).isZero();
        handler.clearDemand();
        assertThat(capability.facet(RecipeEnergyPrefetchFacet.class)).isPresent();
        assertThat(capability.planPrefetch(50L)).isEmpty();
        handler.onCycleStart();
        assertThat(handler.getRequest()).isZero();
        assertThat(capability.consumeReservation(10L).success()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(40L);
        assertThat(handler.reserved()).isEqualTo(20L);
        active.set(true);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isZero();
    }

    @Test
    void staleDemandAndExplicitClearCancelOutstandingRequestsWithoutLosingInventory() {
        AtomicLong time = new AtomicLong(1L);
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, time::get, () -> {});
        handler.setLimit(100L);
        handler.requestWarmup(50L);
        handler.onCycleStart();
        handler.addToBuffer(20L);
        time.incrementAndGet();
        assertThat(handler.getRequest()).isZero();
        handler.requestWarmup(50L);
        // A hint in a new tick cannot revive the previous cycle's unfilled request.
        assertThat(handler.getRequest()).isZero();
        assertThatThrownBy(() -> handler.addToBuffer(1L)).isInstanceOf(IllegalArgumentException.class);
        handler.onCycleStart();
        assertThat(handler.getRequest()).isEqualTo(30L);
        handler.clearDemand();
        assertThat(handler.getRequest()).isZero();
        assertThat(handler.storage().amount()).isEqualTo(20L);
    }

    @Test
    void searchCandidateRollbackAndStandaloneScalarPlanUseOnlyLocalEnergy() {
        FluxNetworkPointHandler handler = handler(100L);
        FluxNetworkInputCapability capability = capability(handler);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(capability)));
        var recipe = RecipeTestSupport.create(MMCR.id("flux_point_prefetch"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2L)));
        assertThat(context.planStartResult(recipe, 1L).successful()).isFalse();
        handler.onCycleStart();
        handler.addToBuffer(handler.getRequest());
        assertThat(context.planStartResult(recipe, 1L).successful()).isTrue();
        assertThat(context.planStartResult(recipe, 1L).successful()).isTrue();
        assertThat(handler.availableForPrefetch()).isEqualTo(6L);
        assertThat(handler.reserved()).isZero();

        assertThat(capability.planPrefetch(6L).orElseThrow().operation().commit().success()).isTrue();
        var perTick = context.planRequirements(List.of(new EnergyRequirement(2L)), 1L, Map.of());
        assertThat(perTick.successful()).isFalse();
        assertThat(handler.getBuffer()).isEqualTo(6L);
        assertThat(handler.reserved()).isEqualTo(6L);
        assertThat(capability.consumeReservation(2L).success()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(4L);
        assertThat(handler.reserved()).isEqualTo(4L);
        assertThat(handler.getRequest()).isZero();
    }

    @Test
    void scalarOperationsRejectInsertionWrongDirectionAndOtherResourceTypes() {
        FluxNetworkPointHandler handler = handler(100L);
        handler.requestWarmup(20L);
        handler.onCycleStart();
        handler.addToBuffer(20L);
        FluxNetworkInputCapability capability = capability(handler);
        assertThat(capability.prepareScalar(new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                1L, 1L, true)).commit().success()).isFalse();
        assertThat(capability.prepareOperation(new CapabilityRequests.ValueRequest(capability.type(), IOType.OUTPUT,
                1L, 1L, false)).commit().success()).isFalse();
        assertThat(capability.prepareScalar(new CapabilityRequests.ValueRequest(new CapabilityType(MMCR.id("other")),
                IOType.INPUT, 1L, 1L, false)).commit().success()).isFalse();
        assertThat(capability.prepare(new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                1L, 21L, false)).commit().success()).isFalse();
        assertThat(handler.getBuffer()).isEqualTo(20L);
        assertThat(capability.prepare(new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                1L, 7L, false)).commit().success()).isTrue();
        assertThat(handler.storage().amount()).isEqualTo(13L);
    }

    @Test
    void projectionIsNotAnEnergySourceAndChangesNotifyOnlyAfterRealMutations() {
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, () -> 1L, changes::incrementAndGet);
        handler.setLimit(100L);
        handler.storage().insert(90L, false);
        assertThat(handler.reserveCandidate(1L)).isFalse();
        assertThat(handler.consumeLocal(1L)).isFalse();
        assertThat(handler.storage().amount()).isZero();
        assertThat(changes.get()).isZero();
        handler.requestWarmup(20L);
        handler.onCycleStart();
        handler.addToBuffer(20L);
        assertThat(changes.get()).isEqualTo(1);
        assertThat(handler.reserveCandidate(10L)).isTrue();
        assertThat(changes.get()).isEqualTo(1);
        handler.restoreCandidateOrSavedReservation(10L);
        assertThat(changes.get()).isEqualTo(1);
        assertThat(handler.reserveCandidate(10L)).isTrue();
        assertThat(handler.commitCandidate(10L)).isTrue();
        assertThat(handler.consumeLocal(4L)).isTrue();
        assertThat(handler.releaseReserved(6L)).isEqualTo(6L);
        assertThat(changes.get()).isEqualTo(4);
        assertThat(handler.storage().amount()).isEqualTo(handler.getBuffer());
    }

    @Test
    void reservationKeyThreadCheckPrecedesEveryReservationMutation() {
        FluxNetworkPointHandler handler = handler(100L);
        handler.requestWarmup(20L);
        handler.onCycleStart();
        handler.addToBuffer(20L);
        AtomicBoolean serverThread = new AtomicBoolean(true);
        FluxNetworkInputCapability capability = new FluxNetworkInputCapability(handler, () -> {
            if (!serverThread.get()) throw new IllegalStateException("Not on server thread");
            return "overworld:point:1";
        });
        var candidate = capability.planPrefetch(20L).orElseThrow();
        serverThread.set(false);
        assertThatThrownBy(() -> candidate.operation().commit()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> capability.restoreReservation(20L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> capability.releaseReservation(20L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> capability.consumeReservation(1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> capability.planPrefetch(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(handler.getBuffer()).isEqualTo(20L);
        assertThat(handler.reserved()).isZero();
        assertThat(handler.availableForPrefetch()).isZero();
        serverThread.set(true);
        assertThat(candidate.operation().commit().success()).isTrue();
        assertThat(handler.reserved()).isEqualTo(20L);
    }

    @Test
    void capabilityDeclaresOnlyInternalFacets() {
        FluxNetworkInputCapability capability = capability(handler(100L));
        assertThat(capability.view().facets()).containsExactlyInAnyOrder(ScalarFacet.class, ValueFacet.class,
                OperationFacet.class, PresentationFacet.class, RecipeEnergyPrefetchFacet.class);
        assertThat(capability.facet(RecipeEnergyPrefetchFacet.class)).isPresent();
        assertThat(capability.facet(TransferFacet.class)).isEmpty();
        assertThat(capability.facet(PersistenceFacet.class)).isEmpty();
        assertThat(capability.facet(SyncFacet.class)).isEmpty();
    }

    private static FluxNetworkPointHandler handler(long limit) {
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {});
        handler.setLimit(limit);
        return handler;
    }

    private static FluxNetworkInputCapability capability(FluxNetworkPointHandler handler) {
        return new FluxNetworkInputCapability(handler, () -> "overworld:point:1");
    }
}
