package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.network.view.RequestBody;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class PublicRuntimeApiTest {
    @Test
    void public_runtime_values_and_requests_are_immutable() {
        DataValue value = DataValue.map(Map.of("values", DataValue.list(List.of(DataValue.of(42L)))));
        RequestBody body = RequestBody.of(Map.of("payload", value));

        assertThat(body.get("payload")).contains(value);
        assertThat(body.values()).isUnmodifiable();
        assertThat(value.asMap().orElseThrow().get("values").asList().orElseThrow())
                .containsExactly(DataValue.of(42L));
    }

    @Test
    void io_plan_accepts_public_recipe_requirements() {
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of()));

        plan.addInput(new EnergyRequirement(IOType.INPUT, 20));

        assertThat(plan.requirements()).hasSize(1);
    }
}
