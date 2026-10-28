package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.compat.ae2lt.LightningViewFacet;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;
import static org.assertj.core.api.Assertions.assertThat;

/** Native-independent planner tests; actual AE keys/inventories are exercised by GameTest.
 * @author howxu <dev@howxu.cn> */
class LightningPlanningTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }
    @BeforeEach void present() { LightningBridgeBootstrap.installForTesting(() -> true); }
    @AfterEach void reset() { LightningBridgeBootstrap.resetForTesting(); }

    @Test void tierSpecificInputPlanConsumesOnlyAfterCommit() {
        State state = new State();
        state.amounts[0] = 20L;
        state.amounts[1] = 30L;
        var storage = new View(state, new int[]{0, 1});
        var capability = new LightningCapability(storage, IOType.INPUT);
        var requirement = LightningRequirement.input(HIGH_VOLTAGE, 4L);
        var plan = new LightningRequirementHandler().plan(requirement, List.of(capability), new PlanningContext(3L, 0));
        var prepared = plan.materialize(3L, new PlanningReservations(), null);
        assertThat(prepared.successful()).isTrue();
        assertThat(state.amounts).containsExactly(20L, 30L);
        assertThat(prepared.operations()).hasSize(1);
        assertThat(prepared.operations().getFirst().commit().success()).isTrue();
        assertThat(state.amounts).containsExactly(8L, 30L);
        LightningViewFacet query = capability.facet(LightningViewFacet.class).orElseThrow();
        assertThat(query.storage().amount(0)).isEqualTo(8L);
        assertThat(query.storage().identity()).isSameAs(state);
        assertThat(capability.facet(TransferFacet.class)).isEmpty();
    }

    @Test void reorderedViewsOfSameNetworkCannotOverbookTheSameTier() {
        State state = new State();
        state.amounts[0] = 5L;
        state.amounts[1] = 9L;
        var first = new LightningCapability(new View(state, new int[]{0, 1}), IOType.INPUT);
        var reversed = new LightningCapability(new View(state, new int[]{1, 0}), IOType.INPUT);
        var handler = new LightningRequirementHandler();
        var reservations = new PlanningReservations();
        var requirement = LightningRequirement.input(HIGH_VOLTAGE, 4L);
        var firstPlan = handler.plan(requirement, List.of(first), new PlanningContext(1L, 0));
        var secondPlan = handler.plan(requirement, List.of(reversed), new PlanningContext(1L, 1));
        assertThat(firstPlan.reserve(1L, reservations)).isNull();
        assertThat(secondPlan.reserve(1L, reservations)).isNotNull();
        var extreme = handler.plan(LightningRequirement.input(EXTREME_HIGH_VOLTAGE, 7L), List.of(reversed), new PlanningContext(1L, 2));
        assertThat(extreme.reserve(1L, reservations)).isNull();
        assertThat(state.amounts).containsExactly(5L, 9L);
    }

    @Test void duplicatePortsCannotDuplicateSharedOutputCapacity() {
        State state = new State();
        state.amounts[0] = 7L;
        var first = new LightningCapability(new View(state, new int[]{0}), IOType.OUTPUT);
        var second = new LightningCapability(new View(state, new int[]{0}), IOType.OUTPUT);
        var handler = new LightningRequirementHandler();
        var requirement = LightningRequirement.output(HIGH_VOLTAGE, 4L, 1F);
        var plan = handler.plan(requirement, List.of(first, second), new PlanningContext(1L, 0));
        assertThat(plan.materialize(1L, new PlanningReservations(), null).successful()).isFalse();
        var partial = handler.plan(requirement, List.of(first, second), new PlanningContext(1L, 0, true))
                .materialize(1L, new PlanningReservations(), null);
        assertThat(partial.successful()).isTrue();
        assertThat(partial.outputSimulation().accepted()).isEqualTo(3L);
        assertThat(partial.operations().getFirst().commit().success()).isTrue();
        assertThat(state.amounts[0]).isEqualTo(10L);
    }

    @Test void unavailableAndChanceZeroNeverPrepareNativeOperations() {
        State state = new State();
        var capability = new LightningCapability(new View(state, new int[]{0}), IOType.OUTPUT);
        var handler = new LightningRequirementHandler();
        var noOutput = handler.plan(LightningRequirement.output(HIGH_VOLTAGE, 4L, 0F), List.of(capability), new PlanningContext(1L, 0));
        assertThat(noOutput.successful()).isTrue();
        assertThat(noOutput.operations()).isEmpty();
        LightningBridgeBootstrap.installForTesting(() -> false);
        assertThat(handler.plan(LightningRequirement.input(HIGH_VOLTAGE, 1L), List.of(), new PlanningContext(1L, 0)).successful()).isFalse();
    }

    @Test
    void asyncSnapshotsPlanOnWorkersAndCommitAtomicallyAgainstChangedStorage() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            State state = new State();
            state.amounts[0] = 4L;
            state.amounts[1] = 4L;
            var facet = new LightningCapability(new AsyncView(state), IOType.INPUT)
                    .facet(AsyncPlanningFacet.class).orElseThrow();
            var snapshot = facet.captureSnapshot();
            var planner = facet.workerPlanner();
            var request = new AsyncCapabilityRequest.Resource(LightningRecipeTypes.LIGHTNING, 1L,
                    List.of(new AsyncResourceAction(NativeAsyncResourceValues.lightning(HIGH_VOLTAGE), 6L, false)));
            var operation = CompletableFuture.supplyAsync(() -> planner.plan(snapshot, request).orElseThrow()).join();
            assertThat(state.amounts).containsExactly(4L, 4L);

            state.amounts[1] = 1L;
            assertThat(facet.commit(operation).success()).isFalse();
            assertThat(state.amounts).containsExactly(4L, 1L);

            state.amounts[1] = 4L;
            assertThat(facet.commit(operation).success()).isTrue();
            assertThat(state.amounts).containsExactly(0L, 2L);
            assertThat(facet.commit(operation).success()).isFalse();
            assertThat(state.amounts).containsExactly(0L, 2L);
        }
    }

    @Test
    void asyncCommitsRejectWrongDirectionTierAndSlotWithoutMutation() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            State state = new State();
            state.amounts[0] = 5L;
            var facet = new LightningCapability(new AsyncView(state), IOType.INPUT)
                    .facet(AsyncPlanningFacet.class).orElseThrow();
            var value = NativeAsyncResourceValues.lightning(HIGH_VOLTAGE);
            assertThat(value.resourceId()).isEqualTo(HIGH_VOLTAGE.requirementId());
            assertThat(value.data()).isEmpty();
            List<AsyncCapabilityOperation> invalid = List.of(
                    new AsyncCapabilityOperation.Resource(LightningRecipeTypes.LIGHTNING, 0, value, 1L, true),
                    new AsyncCapabilityOperation.Resource(LightningRecipeTypes.LIGHTNING, 2, value, 1L, false),
                    new AsyncCapabilityOperation.Resource(LightningRecipeTypes.LIGHTNING, 0,
                            NativeAsyncResourceValues.lightning(EXTREME_HIGH_VOLTAGE), 1L, false),
                    new AsyncCapabilityOperation.Resource(LightningRecipeTypes.LIGHTNING, 0,
                            new AsyncResourceValue(HIGH_VOLTAGE.requirementId(), "unexpected data"), 1L, false));
            for (var operation : invalid) assertThat(facet.commit(operation).success()).isFalse();
            assertThat(state.amounts).containsExactly(5L, 0L);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class State { private final long[] amounts = new long[2]; }
    /** Two physical slots can contain the same lightning tier.
     * @author howxu <dev@howxu.cn> */
    private static final class AsyncView implements LightningStorage {
        private final State state;
        private AsyncView(State state) { this.state = state; }
        @Override public int size() { return state.amounts.length; }
        @Override public Object identity() { return state; }
        @Override public Object address(int slot) { return slot; }
        @Override public Object key(LightningTier tier) { return tier; }
        @Override public Object storedKey(int slot) { return HIGH_VOLTAGE; }
        @Override public long amount(int slot) { return state.amounts[slot]; }
        @Override public long capacity(int slot, LightningTier tier) { return 10L; }
        @Override public long extract(int slot, LightningTier tier, long amount, boolean simulate) {
            if (storedKey(slot) != tier) return 0L;
            long moved = Math.min(amount, amount(slot));
            if (!simulate) state.amounts[slot] -= moved;
            return moved;
        }
        @Override public long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations) {
            return 0L;
        }
        @Override public long insert(LightningTier tier, long amount, boolean simulate) {
            long remaining = amount;
            for (int slot = 0; slot < size() && remaining > 0L; slot++) {
                if (storedKey(slot) != tier) continue;
                long moved = Math.min(remaining, Math.max(0L, capacity(slot, tier) - state.amounts[slot]));
                if (!simulate) state.amounts[slot] += moved;
                remaining -= moved;
            }
            return amount - remaining;
        }
    }

    /** Two differently ordered projections can share one physical network identity.
     * @author howxu <dev@howxu.cn> */
    private static final class View implements LightningStorage {
        private final State state;
        private final int[] slots;
        private View(State state, int[] slots) { this.state = state; this.slots = slots; }
        @Override public int size() { return slots.length; }
        @Override public Object identity() { return state; }
        @Override public Object address(int slot) { return LightningTier.values()[slots[slot]]; }
        @Override public Object key(LightningTier tier) { return tier; }
        @Override public Object storedKey(int slot) { return address(slot); }
        @Override public long amount(int slot) { return state.amounts[slots[slot]]; }
        @Override public long capacity(int slot, LightningTier tier) { return 10L; }
        @Override public long extract(int slot, LightningTier tier, long amount, boolean simulate) {
            if (storedKey(slot) != tier) return 0L;
            long moved = Math.min(amount, amount(slot));
            if (!simulate) state.amounts[slots[slot]] -= moved;
            return moved;
        }
        private long outputCapacity(LightningTier tier, PlanningReservations reservations) {
            return reservations.outputAvailable(state, tier, Math.max(0L, 10L - state.amounts[tier.ordinal()]));
        }
        @Override public long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations) {
            long moved = Math.min(amount, outputCapacity(tier, reservations));
            return moved > 0L && reservations.reserveOutput(state, tier, moved) ? moved : 0L;
        }
        @Override public long insert(LightningTier tier, long amount, boolean simulate) {
            long moved = Math.min(amount, Math.max(0L, 10L - state.amounts[tier.ordinal()]));
            if (!simulate) state.amounts[tier.ordinal()] += moved;
            return moved;
        }
    }
}
