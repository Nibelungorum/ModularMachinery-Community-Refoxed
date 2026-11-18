package cn.howxu.mmcr.compat.create.client;

import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.RecipeDetails;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** create metadata contribution to the JEI details area.
 * @author howxu <dev@howxu.cn>
 */
public final class StressDetails implements RecipeDetails {
    @Override public boolean handles(MachineRequirement requirement) { return requirement instanceof StressRequirement; }
    @Override public int order() { return 10; }
    @Override public void append(MachineRecipeDisplay display, List<Row> rows) {
        var icon = ResourceLocation.parse("create:cogwheel");
        display.stressInputs().forEach(value -> rows.add(new Row(value.label(true), icon, value.tooltip(true))));
        display.stressOutputs().forEach(value -> rows.add(new Row(value.label(false), icon, value.tooltip(false))));
    }
}
