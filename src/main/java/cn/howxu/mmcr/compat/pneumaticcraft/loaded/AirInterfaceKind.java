package cn.howxu.mmcr.compat.pneumaticcraft.loaded;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;
import java.util.function.Supplier;

/** Recipe direction does not restrict native pipe equalization. @author howxu <dev@howxu.cn> */
public enum AirInterfaceKind implements IOPortKind {
    INPUT(PneumaticIds.INPUT, IOType.INPUT),
    OUTPUT(PneumaticIds.OUTPUT, IOType.OUTPUT);

    public static final CapabilityType TYPE = new CapabilityType(PneumaticIds.AIR);
    private final String id;
    private final IOType io;

    AirInterfaceKind(String id, IOType io) {
        this.id = id;
        this.io = io;
    }

    @Override public String id() { return id; }
    @Override public IOType ioType() { return io; }
    @Override public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() {
        return (pos, state) -> new AirPortBlockEntity(pos, state, this);
    }

    @Override
    public Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        return new AirInterfaceBlock(this, type, properties);
    }

    @Override
    public PortDefinition definition() {
        return PortDefinition.of(MMCR.id(id), List.of(CapabilityBinding.internalOnly(TYPE,
                CapabilityDirections.of(io), context -> ((AirPortBlockEntity) context.host()).airCapability(),
                (binding, tier) -> true)));
    }

    @Override public List<PortFamilyDescriptor> families() {
        return List.of(new PortFamilyDescriptor(PneumaticIds.AIR, io, 0, List.of(id)));
    }
    @Override public List<String> modDependencies() { return List.of(PneumaticIds.MOD_ID); }
}
