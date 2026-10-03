package cn.howxu.mmcr.mixin.compat.extendedae_plus;

import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import com.extendedae_plus.api.bridge.PatternProviderLogicSyncBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates mirror source caches and rebuilds restored patterns after the complete host NBT load.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = PatternInterfaceBlockEntity.class, remap = false)
public abstract class PatternInterfaceBlockEntityMixin {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void mmcr$restorePatternSync(CompoundTag input, HolderLookup.Provider registries, CallbackInfo callback) {
        PatternInterfaceBlockEntity host = (PatternInterfaceBlockEntity) (Object) this;
        PatternProviderLogic logic = host.getLogic();
        if (logic instanceof PatternProviderLogicSyncBridge bridge) bridge.eap$markPatternSyncDirty();
        // Hosts not yet initialized rebuild through their existing first-tick callback.
        if (host.getLevel() instanceof ServerLevel && host.getMainNode().getNode() != null) logic.updatePatterns();
    }
}
