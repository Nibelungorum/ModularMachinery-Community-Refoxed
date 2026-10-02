package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * Declares a source interface independently of the optional native implementation.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SourcePortKind(String id, IOType ioType) implements IOPortKind {
    @Override
    public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
        return (pos, state) -> ArsNouveauBridge.get().createPort(pos, state, this);
    }

    @Override
    public List<PortFamilyDescriptor> families() {
        return List.of(new PortFamilyDescriptor(ArsSourceIds.SOURCE, ioType, 0,
                List.of(ioType == IOType.INPUT ? "source_input_interface" : "source_output_interface")));
    }

    @Override
    public PortDefinition definition() {
        return PortDefinition.of(MMCR.id(id), new CapabilityBinding(ArsSourceIds.TYPE,
                CapabilityDirections.of(ioType), context -> ArsNouveauBridge.get().createCapability(context),
                (binding, tier) -> tier >= 0));
    }

    @Override
    public List<String> modDependencies() {
        return List.of("ars_nouveau");
    }
}
