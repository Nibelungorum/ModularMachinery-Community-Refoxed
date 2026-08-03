package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.MMCRBlockEntities;
import cn.howxu.mmcr.registry.MMCRPortKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class EnergyHatchBlockEntity extends IOPortBlockEntity {

    private final EnergyStorage storage = new EnergyStorage(100000, 100000, 100000);

    public EnergyHatchBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRBlockEntities.BES.get("io_port_energy_basic").get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage(Direction side) { return storage; }

    public EnergyStorage getMutableEnergyStorage(Direction side) { return storage; }

    @Override
    public IOPortKind kind() { return MMCRPortKinds.ENERGY; }
}
