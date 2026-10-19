package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Exercises controller placement with real player views and registered controller options.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerPlacementGameTest {
    public static final ResourceLocation REQUIRED_VERTICAL_MACHINE = MMCR.id("controller_placement_required_vertical");
    private static final ResourceLocation HORIZONTAL_MACHINE = MMCR.id("test_cube");
    private static final ResourceLocation OPTIONAL_VERTICAL_MACHINE = MMCR.id("expandable_structure_vertical_roll");
    private static final float[] VERTICAL_YAWS = {0, 90, 180, 270, 44.99F, 45, -45, -45.01F};
    private static final Direction[] HORIZONTAL_FRONTS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
            Direction.NORTH, Direction.EAST, Direction.NORTH, Direction.WEST};
    private static final Direction[] UPWARD_ROLLS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST, Direction.SOUTH, Direction.EAST};

    private ControllerPlacementGameTest() {
    }

    public static void horizontalPlacementFollowsEnrichmentChamber(GameTestHelper helper) {
        float[] yaws = {0, 90, 180, 270, 44.99F, 45, 134.99F, 135, -45, -45.01F, 405};
        Direction[] fronts = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
                Direction.NORTH, Direction.EAST, Direction.EAST, Direction.SOUTH,
                Direction.NORTH, Direction.WEST, Direction.EAST};
        for (int i = 0; i < yaws.length; i++) {
            for (float pitch : new float[]{-90, 0, 90}) {
                assertPlacement(helper, HORIZONTAL_MACHINE, yaws[i], pitch, fronts[i], fronts[i]);
            }
        }
        helper.succeed();
    }

    public static void optionalVerticalPlacementFollowsMeDrive(GameTestHelper helper) {
        for (int i = 0; i < VERTICAL_YAWS.length; i++) {
            for (float pitch : new float[]{-65, 0, 65}) {
                assertPlacement(helper, OPTIONAL_VERTICAL_MACHINE, VERTICAL_YAWS[i], pitch,
                        HORIZONTAL_FRONTS[i], HORIZONTAL_FRONTS[i]);
            }
            assertPlacement(helper, OPTIONAL_VERTICAL_MACHINE, VERTICAL_YAWS[i], 65.01F,
                    Direction.UP, UPWARD_ROLLS[i]);
            assertPlacement(helper, OPTIONAL_VERTICAL_MACHINE, VERTICAL_YAWS[i], -65.01F,
                    Direction.DOWN, HORIZONTAL_FRONTS[i]);
        }
        helper.succeed();
    }

    public static void requiredVerticalPlacementFollowsPitchSign(GameTestHelper helper) {
        for (int i = 0; i < VERTICAL_YAWS.length; i++) {
            for (float pitch : new float[]{0, 0.01F, 90}) {
                assertPlacement(helper, REQUIRED_VERTICAL_MACHINE, VERTICAL_YAWS[i], pitch,
                        Direction.UP, UPWARD_ROLLS[i]);
            }
            for (float pitch : new float[]{-0.01F, -90}) {
                assertPlacement(helper, REQUIRED_VERTICAL_MACHINE, VERTICAL_YAWS[i], pitch,
                        Direction.DOWN, HORIZONTAL_FRONTS[i]);
            }
        }
        helper.succeed();
    }

    public static void placementWithoutPlayerUsesReferenceFallbacks(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (Direction clickedFace : Direction.values()) {
            assertState(helper, placement(helper, HORIZONTAL_MACHINE, null, pos, clickedFace),
                    Direction.NORTH, Direction.NORTH, "Mekanism null-player fallback");
            assertState(helper, placement(helper, OPTIONAL_VERTICAL_MACHINE, null, pos, clickedFace),
                    Direction.SOUTH, Direction.SOUTH, "AE2 null-player fallback");
            assertState(helper, placement(helper, REQUIRED_VERTICAL_MACHINE, null, pos, clickedFace),
                    Direction.UP, Direction.NORTH, "Forced-vertical null-player fallback");
        }
        helper.succeed();
    }

    private static void assertPlacement(GameTestHelper helper, ResourceLocation machineId, float yaw, float pitch,
                                        Direction facing, Direction up) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (Vec3 offset : new Vec3[]{new Vec3(-3, 4, 2), new Vec3(3, -2, -4)}) {
            player.setPos(Vec3.atCenterOf(pos).add(offset));
            for (Direction clickedFace : Direction.values()) {
                BlockState state = placement(helper, machineId, player, pos, clickedFace);
                String scenario = machineId + " yaw=" + yaw + " pitch=" + pitch + " face=" + clickedFace + " offset=" + offset;
                assertState(helper, state, facing, up, scenario);
                if (facing.getAxis().isVertical() && pitch != 0.0F) {
                    Vec3 viewUp = player.getUpVector(1.0F);
                    Direction structureUp = BlockRotator.rotateDirection(Direction.UP, facing,
                            state.getValue(MachineControllerBlock.ROLL_FACING));
                    helper.assertTrue(viewUp.x * structureUp.getStepX() + viewUp.z * structureUp.getStepZ() > 0.0D,
                            "Controller top must align with the player's view-up projection: " + scenario);
                }
            }
        }
    }

    private static BlockState placement(GameTestHelper helper, ResourceLocation machineId, Player player,
                                         BlockPos pos, Direction clickedFace) {
        var block = ModBlocks.controllerFor(machineId).get();
        var context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                block.asItem().getDefaultInstance(), new BlockHitResult(Vec3.atCenterOf(pos), clickedFace, pos, false));
        return block.getStateForPlacement(context);
    }

    private static void assertState(GameTestHelper helper, BlockState state, Direction facing, Direction up, String scenario) {
        helper.assertTrue(state != null, "Placement must produce a state: " + scenario);
        helper.assertTrue(state.getValue(MachineControllerBlock.FACING) == facing, "Wrong controller front: " + scenario);
        helper.assertTrue(state.getValue(MachineControllerBlock.ROLL_FACING) == up, "Wrong controller roll: " + scenario);
        if (facing.getAxis().isVertical()) {
            helper.assertTrue(BlockRotator.rotateDirection(Direction.UP, facing,
                    state.getValue(MachineControllerBlock.ROLL_FACING)) == up, "Structure up must follow player view: " + scenario);
        }
    }
}
