package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Visibility changes preserve vanilla slot identity and opening ownership.
 * @author howxu <dev@howxu.cn> */
class ControllerUiSlotVisibilityTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER, new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
        bind(ModUIs.FACTORY_CONTROLLER, new MenuType<>(FactoryControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void both_menus_hide_inventory_and_hotbar_without_rebuilding_or_mutating_slots() {
        Inventory inventory = new Inventory(null, null);
        for (AbstractContainerMenu menu : List.of(MachineControllerMenu.clientOpen(1, inventory),
                FactoryControllerMenu.clientOpen(2, inventory))) {
            List<Slot> original = List.copyOf(menu.slots);
            assertThat(original).hasSize(36);
            var carried = menu.getCarried();
            var items = original.stream().map(Slot::getItem).toList();
            var visibility = visibility(menu, new AtomicReference<>(menu), new AtomicBoolean(true));
            for (boolean visible : List.of(false, true, false, true)) {
                visibility.setPlayerInventoryVisible(visible);
                assertThat(visibility.isPlayerInventoryVisible()).isEqualTo(visible);
                for (int i = 0; i < original.size(); i++) {
                    Slot slot = menu.slots.get(i);
                    assertThat(slot).isSameAs(original.get(i));
                    assertThat(slot.isActive()).isEqualTo(visible);
                    assertThat(slot.container).isSameAs(inventory);
                    assertThat(slot.getContainerSlot()).isEqualTo(i < 27 ? i + 9 : i - 27);
                    assertThat(slot.index).isEqualTo(i);
                    assertThat(slot.getItem()).isSameAs(items.get(i));
                }
                assertThat(menu.getCarried()).isSameAs(carried);
            }
        }
    }

    @Test
    void reset_runs_once_only_for_a_bound_screen_on_a_visible_to_hidden_transition() throws Exception {
        var menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        TestScreen screen = TestScreen.create(menu);
        var visibility = new ControllerUiSlotVisibility(menu, menu.uiOpenData().sessionId(), () -> true,
                () -> true, () -> menu, () -> screen);
        visibility.setPlayerInventoryVisible(false);
        visibility.setPlayerInventoryVisible(false);
        visibility.setPlayerInventoryVisible(true);
        assertThat(screen.resets).isEqualTo(1);
        visibility.setPlayerInventoryVisible(false);
        assertThat(screen.resets).isEqualTo(2);

        var other = MachineControllerMenu.clientOpen(2, new Inventory(null, null));
        var unrelated = new ControllerUiSlotVisibility(other, other.uiOpenData().sessionId(), () -> true,
                () -> true, () -> other, () -> screen);
        unrelated.setPlayerInventoryVisible(false);
        assertThat(screen.resets).isEqualTo(2);
    }

    @Test
    void closed_replaced_and_wrong_token_updates_are_ignored_and_both_methods_require_main_thread() {
        var menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        var replacement = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        var current = new AtomicReference<AbstractContainerMenu>(menu);
        var open = new AtomicBoolean(true);
        var visibility = visibility(menu, current, open);
        current.set(replacement);
        visibility.setPlayerInventoryVisible(false);
        assertThat(menu.playerInventoryVisible()).isTrue();
        assertThat(replacement.playerInventoryVisible()).isTrue();
        current.set(menu);
        open.set(false);
        visibility.setPlayerInventoryVisible(false);
        assertThat(menu.playerInventoryVisible()).isTrue();
        new ControllerUiSlotVisibility(menu, UUID.randomUUID(), () -> true, () -> true, () -> menu, () -> null)
                .setPlayerInventoryVisible(false);
        assertThat(menu.playerInventoryVisible()).isTrue();
        var offThread = new ControllerUiSlotVisibility(menu, menu.uiOpenData().sessionId(), () -> false,
                () -> true, () -> menu, () -> null);
        assertThatThrownBy(offThread::isPlayerInventoryVisible).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> offThread.setPlayerInventoryVisible(false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void synchronous_factory_can_hide_before_installation_but_deferred_or_repeated_initialization_cannot() {
        var preceding = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        var menu = MachineControllerMenu.clientOpen(2, new Inventory(null, null));
        var current = new AtomicReference<AbstractContainerMenu>(preceding);
        var open = new AtomicBoolean(true);
        var visibility = visibility(menu, current, open);
        visibility.setPlayerInventoryVisible(false);
        assertThat(menu.playerInventoryVisible()).isTrue();
        assertThat(visibility.duringScreenConstruction(() -> {
            visibility.setPlayerInventoryVisible(false);
            return "screen";
        })).isEqualTo("screen");
        assertThat(menu.playerInventoryVisible()).isFalse();
        visibility.setPlayerInventoryVisible(true);
        assertThat(menu.playerInventoryVisible()).isFalse();
        assertThatThrownBy(() -> visibility.duringScreenConstruction(() -> null)).isInstanceOf(IllegalStateException.class);
        current.set(menu);
        visibility.setPlayerInventoryVisible(true);
        assertThat(menu.playerInventoryVisible()).isTrue();
        assertThat(visibility.duringScreenConstruction(() -> "restored screen")).isEqualTo("restored screen");
        current.set(preceding);
        visibility.setPlayerInventoryVisible(false);
        assertThat(menu.playerInventoryVisible()).isTrue();
        assertThat(preceding.playerInventoryVisible()).isTrue();
    }

    @Test
    void construction_scope_is_consumed_even_if_factory_throws() {
        var menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        var current = new AtomicReference<AbstractContainerMenu>();
        var visibility = visibility(menu, current, new AtomicBoolean(true));
        assertThatThrownBy(() -> visibility.duringScreenConstruction(() -> {
            throw new IllegalArgumentException("factory");
        })).isInstanceOf(IllegalArgumentException.class);
        visibility.setPlayerInventoryVisible(false);
        assertThat(menu.playerInventoryVisible()).isTrue();
        assertThatThrownBy(() -> visibility.duringScreenConstruction(() -> null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bound_menu_visibility_can_change_without_a_current_client_screen() {
        var menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        var visibility = new ControllerUiSlotVisibility(menu, menu.uiOpenData().sessionId(), () -> true,
                () -> true, () -> menu);
        visibility.setPlayerInventoryVisible(false);
        assertThat(menu.slots).allSatisfy(slot -> assertThat(slot.isActive()).isFalse());
        visibility.setPlayerInventoryVisible(true);
        assertThat(menu.slots).allSatisfy(slot -> assertThat(slot.isActive()).isTrue());
    }

    private static ControllerUiSlotVisibility visibility(AbstractContainerMenu menu,
            AtomicReference<AbstractContainerMenu> current, AtomicBoolean open) {
        return new ControllerUiSlotVisibility(menu, ((ControllerUiMenu) menu).uiOpenData().sessionId(), () -> true,
                open::get, current::get, () -> null);
    }

    private static void bind(Object deferred, MenuType<?> value) throws Exception {
        Class<?> type = deferred.getClass();
        while (type != null) {
            try {
                Field holder = type.getDeclaredField("holder");
                holder.setAccessible(true);
                holder.set(deferred, Holder.direct(value));
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException("holder");
    }

    private static final class TestScreen extends AbstractContainerScreen<MachineControllerMenu>
            implements ContainerScreenInputControl {
        private int resets;
        private TestScreen() { super(null, null, Component.empty()); }
        @Override public void resetControllerSlotInput() { resets++; }
        private static TestScreen create(MachineControllerMenu menu) throws Exception {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) field.get(null);
            TestScreen screen = (TestScreen) unsafe.allocateInstance(TestScreen.class);
            unsafe.putObject(screen, unsafe.objectFieldOffset(AbstractContainerScreen.class.getDeclaredField("menu")), menu);
            return screen;
        }
    }
}
