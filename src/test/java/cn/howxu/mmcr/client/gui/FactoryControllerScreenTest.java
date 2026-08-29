package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Factory controller screen behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryControllerScreenTest {
    private static final Identifier DETAIL_LEVEL_TYPE_ID = MMCR.id("factory_detail_level_type");
    private static final List<Identifier> DETAIL_LEVEL_IDS = List.of(
            MMCR.id("factory_detail_level_one"),
            MMCR.id("factory_detail_level_two"),
            MMCR.id("factory_detail_level_three"));

    @AfterEach
    void clearCache() {
        ControllerScreenTextCache.clear(BlockPos.ZERO);
    }

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
        LevelType levelType = new LevelType(DETAIL_LEVEL_TYPE_ID, Component.literal("Factory Detail Level"));
        TestBootstrap.registerType(levelType);
        for (int index = 0; index < DETAIL_LEVEL_IDS.size(); index++) {
            TestBootstrap.registerLevel(detailLevel(index));
        }
    }

    @Test
    void active_selected_thread_hides_aggregate_last_failure() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
         menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 1, 1L, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, true, false, true, "mmcr:recipe", 1, 20,
                        1, "", false, ""),
                        new FactoryRuntime.ThreadSnapshot(1, false, false, false, "", 0, 0, 1,
                                "", false, "")),
                "Factory", 0, new cn.howxu.mmcr.api.capability.status.ExecutionStatus(
                        cn.howxu.mmcr.MMCR.id("failure"),
                        cn.howxu.mmcr.api.capability.status.StatusSeverity.BLOCKED,
                        cn.howxu.mmcr.MMCR.id("crafting_runtime"),
                        java.util.Map.of("reason", "insufficient_resource")), List.of()));

        assertThat(FactoryControllerScreen.selectedFailureUnloc(menu)).isEmpty();
    }

    @Test
    void factory_detail_lines_preserve_snapshot_order() {
        FactoryControllerMenu menu = menuWithDetailRows();

        assertThat(FactoryControllerScreen.detailLines(menu)).containsExactly(
                new ControllerTextLine(MachineControllerScreen.levelLine(
                        detailLevel(0)), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.levelLine(
                        detailLevel(1)), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.levelLine(
                        detailLevel(2)), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.last_failure", Component.translatable("mmcr:selected_failure")),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.parallelSlotLine(2),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.parallelLine(4, 8),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.redstone_stopped"), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.threads", Component.literal("2"), Component.literal("3")),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.progress", "100%"), -1));
    }

    @Test
    void factory_screen_appends_external_lines_after_standard_lines() {
        FactoryControllerMenu menu = menuWithDetailRows();
        ControllerScreenTextCache.replace(BlockPos.ZERO, 1L,
                List.of(new ControllerScreenTextSnapshot.Line(ControllerScreenTextScope.CONTROLLER,
                        MMCR.id("factory_external"), Component.literal("external"))));

        List<ControllerTextLine> standard = FactoryControllerScreen.detailLines(menu);
        List<ControllerTextLine> composed = FactoryControllerScreen.controllerTextLines(menu);

        assertThat(composed.subList(0, standard.size())).containsExactlyElementsOf(standard);
        assertThat(composed.getLast()).isEqualTo(new ControllerTextLine(Component.literal("external"),
                ControllerScreenTextComposer.DEFAULT_EXTERNAL_COLOR));
        ControllerScreenTextCache.clear(BlockPos.ZERO);
    }

    @Test
    void factory_controller_viewport_wraps_long_external_text() throws Exception {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        ControllerScreenTextCache.replace(BlockPos.ZERO, 1L,
                List.of(new ControllerScreenTextSnapshot.Line(ControllerScreenTextScope.CONTROLLER,
                        MMCR.id("factory_long"), Component.literal("x".repeat(161)))));
        FactoryControllerScreen screen = (FactoryControllerScreen) unsafe().allocateInstance(FactoryControllerScreen.class);

        List<ControllerScreenTextComposer.VisualLine> visualLines = ControllerScreenTextComposer.wrap(
                ControllerScreenTextComposerTest.testFont(), FactoryControllerScreen.controllerTextLines(menu),
                screen.scrollableTextViewport().width());

        assertThat(visualLines).hasSizeGreaterThan(2);
        assertThat(visualLines.getLast().color()).isEqualTo(ControllerScreenTextComposer.DEFAULT_EXTERNAL_COLOR);
        ControllerScreenTextCache.clear(BlockPos.ZERO);
    }

    @Test
    void factory_detail_line_count_is_independent_from_thread_scroll_range() {
        FactoryControllerMenu menu = menuWithDetailRows();

        assertThat(FactoryControllerScreen.detailLines(menu)).hasSize(9);
        assertThat(FactoryControllerScreen.clampScrollOffset(99, menu.threads().size())).isZero();
    }

    @Test
    void factory_detail_rows_use_scaled_pose_coordinates() {
        assertThat(FactoryControllerScreen.detailTextY(34)).isEqualTo(40);
    }

    @Test
    void factory_detail_rows_keep_uniform_spacing_in_scaled_pose() {
        int statusY = 100;
        int firstDetailY = FactoryControllerScreen.detailLineY(statusY, 22, 32);
        int secondDetailY = FactoryControllerScreen.detailLineY(statusY, 22, 42);

        assertThat(firstDetailY - statusY).isEqualTo(10);
        assertThat(secondDetailY - firstDetailY).isEqualTo(10);
    }

    @Test
    void thread_scroll_hit_test_stays_separate_from_detail_viewport() {
        int left = 37;
        int top = 19;
        AbstractScrollableTextScreen.TextViewport viewport =
                new AbstractScrollableTextScreen.TextViewport(113, 32, 160, 92, 0.85F, 10);

        assertThat(FactoryControllerScreen.mouseOverThreadList(left, top,
                left + FactoryControllerScreen.THREAD_ROW_X + 1,
                top + FactoryControllerScreen.THREAD_ROW_Y + 1)).isTrue();
        assertThat(AbstractScrollableTextScreen.containsViewport(viewport, left, top,
                left + viewport.x(), top + viewport.y())).isTrue();
        assertThat(FactoryControllerScreen.mouseOverThreadList(left, top,
                left + viewport.x(), top + viewport.y())).isFalse();
    }

    private static FactoryControllerMenu menuWithDetailRows() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
         menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 3, 2, 8L, true,
                List.of(new FactoryRuntime.ThreadSnapshot(0, true, false, true, "mmcr:recipe", 20, 20,
                        4, "mmcr:selected_failure", false, "")),
                "Factory", 2, null, DETAIL_LEVEL_IDS.stream().map(Identifier::toString).toList()));
        return menu;
    }

    private static MachineLevel detailLevel(int index) {
        return new MachineLevel(DETAIL_LEVEL_IDS.get(index), DETAIL_LEVEL_TYPE_ID, index + 1,
                new BlockPredicate.OfBlockState(detailBlock(index).defaultBlockState()),
                ItemStack.EMPTY, LevelModifier.IDENTITY);
    }

    private static Block detailBlock(int index) {
        return switch (index) {
            case 0 -> Blocks.IRON_BLOCK;
            case 1 -> Blocks.GOLD_BLOCK;
            case 2 -> Blocks.DIAMOND_BLOCK;
            default -> throw new IllegalArgumentException("Unknown detail level: " + index);
        };
    }

    private static void bind(Object deferredHolder, MenuType<FactoryControllerMenu> menuType) throws Exception {
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
