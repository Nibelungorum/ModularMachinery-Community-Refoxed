package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;

import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import cn.howxu.mmcr.internal.port.IOPortKind;

/**
 * A port kind that supplies its AE2 pattern-provider logic profile.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PatternLogicKind extends IOPortKind {
    @Override default InteractionResult openSpecialMenu(Player player, Level level, BlockPos pos) {
        return player instanceof ServerPlayer server && AE2Bridge.get().openMenu(server, level, pos)
                ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    default boolean readOnlyPatterns() {
        return false;
    }

    default PatternProviderLogic createPatternLogic(IManagedGridNode node, PatternProviderLogicHost host) {
        return new PatternProviderLogic(node, host);
    }
}
