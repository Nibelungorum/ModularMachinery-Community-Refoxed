package cn.howxu.mmcr.api.capability.async;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies immutable values used to plan capability work asynchronously.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncPlanningFacetTest {
    @Test
    void resource_operation_is_a_value_object_without_a_live_storage_reference() {
        AsyncResourceValue resource = new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}");
        AsyncCapabilityOperation.Resource operation = new AsyncCapabilityOperation.Resource(
                MMCR.id("item"), 2, resource, 4L, false);

        assertThat(operation.capabilityId()).isEqualTo(MMCR.id("item"));
        assertThat(operation.slot()).isEqualTo(2);
        assertThat(operation.resource()).isEqualTo(resource);
        assertThat(operation.amount()).isEqualTo(4L);
        assertThat(operation.insert()).isFalse();
        assertThat(AsyncCapabilityOperation.Resource.class.getRecordComponents()[2].getType())
                .isEqualTo(AsyncResourceValue.class);
    }

    @Test
    void snapshot_slots_are_worker_values_after_capture() {
        var slots = new ArrayList<>(List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.empty(), 0L, 64L)));
        AsyncCapabilitySnapshot.Resource snapshot = new AsyncCapabilitySnapshot.Resource(
                MMCR.id("item"), slots);
        slots.clear();

        assertThat(snapshot.slots()).hasSize(1);
        assertThat(snapshot.slots().getFirst().resource()).isEmpty();
        assertThat(AsyncCapabilitySnapshot.ResourceSlot.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(Optional.class, long.class, long.class);
    }

    @Test
    void scalar_energy_values_do_not_use_a_resource_identifier() {
        var snapshot = new AsyncCapabilitySnapshot.Scalar(MMCR.id("energy"), 120L, 1_000L, 20L);
        var request = new AsyncCapabilityRequest.Scalar(MMCR.id("energy"), 1L, 32L, false);
        var operation = new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 32L, false);

        assertThat(snapshot.amount()).isEqualTo(120L);
        assertThat(snapshot.capacity()).isEqualTo(1_000L);
        assertThat(snapshot.transferLimit()).isEqualTo(20L);
        assertThat(request.amount()).isEqualTo(32L);
        assertThat(operation.amount()).isEqualTo(32L);
        assertThat(AsyncCapabilityOperation.Scalar.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(ResourceLocation.class, long.class, boolean.class);
    }

    @Test
    void worker_planner_accepts_and_returns_only_async_value_objects() throws Exception {
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Scalar(MMCR.id("energy"));

        var snapshot = new AsyncCapabilitySnapshot.Scalar(MMCR.id("energy"), 120L, 1_000L, 1_000L);
        var request = new AsyncCapabilityRequest.Scalar(MMCR.id("energy"), 1L, 32L, false);
        Optional<AsyncCapabilityOperation> planned = CompletableFuture.supplyAsync(() -> planner.plan(snapshot, request)).get();

        assertThat(planned).contains(new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 32L, false));
    }

    @Test
    void scalar_planner_splits_the_batch_limited_amount_into_committable_operations() {
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Scalar(MMCR.id("energy"));
        var snapshot = new AsyncCapabilitySnapshot.Scalar(MMCR.id("energy"), 100L, 1_000L, 20L);
        var request = new AsyncCapabilityRequest.Scalar(MMCR.id("energy"), 3L, 100L, false);

        assertThat(planner.plan(snapshot, request)).hasValueSatisfying(operation ->
                assertThat(groupOperations(operation)).containsExactly(
                        new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 20L, false),
                        new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 20L, false),
                        new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 20L, false)));
    }

    @Test
    void worker_planner_is_a_sealed_value_descriptor() {
        assertThat(AsyncCapabilityPlanner.class.isSealed()).isTrue();
        assertThat(Arrays.stream(AsyncCapabilityPlanner.class.getPermittedSubclasses()))
                .containsExactlyInAnyOrder(AsyncCapabilityPlanner.Resource.class, AsyncCapabilityPlanner.Scalar.class,
                        AsyncCapabilityPlanner.Heat.class);
        assertThat(AsyncCapabilityPlanner.Resource.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(ResourceLocation.class);
        assertThat(AsyncCapabilityPlanner.Scalar.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(ResourceLocation.class);
        assertThat(AsyncCapabilityPlanner.Heat.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(ResourceLocation.class);
    }

    @Test
    void resource_planner_returns_a_complete_ordered_group_for_cross_slot_actions() {
        AsyncResourceValue iron = new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}");
        AsyncResourceValue gold = new AsyncResourceValue(MMCR.id("gold_ingot"), "components={}");
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Resource(MMCR.id("item"));
        var snapshot = new AsyncCapabilitySnapshot.Resource(MMCR.id("item"), List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.empty(), 0L, 64L),
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.empty(), 0L, 64L)));
        var request = new AsyncCapabilityRequest.Resource(MMCR.id("item"), 1L, List.of(
                new AsyncResourceAction(iron, 3L, true),
                new AsyncResourceAction(gold, 2L, true)));

        Optional<AsyncCapabilityOperation> planned = planner.plan(snapshot, request);

        assertThat(planned).hasValueSatisfying(operation -> {
            assertThat(operation.getClass().getSimpleName()).isEqualTo("Group");
            assertThat(groupOperations(operation)).containsExactly(
                    new AsyncCapabilityOperation.Resource(MMCR.id("item"), 0, iron, 3L, true),
                    new AsyncCapabilityOperation.Resource(MMCR.id("item"), 1, gold, 2L, true));
        });
    }

    @Test
    void resource_planner_combines_one_action_across_matching_slots_in_order() {
        AsyncResourceValue iron = new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}");
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Resource(MMCR.id("item"));
        var snapshot = new AsyncCapabilitySnapshot.Resource(MMCR.id("item"), List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(iron), 2L, 64L),
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(iron), 3L, 64L)));
        var request = new AsyncCapabilityRequest.Resource(MMCR.id("item"), 1L, List.of(
                new AsyncResourceAction(iron, 5L, false)));

        assertThat(planner.plan(snapshot, request)).hasValueSatisfying(operation ->
                assertThat(groupOperations(operation)).containsExactly(
                        new AsyncCapabilityOperation.Resource(MMCR.id("item"), 0, iron, 2L, false),
                        new AsyncCapabilityOperation.Resource(MMCR.id("item"), 1, iron, 3L, false)));
    }

    @Test
    void resource_planner_distributes_one_insert_across_compatible_slots_in_order() {
        AsyncResourceValue iron = new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}");
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Resource(MMCR.id("item"));
        var snapshot = new AsyncCapabilitySnapshot.Resource(MMCR.id("item"), List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(iron), 62L, 64L),
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.empty(), 0L, 64L)));
        var request = new AsyncCapabilityRequest.Resource(MMCR.id("item"), 1L, List.of(
                new AsyncResourceAction(iron, 5L, true)));

        assertThat(planner.plan(snapshot, request)).hasValueSatisfying(operation ->
                assertThat(groupOperations(operation)).containsExactly(
                        new AsyncCapabilityOperation.Resource(MMCR.id("item"), 0, iron, 2L, true),
                        new AsyncCapabilityOperation.Resource(MMCR.id("item"), 1, iron, 3L, true)));
    }

    @Test
    void resource_planner_rejects_an_action_that_cannot_be_fully_combined() {
        AsyncResourceValue iron = new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}");
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Resource(MMCR.id("item"));
        var snapshot = new AsyncCapabilitySnapshot.Resource(MMCR.id("item"), List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(iron), 2L, 64L),
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(iron), 2L, 64L)));
        var request = new AsyncCapabilityRequest.Resource(MMCR.id("item"), 1L, List.of(
                new AsyncResourceAction(iron, 5L, false)));

        assertThat(planner.plan(snapshot, request)).isEmpty();
    }

    @Test
    void resource_planner_simulates_actions_in_request_order() {
        AsyncResourceValue iron = new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}");
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Resource(MMCR.id("item"));
        var snapshot = new AsyncCapabilitySnapshot.Resource(MMCR.id("item"), List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.empty(), 0L, 64L)));
        var request = new AsyncCapabilityRequest.Resource(MMCR.id("item"), 1L, List.of(
                new AsyncResourceAction(iron, 3L, true),
                new AsyncResourceAction(iron, 2L, false)));

        Optional<AsyncCapabilityOperation> planned = planner.plan(snapshot, request);

        assertThat(planned).hasValueSatisfying(operation -> assertThat(groupOperations(operation)).containsExactly(
                new AsyncCapabilityOperation.Resource(MMCR.id("item"), 0, iron, 3L, true),
                new AsyncCapabilityOperation.Resource(MMCR.id("item"), 0, iron, 2L, false)));
    }

    @Test
    void resource_planner_returns_no_prefix_when_a_later_action_cannot_be_planned() {
        AsyncResourceValue iron = new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}");
        AsyncResourceValue gold = new AsyncResourceValue(MMCR.id("gold_ingot"), "components={}");
        AsyncCapabilityPlanner planner = new AsyncCapabilityPlanner.Resource(MMCR.id("item"));
        var snapshot = new AsyncCapabilitySnapshot.Resource(MMCR.id("item"), List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.empty(), 0L, 64L)));
        var request = new AsyncCapabilityRequest.Resource(MMCR.id("item"), 1L, List.of(
                new AsyncResourceAction(iron, 3L, true),
                new AsyncResourceAction(gold, 2L, false)));

        assertThat(planner.plan(snapshot, request)).isEmpty();
    }

    @Test
    void operation_group_is_immutable_and_rejects_empty_or_nested_groups() throws Exception {
        Class<?> groupType = Arrays.stream(AsyncCapabilityOperation.class.getPermittedSubclasses())
                .filter(type -> type.getSimpleName().equals("Group"))
                .findFirst()
                .orElse(null);

        assertThat(groupType).isNotNull();
        assertThat(groupType.getRecordComponents()).extracting(RecordComponent::getType).containsExactly(List.class);
        assertThatThrownBy(() -> groupType.getConstructor(List.class).newInstance(List.of()))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        AsyncCapabilityOperation.Resource operation = new AsyncCapabilityOperation.Resource(
                MMCR.id("item"), 0, new AsyncResourceValue(MMCR.id("iron_ingot"), "components={}"), 1L, true);
        Object group = groupType.getConstructor(List.class).newInstance(List.of(operation));
        assertThatThrownBy(() -> groupType.getConstructor(List.class).newInstance(List.of(group)))
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> groupType.getConstructor(List.class).newInstance(List.of(operation,
                new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 1L, true))))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void public_facet_entries_are_final_and_live_hooks_cannot_be_called_directly() throws Exception {
        assertThat(AsyncPlanningFacet.class.isInterface()).isFalse();
        assertThat(Modifier.isFinal(AsyncPlanningFacet.class.getMethod("captureSnapshot").getModifiers())).isTrue();
        assertThat(Modifier.isFinal(AsyncPlanningFacet.class.getMethod("workerPlanner").getModifiers())).isTrue();
        assertThat(Modifier.isFinal(AsyncPlanningFacet.class.getMethod("commit",
                AsyncCapabilityOperation.class).getModifiers())).isTrue();

        assertThatThrownBy(() -> AsyncPlanningFacet.class.getMethod("captureSnapshotOnServerThread"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> AsyncPlanningFacet.class.getMethod("commitNativeOnServerThread",
                AsyncCapabilityOperation.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void public_entries_reject_worker_thread_access_before_live_hooks_run() {
        TestFacet facet = new TestFacet();

        assertThatThrownBy(() -> facet.captureSnapshot())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("captureSnapshot requires the server thread");
        assertThatThrownBy(() -> facet.workerPlanner())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("workerPlanner requires the server thread");
        assertThatThrownBy(() -> facet.commit(
                new AsyncCapabilityOperation.Scalar(MMCR.id("energy"), 32L, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("commit requires the server thread");
        assertThat(facet.captured).isFalse();
        assertThat(facet.exported).isFalse();
        assertThat(facet.committed).isFalse();
    }

    private static final class TestFacet extends AsyncPlanningFacet {
        private final AtomicBoolean captured = new AtomicBoolean();
        private final AtomicBoolean exported = new AtomicBoolean();
        private final AtomicBoolean committed = new AtomicBoolean();

        @Override
        protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
            captured.set(true);
            return new AsyncCapabilitySnapshot.Scalar(MMCR.id("energy"), 0L, 1_000L, 1_000L);
        }

        @Override
        protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
            exported.set(true);
            return new AsyncCapabilityPlanner.Scalar(MMCR.id("energy"));
        }

        @Override
        protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
            committed.set(true);
            throw new AssertionError("commit should not run");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<AsyncCapabilityOperation> groupOperations(AsyncCapabilityOperation operation) {
        try {
            return (List<AsyncCapabilityOperation>) operation.getClass().getMethod("operations").invoke(operation);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
