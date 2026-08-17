package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects server-script content until it can replace both dynamic snapshots together.
 *
 * @author howxu <dev@howxu.cn>
 */
final class KubeJSContentReloadTransaction {
    private static final ThreadLocal<KubeJSContentReloadTransaction> ACTIVE = new ThreadLocal<>();

    private final Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
    private final Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();

    static KubeJSContentReloadTransaction active() {
        return ACTIVE.get();
    }

    static void activate(KubeJSContentReloadTransaction transaction) {
        ACTIVE.set(transaction);
    }

    static void deactivate() {
        ACTIVE.remove();
    }

    void registerStructure(MachineStructureDefinition structure) {
        Identifier id = structure.machineId();
        if (structures.putIfAbsent(id, structure) != null) {
            throw new IllegalStateException("Dynamic structure already registered: " + id);
        }
    }

    void registerRecipe(MachineRecipe recipe) {
        Identifier id = recipe.id();
        if (recipes.putIfAbsent(id, recipe) != null) {
            throw new IllegalStateException("Dynamic recipe already registered: " + id);
        }
    }

    void commit() {
        validate();
        MachineStructureRegistry.replaceDynamic(structures);
        RecipeRegistry.replaceDynamic(recipes);
    }

    private void validate() {
        MachineStructureRegistry.validateDynamicRoles(structures);
        for (MachineRecipe recipe : recipes.values()) {
            if (RecipeRegistry.containsStatic(recipe.id())) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + recipe.id());
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
