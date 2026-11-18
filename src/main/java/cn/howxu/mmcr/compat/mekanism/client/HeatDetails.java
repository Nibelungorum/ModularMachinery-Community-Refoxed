package cn.howxu.mmcr.compat.mekanism.client;

import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.RecipeDetails;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** mekanism metadata contribution to the JEI details area.
 * @author howxu <dev@howxu.cn>
 */
public final class HeatDetails implements RecipeDetails {
    @Override public boolean handles(MachineRequirement requirement) { return requirement instanceof LoadedHeatRequirement; }
    @Override public int order() { return 30; }
    @Override public void append(MachineRecipeDisplay display, List<Row> rows) {
        var icon = ResourceLocation.parse("mekanism:superheating_element");
        display.minimumTemperature().ifPresent(v -> rows.add(new Row(MachineRecipeDisplay.minimumTemperatureLabel(v), icon, List.of())));
        display.outputHeat().ifPresent(v -> rows.add(new Row(MachineRecipeDisplay.outputHeatLabel(v), icon, List.of())));
    }
}
