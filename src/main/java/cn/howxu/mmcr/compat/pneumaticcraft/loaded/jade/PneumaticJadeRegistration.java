package cn.howxu.mmcr.compat.pneumaticcraft.loaded.jade;

import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirPortBlockEntity;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirInterfaceBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;

/** Registers paired air providers without reusing PNC's own provider identity.
 * @author howxu <dev@howxu.cn>
 */
public final class PneumaticJadeRegistration {
    private PneumaticJadeRegistration() {
    }

    public static void registerCommon(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AirPortJadeProvider.INSTANCE, AirPortBlockEntity.class);
    }

    public static void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(AirPortJadeProvider.INSTANCE, AirInterfaceBlock.class);
    }
}
