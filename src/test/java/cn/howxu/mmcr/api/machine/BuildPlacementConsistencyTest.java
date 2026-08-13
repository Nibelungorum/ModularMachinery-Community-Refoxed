package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 MultiblockAssemblyService 与 StructureMatcher.matches 在同一 ctrlFacing 下
 * 共享 BlockArrayCache 旋转后的 position 一致。
 */
class BuildPlacementConsistencyTest {

    private static final Identifier MACHINE_ID = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace");

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
    void struct_round_trip_in_each_vertical_facing_when_transform_is_used() {
        Machine machine = fixture();
        MachineRegistry.register(machine);
        for (Direction ctrlFacing : List.of(Direction.UP, Direction.DOWN)) {
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

        assertThat(world.get(controller).getBlock()).isEqualTo(ModBlocks.controllerFor(machine.registryName()).get());
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

    @Test
    void unsupported_predicates_are_not_placed_as_stone_fallback() {
        Machine machine = fixture();
        BlockArray rotatedPattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine.registryName()).get()),
                BlockPos.ZERO.east(), new BlockPredicate.Any()
        ));

        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, rotatedPattern);

        assertThat(placements).noneMatch(placement -> placement.state().is(Blocks.STONE));
    }

    /**
     * 模拟 assembly service 写盘:先放 controller,再按已旋转 pattern 生成待放置方块。
     */
    private static Map<BlockPos, BlockState> buildPlacement(Machine machine, BlockPos controller, Direction ctrlFacing) {
        Map<BlockPos, BlockState> written = new LinkedHashMap<>();
        BlockState ctrlBase = ModBlocks.controllerFor(machine.registryName()).get().defaultBlockState();
        BlockState ctrlFinal = ctrlBase.hasProperty(BlockStateProperties.FACING)
                ? ctrlBase.setValue(BlockStateProperties.FACING, ctrlFacing)
                : ctrlBase;
        written.put(controller, ctrlFinal);
        BlockArray rotatedPattern = BlockArrayCache.get(machine.pattern(), ctrlFacing);
        for (var placement : MultiblockAssemblyService.createTemplatePlacements(controller, rotatedPattern)) {
            written.put(placement.pos(), placement.state());
        }
        return written;
    }

    /**
     * 简单的 mock Level:每个 world pos 直接放它对应的 block。predicates 用 BlockState 而非 Block,
     * 把 BlockState 的 getBlock 传给 LevelStub。
     */
    private static Level levelFor(Map<BlockPos, BlockState> states) {
        Map<BlockPos, Block> blocks = new HashMap<>();
        for (var entry : states.entrySet()) {
            if (entry.getValue() != null) blocks.put(entry.getKey(), entry.getValue().getBlock());
        }
        return LevelStub.create(blocks);
    }

    private static Machine fixture() {
        BlockArray pattern = BlockArray.builder()
                .pattern("AAA", "ACA", "AAA")
                .pattern("ABA", "A A", "ABA")
                .pattern("AAA", "ACA", "AAA")
                .set('A', new BlockPredicate.OfBlock(Blocks.STONE))
                .set('B', new BlockPredicate.OfBlock(Blocks.OAK_PLANKS))
                .set('C', new BlockPredicate.OfBlock(ModBlocks.controllerFor(MACHINE_ID).get()))
                .build();
        return new DynamicMachine(
                MACHINE_ID,
                "machine.mmcr.blast_furnace",
                pattern,
                MachineControllerSpec.defaultsFor(MACHINE_ID),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of());
    }
}
