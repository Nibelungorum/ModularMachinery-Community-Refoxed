package cn.howxu.mmcr.api.capability.status;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable failure reason, trace, and diagnostic details.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FailureOccurrence(@Nullable FailureReason reason, FailureTrace trace,
                                Map<String, String> details) {
    public FailureOccurrence {
        Objects.requireNonNull(trace, "trace");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }

    public static FailureOccurrence at(@Nullable FailureReason reason, ResourceLocation source, FailurePhase phase,
                                       @Nullable ResourceLocation recipeId, @Nullable Integer requirementIndex,
                                       Map<String, String> details) {
        return new FailureOccurrence(reason,
                FailureTrace.single(new FailureTrace.Frame(source, phase, recipeId, requirementIndex)), details);
    }

    public FailureOccurrence append(ResourceLocation source, FailurePhase phase,
                                    @Nullable ResourceLocation recipeId, @Nullable Integer requirementIndex) {
        return new FailureOccurrence(reason,
                trace.append(new FailureTrace.Frame(source, phase, recipeId, requirementIndex)), details);
    }
}
