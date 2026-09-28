package cn.howxu.mmcr.api.capability.tick;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.MMCR;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for immutable capability tick plans.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityTickContractTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void result_copies_operations_and_retains_the_requested_phase_order() {
        List<CapabilityOperation> operations = new ArrayList<>();
        operations.add(transaction -> CapabilityResult.successful());
        CapabilityTickResult result = new CapabilityTickResult(operations, null, false);
        operations.clear();

        assertThat(result.operations()).hasSize(1);
        assertThat(CapabilityTickPhase.values()).containsExactly(CapabilityTickPhase.BEFORE_RECIPE,
                CapabilityTickPhase.AFTER_INPUTS, CapabilityTickPhase.AFTER_RECIPE, CapabilityTickPhase.IDLE);
    }

    @Test
    void failure_result_has_no_implicit_operations_or_state_change() {
        Identifier source = Identifier.fromNamespaceAndPath("mmcr_test", "facet");
        ExecutionStatus failure = ExecutionStatus.blocked(Identifier.fromNamespaceAndPath("mmcr_test", "blocked"), source,
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of()));
        CapabilityTickResult result = new CapabilityTickResult(List.of(), failure, false);

        assertThat(result.operations()).isEmpty();
        assertThat(result.failure()).isSameAs(failure);
        assertThat(result.stateChanged()).isFalse();
    }

    @Test
    void rejected_operation_rolls_back_earlier_operations_in_the_same_phase() {
        LongValueStorage storage = new LongValueStorage(10L, 10L, null);
        Identifier source = Identifier.fromNamespaceAndPath("mmcr_test", "facet");
        ExecutionStatus blocked = ExecutionStatus.blocked(Identifier.fromNamespaceAndPath("mmcr_test", "blocked"), source,
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of()));
        TickCapability capability = new TickCapability(context -> new CapabilityTickResult(List.of(
                transaction -> {
                    storage.insert(1L, transaction);
                    return CapabilityResult.successful();
                }, transaction -> CapabilityResult.failure(blocked)), null, false));
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CapabilityTickContext context = new CapabilityTickContext(0L, CapabilityTickPhase.BEFORE_RECIPE, null, 1L,
                new CapabilitySnapshot(List.of(capability)), controller.behaviorContext());

        CapabilityTickResult result = new ComponentRuntime().executeTickPhase(context);

        assertThat(result.failure()).isSameAs(blocked);
        assertThat(storage.amount()).isZero();
    }

    @Test
    void operation_without_status_uses_the_typed_failure_reason() {
        TickCapability capability = new TickCapability(context -> new CapabilityTickResult(
                List.of(transaction -> null), null, false));
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CapabilityTickContext context = new CapabilityTickContext(0L, CapabilityTickPhase.BEFORE_RECIPE, null, 1L,
                new CapabilitySnapshot(List.of(capability)), controller.behaviorContext());

        CapabilityTickResult result = new ComponentRuntime().executeTickPhase(context);

        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.OPERATION_FAILED_WITHOUT_STATUS);
    }

    @Test
    void tick_facets_plan_in_capability_snapshot_order_and_idle_has_no_operations() {
        List<CapabilityTickPhase> phases = new ArrayList<>();
        TickCapability first = new TickCapability(context -> {
            phases.add(context.phase());
            return context.phase() == CapabilityTickPhase.IDLE ? CapabilityTickResult.empty()
                    : new CapabilityTickResult(List.of(transaction -> CapabilityResult.successful()), null, false);
        });
        TickCapability second = new TickCapability(context -> {
            phases.add(context.phase());
            return CapabilityTickResult.empty();
        });
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        ComponentRuntime runtime = new ComponentRuntime();

        CapabilityTickResult before = runtime.executeTickPhase(new CapabilityTickContext(0L,
                CapabilityTickPhase.BEFORE_RECIPE, null, 1L, new CapabilitySnapshot(List.of(first, second)),
                controller.behaviorContext()));
        CapabilityTickResult idle = runtime.executeTickPhase(new CapabilityTickContext(0L, CapabilityTickPhase.IDLE,
                null, 1L, new CapabilitySnapshot(List.of(first, second)), controller.behaviorContext()));

        assertThat(phases).containsExactly(CapabilityTickPhase.BEFORE_RECIPE, CapabilityTickPhase.BEFORE_RECIPE,
                CapabilityTickPhase.IDLE, CapabilityTickPhase.IDLE);
        assertThat(before.operations()).hasSize(1);
        assertThat(idle.operations()).isEmpty();
    }

    @Test
    void empty_tick_operations_preserve_a_reported_state_change() {
        TickCapability capability = new TickCapability(context -> new CapabilityTickResult(List.of(), null, true));
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));

        CapabilityTickResult result = new ComponentRuntime().executeTickPhase(new CapabilityTickContext(0L,
                CapabilityTickPhase.IDLE, null, 1L, new CapabilitySnapshot(List.of(capability)),
                controller.behaviorContext()));

        assertThat(result.operations()).isEmpty();
        assertThat(result.failure()).isNull();
        assertThat(result.stateChanged()).isTrue();
    }

    @Test
    void recipe_search_does_not_invoke_tick_facets() {
        AtomicInteger calls = new AtomicInteger();
        TickCapability capability = new TickCapability(context -> {
            calls.incrementAndGet();
            return CapabilityTickResult.empty();
        });
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        new RecipeSearchTask(controller.currentRuntimeSnapshot(), controller.machineId(), 0L, 1L,
                List.of(), null, List.of(capability)).compute();

        assertThat(calls).hasValue(0);
    }

    private record TickCapability(TickFacet tickFacet) implements MachineCapability, TickFacet {
        @Override
        public CapabilityType type() {
            return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "tick"));
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.input();
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TickCapability.this.type();
                }

                @Override
                public CapabilityDirections directions() {
                    return TickCapability.this.directions();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(TickFacet.class);
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CapabilityTickResult plan(CapabilityTickContext context) {
            return tickFacet.plan(context);
        }
    }
}
