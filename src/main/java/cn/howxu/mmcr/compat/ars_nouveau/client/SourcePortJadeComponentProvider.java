package cn.howxu.mmcr.compat.ars_nouveau.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

/**
 * Displays only the server-synchronized real source inventory, without loaded Ars classes.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum SourcePortJadeComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = MMCR.id("source_port");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL - 9;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("mmcr_source", Tag.TAG_COMPOUND)) return;
        CompoundTag source = data.getCompound("mmcr_source");
        tooltip.add(Component.translatable("gui.mmcr.source.amount",
                ReadableNumber.format(source.getInt("amount")), ReadableNumber.format(source.getInt("capacity"))));
    }
}
