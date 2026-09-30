package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceHost;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes only MMCR-backed pattern-provider hosts to the CPU batch dispatcher.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PatternProviderLogicBatchAccess {
    @Nullable PatternInterfaceHost mmcr$patternInterfaceHost();
}
