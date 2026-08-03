package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;

import static net.minecraft.gametest.framework.GameTestAssert.assertTrue;

@GameTestHolder(MMCR.MODID)
public class EnergyHatchCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
    public void energyHatchStoresFE(net.minecraft.world.level.LevelAccessor level) {
        BlockPos pos = new BlockPos(0, 1, 0);
        level.setBlock(pos, ModBlocks.BLOCKS.get("io_port_energy_basic").get().defaultBlockState(), 3);
        var be = (EnergyHatchBlockEntity) level.getBlockEntity(pos);

        IEnergyStorage h = be.getEnergyStorage(null);
        h.receiveEnergy(500, false);

        assertTrue(h.getEnergyStored() == 500, "Stored 500 FE");
    }
}