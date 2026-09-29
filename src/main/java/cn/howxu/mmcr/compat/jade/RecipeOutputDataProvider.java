package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Pushes the controller's recipe outputs into Jade server data.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeOutputDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return RecipeOutputComponentProvider.UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getTarget() instanceof MachineControllerBlockEntity controller)) return;
        RecipeOutputCodec.write(data, controller.recipeOutputs());
    }
}
