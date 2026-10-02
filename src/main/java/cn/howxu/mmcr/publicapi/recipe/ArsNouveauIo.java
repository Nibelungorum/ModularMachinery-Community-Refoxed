package cn.howxu.mmcr.publicapi.recipe;

import cn.howxu.mmcr.internal.api.facade.recipe.ArsNouveauIoAdapters;

/** Optional-mod-safe source recipe declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class ArsNouveauIo {
    private ArsNouveauIo() {}
    public static CustomIoSpec sourceInput(long amount) { return ArsNouveauIoAdapters.sourceInput(amount); }
    public static CustomIoSpec sourceOutput(long amount) { return ArsNouveauIoAdapters.sourceOutput(amount); }
}
