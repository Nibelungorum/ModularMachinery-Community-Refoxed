package cn.howxu.mmcr.mixin.client.preview;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the render setup needed by the persistent preview buffer path.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Accessor("state")
    RenderSetup mmcr$getState();
}
