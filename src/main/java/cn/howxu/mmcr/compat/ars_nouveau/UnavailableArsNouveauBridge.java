package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Inert bridge used when Ars Nouveau is unavailable.
 *
 * @author howxu <dev@howxu.cn>
 */
final class UnavailableArsNouveauBridge implements ArsNouveauBridge {
    static final UnavailableArsNouveauBridge INSTANCE = new UnavailableArsNouveauBridge();

    private UnavailableArsNouveauBridge() {
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<IOPortKind> portKinds() {
        return List.of();
    }

    @Override
    public IOPortBlockEntity createPort(BlockPos pos, BlockState state, IOPortKind kind) {
        throw new IllegalStateException("Ars Nouveau is unavailable; cannot create a source port");
    }

    @Override
    public MachineCapability createCapability(CapabilityCreationContext context) {
        throw new IllegalStateException("Ars Nouveau is unavailable; cannot create a source capability");
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
    }

    @Override
    public void registerMenus(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar) {
    }

    @Override
    public AbstractContainerMenu createMenu(String id, int containerId, Inventory inventory, Level level, BlockPos pos) {
        return null;
    }

    @Override
    public boolean isSourcePort(String id) {
        return false;
    }

    @Override
    public boolean isDominionWand(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack sourceIcon() {
        return ItemStack.EMPTY;
    }
}
