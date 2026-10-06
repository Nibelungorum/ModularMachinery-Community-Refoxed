package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.network.chat.Component;

/** Plain recipe metadata retaining exact mana totals and IO direction.
 * @author howxu <dev@howxu.cn>
 */
public record ManaJeiDisplay(long amount, boolean input) {
    public ManaJeiDisplay {
        if (amount <= 0L) throw new IllegalArgumentException("Mana amount must be positive");
    }

    public Component label() {
        return Component.translatable(input ? "jei.mmcr.machine_recipe.mana_input"
                : "jei.mmcr.machine_recipe.mana_output", ReadableNumber.format(amount));
    }
}
