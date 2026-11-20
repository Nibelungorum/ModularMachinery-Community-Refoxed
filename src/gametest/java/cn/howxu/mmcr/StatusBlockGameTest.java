package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.StatusBlockCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real recipe, KubeJS opt-in and controller teardown.
 * @author howxu <dev@howxu.cn>
 */
public final class StatusBlockGameTest {
    private static final ResourceLocation MACHINE = ResourceLocation.parse("mmcr_test:status_fixture");

    public static void recipeAndSharedCleanup(GameTestHelper helper) {
        Block coil = block("status_coil"), passive = block("passive_coil");
        BlockPos shared = new BlockPos(3, 3, 3);
        helper.setBlock(shared, passive);
        var left = place(helper, shared, Direction.SOUTH, coil, passive);
        var right = place(helper, shared, Direction.NORTH, coil, passive);
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(MMCR.id("status_block_visual_test"), MACHINE, 600,
                List.of(), List.of(), List.of(), 0, 1, false, false, false, Set.of()));

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(lit(helper, left.privateCoil) && lit(helper, right.privateCoil),
                    "Both formed controllers running a recipe must light their private coils");
            helper.assertTrue(!lit(helper, shared) && !lit(helper, right.passive), "An untagged LIT block is not controlled");
        }).thenExecute(() -> {
            helper.setBlock(shared, coil);
            MachineControllerBlockEntity.markStructureDirty(helper.getLevel(), helper.absolutePos(shared));
        }).thenWaitUntil(() -> helper.assertTrue(lit(helper, shared), "A legal replacement is indexed without a template change"))
                .thenExecute(() -> helper.setBlock(left.origin.south(), Blocks.LEVER.defaultBlockState()
                        .setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.SOUTH)
                        .setValue(LeverBlock.POWERED, true)))
                .thenWaitUntil(() -> {
                    helper.assertTrue(left.controller.isRedstonePaused(), "Lever directly powers the left controller");
                    helper.assertTrue(!lit(helper, left.privateCoil) && lit(helper, shared), "One paused owner cannot extinguish a shared coil");
                    helper.assertTrue(left.controller.structureSnapshot().formed() && right.controller.structureSnapshot().formed(),
                            "Changing LIT preserves both structures");
                }).thenExecute(() -> helper.setBlock(left.origin.south(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(lit(helper, left.privateCoil), "Resuming relights the private coil"))
                .thenExecute(() -> helper.setBlock(left.origin, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(!lit(helper, left.privateCoil) && lit(helper, shared),
                        "Cleanup outlives the removed controller and preserves the other owner"))
                .thenExecute(() -> {
                    helper.setBlock(shared, Blocks.AIR);
                    right.controller.onStructureBlockChanged(helper.absolutePos(shared));
                }).thenWaitUntil(() -> helper.assertTrue(!right.controller.structureSnapshot().formed() && !lit(helper, right.privateCoil),
                        "Invalidation extinguishes surviving members"))
                .thenExecute(() -> {
                    helper.setBlock(shared, coil);
                    right.controller.onStructureBlockChanged(helper.absolutePos(shared));
                }).thenWaitUntil(() -> helper.assertTrue(right.controller.structureSnapshot().formed() && lit(helper, shared)
                                && !lit(helper, right.passive) && helper.getBlockState(shared).getLightEmission(helper.getLevel(), helper.absolutePos(shared)) == 0,
                        "Reformation rebuilds the index without creating a world light source"))
                .thenSucceed();
    }

    public static void doesNotLoadChunks(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 2, 1);
        helper.setBlock(local, block("status_coil"));
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(local.above());
        BlockPos far = origin.offset(2_000_000, 0, 0);
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Remote chunk is absent");
        var coordinator = StatusBlockCoordinator.get(level);
        coordinator.formed(origin, new BlockArray(Map.of(new BlockPos(0, -1, 0), new BlockPredicate.Any(),
                far.subtract(origin), new BlockPredicate.Any())), true, true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(lit(helper, local), "Discovery and notification finished"))
                .thenExecute(() -> coordinator.remove(origin))
                .thenWaitUntil(() -> {
                    helper.assertTrue(!lit(helper, local), "Cleanup finished");
                    helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                            "Visual updates never loaded the remote chunk");
                }).thenSucceed();
    }

    private record Fixture(BlockPos origin, BlockPos privateCoil, BlockPos passive, MachineControllerBlockEntity controller) {}

    private static Fixture place(GameTestHelper helper, BlockPos shared, Direction facing, Block coil, Block passive) {
        var shape = MachineRegistry.getCompiledStages(MACHINE).getFirst().rotatedPattern(facing);
        var origin = shared.subtract(offset(shape, 'S'));
        var privateCoil = origin.offset(offset(shape, 'Z'));
        var untagged = origin.offset(offset(shape, 'P'));
        helper.setBlock(privateCoil, coil);
        helper.setBlock(untagged, passive);
        helper.setBlock(origin, ModBlocks.controllerFor(MACHINE).get().defaultBlockState().setValue(MachineControllerBlock.FACING, facing));
        return new Fixture(origin, privateCoil, untagged, helper.getBlockEntity(origin));
    }

    private static BlockPos offset(BlockArray shape, char symbol) {
        return shape.symbolsByPosition().entrySet().stream().filter(e -> e.getValue() == symbol).findFirst().orElseThrow().getKey();
    }

    private static boolean lit(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(BlockStateProperties.LIT);
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("mmcr_test", path)).orElseThrow();
    }
}
