package cn.howxu.mmcr.compat.ae2lt.client;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.jade.RecipeOutputComponentProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ui.JadeUI;

/** Adds the native tier icon to Jade's existing localized output row.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningJadeDisplay {
    private LightningJadeDisplay() {}

    private static final RecipeOutputComponentProvider.Renderer<LightningOutput> RENDERER = (output, amount) -> tooltip -> {
        tooltip.add(Component.object(LightningSprites.forTier(output.tier()), Component.empty())
                .withStyle(ChatFormatting.WHITE));
        tooltip.append(JadeUI.spacer(2, 0));
        tooltip.append(RecipeOutputComponentProvider.genericText(output.outputType(), amount));
    };

    public static void register() {
        for (LightningTier tier : LightningTier.values()) {
            RecipeOutputComponentProvider.register(LightningRecipeTypes.outputType(tier), RENDERER);
        }
    }
}
