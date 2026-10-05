package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Pushes the controller's recipe outputs into Jade server data.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeOutputDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = MMCR.id("recipe_output");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getTarget() instanceof MachineControllerBlockEntity controller)) return;
        RecipeOutputCodec.write(data, controller.recipeOutputs());
    }
}
