package cn.howxu.mmcr.compat.pneumaticcraft.client;

import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.RecipeDetails;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import java.util.List;

/** pneumaticcraft metadata contribution to the JEI details area.
 * @author howxu <dev@howxu.cn>
 */
public final class AirDetails implements RecipeDetails {
    @Override public boolean handles(MachineRequirement requirement) { return requirement instanceof AirRequirement; }
    @Override public int order() { return 20; }
    @Override public void append(MachineRecipeDisplay display, List<Row> rows) {
        var icon = PneumaticIds.ADVANCED_PRESSURE_TUBE;
        display.airInputs().forEach(value -> rows.add(new Row(value.label(true), icon, value.tooltip(true))));
        display.airOutputs().forEach(value -> rows.add(new Row(value.label(false), icon, value.tooltip(false))));
    }
}
