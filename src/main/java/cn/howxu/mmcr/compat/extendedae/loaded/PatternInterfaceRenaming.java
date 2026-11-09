package cn.howxu.mmcr.compat.extendedae.loaded;

import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import com.glodblock.github.extendedae.container.ContainerRenamer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Opens ExtendedAE's native renamer only for the two writable pattern-interface profiles.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceRenaming {
    private PatternInterfaceRenaming() {
    }

    public static boolean supports(Object host) {
        return host instanceof PatternInterfaceBlockEntity pattern
                && (pattern.kind() == PatternInterfaceKind.INSTANCE
                || pattern.kind() == ExtendedPatternInterfaceKind.INSTANCE);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.isCanceled() || player.isSpectator() || event.getHand() != InteractionHand.MAIN_HAND
                || InteractionUtil.isInAlternateUseMode(player)
                || !(event.getItemStack().getItem() instanceof QuartzCuttingKnifeItem)) return;
        var host = event.getLevel().getBlockEntity(event.getPos());
        if (!supports(host)) return;
        if (event.getLevel().isClientSide()
                || MenuOpener.open(ContainerRenamer.TYPE, player, MenuLocators.forBlockEntity(host))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
