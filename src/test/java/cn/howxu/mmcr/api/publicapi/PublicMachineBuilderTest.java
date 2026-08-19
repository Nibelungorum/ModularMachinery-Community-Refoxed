package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.ControllerSpec;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PatternBuilder;
import cn.howxu.mmcr.api.publicapi.machine.PatternDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.publicapi.machine.StructureStage;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies public startup machine builder value primitives.
 *
 * @author howxu &lt;dev@howxu.cn&gt;
 */
class PublicMachineBuilderTest {

    @BeforeAll static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void pattern_builder_creates_immutable_3x3x1_pattern_with_empty_cells_bindings_and_controller() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);
        BlockPredicate controller = BlockPredicate.block(Blocks.FURNACE);

        PatternDefinition pattern = PatternBuilder.pattern()
                .layer("CCC", "C C", "CFC")
                .where('C', casing)
                .where('F', controller)
                .controller('F')
                .build();

        assertThat(pattern.width()).isEqualTo(3);
        assertThat(pattern.height()).isEqualTo(3);
        assertThat(pattern.depth()).isEqualTo(1);
        assertThat(pattern.layers()).containsExactly(List.of("CCC", "C C", "CFC"));
        assertThat(pattern.predicates()).containsExactlyInAnyOrderEntriesOf(Map.of('C', casing, 'F', controller));
        assertThat(pattern.controllerSymbol()).isEqualTo('F');

        assertThatThrownBy(() -> pattern.layers().add(List.of("XXX")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pattern.layers().getFirst().add("XXX"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pattern.predicates().put('X', casing))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void builder_rejects_invalid_pattern_declarations() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);
        BlockPredicate controller = BlockPredicate.block(Blocks.FURNACE);

        assertThatThrownBy(() -> PatternBuilder.pattern().layer("CC", "CCC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same width");
        assertThatThrownBy(() -> PatternBuilder.pattern().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("layer");
        assertThatThrownBy(() -> PatternBuilder.pattern()
                .layer("CC", "CF")
                .where('C', casing)
                .controller('F')
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unbound");
        assertThatThrownBy(() -> PatternBuilder.pattern()
                .layer("CF")
                .where('C', casing)
                .where('F', controller)
                .controller('C')
                .controller('F'))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("controller");
        assertThatThrownBy(() -> PatternBuilder.pattern().controller('F').controller('F'))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("controller");
        assertThatThrownBy(() -> PatternBuilder.pattern().where('C', null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("predicate");
    }

    @Test
    void block_predicates_are_immutable_and_validate_inputs() {
        BlockPredicate stone = BlockPredicate.block(Blocks.STONE);
        BlockPredicate dirt = BlockPredicate.block(Blocks.DIRT);
        BlockPredicate any = BlockPredicate.any(stone, dirt);
        BlockPredicate tag = BlockPredicate.tag(BlockTags.DIRT);

        assertThat(stone).isNotNull();
        assertThat(any.alternatives()).containsExactly(stone, dirt);
        assertThat(tag.tag()).contains(BlockTags.DIRT);
        assertThat(any.tag()).isEmpty();
        assertThatThrownBy(() -> any.alternatives().add(stone))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> BlockPredicate.block(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("block");
        assertThatThrownBy(() -> BlockPredicate.any())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alternative");
        assertThatThrownBy(() -> BlockPredicate.anyOf(Arrays.asList(stone, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("predicate");
    }

    @Test
    void pattern_definition_rejects_direct_invalid_construction() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);

        assertThatThrownBy(() -> new PatternDefinition(List.of(List.of("CC", "C")), Map.of('C', casing), 'C', 2, 2, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("width");
        assertThatThrownBy(() -> new PatternDefinition(List.of(List.of("CX")), Map.of('C', casing), 'C', 2, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unbound");
        assertThatThrownBy(() -> new PatternDefinition(List.of(List.of("CC")), Map.of('C', casing), 'C', 2, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one controller");
    }

    @Test
    void public_pattern_converts_to_internal_block_array_without_exposing_internal_types() {
        PatternDefinition pattern = PatternBuilder.pattern()
                .layer("CCC", "C C", "CFC")
                .where('C', BlockPredicate.any(BlockPredicate.block(Blocks.STONE), BlockPredicate.tag(BlockTags.DIRT)))
                .where('F', BlockPredicate.block(Blocks.FURNACE))
                .controller('F')
                .build();

        cn.howxu.mmcr.api.machine.BlockArray converted = PublicMachineAdapter.toBlockArray(pattern);

        assertThat(converted.get(BlockPos.ZERO))
                .isInstanceOf(cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock.class)
                .extracting(predicate -> ((cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock) predicate).block())
                .isEqualTo(Blocks.FURNACE);
        assertThat(converted.get(new BlockPos(0, -1, 0))).isNull();
        assertThat(converted.get(new BlockPos(-1, -2, 0)))
                .isInstanceOf(cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf.class)
                .extracting(predicate -> ((cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf) predicate).children())
                .asList()
                .hasSize(2);
    }

    @Test
    void machine_builder_creates_immutable_definition_with_internal_defaults() {
        MachineDefinition definition = MachineBuilder.machine(MMCR.id("basic_press"))
                .pattern(pattern -> pattern
                        .layer("CCC", "CFC", "CCC")
                        .where('C', BlockPredicate.block(Blocks.STONE))
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .build();

        assertThat(definition.id()).isEqualTo(MMCR.id("basic_press"));
        assertThat(definition.displayNameKey()).isEqualTo("machine.mmcr.basic_press");
        assertThat(definition.controller()).isEqualTo(ControllerSpec.builder().build());
        assertThat(definition.structureStages()).hasSize(1);
        assertThat(definition.maxParallelism()).isEqualTo(1);
        assertThat(definition.parallelizable()).isFalse();
        assertThat(definition.factory().hasFactory()).isFalse();
        assertThat(definition.role()).isEqualTo(MachineRole.NORMAL);
        assertThat(definition.failureAction()).isEqualTo(RecipeFailureActions.STILL);

        assertThatThrownBy(() -> definition.structureStages().add(definition.structureStages().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(PublicMachineAdapter.toDynamicMachine(definition).controller())
                .isEqualTo(cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("basic_press")));
    }

    @Test
    void machine_builder_creates_full_default_machines_style_definition_and_converts_it() {
        RecipeModifier speedModifier = new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 0.5F,
                RecipeModifier.Operation.MULTIPLY, false);

        MachineDefinition definition = MachineBuilder.machine(MMCR.id("arc_furnace"))
                .displayNameKey("machine.mmcr.arc_furnace")
                .pattern(pattern -> pattern
                        .layer("CCC", "CIC", "CFC")
                        .where('C', BlockPredicate.block(Blocks.STONE))
                        .where('I', BlockPredicate.block(Blocks.IRON_BLOCK))
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .controller(controller -> controller
                        .textures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                        .allowVerticalFacing()
                        .tooltip("tooltip.mmcr.arc_furnace.0"))
                .appearance(appearance -> appearance
                        .machineBasicBlock(MMCR.id("steel_casing"))
                        .controllerBaseTexture(MMCR.id("block/steel_controller"))
                        .formedPortBaseTexture(MMCR.id("block/steel_port")))
                .ports(ports -> ports.min("item_input_bus", 1).range("energy_input_hatch", 1, 2))
                .portTiers(tiers -> tiers
                        .minItemInput(PortTiers.ItemTier.NORMAL)
                        .minFluidOutput(PortTiers.FluidTier.BIG)
                        .minEnergyInput(PortTiers.EnergyTier.NORMAL))
                .requirements(requirements -> requirements
                        .levelSlot('I', MMCR.id("coil"))
                        .modifier('I', BlockPredicate.block(Blocks.GOLD_BLOCK), List.of(speedModifier), new ItemStack(Blocks.GOLD_BLOCK)))
                .factory(factory -> factory.hasFactory(true).threadLimit(4).thread("smelting", MMCR.id("arc_recipe")))
                .maxParallelism(8)
                .parallelizable(true)
                .failureAction(RecipeFailureActions.RESET)
                .build();

        assertThat(definition.controller().tooltip()).containsExactly("tooltip.mmcr.arc_furnace.0");
        assertThat(definition.appearance().machineBasicBlock()).isEqualTo(MMCR.id("steel_casing"));
        assertThat(definition.portRequirements().requirements()).containsKeys("item_input_bus", "energy_input_hatch");
        assertThat(definition.portTiers().requirements())
                .extracting(PortTiers.Requirement::minTierId)
                .contains("normal", "big");
        assertThat(definition.requirements().levelSlots()).containsEntry('I', MMCR.id("coil"));
        assertThat(definition.requirements().modifierReplacements()).containsKey('I');
        assertThat(definition.factory().threads()).hasSize(1);

        cn.howxu.mmcr.api.machine.Machine converted = PublicMachineAdapter.toDynamicMachine(definition);
        assertThat(converted.registryName()).isEqualTo(MMCR.id("arc_furnace"));
        assertThat(converted.maxParallelism()).isEqualTo(8);
        assertThat(converted.parallelizable()).isTrue();
        assertThat(converted.failureAction()).isEqualTo(RecipeFailureActions.RESET);
        assertThat(converted.hasFactory()).isTrue();
        assertThat(converted.factoryThreadLimit()).isEqualTo(4);
        assertThat(converted.factoryThreads()).hasSize(1);
        assertThat(converted.structureStages().getFirst().levelSlots()).containsValue(MMCR.id("coil"));
        assertThat(converted.structureStages().getFirst().modifierReplacements()).isNotEmpty();
    }

    @Test
    void machine_builder_appends_multi_stage_extensions_and_converts_structure_declarations() {
        MachineDefinition definition = MachineBuilder.machine(MMCR.id("tower"))
                .pattern(pattern -> pattern
                        .layer("CCC", "CFC", "CCC")
                        .where('C', BlockPredicate.block(Blocks.STONE))
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .stage(stage -> stage
                        .extension()
                        .pattern(pattern -> pattern
                                .layer(" S ", "SCS", " S ")
                                .where('S', BlockPredicate.block(Blocks.STONE))
                                .where('C', BlockPredicate.block(Blocks.STONE))
                                .controller('C')))
                .build();

        assertThat(definition.structureStages()).hasSize(2);
        assertThat(definition.structureStages().get(1).kind()).isEqualTo(StructureStage.Kind.EXTENSION);
        assertThat(PublicMachineAdapter.toStructureDefinition(definition).declarations())
                .extracting(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration::kind)
                .containsExactly(
                        cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.FULL,
                        cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.EXTENSION);
    }

    @Test
    void machine_builder_preserves_role_module_semantics_and_rejects_invalid_combinations() {
        Identifier moduleId = MMCR.id("processing_module");
        MachineDefinition host = MachineBuilder.machine(MMCR.id("host"))
                .pattern(pattern -> pattern
                        .layer("CF")
                        .where('C', BlockPredicate.machineCoupler())
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .role(MachineRole.HOST)
                .acceptedModule(moduleId)
                .acceptedModule(moduleId)
                .build();
        MachineDefinition module = MachineBuilder.machine(moduleId)
                .pattern(pattern -> pattern
                        .layer("CF")
                        .where('C', BlockPredicate.machineCoupler())
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .role(MachineRole.MODULE)
                .build();

        assertThat(host.acceptedModuleIds()).containsExactly(moduleId);
        assertThat(PublicMachineAdapter.toRegistration(host).role()).isEqualTo(MachineRole.HOST);
        assertThat(PublicMachineAdapter.toRegistration(module).role()).isEqualTo(MachineRole.MODULE);

        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_host"))
                .pattern(pattern -> pattern
                        .layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .role(MachineRole.HOST)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accept");
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_normal"))
                .pattern(pattern -> pattern
                        .layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .acceptedModule(moduleId)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HOST");
    }

    @Test
    void machine_builder_rejects_missing_required_fields_and_invalid_values() {
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("missing_pattern")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pattern");
        assertThatThrownBy(() -> MachineBuilder.machine(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("bad_parallelism"))
                .pattern(pattern -> pattern
                        .layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .maxParallelism(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxParallelism");
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("bad_factory"))
                .pattern(pattern -> pattern
                        .layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .factory(factory -> factory.threadLimit(0))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threadLimit");
    }
}
