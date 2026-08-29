package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.item.InterfaceTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows the parallelism provided by standalone parallel controllers in Jade.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ParallelControllerComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = MMCR.id("parallel_controller");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlock() instanceof ParallelControllerBlock controller)) return;
        for (Component line : InterfaceTooltips.tooltipLines(controller)) {
            tooltip.add(line);
        }
    }
}
