package cn.howxu.mmcr.compat.pneumaticcraft.loaded.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirPortBlockEntity;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Native PNC pressure presentation with an independent, matching Jade provider identity.
 * @author howxu <dev@howxu.cn>
 */
public enum AirPortJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = MMCR.id("air_port");
    private static final String DATA_KEY = "mmcr_air";

    @Override
    public ResourceLocation getUid() { return UID; }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof AirPortBlockEntity port)) return;
        CompoundTag air = new CompoundTag();
        air.putFloat("pressure", port.airHandler().getPressure());
        air.putFloat("danger", port.airHandler().getDangerPressure());
        data.put(DATA_KEY, air);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(DATA_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag air = accessor.getServerData().getCompound(DATA_KEY);
        tooltip.add(Component.translatable("pneumaticcraft.gui.tooltip.pressureMax",
                PneumaticCraftUtils.roundNumberTo(air.getFloat("pressure"), 2),
                PneumaticCraftUtils.roundNumberTo(air.getFloat("danger"), 1)));
    }
}
