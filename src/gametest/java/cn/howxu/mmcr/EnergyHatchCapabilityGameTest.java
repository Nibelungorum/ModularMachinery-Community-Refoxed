package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class EnergyHatchCapabilityGameTest {

    public void energyHatchStoresFE(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(0, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState());

        BlockPos inputWorldPos = helper.absolutePos(inputPos);
        BlockPos outputWorldPos = helper.absolutePos(outputPos);
        BlockEntity inputBe = helper.getLevel().getBlockEntity(inputWorldPos);
        BlockEntity outputBe = helper.getLevel().getBlockEntity(outputWorldPos);

        EnergyHatchBlockEntity inputHatch = helper.getBlockEntity(inputPos, EnergyHatchBlockEntity.class);
        EnergyHatchBlockEntity outputHatch = helper.getBlockEntity(outputPos, EnergyHatchBlockEntity.class);

        var inputCapability = inputHatch.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = outputHatch.capabilitySnapshot().capabilities().getFirst();
        helper.assertTrue(inputCapability.directions().supports(IOType.INPUT), "Input capability accepts INPUT");
        helper.assertTrue(outputCapability.directions().supports(IOType.OUTPUT), "Output capability accepts OUTPUT");

        IEnergyStorage input = Capabilities.EnergyStorage.BLOCK.getCapability(
                helper.getLevel(), inputWorldPos, helper.getLevel().getBlockState(inputWorldPos), inputBe, Direction.UP);
        IEnergyStorage output = Capabilities.EnergyStorage.BLOCK.getCapability(
                helper.getLevel(), outputWorldPos, helper.getLevel().getBlockState(outputWorldPos), outputBe, Direction.UP);

        helper.assertTrue(input != null, "Input energy capability is present");
        helper.assertTrue(output != null, "Output energy capability is present");

        int movedToInput = input.receiveEnergy(500, false);
        helper.assertTrue(movedToInput == 500, "Input energy capability receives from transfer energy source");
        helper.assertTrue(inputHatch.getEnergyHandler(null).getAmountAsLong() == 500, "Input hatch stores transferred energy");

        int extracted = input.extractEnergy(200, false);
        helper.assertTrue(extracted == 0, "Input energy capability rejects extracting");

        var outputStorage = outputHatch.energyStorage();
        while (outputStorage.forceInsert(10000, false) > 0) {}

        int rejectedReceive = output.receiveEnergy(200, false);
        int outputExtracted = output.extractEnergy(700, false);
        helper.assertTrue(rejectedReceive == 0, "Output energy capability rejects receiving");
        helper.assertTrue(outputExtracted > 0, "Output energy capability extracts stored energy");

        helper.succeed();
    }
}
