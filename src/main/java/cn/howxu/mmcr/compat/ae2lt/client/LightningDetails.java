package cn.howxu.mmcr.compat.ae2lt.client;

import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.RecipeDetails;
import net.minecraft.network.chat.Component;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import java.util.List;

/** ae2lt metadata contribution to the JEI details area.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningDetails implements RecipeDetails {
    @Override public boolean handles(MachineRequirement requirement) { return requirement instanceof LightningRequirement; }
    @Override public int order() { return 40; }
    @Override public void append(MachineRecipeDisplay display, List<Row> rows) {
        for (var value : LightningJeiDisplay.requirements(display)) {
            var tooltip = Component.translatable("jei.mmcr.machine_recipe.output_chance", Math.round(value.chance() * 100F) + "%");
            rows.add(new Row(LightningJeiDisplay.label(value), null, value.io().isInput() ? List.of() : List.of(tooltip),
                    (graphics, x, y, width) -> LightningJeiDisplay.drawLine(graphics, value, x, y, width)));
        }
    }
    @Override public void index(IRecipeLayoutBuilder builder, MachineRecipeDisplay display) {
        LightningJeiDisplay.addInvisibleIngredients(builder, display);
    }
}
