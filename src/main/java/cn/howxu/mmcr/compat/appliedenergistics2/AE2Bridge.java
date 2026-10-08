package cn.howxu.mmcr.compat.appliedenergistics2;

import cn.howxu.mmcr.internal.assembly.StructureItemStorage;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Isolates optional AE2 integration from the common runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface AE2Bridge {
    static AE2Bridge get() {
        return AE2BridgeBootstrap.bridge();
    }

    boolean available();

    List<IOPortKind> portKinds();

    boolean isPort(String id);

    boolean openMenu(ServerPlayer player, Level level, BlockPos pos);

    default void registerMenus(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar) {
    }

    default boolean useMemoryCard(ItemStack stack, Level level, BlockPos pos, Player player) {
        return false;
    }

    default boolean isWirelessAccessPoint(ServerPlayer player, GlobalPos accessPoint) {
        return false;
    }

    default Optional<StructureItemStorage> resolveTerminalNetworkStorage(ServerPlayer player, GlobalPos accessPoint) {
        return Optional.empty();
    }

    default void onPortNeighborChanged(IOPortBlockEntity port) {
    }

    default void registerCapabilities(RegisterCapabilitiesEvent event) {
    }
}
