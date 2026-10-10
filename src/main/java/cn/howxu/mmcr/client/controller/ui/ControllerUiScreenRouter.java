package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.client.ControllerUiRegistration;
import cn.howxu.mmcr.internal.api.facade.client.UiClientAdapters;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Native create-path adapter for both ordinary and advanced open-screen packets.
 * Routing uses opening metadata before the first server snapshot; screen changes
 * retain the menu-owned session. Call registration only in the client screen window.
 * @author howxu <dev@howxu.cn>
 */
@OnlyIn(Dist.CLIENT)
public final class ControllerUiScreenRouter {
    private ControllerUiScreenRouter() {}

    public static <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void register(
            RegisterMenuScreensEvent event, MenuType<M> type,
            ControllerUiRegistration registrations, MenuScreens.ScreenConstructor<M, S> fallback) {
        register(event, type, registrations, fallback, ControllerUiClientEvents::sessionFor);
    }

    static <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void register(
            RegisterMenuScreensEvent event, MenuType<M> type, ControllerUiRegistration registrations,
            MenuScreens.ScreenConstructor<M, S> fallback,
            BiFunction<M, Component, ? extends ControllerUiRegistration.Session> sessions) {
        event.register(type, constructor(registrations, fallback, sessions));
    }

    // The intersection cast is confined here. Both native callers use create(), not just fromPacket().
    @SuppressWarnings("unchecked")
    static <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>, U extends Screen & MenuAccess<M>>
            MenuScreens.ScreenConstructor<M, U> constructor(
            ControllerUiRegistration registrations, MenuScreens.ScreenConstructor<M, S> fallback,
            BiFunction<M, Component, ? extends ControllerUiRegistration.Session> sessions) {
        return (menu, inventory, title) -> {
            if (!(menu instanceof ControllerUiMenu controller)) throw new IllegalArgumentException("Not a controller menu");
            var session = sessions.apply(menu, title);
            var context = UiClientAdapters.context(menu, inventory, title, session);
            var factory = registrations.find(controller.uiOpenData().machineId()).orElse(null);
            var slots = session.slots();
            Supplier<U> create = () -> {
                if (factory != null) {
                    try {
                        Screen screen = factory.create(context);
                        if (!(screen instanceof MenuAccess<?> access) || access.getMenu() != menu) {
                            throw new IllegalStateException("Controller UI must retain its opening menu");
                        }
                        return (U) screen;
                    } catch (RuntimeException exception) {
                        MMCR.LOG.error("Controller UI factory {} failed for machine {}; opening default screen",
                                factory, controller.uiOpenData().machineId(), exception);
                        slots.setPlayerInventoryVisible(true);
                    }
                }
                return (U) fallback.create(menu, inventory, title);
            };
            return slots instanceof ControllerUiSlotVisibility visibility
                    ? visibility.duringScreenConstruction(create) : create.get();
        };
    }
}
