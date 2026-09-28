package cn.howxu.mmcr.api.capability.status;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable chain of sources that observed a failure.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FailureTrace(List<FailureTrace.Frame> frames) {
    public FailureTrace {
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
    }

    public static FailureTrace single(Frame frame) {
        return new FailureTrace(List.of(frame));
    }

    public FailureTrace append(Frame frame) {
        List<Frame> appended = new ArrayList<>(frames);
        appended.add(Objects.requireNonNull(frame, "frame"));
        return new FailureTrace(appended);
    }

    /**
     * One source observation in a failure trace.
     *
     * @param source source that observed the failure
     * @param phase execution phase of the observation
     * @param recipeId related recipe, when available
     * @param requirementIndex related requirement index, when available
     * @author howxu <dev@howxu.cn>
     */
    public record Frame(ResourceLocation source, FailurePhase phase,
                        @Nullable ResourceLocation recipeId, @Nullable Integer requirementIndex) {
        public Frame {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(phase, "phase");
        }
    }
}
