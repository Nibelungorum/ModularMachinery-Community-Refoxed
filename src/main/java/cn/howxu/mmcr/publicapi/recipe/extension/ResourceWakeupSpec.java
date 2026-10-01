package cn.howxu.mmcr.publicapi.recipe.extension;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
/** Typed wakeup matcher; foreign resource classes never reach the predicate.
 * Scalar capability signals (including energy availability) use their family ResourceLocation;
 * item/fluid signals retain their platform resource type.
 * @author howxu <dev@howxu.cn> */
public record ResourceWakeupSpec<T>(Set<ResourceLocation> failureReasonIds, Reason reason,
        Class<T> resourceType, Predicate<ResourceChangeView<T>> matcher) {
    public enum Reason { INPUT_AVAILABLE, ENERGY_AVAILABLE, OUTPUT_CAPACITY }
}
