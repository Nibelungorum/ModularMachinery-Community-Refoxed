package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.world.level.block.Block;
import static cn.howxu.mmcr.client.preview.PreviewHighlights.INTERFACE;
import static cn.howxu.mmcr.client.preview.PreviewHighlights.LEVEL;
import static cn.howxu.mmcr.client.preview.PreviewHighlights.MODIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies schema construction from machine structures.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewSchemaFactoryTest {
    private static final Identifier TEST_LEVEL_TYPE = MMCR.id("preview_test_level_type");
    private static final Identifier TEST_LEVEL = MMCR.id("preview_test_level");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindHolderTag(Blocks.OAK_LOG.builtInRegistryHolder(), previewTag());
        bindHolderTag(Blocks.BIRCH_LOG.builtInRegistryHolder(), previewTag());
    }

    private static TagKey<Block> previewTag() {
        return TagKey.create(Registries.BLOCK, MMCR.id("preview_logs"));
    }

    private static void bindHolderTag(Holder<?> holder, TagKey<?> tag) throws Exception {
        bindHolderTags(holder, Set.of(tag));
    }

    private static void bindHolderTags(Holder<?> holder, Collection<? extends TagKey<?>> tags) throws Exception {
        Method bindTags = Class.forName("net.minecraft.core.Holder$Reference")
                .getDeclaredMethod("bindTags", Collection.class);
        bindTags.setAccessible(true);
        bindTags.invoke(holder, tags);
    }

    @AfterEach
    void restoreDefaultLevels() {
        StructureRegistration.resetCollector();
        var event = StructureRegistration.prepare(Set.of());
        event.registerLevelType(new LevelType(TEST_LEVEL_TYPE, Component.literal("Preview Test")));
        event.registerLevel(new MachineLevel(TEST_LEVEL, TEST_LEVEL_TYPE, 0,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
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
    void factory_renders_machine_couplers_in_the_jei_preview_schema() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, BlockPredicate.machineCoupler()));
        DynamicMachine machine = new DynamicMachine(MMCR.id("preview_coupler"), "machine.preview_coupler", pattern);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        assertThat(schema.stateAt(BlockPos.ZERO)).isEqualTo(ModBlocks.MODULE_BRIDGE.get().defaultBlockState());
        assertThat(schema.highlightAt(BlockPos.ZERO)).isEqualTo(INTERFACE);
    }

    @Test
    void factory_uses_directional_state_and_candidate_from_the_rotated_compiled_pattern() {
        BlockPos rawPosition = new BlockPos(1, 0, 0);
        BlockState southState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        BlockArray pattern = new BlockArray(Map.of(rawPosition, new BlockPredicate.OfBlockState(southState)));
        BlockPos rotatedPosition = BlockRotator.rotateSouthTo(rawPosition, Direction.EAST);
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("rotated_preview"), Direction.EAST);

        assertThat(schema.stateAt(rotatedPosition)).isEqualTo(
                southState.rotate(Rotation.COUNTERCLOCKWISE_90));
        assertThat(schema.candidatesAt(rotatedPosition)).extracting(ItemStack::getItem)
                .containsExactly(Blocks.OAK_LOG.asItem());
    }

    @Test
    void factory_preserves_compiled_directional_states_with_an_asymmetric_structure() {
        Identifier machineId = MMCR.id("directional_preview");
        BlockState southFacing = Blocks.DISPENSER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH);
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(ModBlocks.controllerFor(MMCR.id("test_cube")).get()),
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlockState(southFacing)));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        for (Direction facing : Direction.values()) {
            StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, machineId, facing);
            BlockPos position = BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), facing);
            BlockPredicate.OfBlockState expected = (BlockPredicate.OfBlockState)
                    BlockArrayCache.get(pattern, facing).pattern().get(position);

            assertThat(schema.stateAt(BlockPos.ZERO).getValue(MachineControllerBlock.FACING)).isEqualTo(facing);
            assertThat(schema.stateAt(position)).isEqualTo(expected.state());
        }
    }

    @Test
    void factory_skips_unrepresentable_predicates_without_failing_the_schema() {
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()),
                Map.of(), Map.of(BlockPos.ZERO, 'M'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .modifier('M', modifierReplacement(ModBlocks.SMART_INTERFACE.get())).build(pattern));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("unrepresentable"));

        assertThat(schema.states()).isEmpty();
        assertThat(schema.highlights()).isEmpty();
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
        assertThat(schema.highlightAt(resolved)).isEqualTo(LEVEL);
        assertThat(schema.highlightAt(unresolved)).isEqualTo(INTERFACE | LEVEL);
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
        assertThat(schema.stateAt(BlockPos.ZERO)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(schema.highlightAt(BlockPos.ZERO)).isEqualTo(INTERFACE);
    }

    @Test
    void factory_recognizes_all_interface_block_types_but_not_the_main_controller_or_casing() {
        List<Block> interfaces = List.of(
                ModBlocks.BLOCKS.get("item_input_bus").get(),
                ModBlocks.BLOCKS.get("parallel_controller_normal").get(),
                ModBlocks.FACTORY_CONTROLLER.get(), ModBlocks.SMART_INTERFACE.get(), ModBlocks.DATA_STORAGE.get(),
                ModBlocks.NETWORK_INTERFACE.get(), ModBlocks.BLOCKS.get("upgrade_bus_normal").get(),
                ModBlocks.MODULE_BRIDGE.get());
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(ModBlocks.controllerFor(MMCR.id("test_cube")).get()));
        pattern.put(new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
        for (int index = 0; index < interfaces.size(); index++) {
            pattern.put(new BlockPos(index + 1, 0, 0), new BlockPredicate.OfBlock(interfaces.get(index)));
        }
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(pattern), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("interface_types"));

        assertThat(schema.highlightAt(BlockPos.ZERO)).isZero();
        assertThat(schema.highlightAt(new BlockPos(-1, 0, 0))).isZero();
        for (int index = 0; index < interfaces.size(); index++) {
            assertThat(schema.highlightAt(new BlockPos(index + 1, 0, 0))).as("%s", interfaces.get(index))
                    .isEqualTo(INTERFACE);
        }
        assertThat(schema.highlights()).hasSize(interfaces.size());
    }

    @Test
    void factory_checks_nested_alternatives_exact_states_deferred_blocks_and_special_matchers() {
        Block port = ModBlocks.BLOCKS.get("item_input_bus").get();
        BlockPredicate iron = new BlockPredicate.OfBlock(Blocks.IRON_BLOCK);
        Map<BlockPos, BlockPredicate> pattern = Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(List.of(iron,
                        new BlockPredicate.AnyOf(List.of(iron, new BlockPredicate.OfBlock(port))))),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlockState(port.defaultBlockState()),
                new BlockPos(3, 0, 0), new BlockPredicate.DeferredBlock(() -> port),
                new BlockPos(4, 0, 0), new BlockPredicate.AnyOf(List.of(iron,
                        new BlockPredicate.DeferredBlock(() -> {
                            throw new AssertionError("network matcher supplier must not resolve for classification");
                        }, true))),
                new BlockPos(5, 0, 0), new BlockPredicate.AnyOf(List.of(iron, new BlockPredicate.Any())),
                new BlockPos(6, 0, 0), new BlockPredicate.AnyOf(List.of(iron, new BlockPredicate.Air())),
                new BlockPos(7, 0, 0), new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                new BlockPos(8, 0, 0), new BlockPredicate.DeferredBlock(() -> Blocks.IRON_BLOCK));
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(pattern), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("predicate_roles"));

        assertThat(schema.highlights()).containsOnly(
                Map.entry(new BlockPos(1, 0, 0), INTERFACE), Map.entry(new BlockPos(2, 0, 0), INTERFACE),
                Map.entry(new BlockPos(3, 0, 0), INTERFACE), Map.entry(new BlockPos(4, 0, 0), INTERFACE),
                Map.entry(new BlockPos(5, 0, 0), INTERFACE));
        assertThat(schema.stateAt(new BlockPos(1, 0, 0))).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    void factory_checks_tag_members_and_does_not_keep_classification_across_schema_resolutions() throws Exception {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, MMCR.id("preview_interface_options"));
        Map<TagKey<Block>, List<Holder<Block>>> originalTags = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.getTags().forEach(values -> originalTags.put(values.key(), values.stream().toList()));
        Map<Holder<Block>, List<TagKey<Block>>> originalHolderTags = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.forEach(block -> originalHolderTags.put(block.builtInRegistryHolder(),
                block.builtInRegistryHolder().tags().toList()));
        Map<TagKey<Block>, List<Holder<Block>>> tags = new LinkedHashMap<>(originalTags);
        BlockPredicate predicate = new BlockPredicate.OfTag(tag);
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(
                BlockPos.ZERO, predicate, new BlockPos(1, 0, 0), predicate)), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);
        StructurePreviewSchemaFactory factory = new StructurePreviewSchemaFactory();
        try {
            tags.put(tag, List.of(Blocks.IRON_BLOCK.builtInRegistryHolder(),
                    ModBlocks.BLOCKS.get("item_input_bus").get().builtInRegistryHolder()));
            BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, tags)).apply();

            StructurePreviewSchema acceptsPort = factory.create(stage, MMCR.id("tag_roles"));

            assertThat(acceptsPort.stateAt(BlockPos.ZERO)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
            assertThat(acceptsPort.highlights()).containsOnly(Map.entry(BlockPos.ZERO, INTERFACE),
                    Map.entry(new BlockPos(1, 0, 0), INTERFACE));

            tags.put(tag, List.of(Blocks.IRON_BLOCK.builtInRegistryHolder()));
            BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, tags)).apply();

            assertThat(factory.create(stage, MMCR.id("tag_roles")).highlights()).isEmpty();
        } finally {
            BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, originalTags)).apply();
            for (var entry : originalHolderTags.entrySet()) {
                bindHolderTags(entry.getKey(), entry.getValue());
            }
        }
    }

    @Test
    void factory_marks_replacement_interfaces_without_marking_ordinary_casing() {
        BlockPos replacement = new BlockPos(1, 0, 0);
        BlockPos ordinary = new BlockPos(2, 0, 0);
        BlockPos ordinaryModifier = new BlockPos(3, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                replacement, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                ordinary, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                ordinaryModifier, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)), Map.of(),
                Map.of(replacement, 'M', ordinaryModifier, 'N'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .modifier('M', modifierReplacement(ModBlocks.SMART_INTERFACE.get()))
                        .modifier('N', modifierReplacement(Blocks.GOLD_BLOCK)).build(pattern));
        StructurePreviewSchemaFactory factory = new StructurePreviewSchemaFactory();

        StructurePreviewSchema lazy = factory.create(stage, MMCR.id("replacement_roles"));
        StructurePreviewSchema eager = factory.resolve(stage, StructurePreviewVariantSelection.defaults());

        assertThat(lazy.highlights()).containsOnly(Map.entry(replacement, INTERFACE | MODIFIER),
                Map.entry(ordinaryModifier, MODIFIER));
        assertThat(lazy.highlightAt(ordinary)).isZero();
        assertThat(eager.highlights()).isEqualTo(lazy.highlights());
        assertThat(PreviewHighlights.color(lazy.highlightAt(replacement))).isEqualTo(0x44FF00FF);
    }

    @Test
    void repeated_interface_alternatives_and_replacements_do_not_increase_color_or_opacity() {
        Block port = ModBlocks.BLOCKS.get("item_input_bus").get();
        BlockPredicate repeated = new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlock(port),
                new BlockPredicate.OfBlock(port), new BlockPredicate.DeferredBlock(() -> port)));
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, repeated), Map.of(), Map.of(BlockPos.ZERO, 'M'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .modifier('M', modifierReplacement(port))
                        .modifier('M', modifierReplacement(ModBlocks.SMART_INTERFACE.get())).build(pattern));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("duplicate_roles"));

        assertThat(schema.highlightAt(BlockPos.ZERO)).isEqualTo(INTERFACE | MODIFIER);
        assertThat(PreviewHighlights.color(schema.highlightAt(BlockPos.ZERO))).isEqualTo(0x44FF00FF);
    }

    @Test
    void factory_rotates_all_three_overlapping_roles_with_horizontal_and_vertical_states() {
        Identifier levelType = MMCR.id("rotated_highlight_level");
        registerLevels(Map.of(levelType, List.of(Blocks.COPPER_BLOCK)));
        BlockPos raw = new BlockPos(1, 2, 3);
        BlockArray pattern = new BlockArray(Map.of(raw, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)),
                Map.of(), Map.of(raw, 'L'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .levelSlot('L', levelType).modifier('L', modifierReplacement(ModBlocks.SMART_INTERFACE.get()))
                        .build(pattern));
        Map<Direction, BlockPos> expectedPositions = Map.of(
                Direction.EAST, new BlockPos(3, 2, -1), Direction.UP, new BlockPos(-1, 3, 2));

        expectedPositions.forEach((facing, position) -> {
            StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("rotated_roles"), facing);

            assertThat(schema.states()).containsOnly(Map.entry(position, Blocks.COPPER_BLOCK.defaultBlockState()));
            assertThat(schema.highlights()).containsOnly(Map.entry(position, INTERFACE | LEVEL | MODIFIER));
            assertThat(schema.levelSlotAt(position)).isEqualTo(levelType);
            assertThat(schema.highlightAt(raw)).isZero();
            assertThat(PreviewHighlights.color(schema.highlightAt(position))).isEqualTo(0x44FFFFFF);
        });
    }

    @Test
    void factory_reuses_predicate_classification_without_resolving_preview_candidates() {
        Identifier levelType = MMCR.id("cached_highlight_level");
        registerLevels(Map.of(levelType, List.of(Blocks.COPPER_BLOCK)));
        AtomicInteger resolutions = new AtomicInteger();
        Block port = ModBlocks.BLOCKS.get("item_input_bus").get();
        BlockPredicate predicate = new BlockPredicate.DeferredBlock(() -> {
            resolutions.incrementAndGet();
            return port;
        });
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(first, predicate, second, predicate), Map.of(),
                Map.of(first, 'L', second, 'L'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .levelSlot('L', levelType).build(pattern));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("cached_roles"));

        assertThat(schema.highlights()).containsOnly(Map.entry(first, INTERFACE | LEVEL), Map.entry(second, INTERFACE | LEVEL));
        assertThat(resolutions).hasValue(1);
        assertThat(schema.candidatesAt(first)).extracting(ItemStack::getItem).containsExactly(port.asItem());
        assertThat(resolutions).hasValue(2);
        schema.candidatesAt(first);
        assertThat(resolutions).hasValue(2);
        assertThat(schema.candidatesAt(second)).extracting(ItemStack::getItem).containsExactly(port.asItem());
        assertThat(resolutions).hasValue(3);
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
    void factory_resolves_deferred_candidates_only_when_the_position_is_selected() {
        AtomicInteger resolutions = new AtomicInteger();
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPredicate.DeferredBlock(() -> {
                    resolutions.incrementAndGet();
                    return Blocks.GOLD_BLOCK;
                })));
        MachineStructureStage stage = new MachineStructureStage(1, new BlockArray(Map.of(BlockPos.ZERO, predicate)),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage,
                MMCR.id("lazy_deferred_candidates"), StructurePreviewVariantSelection.defaults());

        int resolutionsBeforeSelection = resolutions.get();
        assertThat(schema.candidatesAt(BlockPos.ZERO)).extracting(ItemStack::getItem).containsExactlyInAnyOrder(
                Blocks.IRON_BLOCK.asItem(), Blocks.GOLD_BLOCK.asItem());
        assertThat(resolutions).hasValue(resolutionsBeforeSelection + 1);
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
    void factory_rotates_directional_level_state_with_the_pattern() {
        Identifier levelType = MMCR.id("rotated_directional_level");
        BlockState southState = Blocks.DISPENSER.defaultBlockState().setValue(
                BlockStateProperties.FACING, Direction.SOUTH);
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(levelType, Component.literal(levelType.toString())));
        TestBootstrap.registerLevel(new MachineLevel(MMCR.id("rotated_directional_level_0"), levelType, 0,
                new BlockPredicate.OfBlockState(southState), ItemStack.EMPTY, ModifierDefinition.EMPTY));
        TestBootstrap.freezeRegistration();

        BlockPos rawPosition = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(rawPosition, new BlockPredicate.Any()), Map.of(), Map.of(rawPosition, 'L'));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .levelSlot('L', levelType).build(pattern));

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage,
                MMCR.id("rotated_directional_preview"), Direction.EAST);

        assertThat(schema.stateAt(BlockRotator.rotateSouthTo(rawPosition, Direction.EAST))).isEqualTo(
                southState.rotate(Rotation.COUNTERCLOCKWISE_90));
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
    void symmetric_exported_structure_uses_template_facing_without_rotating_stair_states_again() {
        var builder = BlockArray.builder()
                .pattern(" AXXXB ").pattern("AD   EB").pattern("F     G")
                .pattern("F  C  G").pattern("F     G").pattern("HI   JK").pattern(" LMMMK ");
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        for (char symbol : "XABDEFGHIJKLM".toCharArray()) {
            builder.set(symbol, new BlockPredicate.OfBlockState(stairs));
        }
        builder.set('C', new BlockPredicate.OfBlock(ModBlocks.controllerFor(MMCR.id("test_cube")).get()));
        BlockArray pattern = builder.controller('C').build();
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage, MMCR.id("aaa_preview"), facing);
            assertThat(schema.stateAt(BlockPos.ZERO).getValue(MachineControllerBlock.FACING)).isEqualTo(facing);
            var expectedPattern = BlockArrayCache.get(pattern, facing);
            expectedPattern.pattern().forEach((position, predicate) -> {
                if (predicate instanceof BlockPredicate.OfBlockState state) {
                    assertThat(schema.stateAt(position)).isEqualTo(state.state());
                }
            });
        }
    }

    @Test
    void asymmetric_exported_structure_keeps_controller_facing_consistent_with_the_template() {
        BlockArray pattern = BlockArray.builder()
                .pattern("XXX", "XXX", "XXX", "XXX", "XXX", "XXX", "XXX")
                .pattern("XXX", "XXX", "XXX", "XCX", "XXX", "XXX", "XXX")
                .pattern("XXX", "XXX", "   ", "   ", "   ", "XXX", "XXX")
                .pattern("XXX", "XXX", " X ", " X ", " X ", "XXX", "XXX")
                .pattern("XXX", "XXX", "   ", "   ", "   ", "XXX", "XXX")
                .set('X', new BlockPredicate.OfBlock(Blocks.BRICKS))
                .set('C', new BlockPredicate.OfBlock(ModBlocks.controllerFor(MMCR.id("test_cube")).get()))
                .controller('C').build();
        DynamicMachine machine = new DynamicMachine(MMCR.id("asymmetric_export_preview"), "machine.preview", pattern);
        StructurePreviewSchemaFactory factory = new StructurePreviewSchemaFactory();

        assertThat(factory.create(machine).stateAt(BlockPos.ZERO).getValue(MachineControllerBlock.FACING))
                .isEqualTo(Direction.SOUTH);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            StructurePreviewSchema schema = factory.create(machine.structureStages().getFirst(), machine.registryName(), facing);

            assertThat(schema.stateAt(BlockPos.ZERO).getValue(MachineControllerBlock.FACING)).isEqualTo(facing);
            assertThat(schema.states().keySet()).containsExactlyInAnyOrderElementsOf(
                    BlockArrayCache.get(pattern, facing).pattern().keySet());
            schema.states().forEach((position, state) -> {
                if (!position.equals(BlockPos.ZERO)) {
                    assertThat(state).isEqualTo(Blocks.BRICKS.defaultBlockState());
                }
            });
        }
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
    void factory_uses_vertical_facing_for_required_vertical_machines() {
        Block controller = ModBlocks.controllerFor(MMCR.id("test_cube")).get();
        BlockPos rawStructureBlock = new BlockPos(0, 0, 1);
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(controller),
                rawStructureBlock, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);
        Machine machine = new Machine() {
            @Override
            public Identifier registryName() {
                return MMCR.id("vertical_preview");
            }

            @Override
            public BlockArray pattern() {
                return pattern;
            }

            @Override
            public MachineControllerSpec controller() {
                MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(registryName());
                return new MachineControllerSpec(defaults.id(), defaults.frontTexture(), defaults.sideTexture(),
                        defaults.topTexture(), defaults.bottomTexture(), true, false, true);
            }

            @Override
            public List<MachineStructureStage> structureStages() {
                return List.of(stage);
            }
        };

        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        assertThat(schema.stateAt(BlockPos.ZERO).getValue(MachineControllerBlock.FACING)).isEqualTo(Direction.UP);
        assertThat(schema.stateAt(new BlockPos(0, 1, 0))).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(schema.stateAt(rawStructureBlock)).isNull();
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
                        new BlockPredicate.OfBlockState(block.defaultBlockState()), ItemStack.EMPTY, ModifierDefinition.EMPTY));
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
