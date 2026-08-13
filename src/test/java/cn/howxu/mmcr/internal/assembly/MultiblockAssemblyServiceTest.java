package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockAssemblyServiceTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void requirementsAreAggregatedByItemAndComponents() {
        List<MultiblockAssemblyService.Placement> placements = List.of(
                new MultiblockAssemblyService.Placement(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), Blocks.IRON_BLOCK.asItem().getDefaultInstance()),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(), Blocks.IRON_BLOCK.defaultBlockState(), Blocks.IRON_BLOCK.asItem().getDefaultInstance())
        );

        var requirements = MultiblockAssemblyService.aggregateRequirements(placements);

        assertEquals(1, requirements.size());
        assertEquals(2, requirements.getFirst().getCount());
        assertEquals(Items.IRON_BLOCK, requirements.getFirst().getItem());
    }

    @Test
    void representativeStateSkipsAirAndUnsupportedPredicates() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.Air(),
                BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK),
                BlockPos.ZERO.west(), new BlockPredicate.Any()
        ));

        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, pattern);

        assertEquals(1, placements.size());
        assertEquals(Blocks.COPPER_BLOCK, placements.getFirst().state().getBlock());
    }

    @Test
    void placementRetainsPredicateForDemolishMatching() {
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK),
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)
        ));
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO.east(), predicate));

        var placement = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, pattern).getFirst();

        assertTrue(placement.matches(Blocks.COPPER_BLOCK.defaultBlockState()));
        assertTrue(placement.matches(Blocks.IRON_BLOCK.defaultBlockState()));
        assertFalse(placement.matches(Blocks.GOLD_BLOCK.defaultBlockState()));
    }
}
