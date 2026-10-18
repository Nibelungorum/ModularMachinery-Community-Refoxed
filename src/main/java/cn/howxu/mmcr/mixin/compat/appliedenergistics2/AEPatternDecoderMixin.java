package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEPatternDecoder;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchCodec;
import cn.howxu.mmcr.registry.ModDataComponents;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Claims only MMCR-marked definitions before the native encoded-item decoder sees them.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(AEPatternDecoder.class)
public abstract class AEPatternDecoderMixin {
    @Inject(method = "decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
            at = @At("HEAD"), cancellable = true)
    private void mmcr$decodeBatch(AEItemKey definition, Level level, CallbackInfoReturnable<IPatternDetails> callback) {
        if (definition != null && definition.get(ModDataComponents.PATTERN_BATCH.get()) != null) {
            callback.setReturnValue(PatternBatchCodec.decode(definition, level));
        }
    }
}
