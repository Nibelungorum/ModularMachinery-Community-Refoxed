package cn.howxu.mmcr.internal.preview;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockPreviewBuilderTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void preview_state_uses_explicit_block_state() {
        var state = Blocks.COPPER_BLOCK.defaultBlockState();

        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.OfBlockState(state));

        assertEquals(state, result.orElseThrow());
    }

    @Test
    void preview_state_uses_default_state_for_block_predicate() {
        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));

        assertEquals(Blocks.IRON_BLOCK.defaultBlockState(), result.orElseThrow());
    }

    @Test
    void preview_state_uses_first_supported_any_of_child() {
        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK))));

        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), result.orElseThrow());
    }

    @Test
    void preview_state_returns_empty_for_unsupported_predicate() {
        BlockPredicate unsupported = new BlockPredicate.OfTag(TagKey.create(
                BuiltInRegistries.BLOCK.key(), Identifier.fromNamespaceAndPath("mmcr", "preview_test")));

        assertTrue(MultiblockPreviewBuilder.previewState(unsupported).isEmpty());
    }

    @Test
    void build_skips_positions_that_are_not_air() {
        var controller = new BlockPos(10, 64, 10);
        var occupiedRelative = new BlockPos(1, 0, 0);
        var level = LevelStub.create(Map.of(controller.offset(occupiedRelative), Blocks.STONE));
        var pattern = new BlockArray(Map.of(
                occupiedRelative, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));

        var snapshot = MultiblockPreviewBuilder.build(level, controller, pattern, 8192);

        assertEquals(1, snapshot.entries().size());
        assertEquals(new BlockPos(2, 0, 0), snapshot.entries().getFirst().relativePos());
    }

    @Test
    void build_truncates_to_max_entries() {
        var level = LevelStub.create(Map.of());
        var controller = new BlockPos(0, 64, 0);
        var pattern = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));

        var snapshot = MultiblockPreviewBuilder.build(level, controller, pattern, 1);

        assertEquals(1, snapshot.entries().size());
    }
}
