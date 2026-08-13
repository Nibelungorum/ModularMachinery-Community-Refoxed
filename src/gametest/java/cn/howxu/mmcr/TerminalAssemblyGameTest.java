package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Covers terminal-backed structure build and demolish operations in GameTest worlds.
 *
 * @author howxu <dev@howxu.cn>
 */
public class TerminalAssemblyGameTest {

    public void buildSkipsOccupiedPositionsAndPlacesOnlyMissingBlocks(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        Block preexistingBlock = Blocks.COBBLESTONE;

        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        List<MultiblockAssemblyService.Placement> template = template(controller);
        BlockPos occupiedPos = template.get(0).pos();
        BlockPos missingPos = template.get(1).pos();
        helper.getLevel().setBlock(occupiedPos, preexistingBlock.defaultBlockState(), 3);

        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, true);

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Build succeeds in creative service mode");
        helper.assertTrue(helper.getLevel().getBlockState(missingPos).is(ModBlocks.CASING.get()), "Missing block is built");
        helper.assertTrue(helper.getLevel().getBlockState(occupiedPos).is(preexistingBlock), "Occupied block is preserved");
        helper.assertTrue(result.changedBlocks() == template.size() - 1, "Build places only missing structure blocks");
        helper.succeed();
    }

    public void demolishSkipsAirAndNonMatchingBlocks(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        Block nonMatchingBlock = Blocks.COBBLESTONE;

        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        List<MultiblockAssemblyService.Placement> template = template(controller);
        BlockPos removedPos = template.get(0).pos();
        BlockPos airPos = template.get(1).pos();
        BlockPos nonMatchingPos = template.get(2).pos();
        BlockPos cappedMatchingPos = template.get(3).pos();
        for (MultiblockAssemblyService.Placement placement : template) {
            if (!placement.pos().equals(airPos) && !placement.pos().equals(nonMatchingPos)) {
                helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
            }
        }
        helper.getLevel().setBlock(nonMatchingPos, nonMatchingBlock.defaultBlockState(), 3);

        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.demolish(player, controller, 1, stack -> {});

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Demolish succeeds in service mode");
        helper.assertTrue(helper.getLevel().getBlockState(removedPos).isAir(), "Matching block is removed");
        helper.assertTrue(helper.getLevel().getBlockState(airPos).isAir(), "Existing air stays air");
        helper.assertTrue(helper.getLevel().getBlockState(nonMatchingPos).is(nonMatchingBlock), "Non-matching block is preserved");
        helper.assertTrue(helper.getLevel().getBlockState(cappedMatchingPos).is(ModBlocks.CASING.get()), "Cap preserves later matching blocks");
        helper.assertTrue(result.changedBlocks() == 1, "Demolish obeys the requested cap");
        helper.succeed();
    }

    private static List<MultiblockAssemblyService.Placement> template(MachineControllerBlockEntity controller) {
        var machine = controller.boundMachine().orElseThrow();
        return MultiblockAssemblyService.createTemplatePlacements(controller.getBlockPos(),
                controller.assemblyCandidatePatterns(machine).getFirst());
    }

    private static ServerPlayer servicePlayer(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-terminal-gametest".getBytes(StandardCharsets.UTF_8)), "mmcr-terminal"),
                ClientInformation.createDefault());
    }
}
