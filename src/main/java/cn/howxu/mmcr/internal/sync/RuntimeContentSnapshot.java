package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.CraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
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
        long contentVersion) {

    public RuntimeContentSnapshot {
        if (contentVersion < 0) throw new IllegalArgumentException("contentVersion must not be negative");
        structures = Map.copyOf(structures == null ? Map.of() : structures);
        recipes = Map.copyOf(recipes == null ? Map.of() : recipes);
        controllerSpecs = Map.copyOf(controllerSpecs == null ? Map.of() : controllerSpecs);
        appearances = Map.copyOf(appearances == null ? Map.of() : appearances);
    }

    public static RuntimeContentSnapshot empty() {
        return new RuntimeContentSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), 0L);
    }

    public boolean applyClient() {
        synchronized (ClientRuntimeSnapshotBridge.class) {
            if (!ClientRuntimeSnapshotBridge.canApply(contentVersion)) return false;
            validateForClient();
            ClientRuntimeSnapshotBridge.validate(this);
            if (!ClientRuntimeSnapshotBridge.isIntegratedServer()) {
                MachineStructureRegistry.replaceClientSnapshot(structures);
                RecipeRegistry.replaceClientSnapshot(recipes);
            }
            CraftingContextPool.onGlobalReload();
            ClientRuntimeSnapshotBridge.apply(this);
            ClientRuntimeSnapshotBridge.markApplied(contentVersion);
            return true;
        }
    }

    private void validateForClient() {
        MachineStructureRegistry.validateClientSnapshot(structures);
        RecipeRegistry.validateClientSnapshot(recipes);
        controllerSpecs.forEach((id, spec) -> {
            if (id == null || spec == null || spec.id() == null
                    || !MachineControllerSpec.defaultsFor(id).id().equals(spec.id())) {
                throw new IllegalArgumentException("Controller spec key does not match spec id: " + id);
            }
        });
        appearances.forEach((id, appearance) -> {
            if (id == null || appearance == null) {
                throw new IllegalArgumentException("Invalid machine appearance entry: " + id);
            }
        });
    }
}
