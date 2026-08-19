package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Coordinates immutable public machine and recipe startup declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicApiBootstrap {
    private static final Map<Identifier, MachineDefinition> MACHINES = new LinkedHashMap<>();
    private static final Map<Identifier, MachineRecipeDefinition> RECIPES = new LinkedHashMap<>();
    private static State state = State.BEFORE_BEGIN;

    private PublicApiBootstrap() {
    }

    public static synchronized void begin() {
        if (state == State.BEFORE_BEGIN) state = State.OPEN;
    }

    public static synchronized boolean isRegistrationOpen() {
        return state == State.OPEN;
    }

    public static synchronized void registerMachine(MachineDefinition definition) {
        requireOpen(definition.id());
        if (MACHINES.containsKey(definition.id()) || MachineDefinitions.containsStatic(definition.id())) {
            throw duplicate(definition.id(), "machine");
        }
        MACHINES.put(definition.id(), definition);
    }

    public static synchronized void registerRecipe(MachineRecipeDefinition definition) {
        requireOpen(definition.id());
        if (RECIPES.containsKey(definition.id()) || RecipeRegistry.containsStatic(definition.id())) {
            throw duplicate(definition.id(), "recipe");
        }
        RECIPES.put(definition.id(), definition);
    }

    public static synchronized void freezeAndInstall() {
        if (state == State.FROZEN) return;
        if (state == State.BEFORE_BEGIN) {
            throw new ApiRegistrationException("Public API freeze rejected: lifecycle is before begin");
        }
        MachineDefinitions.bootstrapBuiltins();
        for (MachineRecipeDefinition recipe : RECIPES.values()) {
            if (!MACHINES.containsKey(recipe.machineId()) && !MachineDefinitions.containsStatic(recipe.machineId())) {
                throw new ApiRegistrationException("Recipe " + recipe.id()
                        + " refers to unknown machine " + recipe.machineId() + " during freeze");
            }
        }
        for (MachineDefinition machine : MACHINES.values()) {
            PublicRegistryBridge.registerMachine(PublicMachineAdapter.toRegistration(machine));
        }
        for (MachineRecipeDefinition recipe : RECIPES.values()) {
            PublicRegistryBridge.registerRecipe(PublicRecipeAdapter.toRecipe(recipe));
        }
        MachineDefinitions.freezeRegistryPhase();
        state = State.FROZEN;
    }

    /** Test-only reset hook; not part of the public API surface. */
    public static synchronized void clearForTesting() {
        MACHINES.clear();
        RECIPES.clear();
        state = State.BEFORE_BEGIN;
    }

    private static void requireOpen(Identifier id) {
        if (state != State.OPEN) {
            throw new ApiRegistrationException("Registration of " + id + " rejected: lifecycle is "
                    + state.name().toLowerCase().replace('_', ' '));
        }
    }

    private static ApiRegistrationException duplicate(Identifier id, String kind) {
        return new ApiRegistrationException("Duplicate " + kind + " ID " + id
                + " during open registration phase");
    }

    private enum State {
        BEFORE_BEGIN, OPEN, FROZEN
    }
}
