package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
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

    @Test
    void changing_the_export_limit_can_admit_a_previously_rejected_selection() throws ReflectiveOperationException {
        long volume = ExportCommand.volume(BlockPos.ZERO, new BlockPos(255, 63, 255));
        int exactLimit = Math.toIntExact(volume);
        boolean loaded = ServerConfig.SPEC.isLoaded();
        ConfigTestSupport.ensureServerConfigLoaded();
        int previous = ServerConfig.EXPORT_MAX_VOLUME.getAsInt();
        try {
            ServerConfig.EXPORT_MAX_VOLUME.set(exactLimit - 1);
            assertThat(ExportCommand.exportVolumeError(volume)).isEqualTo(
                    Component.translatable("command.mmcr.export.volume_exceeded", volume, exactLimit - 1));

            ServerConfig.EXPORT_MAX_VOLUME.set(exactLimit);
            assertThat(ExportCommand.exportVolumeError(volume)).isNull();
        } finally {
            if (loaded) ServerConfig.EXPORT_MAX_VOLUME.set(previous);
            else ServerConfig.SPEC.acceptConfig(null);
        }
    }

    @Test
    void rejects_extreme_regions_without_overflowing_their_volume() {
        BlockPos min = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        BlockPos max = new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertThat(ExportCommand.volume(min, max)).isEqualTo(Long.MAX_VALUE);
        assertThat(ExportCommand.exportVolumeError(ExportCommand.volume(min, max))).isNotNull();
    }
}
