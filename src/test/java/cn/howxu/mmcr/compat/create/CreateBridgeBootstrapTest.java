package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Optional loading and canonical registrations without loading native Create classes.
 * @author howxu <dev@howxu.cn>
 */
class CreateBridgeBootstrapTest {
    @Test
    void absent_create_still_decodes_but_plans_unavailable() {
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            CreateBridgeBootstrap.installForTesting(CreateBridgeBootstrap.selectForTesting(false));
            CreateRecipeTypes.register();
            assertThat(CreateBridge.get().available()).isFalse();
            var requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"type\":\"create:stress\",\"io\":\"input\",\"stress\":8,\"min_rpm\":32}")).getOrThrow();
            assertThat(requirement).isEqualTo(StressRequirement.input(8, 32));
            var result = new RequirementPlanner().plan(List.of(requirement), List.of(), new PlanningContext(1, 0));
            assertThat(result.plan()).isNull();
            assertThat(result.failure().reason()).isSameAs(CreateFailureReasons.CREATE_UNAVAILABLE);
        } finally {
            CreateBridgeBootstrap.resetForTesting();
        }
    }

    @Test
    void repeated_registration_preserves_other_types_and_registers_again_after_scope_close() {
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            var other = new RequirementType.Definition<>(ResourceLocation.parse("test:other"),
                    StressRequirement.CODEC, new StressRequirementHandler());
            RequirementHandlerRegistry.register(other);
            CreateRecipeTypes.register();
            CreateRecipeTypes.register();
            assertThat(RequirementHandlerRegistry.typeFor(CreateRecipeTypes.STRESS)).isSameAs(StressRequirement.TYPE);
            assertThat(RequirementHandlerRegistry.typeFor(other.id())).isSameAs(other);
        }
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            CreateRecipeTypes.register();
            assertThat(RequirementHandlerRegistry.typeFor(CreateRecipeTypes.STRESS)).isSameAs(StressRequirement.TYPE);
        }
    }
}
