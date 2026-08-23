package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;

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
        StartupContentRegistration.registerForTesting(RuntimeContentRegistration::registerDefinitions,
                RuntimeContentRegistration::registerStructures, RuntimeContentRegistration::registerRecipesSource);
    }

    /** @deprecated Use {@link #registerTestStartupContent()} for the pure test startup facade. */
    @Deprecated
    public static void registerPublicApiLifecycleForTesting() {
        registerTestStartupContent();
    }

    /** Compatibility alias for the production startup test seam. */
    public static void registerProductionStartupContentForTesting() {
        registerTestStartupContent();
    }

    /** @deprecated Use {@link #registerTestStartupContent(Consumer, Consumer, Consumer)}. */
    @Deprecated
    public static void registerPublicApiLifecycleForTesting(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource) {
        registerTestStartupContent(definitionsSource, structuresSource, recipesSource);
    }

    /** Pure test startup facade with explicit declaration sources. */
    public static void registerTestStartupContent(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource) {
        StartupContentRegistration.registerForTesting(definitionsSource, structuresSource, recipesSource);
    }

    private static void ensureStartupContentRegistered() {
        if (!ContentRegistrationCoordinator.isCommitted()
                && "NOT_STARTED".equals(StartupContentRegistration.startupPhaseForTesting())) {
            registerTestStartupContent();
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
