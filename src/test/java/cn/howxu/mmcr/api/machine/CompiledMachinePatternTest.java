package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompiledMachinePatternTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
        BlockArrayCache.clearForTesting();
    }

    @Test
    void registering_machine_builds_compiled_pattern_and_prewarms_rotations() {
        BlockArray pattern = pattern();
        Machine machine = new DynamicMachine(Identifier.fromNamespaceAndPath("mmcr", "compiled_test"), "Compiled Test", pattern);

        MachineRegistry.register(machine);

        CompiledMachinePattern compiled = MachineRegistry.getCompiled(machine.registryName());
        assertThat(compiled).isNotNull();
        assertThat(compiled.machine()).isEqualTo(machine);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertThat(compiled.rotatedPattern(facing)).isSameAs(BlockArrayCache.get(pattern, facing));
        }
        assertThat(compiled.componentPositions(Direction.SOUTH)).containsExactly(new BlockPos(0, 0, 1));
        assertThat(compiled.portPositions(Direction.SOUTH)).containsExactly(new BlockPos(0, 0, 1));
        assertThat(compiled.boundingBox(Direction.SOUTH).minX()).isEqualTo(0);
        assertThat(compiled.boundingBox(Direction.SOUTH).maxX()).isEqualTo(1);
        assertThat(compiled.boundingBox(Direction.SOUTH).minZ()).isEqualTo(0);
        assertThat(compiled.boundingBox(Direction.SOUTH).maxZ()).isEqualTo(1);
    }

    @Test
    void clear_for_testing_clears_compiled_patterns() {
        Machine machine = new DynamicMachine(Identifier.fromNamespaceAndPath("mmcr", "clear_test"), "Clear Test", pattern());
        MachineRegistry.register(machine);

        MachineRegistry.clearForTesting();

        assertThat(MachineRegistry.getCompiled(machine.registryName())).isNull();
    }

    @Test
    void compiled_pattern_contains_rotated_replacements_for_horizontal_facing() {
        Identifier id = Identifier.fromNamespaceAndPath("mmcr", "compiled_replacement");
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(), "", ItemStack.EMPTY);
        var machine = new DynamicMachine(
                id, "Compiled Replacement", new BlockArray(Map.of(
                        BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE))),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
                Map.of(new BlockPos(-1, 0, 0), List.of(replacement)));

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.modifierReplacements(Direction.EAST))
                .containsKey(new BlockPos(0, 0, 1));
    }

    private static BlockArray pattern() {
        return new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.FURNACE),
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(0, 0, 1), new BlockPredicate.AnyOf(java.util.List.of(
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get())))))
                .tagged(new BlockPos(0, 0, 1), "port:item_input_bus");
    }
}
