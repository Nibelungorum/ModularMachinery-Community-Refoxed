package cn.howxu.mmcr.publicapi.recipe.extension;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced capability metadata. Use PlanningView.plan to invoke its registered core handler.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface CapabilityAccess {
    ResourceLocation kindId(); Set<IoDirection> directions(); int outputPriority();
    <R> Optional<ResourceStore<R>> resources(Class<R> type);
    Optional<LongStore> longValues();
    boolean supportsLargeStacks();
    <R> RecipeOperation prepareResources(IoDirection io, long parallelism, List<ResourceAction<R>> actions);
    /** amount is the total quantity for this operation, already scaled for parallelism. */
    RecipeOperation prepareValue(IoDirection io, long parallelism, long amount, boolean insert);
    RecipeOperation prepareSmartValue(IoDirection io, long parallelism, String interfaceType, float value);
}
