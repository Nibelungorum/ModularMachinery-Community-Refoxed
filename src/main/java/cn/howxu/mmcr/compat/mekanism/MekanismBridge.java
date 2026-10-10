package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.capability.MachineCapability;
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

    default HeatDisplayData heatDisplayData(double kelvin) {
        return new HeatDisplayData(kelvin, "K");
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

    /** Returns the optional capability identity represented by a menu, or {@code null}. */
    default Identifier capabilityIdForMenu(AbstractContainerMenu menu) {
        return null;
    }

    /**
     * Checks an optional port menu without exposing its implementation type to common code.
     * A {@code null} position is treated as a failure; a {@code null port} skips owner matching.
     */
    default boolean isPortMenuAt(AbstractContainerMenu menu, BlockPos pos, IOPortBlockEntity port) {
        return false;
    }

    default ResourceHandler<FluidResource> manualFluidContainerHandler(ResourceHandler<FluidResource> handler) {
        return handler;
    }

    default int transferChemicalContainer(ServerPlayer player, AbstractContainerMenu menu,
                                          int tankIndex, TransactionContext transaction) {
        return 0;
    }

    default boolean isPort(String id) {
        return portDeclarations().stream().anyMatch(declaration -> declaration.id().equals(id));
    }

    default AbstractContainerMenu createMenu(String id, int containerId, Inventory playerInventory,
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
                           boolean radioactive) {
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
