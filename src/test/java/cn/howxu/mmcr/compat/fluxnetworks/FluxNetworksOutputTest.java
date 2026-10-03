package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkOutputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPlugHandler;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sonar.fluxnetworks.common.device.FluxPlugHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Plug admission and conservation against real Flux handler behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluxNetworksOutputTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
    }

    @Test
    void valueOnlyRequirementSplitsFullOutputAcrossIndependentPlugs() {
        FluxNetworkPlugHandler first = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        FluxNetworkPlugHandler second = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        first.setLimit(20L);
        second.setLimit(20L);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new FluxNetworkOutputCapability(first), new FluxNetworkOutputCapability(second))));

        var result = context.planRequirements(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 25L)),
                1L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(25L);
            assertThat(simulation.accepted()).isEqualTo(25L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.FULL);
        });
        assertThat(first.getBuffer()).isZero();
        assertThat(second.getBuffer()).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(first.getBuffer()).isEqualTo(20L);
        assertThat(second.getBuffer()).isEqualTo(5L);
        assertThat(first.storage().amount() + second.storage().amount()).isEqualTo(25L);
    }

    @Test
    void valueOnlyRequirementBlocksInsufficientFullOutputButCommitsPartialOutput() {
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 100L, changes::incrementAndGet);
        handler.setLimit(20L);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new FluxNetworkOutputCapability(handler))));
        var requirement = new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 25L);

        var full = context.planRequirements(List.of(requirement), 1L, Map.of());
        assertThat(full.successful()).isFalse();
        assertThat(full.plan()).isNull();
        assertThat(full.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(full.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.accepted()).isEqualTo(20L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(handler.getBuffer()).isZero();
        assertThat(handler.storage().amount()).isZero();
        assertThat(changes.get()).isZero();

        var partial = context.planRequirements(List.of(requirement), 1L, Map.of(0, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isTrue();
        assertThat(partial.outputSimulations()).singleElement().satisfies(simulation -> {
            assertThat(simulation.requested()).isEqualTo(25L);
            assertThat(simulation.accepted()).isEqualTo(20L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(handler.getBuffer()).isZero();
        assertThat(changes.get()).isZero();
        assertThat(partial.plan().commit()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(20L);
        assertThat(handler.storage().amount()).isEqualTo(20L);
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void virtualBufferMatchesNativeAdmissionForSequentialOutputs() {
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 100L, changes::incrementAndGet);
        handler.setLimit(100L);
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        PlanningReservations reservations = new PlanningReservations();

        var first = capability.planOutput(40L, reservations, true);
        assertThat(first.accepted()).isEqualTo(40L);
        assertThat(handler.getBuffer()).isZero();
        assertThat(handler.storage().amount()).isZero();
        assertThat(changes.get()).isZero();
        assertThat(capability.outputCapacity(reservations)).isEqualTo(20L);
        assertThat(capability.planOutput(40L, reservations, true).accepted()).isZero();
        var second = capability.planOutput(20L, reservations, true);
        assertThat(second.accepted()).isEqualTo(20L);
        assertThat(first.operation().commit().success()).isTrue();
        assertThat(second.operation().commit().success()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(60L);
        assertThat(handler.storage().amount()).isEqualTo(60L);
        assertThat(handler.removeFromBuffer(60L)).isEqualTo(60L);
        assertThat(handler.removeFromBuffer(1L)).isZero();
        assertThat(handler.storage().amount()).isZero();
        assertThat(changes.get()).isEqualTo(3);
    }

    @Test
    void admissionMatchesNativeReceiveAtLimitAndLimiterBoundaries() {
        long[] bounds = {0L, 1L, 100L, (long) Integer.MAX_VALUE + 100L, Long.MAX_VALUE};
        for (long limit : bounds) {
            for (long limiter : bounds) {
                long[] buffers = {0L, Math.min(limit, limiter) / 2L, Math.min(limit, limiter)};
                for (long buffer : buffers) {
                    FluxPlugHandler nativeHandler = new FluxPlugHandler();
                    nativeHandler.setLimit(limit);
                    assertThat(nativeHandler.receive(buffer, Direction.DOWN, false, limiter)).isEqualTo(buffer);
                    FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> limiter, () -> {});
                    handler.setLimit(limit);

                    assertThat(handler.admissionAt(buffer))
                            .isEqualTo(nativeHandler.receive(Long.MAX_VALUE, Direction.DOWN, true, limiter));
                    assertThat(handler.getBuffer()).isZero();
                }
            }
        }
    }

    @Test
    void disconnectedCommitDoesNotInsertSimulatedEnergy() {
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(active::get, () -> 100L, changes::incrementAndGet);
        handler.setLimit(100L);
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        var plan = capability.planOutput(20L, new PlanningReservations(), true);
        active.set(false);

        var result = plan.operation().commit();
        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(handler.getBuffer()).isZero();
        assertThat(handler.storage().amount()).isZero();
        assertThat(changes.get()).isZero();
        assertThat(capability.outputCapacity(new PlanningReservations())).isZero();
        active.set(true);
        assertThat(capability.planOutput(20L, new PlanningReservations(), true).operation().commit().success()).isTrue();
    }

    @Test
    void changedLimiterOrLimitRejectsTheWholeCommit() {
        AtomicLong limiter = new AtomicLong(100L);
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, limiter::get, () -> {});
        handler.setLimit(100L);
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        assertThat(handler.acceptRecipeEnergy(10L)).isTrue();
        var limiterPlan = capability.planOutput(60L, new PlanningReservations(), true);
        limiter.set(30L);
        assertThat(limiterPlan.operation().commit().success()).isFalse();
        assertThat(handler.getBuffer()).isEqualTo(10L);
        limiter.set(100L);
        var limitPlan = capability.planOutput(60L, new PlanningReservations(), true);
        handler.setLimit(20L);
        assertThat(limitPlan.operation().commit().success()).isFalse();
        assertThat(handler.getBuffer()).isEqualTo(10L);
        assertThat(handler.storage().amount()).isEqualTo(10L);
    }

    @Test
    void discardedCandidateOnlyReservesVirtualBuffer() {
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        handler.setLimit(100L);
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        PlanningReservations selected = new PlanningReservations();
        PlanningReservations candidate = selected.copy();

        var discarded = capability.planOutput(40L, candidate, false);
        assertThat(discarded.accepted()).isEqualTo(40L);
        assertThat(discarded.operation()).isNull();
        assertThat(capability.outputCapacity(candidate)).isEqualTo(20L);
        assertThat(capability.outputCapacity(selected)).isEqualTo(100L);
        assertThat(handler.getBuffer()).isZero();
        assertThat(handler.storage().amount()).isZero();
        assertThat(capability.planOutput(100L, selected, true).operation().commit().success()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(100L);
    }

    @Test
    void reservationsAreSharedByCapabilitiesWrappingTheSameHandler() {
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        handler.setLimit(100L);
        FluxNetworkOutputCapability first = new FluxNetworkOutputCapability(handler);
        FluxNetworkOutputCapability second = new FluxNetworkOutputCapability(handler);
        PlanningReservations reservations = new PlanningReservations();
        assertThat(first.planOutput(40L, reservations, false).accepted()).isEqualTo(40L);
        assertThat(second.planOutput(40L, reservations, false).accepted()).isZero();
        assertThat(second.planOutput(20L, reservations, false).accepted()).isEqualTo(20L);
        assertThat(handler.getBuffer()).isZero();
    }

    @Test
    void committedBufferDrainsWhileInactiveWithinNativeCumulativeCycleLimit() {
        AtomicBoolean active = new AtomicBoolean(true);
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(active::get, () -> 200L, () -> {});
        handler.setLimit(100L);
        assertThat(handler.acceptRecipeEnergy(100L)).isTrue();
        active.set(false);
        handler.setLimit(60L);

        assertThat(handler.acceptRecipeEnergy(1L)).isFalse();
        assertThat(handler.removeFromBuffer(40L)).isEqualTo(40L);
        assertThat(handler.removeFromBuffer(40L)).isEqualTo(20L);
        assertThat(handler.removeFromBuffer(1L)).isZero();
        assertThat(handler.getBuffer()).isEqualTo(40L);
        assertThat(handler.storage().amount()).isEqualTo(40L);
        handler.onCycleEnd();
        assertThat(handler.getChange()).isEqualTo(100L);
        handler.onCycleStart();
        assertThat(handler.removeFromBuffer(Long.MAX_VALUE)).isEqualTo(40L);
        assertThat(handler.storage().amount()).isZero();
        assertThat(handler.removeFromBuffer(1L)).isZero();
    }

    @Test
    void zeroLimiterAndNonPositiveRequestsDoNotMutateInventory() {
        AtomicLong limiter = new AtomicLong(0L);
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, limiter::get, changes::incrementAndGet);
        handler.setLimit(100L);
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        assertThat(capability.planOutput(1L, new PlanningReservations(), true).accepted()).isZero();
        assertThat(handler.acceptRecipeEnergy(1L)).isFalse();
        limiter.set(100L);
        assertThat(handler.acceptRecipeEnergy(0L)).isFalse();
        assertThat(handler.acceptRecipeEnergy(-1L)).isFalse();
        assertThat(capability.planOutput(0L, new PlanningReservations(), true).operation()).isNull();
        assertThat(handler.removeFromBuffer(-1L)).isZero();
        assertThat(handler.getBuffer()).isZero();
        assertThat(changes.get()).isZero();
    }

    @Test
    void loweringLimitBelowAlreadyRemovedEnergyCannotIncreaseBuffer() {
        AtomicInteger changes = new AtomicInteger();
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 200L, changes::incrementAndGet);
        handler.setLimit(100L);
        assertThat(handler.acceptRecipeEnergy(100L)).isTrue();
        assertThat(handler.removeFromBuffer(60L)).isEqualTo(60L);
        handler.setLimit(20L);

        assertThat(handler.removeFromBuffer(10L)).isZero();
        assertThat(handler.getBuffer()).isEqualTo(40L);
        assertThat(handler.storage().amount()).isEqualTo(40L);
        assertThat(changes.get()).isEqualTo(2);
        handler.setLimit(70L);
        assertThat(handler.removeFromBuffer(40L)).isEqualTo(10L);
        assertThat(handler.removeFromBuffer(1L)).isZero();
        handler.onCycleEnd();
        assertThat(handler.removeFromBuffer(30L)).isEqualTo(30L);
        assertThat(handler.storage().amount()).isZero();
    }

    @Test
    void disabledLimitAllowsLongSizedOutputAndConservesBufferAtLongBoundary() {
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> Long.MAX_VALUE, () -> {});
        handler.setLimit(1L);
        handler.setDisableLimit(true);
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        PlanningReservations reservations = new PlanningReservations();
        long firstAmount = Long.MAX_VALUE / 2L;
        var first = capability.planOutput(firstAmount, reservations, true);
        var second = capability.planOutput(1L, reservations, true);
        assertThat(first.accepted()).isEqualTo(firstAmount);
        assertThat(second.accepted()).isEqualTo(1L);
        assertThat(capability.planOutput(1L, reservations, true).accepted()).isZero();
        assertThat(first.operation().commit().success()).isTrue();
        assertThat(second.operation().commit().success()).isTrue();
        assertThat(handler.getBuffer()).isEqualTo(firstAmount + 1L);
        assertThat(handler.storage().amount()).isEqualTo(firstAmount + 1L);
        assertThat(handler.removeFromBuffer(Long.MAX_VALUE)).isEqualTo(firstAmount + 1L);
        assertThat(handler.getBuffer()).isZero();
        handler.onCycleEnd();
        assertThat(capability.planOutput(Long.MAX_VALUE, new PlanningReservations(), true).operation().commit().success()).isTrue();
        assertThat(handler.removeFromBuffer(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
        assertThat(handler.storage().amount()).isZero();
    }

    @Test
    void scalarOutputCommitsNativelyAndRejectsInputOrExtraction() {
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        handler.setLimit(100L);
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        assertThat(capability.prepare(new CapabilityRequests.ValueRequest(capability.type(), IOType.OUTPUT,
                1L, 20L, true)).commit().success()).isTrue();
        assertThat(capability.prepareScalar(new CapabilityRequests.ValueRequest(capability.type(), IOType.OUTPUT,
                1L, 10L, true)).commit().success()).isTrue();
        var input = capability.prepareOperation(new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                1L, 1L, true)).commit();
        var extract = capability.prepareOperation(new CapabilityRequests.ValueRequest(capability.type(), IOType.OUTPUT,
                1L, 1L, false)).commit();
        assertThat(input.status().reason()).isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertThat(extract.status().reason()).isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertThat(handler.getBuffer()).isEqualTo(30L);
        assertThat(handler.storage().amount()).isEqualTo(30L);
        assertThat(capability.displays(capability.view()).getFirst().value()).isEqualTo("30");
    }

    @Test
    void outputDeclaresAdmissionFacetsWithoutExternalEnergyExposure() {
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        FluxNetworkOutputCapability capability = new FluxNetworkOutputCapability(handler);
        assertThat(capability.view().facets()).containsExactlyInAnyOrder(ScalarFacet.class, ValueFacet.class,
                OperationFacet.class, PresentationFacet.class, EnergyOutputAdmissionFacet.class);
        assertThat(capability.facet(EnergyOutputAdmissionFacet.class)).isPresent();
        assertThat(capability.facet(TransferFacet.class)).isEmpty();
        assertThat(capability.facet(EnergyStorageFacet.class)).isEmpty();
        assertThat(capability.storage()).isSameAs(handler.storage());
    }
}
