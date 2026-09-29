package cn.howxu.mmcr.compat.extendedae.loaded.kind;

import appeng.api.networking.IManagedGridNode;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InterfaceLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** @author howxu <dev@howxu.cn> */
public final class ExtendedInputInterfaceKind implements InterfaceLogicKind {
    public static final ExtendedInputInterfaceKind INSTANCE = new ExtendedInputInterfaceKind();
    private final PortDefinition definition = PortDefinition.of(MMCR.id(id()), List.of(
            AE2ResourceFamilies.itemBinding(CapabilityDirections.input(), host -> host.nativeItemHandler()),
            AE2ResourceFamilies.fluidBinding(CapabilityDirections.input(), host -> host.nativeFluidHandler())));
    private ExtendedInputInterfaceKind() {}
    @Override public String id() { return "eae_me_extended_input_interface"; }
    @Override public IOType ioType() { return IOType.INPUT; }
    @Override public List<PortFamilyDescriptor> families() { return AE2ResourceFamilies.inputFamilies(); }
    @Override public BlockEntityType.BlockEntitySupplier<InputInterfaceBlockEntity> entityFactory() { return (pos, state) -> new InputInterfaceBlockEntity(pos, state, this); }
    @Override public PortDefinition definition() { return definition; }
    @Override public List<String> modDependencies() { return List.of("ae2", "extendedae"); }
    @Override public InterfaceLogic createInterfaceLogic(IManagedGridNode node, InterfaceLogicHost host, net.minecraft.world.item.Item icon) { return new InterfaceLogic(node, host, AEBlocks.INTERFACE.asItem(), 36); }
}
