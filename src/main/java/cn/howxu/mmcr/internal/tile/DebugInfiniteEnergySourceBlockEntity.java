package cn.howxu.mmcr.internal.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 调试用无限能量源 BE。任何方向的 {@link net.neoforged.neoforge.transfer.energy.EnergyHandler}
 * 抽取/输入都按请求量全量成功,容量视为无限(实际返回 {@link Long#MAX_VALUE})。
 * 该 BE 不保存数据,无需序列化。
 *
 * @author howxu <dev@howxu.cn>
 */
public class DebugInfiniteEnergySourceBlockEntity extends DebugInfiniteSourceBlockEntity {

    public DebugInfiniteEnergySourceBlockEntity(BlockPos pos, BlockState state) {
        super("debug_infinite_energy_source", pos, state);
    }
}
