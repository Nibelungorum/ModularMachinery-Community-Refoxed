package cn.howxu.mmcr.internal.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class DirectSignalPositionTest {
    @Test
    void cached_neighbors_preserve_native_direct_signal_results_directions_and_short_circuit() {
        BlockPos origin = new BlockPos(17, 64, -33);
        DirectSignalPosition cached = new DirectSignalPosition(origin);
        TestSignals signals = new TestSignals();
        for (Direction direction : Direction.values()) {
            for (int strength : new int[]{0, 1, 7, 15}) {
                signals.strengths.clear();
                signals.strengths.put(new SignalRead(origin.relative(direction), direction), strength);
                signals.reads.clear();
                int expected = signals.getDirectSignalTo(origin);
                List<SignalRead> expectedReads = List.copyOf(signals.reads);
                signals.reads.clear();

                assertThat(signals.getDirectSignalTo(cached)).isEqualTo(expected);
                assertThat(signals.reads).containsExactlyElementsOf(expectedReads);
            }
        }
    }

    @Test
    void signal_changes_remain_live_and_retained_positions_are_immutable_and_reused() {
        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos(17, 64, -33);
        BlockPos original = origin.immutable();
        DirectSignalPosition cached = new DirectSignalPosition(origin);
        TestSignals signals = new TestSignals();
        assertThat(signals.getDirectSignalTo(cached)).isZero();
        List<SignalRead> retained = List.copyOf(signals.reads);
        origin.set(90, 10, 90);
        signals.strengths.put(new SignalRead(original.east(), Direction.EAST), 7);
        signals.reads.clear();

        assertThat(signals.getDirectSignalTo(cached)).isEqualTo(7);
        for (int index = 0; index < retained.size(); index++) {
            BlockPos position = retained.get(index).position();
            assertThat(position).isEqualTo(original.relative(retained.get(index).direction()));
            assertThat(position).isNotInstanceOf(BlockPos.MutableBlockPos.class);
            assertThat(signals.reads.get(index).position()).isSameAs(position);
        }
        signals.strengths.clear();
        assertThat(signals.getDirectSignalTo(cached)).isZero();
    }

    /** @author howxu <dev@howxu.cn> */
    private record SignalRead(BlockPos position, Direction direction) { }

    /** Uses the real SignalGetter default helper, with independently supplied directional signals.
     * @author howxu <dev@howxu.cn> */
    private static final class TestSignals implements SignalGetter {
        private final Map<SignalRead, Integer> strengths = new HashMap<>();
        private final List<SignalRead> reads = new ArrayList<>();

        @Override
        public int getDirectSignal(BlockPos position, Direction direction) {
            SignalRead read = new SignalRead(position, direction);
            reads.add(read);
            return strengths.getOrDefault(read, 0);
        }

        @Override public int getSignal(BlockPos position, Direction direction) {
            throw new AssertionError("Direct-signal query must not use weak power");
        }
        @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
        @Override public BlockState getBlockState(BlockPos position) { throw new AssertionError("Unexpected state lookup"); }
        @Override public FluidState getFluidState(BlockPos position) { throw new AssertionError("Unexpected fluid lookup"); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
