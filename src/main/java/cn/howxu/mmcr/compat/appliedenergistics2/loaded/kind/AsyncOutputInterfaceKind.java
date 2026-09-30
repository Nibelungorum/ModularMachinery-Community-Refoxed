package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * AE2 interface-backed async output port kind that drains through a transient service.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AsyncOutputInterfaceKind implements InterfaceLogicKind {
    private static final String ID = "ae2_me_async_output_interface";
    private static final List<PortFamilyDescriptor> FAMILIES = AE2ResourceFamilies.outputFamilies();

    public static final AsyncOutputInterfaceKind INSTANCE = new AsyncOutputInterfaceKind();

    private final PortDefinition definition = PortDefinition.of(MMCR.id(ID), List.of(
            AE2ResourceFamilies.itemBinding(CapabilityDirections.output(), host -> host.nativeItemHandler(), false),
            AE2ResourceFamilies.fluidBinding(CapabilityDirections.output(), host -> host.nativeFluidHandler(), false)));

    private AsyncOutputInterfaceKind() {}

    @Override
    public String id() {
        return ID;
    }

    @Override
    public IOType ioType() {
        return IOType.OUTPUT;
    }

    @Override
    public List<PortFamilyDescriptor> families() {
        return FAMILIES;
    }

    @Override
    public int outputPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<AsyncOutputInterfaceBlockEntity> entityFactory() {
        return (pos, state) -> new AsyncOutputInterfaceBlockEntity(pos, state, this);
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
