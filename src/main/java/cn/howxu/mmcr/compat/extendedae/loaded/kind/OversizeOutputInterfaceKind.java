package cn.howxu.mmcr.compat.extendedae.loaded.kind;

import net.minecraft.resources.Identifier;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.extendedae.loaded.OversizeInterfaceLogicFactory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InterfaceLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** @author howxu <dev@howxu.cn> */
public final class OversizeOutputInterfaceKind implements InterfaceLogicKind {
    public static final OversizeOutputInterfaceKind INSTANCE = new OversizeOutputInterfaceKind();
    private final PortDefinition definition = PortDefinition.of(MMCR.id(id()), AE2ResourceFamilies.outputBindings());
    private OversizeOutputInterfaceKind() {}
    @Override public List<Identifier> overlayTextures() { return List.of(MMCR.id("block/extendedae/" + id())); }

    @Override public String id() { return "eae_me_oversize_output_interface"; }
    @Override public IOType ioType() { return IOType.OUTPUT; }
    @Override public List<PortFamilyDescriptor> families() { return AE2ResourceFamilies.outputFamilies(); }
    @Override public int outputPriority() { return Integer.MAX_VALUE; }
    @Override public BlockEntityType.BlockEntitySupplier<OutputInterfaceBlockEntity> entityFactory() { return (pos, state) -> new OutputInterfaceBlockEntity(pos, state, this); }
    @Override public PortDefinition definition() { return definition; }
    @Override public List<String> modDependencies() { return List.of("ae2", "extendedae"); }
    @Override public InterfaceLogic createInterfaceLogic(IManagedGridNode node, InterfaceLogicHost host, Item icon) { return OversizeInterfaceLogicFactory.create(node, host, icon); }
}
