package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;

/** Public-safe runtime hook used by the API artifact to reach its implementation.
 * @author howxu <dev@howxu.cn>
 */
public final class ApiRuntime {
    private static Hook hook;

    private ApiRuntime() {
    }

    public static synchronized void install(Hook implementation) {
        hook = implementation;
    }

    public static synchronized void uninstall() {
        hook = null;
    }

    static synchronized void registerMachine(MachineDefinition definition) {
        if (hook == null) {
            throw new ApiRegistrationException("Registration of " + definition.id() + " rejected: lifecycle is before begin");
        }
        hook.registerMachine(definition);
    }

    static synchronized void registerRecipe(MachineRecipeDefinition definition) {
        if (hook == null) {
            throw new ApiRegistrationException("Registration of " + definition.id() + " rejected: lifecycle is before begin");
        }
        hook.registerRecipe(definition);
    }

    static synchronized boolean isRegistrationOpen() {
        return hook != null && hook.isRegistrationOpen();
    }

    /** Implementation boundary installed by the mod during startup. */
    public interface Hook {
        void registerMachine(MachineDefinition definition);

        void registerRecipe(MachineRecipeDefinition definition);

        boolean isRegistrationOpen();
    }
}
