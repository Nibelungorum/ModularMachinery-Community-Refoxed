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
        BlockPos rawPos = new BlockPos(-1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        var machine = new DynamicMachine(
                id, "Compiled Replacement", new BlockArray(Map.of(
                        BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                        rawPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                        new BlockPos(0, 0, 1), new BlockPredicate.AnyOf(java.util.List.of(
                                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()))))),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
                Map.of(rawPos, List.of(replacement)));

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertThat(compiled.modifierReplacements(facing))
                    .containsKey(BlockRotator.rotateSouthTo(rawPos, facing));
        }
        assertThat(compiled.rotatedPattern(Direction.SOUTH).pattern()).hasSize(3);
        assertThat(compiled.componentPositions(Direction.SOUTH)).containsExactly(new BlockPos(0, 0, 1));
        assertThat(compiled.portPositions(Direction.SOUTH)).containsExactly(new BlockPos(0, 0, 1));
        assertThat(compiled.boundingBox(Direction.SOUTH).minX()).isEqualTo(-1);
        assertThat(compiled.boundingBox(Direction.SOUTH).maxX()).isEqualTo(0);
        assertThat(compiled.boundingBox(Direction.SOUTH).minZ()).isEqualTo(0);
        assertThat(compiled.boundingBox(Direction.SOUTH).maxZ()).isEqualTo(1);
    }

    @Test
    void compiler_supports_non_dynamic_machine_without_replacements() {
        Identifier id = Identifier.fromNamespaceAndPath("mmcr", "plain_machine");
        Machine machine = new PlainMachine(id, "Plain Machine", pattern(), MachineControllerSpec.defaultsFor(id));

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.modifierReplacements(Direction.SOUTH)).isEmpty();
        assertThat(compiled.modifierReplacements(Direction.UP, Direction.NORTH)).isEmpty();
    }

    @Test
    void compiler_preserves_stage_specific_pattern_data() {
        Identifier id = Identifier.parse("mmcr:compiled_stage");
        BlockArray first = pattern();
        BlockArray second = new BlockArray(Map.of(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        Machine machine = new DynamicMachine(id, "Compiled Stage", first);
        machine = new Machine() {
            @Override public Identifier registryName() { return machineId(); }
            @Override public BlockArray pattern() { return first; }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public List<MachineStructureStage> structureStages() {
                return List.of(new MachineStructureStage(1, first, PortRequirementSpec.none(),
                        PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of()),
                        new MachineStructureStage(2, second, PortRequirementSpec.none(),
                                PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of()));
            }
            private Identifier machineId() { return id; }
        };

        List<CompiledMachinePattern> stages = MachinePatternCompiler.compileStages(machine, null);

        assertThat(stages).extracting(CompiledMachinePattern::stageNumber).containsExactly(1, 2);
        assertThat(stages.get(0).boundingBox(Direction.SOUTH).maxX()).isEqualTo(1);
        assertThat(stages.get(1).boundingBox(Direction.SOUTH).maxX()).isEqualTo(2);
        assertThat(stages.get(1).machine().pattern()).isEqualTo(second);
    }

    @Test
    void stage_compiled_modifier_replacements_support_vertical_roll_without_parent_type_checks() {
        Identifier id = Identifier.parse("mmcr:compiled_stage_modifiers");
        BlockPos rawPosition = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("stage_modifier", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        BlockArray first = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE)));
        BlockPos stagePort = new BlockPos(2, 0, 0);
        BlockArray second = new BlockArray(Map.of(rawPosition, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                stagePort, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get())));
        DynamicPatternSpec dynamic = new DynamicPatternSpec("stage_dynamic", new BlockArray(Map.of()), null,
                0, 1, BlockPos.ZERO, BlockPos.ZERO, java.util.Set.of(Direction.SOUTH));
        Machine machine = new Machine() {
            @Override public Identifier registryName() { return id; }
            @Override public BlockArray pattern() { return first; }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public List<MachineStructureStage> structureStages() {
                return List.of(new MachineStructureStage(1, first, PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                                List.of(), Map.of(), Map.of()),
                        new MachineStructureStage(2, second, PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                                List.of(dynamic), Map.of(rawPosition, List.of(replacement)), Map.of()));
            }
        };

        List<MachineStructureStage> parentStages = machine.structureStages();
        CompiledMachinePattern compiled = MachinePatternCompiler.compileStages(machine, null).get(1);
        BlockPos rotated = BlockRotator.rotateSouthTo(rawPosition, Direction.UP, Direction.EAST);

        assertThat(compiled.modifierReplacements(Direction.UP, Direction.EAST)).containsKey(rotated);
        assertThat(compiled.modifierReplacements(Direction.SOUTH)).containsKey(rawPosition);
        assertThat(compiled.dynamicPatterns()).hasSize(1);
        assertThat(compiled.componentPositions(Direction.SOUTH)).contains(stagePort);
        assertThat(compiled.portPositions(Direction.SOUTH)).contains(stagePort);
        assertThat(machine.structureStages()).isEqualTo(parentStages);
        assertThat(machine.pattern()).isEqualTo(first);
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

    private record PlainMachine(
            Identifier registryName,
            String localizedName,
            BlockArray pattern,
            MachineControllerSpec controller
    ) implements Machine {
    }
}
