package cn.howxu.mmcr.compat.appliedflux.loaded.kind;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import net.minecraft.resources.ResourceLocation;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedflux.loaded.tile.FluxEnergyOutputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * AppFlux-backed ME energy output port kind.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyOutputKind implements IOPortKind {
    @Override public InteractionResult openSpecialMenu(Player player, Level level, BlockPos pos) { return InteractionResult.CONSUME; }

    private static final String ID = "appflux_me_flux_output_interface";
    private static final int DETECTION_TIER = EnergyHatchSize.ULTIMATE.ordinal() + 1;
    private static final List<PortFamilyDescriptor> FAMILIES = List.of(
            new PortFamilyDescriptor(PortFamilyIds.ENERGY, IOType.OUTPUT, DETECTION_TIER,
                    List.of("energy_output_hatch")));

    public static final FluxEnergyOutputKind INSTANCE = new FluxEnergyOutputKind();

    private final PortDefinition definition = PortDefinition.of(MMCR.id(ID), CapabilityBinding.internalOnly(
            BuiltinCapabilityDefinitions.ENERGY_TYPE,
            CapabilityDirections.output(),
            FluxEnergyOutputKind::hostedCapability,
            (binding, tier) -> tier >= DETECTION_TIER));

    private FluxEnergyOutputKind() {
    }

    @Override public List<ResourceLocation> overlayTextures() { return List.of(MMCR.id("block/appliedflux/appflux_output")); }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public IOType ioType() {
        return IOType.OUTPUT;
    }

    @Override
    public int outputPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<FluxEnergyOutputInterfaceBlockEntity> entityFactory() {
        return (pos, state) -> new FluxEnergyOutputInterfaceBlockEntity(pos, state, this);
    }

    @Override
    public PortDefinition definition() {
        return definition;
    }

    @Override
    public List<PortFamilyDescriptor> families() {
        return FAMILIES;
    }

    @Override
    public List<String> modDependencies() {
        return List.of("ae2", "appflux");
    }

    private static MachineCapability hostedCapability(CapabilityCreationContext context) {
        return context.host().capabilities().stream()
                .filter(capability -> capability.type().equals(BuiltinCapabilityDefinitions.ENERGY_TYPE))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AppFlux output host has no energy capability"));
    }
}
