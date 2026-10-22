package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies default-stage structure material aggregation.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureMaterialSummaryTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void emptySummaryHasNoEntriesOrTransferStacks() {
        StructureMaterialSummary summary = StructureMaterialSummary.empty();

        assertThat(summary.entries()).isEmpty();
        assertThat(summary.transferStacks()).isEmpty();
    }

    @Test
    void summarizesRenderedBlocksByCount() {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        states.put(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        states.put(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
        states.put(new BlockPos(2, 0, 0), Blocks.COBBLESTONE.defaultBlockState());
        states.put(new BlockPos(3, 0, 0), Blocks.AIR.defaultBlockState());

        StructureMaterialSummary summary = StructureMaterialSummary.from(
                new StructurePreviewSchema(MMCR.id("summary_test"), states, Map.of()));

        assertThat(summary.entries()).hasSize(2);
        assertThat(summary.entries().get(0).stack().is(Blocks.STONE.asItem())).isTrue();
        assertThat(summary.entries().get(0).count()).isEqualTo(2);
        assertThat(summary.entries().get(0).stack().getCount()).isOne();
        assertThat(summary.entries().get(1).stack().is(Blocks.COBBLESTONE.asItem())).isTrue();
        assertThat(summary.entries().get(1).count()).isOne();
    }

    @Test
    void transferStacksCarryTheAggregatedCounts() {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        states.put(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        states.put(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());

        StructureMaterialSummary summary = StructureMaterialSummary.from(
                new StructurePreviewSchema(MMCR.id("transfer_summary_test"), states, Map.of()));

        assertThat(summary.transferStacks()).singleElement()
                .satisfies(stack -> {
                    assertThat(stack.is(Blocks.STONE.asItem())).isTrue();
                    assertThat(stack.getCount()).isEqualTo(2);
                });
    }

    @Test
    void entryStacksAreDefensiveCopies() {
        StructureMaterialSummary summary = StructureMaterialSummary.from(
                new StructurePreviewSchema(MMCR.id("entry_copy_test"),
                        Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()), Map.of()));

        ItemStack exposed = summary.entries().getFirst().stack();
        exposed.setCount(42);

        assertThat(summary.entries().getFirst().stack().getCount()).isOne();
    }

    @Test
    void waterloggedBlocksStillRequireTheBlockItemRatherThanABucket() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        StructureMaterialSummary summary = StructureMaterialSummary.from(
                new StructurePreviewSchema(MMCR.id("waterlogged_summary_test"),
                        Map.of(BlockPos.ZERO, stairs), Map.of()));

        assertThat(summary.transferStacks()).singleElement().satisfies(stack -> {
            assertThat(stack.is(Items.OAK_STAIRS)).isTrue();
            assertThat(stack.getCount()).isOne();
        });
        assertThat(summary.entries().getFirst().tooltipLines()).singleElement()
                .satisfies(line -> assertTranslation(line, "jei.mmcr.machine_recipe.item_count", "1"));
    }

    @Test
    void fluidTooltipReportsSourcesAndFlowsInsteadOfDisplayQuantity() {
        var entry = new StructureMaterialSummary.Entry(new ItemStack(Items.WATER_BUCKET), 1, 0, 12);

        assertThat(entry.stack().getCount()).isOne();
        assertThat(entry.tooltipLines()).hasSize(2);
        assertTranslation(entry.tooltipLines().get(0), "gui.mmcr.structure_preview.fluid_source_count", "0");
        assertTranslation(entry.tooltipLines().get(1), "gui.mmcr.structure_preview.fluid_non_source_count", "12");
    }

    private static void assertTranslation(Component line, String key, String count) {
        assertThat(line.getContents()).isInstanceOfSatisfying(TranslatableContents.class, contents -> {
            assertThat(contents.getKey()).isEqualTo(key);
            assertThat(contents.getArgs()).containsExactly(count);
        });
    }
}
