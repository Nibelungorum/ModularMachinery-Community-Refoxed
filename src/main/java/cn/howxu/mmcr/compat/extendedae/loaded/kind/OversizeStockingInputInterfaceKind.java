package cn.howxu.mmcr.compat.extendedae.loaded.kind;

import appeng.api.networking.IManagedGridNode;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.extendedae.loaded.OversizeInterfaceLogicFactory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InterfaceLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** @author howxu <dev@howxu.cn> */
public final class OversizeStockingInputInterfaceKind implements InterfaceLogicKind {
    public static final OversizeStockingInputInterfaceKind INSTANCE = new OversizeStockingInputInterfaceKind();
    private final PortDefinition definition = PortDefinition.of(MMCR.id(id()), List.of(
            AE2ResourceFamilies.itemBinding(CapabilityDirections.input(), host -> host.nativeItemHandler()),
            AE2ResourceFamilies.fluidBinding(CapabilityDirections.input(), host -> host.nativeFluidHandler())));
    private OversizeStockingInputInterfaceKind() {}
    @Override public String id() { return "eae_me_oversize_stocking_input_interface"; }
    @Override public IOType ioType() { return IOType.INPUT; }
    @Override public List<PortFamilyDescriptor> families() { return AE2ResourceFamilies.inputFamilies(); }
    @Override public BlockEntityType.BlockEntitySupplier<StockingInterfaceBlockEntity> entityFactory() { return (pos, state) -> new StockingInterfaceBlockEntity(pos, state, this); }
    @Override public PortDefinition definition() { return definition; }
    @Override public List<String> modDependencies() { return List.of("ae2", "extendedae"); }
    @Override public InterfaceLogic createInterfaceLogic(IManagedGridNode node, InterfaceLogicHost host, Item icon) { return OversizeInterfaceLogicFactory.create(node, host, icon); }
}
