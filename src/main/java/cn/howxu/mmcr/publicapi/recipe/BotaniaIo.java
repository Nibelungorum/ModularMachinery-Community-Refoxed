package cn.howxu.mmcr.publicapi.recipe;

import cn.howxu.mmcr.internal.api.facade.recipe.BotaniaIoAdapters;

/** Neutral total-mana declarations for Botania recipes.
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaIo {
    private BotaniaIo() {}
    public static CustomIoSpec manaInput(long amount) { return BotaniaIoAdapters.manaInput(amount); }
    public static CustomIoSpec manaOutput(long amount) { return BotaniaIoAdapters.manaOutput(amount); }
}
