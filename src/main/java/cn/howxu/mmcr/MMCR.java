package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.registration.GameTestRegistration;
import cn.howxu.mmcr.internal.registration.ModEventRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;

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
        ModEventRegistration.register(modBus, modContainer);
        modBus.addListener((FMLConstructModEvent event) ->
                StartupContentRegistration.registerProductionForModStartup(NeoForge.EVENT_BUS));
        modBus.addListener((FMLCommonSetupEvent event) ->
                StartupContentRegistration.completeProductionForModStartup(NeoForge.EVENT_BUS));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
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
