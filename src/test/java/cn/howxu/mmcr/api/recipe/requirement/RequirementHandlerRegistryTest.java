package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class RequirementHandlerRegistryTest {
    private RequirementHandlerRegistry.TestScope scope;
    private static final RequirementHandler<TestRequirement> TEST_HANDLER = (value, capabilities, context) ->
            new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
    private static final RequirementType<TestRequirement> TEST_TYPE = new RequirementType.Definition<>(
            Identifier.fromNamespaceAndPath("mmcr_test", "registered_requirement"),
            MapCodec.unit(() -> new TestRequirement(null, RecipeModifier.IOType.INPUT)), TEST_HANDLER);

    @BeforeEach
    void openRegistryScope() {
        scope = RequirementHandlerRegistry.openTestScope();
    }

    @AfterEach
    void closeRegistryScope() {
        scope.close();
    }

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

    @Test
    void rejects_different_type_implementations_with_the_same_stable_identifier() {
        Identifier id = Identifier.fromNamespaceAndPath("mmcr_test", "same_identifier");
        RequirementType<TestRequirement> first = new IdentityType(id);
        RequirementType<TestRequirement> second = new IdentityType(id);

        RequirementHandlerRegistry.register(first);

        assertThatThrownBy(() -> RequirementHandlerRegistry.register(second))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RequirementHandlerRegistry.typeFor(id)).isSameAs(first);
    }

    @Test
    void test_scope_removes_custom_types_without_leaking_to_the_next_scope() {
        RequirementType<TestRequirement> type = type("scoped_requirement");
        RequirementHandlerRegistry.register(type);
        assertThat(RequirementHandlerRegistry.handlerFor(type)).isSameAs(TEST_HANDLER);

        scope.close();
        assertThat(RequirementHandlerRegistry.typeFor(type.id())).isNull();
        scope = RequirementHandlerRegistry.openTestScope();
        assertThat(RequirementHandlerRegistry.typeFor(type.id())).isNull();
    }

    @Test
    void canonical_codec_rejects_an_equal_identifier_substitute_type() {
        Identifier id = Identifier.fromNamespaceAndPath("mmcr_test", "canonical_codec");
        RequirementType<TestRequirement> canonical = new IdentityType(id);
        RequirementType<TestRequirement> substitute = new IdentityType(id);
        RequirementHandlerRegistry.register(canonical);
        TestRequirement requirement = new TestRequirement(substitute, RecipeModifier.IOType.INPUT);

        assertThat(RequirementHandlerRegistry.handlerFor(substitute)).isNull();
        assertThat(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).error()).isPresent();
        assertThatThrownBy(() -> MachineRequirement.copyOf(requirement))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RequirementType<TestRequirement> type(String path) {
        return new RequirementType.Definition<>(
                Identifier.fromNamespaceAndPath("mmcr_test", path),
                MapCodec.unit(() -> new TestRequirement(null, RecipeModifier.IOType.INPUT)), TEST_HANDLER);
    }

    private static final class IdentityType implements RequirementType<TestRequirement> {
        private final Identifier id;

        private IdentityType(Identifier id) {
            this.id = id;
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public MapCodec<TestRequirement> codec() {
            return MapCodec.unit(() -> new TestRequirement(this, RecipeModifier.IOType.INPUT));
        }

        @Override
        public RequirementHandler<TestRequirement> handler() {
            return TEST_HANDLER;
        }
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }
}
