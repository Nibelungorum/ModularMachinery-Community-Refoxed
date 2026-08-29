package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.ContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class KubeJSApiTest {
    private static final Identifier TEST_LEVEL_TYPE = MMCR.id("api_test_level_type");
    private static final Identifier TEST_LEVEL = MMCR.id("api_test_level");
    private final KubeJSApi api = new KubeJSApi();

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void restoreMachineLevels() {
        cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.resetCollector();
        var event = cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.prepare(java.util.Set.of());
        event.registerLevelType(new LevelType(TEST_LEVEL_TYPE, Component.literal("API Test")));
        event.registerLevel(new MachineLevel(TEST_LEVEL, TEST_LEVEL_TYPE, 0,
                new BlockPredicate.OfBlockState(Blocks.EMERALD_BLOCK.defaultBlockState()), ItemStack.EMPTY,
                LevelModifier.IDENTITY));
        MachineLevelRegistry.installSnapshot(event.levelTypes().values(), event.levels().values());
    }

    @Test
    void state_parses_default_and_property_block_states() {
        assertThat(api.state("minecraft:oak_log")).isEqualTo(new BlockPredicate.OfBlockState(Blocks.OAK_LOG.defaultBlockState()));
        assertThat(api.state("minecraft:oak_log[axis=x]")).isEqualTo(new BlockPredicate.OfBlockState(
                Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,
                        Direction.Axis.X)));
    }

    @Test
    void state_rejects_unknown_blocks_properties_and_property_values() {
        assertThatThrownBy(() -> api.state("test:missing_block")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.state("minecraft:oak_log[missing=value]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.state("minecraft:oak_log[axis=invalid]")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tag_accepts_tags_before_server_resources_are_bound() {
        assertThat(api.tag("test:tag_loaded_after_scripts")).isInstanceOf(BlockPredicate.OfTag.class);
    }

    @Test
    void port_tier_requirements_parse_canonical_port_families() {
        var requirements = api.portTierRequirements(List.of(
                "item_input_bus>=small", "item_output_bus>=big",
                "fluid_input_hatch>=normal", "fluid_output_hatch>=big",
                "energy_input_hatch>=small", "energy_output_hatch>=ultimate"));

        assertThat(requirements.requirements()).extracting(requirement -> requirement.id()).containsExactly(
                "item_input_bus>=small", "item_output_bus>=big",
                "fluid_input_hatch>=normal", "fluid_output_hatch>=big",
                "energy_input_hatch>=small", "energy_output_hatch>=ultimate");
    }

    @Test
    void port_requirements_reject_non_integral_negative_and_inverted_ranges() {
        assertThatThrownBy(() -> api.portRequirements(Map.of("item_input_bus", 1.5D))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.portRequirements(Map.of("item_input_bus", -1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.portRequirements(Map.of("item_input_bus", List.of(2, 1)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void facade_rejects_unknown_registry_values_empty_any_of_and_unknown_tiers() {
        assertThatThrownBy(() -> api.block("test:missing_block")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.itemInput("test:missing_item", 1, 1F)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.fluidInput("test:missing_fluid", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.anyOf()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.portTierRequirements(List.of("item_input_bus>=missing"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interface_predicate_factories_expose_all_port_categories() {
        assertThat(api.anyOfItemInput().children()).hasSize(18);
        assertThat(api.anyOfItemOutput().children()).hasSize(18);
        assertThat(api.anyOfFluidInput().children()).hasSize(19);
        assertThat(api.anyOfFluidOutput().children()).hasSize(19);
        assertThat(api.anyOfEnergyInput().children()).hasSize(10);
        assertThat(api.anyOfEnergyOutput().children()).hasSize(10);
    }

    @Test
    void kubejs_interface_predicates_match_extended_and_combined_ports() {
        var combinedInput = ModBlocks.BLOCKS.get("combined_input_basic").get().defaultBlockState();

        assertThat(api.anyOfItemInput().matches(combinedInput)).isTrue();
        assertThat(api.anyOfFluidInput().matches(combinedInput)).isTrue();
        assertThat(api.anyOfItemInput().matches(
                ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState())).isTrue();
        assertThat(api.anyOfFluidInput().matches(
                ModBlocks.BLOCKS.get("extended_fluid_input_hatch_basic").get().defaultBlockState())).isTrue();
        assertThat(api.anyOfEnergyInput().matches(
                ModBlocks.BLOCKS.get("extended_energy_input_hatch_reinforced").get().defaultBlockState())).isTrue();
    }

    @Test
    void interface_predicate_factories_expose_controller_shortcuts() {
        assertThat(api.parallelControllers().children()).hasSize(8);
        assertThat(api.smartInterface()).isInstanceOf(BlockPredicate.DeferredBlock.class);
        assertThat(api.factoryController()).isInstanceOf(BlockPredicate.DeferredBlock.class);
        assertThat(api.dataStorage().matches(ModBlocks.DATA_STORAGE.get().defaultBlockState())).isTrue();
        assertThat(api.dataStorage().matches(Blocks.STONE.defaultBlockState())).isFalse();
    }

    @Test
    void level_requirement_rejects_a_level_from_another_type() {
        var otherType = MMCR.id("api_other_type");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(otherType, Component.literal("Other")));
        TestBootstrap.registerLevel(new MachineLevel(MMCR.id("api_other_level"), otherType, 0,
                new BlockPredicate.OfBlockState(Blocks.EMERALD_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));
        TestBootstrap.freezeRegistration();

        assertThatThrownBy(() -> api.levelRequirement(TEST_LEVEL_TYPE.toString(), "mmcr:api_other_level"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void level_slot_factory_exposes_registered_type_to_server_scripts() {
        var slot = api.levelSlot(TEST_LEVEL_TYPE.toString());

        assertThat(slot).isEqualTo(new LevelSlot(TEST_LEVEL_TYPE));
        assertThatThrownBy(() -> api.levelSlot("test:missing_level_type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown machine level type");
    }

    @Test
    void rhino_converts_list_map_and_varargs_for_the_facade() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "api", api, context);

        context.evaluateString(scope, """
                api.anyOf(api.block('minecraft:stone'), api.block('minecraft:dirt'));
                api.anyOf(api.anyOfItemInput(), api.anyOfFluidOutput());
                api.portRequirements({item_input_bus: [1, 2]});
                api.portTierRequirements(['item_input_bus>=small']);
                """, "api-test", 1, null);
    }

    @Test
    void fluid_stack_creates_a_bound_neoforge_fluid_stack() {
        var stack = api.fluidStack("minecraft:water", 250);

        assertThat(stack.getFluid()).isSameAs(Fluids.WATER);
        assertThat(stack.getAmount()).isEqualTo(250);
    }

    @Test
    void readable_number_methods_are_available_to_kubejs_api() {
        assertThat(api.readableNumber(1_000L)).isEqualTo("1k");
        assertThat(api.readableNumberExact(1_000_000L)).isEqualTo("1,000,000");
    }

    @Test
    void rhino_exposes_controller_screen_scopes_through_api() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "api", api, context);

        assertThat(context.evaluateString(scope, "api.screenScope().CONTROLLER.name() === 'CONTROLLER'",
                "screen-scope-test", 1, null)).isEqualTo(true);
        assertThat(context.evaluateString(scope, "api.screenScope().OPERATION.name() === 'OPERATION'",
                "screen-scope-test", 1, null)).isEqualTo(true);
        assertThat(api.screenScope().CONTROLLER).isSameAs(ControllerScreenTextScope.CONTROLLER);
        assertThat(api.screenScope().OPERATION).isSameAs(ControllerScreenTextScope.OPERATION);
    }

    @Test
    void single_block_modifier_factory_preserves_four_argument_value_state() {
        var predicate = new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK);
        var modifier = new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 0.5F,
                RecipeModifier.Operation.MULTIPLY, false);
        var display = new ItemStack(Blocks.DIAMOND_BLOCK);

        var replacement = api.singleBlockModifier("diamond_speedup", predicate, List.of(modifier), display);

        assertThat(replacement.getModifierName()).isEqualTo("diamond_speedup");
        assertThat(replacement.getReplacement()).isSameAs(predicate);
        assertThat(replacement.getModifiers()).containsExactly(modifier);
        assertThat(replacement.getDescriptiveStack()).isSameAs(display);
        assertThat(replacement.getDescriptionLines()).isEmpty();
    }

    @Test
    void pattern_entry_factory_preserves_base_and_modifier_alternatives() {
        var base = new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE);
        var replacement = api.singleBlockModifier("diamond_speedup",
                new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), ItemStack.EMPTY);

        var entry = api.patternEntry(base, List.of(replacement));

        assertThat(entry.base()).isSameAs(base);
        assertThat(entry.modifiers()).containsExactly(replacement);
    }

    @Test
    void pattern_accepts_plain_block_predicate_without_modifiers() {
        var definition = new MachineStructureBuilderJS("test:plain_pattern_key")
                .pattern("B", Map.of("B", api.block("minecraft:bricks")))
                .createObject();

        assertThat(definition.pattern().pattern().get(BlockPos.ZERO)
                .matches(Blocks.BRICKS.defaultBlockState())).isTrue();
        assertThat(definition.requirements().modifierReplacements()).isEmpty();
    }

    @Test
    void slice_pattern_binds_symbols_and_normalizes_controller() {
        var replacement = api.singleBlockModifier("explicit", api.block("minecraft:stone"), List.of(), ItemStack.EMPTY);
        var definition = new MachineStructureBuilderJS("mmcr_test:iron_compressor")
                .pattern(List.of("XCX", "XXX", "XXX"))
                .set("X", api.patternEntry(api.block("minecraft:bricks"), List.of(replacement)))
                .set("C", api.block("minecraft:blast_furnace"))
                .controller("C")
                .fullStructure(api.portRequirements(Map.of()), api.portTierRequirements(List.of()),
                        List.of(), MachineStructureRequirements.builder().modifier('C', replacement).build())
                .createObject();

        assertThat(definition.pattern().pattern()).hasSize(9);
        assertThat(definition.pattern().pattern().get(BlockPos.ZERO)
                .matches(ModBlocks.controllerFor(Identifier.parse("mmcr_test:iron_compressor")).get().defaultBlockState())).isTrue();
        assertThat(definition.pattern().symbolsByPosition()).containsEntry(BlockPos.ZERO, 'C');
        assertThat(definition.requirements().modifierReplacements()).containsKeys('X', 'C');
    }

    @Test
    void slice_patterns_accept_rhino_javascript_arrays() {
        var builder = new MachineStructureBuilderJS("mmcr_test:iron_compressor");
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "api", api, context);
        ScriptableObject.putProperty(scope, "builder", builder, context);

        context.evaluateString(scope, """
                builder.patternAll([['CXX', 'XXX', 'XXX']]);
                builder.set('X', api.block('minecraft:bricks'));
                builder.set('C', api.block('minecraft:blast_furnace'));
                builder.controller('C');
                """, "builder-test", 1, null);

        assertThat(builder.createObject().pattern().pattern()).hasSize(9);
        assertThat(builder.createObject().pattern().pattern().get(BlockPos.ZERO)
                .matches(ModBlocks.controllerFor(Identifier.parse("mmcr_test:iron_compressor")).get().defaultBlockState())).isTrue();
    }

    @Test
    void all_slices_are_appended_and_unbound_characters_are_skipped() {
        var definition = new MachineStructureBuilderJS("mmcr_test:iron_compressor")
                .patternAll(List.of(
                        List.of("AXA", "XIX", "XXX"),
                        List.of("XXX", "I I", "XBX"),
                        List.of("AXA", "XCX", "XXX")))
                .set("X", api.block("minecraft:bricks"))
                .set("A", api.block("minecraft:stone"))
                .set("B", api.block("minecraft:iron_block"))
                .set("C", api.block("minecraft:blast_furnace"))
                .set("I", api.block("minecraft:hopper"))
                .controller("C")
                .fullStructure(api.portRequirements(Map.of()), api.portTierRequirements(List.of()),
                        List.of(), MachineStructureRequirements.EMPTY)
                .createObject();

        assertThat(definition.pattern().pattern()).containsKeys(
                new BlockPos(-1, -1, -2), new BlockPos(1, -1, -1), BlockPos.ZERO);
        assertThat(definition.pattern().pattern()).doesNotContainKey(new BlockPos(0, 0, -1));
        assertThat(definition.pattern().symbolsByPosition()).containsEntry(BlockPos.ZERO, 'C');
    }

    @Test
    void slice_patterns_reject_inconsistent_dimensions_and_empty_slices() {
        assertThatThrownBy(() -> new MachineStructureBuilderJS("test:bad_width")
                .patternAll(List.of(List.of("XX", "XX"), List.of("XXX", "XXX"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MachineStructureBuilderJS("test:bad_height")
                .patternAll(List.of(List.of("XX", "XX"), List.of("XX"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MachineStructureBuilderJS("test:empty_slice")
                .patternAll(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacy_and_slice_pattern_apis_cannot_be_mixed() {
        assertThatThrownBy(() -> new MachineStructureBuilderJS("test:legacy_then_slice")
                .pattern("B", Map.of("B", api.block("minecraft:bricks")))
                .set("X", api.block("minecraft:stone")))
                .isInstanceOf(IllegalStateException.class);

        var builder = new MachineStructureBuilderJS("test:slice_then_legacy")
                .pattern(List.of("C"))
                .set("C", api.block("minecraft:blast_furnace"));

        assertThatThrownBy(() -> builder.pattern("B", Map.of("B", api.block("minecraft:bricks"))))
                .isInstanceOf(IllegalStateException.class);
    }

}
