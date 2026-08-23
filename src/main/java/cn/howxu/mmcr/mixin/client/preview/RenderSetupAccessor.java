package cn.howxu.mmcr.mixin.client.preview;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes render-pipeline state for persistent preview draw calls.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Accessor("pipeline")
    RenderPipeline mmcr$getPipeline();

    @Accessor("textureTransform")
    TextureTransform mmcr$getTextureTransform();
}
