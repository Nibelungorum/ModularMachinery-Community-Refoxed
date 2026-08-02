package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;

import static net.minecraft.gametest.framework.GameTestAssert.assertTrue;

@GameTestHolder(MMCR.MODID)
public class FluidHatchCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
    public void fluidHatchStoresWater(net.minecraft.world.level.LevelAccessor level) {
        BlockPos pos = new BlockPos(0, 1, 0);
        level.setBlock(pos, MMCRRegistries.FLUID_HATCH_BLOCK.get().defaultBlockState(), 3);
        var be = (FluidHatchBlockEntity) level.getBlockEntity(pos);

        IFluidHandler handler = be.getFluidHandler(null);
        handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        FluidStack stored = handler.getFluidInTank(0);
        assertTrue(stored.getFluid() == Fluids.WATER, "Stored water");
        assertTrue(stored.getAmount() == 1000, "1000 mB");
    }
}
