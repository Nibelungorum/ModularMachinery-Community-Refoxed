package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class RequirementHandlerRegistryTest {
    private static final RequirementHandler<TestRequirement> TEST_HANDLER = (value, capabilities, context) ->
            new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
    private static final RequirementType<TestRequirement> TEST_TYPE = new RequirementType.Definition<>(
            Identifier.fromNamespaceAndPath("mmcr_test", "registered_requirement"),
            MapCodec.unit(() -> new TestRequirement(null, RecipeModifier.IOType.INPUT)), TEST_HANDLER);

    @Test
    void registers_and_invokes_a_custom_handler_with_planning_context() {
        TestRequirement requirement = new TestRequirement(TEST_TYPE, RecipeModifier.IOType.INPUT);
        RequirementHandlerRegistry.register(TEST_TYPE);

        assertThat(RequirementHandlerRegistry.handlerFor(TEST_TYPE)).isSameAs(TEST_HANDLER);
        RequirementPlan plan = TEST_HANDLER.plan(requirement, List.of(), new PlanningContext(4, 2));
        assertThat(plan.requirementIndex()).isEqualTo(2);
        assertThat(plan.maxParallelism()).isEqualTo(4);
        assertThat(plan.successful()).isTrue();
    }

    @Test
    void rejects_duplicate_and_null_handler_registration() {
        RequirementType<TestRequirement> type = type("duplicate_requirement");
        RequirementHandlerRegistry.register(type);

        assertThatThrownBy(() -> RequirementHandlerRegistry.register(type))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RequirementHandlerRegistry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RequirementHandlerRegistry.handlerFor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RequirementType<TestRequirement> type(String path) {
        return new RequirementType.Definition<>(
                Identifier.fromNamespaceAndPath("mmcr_test", path),
                MapCodec.unit(() -> new TestRequirement(null, RecipeModifier.IOType.INPUT)), TEST_HANDLER);
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }
}
