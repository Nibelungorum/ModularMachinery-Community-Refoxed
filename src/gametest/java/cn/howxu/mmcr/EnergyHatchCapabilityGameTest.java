package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(MMCR.MODID)
public class EnergyHatchCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
    public void energyHatchStoresFE(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("io_port_energy_basic").get().defaultBlockState());
        var be = helper.getBlockEntity(pos, EnergyHatchBlockEntity.class);

        IEnergyStorage h = be.getEnergyStorage(null);
        h.receiveEnergy(500, false);

        helper.assertTrue(h.getEnergyStored() == 500, "Stored 500 FE");
        helper.succeed();
    }
}
