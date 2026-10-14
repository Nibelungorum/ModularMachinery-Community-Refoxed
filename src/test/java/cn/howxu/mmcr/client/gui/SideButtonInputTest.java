package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the shared screen through vanilla widget and container mouse dispatch.
 *
 * @author howxu <dev@howxu.cn>
 */
class SideButtonInputTest {
    private Unsafe unsafe;
    private Minecraft minecraft;
    private Minecraft previousMinecraft;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void installClientFixture() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        Options options = (Options) unsafe.allocateInstance(Options.class);
        KeyMapping pickItem = (KeyMapping) unsafe.allocateInstance(KeyMapping.class);
        // Nonmatching middle mouse binding short-circuits NeoForge modifier/window checks.
        setField(pickItem, "key", InputConstants.Type.MOUSE.getOrCreate(2));
        setField(options, "keyPickItem", pickItem);
        setField(options, "touchscreen", OptionInstance.createBoolean("options.touchscreen", false));
        setField(minecraft, "options", options);
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousMinecraft = (Minecraft) instance.get(null);
        instance.set(null, minecraft);
    }

    @AfterEach
    void restoreClient() throws Exception {
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousMinecraft);
    }

    @Test
    void sidebar_press_drag_and_release_preserve_carried_items_and_slots() throws Exception {
        // Port navigation on the left; normal/factory M and C rows on the right.
        for (int[] layout : List.of(new int[]{176, -18, 1}, new int[]{176, 178, 1},
                new int[]{176, 178, 19}, new int[]{280, 282, 1}, new int[]{280, 282, 19})) {
            FixtureScreen screen = screen(layout[0]);
            var presses = new AtomicInteger();
            StyledButton sidebar = screen.addButton(50 + layout[1], 40 + layout[2], button -> {
                presses.incrementAndGet();
                button.setFocused(false); // Reproduce the original callback-only focus clearing.
            });
            double pressX = sidebar.getX() + 5;
            double pressY = sidebar.getY() + 5;
            ItemStack carried = screen.getMenu().getCarried();

            assertThat(screen.mouseClicked(pressX, pressY, 0)).isTrue();
            assertThat(presses.get()).isEqualTo(1);
            assertThat(screen.getFocused()).isNull();
            assertThat(sidebar.isFocused()).isFalse();
            assertThat(screen.isDragging()).isFalse();
            assertThat(screen.mouseDragged(5, 5, 0, -100, -100)).isTrue();
            assertThat(screen.mouseReleased(5, 5, 0)).isTrue();
            assertThat(screen.clicks).isEmpty();

            assertThat(screen.mouseClicked(pressX, pressY, 0)).isTrue();
            assertThat(screen.mouseDragged(65, 125, 0, 30, 80)).isTrue();
            assertThat(screen.mouseReleased(65, 125, 0)).isTrue();
            assertThat(screen.clicks).isEmpty();
            assertThat(screen.hasQuickCraftSlots()).isFalse();
            assertThat(screen.getMenu().getCarried()).isSameAs(carried);
            assertThat(carried.getCount()).isEqualTo(32);
            assertThat(screen.getMenu().getSlot(0).getItem().isEmpty()).isTrue();

            assertNormalSlotGesture(screen);
        }
    }

    @Test
    void hiding_the_pressed_navigation_button_does_not_release_into_the_container() throws Exception {
        FixtureScreen screen = screen(176);
        StyledButton sidebar = screen.addButton(32, 59, button -> {
            button.visible = false;
            button.active = false;
        });

        assertThat(screen.mouseClicked(37, 64, 0)).isTrue();
        assertThat(sidebar.visible).isFalse();
        assertThat(screen.getFocused()).isNull();
        assertThat(sidebar.isFocused()).isFalse();
        assertThat(screen.mouseDragged(65, 125, 0, 28, 61)).isTrue();
        assertThat(screen.mouseReleased(65, 125, 0)).isTrue();
        assertThat(screen.clicks).isEmpty();
        assertNormalSlotGesture(screen);
    }

    @Test
    void hidden_or_disabled_sidebar_buttons_leave_outside_input_to_vanilla() throws Exception {
        for (boolean hidden : List.of(true, false)) {
            FixtureScreen screen = screen(176);
            var presses = new AtomicInteger();
            StyledButton sidebar = screen.addButton(229, 40, button -> presses.incrementAndGet());
            sidebar.visible = !hidden;
            sidebar.active = hidden;

            // Vanilla returns true for container input, so inspect the actual slot action.
            assertThat(screen.mouseClicked(234, 45, 0)).isTrue();
            assertThat(screen.mouseDragged(240, 50, 0, 6, 5)).isTrue();
            screen.mouseReleased(240, 50, 0);
            assertThat(presses.get()).isZero();
            assertThat(screen.clicks).containsExactly(new SlotClick(null, -999, 0, ClickType.PICKUP));
        }
    }

    @Test
    void a_new_slot_press_recovers_when_the_sidebar_release_went_to_a_subpage() throws Exception {
        FixtureScreen screen = screen(176);
        screen.addButton(229, 40, button -> {});
        assertThat(screen.mouseClicked(234, 45, 0)).isTrue();

        // M changes Screen during onPress; its release is delivered to the new page.
        assertNormalSlotGesture(screen);
    }

    @Test
    void reinitializing_the_parent_discards_the_unfinished_sidebar_gesture() throws Exception {
        FixtureScreen screen = screen(176);
        screen.addButton(229, 40, button -> {});
        assertThat(screen.mouseClicked(234, 45, 0)).isTrue();

        screen.init();
        // A release after re-init must reach vanilla, rather than retain the old capture.
        screen.mouseReleased(5, 5, 0);
        assertThat(screen.clicks).containsExactly(new SlotClick(null, -999, 0, ClickType.PICKUP));
    }

    @Test
    void internal_buttons_keep_their_existing_focus_and_container_release_behavior() throws Exception {
        FixtureScreen screen = screen(176);
        StyledButton internal = screen.addButton(80, 50, button -> {});

        assertThat(screen.mouseClicked(85, 55, 0)).isTrue();
        assertThat(screen.getFocused()).isSameAs(internal);
        assertThat(internal.isFocused()).isTrue();
        screen.mouseReleased(5, 5, 0);
        assertThat(screen.clicks).containsExactly(new SlotClick(null, -999, 0, ClickType.PICKUP));
    }

    @Test
    void cleared_mouse_focus_does_not_remove_keyboard_navigation_or_activation() throws Exception {
        FixtureScreen screen = screen(176);
        var presses = new AtomicInteger();
        StyledButton sidebar = screen.addButton(229, 40, button -> presses.incrementAndGet());
        screen.mouseClicked(234, 45, 0);
        screen.mouseReleased(234, 45, 0);
        assertThat(screen.getFocused()).isNull();

        var focus = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(true));
        assertThat(focus).isNotNull();
        focus.applyFocus(true);
        assertThat(screen.getFocused()).isSameAs(sidebar);
        assertThat(sidebar.isFocused()).isTrue();
        assertThat(screen.keyPressed(32, 0, 0)).isTrue();
        assertThat(presses.get()).isEqualTo(2);
        assertThat(screen.clicks).isEmpty();
        assertNormalSlotGesture(screen);
    }

    private FixtureScreen screen(int imageWidth) throws Exception {
        FixtureScreen screen = (FixtureScreen) unsafe.allocateInstance(FixtureScreen.class);
        setField(screen, "minecraft", minecraft);
        setField(screen, "menu", new FixtureMenu());
        setField(screen, "children", new ArrayList<>());
        setField(screen, "renderables", new ArrayList<>());
        setField(screen, "narratables", new ArrayList<>());
        setField(screen, "quickCraftSlots", new HashSet<>());
        setField(screen, "imageWidth", imageWidth);
        setField(screen, "imageHeight", 166);
        setField(screen, "leftPos", 50);
        setField(screen, "topPos", 40);
        screen.width = imageWidth + 100;
        screen.height = 246;
        screen.clicks = new ArrayList<>();
        // Model a completed slot pickup, not constructor-time skipNextRelease=true.
        setField(screen, "skipNextRelease", false);
        screen.getMenu().setCarried(new ItemStack(Items.STONE, 32));
        return screen;
    }

    private static void assertNormalSlotGesture(FixtureScreen screen) {
        // 1.21.1's single-slot release polls GLFW for Shift. A real quick-craft
        // gesture exercises slot dispatch without requiring a native client window.
        assertThat(screen.mouseClicked(65, 125, 0)).isTrue();
        assertThat(screen.mouseDragged(65, 125, 0, 0, 0)).isTrue();
        screen.mouseReleased(65, 125, 0);
        assertThat(screen.clicks).containsExactly(
                new SlotClick(null, -999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT),
                new SlotClick(screen.getMenu().getSlot(0), 0,
                        AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT),
                new SlotClick(null, -999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** @author howxu <dev@howxu.cn> */
    private record SlotClick(Slot slot, int index, int button, ClickType input) {
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class FixtureScreen extends AbstractScrollableTextScreen<FixtureMenu> {
        private List<SlotClick> clicks;

        private FixtureScreen(FixtureMenu menu, Inventory inventory) {
            super(menu, inventory, Component.empty(), 176, 166);
        }

        StyledButton addButton(int x, int y, Button.OnPress action) {
            return addRenderableWidget(new SilentStyledButton(x, y, action));
        }

        boolean hasQuickCraftSlots() {
            return !quickCraftSlots.isEmpty();
        }

        @Override
        protected TextViewport scrollableTextViewport() {
            return new TextViewport(9, 24, 160, 100, 0.85F, 10);
        }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            // Input-only fixture; no rendering context is installed.
        }

        @Override
        protected void slotClicked(Slot slot, int index, int button, ClickType input) {
            // Observe vanilla's operation boundary without requiring a networked player.
            clicks.add(new SlotClick(slot, index, button, input));
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class SilentStyledButton extends StyledButton {
        private SilentStyledButton(int x, int y, OnPress action) {
            super(x, y, 16, 16, Component.empty(), action);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            // Only audio is bypassed; mouse, focus, onPress and keyboard dispatch are real.
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class FixtureMenu extends AbstractContainerMenu {
        private FixtureMenu() {
            super(null, 1);
            addSlot(new Slot(new SimpleContainer(1), 0, 10, 80));
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
