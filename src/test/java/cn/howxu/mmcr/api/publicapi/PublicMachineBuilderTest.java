package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineRole;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PatternBuilder;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the public builders keep definitions and structures separate.
 * @author howxu <dev@howxu.cn>
 */
class PublicMachineBuilderTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void machine_builder_builds_base_properties_without_structure() {
        MachineDefinition definition = MachineBuilder.machine(MMCR.id("base_machine"))
                .displayNameKey("machine.mmcr.base_machine")
                .maxParallelism(4)
                .build();

        assertThat(definition.id()).isEqualTo(MMCR.id("base_machine"));
        assertThat(definition.displayNameKey()).isEqualTo("machine.mmcr.base_machine");
        assertThat(definition.behavior().kind()).isEqualTo(MachineBehavior.Kind.RECIPE);
        assertThat(MachineBuilder.class.getDeclaredMethods()).noneMatch(method ->
                method.getName().equals("pattern") || method.getName().equals("stage")
                        || method.getName().equals("expandableStructure"));
    }

    @Test
    void machine_definition_builder_preserves_metadata_factory_and_role_semantics() {
        var moduleId = MMCR.id("processing_module");
        var definition = MachineBuilder.machine(MMCR.id("arc_furnace"))
                .displayNameKey("machine.mmcr.arc_furnace")
                .controller(controller -> controller
                        .textures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                        .allowVerticalFacing()
                        .tooltip("tooltip.mmcr.arc_furnace.0"))
                .appearance(appearance -> appearance
                        .machineBasicBlock(MMCR.id("steel_casing"))
                        .controllerBaseTexture(MMCR.id("block/steel_controller"))
                        .formedPortBaseTexture(MMCR.id("block/steel_port")))
                .factory(factory -> factory.hasFactory(true).threadLimit(4)
                        .thread("smelting", MMCR.id("arc_recipe")))
                .role(MachineRole.HOST)
                .acceptedModule(moduleId)
                .maxParallelism(8)
                .parallelizable(true)
                .failureAction(RecipeFailureActions.RESET)
                .build();

        assertThat(definition.controller().tooltip()).containsExactly("tooltip.mmcr.arc_furnace.0");
        assertThat(definition.controller().allowVerticalFacing()).isTrue();
        assertThat(definition.appearance().machineBasicBlock()).isEqualTo(MMCR.id("steel_casing"));
        assertThat(definition.factory().hasFactory()).isTrue();
        assertThat(definition.factory().threadLimit()).isEqualTo(4);
        assertThat(definition.factory().threads()).hasSize(1);
        assertThat(definition.role()).isEqualTo(MachineRole.HOST);
        assertThat(definition.acceptedModuleIds()).containsExactly(moduleId);
        assertThat(definition.maxParallelism()).isEqualTo(8);
        assertThat(definition.parallelizable()).isTrue();
        assertThat(definition.failureAction()).isEqualTo(RecipeFailureActions.RESET);
    }

    @Test
    void machine_definition_builder_enforces_role_and_factory_values() {
        var moduleId = MMCR.id("processing_module");

        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_host"))
                .role(MachineRole.HOST).build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("accept");
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_normal"))
                .acceptedModule(moduleId).build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HOST");
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("bad_factory"))
                .factory(factory -> factory.threadLimit(0)).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("threadLimit");
    }

    @Test
    void structure_builder_owns_main_structure_and_extensions() {
        MachineStructureDefinition structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("tower"));

        assertThat(structure.machineId()).isEqualTo(MMCR.id("tower"));
        assertThat(structure.stages()).extracting("kind")
                .containsExactly(cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.FULL,
                        cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.EXTENSION);
    }

    @Test
    void structure_builder_preserves_ports_tiers_and_requirements() {
        var machineId = MMCR.id("structured_machine");
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage
                        .pattern(pattern -> pattern.layer("CFC")
                                .where('C', BlockPredicate.block(Blocks.STONE))
                                .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                        .ports(ports -> ports.min("item_input_bus", 1).range("energy_input_hatch", 1, 2))
                        .portTiers(tiers -> tiers.minItemInput(PortTiers.ItemTier.NORMAL)
                                .minFluidOutput(PortTiers.FluidTier.BIG)
                                .minEnergyInput(PortTiers.EnergyTier.NORMAL))
                        .requirements(requirements -> requirements
                                .levelSlot('C', MMCR.id("coil"))
                                 .modifier('C', MMCR.id("gold_modifier"), BlockPredicate.block(Blocks.GOLD_BLOCK),
                                         new ItemStack(Blocks.GOLD_BLOCK))))
                .build(machineId);

        var stage = structure.stages().getFirst();
        assertThat(stage.portRequirements().requirements()).containsKeys("item_input_bus", "energy_input_hatch");
        assertThat(stage.portTiers().requirements()).extracting("minTierId")
                .containsExactly("normal", "big", "normal");
        assertThat(stage.requirements().levelSlots()).containsEntry('C', MMCR.id("coil"));
        assertThat(stage.requirements().modifierReplacements()).containsKey('C');
    }

    @Test
    void structure_builder_rejects_missing_main_structure() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("missing_main")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Main machine structure");
    }

    @Test
    void structure_builder_expands_complete_levels_in_order() {
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .expandStructure(stage -> stage.pattern(pattern -> pattern.layer("S")
                        .where('S', BlockPredicate.block(Blocks.STONE)).controller('S')))
                .expandStructure(stage -> stage.pattern(pattern -> pattern.layer("I")
                        .where('I', BlockPredicate.block(Blocks.IRON_BLOCK)).controller('I')))
                .build(MMCR.id("expanded_levels"));

        assertThat(structure.stages()).extracting("kind")
                .containsExactly(cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.FULL,
                        cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.EXPANSION,
                        cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.EXPANSION);
    }

    @Test
    void structure_builder_rejects_expansion_before_main_structure() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .expandStructure(stage -> stage.pattern(pattern -> pattern.layer("S")
                        .where('S', BlockPredicate.block(Blocks.STONE)).controller('S'))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expandStructure requires a full structure first");
    }

    @Test
    void structure_builder_rejects_multiple_main_structures() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("multiple_main")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only one main machine structure");
    }

    @Test
    void pattern_builder_preserves_immutable_structure_values_and_rejects_invalid_bindings() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);
        BlockPredicate controller = BlockPredicate.block(Blocks.FURNACE);
        var pattern = PatternBuilder.pattern().layer("CCC", "C C", "CFC")
                .where('C', casing).where('F', controller).controller('F').build();

        assertThat(pattern.layers()).containsExactly(List.of("CCC", "C C", "CFC"));
        assertThatThrownBy(() -> pattern.layers().add(List.of("X")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> PatternBuilder.pattern().layer("CC", "CCC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same width");
        assertThat(PatternBuilder.pattern().layer("CF")
                .where('C', casing).controller('F').build().predicates()).containsKey('F');
    }

    @Test
    void structure_builder_preserves_full_then_extension_conversion() {
        var machineId = MMCR.id("structure_conversion");
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.machineCoupler()).controller('C')))
                .build(machineId);

        assertThat(PublicMachineAdapter.toStructureDefinition(structure).declarations())
                .extracting(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration::kind)
                .containsExactly(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.FULL,
                        cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.EXTENSION);
    }
}
