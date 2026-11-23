package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * The material row and the full transfer view of a single JEI structure preview.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
final class StructureStageSlots implements IRecipeSlotsView {
    private final Map<Integer, Stage> stages = new HashMap<>();
    private final IntSupplier stageNumber;

    StructureStageSlots(MachineStructureDisplay display, IRecipeSlotDrawablesView layout, IGuiHelper guiHelper,
            IntSupplier stageNumber) {
        this.stageNumber = stageNumber;
        var materialSlots = List.copyOf(layout.getSlots(RecipeIngredientRole.RENDER_ONLY));
        var outputs = layout.getSlots(RecipeIngredientRole.OUTPUT);
        int start = 0;
        // JEI returns the material slots in the same order they were added in setRecipe.
        for (var stage : display.materialsByStage().entrySet()) {
            var summary = stage.getValue();
            int end = start + summary.entries().size();
            var materials = materialSlots.subList(start, end);
            var all = new ArrayList<IRecipeSlotView>(materials);
            // The default stage already has native input slots for recipe lookup.
            if (stages.isEmpty()) {
                all.addAll(layout.getSlots(RecipeIngredientRole.INPUT));
            } else {
                for (ItemStack stack : summary.transferStacks()) {
                    var input = guiHelper.createRecipeSlotDrawable(RecipeIngredientRole.INPUT,
                            slot -> slot.addItemStack(stack), Set.of(), 0);
                    input.setPosition(-1000, -1000);
                    all.add(input);
                }
            }
            all.addAll(outputs);
            stages.put(stage.getKey(), new Stage(materials, List.copyOf(all)));
            start = end;
        }
    }

    List<IRecipeSlotDrawable> materials() {
        return stages.get(stageNumber.getAsInt()).materials();
    }

    @Override
    public List<IRecipeSlotView> getSlotViews() {
        return stages.get(stageNumber.getAsInt()).all();
    }

    private record Stage(List<IRecipeSlotDrawable> materials, List<IRecipeSlotView> all) { }
}
