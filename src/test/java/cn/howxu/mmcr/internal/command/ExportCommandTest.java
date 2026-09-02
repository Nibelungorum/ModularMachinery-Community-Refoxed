package cn.howxu.mmcr.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies export command registration and detector selection rules.
 * @author howxu <dev@howxu.cn>
 */
class ExportCommandTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void registers_java_and_kubejs_export_commands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();

        ExportCommand.register(dispatcher);

        var mmcr = dispatcher.getRoot().getChild("mmcr");
        assertThat(mmcr).isNotNull();
        var export = mmcr.getChild("export");
        assertThat(export).isNotNull();
        assertThat(export.getChildren()).extracting(command -> command.getName())
                .containsExactlyInAnyOrder("java", "kjs");
    }

    @Test
    void command_selection_requires_exactly_one_detector() {
        assertThat(ExportCommand.detectorSelectionAllowed(true, false, false)).isTrue();
        assertThat(ExportCommand.detectorSelectionAllowed(false, true, false)).isTrue();
        assertThat(ExportCommand.detectorSelectionAllowed(false, false, false)).isFalse();
        assertThat(ExportCommand.detectorSelectionAllowed(true, true, false)).isFalse();
    }

    @Test
    void screen_selection_requires_a_main_hand_detector() {
        assertThat(ExportCommand.detectorSelectionAllowed(true, false, true)).isTrue();
        assertThat(ExportCommand.detectorSelectionAllowed(true, true, true)).isTrue();
        assertThat(ExportCommand.detectorSelectionAllowed(false, true, true)).isFalse();
        assertThat(ExportCommand.detectorSelectionAllowed(false, false, true)).isFalse();
    }
}
