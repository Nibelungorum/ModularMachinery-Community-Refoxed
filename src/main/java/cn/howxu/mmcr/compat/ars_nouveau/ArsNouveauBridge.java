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
 * Neutral entry point for the optional Ars Nouveau integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ArsNouveauBridge {
    static ArsNouveauBridge get() {
        return ArsNouveauBridgeBootstrap.get();
    }

    boolean available();

    List<IOPortKind> portKinds();

    IOPortBlockEntity createPort(BlockPos pos, BlockState state, IOPortKind kind);

    MachineCapability createCapability(CapabilityCreationContext context);

    void registerCapabilities(RegisterCapabilitiesEvent event);

    void registerMenus(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar);

    AbstractContainerMenu createMenu(String id, int containerId, Inventory inventory, Level level, BlockPos pos);

    boolean isSourcePort(String id);

    boolean isDominionWand(ItemStack stack);

    ItemStack sourceIcon();
}
