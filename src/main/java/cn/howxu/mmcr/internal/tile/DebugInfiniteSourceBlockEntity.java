package cn.howxu.mmcr.internal.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 调试用无限源 BE 基类。构造时通过 {@code typeKey} 在 {@link cn.howxu.mmcr.registry.ModBlockEntities#BES}
 * 中查找自身已注册的 {@link BlockEntityType},并以此完成 {@link BlockEntity} 父类构造。
 * 不持有任何运行时状态(容量无限、无序列化字段)。
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class DebugInfiniteSourceBlockEntity extends BlockEntity {

    protected DebugInfiniteSourceBlockEntity(String typeKey, BlockPos pos, BlockState state) {
        super(lookup(typeKey), pos, state);
    }

    private static BlockEntityType<?> lookup(String typeKey) {
        return cn.howxu.mmcr.registry.ModBlockEntities.BES.get(typeKey).get();
    }
}
