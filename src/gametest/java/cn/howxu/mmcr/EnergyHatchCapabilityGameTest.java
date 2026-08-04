package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

@GameTestHolder(MMCR.MODID)
public class EnergyHatchCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
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

        helper.assertTrue(inputHatch.ioType() == IOType.INPUT, "Input hatch is INPUT");
        helper.assertTrue(outputHatch.ioType() == IOType.OUTPUT, "Output hatch is OUTPUT");

        EnergyHandler input = ModCapabilities.ENERGY_BLOCK.getCapability(
                helper.getLevel(), inputWorldPos, helper.getLevel().getBlockState(inputWorldPos), inputBe, Direction.UP);
        EnergyHandler output = ModCapabilities.ENERGY_BLOCK.getCapability(
                helper.getLevel(), outputWorldPos, helper.getLevel().getBlockState(outputWorldPos), outputBe, Direction.UP);

        helper.assertTrue(input != null, "Input energy capability is present");
        helper.assertTrue(output != null, "Output energy capability is present");

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = input.insert(500, tx);
            int extracted = input.extract(200, tx);
            helper.assertTrue(inserted == 500, "Input energy capability receives");
            helper.assertTrue(extracted == 0, "Input energy capability rejects extraction");
            tx.commit();
        }

        outputHatch.getEnergyStorage(null).receiveEnergy(10000, false);

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = output.insert(200, tx);
            int extracted = output.extract(700, tx);
            helper.assertTrue(inserted == 0, "Output energy capability rejects receiving");
            helper.assertTrue(extracted == 700, "Output energy capability extracts");
            tx.commit();
        }

        helper.succeed();
    }
}
