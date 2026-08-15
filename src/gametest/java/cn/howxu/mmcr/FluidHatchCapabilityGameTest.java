package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.block.FluidHatchBucketInteraction;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class FluidHatchCapabilityGameTest {

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

        try (Transaction tx = Transaction.openRoot()) {
            try {
                input.extract(0, FluidResource.of(Fluids.WATER), -1, tx);
                helper.fail("Fluid capability rejects negative extraction amount");
            } catch (IllegalArgumentException ignored) {
            }
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

    public void bucketInteractionRespectsHatchDirection(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(0, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());

        FluidHatchBlockEntity input = helper.getBlockEntity(inputPos, FluidHatchBlockEntity.class);
        FluidHatchBlockEntity output = helper.getBlockEntity(outputPos, FluidHatchBlockEntity.class);
        output.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 1_000));

        FluidHatchBucketInteraction.Result filled = FluidHatchBucketInteraction.tryTransfer(
                output.getFluidTank(null), IOType.OUTPUT, new ItemStack(Items.BUCKET));
        helper.assertTrue(filled.successful() && filled.container().is(Items.WATER_BUCKET),
                "Output hatch fills an empty bucket");
        helper.assertTrue(output.getFluidTank(null).isEmpty(), "Output hatch transfers exactly one bucket");

        FluidHatchBucketInteraction.Result emptied = FluidHatchBucketInteraction.tryTransfer(
                input.getFluidTank(null), IOType.INPUT, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(emptied.successful() && emptied.container().is(Items.BUCKET),
                "Input hatch empties a filled bucket");
        helper.assertTrue(FluidStack.isSameFluidSameComponents(input.getFluidTank(null).getFluid(), new FluidStack(Fluids.WATER, 1_000))
                        && input.getFluidTank(null).getFluidAmount() == 1_000,
                "Input hatch receives exactly one bucket");

        input.getFluidTank(null).setFluid(new FluidStack(Fluids.LAVA, 1_000));
        FluidHatchBucketInteraction.Result rejectedInput = FluidHatchBucketInteraction.tryTransfer(
                input.getFluidTank(null), IOType.INPUT, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(!rejectedInput.successful() && rejectedInput.container().is(Items.WATER_BUCKET),
                "Input hatch rejects a different fluid");

        output.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 1_000));
        FluidHatchBucketInteraction.Result rejectedOutput = FluidHatchBucketInteraction.tryTransfer(
                output.getFluidTank(null), IOType.OUTPUT, new ItemStack(Items.LAVA_BUCKET));
        helper.assertTrue(!rejectedOutput.successful() && rejectedOutput.container().is(Items.LAVA_BUCKET),
                "Output hatch does not fill an already-filled bucket");
        helper.succeed();
    }
}
