package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies generic failure matcher delegation for factory recipe threads.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryRecipeThreadTest {
    @Test
    void delegates_custom_requirement_wakeups_without_concrete_requirement_dispatch() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            Identifier id = Identifier.fromNamespaceAndPath("mmcr_test", "factory_wakeup");
            RequirementHandler<TestRequirement> handler = new RequirementHandler<>() {
                @Override
                public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                            PlanningContext context) {
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }

                @Override
                public List<ResourceWakeup> resourceWakeups(TestRequirement requirement) {
                    return List.of(new ResourceWakeup(Set.of("insufficient_resource"), WakeupReason.INPUT_AVAILABLE,
                            resource -> resource.equals("virtual-resource")));
                }
            };
            RequirementType<TestRequirement> type = new RequirementType.Definition<>(id,
                    MapCodec.unit(() -> new TestRequirement(null, RecipeModifier.IOType.INPUT)), handler);
            RequirementHandlerRegistry.register(type);

            EnumMap<cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers =
                    new EnumMap<>(cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier.Reason.class);
            FactoryRecipeThread.addRequirementMatchers(matchers,
                    new TestRequirement(type, RecipeModifier.IOType.INPUT), "insufficient_resource");

            assertThat(matchers).containsKey(INPUT_AVAILABLE);
            assertThat(matchers.get(INPUT_AVAILABLE).getFirst().test("virtual-resource")).isTrue();
            assertThat(matchers.get(INPUT_AVAILABLE).getFirst().test("other-resource")).isFalse();
        }
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }
}
