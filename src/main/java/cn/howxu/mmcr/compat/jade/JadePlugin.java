package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedflux.AppliedFluxBridge;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.runtime.JadeTextSupport;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * @author howxu <dev@howxu.cn>
 */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    private static final String AE2_REGISTRATION =
            "cn.howxu.mmcr.compat.appliedenergistics2.loaded.jade.AE2JadeRegistration";
    private static final String APPLIED_FLUX_REGISTRATION =
            "cn.howxu.mmcr.compat.appliedflux.loaded.jade.AppliedFluxJadeRegistration";

    @Override
    public void register(IWailaCommonRegistration registration) {
        JadeTextSupport.enable();
        registration.registerBlockDataProvider(MachineControllerDataProvider.INSTANCE, MachineControllerBlockEntity.class);
        registration.registerBlockDataProvider(RecipeOutputDataProvider.INSTANCE, MachineControllerBlockEntity.class);
        if (AE2Bridge.get().available()) register(AE2_REGISTRATION, "registerCommon",
                IWailaCommonRegistration.class, registration);
        if (AppliedFluxBridge.get().available()) register(APPLIED_FLUX_REGISTRATION, "registerCommon",
                IWailaCommonRegistration.class, registration);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineControllerComponentProvider.INSTANCE, MachineControllerBlock.class);
        registration.registerBlockComponent(RecipeOutputComponentProvider.INSTANCE, MachineControllerBlock.class);
        registration.registerBlockComponent(ParallelControllerComponentProvider.INSTANCE, ParallelControllerBlock.class);
        if (AE2Bridge.get().available()) register(AE2_REGISTRATION, "registerClient",
                IWailaClientRegistration.class, registration);
    }

    private static <T> void register(String className, String methodName, Class<T> registrationType,
                                     T registration) {
        try {
            Class.forName(className).getMethod(methodName, registrationType).invoke(null, registration);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register optional Jade integration: " + className, exception);
        }
    }
}
