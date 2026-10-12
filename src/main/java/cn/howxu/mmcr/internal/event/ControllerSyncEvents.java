package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Completes controller baselines only after the client receives the owning chunk.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerSyncEvents {
    private static final Map<ServerPlayer, Set<MachineControllerBlockEntity>> TRACKING = new IdentityHashMap<>();
    private static final Map<ServerPlayer, MenuObserver> MENUS = new IdentityHashMap<>();
    private ControllerSyncEvents() { }

    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        for (var blockEntity : event.getChunk().getBlockEntities().values()) {
            if (blockEntity instanceof MachineControllerBlockEntity controller) {
                controller.sendClientStateBaseline(event.getPlayer());
            }
        }
    }

    public static void onChunkUnWatch(ChunkWatchEvent.UnWatch event) {
        Set<MachineControllerBlockEntity> controllers = TRACKING.get(event.getPlayer());
        if (controllers == null) return;
        for (MachineControllerBlockEntity controller : Set.copyOf(controllers)) {
            if (controller.getLevel() == event.getLevel()
                    && new ChunkPos(controller.getBlockPos()).equals(event.getPos())) {
                controller.forgetClientStateBaseline(event.getPlayer());
            }
        }
    }

    public static void observeTracking(ServerPlayer player, MachineControllerBlockEntity controller) {
        TRACKING.computeIfAbsent(player, ignored -> new HashSet<>()).add(controller);
    }

    public static void forgetTracking(ServerPlayer player, MachineControllerBlockEntity controller) {
        Set<MachineControllerBlockEntity> controllers = TRACKING.get(player);
        if (controllers == null) return;
        controllers.remove(controller);
        if (controllers.isEmpty()) TRACKING.remove(player);
    }

    public static void onMenuOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbstractContainerMenu menu = event.getContainer();
        MachineControllerBlockEntity controller = owner(player, menu);
        if (controller == null || player.containerMenu != menu) return;
        MenuObserver previous = MENUS.put(player, new MenuObserver(controller, menu));
        if (previous != null && previous.menu() != menu) previous.controller().forgetMenu(player, previous.menu());
        controller.observeMenu(player, menu);
        if (!(menu instanceof ControllerUiMenu uiMenu) || uiMenu.uiServerSession() == null) {
            controller.sendControllerScreenText(player);
            if (menu instanceof FactoryControllerMenu) controller.sendFactoryControllerState(player);
        }
    }

    public static void onMenuClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        forgetMenu(player, event.getContainer());
    }

    public static void forgetMenu(ServerPlayer player, AbstractContainerMenu menu) {
        // Late Close for an older menu must not remove the current opening.
        MenuObserver observer = MENUS.get(player);
        if (observer != null && observer.menu() == menu) {
            MENUS.remove(player);
            observer.controller().forgetMenu(player, menu);
        }
    }

    /** A newly placed/reloaded BE may appear in a chunk which was already delivered. */
    public static void onControllerLoaded(MachineControllerBlockEntity controller) {
        if (!(controller.getLevel() instanceof ServerLevel level) || controller.isRemoved()) return;
        BlockPos pos = controller.getBlockPos();
        // This scan is a load event, never part of the controller ticker.
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false)) {
            controller.sendClientStateBaseline(player);
        }
    }

    private static MachineControllerBlockEntity owner(ServerPlayer player, AbstractContainerMenu menu) {
        if (menu instanceof ControllerUiMenu uiMenu && uiMenu.uiServerSession() != null) {
            return uiMenu.uiServerSession().closed() ? null : uiMenu.uiServerSession().owner();
        }
        BlockPos pos = menu instanceof MachineControllerMenu normal ? normal.controllerPos()
                : menu instanceof FactoryControllerMenu factory ? factory.controllerPos() : null;
        return pos != null && player.level().getBlockEntity(pos) instanceof MachineControllerBlockEntity controller
                ? controller : null;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forgetPlayer(player);
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forgetPlayer(player);
    }

    public static void forgetPlayer(ServerPlayer player) {
        Set<MachineControllerBlockEntity> controllers = TRACKING.remove(player);
        if (controllers != null) {
            for (MachineControllerBlockEntity controller : controllers) controller.forgetClientStateBaseline(player);
        }
        MenuObserver observer = MENUS.remove(player);
        if (observer != null) {
            observer.controller().forgetMenu(player, observer.menu());
            if (observer.menu() instanceof ControllerUiMenu menu && menu.uiServerSession() != null) {
                menu.uiServerSession().close();
            }
        }
        if (player.containerMenu instanceof ControllerUiMenu menu && menu.uiServerSession() != null) {
            menu.uiServerSession().close();
        }
    }

    public static void forgetController(MachineControllerBlockEntity controller) {
        TRACKING.values().forEach(controllers -> controllers.remove(controller));
        TRACKING.values().removeIf(Set::isEmpty);
        for (var entry : Map.copyOf(MENUS).entrySet()) {
            MenuObserver observer = entry.getValue();
            if (observer.controller() != controller) continue;
            forgetMenu(entry.getKey(), observer.menu());
            if (observer.menu() instanceof ControllerUiMenu menu && menu.uiServerSession() != null) {
                menu.uiServerSession().close();
            }
        }
    }

    public static void forgetLevel(LevelAccessor level) {
        for (var entry : Map.copyOf(TRACKING).entrySet()) {
            for (MachineControllerBlockEntity controller : Set.copyOf(entry.getValue())) {
                if (controller.getLevel() == level) controller.forgetClientStateBaseline(entry.getKey());
            }
        }
        for (var entry : Map.copyOf(MENUS).entrySet()) {
            if (entry.getValue().controller().getLevel() == level) forgetPlayer(entry.getKey());
        }
    }

    public static void clear() {
        for (ServerPlayer player : Set.copyOf(TRACKING.keySet())) forgetPlayer(player);
        for (ServerPlayer player : Set.copyOf(MENUS.keySet())) forgetPlayer(player);
    }

    /** Exact menu identity protects a new opening from a delayed Close event.
     * @author howxu <dev@howxu.cn>
     */
    private record MenuObserver(MachineControllerBlockEntity controller, AbstractContainerMenu menu) { }
}
