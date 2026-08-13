package cn.howxu.mmcr.internal.autoio;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class AutoIOConfigTest {

    @Test
    void direction_bitmask_round_trips_all_sides() {
        EnumSet<Direction> sides = EnumSet.of(Direction.NORTH, Direction.EAST, Direction.DOWN);

        int mask = AutoIOConfig.toMask(sides);

        assertThat(AutoIOConfig.fromMask(mask)).containsExactlyInAnyOrder(Direction.NORTH, Direction.EAST, Direction.DOWN);
    }

    @Test
    void config_save_load_preserves_enabled_and_sides() {
        AutoIOConfig config = new AutoIOConfig();
        config.setEnabled(true);
        config.toggleSide(Direction.NORTH);
        config.toggleSide(Direction.SOUTH);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));

        config.save(output);
        AutoIOConfig loaded = AutoIOConfig.load(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(loaded.enabled()).isTrue();
        assertThat(loaded.enabledSides()).containsExactlyInAnyOrder(Direction.NORTH, Direction.SOUTH);
    }

    @Test
    void toggling_side_is_symmetric() {
        AutoIOConfig config = new AutoIOConfig();

        config.toggleSide(Direction.WEST);
        config.toggleSide(Direction.WEST);

        assertThat(config.enabledSides()).isEmpty();
    }
}
