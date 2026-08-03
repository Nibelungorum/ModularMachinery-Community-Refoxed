package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(MMCR.MODID)
public class FluidHatchCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
    public void fluidHatchStoresWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("io_port_fluid_basic").get().defaultBlockState());
        var be = helper.getBlockEntity(pos, FluidHatchBlockEntity.class);

        IFluidHandler handler = be.getFluidHandler(null);
        handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        FluidStack stored = handler.getFluidInTank(0);
        helper.assertTrue(stored.getFluid() == Fluids.WATER, "Stored water");
        helper.assertTrue(stored.getAmount() == 1000, "1000 mB");
        helper.succeed();
    }
}
