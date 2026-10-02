package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;
import java.util.function.Supplier;

/** @author howxu <dev@howxu.cn> */
public enum StressInterfaceKind implements IOPortKind {
    INPUT("create_stress_input_interface", IOType.INPUT, StressInputBlockEntity::new),
    OUTPUT("create_stress_output_interface", IOType.OUTPUT, StressOutputBlockEntity::new);

    public static final CapabilityType TYPE = new CapabilityType(ResourceLocation.parse("create:stress"));
    private final String id;
    private final IOType ioType;
    private final BlockEntityType.BlockEntitySupplier<? extends BlockEntity> factory;

    StressInterfaceKind(String id, IOType ioType, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> factory) {
        this.id = id;
        this.ioType = ioType;
        this.factory = factory;
    }

    @Override public String id() { return id; }
    @Override public IOType ioType() { return ioType; }
    @Override public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() { return factory; }

    @Override
    public Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        return new StressInterfaceBlock(this, type, properties);
    }

    @Override
    public PortDefinition definition() {
        return PortDefinition.of(MMCR.id(id), List.of(CapabilityBinding.internalOnly(TYPE,
                CapabilityDirections.of(ioType), context -> {
                    return context.host().capabilitySnapshot().capabilities().getFirst();
                }, (binding, tier) -> true)));
    }

    @Override
    public List<PortFamilyDescriptor> families() {
        return List.of(new PortFamilyDescriptor(TYPE.id(), ioType, 0,
                List.of("create_stress_" + ioType.getSerializedName() + "_interface")));
    }

    @Override public List<String> modDependencies() { return List.of("create"); }
}
