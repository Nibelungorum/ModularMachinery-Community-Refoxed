package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternProviderLogicBatchAccess;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceHost;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adds the MMCR host marker to AE2's shared provider logic, including EAE interfaces.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicBatchAccessMixin implements PatternProviderLogicBatchAccess {
    @Shadow @Final private PatternProviderLogicHost host;

    @Override
    public @Nullable PatternInterfaceHost mmcr$patternInterfaceHost() {
        return host instanceof PatternInterfaceHost patternHost ? patternHost : null;
    }
}
