package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary.Entry;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies preview bucket requirements against registered vanilla and mod fluids.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureMaterialGameTest {
    private StructureMaterialGameTest() {
    }

    public static void sourcesAndFlowsShareBuckets(GameTestHelper helper) {
        Map<BlockPos, BlockState> states = states(
                water(1), water(0), water(7), water(0), water(8), water(15),
                Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 3),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        for (StructureMaterialSummary summary : summaries(states)) {
            helper.assertTrue(summary.entries().size() == 4, "Only two buckets, stairs and stone are materials");
            assertFluid(helper, summary, Items.WATER_BUCKET, 2, 2, 4);
            assertFluid(helper, summary, Items.LAVA_BUCKET, 1, 1, 1);
            helper.assertTrue(summary.transferStacks().stream().anyMatch(stack -> stack.is(Items.OAK_STAIRS)),
                    "Waterlogged stairs remain a block requirement, not another water source");
            helper.assertTrue(transferred(summary, Items.WATER_BUCKET) == 2
                            && transferred(summary, Items.LAVA_BUCKET) == 1,
                    "Only source blocks add bucket transfer requirements");
        }
        helper.succeed();
    }

    public static void flowingOnlyBucketsAreDisplayOnly(GameTestHelper helper) {
        for (StructureMaterialSummary summary : summaries(states(water(1), water(8),
                Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 15), Blocks.STONE.defaultBlockState()))) {
            assertFluid(helper, summary, Items.WATER_BUCKET, 1, 0, 2);
            assertFluid(helper, summary, Items.LAVA_BUCKET, 1, 0, 1);
            helper.assertTrue(summary.transferStacks().size() == 1
                            && summary.transferStacks().getFirst().is(Items.STONE),
                    "Display-only buckets must not become JEI transfer inputs");
        }
        helper.succeed();
    }

    public static void bucketDemandIsNotStackClamped(GameTestHelper helper) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (int index = 0; index < 128; index++) states.put(new BlockPos(index, 0, 0), water(0));
        states.put(new BlockPos(128, 0, 0), water(8));
        for (StructureMaterialSummary summary : summaries(states)) {
            assertFluid(helper, summary, Items.WATER_BUCKET, 128, 128, 1);
            helper.assertTrue(transferred(summary, Items.WATER_BUCKET) == 128,
                    "Transfer demand preserves all sources even for unstackable buckets");
            ItemStack exposed = summary.transferStacks().getFirst();
            exposed.setCount(1);
            helper.assertTrue(transferred(summary, Items.WATER_BUCKET) == 128,
                    "Transfer stacks cannot mutate cached requirements");
        }
        helper.succeed();
    }

    public static void mekanismFluidUsesItsRegisteredBucket(GameTestHelper helper) {
        BlockState brine = BuiltInRegistries.FLUID.get(ResourceLocation.parse("mekanism:brine"))
                .defaultFluidState().createLegacyBlock();
        Item bucket = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:brine_bucket"));
        helper.assertTrue(brine.getBlock() instanceof LiquidBlock && bucket != Items.AIR,
                "Registered Mekanism brine provides a fluid block and bucket");
        for (StructureMaterialSummary summary : summaries(states(brine.setValue(LiquidBlock.LEVEL, 1), brine))) {
            assertFluid(helper, summary, bucket, 1, 1, 1);
            helper.assertTrue(transferred(summary, bucket) == 1,
                    "Mod fluid sources and flows resolve to the same registered bucket");
        }
        helper.succeed();
    }

    private static BlockState water(int level) {
        return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, level);
    }

    private static Map<BlockPos, BlockState> states(BlockState... states) {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (int index = 0; index < states.length; index++) result.put(new BlockPos(index, 0, 0), states[index]);
        return result;
    }

    private static List<StructureMaterialSummary> summaries(Map<BlockPos, BlockState> states) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        states.forEach((position, state) -> pattern.put(position, new BlockPredicate.OfBlockState(state)));
        var machine = new DynamicMachine(MMCR.id("fluid_materials_test"), "machine.preview", new BlockArray(pattern));
        return List.of(StructureMaterialSummary.from(new StructurePreviewSchema(machine.registryName(), states, Map.of())),
                StructureMaterialSummary.from(machine));
    }

    private static void assertFluid(GameTestHelper helper, StructureMaterialSummary summary, Item bucket,
            int count, int sources, int nonSources) {
        Entry entry = summary.entries().stream().filter(value -> value.stack().is(bucket)).findFirst().orElseThrow();
        helper.assertTrue(entry.count() == count && entry.stack().getCount() == 1
                        && entry.sourceCount() == sources && entry.nonSourceCount() == nonSources,
                "Bucket display and source/non-source metadata match the rendered structure");
    }

    private static int transferred(StructureMaterialSummary summary, Item item) {
        return summary.transferStacks().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
