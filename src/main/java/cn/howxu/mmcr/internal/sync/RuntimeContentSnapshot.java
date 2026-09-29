package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.CraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Immutable boundary object for runtime-reloadable server content.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RuntimeContentSnapshot(
        Map<ResourceLocation, MachineStructureDefinition> structures,
        Map<ResourceLocation, MachineRecipe> recipes,
        Map<ResourceLocation, MachineControllerSpec> controllerSpecs,
        Map<ResourceLocation, MachineAppearanceSpec> appearances,
        Map<ResourceLocation, List<ResourceLocation>> machineRecipePools,
        long contentVersion) {

    public RuntimeContentSnapshot {
        if (contentVersion < 0) throw new IllegalArgumentException("contentVersion must not be negative");
        structures = Map.copyOf(structures == null ? Map.of() : structures);
        recipes = Map.copyOf(recipes == null ? Map.of() : recipes);
        controllerSpecs = Map.copyOf(controllerSpecs == null ? Map.of() : controllerSpecs);
        appearances = Map.copyOf(appearances == null ? Map.of() : appearances);
        Map<ResourceLocation, List<ResourceLocation>> recipePoolCopy = new LinkedHashMap<>();
        if (machineRecipePools != null) {
            machineRecipePools.forEach((id, pools) -> recipePoolCopy.put(id,
                    pools == null ? null : List.copyOf(pools)));
        }
        machineRecipePools = Map.copyOf(recipePoolCopy);
    }

    public static RuntimeContentSnapshot empty() {
        return new RuntimeContentSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0L);
    }

    public boolean applyClient() {
        synchronized (ClientRuntimeSnapshotBridge.class) {
            if (!ClientRuntimeSnapshotBridge.canApply(contentVersion)) return false;
            validateForClient();
            ClientRuntimeSnapshotBridge.validate(this);
            if (!ClientRuntimeSnapshotBridge.isIntegratedServer()) {
                MachineStructureRegistry.replaceClientSnapshot(structures);
                MachineRegistry.replaceClientRecipePools(machineRecipePools);
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
        MachineRegistry.validateClientRecipePools(machineRecipePools);
        if (!machineRecipePools.keySet().containsAll(structures.keySet())) {
            throw new IllegalArgumentException("Missing machine recipe pool mapping for synced structure");
        }
        if (recipes.values().stream().anyMatch(recipe -> machineRecipePools.values().stream()
                .noneMatch(pools -> pools.contains(recipe.recipePoolId())))) {
            throw new IllegalArgumentException("Synced recipe pool is not mapped to a machine");
        }
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
