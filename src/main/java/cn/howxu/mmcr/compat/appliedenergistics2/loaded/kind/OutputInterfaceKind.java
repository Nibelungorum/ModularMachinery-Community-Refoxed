package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;
import java.util.stream.Stream;

/**
 * AE2 interface-backed output port kind with a bounded local cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class OutputInterfaceKind implements InterfaceLogicKind {
    private static final String ID = "ae2_me_output_interface";
    private static final List<PortFamilyDescriptor> FAMILIES = AE2ResourceFamilies.outputFamilies();

    public static final OutputInterfaceKind INSTANCE = new OutputInterfaceKind();

    private final PortDefinition definition = PortDefinition.of(MMCR.id(ID), Stream.concat(Stream.of(
            AE2ResourceFamilies.ITEM.binding(IOType.OUTPUT, CapabilityDirections.output(),
                    host -> ((OutputInterfaceBlockEntity) host).itemStorage(), true,
                    new CapabilityBinding.ExternalExposure<>(Capabilities.Item.BLOCK.name(),
                            ResourceHandler.asClass(),
                            (host, _, _) -> ModCapabilities.resourceStorageHandler(
                                    ((OutputInterfaceBlockEntity) host).itemStorage(), false, true))),
            AE2ResourceFamilies.FLUID.binding(IOType.OUTPUT, CapabilityDirections.output(),
                    host -> ((OutputInterfaceBlockEntity) host).fluidStorage(), true,
                    new CapabilityBinding.ExternalExposure<>(Capabilities.Fluid.BLOCK.name(),
                            ResourceHandler.asClass(),
                            (host, _, _) -> ModCapabilities.resourceStorageHandler(
                                    ((OutputInterfaceBlockEntity) host).fluidStorage(), false, true)))),
            AE2ResourceFamilies.additionalOutputBindings().stream()).toList());

    private OutputInterfaceKind() {}

    @Override public List<Identifier> overlayTextures() { return List.of(MMCR.id("block/appliedenergistics2/ae2_output")); }

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
    public BlockEntityType.BlockEntitySupplier<OutputInterfaceBlockEntity> entityFactory() {
        return (pos, state) -> new OutputInterfaceBlockEntity(pos, state, this);
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
