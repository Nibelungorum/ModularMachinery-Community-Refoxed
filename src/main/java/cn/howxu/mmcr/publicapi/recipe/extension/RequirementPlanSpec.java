package cn.howxu.mmcr.publicapi.recipe.extension;
import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced plan backed by the core planner's plan. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RequirementPlanSpec { int requirementIndex(); long maxParallelism(); boolean successful(); Optional<PlanStatus> failure(); }
