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

    public static void registerRuntimeBuiltins() {
        RuntimeContentRegistration.registerBuiltins();
    }

    private static void registerRuntimeRecipes() {
        RuntimeContentRegistration.registerRecipes();
    }

    private static void registerPublicApiLifecycle() {
        RuntimeContentRegistration.registerProductionStartupContentForTesting();
    }

    /** Test seam for the production startup path; runtime code should not call this directly. */
    public static void registerProductionApiLifecycleForTesting() {
        registerPublicApiLifecycle();
    }

    public static void completeKubeJSStartup() {
        StartupContentRegistration.completeKubeJSStartup();
    }

    public static void completeKubeJSStartupIfReady() {
        StartupContentRegistration.completeKubeJSStartupIfReady();
    }

    /** Pure test startup seam; unlike the production seam, it does not install production sources. */
    /** Compatibility alias for the pure test startup facade. */
    @Deprecated
    public static void registerPublicApiLifecycleForTesting() {
        StartupContentRegistration.registerForTesting();
    }

    /** Compatibility alias for the pure test startup facade with explicit sources. */
    @Deprecated
    public static void registerPublicApiLifecycleForTesting(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource) {
        RuntimeContentRegistration.registerTestStartupContent(definitionsSource, structuresSource, recipesSource);
    }

    public static String startupPhaseForTesting() {
        return StartupContentRegistration.startupPhaseForTesting();
    }

    static void invokeOptionalSourceForTesting(String className, String methodName, Class<?>[] parameterTypes,
            Object... arguments) {
        GameTestRegistration.invokeOptionalSourceForTesting(className, methodName, parameterTypes, arguments);
    }

}
