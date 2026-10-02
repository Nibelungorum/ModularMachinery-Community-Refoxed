package cn.howxu.mmcr.compat.ars_nouveau.loaded.jade;

import cn.howxu.mmcr.compat.ars_nouveau.client.SourcePortJadeComponentProvider;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortBlockEntity;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;

/**
 * Registers source Jade providers after the optional Ars bridge becomes available.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceJadeRegistration {
    private SourceJadeRegistration() {
    }

    public static void registerCommon(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SourcePortJadeDataProvider.INSTANCE, SourcePortBlockEntity.class);
    }

    public static void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SourcePortJadeComponentProvider.INSTANCE, IOPortBlock.class);
    }
}
