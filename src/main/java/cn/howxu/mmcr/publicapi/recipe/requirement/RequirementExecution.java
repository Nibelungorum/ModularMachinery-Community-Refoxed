package cn.howxu.mmcr.publicapi.recipe.requirement;
import cn.howxu.mmcr.publicapi.recipe.extension.PlanningView;
import cn.howxu.mmcr.publicapi.recipe.extension.RequirementPlanSpec;
import cn.howxu.mmcr.publicapi.recipe.extension.ResourceWakeupSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import java.util.List;
/** Open execution SPI; returned plans are executed only by the shared core planner.
 * @author howxu <dev@howxu.cn> */
@FunctionalInterface
public interface RequirementExecution<R> {
    RequirementPlanSpec plan(R value, PlanningView context);
    default R applyModifiers(R value, List<RecipeAdjustmentSpec> modifiers) { return value; }
    default R applyLevelModifiers(R value, double energyMultiplier, double outputMultiplier) { return value; }
    default boolean overlaps(R value, RequirementSpec other) { return false; }
    default List<ResourceWakeupSpec<?>> resourceWakeups(R value) { return List.of(); }
}
