package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.MMCR;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
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
        DynamicMachine machine = new DynamicMachine(MMCR.id("matcher_compiled"), "Matcher Compiled", pattern);
        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        BlockPos controllerPos = new BlockPos(32, 64, 32);
        Level level = LevelStub.create(Map.of(controllerPos.offset(0, 0, 1), Blocks.STONE));

        assertThat(StructureMatcher.matchesCompiled(compiled, Direction.WEST, level, controllerPos)).isTrue();
    }

    @Test
    void matches_compiled_keeps_the_compiled_path_with_modifier_replacements() {
        BlockPos rawPos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("matcher_compiled_replacement"),
                "Matcher Compiled Replacement", pattern);
        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        BlockPos controllerPos = new BlockPos(32, 64, 32);
        BlockPos rotatedPos = controllerPos.offset(BlockRotator.rotateSouthTo(rawPos, Direction.WEST));
        var replacement = new SingleBlockModifierReplacement("speed",
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        Level level = LevelStub.create(Map.of(rotatedPos, Blocks.GOLD_BLOCK));

        assertThat(StructureMatcher.matchesCompiled(compiled, Direction.WEST, Direction.SOUTH, level, controllerPos,
                Map.of(BlockRotator.rotateSouthTo(rawPos, Direction.WEST), List.of(replacement)), true)).isTrue();
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
    void structure_matching_rejects_a_block_with_the_wrong_state() {
        BlockPos position = new BlockPos(1, 0, 0);
        BlockState expected = Blocks.DISPENSER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH);
        BlockState actual = Blocks.DISPENSER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH);
        BlockArray pattern = new BlockArray(Map.of(position, new BlockPredicate.OfBlockState(expected)));

        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.createStates(Map.of(position, actual)), BlockPos.ZERO)).isFalse();
    }

    @Test
    void structure_matching_can_ignore_or_require_block_state() {
        BlockPos position = new BlockPos(1, 0, 0);
        BlockState expected = Blocks.DISPENSER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH);
        BlockState actual = Blocks.DISPENSER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH);
        BlockArray pattern = new BlockArray(Map.of(position, new BlockPredicate.OfBlockState(expected)));
        Level level = LevelStub.createStates(Map.of(position, actual));

        assertThat(StructureMatcher.matchesRotated(pattern, level, BlockPos.ZERO, Map.of(), false)).isTrue();
        assertThat(StructureMatcher.matchesRotated(pattern, level, BlockPos.ZERO, Map.of(), true)).isFalse();
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
    void compiled_matching_does_not_require_every_bounding_box_chunk_loaded() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(20, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        DynamicMachine machine = new DynamicMachine(MMCR.id("matcher_area_loaded"), "Matcher Area Loaded", pattern);
        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        Level level = LevelStub.createWithLoadedChunks(
                Map.of(controllerPos, Blocks.STONE, controllerPos.offset(20, 0, 0), Blocks.STONE),
                Set.of(LevelStub.chunkKey(0, 0)));

        assertThat(StructureMatcher.isAreaLoaded(compiled, Direction.SOUTH, level, controllerPos)).isFalse();
        assertThat(StructureMatcher.matchesCompiled(compiled, Direction.SOUTH, level, controllerPos)).isTrue();
    }

    @Test
    void replacement_allows_only_the_configured_position_to_match() {
        BlockPos replacementPos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                replacementPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        var level = LevelStub.create(Map.of(
                BlockPos.ZERO, Blocks.STONE,
                replacementPos, Blocks.GOLD_BLOCK));

        assertThat(StructureMatcher.matchesRotated(pattern, level, BlockPos.ZERO,
                Map.of(replacementPos, List.of(replacement)))).isTrue();
    }

    @Test
    void base_or_replacement_predicate_matches_configured_position() {
        BlockPos pos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(pos, new BlockPredicate.OfBlock(Blocks.FURNACE)));
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), ItemStack.EMPTY);
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = Map.of(pos, List.of(replacement));

        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(pos, Blocks.FURNACE)), BlockPos.ZERO, replacements)).isTrue();
        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(pos, Blocks.DIAMOND_BLOCK)), BlockPos.ZERO, replacements)).isTrue();
        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(pos, Blocks.GOLD_BLOCK)), BlockPos.ZERO, replacements)).isFalse();
    }

    @Test
    void replacement_matches_all_horizontal_rotations_without_mutating_pattern_positions() {
        BlockPos controllerPos = new BlockPos(8, 64, 8);
        BlockPos rawPos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                rawPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
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
    void compiled_modifier_replacement_is_reused_under_rotated_key() {
        BlockPos rawPos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(Blocks.FURNACE)));
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), ItemStack.EMPTY);
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("matcher_rotated_replacement"),
                "Matcher Rotated Replacement",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("matcher_rotated_replacement")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(rawPos, List.of(replacement)));
        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        BlockPos rotatedPos = BlockRotator.rotateSouthTo(rawPos, Direction.WEST);

        assertThat(compiled.modifierReplacements(Direction.WEST)).containsOnlyKeys(rotatedPos);
        assertThat(compiled.modifierReplacements(Direction.WEST).get(rotatedPos)).containsExactly(replacement);
    }

    @Test
    void character_bound_modifier_requirements_match_every_generated_position() {
        BlockArray pattern = BlockArray.builder()
                .pattern("MM")
                .set('M', new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE))
                .build();
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), ItemStack.EMPTY);
        MachineStructureDefinition definition = new MachineStructureDefinition(
                MMCR.id("matcher_key_replacement"),
                List.of(new MachineStructureDefinition.Declaration(
                        MachineStructureDefinition.Declaration.Kind.FULL,
                        pattern,
                        PortRequirementSpec.none(),
                        PortTierRequirementSpec.none(),
                        List.of(),
                        MachineStructureRequirements.builder().modifier('M', replacement).build())));

        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK,
                        BlockPos.ZERO, Blocks.DIAMOND_BLOCK)),
                BlockPos.ZERO, definition.modifierReplacements())).isTrue();
        assertThat(definition.modifierReplacements())
                .containsEntry(new BlockPos(-1, 0, 0), List.of(replacement))
                .containsEntry(BlockPos.ZERO, List.of(replacement));
    }

    @Test
    void staged_vertical_rotation_matches_non_default_roll_without_mutating_stage_pattern() {
        BlockPos controllerPos = new BlockPos(8, 64, 8);
        BlockPos rawPos = new BlockPos(1, 0, 0);
        Direction rollFacing = Direction.WEST;
        BlockArray stage3 = new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK)));
        BlockPos rotatedPos = BlockRotator.rotateSouthTo(rawPos, Direction.UP, rollFacing);
        BlockArray rotatedStage3 = BlockArrayCache.get(stage3, Direction.UP, rollFacing);
        Level level = LevelStub.create(Map.of(controllerPos.offset(rotatedPos), Blocks.DIAMOND_BLOCK));

        assertThat(StructureMatcher.matchesRotated(rotatedStage3, level, controllerPos)).isTrue();
        assertThat(StructureMatcher.matches(stage3, level, controllerPos, Direction.UP)).isFalse();
        assertThat(stage3.pattern().keySet()).containsExactly(rawPos);
    }

    @Test
    void replacement_at_another_position_does_not_match() {
        BlockPos expected = new BlockPos(1, 0, 0);
        BlockPos wrong = new BlockPos(2, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                expected, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);

        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(BlockPos.ZERO, Blocks.STONE, expected, Blocks.GOLD_BLOCK)),
                BlockPos.ZERO, Map.of(wrong, List.of(replacement)))).isFalse();
    }

    @Test
    void multiple_replacements_at_one_position_use_any_matching_predicate() {
        BlockPos pos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(pos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        var first = new SingleBlockModifierReplacement("first", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        var second = new SingleBlockModifierReplacement("second", new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), ItemStack.EMPTY);

        assertThat(StructureMatcher.matchesRotated(pattern,
                LevelStub.create(Map.of(pos, Blocks.DIAMOND_BLOCK)), BlockPos.ZERO,
                Map.of(pos, List.of(first, second)))).isTrue();
    }

    @Test
    void compiled_pattern_records_rotated_coupler_and_interface_positions() {
        BlockArray pattern = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(List.of(BlockPredicate.machineCoupler())),
                new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get())));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("matcher_interfaces"),
                "Matcher Interfaces", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.couplerPositions(Direction.WEST)).containsExactly(new BlockPos(0, 0, 1));
        assertThat(compiled.interfacePositions(Direction.WEST)).containsExactly(new BlockPos(-1, 0, 0));
    }

    @Test
    void compiled_pattern_reuses_an_immutable_scan_plan() {
        Map<BlockPos, BlockPredicate> ordered = new LinkedHashMap<>();
        ordered.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE));
        ordered.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
        ordered.put(new BlockPos(2, 0, 0), new BlockPredicate.Any());
        BlockArray pattern = new BlockArray(ordered);
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("matcher_scan_plan"), "Matcher Scan Plan", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        CompiledMachinePattern.ScanPlan first = compiled.scanPlan(Direction.SOUTH, 2);
        CompiledMachinePattern.ScanPlan second = compiled.scanPlan(Direction.SOUTH, 2);

        assertThat(second).isSameAs(first);
        assertThat(first.entryPositions()).containsExactly(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        assertThat(first.entryPredicates()).containsExactly(
                new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPredicate.Any());
        assertThat(first.entryOrder()).containsExactly(0, 1, 2);
        assertThat(first.sentinelIndexes()).containsExactly(0, 1);
        assertThat(first.sentinelMembership()).containsExactly(true, true, false);
    }

    @Test
    void bounded_scan_advances_one_of_five_batches_until_valid() {
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            entries.put(new BlockPos(index, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE));
        }
        BlockArray pattern = new BlockArray(entries);
        Map<BlockPos, Block> blocks = entries.keySet().stream()
                .collect(Collectors.toMap(pos -> pos, pos -> Blocks.STONE));
        Level level = LevelStub.create(blocks);
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0));

        for (int index = 0; index < 4; index++) {
            StructureMatcher.ScanResult result = scan.step(level, BlockPos.ZERO);
            assertThat(result.checkedEntries()).isLessThanOrEqualTo(scan.batchSize());
            assertThat(result.inProgress()).isTrue();
        }
        StructureMatcher.ScanResult result = scan.step(level, BlockPos.ZERO);

        assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.VALID);
        assertThat(scan.cursor()).isEqualTo(entries.size());
    }

    @Test
    void bounded_scan_prioritizes_previous_mismatch_and_continues_when_fixed() {
        BlockPos mismatchPos = BlockPos.ZERO;
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            entries.put(new BlockPos(index, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE));
        }
        BlockArray pattern = new BlockArray(entries);
        Map<BlockPos, Block> wrong = new LinkedHashMap<>();
        entries.keySet().forEach(pos -> wrong.put(pos, Blocks.STONE));
        wrong.put(mismatchPos, Blocks.DIRT);
        StructureMatcher.ScanState first = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0));
        StructureMatcher.ScanResult mismatch = first.step(LevelStub.create(wrong), BlockPos.ZERO);

        assertThat(mismatch.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        assertThat(mismatch.mismatch().orElseThrow().relativePos()).isEqualTo(mismatchPos);

        StructureMatcher.ScanState retry = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0), mismatch.mismatch().orElseThrow());
        StructureMatcher.ScanResult fixed = retry.step(LevelStub.create(wrong), BlockPos.ZERO);
        assertThat(fixed.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);

        wrong.put(mismatchPos, Blocks.STONE);
        StructureMatcher.ScanState resumed = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0), mismatch.mismatch().orElseThrow());
        assertThat(resumed.step(LevelStub.create(wrong), BlockPos.ZERO).inProgress()).isTrue();
    }

    @Test
    void bounded_scan_uses_air_fast_path_and_sentinels() {
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            entries.put(new BlockPos(index, 0, 0), new BlockPredicate.Air());
        }
        BlockArray pattern = new BlockArray(entries);
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(0, 0, 0), Blocks.STONE);
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, true, 16));

        StructureMatcher.ScanResult result = scan.step(LevelStub.create(blocks), BlockPos.ZERO);

        assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        assertThat(result.checkedEntries()).isLessThanOrEqualTo(scan.batchSize());
        assertThat(scan.cursor()).isZero();
    }

    @Test
    void bounded_scan_does_not_advance_past_an_unmatched_entry() {
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 6; index++) {
            entries.put(new BlockPos(index, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE));
        }
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(entries), Map.of(), true,
                StructureMatcher.ScanOptions.of(2, false, 0));

        StructureMatcher.ScanResult result = scan.step(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.DIRT)), BlockPos.ZERO);

        assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        assertThat(scan.cursor()).isZero();
    }

    @Test
    void sentinel_reads_share_the_batch_budget_while_scan_progresses() {
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            entries.put(new BlockPos(index, 0, 0), new BlockPredicate.Air());
        }
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(entries), Map.of(), true,
                StructureMatcher.ScanOptions.of(5, true, 16));
        Level level = LevelStub.create(Map.of());

        StructureMatcher.ScanResult result;
        do {
            result = scan.step(level, BlockPos.ZERO);
            assertThat(result.checkedEntries()).isLessThanOrEqualTo(scan.batchSize());
        } while (result.inProgress());

        assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.VALID);
        assertThat(scan.cursor()).isEqualTo(entries.size());
    }

    @Test
    void sentinel_is_checked_once_and_five_steps_complete_the_scan() {
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            entries.put(new BlockPos(index, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE));
        }
        Level level = LevelStub.create(entries.keySet().stream()
                .collect(Collectors.toMap(pos -> pos, pos -> Blocks.STONE)));
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(entries), Map.of(), true,
                StructureMatcher.ScanOptions.of(5, true, 2));

        for (int step = 0; step < 5; step++) {
            StructureMatcher.ScanResult result = scan.step(level, BlockPos.ZERO);
            assertThat(result.checkedEntries()).isLessThanOrEqualTo(scan.batchSize());
            if (step < 4) assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.IN_PROGRESS);
        }

        assertThat(scan.cursor()).isEqualTo(entries.size());
    }

    @Test
    void air_fast_path_still_checks_replacements() {
        BlockPos position = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(position, new BlockPredicate.Air()));
        var replacement = new SingleBlockModifierReplacement(
                "air_replacement", new BlockPredicate.OfBlock(Blocks.STONE), List.of(), ItemStack.EMPTY);

        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern,
                Map.of(position, List.of(replacement)), true,
                StructureMatcher.ScanOptions.of(5, false, 0));

        assertThat(scan.step(LevelStub.create(Map.of(position, Blocks.STONE)), BlockPos.ZERO).status())
                .isEqualTo(StructureMatcher.ScanStatus.VALID);
    }

    @Test
    void previous_mismatch_is_not_reused_for_a_different_scan_identity() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE)));
        StructureMatcher.ScanState first = StructureMatcher.beginScan(1L, Direction.SOUTH, Direction.SOUTH,
                1, "first", pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0), null);
        StructureMatcher.Mismatch mismatch = first.step(LevelStub.create(Map.of()), BlockPos.ZERO)
                .mismatch().orElseThrow();

        StructureMatcher.ScanState retry = StructureMatcher.beginScan(2L, Direction.SOUTH, Direction.SOUTH,
                1, "second", pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0), mismatch);

        assertThat(mismatch.structureVersion()).isEqualTo(1L);
        assertThat(mismatch.patternIdentity()).isEqualTo("first");
        assertThat(retry.previousMismatch()).isNull();
    }

    @Test
    void capture_reuses_shared_recursive_snapshots_and_refreshes_suppliers_in_the_next_batch() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Block> supplied = new AtomicReference<>(Blocks.STONE);
        BlockPredicate deferred = new BlockPredicate.DeferredBlock(() -> {
            calls.incrementAndGet();
            return supplied.get();
        });
        BlockPredicate nested = new BlockPredicate.AnyOf(List.of(deferred));
        BlockPredicate shared = new BlockPredicate.AnyOf(List.of(nested, deferred));
        var replacement = new SingleBlockModifierReplacement("shared", shared, List.of(), ItemStack.EMPTY);
        var leafReplacement = new SingleBlockModifierReplacement("leaf", deferred, List.of(), ItemStack.EMPTY);
        Map<BlockPos, BlockPredicate> predicates = new LinkedHashMap<>();
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = new LinkedHashMap<>();
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        for (int index = 0; index < 4; index++) {
            BlockPos position = new BlockPos(index, 0, 0);
            predicates.put(position, shared);
            replacements.put(position, List.of(replacement, leafReplacement));
            blocks.put(position, Blocks.STONE);
        }
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(predicates), replacements, true,
                StructureMatcher.ScanOptions.of(2, false, 0));

        StructureMatcher.ScanBatch first = scan.capture(LevelStub.create(blocks), BlockPos.ZERO);
        assertThat(calls.get()).isEqualTo(1);
        BlockPredicate snapshot = first.entries().getFirst().matchingExpected();
        assertThat(snapshot).isNotSameAs(shared);
        for (StructureMatcher.ScanEntry entry : first.entries()) {
            assertThat(entry.matchingExpected()).isSameAs(snapshot);
            assertThat(entry.replacementPredicates().getFirst()).isSameAs(snapshot);
            assertThat(entry.replacementPredicates().get(1)).isSameAs(snapshot.children().get(1));
            assertThat(snapshot.children().getFirst().children().getFirst()).isSameAs(snapshot.children().get(1));
            assertThat(entry.mismatch().expected()).isSameAs(shared);
        }
        supplied.set(Blocks.DIRT);
        StructureMatcher.ScanResult firstResult = first.match();
        assertThat(firstResult.status()).isEqualTo(StructureMatcher.ScanStatus.IN_PROGRESS);
        assertThat(calls.get()).isEqualTo(1);
        scan.apply(first.identity(), firstResult);

        blocks.replaceAll((position, block) -> Blocks.DIRT);
        StructureMatcher.ScanBatch second = scan.capture(LevelStub.create(blocks), BlockPos.ZERO);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(second.entries().getFirst().matchingExpected()).isNotSameAs(snapshot);
        StructureMatcher.ScanResult secondResult = second.match();
        assertThat(secondResult.status()).isEqualTo(StructureMatcher.ScanStatus.VALID);
        scan.apply(second.identity(), secondResult);
        assertThat(scan.cursor()).isEqualTo(4);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void capture_preserves_static_leaf_identity_in_mixed_batches_and_refreshes_shared_dynamic_snapshots() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Block> supplied = new AtomicReference<>(Blocks.GOLD_BLOCK);
        BlockPredicate deferred = new BlockPredicate.DeferredBlock(() -> {
            calls.incrementAndGet();
            return supplied.get();
        });
        BlockPredicate fallback = new BlockPredicate.OfBlock(Blocks.BEDROCK);
        BlockPredicate shared = new BlockPredicate.AnyOf(List.of(fallback, deferred));
        Map<BlockPos, BlockPredicate> predicates = new LinkedHashMap<>();
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        List<BlockPos> dynamicPositions = new ArrayList<>();
        for (int batchIndex = 0; batchIndex < 2; batchIndex++) {
            for (int index = 0; index < 32; index++) {
                BlockPos position = new BlockPos(predicates.size(), 0, 0);
                Block block = index % 2 == 0 ? Blocks.STONE : Blocks.DIRT;
                predicates.put(position, new BlockPredicate.OfBlock(block));
                blocks.put(position, block);
            }
            for (BlockPredicate predicate : List.of(deferred, shared, shared, deferred)) {
                BlockPos position = new BlockPos(predicates.size(), 0, 0);
                predicates.put(position, predicate);
                blocks.put(position, Blocks.GOLD_BLOCK);
                dynamicPositions.add(position);
            }
        }
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(predicates), Map.of(), true,
                StructureMatcher.ScanOptions.of(2, false, 0));
        BlockPredicate previousSharedSnapshot = null;
        BlockPredicate previousDeferredSnapshot = null;
        for (int batchIndex = 0; batchIndex < 2; batchIndex++) {
            StructureMatcher.ScanBatch batch = scan.capture(LevelStub.create(blocks), BlockPos.ZERO);
            assertThat(calls.get()).isEqualTo(batchIndex + 1);
            BlockPredicate sharedSnapshot = batch.entries().stream()
                    .filter(entry -> entry.mismatch().expected() == shared)
                    .findFirst().orElseThrow().matchingExpected();
            BlockPredicate deferredSnapshot = batch.entries().stream()
                    .filter(entry -> entry.mismatch().expected() == deferred)
                    .findFirst().orElseThrow().matchingExpected();
            assertThat(sharedSnapshot).isNotSameAs(shared).isNotSameAs(previousSharedSnapshot);
            assertThat(deferredSnapshot).isNotSameAs(deferred).isNotSameAs(previousDeferredSnapshot);
            assertThat(sharedSnapshot.children().getFirst()).isSameAs(fallback);
            assertThat(sharedSnapshot.children().getLast()).isSameAs(deferredSnapshot);
            for (StructureMatcher.ScanEntry entry : batch.entries()) {
                BlockPredicate expected = predicates.get(entry.mismatch().relativePos());
                assertThat(entry.mismatch().expected()).isSameAs(expected);
                if (expected == shared) {
                    assertThat(entry.matchingExpected()).isSameAs(sharedSnapshot);
                } else if (expected == deferred) {
                    assertThat(entry.matchingExpected()).isSameAs(deferredSnapshot);
                } else {
                    assertThat(entry.matchingExpected()).isSameAs(expected);
                }
            }
            supplied.set(Blocks.DIAMOND_BLOCK);
            StructureMatcher.ScanResult result = batch.match();
            assertThat(result.status()).isEqualTo(batchIndex == 0
                    ? StructureMatcher.ScanStatus.IN_PROGRESS : StructureMatcher.ScanStatus.VALID);
            assertThat(calls.get()).isEqualTo(batchIndex + 1);
            scan.apply(batch.identity(), result);
            for (BlockPos position : dynamicPositions) blocks.put(position, Blocks.DIAMOND_BLOCK);
            previousSharedSnapshot = sharedSnapshot;
            previousDeferredSnapshot = deferredSnapshot;
        }
        assertThat(scan.cursor()).isEqualTo(predicates.size());
    }

    @Test
    void capture_memo_uses_predicate_identity_instead_of_record_equality() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Block> supplier = () -> {
            calls.incrementAndGet();
            return Blocks.STONE;
        };
        BlockPredicate first = new BlockPredicate.DeferredBlock(supplier);
        BlockPredicate second = new BlockPredicate.DeferredBlock(supplier);
        assertThat(second).isEqualTo(first).isNotSameAs(first);
        BlockPos other = new BlockPos(1, 0, 0);
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(
                new BlockArray(Map.of(BlockPos.ZERO, first, other, second)), Map.of(), true,
                StructureMatcher.ScanOptions.of(1, false, 0));

        StructureMatcher.ScanBatch batch = scan.capture(
                LevelStub.create(Map.of(BlockPos.ZERO, Blocks.STONE, other, Blocks.STONE)), BlockPos.ZERO);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(batch.match().status()).isEqualTo(StructureMatcher.ScanStatus.VALID);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void capture_shared_replacements_match_only_their_configured_positions() {
        AtomicInteger calls = new AtomicInteger();
        BlockPredicate replacementPredicate = new BlockPredicate.AnyOf(List.of(new BlockPredicate.DeferredBlock(() -> {
            calls.incrementAndGet();
            return Blocks.GOLD_BLOCK;
        })));
        var replacement = new SingleBlockModifierReplacement("gold", replacementPredicate, List.of(), ItemStack.EMPTY);
        BlockPos replaced = new BlockPos(1, 0, 0);
        BlockPos unconfigured = new BlockPos(2, 0, 0);
        BlockPredicate expected = new BlockPredicate.OfBlock(Blocks.STONE);
        Map<BlockPos, BlockPredicate> ordered = new LinkedHashMap<>();
        ordered.put(BlockPos.ZERO, expected);
        ordered.put(replaced, expected);
        ordered.put(unconfigured, expected);
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(ordered),
                Map.of(BlockPos.ZERO, List.of(replacement), replaced, List.of(replacement)), true,
                StructureMatcher.ScanOptions.of(1, false, 0));

        StructureMatcher.ScanBatch batch = scan.capture(LevelStub.create(Map.of(
                BlockPos.ZERO, Blocks.GOLD_BLOCK, replaced, Blocks.GOLD_BLOCK, unconfigured, Blocks.GOLD_BLOCK)),
                BlockPos.ZERO);
        assertThat(calls.get()).isEqualTo(1);
        StructureMatcher.ScanResult result = batch.match();
        assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        assertThat(result.checkedEntries()).isEqualTo(3);
        assertThat(result.mismatch().orElseThrow().relativePos()).isEqualTo(unconfigured);
        assertThat(batch.entries().getLast().replacementPredicates()).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void capture_preserves_network_interface_matching_without_resolving_its_supplier() {
        AtomicInteger calls = new AtomicInteger();
        BlockPredicate network = new BlockPredicate.DeferredBlock(() -> {
            calls.incrementAndGet();
            return Blocks.STONE;
        }, true);
        BlockPredicate expected = new BlockPredicate.AnyOf(List.of(network));
        BlockPos replacementPos = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, expected,
                replacementPos, new BlockPredicate.OfBlock(Blocks.STONE)));
        var replacement = new SingleBlockModifierReplacement("network", network, List.of(), ItemStack.EMPTY);
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern,
                Map.of(replacementPos, List.of(replacement)), true, StructureMatcher.ScanOptions.of(1, false, 0));

        StructureMatcher.ScanBatch valid = scan.capture(
                LevelStub.create(Map.of(BlockPos.ZERO, ModBlocks.NETWORK_INTERFACE.get(),
                        replacementPos, ModBlocks.NETWORK_INTERFACE.get())), BlockPos.ZERO);
        for (StructureMatcher.ScanEntry entry : valid.entries()) {
            if (entry.mismatch().relativePos().equals(BlockPos.ZERO)) {
                assertThat(entry.matchingExpected()).isSameAs(expected);
            } else {
                assertThat(entry.replacementPredicates().getFirst()).isSameAs(network);
            }
        }
        StructureMatcher.ScanResult validResult = valid.match();
        assertThat(validResult.status()).isEqualTo(StructureMatcher.ScanStatus.VALID);
        scan.apply(valid.identity(), validResult);

        StructureMatcher.ScanState wrong = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(1, false, 0));
        assertThat(wrong.capture(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.STONE)), BlockPos.ZERO).match().status())
                .isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        assertThat(calls.get()).isZero();
    }

    @Test
    void capture_preserves_state_sensitive_expected_replacement_and_mismatch_diagnostics() {
        BlockState expectedState = Blocks.DISPENSER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH);
        BlockState actualState = expectedState.setValue(DirectionalBlock.FACING, Direction.SOUTH);
        BlockPredicate statePredicate = new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlockState(expectedState)));
        BlockPredicate expected = new BlockPredicate.AnyOf(List.of(statePredicate));
        BlockPos replacementPos = new BlockPos(1, 0, 0);
        BlockPredicate base = new BlockPredicate.OfBlock(Blocks.STONE);
        Map<BlockPos, BlockPredicate> ordered = new LinkedHashMap<>();
        ordered.put(replacementPos, base);
        ordered.put(BlockPos.ZERO, expected);
        BlockArray pattern = new BlockArray(ordered);
        var replacement = new SingleBlockModifierReplacement("state", statePredicate, List.of(), ItemStack.EMPTY);
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = Map.of(replacementPos, List.of(replacement));
        BlockPos controller = new BlockPos(10, 64, -3);
        Level wrongStates = LevelStub.createStates(Map.of(controller, actualState,
                controller.offset(replacementPos), actualState));

        StructureMatcher.ScanState insensitive = StructureMatcher.beginScan(pattern, replacements, false,
                StructureMatcher.ScanOptions.of(1, false, 0));
        assertThat(insensitive.capture(wrongStates, controller).match().status()).isEqualTo(StructureMatcher.ScanStatus.VALID);

        StructureMatcher.ScanState sensitive = StructureMatcher.beginScan(pattern, replacements, true,
                StructureMatcher.ScanOptions.of(1, false, 0));
        StructureMatcher.ScanBatch first = sensitive.capture(wrongStates, controller);
        StructureMatcher.ScanResult wrongReplacement = first.match();
        assertThat(wrongReplacement.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        assertThat(wrongReplacement.mismatch().orElseThrow().expected()).isSameAs(base);
        sensitive.apply(first.identity(), wrongReplacement);

        Level fixedReplacement = LevelStub.createStates(Map.of(controller, actualState,
                controller.offset(replacementPos), expectedState));
        StructureMatcher.ScanBatch retry = sensitive.capture(fixedReplacement, controller);
        StructureMatcher.ScanResult retryResult = retry.match();
        assertThat(retryResult.status()).isEqualTo(StructureMatcher.ScanStatus.IN_PROGRESS);
        sensitive.apply(retry.identity(), retryResult);
        StructureMatcher.ScanBatch next = sensitive.capture(fixedReplacement, controller);
        StructureMatcher.ScanResult wrongExpected = next.match();
        assertThat(wrongExpected.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        StructureMatcher.Mismatch mismatch = wrongExpected.mismatch().orElseThrow();
        assertThat(mismatch.relativePos()).isEqualTo(BlockPos.ZERO);
        assertThat(mismatch.worldPos()).isEqualTo(controller);
        assertThat(mismatch.expected()).isSameAs(expected);
        assertThat(mismatch.actualState()).isEqualTo(actualState);
        assertThat(next.entries().getFirst().matchingExpected()).isSameAs(expected);
    }

    @Test
    void capture_copies_mutable_any_of_children_and_keeps_each_snapshot_isolated() {
        List<BlockPredicate> children = new ArrayList<>(List.of(new BlockPredicate.OfBlock(Blocks.STONE)));
        BlockPredicate expected = new BlockPredicate.AnyOf(List.of(new BlockPredicate.AnyOf(children)));
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(Map.of(BlockPos.ZERO, expected)),
                Map.of(), true, StructureMatcher.ScanOptions.of(1, false, 0));

        StructureMatcher.ScanBatch first = scan.capture(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.STONE)), BlockPos.ZERO);
        assertThat(first.entries().getFirst().matchingExpected()).isNotSameAs(expected);
        children.set(0, new BlockPredicate.OfBlock(Blocks.DIRT));
        StructureMatcher.ScanResult result = first.match();
        assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.VALID);
        scan.apply(first.identity(), result);

        StructureMatcher.ScanState nextScan = StructureMatcher.beginScan(new BlockArray(Map.of(BlockPos.ZERO, expected)),
                Map.of(), true, StructureMatcher.ScanOptions.of(1, false, 0));
        assertThat(nextScan.capture(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.STONE)), BlockPos.ZERO).match().status())
                .isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
    }

    @Test
    void capture_shares_the_memo_with_previous_mismatch_and_sentinels() {
        AtomicInteger calls = new AtomicInteger();
        BlockPredicate expected = new BlockPredicate.DeferredBlock(() -> {
            calls.incrementAndGet();
            return Blocks.STONE;
        });
        Map<BlockPos, BlockPredicate> ordered = new LinkedHashMap<>();
        for (int index = 0; index < 4; index++) ordered.put(new BlockPos(index, 0, 0), expected);
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(new BlockArray(ordered), Map.of(), true,
                StructureMatcher.ScanOptions.of(1, true, 1));

        StructureMatcher.ScanBatch first = scan.capture(LevelStub.create(Map.of()), BlockPos.ZERO);
        StructureMatcher.ScanResult mismatch = first.match();
        assertThat(mismatch.status()).isEqualTo(StructureMatcher.ScanStatus.MISMATCH);
        assertThat(mismatch.mismatch().orElseThrow().expected()).isSameAs(expected);
        assertThat(calls.get()).isEqualTo(1);
        scan.apply(first.identity(), mismatch);

        Map<BlockPos, Block> fixed = ordered.keySet().stream()
                .collect(Collectors.toMap(pos -> pos, pos -> Blocks.STONE));
        StructureMatcher.ScanBatch retry = scan.capture(LevelStub.create(fixed), BlockPos.ZERO);
        assertThat(retry.entries()).hasSize(4);
        BlockPredicate snapshot = retry.entries().getFirst().matchingExpected();
        for (StructureMatcher.ScanEntry entry : retry.entries()) {
            assertThat(entry.matchingExpected()).isSameAs(snapshot);
        }
        assertThat(retry.match().status()).isEqualTo(StructureMatcher.ScanStatus.IN_PROGRESS);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void invalidated_scan_fails_without_reading_world() {
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()));
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0));
        scan.invalidate(StructureMatcher.InvalidationReason.ORIENTATION);

        StructureMatcher.ScanResult result = scan.step(LevelStub.create(Map.of()), BlockPos.ZERO);

        assertThat(result.status()).isEqualTo(StructureMatcher.ScanStatus.INVALIDATED);
        assertThat(result.checkedEntries()).isZero();
    }
}
