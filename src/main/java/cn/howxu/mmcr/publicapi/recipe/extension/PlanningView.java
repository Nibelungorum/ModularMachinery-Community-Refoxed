package cn.howxu.mmcr.publicapi.recipe.extension;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.OutputMode;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced planning context. Delegated plans retain shared reservations and deferred factories.
 * Prepared operations are tagged with requestedParallelism and rescaled by the core planner when needed.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface PlanningView {
    long requestedParallelism(); int requirementIndex(); boolean allowPartialOutputs();
    OutputMode outputMode();
    List<CapabilityAccess> capabilities();
    ReservationsView reservations();
    RequirementPlanSpec plan(RequirementSpec requirement);
    RequirementPlanSpec prepared(long maxParallelism, List<RecipeOperation> operations);
    RequirementPlanSpec deferred(long maxParallelism, OperationPlanner operations, ReservationPlanner reservations);
    RequirementPlanSpec blocked(PlanStatus failure);
    PlanStatus failure(ResourceLocation id, ResourceLocation source, Map<String, String> details);
    OutputSimulationView outputSimulation(long requested, long accepted);
}
