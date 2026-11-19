package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.item.InterfaceTooltips;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.function.Supplier;

/**
 * Isolates optional Mekanism integration from the common runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MekanismBridge {
    static MekanismBridge get() {
        return MekanismBridgeBootstrap.bridge();
    }

    boolean available();

    boolean supportsPortFamily(Identifier familyId);

    Identifier unavailableReason();

    default ChemicalRenderData chemicalRenderData(Identifier chemicalId) {
        return null;
    }

    /** Presents heat output, not an absolute temperature. */
    default HeatDisplayData heatDisplayData(double heat) {
        return new HeatDisplayData(heat, "K");
    }

    default boolean isNonEmptyRadioactiveChemicalPort(BlockEntity blockEntity) {
        return false;
    }

    void registerRecipeTypes(Identifier chemical, Identifier heatTemperature, Identifier heat);

    default List<PortDeclaration> portDeclarations() {
        return List.of();
    }

    default MachineCapability createChemicalCapability(CapabilityCreationContext context) {
        throw new IllegalStateException("Mekanism chemical capability is unavailable");
    }

    default MachineCapability createHeatCapability(CapabilityCreationContext context) {
        throw new IllegalStateException("Mekanism heat capability is unavailable");
    }

    default IOPortBlockEntity createChemicalPort(BlockPos pos, BlockState state, IOPortKind kind,
                                                  long capacity, boolean radioactive) {
        throw new IllegalStateException("Mekanism chemical ports are unavailable");
    }

    default IOPortBlockEntity createHeatPort(BlockPos pos, BlockState state, IOPortKind kind) {
        throw new IllegalStateException("Mekanism heat ports are unavailable");
    }

    default void registerMenus(MenuRegistrar registrar) {
    }

    default ResourceHandler<FluidResource> manualFluidContainerHandler(ResourceHandler<FluidResource> handler) {
        return handler;
    }

    default int transferChemicalContainer(ServerPlayer player, AbstractContainerMenu menu,
                                          int tankIndex, boolean extract, TransactionContext transaction) {
        return 0;
    }

    default int interactWithChemicalContainer(ItemAccess access, IOPortBlockEntity port,
                                               TransactionContext transaction) {
        return 0;
    }

    default boolean isPort(String id) {
        return portDeclarations().stream().anyMatch(declaration -> declaration.id().equals(id));
    }

    default AbstractContainerMenu createMenu(PortType type, int containerId, Inventory playerInventory,
                                              Level level, BlockPos pos) {
        return null;
    }

    default void writeClientOpenData(FriendlyByteBuf buffer, String id, BlockPos pos) {
        buffer.writeBlockPos(pos);
    }

    default void registerCapabilities(RegisterCapabilitiesEvent event) {
    }

    default void registerTransferPolicies() {
    }

    record PortDeclaration(String id, PortType type, IOType ioType, int tier, long capacity,
                           boolean radioactive) implements IOPortKind {
        @Override public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Level level, BlockPos pos) {
            return get().createMenu(type, containerId, inventory, level, pos);
        }
        @Override public void writeOpenData(FriendlyByteBuf buffer, BlockPos pos) { get().writeClientOpenData(buffer, id, pos); }
        @Override public boolean handlesContainerInteraction() { return true; }
        @Override public int interactWithContainer(ItemAccess access, IOPortBlockEntity port, TransactionContext tx) {
            return get().interactWithChemicalContainer(access, port, tx);
        }
        @Override public boolean availableForStructure() { return MekanismBridge.get().available(); }
        @Override public List<String> modDependencies() { return List.of("mekanism"); }

        @Override public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
            return type == PortType.HEAT ? (pos, state) -> get().createHeatPort(pos, state, this)
                    : (pos, state) -> get().createChemicalPort(pos, state, this, capacity, radioactive);
        }

        private Identifier bindingId() {
            return type == PortType.HEAT ? MekanismPortFamilies.HEAT : MekanismPortFamilies.CHEMICAL;
        }

        @Override public PortDefinition definition() {
            return PortDefinition.of(MMCR.id(id), new CapabilityBinding(new CapabilityType(bindingId()),
                    CapabilityDirections.of(ioType), context -> type == PortType.HEAT
                    ? get().createHeatCapability(context) : get().createChemicalCapability(context),
                    (binding, currentTier) -> currentTier >= tier));
        }

        @Override public List<PortFamilyDescriptor> families() {
            String family = type == PortType.HEAT ? "heat" : radioactive ? "radioactive_chemical" : "chemical";
            String alias = family + (ioType == IOType.INPUT ? "_input_hatch" : "_output_hatch");
            return List.of(new PortFamilyDescriptor(radioactive ? MekanismPortFamilies.RADIOACTIVE_CHEMICAL : bindingId(),
                    ioType, tier, List.of(alias), bindingId()));
        }

        @Override public List<Component> tooltipLines() {
            return List.of(type == PortType.HEAT ? InterfaceTooltips.capacityTooltip((double) capacity, "J/K")
                    : InterfaceTooltips.capacityTooltip(capacity, "mB"));
        }

        @Override public List<Identifier> overlayTextures() {
            String base = type == PortType.HEAT ? "heat" : radioactive ? "raditional_chemical" : "chemical";
            String tierTexture = type == PortType.HEAT ? "huge" : radioactive ? "ludicrous"
                    : "extended/" + id.substring(id.lastIndexOf('_') + 1);
            return List.of(MMCR.id("block/overlay/base/mekanism/" + base),
                    MMCR.id("block/overlay/direction/" + (ioType == IOType.INPUT ? "input" : "output")),
                    MMCR.id("block/overlay/tier/" + tierTexture));
        }

        public PortDeclaration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (type == null) throw new IllegalArgumentException("type must not be null");
            if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
            if (tier < 0) throw new IllegalArgumentException("tier must be >= 0");
            if (capacity <= 0L) throw new IllegalArgumentException("capacity must be positive");
            if (type != PortType.CHEMICAL && radioactive) {
                throw new IllegalArgumentException("only chemical ports may be radioactive");
            }
        }
    }

    enum PortType {
        CHEMICAL,
        HEAT
    }

    record ChemicalRenderData(Identifier spriteLocation, int tint, Component displayName) {
        public ChemicalRenderData {
            if (spriteLocation == null || displayName == null) throw new IllegalArgumentException("chemical data is incomplete");
        }
    }

    record HeatDisplayData(double value, String unit) {
        public HeatDisplayData {
            if (!Double.isFinite(value) || unit == null || unit.isBlank()) {
                throw new IllegalArgumentException("heat display data is incomplete");
            }
        }
    }

    @FunctionalInterface
    interface MenuRegistrar {
        void register(String id, Supplier<? extends net.minecraft.world.inventory.MenuType<?>> supplier);
    }
}
