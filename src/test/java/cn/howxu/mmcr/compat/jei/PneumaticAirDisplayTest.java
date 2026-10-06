package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/** Exercises the production recipe conversion and JEI air metadata without loading native PNC types.
 * @author howxu <dev@howxu.cn>
 */
class PneumaticAirDisplayTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:air_display");
    private static final StructureRegistration.Snapshot EMPTY_STRUCTURE =
            new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of());

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        PneumaticRecipeTypes.register();
        CreateRecipeTypes.register();
        if (RequirementHandlerRegistry.canonicalType(LoadedHeatRequirement.TEMPERATURE_TYPE) == null) {
            RequirementHandlerRegistry.register(LoadedHeatRequirement.TEMPERATURE_TYPE);
        }
        if (RequirementHandlerRegistry.canonicalType(LoadedHeatRequirement.HEAT_TYPE) == null) {
            RequirementHandlerRegistry.register(LoadedHeatRequirement.HEAT_TYPE);
        }
    }

    @Test
    void production_conversion_keeps_multiple_air_rows_without_duplicate_ingredient_slots() {
        var display = display(builder().inputAir(0, 4F).inputAir(40, 4.5F, List.of("drive"))
                .outputAir(80, List.of("generator")).outputAir(0).build());

        assertThat(display.airInputs()).containsExactly(
                new MachineRecipeDisplay.AirDisplay(0, 4F, List.of()),
                new MachineRecipeDisplay.AirDisplay(40, 4.5F, List.of("drive")));
        assertThat(display.airOutputs()).containsExactly(
                new MachineRecipeDisplay.AirDisplay(80, 0F, List.of("generator")),
                new MachineRecipeDisplay.AirDisplay(0, 0F, List.of()));
        assertThat(display.entries()).isEmpty();
        for (int scale = 1; scale <= 5; scale++) {
            var layout = MachineRecipeLayout.forDisplay(display, scale);
            assertThat(layout.inputs().slots()).isEmpty();
            assertThat(layout.outputs().slots()).isEmpty();
            assertThat(layout.hasInputOverflow()).isFalse();
            assertThat(layout.hasOutputOverflow()).isFalse();
            int followingY = layout.airTextY(display) + 4 * MachineRecipeLayout.TEXT_LINE_SPACING;
            assertThat(layout.levelRequirementSlotY(display, 0)).isEqualTo(followingY);
            assertThat(layout.stageRequirementTextY(display)).isEqualTo(followingY);
            assertThat(layout.smartInterfaceTextY(display)).isEqualTo(followingY);
            assertThat(layout.informationTextY(display)).isEqualTo(followingY);
            assertThat(layout.lastMetadataTextY(display)).isEqualTo(followingY - MachineRecipeLayout.TEXT_LINE_SPACING);
        }
        assertThatThrownBy(() -> display.airInputs().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> display.airOutputs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pressure_only_consuming_input_and_output_use_distinct_translation_arguments() {
        var display = display(builder().inputAir(0, 4F).inputAir(40, 4.1F).outputAir(80).outputAir(0).build());
        var condition = display.airInputs().get(0);
        var input = display.airInputs().get(1);
        var output = display.airOutputs().get(0);

        assertTranslation(condition.label(true), "air_condition", "4");
        assertTranslation(condition.tooltip(true).getFirst(), "air_condition.tooltip", "4");
        assertTranslation(input.label(true), "air_in", "40", "4.1");
        assertTranslation(input.tooltip(true).getFirst(), "air_in.tooltip", 40L, "4.1");
        assertTranslation(output.label(false), "air_out", "80");
        assertTranslation(output.tooltip(false).getFirst(), "air_out.tooltip", 80L);
        assertTranslation(display.airOutputs().get(1).label(false), "air_out", "0");
        assertTranslation(display.airOutputs().get(1).tooltip(false).getFirst(), "air_out.tooltip", 0L);
    }

    @Test
    void actual_en_cn_resources_render_tooltip_keys_and_exact_long_arguments() throws Exception {
        Language previous = Language.getInstance();
        try {
            for (String language : List.of("en_us", "zh_cn")) {
                Map<String, String> translations = new HashMap<>();
                try (var stream = getClass().getResourceAsStream("/assets/mmcr/lang/" + language + ".json")) {
                    assertThat(stream).isNotNull();
                    Language.loadFromJson(stream, translations::put);
                }
                var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
                constructor.setAccessible(true);
                Language.inject(constructor.newInstance(translations, false));
                var display = display(builder().inputAir(0, 4F).inputAir(40, 4.1F)
                        .inputAir(10_000_000_000L, 4.5F).outputAir(80).outputAir(0).outputAir(Long.MAX_VALUE).build());

                assertRenderedTranslation(display.airInputs().get(0).tooltip(true).getFirst(), translations,
                        "air_condition.tooltip", "4");
                assertRenderedTranslation(display.airInputs().get(1).tooltip(true).getFirst(), translations,
                        "air_in.tooltip", 40L, "4.1");
                assertRenderedTranslation(display.airInputs().get(2).tooltip(true).getFirst(), translations,
                        "air_in.tooltip", 10_000_000_000L, "4.5");
                assertRenderedTranslation(display.airOutputs().get(0).tooltip(false).getFirst(), translations,
                        "air_out.tooltip", 80L);
                assertRenderedTranslation(display.airOutputs().get(1).tooltip(false).getFirst(), translations,
                        "air_out.tooltip", 0L);
                assertRenderedTranslation(display.airOutputs().get(2).tooltip(false).getFirst(), translations,
                        "air_out.tooltip", Long.MAX_VALUE);
            }
        } finally {
            Language.inject(previous);
        }
    }

    @Test
    void canonical_recipe_codec_round_trip_preserves_long_rates_directions_pressure_and_tags() {
        var original = display(builder().inputAir(10_000_000_000L, 4.1F, List.of("drive"))
                .outputAir(Long.MAX_VALUE, List.of("generator")).build());
        var json = MachineRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, original.recipe()).getOrThrow();
        var decoded = MachineRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        var roundTrip = MachineRecipeDisplay.from(decoded);

        assertThat(roundTrip.airInputs()).isEqualTo(original.airInputs());
        assertThat(roundTrip.airOutputs()).isEqualTo(original.airOutputs());
        assertThat(roundTrip.entries()).isEmpty();
        assertTranslation(roundTrip.airInputs().getFirst().label(true), "air_in", "10.00G", "4.1");
        assertTranslation(roundTrip.airOutputs().getFirst().label(false), "air_out", "9.22E");
        assertTranslation(roundTrip.airInputs().getFirst().tooltip(true).getFirst(), "air_in.tooltip", 10_000_000_000L, "4.1");
        assertTranslation(roundTrip.airOutputs().getFirst().tooltip(false).getFirst(), "air_out.tooltip", Long.MAX_VALUE);
    }

    @Test
    void metadata_uses_effective_runtime_rates_without_scaling_pressure_or_tags() {
        var modifierId = ResourceLocation.parse("test:air_display_modifier");
        var modifiers = ModifierDefinition.of("output", "output", 3, "multiply", false);
        var definition = builder().inputAir(40, 4.1F, List.of("drive"))
                .outputAir(80, List.of("generator")).modifier(modifierId).build();
        var snapshot = new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of(modifierId, modifiers));
        var display = MachineRecipeDisplay.from(MachineRecipeConverter.toRecipe(definition, snapshot));

        assertThat(display.airInputs()).containsExactly(new MachineRecipeDisplay.AirDisplay(40, 4.1F, List.of("drive")));
        assertThat(display.airOutputs()).containsExactly(new MachineRecipeDisplay.AirDisplay(240, 0F, List.of("generator")));
        assertTranslation(display.airInputs().getFirst().label(true), "air_in", "40", "4.1");
        assertTranslation(display.airOutputs().getFirst().label(false), "air_out", "240");
    }

    @Test
    void air_display_defensively_copies_tags_and_builder_tags_survive_conversion() {
        var tags = new ArrayList<>(List.of("drive"));
        var definition = builder().inputAir(40, 4F, tags).build();
        var air = new MachineRecipeDisplay.AirDisplay(40, 4F, tags);
        tags.clear();

        assertThat(air.tags()).containsExactly("drive");
        assertThat(display(definition).airInputs()).containsExactly(air);
        assertThatThrownBy(() -> air.tags().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mixed_energy_stress_air_heat_shift_all_following_rows_consistently_at_every_gui_scale() {
        var levelType = ResourceLocation.parse("test:air_display_level_type");
        var levelId = ResourceLocation.parse("test:air_display_level");
        var type = new LevelType(levelType, Component.translatable("test.air.level"));
        var level = new MachineLevel(levelId, levelType, 0,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(type);
        TestBootstrap.registerLevel(level);
        TestBootstrap.freezeRegistration();
        var snapshot = new StructureRegistration.Snapshot(Map.of(), Map.of(levelType, type), Map.of(levelId, level), Map.of());
        var baselineDefinition = mixedBuilder(levelType, levelId).build();
        var mixedDefinition = mixedBuilder(levelType, levelId)
                .inputAir(0, 4F).inputAir(40, 4.5F).outputAir(80).outputAir(120).build();
        var baseline = MachineRecipeDisplay.from(MachineRecipeConverter.toRecipe(baselineDefinition, snapshot));
        var mixed = MachineRecipeDisplay.from(MachineRecipeConverter.toRecipe(mixedDefinition, snapshot));

        assertThat(mixed.entries()).extracting(JeiDisplayEntry::typeId)
                .containsExactlyElementsOf(baseline.entries().stream().map(JeiDisplayEntry::typeId).toList());
        assertThat(mixed.minimumTemperature()).hasValue(300D);
        assertThat(mixed.outputHeat()).hasValue(500D);
        int spacing = MachineRecipeLayout.TEXT_LINE_SPACING;
        for (int scale = 1; scale <= 5; scale++) {
            var before = MachineRecipeLayout.forDisplay(baseline, scale);
            var after = MachineRecipeLayout.forDisplay(mixed, scale);
            assertThat(after.inputs().slots()).extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                    .containsExactlyElementsOf(before.inputs().slots().stream().map(slot -> tuple(slot.x(), slot.y())).toList());
            assertThat(after.outputs().slots()).extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                    .containsExactlyElementsOf(before.outputs().slots().stream().map(slot -> tuple(slot.x(), slot.y())).toList());
            assertThat(after.durationTextY()).isEqualTo(before.durationTextY());
            assertThat(after.stressTextY(mixed)).isEqualTo(after.durationTextY() + 3 * spacing);
            assertThat(after.airTextY(mixed)).isEqualTo(after.stressTextY(mixed) + 2 * spacing);
            assertThat(after.hostRequirementTextY()).isEqualTo(after.airTextY(mixed) + 6 * spacing);
            assertThat(after.hostRequirementTextY()).isEqualTo(before.hostRequirementTextY() + 4 * spacing);
            assertThat(after.levelRequirementSlotY(mixed, 0)).isEqualTo(after.hostRequirementTextY() + spacing);
            assertThat(after.levelRequirementSlotY(mixed, 0)).isEqualTo(before.levelRequirementSlotY(baseline, 0) + 4 * spacing);
            assertThat(after.stageRequirementTextY(mixed)).isEqualTo(after.levelRequirementSlotY(mixed, 0) + 18 + spacing);
            assertThat(after.stageRequirementTextY(mixed)).isEqualTo(before.stageRequirementTextY(baseline) + 4 * spacing);
            assertThat(after.smartInterfaceTextY(mixed)).isEqualTo(after.stageRequirementTextY(mixed) + spacing);
            assertThat(after.smartInterfaceTextY(mixed)).isEqualTo(before.smartInterfaceTextY(baseline) + 4 * spacing);
            assertThat(after.lastMetadataTextY(mixed)).isEqualTo(after.smartInterfaceTextY(mixed) + spacing);
            assertThat(after.informationTextY(mixed)).isEqualTo(before.informationTextY(baseline) + 4 * spacing);
            assertThat(after.informationTextY(mixed)).isEqualTo(after.lastMetadataTextY(mixed) + spacing);
            int categoryHeight = switch (scale) { case 1 -> 300; case 2 -> 280; case 3 -> 220; default -> 150; };
            assertThat(after.informationLineCapacity(mixed, categoryHeight))
                    .isEqualTo(Math.max(0, before.informationLineCapacity(baseline, categoryHeight) - 4));
        }
    }

    @Test
    void air_reserves_host_height_and_moves_hidden_ingredients_to_existing_overflow_at_scale_four_and_five() {
        var baselineBuilder = builder().inputEnergy(40).outputEnergy(80)
                .requiredHost(ResourceLocation.parse("test:air_display_host"));
        var airBuilder = builder().inputEnergy(40).outputEnergy(80).inputAir(40, 4F).outputAir(80)
                .requiredHost(ResourceLocation.parse("test:air_display_host"));
        for (int index = 0; index < 15; index++) {
            baselineBuilder.inputItem(Items.IRON_INGOT, index + 1);
            airBuilder.inputItem(Items.IRON_INGOT, index + 1);
        }
        var baseline = display(baselineBuilder.build());
        var air = display(airBuilder.build());
        for (int scale : List.of(4, 5)) {
            var before = MachineRecipeLayout.forDisplay(baseline, scale);
            var after = MachineRecipeLayout.forDisplay(air, scale);
            assertThat(before.inputs().slots()).hasSize(15);
            assertThat(before.hasInputOverflow()).isFalse();
            assertThat(before.durationTextY()).isEqualTo(102);
            assertThat(before.hostRequirementTextY()).isEqualTo(132);
            assertMandatoryRowsInside(before, baseline);

            assertThat(after.inputs().slots()).hasSize(11);
            assertThat(after.inputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(48, 62));
            assertThat(after.inputs().hiddenEntries()).extracting(MachineRecipeLayout.EntryPlan::index)
                    .containsExactly(11, 12, 13, 14);
            assertThat(after.inputs().hiddenEntries()).extracting(entry -> entry.displayEntry().count())
                    .containsExactly(12, 13, 14, 15);
            assertThat(after.durationTextY()).isEqualTo(84);
            assertThat(after.hostRequirementTextY()).isEqualTo(134);
            assertThat(after.hasInputOverflow()).isTrue();
            assertThat(after.hasOutputOverflow()).isFalse();
            assertMandatoryRowsInside(after, air);
            assertThat(tooltipAt(air, after, after.durationTextX(), after.airTextY(air)).getFirst())
                    .isEqualTo(air.airInputs().getFirst().tooltip(true).getFirst());
        }
    }

    @Test
    void recipes_without_air_keep_existing_grid_limits_and_coordinates_at_every_scale() {
        var builder = builder().inputEnergy(40).outputEnergy(80)
                .requiredHost(ResourceLocation.parse("test:air_display_host"));
        for (int index = 0; index < 50; index++) {
            builder.inputItem(Items.IRON_INGOT, 1).outputItem(Items.GOLD_INGOT, 1);
        }
        var display = display(builder.build());
        for (int scale = 1; scale <= 5; scale++) {
            int rows = switch (scale) { case 1 -> 14; case 2 -> 11; case 3 -> 8; default -> 5; };
            var layout = MachineRecipeLayout.forDisplay(display, scale);
            assertThat(layout.durationTextY()).isEqualTo(12 + 18 * rows);
            assertThat(layout.hostRequirementTextY()).isEqualTo(layout.durationTextY() + 30);
            for (var region : List.of(layout.inputs(), layout.outputs())) {
                assertThat(region.slots()).hasSize(3 * rows - 1);
                assertThat(region.hiddenEntries()).hasSize(50 - (3 * rows - 1));
                assertThat(region.overflowSlot().y()).isEqualTo(8 + 18 * (rows - 1));
            }
        }
    }

    @Test
    void air_budget_includes_level_slot_geometry_stage_and_smart_rows_at_every_scale() {
        var levelType = ResourceLocation.parse("test:air_boundary_level_type");
        var levelId = ResourceLocation.parse("test:air_boundary_level");
        var type = new LevelType(levelType, Component.translatable("test.air.level"));
        var level = new MachineLevel(levelId, levelType, 0,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(type);
        TestBootstrap.registerLevel(level);
        TestBootstrap.freezeRegistration();
        var snapshot = new StructureRegistration.Snapshot(Map.of(), Map.of(levelType, type), Map.of(levelId, level), Map.of());
        var builder = builder().inputEnergy(40).outputEnergy(80).inputAir(40, 4F).outputAir(80)
                .requiredHost(ResourceLocation.parse("test:air_display_host"))
                .levelRequirement(levelType, levelId).stageRequirement(2)
                .smartInterface(SmartInterfaceRequirement.input("mode", 1F))
                .smartInterface(SmartInterfaceRequirement.output("mode", 2F));
        for (int index = 0; index < 15; index++) {
            builder.inputItem(Items.IRON_INGOT, 1).outputItem(Items.GOLD_INGOT, 1);
        }
        var display = MachineRecipeDisplay.from(MachineRecipeConverter.toRecipe(builder.build(), snapshot));
        for (int scale = 1; scale <= 5; scale++) {
            var layout = MachineRecipeLayout.forDisplay(display, scale);
            assertThat(layout.height()).isEqualTo(switch (scale) {
                case 1 -> 300; case 2 -> 280; case 3 -> 220; default -> 150;
            });
            assertMandatoryRowsInside(layout, display);
            if (scale >= 4) {
                assertThat(layout.durationTextY()).isEqualTo(30);
                assertThat(layout.inputs().slots()).hasSize(2);
                assertThat(layout.outputs().slots()).hasSize(2);
                assertThat(layout.inputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(48, 8));
                assertThat(layout.outputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(138, 8));
                assertThat(layout.inputs().hiddenEntries()).hasSize(13);
                assertThat(layout.outputs().hiddenEntries()).hasSize(13);
                assertThat(layout.lastMetadataTextY(display) + MachineRecipeLayout.TEXT_LINE_SPACING).isEqualTo(148);
            } else {
                assertThat(layout.inputs().slots()).hasSize(15);
                assertThat(layout.outputs().slots()).hasSize(15);
                assertThat(layout.hasInputOverflow()).isFalse();
                assertThat(layout.hasOutputOverflow()).isFalse();
                assertThat(layout.durationTextY()).isEqualTo(102);
            }
        }
    }

    @Test
    void eight_air_rows_use_the_minimum_ingredient_row_and_fit_exactly_at_scale_four_and_five() {
        var builder = builder().inputEnergy(40).outputEnergy(80)
                .requiredHost(ResourceLocation.parse("test:air_display_host"));
        for (int index = 0; index < 4; index++) builder.inputAir(40 + index, 4F).outputAir(80 + index);
        for (int index = 0; index < 50; index++) builder.inputItem(Items.IRON_INGOT, 1);
        var display = display(builder.build());
        for (int scale = 1; scale <= 5; scale++) {
            int rows = switch (scale) { case 1 -> 9; case 2 -> 8; case 3 -> 4; default -> 1; };
            var layout = MachineRecipeLayout.forDisplay(display, scale);
            assertThat(layout.inputs().slots()).hasSize(3 * rows - 1);
            assertThat(layout.inputs().hiddenEntries()).hasSize(50 - (3 * rows - 1));
            assertThat(layout.inputs().overflowSlot())
                    .isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(48, 8 + 18 * (rows - 1)));
            assertThat(layout.durationTextY()).isEqualTo(12 + 18 * rows);
            assertMandatoryRowsInside(layout, display);
            if (scale >= 4) {
                assertThat(layout.hostRequirementTextY() + MachineRecipeLayout.TEXT_LINE_SPACING).isEqualTo(layout.height());
                assertThat(layout.informationLineCapacity(display, layout.height())).isZero();
            }
        }
    }

    @Test
    void metadata_larger_than_category_keeps_a_usable_overflow_row_without_negative_slot_capacity() {
        var builder = builder();
        for (int index = 0; index < 20; index++) builder.inputAir(40 + index, 4F);
        for (int index = 0; index < 15; index++) builder.inputItem(Items.IRON_INGOT, 1);
        var display = display(builder.build());
        for (int scale : List.of(4, 5)) {
            var layout = MachineRecipeLayout.forDisplay(display, scale);
            assertThat(layout.inputs().slots()).hasSize(2);
            assertThat(layout.inputs().hiddenEntries()).hasSize(13);
            assertThat(layout.inputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(48, 8));
            assertThat(layout.durationTextY()).isEqualTo(30);
            assertThat(layout.informationLineCapacity(display, layout.height())).isZero();
        }
    }

    @Test
    void air_tooltips_cover_icon_and_whole_row_and_stop_at_heat_and_horizontal_bounds() {
        var display = display(builder().inputEnergy(40).inputStress(8, 32).outputStress(16, -64)
                .inputAir(0, 4F).inputAir(40, 4.1F).outputAir(80)
                .requirement(LoadedHeatRequirement.minimumTemperature(300D)).build());
        for (int scale = 1; scale <= 5; scale++) {
            var layout = MachineRecipeLayout.forDisplay(display, scale);
            int y = layout.airTextY(display);
            int spacing = MachineRecipeLayout.TEXT_LINE_SPACING;
            for (double x : new double[]{layout.durationTextX(), MachineRecipeLayout.CATEGORY_WIDTH - layout.durationTextX() - 0.1}) {
                assertTranslation(tooltipAt(display, layout, x, y).getFirst(), "air_condition.tooltip", "4");
                assertTranslation(tooltipAt(display, layout, x, y + spacing - 0.1).getFirst(), "air_condition.tooltip", "4");
                assertTranslation(tooltipAt(display, layout, x, y + spacing).getFirst(), "air_in.tooltip", 40L, "4.1");
                assertTranslation(tooltipAt(display, layout, x, y + 2 * spacing).getFirst(), "air_out.tooltip", 80L);
            }
            assertThat(tooltipAt(display, layout, layout.durationTextX() - 0.1, y)).isEmpty();
            assertThat(tooltipAt(display, layout, MachineRecipeLayout.CATEGORY_WIDTH - layout.durationTextX(), y)).isEmpty();
            assertThat(tooltipAt(display, layout, layout.durationTextX(), y - 0.1)).isEmpty();
            assertThat(tooltipAt(display, layout, layout.durationTextX(), y + 3 * spacing)).isEmpty();
        }
    }

    @Test
    void missing_pressure_tube_does_not_create_a_default_air_item_icon() {
        assertThat(BuiltInRegistries.ITEM.containsKey(PneumaticIds.ADVANCED_PRESSURE_TUBE)).isFalse();
        List<ItemLike> icons = new ArrayList<>();
        new MachineRecipeCategory(guiHelper(icons), ID, ResourceLocation.parse("mmcr:test_cube"));

        // The optional mana icon is valid; missing air must not add a default item icon.
        assertThat(icons).hasSize(2 + (BuiltInRegistries.ITEM.containsKey(BotaniaManaIds.CREATIVE_POOL) ? 1 : 0));
        assertThat(display(builder().inputAir(0, 4F).build()).airInputs()).singleElement()
                .satisfies(air -> assertTranslation(air.label(true), "air_condition", "4"));
    }

    private static MachineRecipeBuilder builder() {
        return MachineRecipeBuilder.recipe(ID).recipePool(ID).duration(20);
    }

    private static MachineRecipeBuilder mixedBuilder(ResourceLocation levelType, ResourceLocation levelId) {
        return builder().inputItem(Items.IRON_INGOT, 2).inputFluid(Fluids.WATER, 100).outputItem(Items.GOLD_INGOT, 1)
                .inputEnergy(40).outputEnergy(80).inputStress(8, 32).outputStress(16, -64)
                .requirement(LoadedHeatRequirement.minimumTemperature(300D))
                .requirement(LoadedHeatRequirement.outputHeat(500D))
                .requiredHost(ResourceLocation.parse("test:air_display_host"))
                .levelRequirement(levelType, levelId).stageRequirement(2)
                .smartInterface(SmartInterfaceRequirement.input("mode", 1F))
                .smartInterface(SmartInterfaceRequirement.output("mode", 2F));
    }

    private static MachineRecipeDisplay display(MachineRecipeDefinition definition) {
        return MachineRecipeDisplay.from(MachineRecipeConverter.toRecipe(definition, EMPTY_STRUCTURE));
    }

    private static void assertTranslation(Component component, String suffix, Object... arguments) {
        assertThat(component.getContents()).isInstanceOf(TranslatableContents.class);
        var contents = (TranslatableContents) component.getContents();
        assertThat(contents.getKey()).isEqualTo("jei.mmcr.machine_recipe." + suffix);
        assertThat(contents.getArgs()).containsExactly(arguments);
    }

    private static void assertRenderedTranslation(Component component, Map<String, String> translations,
                                                   String suffix, Object... arguments) {
        assertTranslation(component, suffix, arguments);
        String template = translations.get("jei.mmcr.machine_recipe." + suffix);
        assertThat(template).isNotNull();
        assertThat(template.split("%s", -1)).hasSize(arguments.length + 1);
        assertThat(component.getString()).isEqualTo(String.format(Locale.ROOT, template, arguments))
                .doesNotContain("%s", "jei.mmcr.machine_recipe.");
        for (Object argument : arguments) assertThat(component.getString()).contains(argument.toString());
    }

    private static void assertMandatoryRowsInside(MachineRecipeLayout layout, MachineRecipeDisplay display) {
        int spacing = MachineRecipeLayout.TEXT_LINE_SPACING;
        assertThat(layout.durationTextY()).isGreaterThanOrEqualTo(30);
        assertThat(layout.durationTextY() + spacing).isLessThanOrEqualTo(layout.height());
        assertThat(layout.airTextY(display) + spacing * (display.airInputs().size() + display.airOutputs().size()))
                .isLessThanOrEqualTo(layout.height());
        if (!display.requiredHostIds().isEmpty()) {
            assertThat(layout.hostRequirementTextY() + spacing).isLessThanOrEqualTo(layout.height());
        }
        for (int index = 0; index < display.recipe().levelRequirements().size(); index++) {
            assertThat(layout.levelRequirementSlotY(display, index) + 18).isLessThanOrEqualTo(layout.height());
        }
        assertThat(layout.stageRequirementTextY(display) + spacing * display.recipe().stageRequirements().size())
                .isLessThanOrEqualTo(layout.height());
        assertThat(layout.smartInterfaceTextY(display)
                + spacing * (display.smartInterfaceInputs().size() + display.smartInterfaceOutputs().size()))
                .isLessThanOrEqualTo(layout.height());
        for (var region : List.of(layout.inputs(), layout.outputs())) {
            assertThat(region.slots()).allSatisfy(slot -> assertThat(slot.y() + 18).isLessThan(layout.durationTextY()));
            if (region.overflowSlot() != null) {
                assertThat(region.overflowSlot().y() + 18).isLessThan(layout.durationTextY());
            }
        }
    }

    private static List<Component> tooltipAt(MachineRecipeDisplay display, MachineRecipeLayout layout, double x, double y) {
        List<Component> lines = new ArrayList<>();
        var tooltip = (ITooltipBuilder) Proxy.newProxyInstance(PneumaticAirDisplayTest.class.getClassLoader(),
                new Class<?>[]{ITooltipBuilder.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("addAll")) {
                        for (Object line : (List<?>) arguments[0]) lines.add((Component) line);
                    }
                    return method.getReturnType().isInstance(proxy) ? proxy : null;
                });
        assertThat(MachineRecipeCategory.appendAirTooltip(tooltip, display, layout, x, y)).isEqualTo(!lines.isEmpty());
        return lines;
    }

    private static IGuiHelper guiHelper(List<ItemLike> icons) {
        IDrawableStatic drawable = (IDrawableStatic) Proxy.newProxyInstance(PneumaticAirDisplayTest.class.getClassLoader(),
                new Class<?>[]{IDrawableStatic.class}, (proxy, method, arguments) ->
                        method.getReturnType() == int.class ? 16 : null);
        return (IGuiHelper) Proxy.newProxyInstance(PneumaticAirDisplayTest.class.getClassLoader(),
                new Class<?>[]{IGuiHelper.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("createDrawableItemLike")) {
                        icons.add((ItemLike) arguments[0]);
                        return drawable;
                    }
                    if (method.getName().equals("getSlotDrawable")) return drawable;
                    if (method.getName().equals("drawableBuilder")) {
                        return Proxy.newProxyInstance(PneumaticAirDisplayTest.class.getClassLoader(),
                                new Class<?>[]{method.getReturnType()}, (builder, builderMethod, builderArguments) ->
                                        builderMethod.getName().equals("build") ? drawable : builder);
                    }
                    return null;
                });
    }
}
