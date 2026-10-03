package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredient;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.ReadableNumber;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises native Source slots, priority, overflow and exact translated tooltips.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourceRecipeLayoutTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void sourceOnlyInputOutputAndBothHaveSlotsBeforeMetadata() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            for (List<MachineRequirement> source : List.of(
                    List.<MachineRequirement>of(SourceRequirement.input(10_000L)),
                    List.<MachineRequirement>of(SourceRequirement.output(3_000_000_001L)),
                    List.<MachineRequirement>of(SourceRequirement.input(10_000L), SourceRequirement.output(3_000_000_001L)))) {
                MachineRecipeDisplay display = display(source);
                MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);

                List<MachineRecipeLayout.SlotPlan> slots = Stream.concat(layout.inputs().slots().stream(),
                        layout.outputs().slots().stream()).toList();
                assertThat(slots).hasSize(source.size());
                assertThat(layout.sourceTextLines()).isEmpty();
                assertThat(slots).extracting(slot -> slot.entry().displayEntry().role())
                        .containsExactlyElementsOf(source.stream().map(requirement -> requirement.io() == IOType.INPUT
                                ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT).toList());
                assertRowsAreSeparate(layout, display, 150);
            }
        }
    }

    @Test
    void mixedItemGridsAndOverflowKeepSourceFirstAtEveryGuiScale() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            for (int itemCount : List.of(4, 50)) {
                List<MachineRequirement> mixed = new ArrayList<>();
                mixed.add(SourceRequirement.input(10_000L));
                for (int index = 0; index < itemCount; index++) {
                    mixed.add(new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
                    mixed.add(new ItemRequirement(IOType.OUTPUT, null, 1, new ItemStack(Holder.direct(Items.GOLD_INGOT))));
                }
                mixed.add(SourceRequirement.output(3_000_000_001L));
                MachineRecipeDisplay display = display(mixed);
                for (int scale = 1; scale <= 4; scale++) {
                    MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, scale);
                    assertThat(layout.sourceTextLines()).isEmpty();
                    for (var region : List.of(layout.inputs(), layout.outputs())) {
                        assertThat(region.slots().getFirst().entry().displayEntry().typeId()).isEqualTo(ArsSourceIds.SOURCE);
                        assertThat(region.slots().subList(1, region.slots().size()))
                                .allSatisfy(slot -> assertThat(slot.entry().kind()).isEqualTo(MachineRecipeLayout.Kind.ITEM));
                        assertThat(region.hiddenEntries().size() + region.slots().size()).isEqualTo(itemCount + 1);
                        assertThat(region.overflowSlot() != null).isEqualTo(itemCount == 50);
                    }
                    assertThat(layout.inputs().hiddenEntries()).allSatisfy(entry -> assertThat(entry.kind()).isEqualTo(MachineRecipeLayout.Kind.ITEM));
                    assertThat(layout.outputs().hiddenEntries()).allSatisfy(entry -> assertThat(entry.kind()).isEqualTo(MachineRecipeLayout.Kind.ITEM));
                    assertRowsAreSeparate(layout, display, switch (scale) {
                        case 1 -> 300;
                        case 2 -> 280;
                        case 3 -> 220;
                        default -> 150;
                    });
                }
            }
        }
    }

    @Test
    void nativeSlotsPreserveExactLongTotalsAndTranslatedTooltipDirections() throws Exception {
        Language previous = Language.getInstance();
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            for (String language : List.of("en_us", "zh_cn")) {
                injectLanguage(language);
                for (long amount : List.of(1L, 10_000L, 3_000_000_001L, Long.MAX_VALUE)) {
                    MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display(List.of(
                            SourceRequirement.input(amount), SourceRequirement.output(amount))), 4);
                    List<MachineRecipeLayout.SlotPlan> slots = Stream.concat(layout.inputs().slots().stream(),
                            layout.outputs().slots().stream()).toList();
                    assertThat(slots).hasSize(2);
                    for (var slot : slots) {
                        var entry = slot.entry().displayEntry();
                        SourceJeiIngredient ingredient = (SourceJeiIngredient) entry.ingredient();
                        assertThat(ingredient.amount()).isEqualTo(amount);
                        assertThat(ingredient.input()).isEqualTo(entry.role() == RecipeIngredientRole.INPUT);
                        assertThat(ingredient.tooltip()).isEqualTo(Component.translatable(ingredient.input()
                                ? "jei.mmcr.machine_recipe.source_input" : "jei.mmcr.machine_recipe.source_output",
                                ReadableNumber.formatExact(amount)));
                        assertThat(ingredient.tooltip().getString()).contains(ReadableNumber.formatExact(amount));
                        assertThat(entry.ingredientType()).isSameAs(SourceJeiIngredient.TYPE);
                        assertThat(entry.isTextOnly()).isFalse();
                        assertThat(entry.transferable()).isFalse();
                        assertThat(slot.x() + 16).isLessThanOrEqualTo(MachineRecipeLayout.CATEGORY_WIDTH);
                    }
                    assertThat(layout.sourceTextLines()).isEmpty();
                }
            }
        } finally {
            Language.inject(previous);
        }
    }

    private static void assertRowsAreSeparate(MachineRecipeLayout layout, MachineRecipeDisplay display, int categoryHeight) {
        int gridBottom = Stream.concat(layout.inputs().slots().stream(), layout.outputs().slots().stream())
                .mapToInt(slot -> slot.y() + 18).max().orElse(26);
        for (var region : List.of(layout.inputs(), layout.outputs())) {
            if (region.overflowSlot() != null) gridBottom = Math.max(gridBottom, region.overflowSlot().y() + 18);
        }
        int previousBottom = gridBottom;
        for (MachineRecipeLayout.TextPlan line : layout.sourceTextLines()) {
            assertThat(line.y()).isGreaterThanOrEqualTo(previousBottom);
            previousBottom = line.y() + MachineRecipeLayout.TEXT_LINE_SPACING;
        }
        assertThat(layout.durationTextY()).isGreaterThanOrEqualTo(previousBottom);
        assertThat(layout.durationTextY() + MachineRecipeLayout.TEXT_LINE_SPACING).isLessThanOrEqualTo(categoryHeight);
        assertThat(layout.informationTextY(display)).isGreaterThan(layout.durationTextY());
    }

    private static MachineRecipeDisplay display(List<MachineRequirement> requirements) {
        return MachineRecipeDisplay.from(new MachineRecipe(MMCR.id("source_layout"), MMCR.id("test_cube"), 20,
                requirements, List.of(), List.of(), 0, 1, false, false, false, Set.of()));
    }

    private static void injectLanguage(String language) throws Exception {
        Map<String, String> translations = new HashMap<>();
        try (var stream = SourceRecipeLayoutTest.class.getResourceAsStream("/assets/mmcr/lang/" + language + ".json")) {
            Language.loadFromJson(stream, translations::put);
        }
        var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
        constructor.setAccessible(true);
        Language.inject(constructor.newInstance(translations, false));
    }
}
