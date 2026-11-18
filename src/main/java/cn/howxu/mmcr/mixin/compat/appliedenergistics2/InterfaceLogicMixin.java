package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.util.ConfigInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.NetworkOwnedInputHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks AE2-originated input cache inserts and limits automatic returns to that tracked amount.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicMixin {
    @Shadow
    @Final
    protected InterfaceLogicHost host;

    @Shadow
    @Final
    private GenericStack[] plannedWork;

    @Inject(method = "updatePlan(I)V", at = @At("TAIL"))
    private void mmcr$limitInputReturnPlan(int slot, CallbackInfo callbackInfo) {
        if (!(host instanceof NetworkOwnedInputHost input)) return;

        GenericStack planned = plannedWork[slot];
        if (planned == null || planned.amount() >= 0L) {
            return;
        }

        long requested = -planned.amount();
        long returnable = Math.min(requested, input.networkOwnedAmount(slot, planned.what()));
        plannedWork[slot] = returnable == 0L
                ? null : new GenericStack(planned.what(), -returnable);
    }

    // Publish the new balance and its provenance together, before AE replans or notifies the host.
    @WrapOperation(method = {"acquireFromNetwork", "insertCraftedItems"}, at = @At(value = "INVOKE", target =
            "Lappeng/util/ConfigInventory;insert(ILappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long mmcr$networkInsert(ConfigInventory storage, int slot, AEKey key, long amount, Actionable mode,
                                    Operation<Long> original) {
        if (!(host instanceof NetworkOwnedInputHost input) || mode == Actionable.SIMULATE) {
            return original.call(storage, slot, key, amount, mode);
        }
        storage.beginBatch();
        try {
            long inserted = original.call(storage, slot, key, amount, mode);
            input.recordNetworkPull(slot, key, inserted);
            return inserted;
        } finally {
            storage.endBatch();
        }
    }

    @WrapOperation(method = "tryUsePlan", at = @At(value = "INVOKE", target =
            "Lappeng/util/ConfigInventory;extract(ILappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long mmcr$networkReturn(ConfigInventory storage, int slot, AEKey key, long amount, Actionable mode,
                                    Operation<Long> original) {
        if (!(host instanceof NetworkOwnedInputHost input)) return original.call(storage, slot, key, amount, mode);
        storage.beginBatch();
        try {
            long extracted = original.call(storage, slot, key, amount, mode);
            input.recordNetworkReturn(slot, key, extracted);
            return extracted;
        } finally {
            storage.endBatch();
        }
    }
}
