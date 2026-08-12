package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    void firstMismatchReportsWorldPositionExpectedPredicateAndActualState() {
        BlockPos controller = new BlockPos(10, 64, -3);
        Block expectedBlock = Blocks.IRON_BLOCK;
        BlockState actualState = Blocks.COPPER_BLOCK.defaultBlockState();
        BlockArray pattern = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(expectedBlock)));

        Level level = LevelStub.create(Map.of(controller.offset(1, 0, 0), Blocks.COPPER_BLOCK));

        Optional<StructureMatcher.Mismatch> mismatch = StructureMatcher.firstMismatch(pattern, level, controller);

        assertThat(mismatch).isPresent();
        assertThat(mismatch.get().relativePos()).isEqualTo(new BlockPos(1, 0, 0));
        assertThat(mismatch.get().worldPos()).isEqualTo(controller.offset(1, 0, 0));
        assertThat(mismatch.get().expected()).isEqualTo(new BlockPredicate.OfBlock(expectedBlock));
        assertThat(mismatch.get().actualState()).isEqualTo(actualState);
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

    @Test
    void replacement_allows_only_the_configured_position_to_match() {
        BlockPos replacementPos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                replacementPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        var replacement = new SingleBlockModifierReplacement(
                "speed", replacementPos, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(), "", ItemStack.EMPTY);
        var level = LevelStub.create(Map.of(
                BlockPos.ZERO, Blocks.STONE,
                replacementPos, Blocks.GOLD_BLOCK));

        assertThat(StructureMatcher.matchesRotated(pattern, level, BlockPos.ZERO,
                Map.of(replacementPos, List.of(replacement)))).isTrue();
    }

    @Test
    void replacement_matches_all_horizontal_rotations_without_mutating_pattern_positions() {
        BlockPos controllerPos = new BlockPos(8, 64, 8);
        BlockPos rawPos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                rawPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        var replacement = new SingleBlockModifierReplacement(
                "speed", rawPos, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(), "", ItemStack.EMPTY);
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacementMap = Map.of(rawPos, List.of(replacement));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos worldPos = controllerPos.offset(BlockRotator.rotateSouthTo(rawPos, facing));
            BlockPos stonePos = controllerPos.offset(BlockRotator.rotateSouthTo(new BlockPos(0, 1, 0), facing));
            Level level = LevelStub.create(Map.of(
                    controllerPos, Blocks.STONE,
                    worldPos, Blocks.GOLD_BLOCK,
                    stonePos, Blocks.STONE));
            assertThat(StructureMatcher.matches(
                    pattern, level, controllerPos, facing, replacementMap)).isTrue();
        }

        assertThat(pattern.pattern().keySet()).containsExactlyInAnyOrder(rawPos, new BlockPos(0, 1, 0));
    }

    @Test
    void replacement_at_another_position_does_not_match() {
        BlockPos expected = new BlockPos(1, 0, 0);
        BlockPos wrong = new BlockPos(2, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                expected, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        var replacement = new SingleBlockModifierReplacement(
                "speed", wrong, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(), "", ItemStack.EMPTY);

        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(BlockPos.ZERO, Blocks.STONE, expected, Blocks.GOLD_BLOCK)),
                BlockPos.ZERO, Map.of(wrong, List.of(replacement)))).isFalse();
    }

    @Test
    void multiple_replacements_at_one_position_use_any_matching_predicate() {
        BlockPos pos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(pos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        var first = new SingleBlockModifierReplacement("first", pos,
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), "", ItemStack.EMPTY);
        var second = new SingleBlockModifierReplacement("second", pos,
                new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), "", ItemStack.EMPTY);

        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(pos, Blocks.DIAMOND_BLOCK)), BlockPos.ZERO,
                Map.of(pos, List.of(first, second)))).isTrue();
    }
}
