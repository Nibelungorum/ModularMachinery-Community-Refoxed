package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.nibelungorum.DefaultMachines;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 BuildCommand.placeMachine 与 StructureMatcher.matches 在同一 ctrlFacing 下
 * 共享 BlockRotator.rotateYCCWSouthUntil,贴盘与检查 position 一致。
 */
class BuildPlacementConsistencyTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void clearRegistry() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void struct_round_trip_in_each_horizontal_facing() {
        Machine machine = fixture();
        MachineRegistry.register(machine);
        for (Direction ctrlFacing : Direction.Plane.HORIZONTAL) {
            BlockPos controller = new BlockPos(50, 4, 50);
            Map<BlockPos, BlockState> world = buildPlacement(machine, controller, ctrlFacing);
            assertThat(StructureMatcher.matches(machine.pattern(), levelFor(world), controller, ctrlFacing))
                    .as("结构 facing=%s 应该 round-trip 成 form", ctrlFacing)
                    .isTrue();
        }
    }

    @Test
    void build_placement_does_not_overwrite_controller_with_pattern_controller_block() {
        Machine machine = fixture();
        BlockPos controller = new BlockPos(50, 4, 50);

        Map<BlockPos, BlockState> world = buildPlacement(machine, controller, Direction.EAST);

        assertThat(world.get(controller).getBlock()).isEqualTo(ModBlocks.controllerFor(machine).get());
    }

    @Test
    void controller_facing_requires_matching_rotated_structure() {
        Machine machine = fixture();
        BlockPos controller = new BlockPos(50, 4, 50);
        Map<BlockPos, BlockState> world = buildPlacement(machine, controller, Direction.NORTH);

        assertThat(StructureMatcher.matches(machine.pattern(), levelFor(world), controller, Direction.NORTH)).isTrue();
        assertThat(StructureMatcher.matches(machine.pattern(), levelFor(world), controller, Direction.EAST))
                .as("未旋转的 NORTH 结构不应在 EAST controller facing 下成型")
                .isFalse();
    }

    /**
     * 模拟 BuildCommand.placeMachine 的写盘:
     * 对 pattern 每格 (rel, predicate),按 controller facing 旋转后写到 worldPos,
     * 解析 predicate 为 BlockState 写到 written map。
     */
    private static Map<BlockPos, BlockState> buildPlacement(Machine machine, BlockPos controller, Direction ctrlFacing) {
        Map<BlockPos, BlockState> written = new LinkedHashMap<>();
        BlockState ctrlBase = ModBlocks.controllerFor(machine).get().defaultBlockState();
        BlockState ctrlFinal = ctrlBase.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? ctrlBase.setValue(BlockStateProperties.HORIZONTAL_FACING, ctrlFacing)
                : ctrlBase;
        written.put(controller, ctrlFinal);
        for (var entry : machine.pattern().pattern().entrySet()) {
            if (entry.getKey().equals(BlockPos.ZERO)) continue;
            BlockPos world = controller.offset(BlockRotator.rotateYCCWSouthUntil(entry.getKey(), ctrlFacing));
            written.put(world, resolve(entry.getValue()));
        }
        return written;
    }

    private static BlockState resolve(BlockPredicate p) {
        return switch (p) {
            case BlockPredicate.OfBlock of -> of.block().defaultBlockState();
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream()
                    .filter(c -> c instanceof BlockPredicate.OfBlock)
                    .map(c -> ((BlockPredicate.OfBlock) c).block().defaultBlockState())
                    .findFirst().orElse(null);
            case BlockPredicate.Air ignored -> null;
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    /**
     * 简单的 mock Level:每个 world pos 直接放它对应的 block。predicates 用 BlockState 而非 Block,
     * 把 BlockState 的 getBlock 传给 LevelStub。
     */
    private static Level levelFor(Map<BlockPos, BlockState> states) {
        Map<BlockPos, Block> blocks = new HashMap<>();
        for (var entry : states.entrySet()) blocks.put(entry.getKey(), entry.getValue().getBlock());
        return LevelStub.create(blocks);
    }

    private static Machine fixture() {
        return DefaultMachines.blastFurnace(
                Blocks.STONE, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);
    }
}
