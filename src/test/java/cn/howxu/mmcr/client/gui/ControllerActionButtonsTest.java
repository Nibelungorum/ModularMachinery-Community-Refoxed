package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.HeaderData;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both built-in controller screens compact visible action rows after snapshot changes.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerActionButtonsTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void both_screens_compact_and_restore_actions_after_pool_changes() throws Exception {
        for (Class<?> type : List.of(MachineControllerScreen.class, FactoryControllerScreen.class)) {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Unsafe unsafe = (Unsafe) field.get(null);
            Object screen = unsafe.allocateInstance(type);
            var current = new AtomicReference<ControllerUiSnapshot>();
            Supplier<ControllerUiSnapshot> snapshots = current::get;
            unsafe.putObject(screen, unsafe.objectFieldOffset(type.getDeclaredField("snapshot")), snapshots);
            var poolButton = new StyledButton(0, 0, 16, 16, Component.empty(), button -> {});
            var checkButton = new StyledButton(0, 0, 16, 16, Component.empty(), button -> {});
            var isolation = InputIsolationMenuState.forController(null);
            isolation.dataSlot().set(1);
            Object menu = unsafe.allocateInstance(type == MachineControllerScreen.class
                    ? MachineControllerMenu.class : FactoryControllerMenu.class);
            setField(menu, "inputIsolation", isolation);
            setField(screen, "menu", menu);
            var isolationButton = new IsolationToggleButton(0, 0, Component.empty(), isolation::enabled, button -> {});
            setField(screen, "recipePoolButton", poolButton);
            setField(screen, "structureCheckButton", checkButton);
            setField(screen, "isolationButton", isolationButton);
            setField(screen, "leftPos", 50);
            setField(screen, "topPos", 40);
            int backgroundWidth = type == MachineControllerScreen.class ? 176 : 280;
            setField(screen, "imageWidth", backgroundWidth);
            var update = type.getDeclaredMethod("updateControllerButtons");
            update.setAccessible(true);
            var first = MMCR.id("pool_a");
            var second = MMCR.id("pool_b");
            for (List<ResourceLocation> pools : List.of(List.of(first, second), List.of(first), List.<ResourceLocation>of(),
                    List.of(first, second))) {
                current.set(snapshot(type == MachineControllerScreen.class
                        ? ControllerUiSnapshot.Kind.NORMAL : ControllerUiSnapshot.Kind.FACTORY, pools));
                update.invoke(screen);
                boolean showPools = pools.size() > 1;
                assertThat(poolButton.visible).isEqualTo(showPools);
                assertThat(poolButton.active).isEqualTo(showPools);
                assertThat(checkButton.visible).isTrue();
                assertThat(checkButton.active).isTrue();
                assertThat(isolationButton.visible).isTrue();
                assertThat(isolationButton.active).isTrue();
                assertThat(isolationButton.getX()).isEqualTo(checkButton.getX());
                assertThat(isolationButton.getY()).isEqualTo(checkButton.getY() + 18);
                assertThat(checkButton.getX()).isEqualTo(poolButton.getX());
                assertThat(checkButton.getX()).isEqualTo(50 + backgroundWidth + 2);
                assertThat(poolButton.getY()).isEqualTo(41);
                if (showPools) {
                    assertThat(checkButton.getY()).isEqualTo(poolButton.getY() + poolButton.getHeight() + 2);
                } else {
                    assertThat(checkButton.getY()).isEqualTo(poolButton.getY());
                }
            }
        }
    }

    private static ControllerUiSnapshot snapshot(ControllerUiSnapshot.Kind kind, List<ResourceLocation> pools) {
        return new ControllerUiSnapshotData(UUID.randomUUID(), 1, true, Level.OVERWORLD, BlockPos.ZERO,
                new HeaderData(MMCR.id("side_button_fixture"), kind,
                        ControllerUiSnapshot.Role.NORMAL, Component.empty(), false, false, false,
                        0, null, 0, 1, List.of(), 0, 1, 0, 0, pools,
                        pools.isEmpty() ? null : pools.getFirst(), null, false, Map.of(), List.of()), List.of());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
