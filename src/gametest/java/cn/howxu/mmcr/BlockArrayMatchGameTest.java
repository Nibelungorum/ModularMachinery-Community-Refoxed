package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.api.facade.behavior.BehaviorAdapters;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

public class BlockArrayMatchGameTest {

    public static void requiredAirIsNotPadding(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        var pattern = BlockArray.builder().noController().pattern(" ..").set('.', new BlockPredicate.Air()).build();
        level.setBlockAndUpdate(origin.west(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.STONE.defaultBlockState());
        var mismatch = StructureMatcher.firstMismatch(pattern, level, origin).orElseThrow();
        helper.assertTrue(mismatch.worldPos().equals(origin.east()), "Only the required air slot must be empty");
        level.setBlockAndUpdate(origin.east(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(StructureMatcher.matchesRotated(pattern, level, origin), "Padding accepts the remaining stone");
        helper.succeed();
    }

    public void structureForms3x3Casing(GameTestHelper helper) {
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());

        BlockPos ctrlPos = new BlockPos(1, 1, 1);
        helper.setBlock(ctrlPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());

        var machine = MachineRegistry.getMachine(MMCR.id("test_cube"));

        var be = helper.getBlockEntity(ctrlPos, MachineControllerBlockEntity.class);
        be.setMachine(machine);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(be.structureSnapshot().formed(), "Structure formed after bounded scan");
            helper.assertTrue(be.behaviorContext().countStructureBlocks(ModBlocks.CASING.get()) == 8,
                    "Formed structure records the eight casing blocks");
            helper.assertTrue(be.behaviorContext().countStructureBlocks("mmcr:basic_casing") == 8,
                    "Registry-name lookup matches the Block lookup");
            var publicContext = BehaviorAdapters.wrap(be.behaviorContext());
            helper.assertTrue(publicContext.countStructureBlocks(MMCR.id("basic_casing")) == 8,
                    "Public Identifier lookup matches the formed structure");
            helper.assertTrue(publicContext.controller().countStructureBlocks(MMCR.id("basic_casing")) == 8,
                    "Public controller view forwards Identifier lookup");
            boolean rejected = false;
            try {
                publicContext.countStructureBlocks(Identifier.parse("missingmod:not_a_block"));
            } catch (IllegalArgumentException ignored) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Unknown block registry names are rejected");
            be.invalidateFormedStructure();
            helper.assertTrue(be.behaviorContext().countStructureBlocks(ModBlocks.CASING.get()) == 0,
                    "Unformed structures do not retain block counts");
            helper.succeed();
        });
    }
}
