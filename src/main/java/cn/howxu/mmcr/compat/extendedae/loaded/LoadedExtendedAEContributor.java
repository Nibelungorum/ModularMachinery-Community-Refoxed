package cn.howxu.mmcr.compat.extendedae.loaded;

import appeng.api.AECapabilities;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedOutputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedStockingInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.OversizeInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.OversizeOutputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.OversizeStockingInputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.container.ContainerExInterface;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** Provides ExtendedAE-specific AE2 port kinds after both optional mods are loaded.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedExtendedAEContributor implements ExtendedAEContributor {
    // OversizeStockingInputInterfaceKind is intentionally excluded until its UI behavior stabilizes;
    // the kind class is retained so the implementation can be re-enabled without re-deriving it.
    private static final List<IOPortKind> KINDS = List.of(
            ExtendedInputInterfaceKind.INSTANCE, ExtendedStockingInputInterfaceKind.INSTANCE,
            ExtendedOutputInterfaceKind.INSTANCE, OversizeInputInterfaceKind.INSTANCE,
            OversizeOutputInterfaceKind.INSTANCE,
            ExtendedPatternInterfaceKind.INSTANCE);

    @Override public boolean available() { return true; }
    @Override public List<IOPortKind> portKinds() { return KINDS; }
    @Override public boolean isPort(String id) { return KINDS.stream().anyMatch(kind -> kind.id().equals(id)); }

    @Override
    public boolean openMenu(ServerPlayer player, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof cn.howxu.mmcr.internal.tile.IOPortBlockEntity host)
                || !KINDS.contains(host.kind())) return false;
        if (host.kind() == ExtendedPatternInterfaceKind.INSTANCE) {
            return MenuOpener.open(ContainerExPatternProvider.TYPE, player, MenuLocators.forBlockEntity(host));
        }
        if (isOversize(host.kind())) {
            return MenuOpener.open(ContainerExInterface.TYPE_OVERSIZE, player, MenuLocators.forBlockEntity(host));
        }
        return MenuOpener.open(ContainerExInterface.TYPE, player, MenuLocators.forBlockEntity(host));
    }

    @Override
    public boolean returnToMainMenu(ServerPlayer player, ISubMenu subMenu, IOPortKind kind) {
        if (!KINDS.contains(kind)) return false;
        var locator = subMenu.getLocator();
        if (kind == ExtendedPatternInterfaceKind.INSTANCE) {
            return MenuOpener.returnTo(ContainerExPatternProvider.TYPE, player, locator);
        }
        if (isOversize(kind)) {
            return MenuOpener.returnTo(ContainerExInterface.TYPE_OVERSIZE, player, locator);
        }
        return MenuOpener.returnTo(ContainerExInterface.TYPE, player, locator);
    }

    @Override
    public @Nullable ItemStack mainMenuIcon(IOPortKind kind) {
        if (!KINDS.contains(kind)) return null;
        if (isOversize(kind)) {
            return new ItemStack(EAESingletons.OVERSIZE_INTERFACE);
        }
        if (kind == ExtendedPatternInterfaceKind.INSTANCE) {
            return new ItemStack(EAESingletons.EX_PATTERN_PROVIDER);
        }
        return new ItemStack(EAESingletons.EX_INTERFACE);
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        registerInterface(event, ExtendedInputInterfaceKind.INSTANCE.id(), InputInterfaceBlockEntity.class, true);
        registerInterface(event, OversizeInputInterfaceKind.INSTANCE.id(), InputInterfaceBlockEntity.class, true);
        registerInterface(event, ExtendedStockingInputInterfaceKind.INSTANCE.id(), StockingInterfaceBlockEntity.class, false);
        registerInterface(event, ExtendedOutputInterfaceKind.INSTANCE.id(), OutputInterfaceBlockEntity.class, false);
        registerInterface(event, OversizeOutputInterfaceKind.INSTANCE.id(), OutputInterfaceBlockEntity.class, false);
        BlockEntityType<?> type = type(ExtendedPatternInterfaceKind.INSTANCE.id());
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type,
                (be, v) -> be instanceof PatternInterfaceBlockEntity host ? host : null);
        event.registerBlockEntity(AECapabilities.GENERIC_INTERNAL_INV, type,
                (be, v) -> be instanceof PatternInterfaceBlockEntity host ? host.getLogic().getReturnInv() : null);
    }

    private static boolean isOversize(IOPortKind kind) {
        return kind == OversizeInputInterfaceKind.INSTANCE || kind == OversizeStockingInputInterfaceKind.INSTANCE
                || kind == OversizeOutputInterfaceKind.INSTANCE;
    }

    private static BlockEntityType<?> type(String id) { return ModBlockEntities.BES.get(id).get(); }

    private static <T extends cn.howxu.mmcr.internal.tile.IOPortBlockEntity & InterfaceLogicHost
            & IGridConnectedBlockEntity> void registerInterface(
            RegisterCapabilitiesEvent event, String id, Class<T> hostType, boolean storage) {
        BlockEntityType<?> type = type(id);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type,
                (be, v) -> hostType.isInstance(be) ? hostType.cast(be) : null);
        if (storage) {
            event.registerBlockEntity(AECapabilities.GENERIC_INTERNAL_INV, type,
                    (be, direction) -> hostType.isInstance(be) ? hostType.cast(be).getInterfaceLogic().getStorage() : null);
            event.registerBlockEntity(AECapabilities.ME_STORAGE, type,
                    (be, direction) -> hostType.isInstance(be) ? hostType.cast(be).getInterfaceLogic().getInventory() : null);
        }
    }
}
