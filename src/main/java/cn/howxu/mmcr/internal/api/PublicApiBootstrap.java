package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRoleValidator;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.ApiRuntime;
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
        if (state == State.BEFORE_BEGIN) {
            ApiRuntime.install(new ApiRuntime.Hook() {
                @Override
                public void registerMachine(MachineDefinition definition) {
                    PublicApiBootstrap.registerMachine(definition);
                }

                @Override
                public void registerRecipe(MachineRecipeDefinition definition) {
                    PublicApiBootstrap.registerRecipe(definition);
                }

                @Override
                public boolean isRegistrationOpen() {
                    return PublicApiBootstrap.isRegistrationOpen();
                }
            });
            state = State.OPEN;
        }
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
        requireRecipeRegistrationOpen(definition.id());
        if (RECIPES.containsKey(definition.id()) || RecipeRegistry.containsStatic(definition.id())) {
            throw duplicate(definition.id(), "recipe");
        }
        RECIPES.put(definition.id(), definition);
    }

    public static synchronized void freezeAndInstallMachines() {
        if (state == State.MACHINES_FROZEN || state == State.FROZEN) return;
        if (state == State.BEFORE_BEGIN) {
            throw new ApiRegistrationException("Public API machine freeze rejected: lifecycle is before begin");
        }
        Map<Identifier, cn.howxu.mmcr.api.machine.MachineRegistration> machines = new LinkedHashMap<>();
        for (MachineDefinition machine : MACHINES.values()) {
            machines.put(machine.id(), PublicMachineAdapter.toStartupRegistration(machine));
        }
        Map<Identifier, cn.howxu.mmcr.api.machine.MachineRegistration> allMachines = new LinkedHashMap<>();
        for (var machine : MachineDefinitions.allRegistrations()) {
            allMachines.put(machine.id(), machine);
        }
        allMachines.putAll(machines);
        MachineRoleValidator.validate(allMachines.values(), allMachines::get);
        for (Identifier id : machines.keySet()) {
            if (MachineDefinitions.containsStatic(id)) throw duplicate(id, "machine");
        }
        for (var machine : machines.values()) {
            PublicRegistryBridge.registerMachine(machine);
        }
        MachineDefinitions.freezeRegistryPhase();
        state = State.MACHINES_FROZEN;
    }

    public static synchronized void installRecipes() {
        if (state == State.FROZEN) return;
        if (state == State.BEFORE_BEGIN || state == State.OPEN) {
            throw new ApiRegistrationException("Public API recipe installation rejected: machines must be installed first");
        }
        Map<Identifier, cn.howxu.mmcr.api.recipe.MachineRecipe> recipes = new LinkedHashMap<>();
        for (MachineRecipeDefinition recipe : RECIPES.values()) {
            if (!MACHINES.containsKey(recipe.machineId()) && !MachineDefinitions.containsStatic(recipe.machineId())) {
                throw new ApiRegistrationException("Recipe " + recipe.id()
                        + " refers to unknown machine " + recipe.machineId() + " during installation");
            }
            recipes.put(recipe.id(), PublicRecipeAdapter.toRecipe(recipe));
        }
        for (Identifier id : recipes.keySet()) {
            if (RecipeRegistry.containsStatic(id)) throw duplicate(id, "recipe");
        }
        for (var recipe : recipes.values()) {
            PublicRegistryBridge.registerRecipe(recipe);
        }
        state = State.FROZEN;
    }

    /** Test-only reset hook; not part of the public API surface. */
    public static synchronized void clearForTesting() {
        MACHINES.clear();
        RECIPES.clear();
        state = State.BEFORE_BEGIN;
        ApiRuntime.uninstall();
    }

    private static void requireOpen(Identifier id) {
        if (state != State.OPEN) {
            throw new ApiRegistrationException("Registration of " + id + " rejected: lifecycle is "
                    + state.name().toLowerCase().replace('_', ' '));
        }
    }

    private static void requireRecipeRegistrationOpen(Identifier id) {
        if (state != State.OPEN && state != State.MACHINES_FROZEN) {
            throw new ApiRegistrationException("Registration of " + id + " rejected: lifecycle is "
                    + state.name().toLowerCase().replace('_', ' '));
        }
    }

    private static ApiRegistrationException duplicate(Identifier id, String kind) {
        return new ApiRegistrationException("Duplicate " + kind + " ID " + id
                + " during open registration phase");
    }

    private enum State {
        BEFORE_BEGIN, OPEN, MACHINES_FROZEN, FROZEN
    }
}
