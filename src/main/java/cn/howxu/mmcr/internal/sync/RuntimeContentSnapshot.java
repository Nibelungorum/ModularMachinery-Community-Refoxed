package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Immutable boundary object for runtime-reloadable server content.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RuntimeContentSnapshot(
        Map<Identifier, MachineStructureDefinition> structures,
        Map<Identifier, MachineRecipe> recipes,
        Map<Identifier, MachineControllerSpec> controllerSpecs,
        Map<Identifier, MachineAppearanceSpec> appearances,
        long recipeVersion) {

    public RuntimeContentSnapshot {
        structures = Map.copyOf(structures == null ? Map.of() : structures);
        recipes = Map.copyOf(recipes == null ? Map.of() : recipes);
        controllerSpecs = Map.copyOf(controllerSpecs == null ? Map.of() : controllerSpecs);
        appearances = Map.copyOf(appearances == null ? Map.of() : appearances);
    }

    public static RuntimeContentSnapshot empty() {
        return new RuntimeContentSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), 0L);
    }

    public void applyClient() {
        // Task 5 installs the runtime content into client registries.
    }
}
