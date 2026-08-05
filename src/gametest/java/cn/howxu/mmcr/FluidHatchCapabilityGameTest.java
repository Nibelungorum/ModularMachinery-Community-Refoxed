package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

@GameTestHolder(MMCR.MODID)
public class FluidHatchCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
    public void fluidHatchStoresWater(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(0, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());

        BlockPos inputWorldPos = helper.absolutePos(inputPos);
        BlockPos outputWorldPos = helper.absolutePos(outputPos);
        BlockEntity inputBe = helper.getLevel().getBlockEntity(inputWorldPos);
        BlockEntity outputBe = helper.getLevel().getBlockEntity(outputWorldPos);

        FluidHatchBlockEntity inputHatch = helper.getBlockEntity(inputPos, FluidHatchBlockEntity.class);
        FluidHatchBlockEntity outputHatch = helper.getBlockEntity(outputPos, FluidHatchBlockEntity.class);

        helper.assertTrue(inputHatch.ioType() == IOType.INPUT, "Input hatch is INPUT");
        helper.assertTrue(outputHatch.ioType() == IOType.OUTPUT, "Output hatch is OUTPUT");

        ResourceHandler<FluidResource> input = ModCapabilities.FLUID_BLOCK.getCapability(
                helper.getLevel(), inputWorldPos, helper.getLevel().getBlockState(inputWorldPos), inputBe, Direction.UP);
        ResourceHandler<FluidResource> output = ModCapabilities.FLUID_BLOCK.getCapability(
                helper.getLevel(), outputWorldPos, helper.getLevel().getBlockState(outputWorldPos), outputBe, Direction.UP);

        helper.assertTrue(input != null, "Input fluid capability is present");
        helper.assertTrue(output != null, "Output fluid capability is present");

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = input.insert(0, FluidResource.of(Fluids.WATER), 1000, tx);
            int extracted = input.extract(0, FluidResource.of(Fluids.WATER), 500, tx);
            helper.assertTrue(inserted == 1000, "Input fluid capability fills");
            helper.assertTrue(extracted == 500, "Input fluid capability drains");
            tx.commit();
        }

        outputHatch.getFluidHandler(null).fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = output.insert(0, FluidResource.of(Fluids.WATER), 500, tx);
            int extracted = output.extract(0, FluidResource.of(Fluids.WATER), 1000, tx);
            helper.assertTrue(inserted == 0, "Output fluid capability rejects fill");
            helper.assertTrue(extracted == 1000, "Output fluid capability drains");
            tx.commit();
        }

        helper.succeed();
    }
}
