package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.api.controller.ui.client.ControllerUiRegistration;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Bound-menu visibility policy. All slot methods require the Minecraft main thread.
 * @author howxu <dev@howxu.cn> */
public final class ControllerUiSlotVisibility implements ControllerUiRegistration.Slots {
    private final AbstractContainerMenu menu;
    private final ControllerUiMenu controllerMenu;
    private final UUID token;
    private final BooleanSupplier mainThread;
    private final BooleanSupplier open;
    private final Supplier<AbstractContainerMenu> currentMenu;
    private final Supplier<Screen> currentScreen;
    private final AbstractContainerMenu precedingMenu;
    private boolean constructingScreen;
    private boolean constructionFinished;

    public ControllerUiSlotVisibility(AbstractContainerMenu menu, UUID token, BooleanSupplier mainThread,
                                      BooleanSupplier open, Supplier<AbstractContainerMenu> currentMenu) {
        this(menu, token, mainThread, open, currentMenu, () -> {
            Minecraft client = Minecraft.getInstance();
            return client == null ? null : client.screen;
        });
    }

    ControllerUiSlotVisibility(AbstractContainerMenu menu, UUID token, BooleanSupplier mainThread,
                               BooleanSupplier open, Supplier<AbstractContainerMenu> currentMenu,
                               Supplier<Screen> currentScreen) {
        this.menu = Objects.requireNonNull(menu, "menu");
        if (!(menu instanceof ControllerUiMenu controller)) throw new IllegalArgumentException("Not a controller menu");
        this.controllerMenu = controller;
        this.token = Objects.requireNonNull(token, "token");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.open = Objects.requireNonNull(open, "open");
        this.currentMenu = Objects.requireNonNull(currentMenu, "currentMenu");
        this.currentScreen = Objects.requireNonNull(currentScreen, "currentScreen");
        this.precedingMenu = currentMenu.get();
    }

    @Override public boolean isPlayerInventoryVisible() {
        requireMainThread();
        return controllerMenu.playerInventoryVisible();
    }

    @Override public void setPlayerInventoryVisible(boolean visible) {
        requireMainThread();
        if (!validOpening() || currentMenu.get() != menu
                && !(constructingScreen && currentMenu.get() == precedingMenu)) return;
        if (controllerMenu.playerInventoryVisible() == visible) return;
        Screen screen = currentScreen.get();
        if (!visible && screen instanceof AbstractContainerScreen<?> container && container.getMenu() == menu
                && screen instanceof ContainerScreenInputControl control) control.resetControllerSlotInput();
        controllerMenu.setPlayerInventoryVisible(visible);
    }

    /** Router-only synchronous construction scope, before vanilla installs player.containerMenu.
     * The scope is consumed once; deferred setters still require the installed bound menu.
     */
    public <T> T duringScreenConstruction(Supplier<T> factory) {
        requireMainThread();
        if (validOpening() && currentMenu.get() == menu && !constructingScreen) {
            constructionFinished = true;
            return factory.get();
        }
        if (constructionFinished || constructingScreen || !validOpening()
                || currentMenu.get() != precedingMenu) {
            throw new IllegalStateException("Controller screen construction no longer belongs to this opening");
        }
        constructingScreen = true;
        try {
            return factory.get();
        } finally {
            constructingScreen = false;
            constructionFinished = true;
        }
    }

    private boolean validOpening() {
        return open.getAsBoolean() && token.equals(controllerMenu.uiOpenData().sessionId());
    }

    boolean isConstructingScreen() { return constructingScreen; }

    private void requireMainThread() {
        if (!mainThread.getAsBoolean()) throw new IllegalStateException("Slot visibility requires the client main thread");
    }
}
