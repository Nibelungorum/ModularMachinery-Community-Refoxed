package cn.howxu.mmcr.mixin.compat.extendedae_plus;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.MirrorPatternInterfaceLogic;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity.MasterLocation;
import com.extendedae_plus.items.tools.MirrorPatternBindingToolItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Includes MMCR mirror interfaces in EAEP's native binding-tool workflow.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = MirrorPatternBindingToolItem.class, remap = false)
public abstract class MirrorPatternBindingToolItemMixin {
    @Shadow
    private static @Nullable MasterLocation getSelectedMaster(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private void handleRangeBinding(Level level, BlockPos clickedPos, ItemStack stack, Player player) {
        throw new AssertionError();
    }

    @Inject(method = "handleBlockUse", at = @At("HEAD"), cancellable = true)
    private void mmcr$useMirrorInterface(UseOnContext context, ItemStack stack,
                                        CallbackInfoReturnable<InteractionResult> callback) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PatternInterfaceBlockEntity mirror)
                || !(mirror.getLogic() instanceof MirrorPatternInterfaceLogic logic)) return;

        callback.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide()));
        if (level.isClientSide()) return;

        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            handleRangeBinding(level, context.getClickedPos(), stack, player);
            return;
        }

        if (logic.hasMasterBinding()) {
            boolean unbound = logic.unbindFromMaster();
            if (player != null && unbound) {
                player.displayClientMessage(logic.createUnboundMessage(), true);
            }
            return;
        }

        MasterLocation selectedMaster = getSelectedMaster(stack);
        if (selectedMaster == null) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("extendedae_plus.message.mirror_binding_tool.no_selection"), true);
            }
            return;
        }

        if (logic.bindToMaster(selectedMaster)) {
            if (player != null) {
                player.displayClientMessage(logic.createBoundMessage(), true);
            }
        } else if (player != null) {
            player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.mirror_binding_tool.bind_failed"), true);
        }
    }

    @Inject(method = "getClickedMaster", at = @At("HEAD"), cancellable = true)
    private static void mmcr$excludeMirrorMaster(Level level, UseOnContext context,
                                                CallbackInfoReturnable<MasterLocation> callback) {
        if (level.getBlockEntity(context.getClickedPos()) instanceof PatternInterfaceBlockEntity mirror
                && mirror.getLogic() instanceof MirrorPatternInterfaceLogic) {
            callback.setReturnValue(null);
        }
    }

    @WrapOperation(method = "bindMirrorsInRange", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static BlockEntity mmcr$bindMirrorInterfaceInRange(Level level, BlockPos pos,
                                                              Operation<BlockEntity> original,
                                                              @Local(argsOnly = true) MasterLocation selectedMaster,
                                                              @Local(ordinal = 0) LocalIntRef totalMirrors,
                                                              @Local(ordinal = 1) LocalIntRef boundMirrors) {
        BlockEntity blockEntity = original.call(level, pos);
        if (blockEntity instanceof PatternInterfaceBlockEntity mirror
                && mirror.getLogic() instanceof MirrorPatternInterfaceLogic logic) {
            totalMirrors.set(totalMirrors.get() + 1);
            if (logic.bindToMaster(selectedMaster)) {
                boundMirrors.set(boundMirrors.get() + 1);
            }
        }
        return blockEntity;
    }
}
