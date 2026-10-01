package cn.howxu.mmcr.publicapi.machine;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Producer-created appearance configuration. Consumers must not implement it. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface AppearanceOptions {
    AppearanceOptions appearance(ResourceLocation id);
    AppearanceOptions appearance(String id);
    AppearanceOptions machineBasicBlock(ResourceLocation id);
    AppearanceOptions machineBasicBlock(String id);
    AppearanceOptions controllerBaseTexture(ResourceLocation id);
    AppearanceOptions formedPortBaseTexture(ResourceLocation id);
    AppearanceOptions controllerIdleOverlayTexture(ResourceLocation id);
    AppearanceOptions controllerActiveOverlayTexture(ResourceLocation id);
    View build();

    /** Producer-created immutable declaration view. @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface View {
        @Nullable ResourceLocation machineBasicBlock();
        @Nullable ResourceLocation controllerBaseTexture();
        @Nullable ResourceLocation formedPortBaseTexture();
        @Nullable ResourceLocation controllerIdleOverlayTexture();
        @Nullable ResourceLocation controllerActiveOverlayTexture();
    }
}
