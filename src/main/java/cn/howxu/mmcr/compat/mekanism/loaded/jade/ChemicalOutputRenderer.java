package cn.howxu.mmcr.compat.mekanism.loaded.jade;

import cn.howxu.mmcr.compat.jade.JadeChemicalElement;
import cn.howxu.mmcr.compat.jade.RecipeOutputComponentProvider;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.util.ReadableNumber;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceKey;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.overlay.DisplayHelper;

/** Installed by Jade only on a client with Mekanism.
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalOutputRenderer {
    private ChemicalOutputRenderer() {}

    private static final RecipeOutputComponentProvider.Renderer<LoadedChemicalOutput> RENDERER = (output, amount) -> {
        var holder = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, output.id()));
        return holder == null || holder.isEmpty() ? null : tooltip -> render(tooltip, holder.get().value(), amount);
    };

    public static void register() {
        RecipeOutputComponentProvider.register(LoadedChemicalOutput.TYPE, RENDERER);
    }

    private static void render(ITooltip tooltip, Chemical value, long amount) {
        String formattedAmount = amount <= 10L
                ? ReadableNumber.formatForSlot(amount, 0, "mB")
                : ReadableNumber.formatForSlot(amount, 3, "B");
        Component name = ComponentUtils.wrapInSquareBrackets(value.getTextComponent())
                .withStyle(ChatFormatting.WHITE);
        var icon = new JadeChemicalElement(value.getIcon(), value.getTint(), 10,8);
        try {
            int lineHeight = DisplayHelper.font().lineHeight;
            icon.setFreeSpace(lineHeight + 1, lineHeight - 1);
            tooltip.add(icon.offset(0, -1));
        } catch (RuntimeException ignored) {
            // Unit tests have no client font, but Jade can still lay out the default-sized icon.
        }
        tooltip.append(JadeUI.spacer(2, 0));
        tooltip.append(Component.translatable("jade.mmcr.machine_controller.recipe_output.fluid",
                formattedAmount, name));
    }
}
