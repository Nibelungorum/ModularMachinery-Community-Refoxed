package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Plans execution for one machine requirement type.
 *
 * @param <R> the requirement handled by this handler
 * @author howxu <dev@howxu.cn>
 */
public interface RequirementHandler<R extends MachineRequirement> {
    RequirementType<R> type();

    /**
     * Returns the complete map codec for this requirement, including its {@code type} discriminator.
     *
     * <p>Handlers without a codec remain valid for in-memory planning but cannot be persisted through
     * {@link MachineRequirement#CODEC}. The registry lookup is still required before this codec is used.</p>
     */
    default @Nullable Codec<R> codec() {
        return null;
    }

    RequirementPlan plan(R requirement, List<MachineCapability> capabilities, PlanningContext context);
}
