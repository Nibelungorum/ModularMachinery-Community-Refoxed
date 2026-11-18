package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class LevelRequirementHandler implements RequirementHandler<LevelRequirement> {
    @Override
    public List<SearchOverlap> captureOverlap(LevelRequirement requirement) {
        return List.of();
    }

    @Override
    public SearchDemand captureSearch(LevelRequirement requirement) {
        return SearchDemand.operation(SearchDemand.Kind.NOOP, 0L, 0D, BuiltinFailureReasons.MISSING_INPUT);
    }

    @Override public Object derivationToken() { return this; }

    @Override
    public RequirementPlan plan(LevelRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
    }
}
