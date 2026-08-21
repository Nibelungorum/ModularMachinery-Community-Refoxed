package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.registration.RuntimeContentRegistration;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.registration.GameTestRegistration;
import cn.howxu.mmcr.internal.registration.ModEventRegistration;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMCR.MODID)
public class MMCR {
    public static final String MODID = "mmcr";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public MMCR(IEventBus modBus, ModContainer modContainer) {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();
        MachineDefinitions.bootstrapBuiltins();
        StartupContentRegistration.registerProductionForModStartup();
        ModEventRegistration.register(modBus, modContainer);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /**
     * Compatibility entry point for the runtime builtin refresh path; event wiring lives in
     * {@link ModEventRegistration}.
     */
    public static void registerRuntimeBuiltins() {
        RuntimeContentRegistration.registerBuiltins();
    }

    /** Compatibility/test facade for the production public API startup lifecycle. */
    private static void registerPublicApiLifecycle() {
        RuntimeContentRegistration.registerProductionStartupContentForTesting();
    }

    /** Test seam for the production startup path; runtime code should not call this directly. */
    public static void registerProductionApiLifecycleForTesting() {
        registerPublicApiLifecycle();
    }

    /** Compatibility seam called by KubeJS after its startup declarations are ready. */
    public static void completeKubeJSStartup() {
        StartupContentRegistration.completeKubeJSStartup();
    }

    /** Compatibility/test seam that conditionally completes delayed KubeJS startup. */
    public static void completeKubeJSStartupIfReady() {
        StartupContentRegistration.completeKubeJSStartupIfReady();
    }

    /** Compatibility alias for the pure test startup facade; it is not event wiring. */
    @Deprecated
    public static void registerPublicApiLifecycleForTesting() {
        StartupContentRegistration.registerForTesting();
    }

    /** Compatibility alias for the pure test startup facade with explicit sources, not event wiring. */
    @Deprecated
    public static void registerPublicApiLifecycleForTesting(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource) {
        RuntimeContentRegistration.registerTestStartupContent(definitionsSource, structuresSource, recipesSource);
    }

    /** Test-only view of startup lifecycle state. */
    public static String startupPhaseForTesting() {
        return StartupContentRegistration.startupPhaseForTesting();
    }

    /** Test seam for invoking an optional GameTest source without making it a production dependency. */
    static void invokeOptionalSourceForTesting(String className, String methodName, Class<?>[] parameterTypes,
            Object... arguments) {
        GameTestRegistration.invokeOptionalSourceForTesting(className, methodName, parameterTypes, arguments);
    }

}
