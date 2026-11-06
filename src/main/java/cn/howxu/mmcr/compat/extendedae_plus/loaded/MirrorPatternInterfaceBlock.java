package cn.howxu.mmcr.compat.extendedae_plus.loaded;

import appeng.api.implementations.items.IMemoryCard;
import appeng.util.InteractionUtil;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** @author howxu <dev@howxu.cn> */
public final class MirrorPatternInterfaceBlock extends IOPortBlock {
    public MirrorPatternInterfaceBlock(IOPortKind kind, Supplier<? extends BlockEntityType<?>> type, Properties properties) {
        super(kind, type, properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof IMemoryCard || InteractionUtil.canWrenchRotate(stack)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(
                        Component.translatable("extendedae_plus.message.mirror_pattern_provider.readonly"));
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof PatternInterfaceBlockEntity host
                && host.getLogic() instanceof MirrorPatternInterfaceLogic logic) {
            if (!level.isClientSide()) player.sendOverlayMessage(logic.getStatusMessage());
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
