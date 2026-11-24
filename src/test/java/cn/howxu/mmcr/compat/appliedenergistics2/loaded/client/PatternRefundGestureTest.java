package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.client.gui.WidgetContainer;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercise AE and vanilla mouse dispatch without a graphics context or a connected player.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class PatternRefundGestureTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void aGestureStartingOnTheToolbarCannotDropOrDistributeCarriedItems() throws Exception {
        var access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        var unsafe = (Unsafe) access.get(null);
        var screen = (PatternInterfaceScreen) unsafe.allocateInstance(PatternInterfaceScreen.class);
        var menu = (PatternInterfaceMenu) unsafe.allocateInstance(PatternInterfaceMenu.class);
        var client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        var singleton = Minecraft.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        var previous = singleton.get(null);
        try {
            singleton.set(null, client);
            var calls = new AtomicInteger();
            var button = new PatternRefundButton(calls::incrementAndGet);
            button.setDisableClickSound(true);
            button.setPosition(4, 5);
            set(screen, "minecraft", client);
            set(screen, "menu", menu);
            set(screen, "children", List.of(button));
            set(screen, "widgets", new WidgetContainer(null));
            set(screen, "drag_click", new HashSet<>());
            set(screen, "refundButton", button);
            var carried = new ItemStack(Items.DIAMOND, 5);
            menu.setCarried(carried);
            for (int key : List.of(0, 1)) {
                var press = new MouseButtonEvent(8, 8, new MouseButtonInfo(key, 0));
                var outside = new MouseButtonEvent(-50, -50, press.buttonInfo());
                assertThat(screen.mouseClicked(press, false)).isTrue();
                assertThat(screen.mouseDragged(outside, -58, -58)).isTrue();
                assertThat(screen.mouseReleased(outside)).isTrue();
                assertThat(menu.getCarried()).isSameAs(carried);
                assertThat(menu.getCarried().getCount()).isEqualTo(5);
            }
            assertThat(calls.get()).isOne();
        } finally {
            singleton.set(null, previous);
        }
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
