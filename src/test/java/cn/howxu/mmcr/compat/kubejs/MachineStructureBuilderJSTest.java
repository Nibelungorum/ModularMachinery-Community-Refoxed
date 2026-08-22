package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicPatternSpec;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.registry.ModBlocks;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineStructureBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void server_structure_builder_creates_structure_definition() {
        var structure = new MachineStructureBuilderJS("mmcr:arc_furnace")
                .pattern("_I", Map.of("I", Blocks.IRON_BLOCK))
                .createObject();

        assertThat(structure.machineId()).isEqualTo(MMCR.id("arc_furnace"));
        assertThat(structure.pattern().pattern()).containsKey(new BlockPos(1, 0, 0));
    }

    @Test
    void controller_symbol_normalizes_pattern_around_controller() {
        var machineId = Identifier.parse("mmcr_test:iron_compressor");
        var structure = new MachineStructureBuilderJS(machineId.toString())
                .pattern("XIX")
                .set("X", Blocks.BLUE_ICE)
                .controller("I")
                .createObject();

        assertThat(structure.pattern().pattern()).containsKeys(
                new BlockPos(-1, 0, 0), BlockPos.ZERO, new BlockPos(1, 0, 0));
        assertThat(structure.pattern().symbolsByPosition()).containsEntry(BlockPos.ZERO, 'I');
        assertThat(structure.pattern().pattern().get(BlockPos.ZERO)
                .matches(ModBlocks.controllerFor(machineId).get().defaultBlockState())).isTrue();
    }

    @Test
    void set_controller_block_without_controller_keeps_it_as_a_normal_block() {
        var structure = new MachineStructureBuilderJS("mmcr_test:iron_compressor")
                .pattern("CX")
                .set("C", Blocks.IRON_BLOCK)
                .set("X", Blocks.BLUE_ICE)
                .createObject();

        assertThat(structure.pattern().symbolsByPosition()).containsEntry(new BlockPos(-1, 0, 0), 'C');
        assertThat(structure.pattern().symbolsByPosition()).containsEntry(new BlockPos(0, 0, 0), 'X');
    }

    @Test
    void server_event_creates_structure_builder() {
        var builder = new MMCRServerEventJS().createStructure("mmcr:event_structure");

        assertThat(builder).isInstanceOf(MachineStructureBuilderJS.class);
        assertThat(builder.createObject().machineId()).isEqualTo(MMCR.id("event_structure"));
    }

    @Test
    void factory_controller_predicate_is_exposed_by_name() {
        var structure = new MachineStructureBuilderJS("mmcr:factory_structure");

        assertThat(structure.factoryController().matches(
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState())).isTrue();
    }

    @Test
    void pattern_retains_level_slot_coordinates_and_uses_the_type_predicate() {
        Identifier coilType = Identifier.parse("test:coil");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        TestBootstrap.registerLevel(new MachineLevel(
                Identifier.parse("test:copper_coil"), coilType, 1,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, LevelModifier.IDENTITY));

        var definition = new MachineStructureBuilderJS("test:furnace")
                .pattern("C", Map.of("C", new LevelSlot(coilType)))
                .createObject();

        assertThat(definition.levelSlots()).containsExactly(Map.entry(BlockPos.ZERO, coilType));
        assertThat(definition.pattern().pattern().get(BlockPos.ZERO)
                .matches(Blocks.COPPER_BLOCK.defaultBlockState())).isTrue();
    }

    @Test
    void pattern_entry_binds_modifiers_to_every_matching_character_position() {
        var base = new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE);
        var replacement = new SingleBlockModifierReplacement(
                "diamond_speedup", new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), ItemStack.EMPTY);

        var definition = new MachineStructureBuilderJS("test:pattern_entry")
                .pattern("MM", Map.of("M", new MachineStructureBuilderJS.PatternEntry(base, List.of(replacement))))
                .createObject();

        assertThat(definition.pattern().pattern())
                .containsEntry(BlockPos.ZERO, base)
                .containsEntry(new BlockPos(1, 0, 0), base);
        assertThat(definition.requirements().modifierReplacements()).containsEntry('M', List.of(replacement));
        assertThat(definition.modifierReplacements())
                .containsEntry(BlockPos.ZERO, List.of(replacement))
                .containsEntry(new BlockPos(1, 0, 0), List.of(replacement));
    }

    @Test
    void block_array_builder_set_binds_modifiers_to_repeated_pattern_key() {
        var base = new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE);
        var replacement = new SingleBlockModifierReplacement(
                "diamond_speedup", new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), ItemStack.EMPTY);

        var builder = BlockArray.builder()
                .pattern("MCM")
                .set('M', base, replacement)
                .set('C', new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
        var pattern = builder.build();
        var requirements = builder.requirements();

        assertThat(pattern.pattern().get(new BlockPos(-1, 0, 0))).isSameAs(base);
        assertThat(pattern.pattern().get(new BlockPos(1, 0, 0))).isSameAs(base);
        assertThat(requirements.modifierReplacements()).containsEntry('M', List.of(replacement));
    }

    @Test
    void pattern_expands_repeated_level_slot_entries_to_every_matching_key() {
        Identifier coilType = Identifier.parse("test:coil");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        TestBootstrap.registerLevel(new MachineLevel(
                Identifier.parse("test:copper_coil"), coilType, 1,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, LevelModifier.IDENTITY));

        var definition = new MachineStructureBuilderJS("test:repeated_levels")
                .pattern("LL", Map.of("L", new LevelSlot(coilType)))
                .createObject();

        assertThat(definition.requirements().levelSlots()).containsEntry('L', coilType);
        assertThat(definition.levelSlots())
                .containsEntry(BlockPos.ZERO, coilType)
                .containsEntry(new BlockPos(1, 0, 0), coilType);
    }

    @Test
    void builder_supports_full_structure_and_extension_declarations() {
        BlockPredicate casingPredicate = new BlockPredicate.OfBlock(Blocks.IRON_BLOCK);
        var definition = new MachineStructureBuilderJS("mmcr:expandable")
                .pattern("C", Map.of("C", Blocks.IRON_BLOCK))
                .extension(new BlockArray(Map.of(new BlockPos(1, 0, 0), casingPredicate)))
                .createObject();

        assertThat(definition.declarations()).extracting(Declaration::kind)
                .containsExactly(Declaration.Kind.FULL, Declaration.Kind.EXTENSION);
    }

    @Test
    void builder_supports_multiple_full_structure_declarations() {
        BlockArray alternative = new BlockArray(Map.of(new BlockPos(2, 0, 0), new BlockPredicate.Any()));

        var definition = new MachineStructureBuilderJS("mmcr:alternatives")
                .pattern("C", Map.of("C", Blocks.IRON_BLOCK))
                .fullStructure(alternative)
                .createObject();

        assertThat(definition.declarations()).extracting(Declaration::kind)
                .containsExactly(Declaration.Kind.FULL, Declaration.Kind.FULL);
        assertThat(definition.declarations().get(1).pattern()).isEqualTo(alternative);
    }

    @Test
    void builder_retains_complete_metadata_for_each_structure_declaration() {
        Identifier coilType = Identifier.parse("test:coil");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        BlockPos modifierPosition = new BlockPos(1, 0, 0);
        BlockArray full = BlockArray.builder()
                .pattern("CM")
                .set('C', new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))
                .set('M', new BlockPredicate.OfTag(BlockTags.MINEABLE_WITH_PICKAXE))
                .build();
        BlockArray alternative = new BlockArray(Map.of(new BlockPos(2, 0, 0), new BlockPredicate.Any()));
        BlockArray extension = new BlockArray(Map.of(new BlockPos(3, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));
        PortRequirementSpec ports = PortRequirementSpec.builder().min("item_input_bus", 1).build();
        PortTierRequirementSpec tiers = PortTierRequirementSpec.builder().anyItemInput().build();
        DynamicPatternSpec dynamic = new DynamicPatternSpec("length", new BlockArray(Map.of()), null,
                1, 3, BlockPos.ZERO, new BlockPos(0, 0, 1), null);
        SingleBlockModifierReplacement replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        var requirements = MachineStructureRequirements.builder()
                .modifier('M', replacement)
                .levelSlot('C', coilType)
                .build(full);

        var definition = new MachineStructureBuilderJS("test:complete")
                .fullStructure(full, ports, tiers, List.of(dynamic), requirements)
                .fullStructure(alternative, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                        MachineStructureRequirements.EMPTY)
                .extension(extension, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                        MachineStructureRequirements.EMPTY)
                .createObject();

        assertThat(definition.declarations()).extracting(Declaration::kind)
                .containsExactly(Declaration.Kind.FULL, Declaration.Kind.FULL, Declaration.Kind.EXTENSION);
        assertThat(definition.portRequirements().requirements()).containsKey("item_input_bus");
        assertThat(definition.portTierRequirements().requirements()).singleElement();
        assertThat(definition.levelSlots()).containsEntry(BlockPos.ZERO, coilType);
        assertThat(definition.modifierReplacements()).containsKey(modifierPosition);
        assertThat(definition.declarations().get(1).pattern()).isEqualTo(alternative);
        assertThat(definition.declarations().get(2).pattern()).isEqualTo(extension);
    }

    @Test
    void full_structure_rejects_absent_character_level_slot() {
        Identifier coilType = Identifier.parse("test:coil");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        BlockArray full = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)),
                Map.of(), Map.of(BlockPos.ZERO, 'C'));

        assertThatThrownBy(() -> new MachineStructureBuilderJS("test:bad_level_slot")
                .fullStructure(full, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                        MachineStructureRequirements.builder().levelSlot('L', coilType).build())
                .createObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L")
                .hasMessageContaining("absent");
    }

    @Test
    void builder_applies_declaration_metadata_to_compatible_pattern() {
        Identifier coilType = Identifier.parse("test:coil");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        DynamicPatternSpec dynamic = new DynamicPatternSpec("length", new BlockArray(Map.of()), null,
                1, 3, BlockPos.ZERO, new BlockPos(0, 0, 1), null);

        var definition = new MachineStructureBuilderJS("test:compatible")
                .portRequirements(PortRequirementSpec.builder().min("item_input_bus", 1).build())
                .portTierRequirements(PortTierRequirementSpec.builder().anyItemInput().build())
                .dynamicPattern(dynamic)
                .pattern("C", Map.of("C", Blocks.IRON_BLOCK))
                .createObject();

        assertThat(definition.portRequirements().requirements()).containsKey("item_input_bus");
        assertThat(definition.portTierRequirements().requirements()).singleElement();
        assertThat(definition.dynamicPatterns()).containsExactly(dynamic);
        assertThat(definition.levelSlots()).isEmpty();
    }

    @Test
    void builder_applies_class_metadata_set_after_full_structure() {
        Identifier coilType = Identifier.parse("test:coil");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        BlockArray full = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        DynamicPatternSpec dynamic = new DynamicPatternSpec("length", new BlockArray(Map.of()), null,
                1, 3, BlockPos.ZERO, new BlockPos(0, 0, 1), null);

        var definition = new MachineStructureBuilderJS("test:full_then_metadata")
                .fullStructure(full)
                .portRequirements(PortRequirementSpec.builder().min("item_input_bus", 1).build())
                .portTierRequirements(PortTierRequirementSpec.builder().anyItemInput().build())
                .dynamicPattern(dynamic)
                .createObject();

        assertThat(definition.pattern()).isEqualTo(full);
        assertThat(definition.portRequirements().requirements()).containsKey("item_input_bus");
        assertThat(definition.portTierRequirements().requirements()).singleElement();
        assertThat(definition.dynamicPatterns()).containsExactly(dynamic);
        assertThat(definition.levelSlots()).isEmpty();
    }

    @Test
    void builder_applies_pattern_metadata_before_appending_full_structure() {
        BlockArray alternative = new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));

        var definition = new MachineStructureBuilderJS("test:pattern_then_full")
                .pattern("C", Map.of("C", Blocks.IRON_BLOCK))
                .portRequirements(PortRequirementSpec.builder().min("item_input_bus", 1).build())
                .fullStructure(alternative)
                .createObject();

        assertThat(definition.declarations()).hasSize(2);
        assertThat(definition.declarations().getFirst().portRequirements().requirements()).containsKey("item_input_bus");
        assertThat(definition.declarations().get(1).pattern()).isEqualTo(alternative);
    }

    @Test
    void extension_requires_an_existing_full_structure() {
        assertThatThrownBy(() -> new MachineStructureBuilderJS("mmcr:expandable")
                .extension(new BlockArray(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("extension requires a full structure first");
    }

    @Test
    void list_pattern_overload_is_hidden_from_rhino_to_avoid_array_ambiguity() throws Exception {
        var method = MachineStructureBuilderJS.class.getMethod("pattern", List.class);

        assertThat(method.isAnnotationPresent(HideFromJS.class)).isTrue();
    }
}
