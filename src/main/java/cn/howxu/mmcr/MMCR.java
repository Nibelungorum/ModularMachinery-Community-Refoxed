package cn.howxu.mmcr;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.compat.create.CreateBridge;
import cn.howxu.mmcr.compat.create.CreateBridgeBootstrap;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.registration.GameTestRegistration;
import cn.howxu.mmcr.internal.registration.ModEventRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMCR.MODID)
public class MMCR {
    public static final String MODID = "mmcr";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public MMCR(IEventBus modBus, ModContainer modContainer) {
        BuiltinFailureReasons.register();
        CreateBridgeBootstrap.bootstrap();
        CreateRecipeTypes.register();
        MekanismBridgeBootstrap.bootstrap();
        MekanismRecipeTypes.register();
        PublicApiBootstrap.begin();
        CreateBridge.get().registerPorts(modBus);
        MachineDefinitions.beginRegistryPhase();
        MachineDefinitions.bootstrapBuiltins();
        MekanismBridge.get().registerTransferPolicies();
        PublicApiBootstrap.freeze();
        ModEventRegistration.register(modBus, modContainer);
        modBus.addListener((FMLConstructModEvent event) ->
                StartupContentRegistration.registerProductionForModStartup(modBus));
        modBus.addListener((FMLCommonSetupEvent event) ->
                StartupContentRegistration.completeProductionForModStartup(modBus));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** Test-only view of startup lifecycle state. */
    public static String startupPhaseForTesting() {
        return StartupContentRegistration.startupPhaseForTesting();
    }

    /** Test seam for invoking an optional GameTest source without making it a production dependency. */
    static void invokeOptionalSourceForTesting(String className, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        GameTestRegistration.invokeOptionalSourceForTesting(className, methodName, parameterTypes, arguments);
    }

}
