package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import org.nibelungorum.builtin.PublicBuiltinLevelDefinitions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import java.util.LinkedHashMap;

import net.minecraft.world.level.block.Block;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies schema construction from machine structures.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewSchemaFactoryTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindHolderTag(net.minecraft.world.level.block.Blocks.OAK_LOG.builtInRegistryHolder(), previewTag());
        bindHolderTag(net.minecraft.world.level.block.Blocks.BIRCH_LOG.builtInRegistryHolder(), previewTag());
    }

    private static TagKey<net.minecraft.world.level.block.Block> previewTag() {
        return TagKey.create(Registries.BLOCK, MMCR.id("preview_logs"));
    }

    private static void bindHolderTag(Holder<?> holder, TagKey<?> tag) throws Exception {
        java.lang.reflect.Method bindTags = Class.forName("net.minecraft.core.Holder$Reference")
                .getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        bindTags.invoke(holder, java.util.Set.of(tag));
    }

    @AfterEach
    void restoreDefaultLevels() {
        cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.resetCollector();
        var event = cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.prepare(java.util.Set.of());
        PublicBuiltinLevelDefinitions.register(event);
        MachineLevelRegistry.installSnapshot(event.levelTypes().values(), event.levels().values());
    }

    @Test
    void factory_uses_preferred_states_and_preserves_controller_relative_positions() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(1, 2, -1), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));
        DynamicMachine machine = new DynamicMachine(MMCR.id("preview_machine"), "machine.preview", pattern);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        assertThat(schema.machineId()).isEqualTo(MMCR.id("preview_machine"));
        assertThat(schema.stateAt(BlockPos.ZERO)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(schema.stateAt(new BlockPos(1, 2, -1))).isEqualTo(Blocks.GOLD_BLOCK.defaultBlockState());
    }

    @Test
    void factory_uses_directional_state_and_candidate_from_the_rotated_compiled_pattern() {
        BlockPos rawPosition = new BlockPos(1, 0, 0);
        BlockState southState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X);
        BlockArray pattern = new BlockArray(Map.of(rawPosition, new BlockPredicate.OfBlockState(southState)));
        BlockPos rotatedPosition = BlockRotator.rotateSouthTo(rawPosition, Direction.EAST);
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("rotated_preview"), Direction.EAST);

        assertThat(schema.stateAt(rotatedPosition)).isEqualTo(
                southState.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90));
        assertThat(schema.candidatesAt(rotatedPosition)).extracting(ItemStack::getItem)
                .containsExactly(Blocks.OAK_LOG.asItem());
    }

    @Test
    void factory_skips_unrepresentable_predicates_without_failing_the_schema() {
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()));
        DynamicMachine machine = new DynamicMachine(MMCR.id("unrepresentable"), "machine.unrepresentable", pattern);

        assertThat(new StructurePreviewSchemaFactory().create(machine).states()).isEmpty();
    }

    @Test
    void factory_adds_every_block_from_tag_to_preview_carousel() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfTag(previewTag())));
        Machine machine = new DynamicMachine(MMCR.id("tag_preview_machine"), "machine.tag_preview", pattern);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        assertThat(schema.previewCandidatesAt(BlockPos.ZERO).stream()
                .map(candidate -> candidate.stack().getItem().builtInRegistryHolder().getRegisteredName()))
                .contains("minecraft:oak_log", "minecraft:birch_log");
    }

    @Test
    void factory_rejects_machines_without_structure_stages() {
        BlockArray pattern = new BlockArray(Map.of());
        Machine machine = new Machine() {
            @Override
            public Identifier registryName() {
                return MMCR.id("empty_stages");
            }

            @Override
            public BlockArray pattern() {
                return pattern;
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(registryName());
            }

            @Override
            public List<MachineStructureStage> structureStages() {
                return List.of();
            }
        };

        assertThatThrownBy(() -> new StructurePreviewSchemaFactory().create(machine))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factory_keeps_level_slots_with_air_for_unresolved_states() {
        BlockPos resolved = BlockPos.ZERO;
        BlockPos unresolved = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                resolved, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                unresolved, new BlockPredicate.Any()), Map.of(), Map.of(resolved, 'A', unresolved, 'B'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern,
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                MachineStructureRequirements.builder()
                        .levelSlot('A', MMCR.id("coil"))
                        .levelSlot('B', MMCR.id("casing"))
                        .build(pattern));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("slots"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.levelSlots()).containsOnly(
                Map.entry(resolved, MMCR.id("coil")), Map.entry(unresolved, MMCR.id("casing")));
        assertThat(schema.stateAt(unresolved)).isEqualTo(Blocks.AIR.defaultBlockState());
    }

    @Test
    void factory_uses_only_the_complete_first_stage_of_a_multi_stage_machine() {
        BlockPos firstStageOnly = new BlockPos(1, 0, 0);
        BlockArray firstPattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                firstStageOnly, new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK)), Map.of(),
                Map.of(BlockPos.ZERO, 'A', firstStageOnly, 'B'));
        MachineStructureStage firstStage = new MachineStructureStage(1, firstPattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .levelSlot('B', MMCR.id("first_slot"))
                        .build(firstPattern));
        BlockPos finalStageOnly = new BlockPos(2, 0, 0);
        BlockArray finalPattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                finalStageOnly, new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK)), Map.of(),
                Map.of(BlockPos.ZERO, 'A', finalStageOnly, 'B'));
        MachineStructureStage finalStage = new MachineStructureStage(2, finalPattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .levelSlot('B', MMCR.id("final_slot"))
                        .build(finalPattern));
        Machine machine = machineWithStages(new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.EMERALD_BLOCK))), List.of(firstStage, finalStage));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        assertThat(schema.states()).containsOnly(
                Map.entry(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()),
                Map.entry(firstStageOnly, Blocks.AIR.defaultBlockState()));
        assertThat(schema.stateAt(finalStageOnly)).isNull();
        assertThat(schema.levelSlots()).containsOnly(Map.entry(firstStageOnly, MMCR.id("first_slot")));
    }

    @Test
    void factory_stage_overload_keeps_the_preferred_state_for_default_selection() {
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        BlockState preferredState = predicate.preferredState().orElseThrow();
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(BlockPos.ZERO, predicate)),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("default_variant"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.stateAt(BlockPos.ZERO)).isEqualTo(preferredState);
    }

    @Test
    void createBuildsSchemaForTheRequestedStructureStage() {
        MachineStructureStage second = stage(2, Blocks.GOLD_BLOCK.defaultBlockState());

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(second, MMCR.id("stage_preview"));

        assertThat(schema.stateAt(BlockPos.ZERO)).isEqualTo(Blocks.GOLD_BLOCK.defaultBlockState());
    }

    @Test
    void factory_keeps_every_concrete_block_option_as_a_preview_candidate() {
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get())));
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(BlockPos.ZERO, predicate)),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("port_candidates"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.candidatesAt(BlockPos.ZERO)).extracting(ItemStack::getItem).containsExactlyInAnyOrder(
                Blocks.IRON_BLOCK.asItem(), ModBlocks.BLOCKS.get("item_input_bus").get().asItem());
    }

    @Test
    void factory_keeps_deferred_block_options_as_preview_candidates() {
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.DeferredBlock(() -> ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPredicate.DeferredBlock(() -> ModBlocks.BLOCKS.get("item_input_bus_tiny").get())));
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(BlockPos.ZERO, predicate)),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("deferred_port_candidates"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.candidatesAt(BlockPos.ZERO)).extracting(ItemStack::getItem).containsExactlyInAnyOrder(
                ModBlocks.BLOCKS.get("item_input_bus").get().asItem(),
                ModBlocks.BLOCKS.get("item_input_bus_tiny").get().asItem());
    }

    @Test
    void factory_adds_modifier_replacement_blocks_after_the_original_preview_candidates() {
        BlockPos position = BlockPos.ZERO;
        BlockArray pattern = new BlockArray(Map.of(position, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)),
                Map.of(), Map.of(position, 'M'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                .modifier('M', modifierReplacement(position, Blocks.GOLD_BLOCK))
                .modifier('M', modifierReplacement(position, Blocks.DIAMOND_BLOCK))
                .modifier('M', new SingleBlockModifierReplacement("unrepresentable", new BlockPredicate.Any(), List.of(), ItemStack.EMPTY))
                .build(pattern));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("modifier_candidates"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.candidatesAt(position)).extracting(ItemStack::getItem).containsExactly(
                Blocks.IRON_BLOCK.asItem(), Blocks.GOLD_BLOCK.asItem(), Blocks.DIAMOND_BLOCK.asItem());
        assertThat(schema.previewCandidatesAt(position)).extracting(StructurePreviewSchema.Candidate::modifier).containsExactly(
                false, true, true);
    }

    @Test
    void factory_rotates_level_slots_and_modifier_candidates_with_the_pattern() {
        Identifier levelType = MMCR.id("rotated_preview_level");
        registerLevels(Map.of(levelType, List.of(Blocks.COPPER_BLOCK)));
        BlockPos rawPosition = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(
                Map.of(rawPosition, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)),
                Map.of(), Map.of(rawPosition, 'L'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .levelSlot('L', levelType)
                        .modifier('L', modifierReplacement(rawPosition, Blocks.GOLD_BLOCK))
                        .build(pattern));
        BlockPos rotatedPosition = BlockRotator.rotateSouthTo(rawPosition, Direction.EAST);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage,
                MMCR.id("rotated_preview_metadata"), Direction.EAST);

        assertThat(schema.levelSlotAt(rotatedPosition)).isEqualTo(levelType);
        assertThat(schema.stateAt(rotatedPosition)).isEqualTo(Blocks.COPPER_BLOCK.defaultBlockState());
        assertThat(schema.previewCandidatesAt(rotatedPosition)).extracting(candidate -> candidate.stack().getItem())
                .containsExactly(Blocks.IRON_BLOCK.asItem(), Blocks.GOLD_BLOCK.asItem());
        assertThat(schema.previewCandidatesAt(rotatedPosition)).extracting(StructurePreviewSchema.Candidate::modifier)
                .containsExactly(false, true);
    }

    @Test
    void factory_keeps_base_candidate_first_when_modifiers_have_higher_level_priority() {
        Identifier modifierLevels = MMCR.id("preview_modifier_priority");
        registerLevels(Map.of(modifierLevels, List.of(Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK)));
        BlockArray pattern = BlockArray.builder()
                .pattern("H")
                .set('H', new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE))
                .build();
        BlockPos position = pattern.pattern().keySet().iterator().next();
        MachineStructureRequirements requirements = MachineStructureRequirements.builder()
                .modifier('H', modifierReplacement(Blocks.DIAMOND_BLOCK))
                .modifier('H', modifierReplacement(Blocks.GOLD_BLOCK))
                .build(pattern);
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), requirements);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("modifier_priority_candidates"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.stateAt(position)).isEqualTo(Blocks.BLAST_FURNACE.defaultBlockState());
        assertThat(schema.candidatesAt(position)).extracting(ItemStack::getItem).containsExactly(
                Blocks.BLAST_FURNACE.asItem(), Blocks.DIAMOND_BLOCK.asItem(), Blocks.GOLD_BLOCK.asItem());
        assertThat(schema.previewCandidatesAt(position)).extracting(StructurePreviewSchema.Candidate::modifier).containsExactly(
                false, true, true);
    }

    @Test
    void factory_orients_controller_face_away_from_the_structure() {
        BlockState controller = ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState();
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(controller.getBlock()),
                new BlockPos(0, 0, -1), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("preview_machine"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.stateAt(BlockPos.ZERO).getValue(MachineControllerBlock.FACING)).isEqualTo(Direction.SOUTH);
    }

    @Test
    void factory_uses_one_shared_highest_level_for_all_slots() {
        Identifier coil = MMCR.id("preview_coil");
        registerLevels(Map.of(coil, List.of(Blocks.COPPER_BLOCK, Blocks.IRON_BLOCK)));
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(1, 0, 0);
        MachineStructureStage stage = stageWithSlots(Map.of(first, coil, second, coil));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("slots"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.stateAt(first)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(schema.stateAt(second)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    void factory_uses_air_when_a_shared_level_is_missing_for_a_slot() {
        Identifier coil = MMCR.id("preview_coil_missing");
        Identifier casing = MMCR.id("preview_casing_missing");
        registerLevels(Map.of(coil, List.of(Blocks.COPPER_BLOCK, Blocks.IRON_BLOCK), casing, List.of(Blocks.GOLD_BLOCK)));
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(1, 0, 0);
        MachineStructureStage stage = stageWithSlots(Map.of(first, coil, second, casing));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("slots"),
                StructurePreviewVariantSelection.defaults());

        assertThat(schema.stateAt(first)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(schema.stateAt(second)).isEqualTo(Blocks.AIR.defaultBlockState());
    }

    private static MachineStructureStage stageWithSlots(Map<BlockPos, Identifier> slots) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        Map<BlockPos, Character> symbols = new LinkedHashMap<>();
        MachineStructureRequirements.Builder requirements = MachineStructureRequirements.builder();
        int index = 0;
        for (var entry : slots.entrySet()) {
            char symbol = (char) ('A' + index++);
            pattern.put(entry.getKey(), new BlockPredicate.Any());
            symbols.put(entry.getKey(), symbol);
            requirements.levelSlot(symbol, entry.getValue());
        }
        BlockArray blockArray = new BlockArray(pattern, Map.of(), symbols);
        return new MachineStructureStage(1, blockArray, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), requirements.build(blockArray));
    }

    private static MachineStructureStage stage(int index, BlockState state) {
        return new MachineStructureStage(index, new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlockState(state))),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);
    }

    private static SingleBlockModifierReplacement modifierReplacement(BlockPos position, Block block) {
        return modifierReplacement(block);
    }

    private static SingleBlockModifierReplacement modifierReplacement(Block block) {
        return new SingleBlockModifierReplacement(block.getDescriptionId(), new BlockPredicate.OfBlock(block),
                List.of(), new ItemStack(block));
    }

    private static void registerLevels(Map<Identifier, List<Block>> levelsByType) {
        TestBootstrap.beginRegistration();
        for (Identifier type : levelsByType.keySet()) {
            TestBootstrap.registerType(new LevelType(type, Component.literal(type.toString())));
        }
        for (var entry : levelsByType.entrySet()) {
            for (int index = 0; index < entry.getValue().size(); index++) {
                var block = entry.getValue().get(index);
                TestBootstrap.registerLevel(new MachineLevel(MMCR.id(entry.getKey().getPath() + "_" + index), entry.getKey(), index,
                        new BlockPredicate.OfBlockState(block.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));
            }
        }
        TestBootstrap.freezeRegistration();
    }

    private static Machine machineWithStages(BlockArray pattern, List<MachineStructureStage> stages) {
        return new Machine() {
            @Override
            public Identifier registryName() {
                return MMCR.id("multi_stage");
            }

            @Override
            public BlockArray pattern() {
                return pattern;
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(registryName());
            }

            @Override
            public List<MachineStructureStage> structureStages() {
                return stages;
            }
        };
    }
}
