package cn.howxu.mmcr.internal.runtime;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * Global machine execution modes.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum MachineWorkMode implements TranslatableEnum {
    ASYNC,
    SEMI_SYNC,
    SYNC;

    @Override
    public Component getTranslatedName() {
        return Component.translatable("mmcr.configuration.server.machine.work_mode." + name().toLowerCase());
    }
}
