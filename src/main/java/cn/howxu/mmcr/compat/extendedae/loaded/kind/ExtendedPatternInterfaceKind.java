package cn.howxu.mmcr.compat.extendedae.loaded.kind;

import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** @author howxu <dev@howxu.cn> */
public final class ExtendedPatternInterfaceKind implements PatternLogicKind {
    public static final ExtendedPatternInterfaceKind INSTANCE = new ExtendedPatternInterfaceKind();
    private final PortDefinition definition = PortDefinition.of(MMCR.id(id()), List.of(
            AE2ResourceFamilies.itemBinding(CapabilityDirections.bidirectional(), host -> host.nativeItemHandler()),
            AE2ResourceFamilies.fluidBinding(CapabilityDirections.bidirectional(), host -> host.nativeFluidHandler())));
    private ExtendedPatternInterfaceKind() {}
    @Override public String id() { return "eae_me_extended_pattern_interface"; }
    @Override public IOType ioType() { return IOType.INPUT; }
    @Override public List<PortFamilyDescriptor> families() { return AE2ResourceFamilies.patternFamilies(); }
    @Override public BlockEntityType.BlockEntitySupplier<PatternInterfaceBlockEntity> entityFactory() { return (pos, state) -> new PatternInterfaceBlockEntity(pos, state, this); }
    @Override public PortDefinition definition() { return definition; }
    @Override public List<String> modDependencies() { return List.of("ae2", "extendedae"); }
    @Override public PatternProviderLogic createPatternLogic(IManagedGridNode node, PatternProviderLogicHost host) { return new PatternProviderLogic(node, host, 36); }
}
