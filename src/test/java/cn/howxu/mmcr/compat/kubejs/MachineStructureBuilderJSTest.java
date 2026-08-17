package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicPatternSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.test.TestBootstrap;
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
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(), "", ItemStack.EMPTY);

        var structure = new MachineStructureBuilderJS("mmcr:arc_furnace")
                .pattern("_I", Map.of("I", Blocks.IRON_BLOCK))
                .addModifier(replacement)
                .createObject();

        assertThat(structure.machineId()).isEqualTo(MMCR.id("arc_furnace"));
        assertThat(structure.pattern().pattern()).containsKey(new BlockPos(1, 0, 0));
        assertThat(structure.modifierReplacements().get(new BlockPos(1, 0, 0))).singleElement()
                .extracting(SingleBlockModifierReplacement::getModifierName)
                .isEqualTo("speed");
    }

    @Test
    void pattern_retains_level_slot_coordinates_and_uses_the_type_predicate() {
        Identifier coilType = Identifier.parse("test:coil");
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(coilType, Component.literal("Coils")));
        MachineLevelRegistry.registerLevel(new MachineLevel(
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
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(coilType, Component.literal("Coils")));
        BlockPos modifierPosition = new BlockPos(1, 0, 0);
        BlockArray full = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(0, 0, 1), new BlockPredicate.OfTag(BlockTags.MINEABLE_WITH_PICKAXE)));
        BlockArray alternative = new BlockArray(Map.of(new BlockPos(2, 0, 0), new BlockPredicate.Any()));
        BlockArray extension = new BlockArray(Map.of(new BlockPos(3, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));
        PortRequirementSpec ports = PortRequirementSpec.builder().min("item_input_bus", 1).build();
        PortTierRequirementSpec tiers = PortTierRequirementSpec.builder().anyItemInput().build();
        DynamicPatternSpec dynamic = new DynamicPatternSpec("length", new BlockArray(Map.of()), null,
                1, 3, BlockPos.ZERO, new BlockPos(0, 0, 1), null);
        SingleBlockModifierReplacement replacement = new SingleBlockModifierReplacement(
                "speed", modifierPosition, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), "", ItemStack.EMPTY);

        var definition = new MachineStructureBuilderJS("test:complete")
                .fullStructure(full, ports, tiers, List.of(dynamic), Map.of(modifierPosition, List.of(replacement)),
                        Map.of(BlockPos.ZERO, coilType))
                .fullStructure(alternative, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of())
                .extension(extension, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of())
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
    void builder_applies_declaration_metadata_to_compatible_pattern() {
        Identifier coilType = Identifier.parse("test:coil");
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(coilType, Component.literal("Coils")));
        DynamicPatternSpec dynamic = new DynamicPatternSpec("length", new BlockArray(Map.of()), null,
                1, 3, BlockPos.ZERO, new BlockPos(0, 0, 1), null);

        var definition = new MachineStructureBuilderJS("test:compatible")
                .portRequirements(PortRequirementSpec.builder().min("item_input_bus", 1).build())
                .portTierRequirements(PortTierRequirementSpec.builder().anyItemInput().build())
                .dynamicPattern(dynamic)
                .levelSlot(BlockPos.ZERO, coilType.toString())
                .pattern("C", Map.of("C", Blocks.IRON_BLOCK))
                .createObject();

        assertThat(definition.portRequirements().requirements()).containsKey("item_input_bus");
        assertThat(definition.portTierRequirements().requirements()).singleElement();
        assertThat(definition.dynamicPatterns()).containsExactly(dynamic);
        assertThat(definition.levelSlots()).containsEntry(BlockPos.ZERO, coilType);
    }

    @Test
    void extension_requires_an_existing_full_structure() {
        assertThatThrownBy(() -> new MachineStructureBuilderJS("mmcr:expandable")
                .extension(new BlockArray(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("extension requires a full structure first");
    }
}
