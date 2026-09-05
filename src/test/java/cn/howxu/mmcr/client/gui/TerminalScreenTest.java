package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.item.TerminalAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies pure terminal screen state transitions.
 * @author howxu <dev@howxu.cn>
 */
class TerminalScreenTest {
    private static final Identifier TYPE_A = Identifier.parse("test:terminal_type_a");
    private static final Identifier TYPE_B = Identifier.parse("test:terminal_type_b");
    private static final Identifier LEVEL_A = Identifier.parse("test:terminal_level_a");
    private static final Identifier LEVEL_B = Identifier.parse("test:terminal_level_b");
    private static final Identifier LEVEL_B_ALT = Identifier.parse("test:terminal_level_b_alt");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        MachineLevelRegistry.installSnapshot(
                List.of(new LevelType(TYPE_A, Component.literal("Type A")),
                        new LevelType(TYPE_B, Component.literal("Type B"))),
                List.of(new MachineLevel(LEVEL_A, TYPE_A, 1,
                                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                                new ItemStack(Items.IRON_INGOT),
                                LevelModifier.IDENTITY),
                        new MachineLevel(LEVEL_B, TYPE_B, 1,
                                new BlockPredicate.OfBlockState(Blocks.GOLD_BLOCK.defaultBlockState()),
                                new ItemStack(Items.GOLD_INGOT),
                                LevelModifier.IDENTITY),
                        new MachineLevel(LEVEL_B_ALT, TYPE_B, 2,
                                new BlockPredicate.OfBlockState(Blocks.DIAMOND_BLOCK.defaultBlockState()),
                                new ItemStack(Items.DIAMOND),
                                LevelModifier.IDENTITY)));
    }

    @Test
    void level_controls_are_silent_when_machine_has_no_level_slots() {
        TerminalScreen.LevelView view = TerminalScreen.levelView(List.of(), null, Map.of());

        assertThat(view.typeButtonActive()).isFalse();
        assertThat(view.levelButtonActive()).isFalse();
        assertThat(view.slotStack().isEmpty()).isTrue();
    }

    @Test
    void level_view_uses_selected_type_before_map_insertion_order() {
        TerminalScreen.LevelView view = TerminalScreen.levelView(
                List.of(MachineLevelRegistry.getType(TYPE_B), MachineLevelRegistry.getType(TYPE_A)),
                TYPE_A, Map.of(TYPE_B, LEVEL_B, TYPE_A, LEVEL_A));

        assertThat(view.typeId()).isEqualTo(TYPE_A);
        assertThat(view.levelId()).isEqualTo(LEVEL_A);
    }

    @Test
    void invalid_selected_type_falls_back_to_first_available_type() {
        TerminalScreen.LevelView view = TerminalScreen.levelView(
                List.of(MachineLevelRegistry.getType(TYPE_A), MachineLevelRegistry.getType(TYPE_B)),
                Identifier.parse("test:missing_type"), Map.of(TYPE_A, LEVEL_A, TYPE_B, LEVEL_B));

        assertThat(view.typeId()).isEqualTo(TYPE_A);
        assertThat(view.levelId()).isEqualTo(LEVEL_A);
    }

    @Test
    void cross_type_selected_level_is_not_displayed_as_the_selected_type() {
        TerminalScreen.LevelView view = TerminalScreen.levelView(
                List.of(MachineLevelRegistry.getType(TYPE_A)), TYPE_A, Map.of(TYPE_A, LEVEL_B));

        assertThat(view.typeButtonActive()).isFalse();
        assertThat(view.levelButtonActive()).isFalse();
        assertThat(view.typeId()).isNull();
        assertThat(view.levelId()).isNull();
        assertThat(view.slotStack().isEmpty()).isTrue();
    }

    @Test
    void expired_selected_level_falls_back_to_another_valid_type_and_level() {
        TerminalScreen.LevelView view = TerminalScreen.levelView(
                List.of(MachineLevelRegistry.getType(TYPE_A), MachineLevelRegistry.getType(TYPE_B)), TYPE_A,
                Map.of(TYPE_A, Identifier.parse("test:expired_level"), TYPE_B, LEVEL_B));

        assertThat(view.typeId()).isEqualTo(TYPE_B);
        assertThat(view.levelId()).isEqualTo(LEVEL_B);
        assertThat(view.levelButtonActive()).isTrue();
    }

    @Test
    void unavailable_controller_disables_stage_and_layer_controls_but_reset_remains_explicit() {
        TerminalScreen.ControlState state = TerminalScreen.controlState(false, List.of(1), List.of(-2, 0));

        assertThat(state.stageActive()).isFalse();
        assertThat(state.layerActive()).isFalse();
        assertThat(state.resetActive()).isFalse();
    }

    @Test
    void empty_layer_list_disables_navigation_without_disabling_reset_semantics() {
        TerminalScreen.ControlState state = TerminalScreen.controlState(true, List.of(1, 2), List.of());

        assertThat(state.stageActive()).isTrue();
        assertThat(state.layerActive()).isFalse();
        assertThat(state.resetActive()).isTrue();
    }

    @Test
    void level_slot_is_after_the_level_button_in_the_narrow_row() {
        TerminalScreen.Layout layout = TerminalScreen.layout();

        assertThat(layout.levelButtonEnd()).isLessThanOrEqualTo(layout.slotX());
    }

    @Test
    void layer_plus_wraps_from_highest_to_lowest_and_r_wraps_to_all() {
        List<Integer> layers = List.of(-2, 0, 4);

        assertThat(TerminalScreen.nextLayer(4, layers)).isEqualTo(-2);
        assertThat(TerminalScreen.resetLayer()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void action_buttons_close_screen_but_configuration_buttons_do_not() {
        assertThat(TerminalScreen.closesAfter(TerminalAction.BUILD)).isTrue();
        assertThat(TerminalScreen.closesAfter(TerminalAction.SET_STAGE)).isFalse();
    }
}
