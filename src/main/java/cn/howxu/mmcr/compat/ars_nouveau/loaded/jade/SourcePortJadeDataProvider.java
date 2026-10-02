package cn.howxu.mmcr.compat.ars_nouveau.loaded.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Synchronizes real source storage contents rather than the external handler projection.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum SourcePortJadeDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = MMCR.id("source_port");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getTarget() instanceof SourcePortBlockEntity port)) return;
        CompoundTag source = new CompoundTag();
        source.putInt("amount", port.storage().amount());
        source.putInt("capacity", port.storage().capacity());
        source.putString("io", port.ioType().getSerializedName());
        data.put("mmcr_source", source);
    }
}
