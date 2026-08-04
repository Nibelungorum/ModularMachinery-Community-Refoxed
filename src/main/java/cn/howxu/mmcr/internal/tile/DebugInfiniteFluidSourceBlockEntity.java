package cn.howxu.mmcr.internal.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Map;

/**
 * 调试用无限流体源 BE(支持水/岩浆)。任何方向的
 * {@link net.neoforged.neoforge.transfer.ResourceHandler}{@code <FluidResource>}
 * 抽取/输入都按请求量全量成功,所含流体固定为构造时指定的 {@link Fluid}。
 *
 * @author howxu <dev@howxu.cn>
 */
public class DebugInfiniteFluidSourceBlockEntity extends DebugInfiniteSourceBlockEntity {

    private static final Map<Fluid, String> TYPE_BY_FLUID = Map.of(
            Fluids.WATER, "debug_infinite_water_source",
            Fluids.LAVA,  "debug_infinite_lava_source"
    );

    private final Fluid fluid;

    public DebugInfiniteFluidSourceBlockEntity(BlockPos pos, BlockState state, Fluid fluid) {
        super(TYPE_BY_FLUID.get(fluid), pos, state);
        this.fluid = fluid;
    }

    public Fluid getFluid() {
        return fluid;
    }
}
