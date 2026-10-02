package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * AE2 pattern-provider port exposing separate MMCR input and return-output views.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceKind implements PatternLogicKind {
    private static final String ID = "ae2_me_pattern_interface";
    private static final List<PortFamilyDescriptor> FAMILIES = AE2ResourceFamilies.patternFamilies();

    public static final PatternInterfaceKind INSTANCE = new PatternInterfaceKind();

    private final PortDefinition definition = AE2ResourceFamilies.definition(MMCR.id(ID), List.of(
            AE2ResourceFamilies.itemBinding(CapabilityDirections.bidirectional(), host -> host.nativeItemHandler()),
            AE2ResourceFamilies.fluidBinding(CapabilityDirections.bidirectional(), host -> host.nativeFluidHandler())));

    private PatternInterfaceKind() {
    }

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
    public BlockEntityType.BlockEntitySupplier<PatternInterfaceBlockEntity> entityFactory() {
        return (pos, state) -> new PatternInterfaceBlockEntity(pos, state, this);
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
