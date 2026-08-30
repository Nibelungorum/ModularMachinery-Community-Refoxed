package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Machine controller screen text composition tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerScreenTest {
    private static final BlockPos CONTROLLER_POS = new BlockPos(11, 22, 33);
    private static final Identifier TICK_MACHINE_ID = Identifier.parse("mmcr:screen_tick_machine");
    private static final ExecutionStatus FAILURE = new ExecutionStatus(
            MMCR.id("screen_tick_failure"), StatusSeverity.BLOCKED, MMCR.id("screen_tick_controller"),
            Map.of("reason", "insufficient_energy"));

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER,
                new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
        MachineRegistry.register(screenTickMachine());
    }

    @AfterEach
    void clearCache() {
        ControllerScreenTextCache.clear(CONTROLLER_POS);
    }

    @Test
    void newer_cache_snapshot_replaces_external_machine_screen_lines() {
        MachineControllerMenu menu = new MachineControllerMenu(1, new Inventory(null, null), CONTROLLER_POS);

        ControllerScreenTextCache.replace(CONTROLLER_POS, 1L, List.of(line("test:first", "first")));
        assertThat(MachineControllerScreen.controllerTextLines(menu))
                .extracting(ControllerTextLine::text)
                .containsExactly(
                        Component.translatable("gui.mmcr.controller.status_label")
                                .append(Component.literal(" "))
                                .append(Component.translatable("gui.mmcr.controller.unformed")),
                        Component.literal("first"));

        ControllerScreenTextCache.replace(CONTROLLER_POS, 2L, List.of(line("test:second", "second")));
        assertThat(MachineControllerScreen.controllerTextLines(menu))
                .extracting(ControllerTextLine::text)
                .containsExactly(
                        Component.translatable("gui.mmcr.controller.status_label")
                                .append(Component.literal(" "))
                                .append(Component.translatable("gui.mmcr.controller.unformed")),
                        Component.literal("second"));
    }

    @Test
    void controller_status_is_first_scrollable_detail_line() {
        MachineControllerMenu menu = new MachineControllerMenu(1, new Inventory(null, null), CONTROLLER_POS);

        assertThat(MachineControllerScreen.detailLines(menu).getFirst()).isEqualTo(
                new ControllerTextLine(Component.translatable("gui.mmcr.controller.status_label")
                        .append(Component.literal(" "))
                        .append(Component.translatable("gui.mmcr.controller.unformed")),
                        MachineControllerScreen.UNFORMED_STATUS_COLOR));
    }

    @Test
    void pure_tick_controller_hides_recipe_runtime_details() {
        MachineControllerMenu menu = menuWithState(TICK_MACHINE_ID);

        assertThat(menu.isTickMachine()).isTrue();
        assertThat(MachineControllerScreen.detailLines(menu))
                .extracting(ControllerTextLine::text)
                .containsExactly(Component.translatable("gui.mmcr.controller.status_label")
                        .append(Component.literal(" "))
                        .append(Component.translatable("gui.mmcr.controller.running")));
    }

    @Test
    void recipe_controller_keeps_recipe_runtime_details() {
        MachineControllerMenu menu = menuWithState(MMCR.id("test_cube"));

        assertThat(menu.isTickMachine()).isFalse();
        assertThat(MachineControllerScreen.detailLines(menu))
                .extracting(ControllerTextLine::text)
                .contains(
                        Component.translatable("gui.mmcr.controller.last_failure",
                                Component.translatable("gui.mmcr.controller.failure.missing_energy")),
                        MachineControllerScreen.parallelSlotLine(2),
                        MachineControllerScreen.parallelLine(6, 8),
                        Component.translatable("gui.mmcr.controller.progress", "20%"));
    }

    @Test
    void ordinary_controller_viewport_wraps_long_external_text() throws Exception {
        MachineControllerMenu menu = new MachineControllerMenu(1, new Inventory(null, null), CONTROLLER_POS);
        ControllerScreenTextCache.replace(CONTROLLER_POS, 1L,
                List.of(line("test:long", "x".repeat(161))));
        MachineControllerScreen screen = (MachineControllerScreen) unsafe().allocateInstance(MachineControllerScreen.class);

        List<ControllerScreenTextComposer.VisualLine> visualLines = ControllerScreenTextComposer.wrap(
                ControllerScreenTextComposerTest.testFont(), MachineControllerScreen.controllerTextLines(menu),
                screen.scrollableTextViewport().width());

        assertThat(visualLines).hasSize(3);
        assertThat(visualLines.getFirst().color()).isEqualTo(MachineControllerScreen.UNFORMED_STATUS_COLOR);
        assertThat(visualLines.subList(1, visualLines.size())).allSatisfy(line ->
                assertThat(line.color()).isEqualTo(ControllerScreenTextComposer.DEFAULT_EXTERNAL_COLOR));
    }

    private static ControllerScreenTextSnapshot.Line line(String id, String text) {
        return new ControllerScreenTextSnapshot.Line(ControllerScreenTextScope.CONTROLLER,
                Identifier.parse(id), Component.literal(text));
    }

    private static MachineControllerMenu menuWithState(Identifier machineId) {
        MachineControllerMenu menu = new MachineControllerMenu(1, new Inventory(null, null), CONTROLLER_POS,
                machineId, null, 0, true, 0);
        menu.applyClientSnapshot(new PktMachineStatePayload(
                CONTROLLER_POS, "mmcr:recipe", true, true, List.of(), true, "mmcr:locked_recipe",
                machineId.toString(), 0, 0, false, "", CraftingStatus.Status.CRAFTING, "", FAILURE,
                true, false, 4, 20, 6, 8, false, 0, 0, 2, 3, 0, 0,
                FluidStack.EMPTY, FluidStack.EMPTY));
        return menu;
    }

    private static Machine screenTickMachine() {
        return new Machine() {
            @Override
            public Identifier registryName() {
                return TICK_MACHINE_ID;
            }

            @Override
            public BlockArray pattern() {
                return new BlockArray(Map.of());
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(TICK_MACHINE_ID);
            }

            @Override
            public TickBehavior behavior() {
                return TickBehavior.defaults();
            }
        };
    }

    private static void bind(Object deferredHolder, MenuType<MachineControllerMenu> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
