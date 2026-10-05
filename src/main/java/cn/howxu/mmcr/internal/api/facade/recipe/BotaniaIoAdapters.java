package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.botania.ManaRecipeDeclarations;
import cn.howxu.mmcr.publicapi.recipe.CustomIoSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.IoValues;

/** Delegates total-mana declarations to neutral codecs.
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaIoAdapters {
    private BotaniaIoAdapters() {}
    public static CustomIoSpec manaInput(long amount) {
        return IoValues.customIo(BotaniaManaIds.MANA, IoDirection.INPUT, ManaRecipeDeclarations.inputPayload(amount));
    }
    public static CustomIoSpec manaOutput(long amount) {
        return IoValues.customIo(BotaniaManaIds.MANA, IoDirection.OUTPUT, ManaRecipeDeclarations.outputPayload(amount));
    }
}
