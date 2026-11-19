package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import net.minecraft.world.inventory.MenuType;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

/**
 * A port kind that supplies its AE2 pattern-provider logic profile.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PatternLogicKind extends AE2PortKind {
    @Override default MenuType<?> menuType() { return AE2MenuTypes.PATTERN; }

    default boolean readOnlyPatterns() {
        return false;
    }

    default PatternProviderLogic createPatternLogic(IManagedGridNode node, PatternProviderLogicHost host) {
        return new PatternProviderLogic(node, host);
    }
}
