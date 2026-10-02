package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRecipeDeclarations;
import cn.howxu.mmcr.publicapi.recipe.CustomIoSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.IoValues;

/** Delegates source declarations to neutral codecs without loading Ars implementation classes.
 * @author howxu <dev@howxu.cn>
 */
public final class ArsNouveauIoAdapters {
    private ArsNouveauIoAdapters() {}
    public static CustomIoSpec sourceInput(long amount) {
        return IoValues.customIo(ArsSourceIds.SOURCE, IoDirection.INPUT, SourceRecipeDeclarations.inputPayload(amount));
    }
    public static CustomIoSpec sourceOutput(long amount) {
        return IoValues.customIo(ArsSourceIds.SOURCE, IoDirection.OUTPUT, SourceRecipeDeclarations.outputPayload(amount));
    }
}
