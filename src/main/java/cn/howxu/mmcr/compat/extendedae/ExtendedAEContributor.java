package cn.howxu.mmcr.compat.extendedae;

import appeng.menu.ISubMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Isolates optional ExtendedAE integration from the AE2 bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ExtendedAEContributor {
    boolean available();

    List<IOPortKind> portKinds();

    boolean isPort(String id);

    boolean openMenu(ServerPlayer player, Level level, BlockPos pos);

    default void registerMenus(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar) {
    }

    default @Nullable MenuType<?> menuType(IOPortKind kind) {
        return null;
    }

    /**
     * Routes an AE2 sub-menu return back to the matching ExtendedAE main menu.
     *
     * @return {@code true} if the contributor claimed the {@code kind} and dispatched a return.
     */
    boolean returnToMainMenu(ServerPlayer player, ISubMenu subMenu, IOPortKind kind);

    /**
     * @return the ExtendedAE main-menu icon for {@code kind}, or {@code null} if the contributor does not
     *         claim the kind so the host can fall back to the AE2 default.
     */
    @Nullable
    ItemStack mainMenuIcon(IOPortKind kind);

    void registerCapabilities(RegisterCapabilitiesEvent event);
}
