package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.internal.api.PublicBuiltinRuntime;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;

import java.util.function.Consumer;

/** Owns runtime builtin registration and the runtime recipe hook.
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentRegistration {
    private RuntimeContentRegistration() {
    }

    public static void registerBuiltins() {
        ensureStartupContentRegistered();
        DynamicContentReloadService.reload(PublicBuiltinRuntime::registerStructures);
        MachineRegistry.rebuildCompiledCache();
    }

    public static void registerRecipes() {
        ensureStartupContentRegistered();
    }

    public static void registerPublicApiLifecycleForTesting() {
        StartupContentRegistration.registerForTesting(RuntimeContentRegistration::registerDefinitions,
                RuntimeContentRegistration::registerStructures, RuntimeContentRegistration::registerRecipesSource);
    }

    /** Compatibility alias for the production startup test seam. */
    public static void registerProductionStartupContentForTesting() {
        registerPublicApiLifecycleForTesting();
    }

    public static void registerPublicApiLifecycleForTesting(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource) {
        StartupContentRegistration.registerForTesting(definitionsSource, structuresSource, recipesSource);
    }

    private static void ensureStartupContentRegistered() {
        if (!ContentRegistrationCoordinator.isCommitted()) {
            registerPublicApiLifecycleForTesting();
        }
    }

    private static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        GameTestRegistration.registerStartupSources(event, null, null);
    }

    private static void registerStructures(MMCRMachineStructuresEvent event) {
        GameTestRegistration.registerStartupSources(null, event, null);
    }

    private static void registerRecipesSource(MMCRMachineRecipesEvent event) {
        GameTestRegistration.registerStartupSources(null, null, event);
    }
}
