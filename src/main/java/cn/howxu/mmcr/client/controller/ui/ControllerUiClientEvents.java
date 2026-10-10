package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiCustomStatePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiProgressPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiResponsePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Main-thread client session ownership and S2C handoff. Screen removal is not closure.
 * @author howxu <dev@howxu.cn> */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class ControllerUiClientEvents {
    private static final Map<AbstractContainerMenu, ControllerUiClientSession> SESSIONS = new IdentityHashMap<>();
    private static final AtomicReference<AbstractContainerMenu> OBSERVED_MENU = new AtomicReference<>();

    private ControllerUiClientEvents() {}

    /** Main-thread read-only query for legacy payload isolation; never creates a session. */
    public static boolean hasActiveSession(AbstractContainerMenu menu) {
        ControllerUiClientSession session = SESSIONS.get(menu);
        return session != null && session.isOpen();
    }

    /** Router entry, also used by independent MenuAccess screens. Call on the client main thread. */
    public static ControllerUiClientSession sessionFor(AbstractContainerMenu menu) {
        if (!(menu instanceof ControllerUiMenu controller)) throw new IllegalArgumentException("Not a controller menu");
        mainClient();
        var machine = MachineRegistry.getMachine(controller.uiOpenData().machineId());
        Component title = machine == null
                ? Component.translatable(MachineRegistration.defaultDisplayNameKey(controller.uiOpenData().machineId()))
                : machine.displayName();
        return sessionFor(menu, title);
    }

    /** The first call owns the opening title; subsequent calls return the same menu session. */
    public static ControllerUiClientSession sessionFor(AbstractContainerMenu menu, Component title) {
        Minecraft minecraft = mainClient();
        if (!(menu instanceof ControllerUiMenu controller)) throw new IllegalArgumentException("Not a controller menu");
        if (minecraft.player == null) throw new IllegalStateException("Controller UI requires a connected client player");
        AbstractContainerMenu current = minecraft.player.containerMenu;
        if (OBSERVED_MENU.get() != current) observeMenu(current, SESSIONS, OBSERVED_MENU);
        return SESSIONS.computeIfAbsent(menu, bound -> new ControllerUiClientSession(bound, controller.uiOpenData(), title,
                minecraft.player.level().registryAccess(), ControllerUiServerSession.currentProtocols(), minecraft,
                System::nanoTime, ClientPacketDistributor::sendToServer, minecraft::isSameThread,
                () -> minecraft.player == null ? null : minecraft.player.containerMenu,
                () -> { if (minecraft.player != null && minecraft.player.containerMenu == bound) minecraft.player.closeContainer(); },
                OBSERVED_MENU::get));
    }

    public static void handle(PktControllerUiSnapshotPayload packet) {
        ControllerUiClientSession session = matching(packet.containerId(), packet.sessionId());
        if (session != null) session.handle(packet);
    }

    public static void handle(PktControllerUiProgressPayload packet) {
        ControllerUiClientSession session = matching(packet.containerId(), packet.sessionId());
        if (session != null) session.handle(packet);
    }

    public static void handle(PktControllerUiResponsePayload packet) {
        ControllerUiClientSession session = matching(packet.containerId(), packet.sessionId());
        if (session != null) session.handle(packet);
    }

    public static void handle(PktControllerUiCustomStatePayload packet) {
        ControllerUiClientSession session = matching(packet.containerId(), packet.sessionId());
        if (session != null) session.handle(packet);
    }

    private static ControllerUiClientSession matching(int containerId, UUID sessionId) {
        Minecraft minecraft = mainClient();
        if (minecraft.player == null) return null;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu.containerId != containerId || !(menu instanceof ControllerUiMenu controller)
                || !controller.uiOpenData().sessionId().equals(sessionId)) return null;
        return sessionFor(menu);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = mainClient();
        AbstractContainerMenu current = minecraft.player == null ? null : minecraft.player.containerMenu;
        if (OBSERVED_MENU.get() != current) observeMenu(current, SESSIONS, OBSERVED_MENU);
        for (AbstractContainerMenu menu : List.copyOf(SESSIONS.keySet())) {
            ControllerUiClientSession session = SESSIONS.get(menu);
            if (session == null) continue;
            session.tick();
            if (menu != current) SESSIONS.remove(menu, session);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        mainClient();
        observeMenu(null, SESSIONS, OBSERVED_MENU);
    }

    /** Native open packets install containerMenu before this event, even if opening is cancelled.
     * Temporary pages/resizes keep the same menu and therefore the same session. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onScreenOpening(ScreenEvent.Opening event) { onMenuChanged(); }

    /** Also called after LocalPlayer restores inventoryMenu, before setScreen(null). */
    public static void onMenuChanged() {
        Minecraft minecraft = mainClient();
        observeMenu(minecraft.player == null ? null : minecraft.player.containerMenu, SESSIONS, OBSERVED_MENU);
    }

    static void observeMenu(AbstractContainerMenu current, Map<AbstractContainerMenu, ControllerUiClientSession> sessions,
                            AtomicReference<AbstractContainerMenu> observed) {
        // Publish before invoking onClosed authors: arbitrary executors only read this reference
        // and the session's volatile lifetime, never Minecraft's live player.
        observed.set(current);
        for (AbstractContainerMenu menu : List.copyOf(sessions.keySet())) {
            if (observed.get() != current) return; // A reentrant native transition supersedes this one.
            ControllerUiClientSession session = sessions.get(menu);
            if (session == null) continue;
            session.menuChanged(current);
            if (menu != current) sessions.remove(menu, session);
        }
    }

    private static Minecraft mainClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Controller UI events require the client main thread");
        return minecraft;
    }
}
