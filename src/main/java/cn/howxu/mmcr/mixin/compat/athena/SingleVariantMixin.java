package cn.howxu.mmcr.mixin.compat.athena;

import cn.howxu.mmcr.compat.athena.loaded.AthenaGeometryModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keep an opted-in model dynamic instead of flattening it into a static cuboid part.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(SingleVariant.Unbaked.class)
abstract class SingleVariantMixin {
    @Shadow public abstract Variant variant();

    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    private void mmcr$bakeConnectedGeometry(ModelBaker baker, CallbackInfoReturnable<BlockStateModel> cir) {
        if (baker.getModel(variant().modelLocation()).wrapped() instanceof AthenaGeometryModel model) {
            cir.setReturnValue(model.bakeBlock(baker));
        }
    }
}
