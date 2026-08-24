package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class RequirementHandlerRegistryTest {
    private static final RequirementType<TestRequirement> TEST_TYPE = new RequirementType<>(
            Identifier.fromNamespaceAndPath("mmcr_test", "registered_requirement"));

    @Test
    void registers_and_invokes_a_custom_handler_with_planning_context() {
        TestRequirement requirement = new TestRequirement(TEST_TYPE, RecipeModifier.IOType.INPUT);
        RequirementHandler<TestRequirement> handler = new RequirementHandler<>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return TEST_TYPE;
            }

            @Override
            public RequirementPlan plan(TestRequirement value, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
            }
        };

        RequirementHandlerRegistry.register(handler);

        assertThat(RequirementHandlerRegistry.handlerFor(TEST_TYPE)).isSameAs(handler);
        RequirementPlan plan = handler.plan(requirement, List.of(), new PlanningContext(4, 2));
        assertThat(plan.requirementIndex()).isEqualTo(2);
        assertThat(plan.maxParallelism()).isEqualTo(4);
        assertThat(plan.successful()).isTrue();
    }

    @Test
    void rejects_duplicate_and_null_handler_registration() {
        RequirementType<TestRequirement> type = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "duplicate_requirement"));
        RequirementHandler<TestRequirement> handler = handler(type);
        RequirementHandlerRegistry.register(handler);

        assertThatThrownBy(() -> RequirementHandlerRegistry.register(handler(type)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RequirementHandlerRegistry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RequirementHandlerRegistry.handlerFor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RequirementHandler<TestRequirement> handler(RequirementType<TestRequirement> type) {
        return new RequirementHandler<>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return type;
            }

            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
            }
        };
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }
}
