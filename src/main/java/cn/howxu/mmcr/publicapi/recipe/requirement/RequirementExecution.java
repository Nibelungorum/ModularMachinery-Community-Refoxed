package cn.howxu.mmcr.publicapi.recipe.requirement;

import net.minecraft.resources.ResourceLocation;
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
    /** When true, execution records a successful one-shot payment at the original input index. */
    default boolean paysAtStart(R value) { return false; }
    default R asRetainedInput(R value) { throw new IllegalStateException("Missing read-only input projection"); }
    /** Exact search-only equivalent, including shortage semantics. Null requests synchronous proof. */
    default RequirementSpec searchProjection(R value) { return null; }
    /** -1 leaves batching unproven; zero denotes a condition, positive values a linear per-craft bound. */
    default long batchAmount(R value, int duration) { return -1L; }
    default ResourceLocation batchIdentity(R value) { return null; }
    default float consumptionProbability(R value) { return Float.NaN; }
    default boolean perTickOutput(R value) { return false; }
    default R applyModifiers(R value, List<RecipeAdjustmentSpec> modifiers) { return value; }
    default R applyLevelModifiers(R value, double energyMultiplier, double outputMultiplier) { return value; }
    default boolean overlaps(R value, RequirementSpec other) { return false; }
    default List<ResourceWakeupSpec<?>> resourceWakeups(R value) { return List.of(); }
}
