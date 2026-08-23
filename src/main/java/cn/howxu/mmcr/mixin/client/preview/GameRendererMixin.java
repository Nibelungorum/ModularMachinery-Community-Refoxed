package cn.howxu.mmcr.mixin.client.preview;

import cn.howxu.mmcr.client.preview.scene.PreviewLightmapScope;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the isolated lightmap while a structure preview is being rendered.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "lightmap", at = @At("HEAD"), cancellable = true)
    private void mmcr$usePreviewLightmap(CallbackInfoReturnable<GpuTextureView> callback) {
        GpuTextureView override = PreviewLightmapScope.current();
        if (override != null) callback.setReturnValue(override);
    }
}
