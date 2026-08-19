package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.gui.MachineControllerScreen;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.core.Holder;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the controller GUI opens recipes for its current machine only.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiPluginGuiHandlerTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER, new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void gui_handler_opens_only_its_machine_structure_display() {
        AtomicReference<IGuiContainerHandler<MachineControllerScreen>> handler = new AtomicReference<>();
        new JeiPlugin().registerGuiHandlers(registration(handler));
        IRecipesGui recipesGui = recipesGui();

        handler.get().getGuiClickableAreas(screenWith(MMCR.id("blast_furnace")), 10, 30)
                .forEach(area -> area.onClick(focusFactory(), recipesGui));

        assertThat(focuses.get()).hasSize(1);
        assertThat(focuses.get().getFirst().getRole()).isEqualTo(RecipeIngredientRole.INPUT);
    }

    @Test
    void gui_handler_has_no_click_area_without_a_resolved_machine() {
        AtomicReference<IGuiContainerHandler<MachineControllerScreen>> handler = new AtomicReference<>();
        new JeiPlugin().registerGuiHandlers(registration(handler));

        assertThat(handler.get().getGuiClickableAreas(screenWith(null), 10, 30)).isEmpty();
    }

    private final AtomicReference<List<IFocus<?>>> focuses = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    private static IGuiHandlerRegistration registration(AtomicReference<IGuiContainerHandler<MachineControllerScreen>> handler) {
        return (IGuiHandlerRegistration) Proxy.newProxyInstance(
                JeiPluginGuiHandlerTest.class.getClassLoader(),
                new Class<?>[]{IGuiHandlerRegistration.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addGuiContainerHandler")) {
                        handler.set((IGuiContainerHandler<MachineControllerScreen>) args[1]);
                        return null;
                    }
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("toString")) return "GuiHandlerRegistration";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private IRecipesGui recipesGui() {
        return (IRecipesGui) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IRecipesGui.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("show")) {
                        focuses.set(args[0] instanceof List<?> values
                                ? (List<IFocus<?>>) values
                                : List.of((IFocus<?>) args[0]));
                        return null;
                    }
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("toString")) return "RecipesGui";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private IFocusFactory focusFactory() {
        return (IFocusFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IFocusFactory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("createFocus")) {
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IFocus.class},
                                (focusProxy, focusMethod, focusArgs) -> switch (focusMethod.getName()) {
                                    case "getRole" -> args[0];
                                    case "getTypedValue" -> null;
                                    case "hashCode" -> System.identityHashCode(focusProxy);
                                    case "equals" -> focusProxy == focusArgs[0];
                                    case "toString" -> "Focus";
                                    default -> throw new UnsupportedOperationException(focusMethod.getName());
                                });
                    }
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("toString")) return "FocusFactory";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static MachineControllerScreen screenWith(Identifier machineId) {
        MachineControllerMenu menu = new MachineControllerMenu(1, new Inventory(null, null)) {
            @Override
            public Identifier machineId() {
                return machineId;
            }
        };
        try {
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineControllerScreen screen = (MachineControllerScreen) unsafe.allocateInstance(MachineControllerScreen.class);
            java.lang.reflect.Field menuField = AbstractContainerScreen.class.getDeclaredField("menu");
            menuField.setAccessible(true);
            menuField.set(screen, menu);
            return screen;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate menu screen", e);
        }
    }

    private static void bind(Object deferredHolder, MenuType<MachineControllerMenu> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        java.lang.reflect.Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }
}
