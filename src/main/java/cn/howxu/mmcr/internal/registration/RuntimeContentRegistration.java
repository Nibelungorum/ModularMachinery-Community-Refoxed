package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;

import java.util.function.Consumer;

/** Owns runtime builtin registration and the runtime recipe hook.
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentRegistration {
    private RuntimeContentRegistration() {
    }

    public static void registerBuiltins() {
        registerBuiltins(RuntimeContentRegistration::ensureStartupContentRegistered,
                MachineRegistry::rebuildCompiledCache);
    }

    static void registerBuiltins(Runnable startup, Runnable rebuildCache) {
        startup.run();
        rebuildCache.run();
    }

    public static void registerRecipes() {
        ensureStartupContentRegistered();
    }

    /** Pure test startup facade; production runtime callers should use {@link #registerBuiltins()}. */
    public static void registerTestStartupContent() {
        StartupContentRegistration.registerPublicForTesting(RuntimeContentRegistration::registerDefinitions,
                RuntimeContentRegistration::registerStructures, RuntimeContentRegistration::registerRecipesSource);
    }

    /** Pure test startup facade with explicit declaration sources. */
    public static void registerTestStartupContent(
            Consumer<MachineDefinitionRegistration> definitionsSource,
            Consumer<StructureRegistration> structuresSource,
            Consumer<MachineRecipeRegistration> recipesSource) {
        StartupContentRegistration.registerForTesting(definitionsSource, structuresSource, recipesSource);
    }

    private static void ensureStartupContentRegistered() {
        if (!ContentRegistrationCoordinator.isCommitted()
                && "NOT_STARTED".equals(StartupContentRegistration.startupPhaseForTesting())) {
            StartupContentRegistration.registerProduction();
        }
    }

    private static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        GameTestRegistration.registerStartupSources(event, null, null);
    }

    private static void registerStructures(RegisterMachineStructuresEvent event) {
        GameTestRegistration.registerStartupSources(null, event, null);
    }

    private static void registerRecipesSource(RegisterMachineRecipesEvent event) {
        GameTestRegistration.registerStartupSources(null, null, event);
    }
}
