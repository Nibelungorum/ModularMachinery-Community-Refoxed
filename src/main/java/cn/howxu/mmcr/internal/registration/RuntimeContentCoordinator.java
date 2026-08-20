package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRoleValidator;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Commits runtime content layers as validated reload transactions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentCoordinator {
    private RuntimeContentCoordinator() {
    }

    public static DynamicContentReloadService.ReloadResult commitDynamic(
            Map<Identifier, MachineStructureDefinition> structures,
            Map<Identifier, MachineRecipe> recipes) {
        Map<Identifier, MachineStructureDefinition> oldStructures = MachineStructureRegistry.dynamicSnapshot();
        Map<Identifier, MachineRecipe> oldRecipes = RecipeRegistry.dynamicSnapshot();
        Map<Identifier, MachineStructureDefinition> structureReplacement = Map.copyOf(new LinkedHashMap<>(structures));
        Map<Identifier, MachineRecipe> recipeReplacement = Map.copyOf(new LinkedHashMap<>(recipes));

        validate(structureReplacement, recipeReplacement);
        MachineStructureRegistry.replaceDynamic(structureReplacement);
        RecipeRegistry.replaceDynamic(recipeReplacement);
        RecipeCraftingContextPool.onGlobalReload();
        return DynamicContentReloadService.ReloadResult.fromSnapshots(
                oldStructures, structureReplacement, oldRecipes, recipeReplacement);
    }

    public static void replaceDataPackRecipes(Map<Identifier, MachineRecipe> recipes) {
        RecipeRegistry.replaceDataPack(recipes);
    }

    private static void validate(Map<Identifier, MachineStructureDefinition> structures,
                                 Map<Identifier, MachineRecipe> recipes) {
        Map<Identifier, MachineRegistration> registrations = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineStructureDefinition> entry : structures.entrySet()) {
            Identifier id = entry.getKey();
            MachineStructureDefinition structure = entry.getValue();
            MachineRegistration registration = MachineDefinitions.getRegistration(id);
            if (registration == null) {
                throw new IllegalStateException("No startup machine registration for structure: " + id);
            }
            if (!id.equals(structure.machineId())) {
                throw new IllegalStateException("Structure key does not match machine id: " + id + " != " + structure.machineId());
            }
            registrations.put(id, registration.withPattern(structure.pattern()));
        }
        MachineRoleValidator.validate(registrations.values(), null);
        for (MachineRecipe recipe : recipes.values()) {
            if (RecipeRegistry.containsStatic(recipe.id())) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + recipe.id());
            }
            if (RecipeRegistry.dataPackSnapshot().containsKey(recipe.id())) {
                throw new IllegalStateException("Dynamic recipe conflicts with data-pack recipe: " + recipe.id());
            }
            if (MachineDefinitions.getRegistration(recipe.machineId()) == null) {
                throw new IllegalStateException("No startup machine registration for recipe: " + recipe.machineId());
            }
            if (!structures.containsKey(recipe.machineId()) && !MachineRegistry.containsStatic(recipe.machineId())) {
                throw new IllegalStateException("Machine not found for dynamic recipe: " + recipe.machineId());
            }
        }
    }
}
