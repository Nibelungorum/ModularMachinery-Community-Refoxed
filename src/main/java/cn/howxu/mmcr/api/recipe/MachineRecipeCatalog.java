package cn.howxu.mmcr.api.recipe;

import java.util.List;

/** Immutable published recipe data for one machine.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeCatalog(long version,
                                   List<MachineRecipe> recipes,
                                   List<MachineRecipe> orderedRecipes,
                                   RecipeCandidateIndex inputIndex) {
    public MachineRecipeCatalog {
        recipes = List.copyOf(recipes == null ? List.of() : recipes);
        orderedRecipes = List.copyOf(orderedRecipes == null ? List.of() : orderedRecipes);
        inputIndex = inputIndex == null ? RecipeCandidateIndex.empty() : inputIndex;
    }
}
