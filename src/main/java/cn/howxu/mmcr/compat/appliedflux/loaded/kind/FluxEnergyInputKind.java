package cn.howxu.mmcr.compat.appliedflux.loaded.kind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.appliedflux.loaded.tile.FluxEnergyInputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * AppFlux-backed ME energy input port kind.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyInputKind implements IOPortKind {
    private static final String ID = "appflux_me_flux_input_interface";
    private static final int DETECTION_TIER = EnergyHatchSize.ULTIMATE.ordinal() + 1;
    private static final List<PortFamilyDescriptor> FAMILIES = List.of(
            new PortFamilyDescriptor(PortFamilyIds.ENERGY, IOType.INPUT, DETECTION_TIER,
                    List.of("energy_input_hatch")));

    public static final FluxEnergyInputKind INSTANCE = new FluxEnergyInputKind();

    private final PortDefinition definition = PortDefinition.of(MMCR.id(ID), CapabilityBinding.internalOnly(
            BuiltinCapabilityDefinitions.ENERGY_TYPE,
            CapabilityDirections.input(),
            FluxEnergyInputKind::hostedCapability,
            (binding, tier) -> tier >= DETECTION_TIER));

    private FluxEnergyInputKind() {
    }

    @Override
    public InteractionResult openSpecialMenu(Player player, Level level, BlockPos pos) {
        return InteractionResult.CONSUME;
    }

    @Override public List<Identifier> overlayTextures() { return List.of(MMCR.id("block/appliedflux/appflux_input")); }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<FluxEnergyInputInterfaceBlockEntity> entityFactory() {
        return (pos, state) -> new FluxEnergyInputInterfaceBlockEntity(pos, state, this);
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
                .orElseThrow(() -> new IllegalStateException("AppFlux input host has no energy capability"));
    }
}
