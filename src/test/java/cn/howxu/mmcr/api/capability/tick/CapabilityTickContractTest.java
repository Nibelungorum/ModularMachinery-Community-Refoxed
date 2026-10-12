package cn.howxu.mmcr.api.capability.tick;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.MMCR;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        operations.add(CapabilityResult::successful);
        CapabilityTickResult result = new CapabilityTickResult(operations, null, false);
        operations.clear();

        assertThat(result.operations()).hasSize(1);
        assertThat(CapabilityTickPhase.values()).containsExactly(CapabilityTickPhase.BEFORE_RECIPE,
                CapabilityTickPhase.AFTER_INPUTS, CapabilityTickPhase.AFTER_RECIPE, CapabilityTickPhase.IDLE);
    }

    @Test
    void failure_result_has_no_implicit_operations_or_state_change() {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("mmcr_test", "facet");
        ExecutionStatus failure = ExecutionStatus.blocked(ResourceLocation.fromNamespaceAndPath("mmcr_test", "blocked"), source,
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of()));
        CapabilityTickResult result = new CapabilityTickResult(List.of(), failure, false);

        assertThat(result.operations()).isEmpty();
        assertThat(result.failure()).isSameAs(failure);
        assertThat(result.stateChanged()).isFalse();
    }

    @Test
    void operation_without_status_uses_the_typed_failure_reason() {
        TickCapability capability = new TickCapability(context -> new CapabilityTickResult(
                List.of(() -> null), null, false));
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
                    : new CapabilityTickResult(List.of(CapabilityResult::successful), null, false);
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
                List.of(), List.of(capability)).compute();

        assertThat(calls).hasValue(0);
    }

    @Test
    void custom_tick_facet_is_discovered_on_the_second_live_query_and_executes_its_phase() {
        AtomicInteger queries = new AtomicInteger();
        List<CapabilityTickPhase> phases = new ArrayList<>();
        TickCapability delegate = new TickCapability(context -> {
            phases.add(context.phase());
            return CapabilityTickResult.empty();
        });
        MachineCapability capability = new MachineCapability() {
            @Override
            public CapabilityType type() {
                return delegate.type();
            }

            @Override
            public CapabilityDirections directions() {
                return delegate.directions();
            }

            @Override
            public CapabilityView view() {
                return delegate.view();
            }

            @Override
            public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
                return facetType == TickFacet.class && queries.incrementAndGet() > 1
                        ? Optional.of(facetType.cast(delegate)) : Optional.empty();
            }

            @Override
            public CapabilityOperation prepare(CapabilityRequest request) {
                return delegate.prepare(request);
            }
        };
        ComponentRuntime runtime = new ComponentRuntime();
        TickCapabilityHost host = new TickCapabilityHost(capability);
        runtime.replaceComponents(List.of(new ProcessingComponent(null, host, BlockPos.ZERO, BlockPos.ZERO,
                (String) null)));
        queries.set(0);

        assertThat(runtime.hasTickFacets()).isFalse();
        assertThat(runtime.hasTickFacets()).isTrue();
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CapabilityTickResult result = runtime.executeTickPhase(new CapabilityTickContext(2L,
                CapabilityTickPhase.AFTER_INPUTS, null, 1L, new CapabilitySnapshot(runtime.capabilities()),
                controller.behaviorContext()));

        assertThat(result.failure()).isNull();
        assertThat(phases).containsExactly(CapabilityTickPhase.AFTER_INPUTS);
        runtime.replaceComponents(List.of());
        assertThat(runtime.hasTickFacets()).isFalse();
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class TickCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private TickCapabilityHost(MachineCapability capability) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            snapshot = new CapabilitySnapshot(List.of(capability));
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return snapshot;
        }
    }

    private record TickCapability(TickFacet tickFacet) implements MachineCapability, TickFacet {
        @Override
        public CapabilityType type() {
            return new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", "tick"));
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
