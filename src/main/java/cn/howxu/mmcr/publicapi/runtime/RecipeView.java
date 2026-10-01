package cn.howxu.mmcr.publicapi.runtime;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced runtime recipe. requirements/outputs are original data; runtime* getters derive effective data.
 * Context phase getters remain the source of the current execution's values.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RecipeView {
    ResourceLocation id(); ResourceLocation recipePoolId(); int tickTime(); int priority(); int maxThreads();
    boolean cancelRecipeOnPerTickFailure(); boolean parallelized(); boolean allowPartialOutputs();
    List<RequirementSpec> requirements(); List<OutputView> outputs(); List<RecipeAdjustmentSpec> modifiers(); Set<ResourceLocation> requiredHostIds();
    List<RequirementSpec> runtimeRequirements();
    List<RequirementSpec> runtimeRequirements(List<RecipeAdjustmentSpec> extraModifiers);
    List<RequirementSpec> runtimeRequirements(List<RecipeAdjustmentSpec> extraModifiers, double energyMultiplier, double outputMultiplier);
    List<OutputView> runtimeMachineOutputs(); List<OutputView> runtimeMachineOutputs(List<RecipeAdjustmentSpec> extraModifiers);
}
