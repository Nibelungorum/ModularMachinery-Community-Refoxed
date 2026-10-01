package cn.howxu.mmcr.publicapi.machine;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Producer-created controller configuration. Consumers must not implement it. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ControllerOptions {
    ControllerOptions id(ResourceLocation id);
    ControllerOptions textures(ResourceLocation front, ResourceLocation otherFaces);
    ControllerOptions textures(ResourceLocation front, ResourceLocation side, ResourceLocation top, ResourceLocation bottom);
    ControllerOptions frontTexture(ResourceLocation id);
    ControllerOptions sideTexture(ResourceLocation id);
    ControllerOptions topTexture(ResourceLocation id);
    ControllerOptions bottomTexture(ResourceLocation id);
    ControllerOptions allowVerticalFacing();
    ControllerOptions allowVerticalFacing(boolean enabled);
    ControllerOptions fullyRotationallySymmetric();
    ControllerOptions fullyRotationallySymmetric(boolean enabled);
    ControllerOptions requireVerticalFacing();
    ControllerOptions requireVerticalFacing(boolean enabled);
    ControllerOptions tooltip(String... translationKeys);
    View build();

    /** Producer-created immutable declaration view. @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface View {
        @Nullable ResourceLocation id();
        @Nullable ResourceLocation frontTexture();
        @Nullable ResourceLocation sideTexture();
        @Nullable ResourceLocation topTexture();
        @Nullable ResourceLocation bottomTexture();
        boolean allowVerticalFacing();
        boolean fullyRotationallySymmetric();
        boolean requireVerticalFacing();
        List<String> tooltip();
    }
}
