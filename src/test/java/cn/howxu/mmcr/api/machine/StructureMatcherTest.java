package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StructureMatcherTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void matches_compiled_uses_pre_rotated_pattern_for_facing() {
        BlockArray pattern = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        DynamicMachine machine = new DynamicMachine(net.minecraft.resources.Identifier.fromNamespaceAndPath("mmcr", "matcher_compiled"), "Matcher Compiled", pattern);
        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        BlockPos controllerPos = new BlockPos(32, 64, 32);
        Level level = LevelStub.create(Map.of(controllerPos.offset(0, 0, 1), Blocks.STONE));

        assertThat(StructureMatcher.matchesCompiled(compiled, Direction.WEST, level, controllerPos)).isTrue();
    }

    @Test
    void first_mismatch_returns_relative_position_and_actual_state() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.FURNACE)));
        Level level = LevelStub.create(Map.of(
                new BlockPos(0, 0, 0), Blocks.STONE,
                new BlockPos(1, 0, 0), Blocks.DIRT));

        Optional<StructureMatcher.Mismatch> mismatch = StructureMatcher.firstMismatch(pattern, level, BlockPos.ZERO);

        assertThat(mismatch).isPresent();
        assertThat(mismatch.get().relativePos()).isEqualTo(new BlockPos(1, 0, 0));
        assertThat(mismatch.get().worldPos()).isEqualTo(new BlockPos(1, 0, 0));
        assertThat(mismatch.get().expected()).isEqualTo(new BlockPredicate.OfBlock(Blocks.FURNACE));
        assertThat(mismatch.get().actualState().getBlock()).isEqualTo(Blocks.DIRT);
    }

    @Test
    void area_loaded_checks_every_chunk_touched_by_bounding_box() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(20, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        DynamicMachine machine = new DynamicMachine(net.minecraft.resources.Identifier.fromNamespaceAndPath("mmcr", "matcher_area_loaded"), "Matcher Area Loaded", pattern);
        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        Level level = LevelStub.createWithLoadedChunks(
                Map.of(controllerPos, Blocks.STONE, controllerPos.offset(20, 0, 0), Blocks.STONE),
                Set.of(LevelStub.chunkKey(0, 0)));

        assertThat(StructureMatcher.isAreaLoaded(compiled, Direction.SOUTH, level, controllerPos)).isFalse();
        assertThat(StructureMatcher.matchesCompiled(compiled, Direction.SOUTH, level, controllerPos)).isFalse();
    }
}
