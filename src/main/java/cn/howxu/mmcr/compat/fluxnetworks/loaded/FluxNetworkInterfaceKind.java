package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;
import java.util.function.Supplier;

/** @author howxu <dev@howxu.cn> */
public enum FluxNetworkInterfaceKind implements IOPortKind {
    INPUT(FluxNetworksIds.INPUT, IOType.INPUT, FluxNetworkInputBlockEntity::new),
    OUTPUT(FluxNetworksIds.OUTPUT, IOType.OUTPUT, FluxNetworkOutputBlockEntity::new);

    private static final int TIER = EnergyHatchSize.ULTIMATE.ordinal() + 1;
    private final String id;
    private final IOType io;
    private final BlockEntityType.BlockEntitySupplier<? extends BlockEntity> factory;

    FluxNetworkInterfaceKind(String id, IOType io, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> factory) {
        this.id = id;
        this.io = io;
        this.factory = factory;
    }

    @Override public String id() { return id; }
    @Override public IOType ioType() { return io; }
    @Override public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() { return factory; }

    @Override
    public Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        return new FluxNetworkInterfaceBlock(this, type, properties);
    }

    @Override
    public PortDefinition definition() {
        return PortDefinition.of(MMCR.id(id), CapabilityBinding.internalOnly(BuiltinCapabilityDefinitions.ENERGY_TYPE,
                CapabilityDirections.of(io), context -> context.host().capabilitySnapshot().capabilities().getFirst(),
                (binding, tier) -> tier >= TIER));
    }

    @Override
    public List<PortFamilyDescriptor> families() {
        return List.of(new PortFamilyDescriptor(PortFamilyIds.ENERGY, io, TIER,
                List.of(io == IOType.INPUT ? "energy_input_hatch" : "energy_output_hatch")));
    }

    @Override public List<String> modDependencies() { return List.of(FluxNetworksIds.MOD_ID); }
}
