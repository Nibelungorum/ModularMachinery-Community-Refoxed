package cn.howxu.mmcr.publicapi.recipe.extension;
import java.util.List;
import org.jetbrains.annotations.Nullable;
/** Callback result materialized by the core planner. @author howxu <dev@howxu.cn> */
public record OperationBatch(List<RecipeOperation> operations, @Nullable PlanStatus failure, @Nullable OutputSimulationView outputSimulation) {
    public OperationBatch(List<RecipeOperation> operations, @Nullable PlanStatus failure) { this(operations, failure, null); }
}
