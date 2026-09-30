package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * AE2 interface-backed input port kind.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InputInterfaceKind implements InterfaceLogicKind {
    private static final String ID = "ae2_me_input_interface";
    private static final List<PortFamilyDescriptor> FAMILIES = AE2ResourceFamilies.inputFamilies();

    public static final InputInterfaceKind INSTANCE = new InputInterfaceKind();

    private final PortDefinition definition = PortDefinition.of(MMCR.id(ID), List.of(
            AE2ResourceFamilies.itemBinding(CapabilityDirections.input(), host -> host.nativeItemHandler(), false),
            AE2ResourceFamilies.fluidBinding(CapabilityDirections.input(), host -> host.nativeFluidHandler(), false)));

    private InputInterfaceKind() {}

    @Override
    public String id() {
        return ID;
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public List<PortFamilyDescriptor> families() {
        return FAMILIES;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<InputInterfaceBlockEntity> entityFactory() {
        return (pos, state) -> new InputInterfaceBlockEntity(pos, state, this);
    }

    @Override
    public PortDefinition definition() {
        return definition;
    }

    @Override
    public List<String> modDependencies() {
        return List.of("ae2");
    }
}
