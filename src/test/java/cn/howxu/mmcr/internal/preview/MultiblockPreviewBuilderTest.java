package cn.howxu.mmcr.internal.preview;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;

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
}
