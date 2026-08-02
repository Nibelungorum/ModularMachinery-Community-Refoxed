package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.block.EnergyHatchBlock;
import cn.howxu.mmcr.registry.MMCRRegistries;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class EnergyHatchBlockEntity extends BlockEntity {

    private final EnergyStorage storage = new EnergyStorage(100000, 1000, 1000);

    public EnergyHatchBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRRegistries.ENERGY_HATCH_BE.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage(Direction side) { return storage; }

    public IOType ioType() { return getBlockState().getValue(EnergyHatchBlock.IO_TYPE); }
}