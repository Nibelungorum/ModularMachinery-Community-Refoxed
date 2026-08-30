package cn.howxu.mmcr.compat.jade;

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

    @Override
    public void register(IWailaCommonRegistration registration) {
        JadeTextSupport.enable();
        registration.registerBlockDataProvider(MachineControllerDataProvider.INSTANCE, MachineControllerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addConfig(MachineControllerComponentProvider.UID, true);
        registration.registerBlockComponent(MachineControllerComponentProvider.INSTANCE, MachineControllerBlock.class);
        registration.addConfig(ParallelControllerComponentProvider.UID, true);
        registration.registerBlockComponent(ParallelControllerComponentProvider.INSTANCE, ParallelControllerBlock.class);
    }
}
