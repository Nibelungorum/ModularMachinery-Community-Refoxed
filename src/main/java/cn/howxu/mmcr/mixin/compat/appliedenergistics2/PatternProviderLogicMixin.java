package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.crafting.IPatternDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceHost;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Propagates native pattern return-inventory drains to the MMCR pattern host.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    @Inject(method = "updatePatterns", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;requestUpdate(Lappeng/api/networking/IManagedGridNode;)V"))
    private void mmcr$publishBatchPatterns(CallbackInfo callback) {
        if (PatternBatchPlanning.isBatchHost(host) && host.getBlockEntity().getLevel() instanceof ServerLevel) {
            PatternBatchPlanning.publish((PatternInterfaceBlockEntity) host, patterns);
        }
    }

    @Inject(method = "getAvailablePatterns", at = @At("RETURN"), cancellable = true)
    private void mmcr$availableBatchPatterns(CallbackInfoReturnable<List<IPatternDetails>> callback) {
        if (PatternBatchPlanning.isBatchHost(host)) {
            callback.setReturnValue(PatternBatchPlanning.publishedPatterns(
                    (PatternInterfaceBlockEntity) host, callback.getReturnValue()));
        }
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private boolean mmcr$containsBatchPattern(List<IPatternDetails> originals, Object requested,
                                             Operation<Boolean> original) {
        if (PatternBatchPlanning.isBatchHost(host) && requested instanceof IPatternDetails pattern) {
            return PatternBatchPlanning.contains((PatternInterfaceBlockEntity) host, originals, pattern);
        }
        return original.call(originals, requested);
    }

    @Unique
    private long[] mmcr$returnInventoryAmounts = new long[0];

    @WrapOperation(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At(value = "NEW", target = "(Ljava/lang/Runnable;)Lappeng/helpers/patternprovider/PatternProviderReturnInventory;"))
    private PatternProviderReturnInventory mmcr$createReturnInventory(Runnable listener,
                                                                      Operation<PatternProviderReturnInventory> original) {
        return host instanceof PatternInterfaceHost ? new PatternReturnInventory(listener) : original.call(listener);
    }

    @WrapOperation(method = "clearContent", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/patternprovider/PatternProviderReturnInventory;clear()V"))
    private void mmcr$preserveCarrierReturns(PatternProviderReturnInventory inventory, Operation<Void> original) {
        // Clearing transient native patterns/sendList must not erase the block carrier's material owner.
        if (!(host instanceof PatternInterfaceHost)) original.call(inventory);
    }

    @Inject(method = "doWork", at = @At("HEAD"))
    private void mmcr$recordReturnInventoryAmount(CallbackInfoReturnable<Boolean> callback) {
        if (host instanceof PatternInterfaceHost) {
            PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
            if (mmcr$returnInventoryAmounts.length != logic.getReturnInv().size()) {
                mmcr$returnInventoryAmounts = new long[logic.getReturnInv().size()];
            }
            for (int slot = 0; slot < mmcr$returnInventoryAmounts.length; slot++) {
                mmcr$returnInventoryAmounts[slot] = logic.getReturnInv().getAmount(slot);
            }
        }
    }

    @Inject(method = "doWork", at = @At("RETURN"))
    private void mmcr$notifyReturnInventoryDrain(CallbackInfoReturnable<Boolean> callback) {
        if (host instanceof PatternInterfaceHost patternHost
                && mmcr$returnInventoryDrained()) {
            patternHost.onNativeReturnInventoryDrained();
        }
    }

    @Unique
    private boolean mmcr$returnInventoryDrained() {
        PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
        if (mmcr$returnInventoryAmounts.length != logic.getReturnInv().size()) return false;
        for (int slot = 0; slot < mmcr$returnInventoryAmounts.length; slot++) {
            if (logic.getReturnInv().getAmount(slot) < mmcr$returnInventoryAmounts[slot]) return true;
        }
        return false;
    }

    @Redirect(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/implementations/blockentities/ICraftingMachine;of(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lappeng/api/implementations/blockentities/ICraftingMachine;"))
    private ICraftingMachine mmcr$findCraftingMachine(Level level, BlockPos pos, Direction side) {
        return host instanceof PatternInterfaceHost patternHost
                ? patternHost.craftingMachine()
                : ICraftingMachine.of(level, pos, side);
    }
}
