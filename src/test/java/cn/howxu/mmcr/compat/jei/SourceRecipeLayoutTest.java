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
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.ReadableNumber;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.StringSplitter;
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
 * Exercises the Source rows consumed by the JEI category, including clipping and hover bounds.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourceRecipeLayoutTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void sourceOnlyInputOutputAndBothHaveIndependentRowsBeforeMetadata() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            for (List<MachineRequirement> source : List.of(
                    List.<MachineRequirement>of(SourceRequirement.input(10_000L)),
                    List.<MachineRequirement>of(SourceRequirement.output(3_000_000_001L)),
                    List.<MachineRequirement>of(SourceRequirement.input(10_000L), SourceRequirement.output(3_000_000_001L)))) {
                MachineRecipeDisplay display = display(source);
                MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);

                assertThat(layout.inputs().slots()).isEmpty();
                assertThat(layout.outputs().slots()).isEmpty();
                assertThat(layout.sourceTextLines()).hasSize(source.size());
                assertThat(layout.sourceTextLines()).extracting(line -> line.entry().role())
                        .containsExactlyElementsOf(source.stream().map(requirement -> requirement.io() == IOType.INPUT
                                ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT).toList());
                assertRowsAreSeparate(layout, display, 150);
            }
        }
    }

    @Test
    void mixedItemGridsAndOverflowReserveSourceRowsAtEveryGuiScale() {
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
                    assertThat(layout.sourceTextLines()).hasSize(2);
                    assertThat(layout.inputs().slots()).allSatisfy(slot -> assertThat(slot.entry().kind()).isEqualTo(MachineRecipeLayout.Kind.ITEM));
                    assertThat(layout.outputs().slots()).allSatisfy(slot -> assertThat(slot.entry().kind()).isEqualTo(MachineRecipeLayout.Kind.ITEM));
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
    void translatedLongTotalsAreBoundedAndExactTooltipsUseTheRealRowHitArea() throws Exception {
        Language previous = Language.getInstance();
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            for (String language : List.of("en_us", "zh_cn")) {
                injectLanguage(language);
                StringSplitter splitter = new StringSplitter((codePoint, style) -> codePoint > 127 ? 9F : 6F);
                for (long amount : List.of(1L, 10_000L, 3_000_000_001L, Long.MAX_VALUE)) {
                    MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display(List.of(
                            SourceRequirement.input(amount), SourceRequirement.output(amount))), 4);
                    for (MachineRecipeLayout.TextPlan line : layout.sourceTextLines()) {
                        Component full = (Component) line.entry().ingredient();
                        var visible = MachineRecipeCategory.sourceTextLine(line, splitter);
                        assertThat(visible.getString()).isNotEmpty();
                        assertThat(splitter.stringWidth(visible) * 0.85F).isLessThanOrEqualTo((float) line.width());
                        assertThat(line.x() + line.width()).isLessThanOrEqualTo(MachineRecipeLayout.CATEGORY_WIDTH);
                        assertThat(full.getString()).contains(ReadableNumber.formatExact(amount));
                        assertThat(MachineRecipeCategory.sourceTooltip(layout, line.x(), line.y())).contains(full);
                        assertThat(MachineRecipeCategory.sourceTooltip(layout, line.x() + line.width() - 0.01,
                                line.y() + MachineRecipeLayout.TEXT_LINE_SPACING - 0.01)).contains(full);
                        assertThat(MachineRecipeCategory.sourceTooltip(layout, line.x() - 0.01, line.y())).isEmpty();
                        assertThat(MachineRecipeCategory.sourceTooltip(layout, line.x() + line.width(), line.y())).isEmpty();
                        assertThat(line.contains(line.x(), line.y() - 0.01)).isFalse();
                        assertThat(line.contains(line.x(), line.y() + MachineRecipeLayout.TEXT_LINE_SPACING)).isFalse();
                        assertThat(line.entry().isTextOnly()).isTrue();
                        assertThat(line.entry().transferable()).isFalse();
                    }
                    assertThat(MachineRecipeCategory.sourceTooltip(layout, 8, layout.durationTextY())).isEmpty();
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
