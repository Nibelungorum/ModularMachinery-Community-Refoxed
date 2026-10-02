package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/** @author howxu <dev@howxu.cn> */
public final class StressInterfaceBlock extends ChainDriveBlock implements MachinePort {
    private final StressInterfaceKind kind;
    private final Supplier<? extends BlockEntityType<?>> type;

    public StressInterfaceBlock(StressInterfaceKind kind, Supplier<? extends BlockEntityType<?>> type, Properties properties) {
        super(properties.strength(3.5F).sound(SoundType.METAL));
        this.kind = kind;
        this.type = type;
    }

    @Override public IOPortKind kind() { return kind; }

    @SuppressWarnings("unchecked")
    @Override
    public Class<KineticBlockEntity> getBlockEntityClass() {
        return (Class<KineticBlockEntity>) (Class<?>) (kind == StressInterfaceKind.INPUT
                ? StressInputBlockEntity.class : StressOutputBlockEntity.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends KineticBlockEntity>) type.get();
    }
}
