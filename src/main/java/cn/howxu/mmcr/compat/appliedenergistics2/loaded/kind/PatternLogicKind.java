package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

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
    default boolean readOnlyPatterns() {
        return false;
    }

    default PatternProviderLogic createPatternLogic(IManagedGridNode node, PatternProviderLogicHost host) {
        return new PatternProviderLogic(node, host);
    }
}
