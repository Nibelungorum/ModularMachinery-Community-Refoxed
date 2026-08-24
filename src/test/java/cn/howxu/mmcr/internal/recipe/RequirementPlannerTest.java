package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies generic requirement planning without concrete port knowledge.
 *
 * @author howxu <dev@howxu.cn>
 */
class RequirementPlannerTest {
    private static final RequirementType<TestRequirement> TYPE = new RequirementType<>(
            Identifier.fromNamespaceAndPath("mmcr_test", "planner_requirement"));

    @Test
    void looks_up_handler_filters_capabilities_and_limits_parallelism() {
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return TYPE;
            }

            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                int limit = capabilities.stream().mapToInt(capability -> ((TestCapability) capability).limit()).min().orElse(0);
                return new RequirementPlan(context.requirementIndex(), limit,
                        capabilities.stream().map(capability -> capability.prepare(new TestRequest(context.requestedParallelism())))
                                .toList(), null);
            }
        });

        TestCapability matching = new TestCapability(TYPE.id(), IOType.INPUT, 3);
        TestCapability wrongDirection = new TestCapability(TYPE.id(), IOType.OUTPUT, 1);
        TestCapability wrongType = new TestCapability(MMCR.id("other"), IOType.INPUT, 1);

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(TYPE, RecipeModifier.IOType.INPUT)),
                List.of(matching, wrongDirection, wrongType),
                new PlanningContext(8, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(3);
        assertThat(matching.requestedParallelisms()).containsExactly(8, 3);
        assertThat(wrongDirection.requestedParallelisms()).isEmpty();
        assertThat(wrongType.requestedParallelisms()).isEmpty();
    }

    @Test
    void supports_mixed_requirements_and_custom_handlers_without_planner_changes() {
        RequirementType<TestRequirement> secondType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "planner_second_requirement"));
        RequirementHandlerRegistry.register(new SimpleHandler(secondType));

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(TYPE, RecipeModifier.IOType.INPUT), new TestRequirement(secondType, RecipeModifier.IOType.OUTPUT)),
                List.of(new TestCapability(TYPE.id(), IOType.INPUT, 5),
                        new TestCapability(secondType.id(), IOType.OUTPUT, 2)),
                new PlanningContext(4, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(result.plan().requirements()).hasSize(2);
    }

    @Test
    void carries_a_structured_handler_failure() {
        RequirementType<TestRequirement> failureType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "planner_failure_requirement"));
        ExecutionStatus failure = new ExecutionStatus(failureType.id(), StatusSeverity.FAILURE, failureType.id(), java.util.Map.of());
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return failureType;
            }

            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 0, List.of(), failure);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(failureType, RecipeModifier.IOType.INPUT)), List.of(), new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).isSameAs(failure);
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }

    private record TestRequest(int parallelism) implements CapabilityRequest {
        @Override
        public CapabilityType type() {
            return new CapabilityType(TYPE.id());
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }
    }

    private static final class TestCapability implements MachineCapability {
        private final CapabilityType type;
        private final IOType ioType;
        private final int limit;
        private final java.util.ArrayList<Integer> requestedParallelisms = new java.util.ArrayList<>();

        private TestCapability(Identifier type, IOType ioType, int limit) {
            this.type = new CapabilityType(type);
            this.ioType = ioType;
            this.limit = limit;
        }

        private int limit() {
            return limit;
        }

        private List<Integer> requestedParallelisms() {
            return requestedParallelisms;
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public IOType ioType() {
            return ioType;
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TestCapability.this.type;
                }

                @Override
                public IOType ioType() {
                    return TestCapability.this.ioType;
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            requestedParallelisms.add(request.parallelism());
            return transaction -> CapabilityResult.successful();
        }
    }

    private record SimpleHandler(RequirementType<TestRequirement> type) implements RequirementHandler<TestRequirement> {
        @Override
        public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return new RequirementPlan(context.requirementIndex(), capabilities.isEmpty() ? 0 : 2, List.of(), null);
        }
    }
}
