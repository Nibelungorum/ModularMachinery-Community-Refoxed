package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.MMCR;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.Set;
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
    void scan_plans_reuse_pattern_identity_and_normalize_sentinel_counts() {
        BlockArray pattern = pattern();
        Machine machine = new DynamicMachine(MMCR.id("scan_plan_cache"), "Scan Plan Cache", pattern);
        CompiledMachinePattern compiled = new CompiledMachinePattern(machine, Map.of(Direction.SOUTH, pattern),
                Map.of(), Map.of(), Map.of());
        CompiledMachinePattern.ScanPlan plan = compiled.scanPlan(Direction.SOUTH, 2);

        assertThat(compiled.scanPlan(Direction.SOUTH, 2)).isSameAs(plan);
        assertThat(CompiledMachinePattern.ScanPlan.forPattern(pattern, 2)).isSameAs(plan);
        assertThat(compiled.scanPlan(Direction.SOUTH, 0)).isNotSameAs(plan);
        assertThat(compiled.scanPlan(Direction.SOUTH, -1)).isSameAs(compiled.scanPlan(Direction.SOUTH, 0));
        assertThat(plan.entryPositions()).containsExactlyElementsOf(pattern.pattern().keySet());
        assertThat(plan.sentinelIndexes()).hasSize(2);
        assertThat(compiled.scanPlan(Direction.NORTH, 2).entryPositions()).isEmpty();
    }

    @Test
    void equal_patterns_with_different_entry_order_keep_their_own_scan_plans() {
        BlockPredicate predicate = new BlockPredicate.OfBlock(Blocks.STONE);
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(1, 0, 0);
        Map<BlockPos, BlockPredicate> forward = new LinkedHashMap<>();
        forward.put(first, predicate);
        forward.put(second, predicate);
        Map<BlockPos, BlockPredicate> reverse = new LinkedHashMap<>();
        reverse.put(second, predicate);
        reverse.put(first, predicate);
        BlockArray forwardPattern = new BlockArray(forward);
        BlockArray reversePattern = new BlockArray(reverse);
        assertThat(forwardPattern).isEqualTo(reversePattern);

        var forwardPlan = CompiledMachinePattern.ScanPlan.forPattern(forwardPattern, 1);
        var reversePlan = CompiledMachinePattern.ScanPlan.forPattern(reversePattern, 1);

        assertThat(forwardPlan).isNotSameAs(reversePlan);
        assertThat(forwardPlan.entryPositions()).containsExactly(first, second);
        assertThat(reversePlan.entryPositions()).containsExactly(second, first);
        assertThat(forwardPlan.entryPredicates()).containsExactly(predicate, predicate);
        assertThat(reversePlan.entryPredicates()).containsExactly(predicate, predicate);
    }

    @Test
    void replacing_structure_caches_evicts_compiled_and_standalone_scan_plans() {
        BlockArray pattern = pattern();
        Machine machine = new DynamicMachine(MMCR.id("scan_plan_reload"), "Scan Plan Reload", pattern);
        MachineRegistry.register(machine);
        for (Runnable reload : List.<Runnable>of(MachineRegistry::rebuildCompiledCache,
                () -> MachineRegistry.installStructures(Map.of()),
                () -> BlockArrayCache.buildCache(List.of(machine)))) {
            CompiledMachinePattern compiled = MachineRegistry.getCompiled(machine.registryName());
            BlockArray rotated = compiled.rotatedPattern(Direction.SOUTH);
            var compiledPlan = compiled.scanPlan(Direction.SOUTH, 2);
            var standalonePlan = CompiledMachinePattern.ScanPlan.forPattern(pattern, 2);

            reload.run();

            var reloadedPlan = MachineRegistry.getCompiled(machine.registryName()).scanPlan(Direction.SOUTH, 2);
            assertThat(reloadedPlan).isNotSameAs(compiledPlan);
            assertThat(reloadedPlan.entryPositions()).containsExactlyElementsOf(compiledPlan.entryPositions());
            assertThat(CompiledMachinePattern.ScanPlan.forPattern(rotated, 2)).isNotSameAs(compiledPlan);
            assertThat(CompiledMachinePattern.ScanPlan.forPattern(pattern, 2)).isNotSameAs(standalonePlan);
        }
    }

    @Test
    void registering_machine_builds_compiled_pattern_and_prewarms_rotations() {
        BlockArray pattern = pattern();
        Machine machine = new DynamicMachine(MMCR.id("compiled_test"), "Compiled Test", pattern);

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
    void compiler_derives_coupler_positions_from_the_structure_pattern() {
        ResourceLocation id = ResourceLocation.parse("mmcr:coupler_pattern");
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, BlockPredicate.machineCoupler(),
                new BlockPos(1, 0, 0), BlockPredicate.machineCoupler()));
        Machine machine = new DynamicMachine(id, "Coupler Pattern", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.couplerPositions(Direction.SOUTH))
                .containsExactlyInAnyOrder(BlockPos.ZERO, new BlockPos(1, 0, 0));
    }

    @Test
    void clear_for_testing_clears_compiled_patterns() {
        Machine machine = new DynamicMachine(MMCR.id("clear_test"), "Clear Test", pattern());
        MachineRegistry.register(machine);
        var plan = CompiledMachinePattern.ScanPlan.forPattern(machine.pattern(), 2);

        MachineRegistry.clearForTesting();

        assertThat(MachineRegistry.getCompiled(machine.registryName())).isNull();
        assertThat(CompiledMachinePattern.ScanPlan.forPattern(machine.pattern(), 2)).isNotSameAs(plan);
    }

    @Test
    void compiled_pattern_contains_rotated_replacements_for_horizontal_facing() {
        ResourceLocation id = MMCR.id("compiled_replacement");
        BlockPos rawPos = new BlockPos(-1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        var machine = new DynamicMachine(
                id, "Compiled Replacement", new BlockArray(Map.of(
                        BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                        rawPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                        new BlockPos(0, 0, 1), new BlockPredicate.AnyOf(List.of(
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
        ResourceLocation id = MMCR.id("plain_machine");
        Machine machine = new PlainMachine(id, pattern(), MachineControllerSpec.defaultsFor(id));

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.modifierReplacements(Direction.SOUTH)).isEmpty();
        assertThat(compiled.modifierReplacements(Direction.UP, Direction.NORTH)).isEmpty();
    }

    @Test
    void compiler_preserves_stage_specific_pattern_data() {
        ResourceLocation id = ResourceLocation.parse("mmcr:compiled_stage");
        BlockArray first = pattern();
        BlockArray second = new BlockArray(Map.of(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        MachineBehavior behavior = TickBehavior.builder().build();
        Machine machine = new DynamicMachine(id, "Compiled Stage", first);
        machine = new Machine() {
            @Override public ResourceLocation registryName() { return machineId(); }
            @Override public BlockArray pattern() { return first; }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public List<MachineStructureStage> structureStages() {
                return List.of(new MachineStructureStage(1, first, PortRequirementSpec.none(),
                        PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY),
                        new MachineStructureStage(2, second, PortRequirementSpec.none(),
                                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY));
            }
            @Override public MachineBehavior behavior() { return behavior; }
            private ResourceLocation machineId() { return id; }
        };

        List<CompiledMachinePattern> stages = MachinePatternCompiler.compileStages(machine, null);

        assertThat(stages).extracting(CompiledMachinePattern::stageNumber).containsExactly(1, 2);
        assertThat(stages.get(0).boundingBox(Direction.SOUTH).maxX()).isEqualTo(1);
        assertThat(stages.get(1).boundingBox(Direction.SOUTH).maxX()).isEqualTo(2);
        assertThat(stages.get(1).machine().pattern()).isEqualTo(second);
        assertThat(stages.get(1).machine().behavior()).isSameAs(behavior);
    }

    @Test
    void stage_compiled_modifier_replacements_support_vertical_roll_without_parent_type_checks() {
        ResourceLocation id = ResourceLocation.parse("mmcr:compiled_stage_modifiers");
        BlockPos rawPosition = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("stage_modifier", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        BlockArray first = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE)));
        BlockPos stagePort = new BlockPos(2, 0, 0);
        BlockArray second = new BlockArray(Map.of(rawPosition, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                stagePort, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get())),
                Map.of(), Map.of(rawPosition, 'M', stagePort, 'P'));
        MachineStructureRequirements secondRequirements = MachineStructureRequirements.builder()
                .modifier('M', replacement)
                .build(second);
        DynamicPatternSpec dynamic = new DynamicPatternSpec("stage_dynamic", new BlockArray(Map.of()), null,
                0, 1, BlockPos.ZERO, BlockPos.ZERO, Set.of(Direction.SOUTH));
        Machine machine = new Machine() {
            @Override public ResourceLocation registryName() { return id; }
            @Override public BlockArray pattern() { return first; }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public List<MachineStructureStage> structureStages() {
                return List.of(new MachineStructureStage(1, first, PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                                List.of(), MachineStructureRequirements.EMPTY),
                        new MachineStructureStage(2, second, PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                                List.of(dynamic), secondRequirements));
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
                new BlockPos(0, 0, 1), new BlockPredicate.AnyOf(List.of(
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get())))))
                .tagged(new BlockPos(0, 0, 1), "port:item_input_bus");
    }

    private record PlainMachine(
            ResourceLocation registryName,
            BlockArray pattern,
            MachineControllerSpec controller
    ) implements Machine {
    }
}
