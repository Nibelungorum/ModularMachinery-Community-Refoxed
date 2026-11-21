package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.client.gui.AbstractScrollableTextScreen;
import cn.howxu.mmcr.client.gui.FactoryControllerScreen;
import cn.howxu.mmcr.client.gui.FluidHatchScreen;
import cn.howxu.mmcr.client.gui.ItemBusScreen;
import cn.howxu.mmcr.client.gui.MachineControllerScreen;
import cn.howxu.mmcr.client.gui.RecipePoolScreen;
import cn.howxu.mmcr.client.gui.StyledButton;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Tests the screen handlers MMCR actually registers with JEI.
 * @author HowXu {@literal <dev@howxu.cn>} */
class JeiGuiHandlersTest {
    private static final Map<Class<?>, Object> HANDLERS = new HashMap<>();
    private static Unsafe unsafe;

    @BeforeAll
    static void registerHandlers() throws Exception {
        TestBootstrap.bootstrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        new JeiPlugin().registerGuiHandlers((IGuiHandlerRegistration) Proxy.newProxyInstance(
                IGuiHandlerRegistration.class.getClassLoader(), new Class<?>[]{IGuiHandlerRegistration.class},
                (proxy, method, args) -> { HANDLERS.put((Class<?>) args[0], args[1]); return null; }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void controller_and_port_exclusions_follow_only_visible_sidebar_buttons() throws Exception {
        assertThat(HANDLERS.keySet()).noneMatch(type -> type.isAssignableFrom(InventoryScreen.class));
        for (var type : List.of(MachineControllerScreen.class, FactoryControllerScreen.class,
                ItemBusScreen.class, FluidHatchScreen.class)) {
            var screen = (AbstractScrollableTextScreen<?>) unsafe.allocateInstance(type);
            var handler = (IGuiContainerHandler<AbstractScrollableTextScreen<?>>) HANDLERS.entrySet().stream()
                    .filter(entry -> entry.getKey().isInstance(screen)).findFirst().orElseThrow().getValue();
            boolean controller = screen instanceof MachineControllerScreen || screen instanceof FactoryControllerScreen;
            int width = screen instanceof FactoryControllerScreen ? 280 : 176;
            int left = 50;
            int x = controller ? left + width + 2 : left - 18;
            var button = new StyledButton(x, 41, 16, 16, Component.empty(), ignored -> {});
            var other = new StyledButton(x, 59, 16, 16, Component.empty(), ignored -> {});
            var inside = new StyledButton(left + 8, 50, 16, 16, Component.empty(), ignored -> {});
            button.active = false; // Disabled but still drawn.
            other.visible = false;
            setField(screen, AbstractContainerScreen.class, "leftPos", left);
            setField(screen, AbstractContainerScreen.class, "imageWidth", width);
            setField(screen, Screen.class, "children", List.of(button, other, inside));
            assertThat(handler.getGuiExtraAreas(screen)).extracting(Rect2i::getX, Rect2i::getY, Rect2i::getWidth, Rect2i::getHeight)
                    .containsExactly(tuple(x, 41, 16, 16));

            // The new sidebar is outside the new panel, but inside the previous panel bounds.
            int shift = controller ? -25 : 25;
            setField(screen, AbstractContainerScreen.class, "leftPos", left + shift);
            inside.setPosition(left + shift + 8, 37);
            button.visible = false;
            other.visible = true;
            other.setPosition(x + shift, 28);
            assertThat(handler.getGuiExtraAreas(screen)).extracting(Rect2i::getX, Rect2i::getY, Rect2i::getWidth, Rect2i::getHeight)
                    .containsExactly(tuple(x + shift, 28, 16, 16));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void recipe_pool_handler_tracks_the_centered_panel_when_gui_scale_changes() throws Exception {
        var handler = (IScreenHandler<RecipePoolScreen>) HANDLERS.get(RecipePoolScreen.class);
        assertThat(handler).isNotNull();
        var screen = (RecipePoolScreen) unsafe.allocateInstance(RecipePoolScreen.class);
        for (int[] size : List.of(new int[]{427, 291}, new int[]{320, 240})) {
            screen.width = size[0];
            screen.height = size[1];
            var bounds = handler.apply(screen);
            int left = (screen.width - 108) / 2;
            int top = (screen.height - 139) / 2;
            assertThat(bounds.screenClass()).isEqualTo(RecipePoolScreen.class);
            assertThat(List.of(bounds.guiLeft(), bounds.guiTop(), bounds.guiRight(), bounds.guiBottom()))
                    .containsExactly(left, top, left + 108, top + 139);
            assertThat(List.of(bounds.screenWidth(), bounds.screenHeight())).containsExactly(screen.width, screen.height);
        }
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
