package cn.howxu.mmcr.publicapi.runtime;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** MMCR-provided failure trace frame. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface FailureFrame {
    ResourceLocation source();
    FailurePhase phase();
    @Nullable ResourceLocation recipeId();
    @Nullable Integer requirementIndex();
}
