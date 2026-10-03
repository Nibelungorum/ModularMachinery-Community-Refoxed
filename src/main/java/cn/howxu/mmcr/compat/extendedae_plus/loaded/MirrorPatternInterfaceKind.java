package cn.howxu.mmcr.compat.extendedae_plus.loaded;

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
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** @author howxu <dev@howxu.cn> */
public final class MirrorPatternInterfaceKind implements PatternLogicKind {
    public static final MirrorPatternInterfaceKind INSTANCE = new MirrorPatternInterfaceKind();
    private final PortDefinition definition = AE2ResourceFamilies.definition(MMCR.id(id()), List.of(
            AE2ResourceFamilies.itemBinding(CapabilityDirections.bidirectional(), host -> host.nativeItemHandler()),
            AE2ResourceFamilies.fluidBinding(CapabilityDirections.bidirectional(), host -> host.nativeFluidHandler())));

    private MirrorPatternInterfaceKind() {
    }

    @Override public String id() { return "eaep_me_mirror_pattern_interface"; }
    @Override public IOType ioType() { return IOType.INPUT; }
    @Override public List<PortFamilyDescriptor> families() { return AE2ResourceFamilies.patternFamilies(); }
    @Override public PortDefinition definition() { return definition; }
    @Override public List<String> modDependencies() { return List.of("ae2", "extendedae", "extendedae_plus"); }
    @Override public boolean readOnlyPatterns() { return true; }

    @Override
    public BlockEntityType.BlockEntitySupplier<PatternInterfaceBlockEntity> entityFactory() {
        return (pos, state) -> new PatternInterfaceBlockEntity(pos, state, this);
    }

    @Override
    public PatternProviderLogic createPatternLogic(IManagedGridNode node, PatternProviderLogicHost host) {
        return new MirrorPatternInterfaceLogic(node, host);
    }

    @Override
    public Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        return new MirrorPatternInterfaceBlock(this, type, properties);
    }

    @Override
    public void tick(IOPortBlockEntity be) {
        if (be instanceof PatternInterfaceBlockEntity host && be.getLevel() instanceof ServerLevel level
                && host.getLogic() instanceof MirrorPatternInterfaceLogic logic) {
            logic.serverTick(level);
        }
    }
}
