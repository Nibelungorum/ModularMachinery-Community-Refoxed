package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.client.recipe.RecipeDetailsLayout;
import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real recipe/registry rows through the production layout and vanilla text splitter.
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeDetailsReachabilityTest {
    private final StringSplitter splitter = new StringSplitter((codePoint, style) -> 3F);
    private Language previousLanguage;
    private List<RecipeInformation> previousInformation;
    private Map<String, String> translations;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        PneumaticRecipeTypes.register();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepareRegistryAndLanguage() throws Exception {
        previousLanguage = Language.getInstance();
        var field = RecipeInformationRegistry.class.getDeclaredField("kubeJSEntries");
        field.setAccessible(true);
        previousInformation = (List<RecipeInformation>) field.get(null);
        RecipeInformationRegistry.replaceKubeJS(List.of());
        translations = new HashMap<>();
        try (var stream = getClass().getResourceAsStream("/assets/mmcr/lang/en_us.json")) {
            Language.loadFromJson(stream, translations::put);
        }
        translations.put("test.details.description", "%s");
        injectLanguage();
    }

    @AfterEach
    void restoreRegistryAndLanguage() {
        RecipeInformationRegistry.replaceKubeJS(previousInformation);
        Language.inject(previousLanguage);
    }

    @Test
    void occupiedIoSpaceTriggersPagesAndTheLastAirConditionRemainsReachable() {
        var display = display("details_trigger", 10, 5);
        var layout = layout(display);
        assertThat(layout.pagesIncludeIngredients()).isFalse();
        assertThat(layout.metadataPages()).hasSizeGreaterThan(1);
        assertThat(layout.details().getLast().y() + layout.details().getLast().height()).isGreaterThan(layout.height());
        assertLastAirTooltipReachable(display, layout);
    }

    @Test
    void gridTallerThanTheCardScrollsWithoutDroppingSlotsOrConditions() {
        var display = display("details_tall_grid", 50, 3);
        var layout = layout(display);
        assertThat(layout.pagesIncludeIngredients()).isTrue();
        assertThat(layout.inputs().slots()).hasSize(50);
        assertThat(layout.outputs().slots()).hasSize(50);
        var gridPage = layout.metadataPages().getFirst();
        assertThat(gridPage.startY()).isZero();
        assertThat(gridPage.endY()).isEqualTo(layout.durationTextY());
        assertThat(layout.inputs().slots()).allSatisfy(slot ->
                assertThat(slot.y() + 18).isLessThan(gridPage.endY()));
        assertThat(layout.outputs().slots()).allSatisfy(slot ->
                assertThat(slot.y() + 18).isLessThan(gridPage.endY()));
        assertThat(gridPage.endY() - gridPage.startY()).isGreaterThan(layout.metadataViewportHeight());
        assertLastAirTooltipReachable(display, layout);
    }

    @Test
    void oversizedTranslatedDescriptionKeepsItsLastWrappedLineAndFollowingEntries() {
        var display = display("details_long_information", 0, 1);
        String description = "x".repeat(2200) + "TAIL_END";
        RecipeInformationRegistry.replaceKubeJS(List.of(
                RecipeInformation.recipe(display.recipeId(), "test.details.description", description),
                RecipeInformation.recipe(display.recipeId(), "test.details.description", "following-entry")));
        var layout = layout(display);
        var information = layout.details().stream()
                .filter(row -> row.kind() == MachineRecipeLayout.DetailKind.INFORMATION).toList();
        assertThat(information).hasSize(2);
        var row = information.getFirst();
        var page = layout.metadataPages().stream().filter(candidate -> row.y() >= candidate.startY()
                && row.y() + row.height() <= candidate.endY()).findFirst().orElseThrow();
        assertThat(page.endY() - page.startY()).isEqualTo(row.height());
        int viewportHeight = layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT - 2;
        var geometry = new RecipeDetailsLayout.Page(0, 1, row.height());
        var viewport = new RecipeDetailsLayout.Viewport(0, 1, layout.width(), viewportHeight,
                geometry.maxScrollY(viewportHeight));
        assertThat(viewport.scrollY()).isPositive();
        assertThat(viewport.contentY(viewport.y() + viewport.height() - 1)).isEqualTo(row.height() - 1);

        var emitted = new StringBuilder();
        var offsets = new ArrayList<Integer>();
        row.wrapped().drawLines((line, offsetY) -> {
            emitted.append(text(line));
            offsets.add(offsetY);
            assertThat(splitter.stringWidth(line)).isLessThanOrEqualTo(
                    RecipeDisplayDrawing.metadataTextWidth(row.width(), 0));
        });
        assertThat(emitted.toString()).isEqualTo(description);
        assertThat(offsets.getLast()).isEqualTo(row.height() - MachineRecipeLayout.TEXT_LINE_SPACING);
        assertThat(layout.metadataPages().getLast().endY()).isEqualTo(
                information.getLast().y() + information.getLast().height());
        assertThat(information.getLast().metadata().text().getString()).isEqualTo("following-entry");
        assertLastAirTooltipReachable(display, layout);
    }

    @Test
    void everyRegisteredDescriptionParticipatesInPagesIncludingTheLastEntry() {
        var display = display("details_many_information", 10, 2);
        var entries = new ArrayList<RecipeInformation>();
        for (int index = 0; index < 30; index++) entries.add(RecipeInformation.recipe(display.recipeId(),
                "test.details.description", "description-" + index));
        RecipeInformationRegistry.replaceKubeJS(entries);
        var layout = layout(display);
        var information = layout.details().stream()
                .filter(row -> row.kind() == MachineRecipeLayout.DetailKind.INFORMATION).toList();
        assertThat(information).hasSize(entries.size());
        for (var row : information) assertThat(layout.metadataPages().stream().filter(page ->
                row.y() >= page.startY() && row.y() + row.height() <= page.endY()).count()).isEqualTo(1);
        assertThat(information.getLast().metadata().text().getString()).isEqualTo("description-29");
        assertThat(layout.metadataPages().getLast().endY()).isEqualTo(
                information.getLast().y() + information.getLast().height());
        assertLastAirTooltipReachable(display, layout);
    }

    @Test
    void oversizedWrappedConditionsRetainTheirLastLineAndTooltipAfterScrolling() throws Exception {
        translations.put("jei.mmcr.machine_recipe.air_in", "x".repeat(500) + " %s %s CONDITION_END");
        injectLanguage();
        var display = display("details_long_conditions", 10, 2);
        var layout = layout(display);
        int capacity = layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT - 2;
        var conditions = layout.details().stream().filter(row -> row.kind() == MachineRecipeLayout.DetailKind.AIR).toList();
        for (var row : conditions) {
            assertThat(row.height()).isGreaterThan(capacity);
            var emitted = new StringBuilder();
            row.wrapped().drawLines((line, y) -> emitted.append(text(line)));
            assertThat(emitted.toString()).endsWith("CONDITION_END");
            assertThat(layout.metadataPages().stream().filter(page -> row.y() >= page.startY()
                    && row.y() + row.height() <= page.endY()).count()).isEqualTo(1);
        }
        assertLastAirTooltipReachable(display, layout);
    }

    private void injectLanguage() throws Exception {
        var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
        constructor.setAccessible(true);
        Language.inject(constructor.newInstance(translations, false));
    }

    private MachineRecipeLayout layout(MachineRecipeDisplay display) {
        return MachineRecipeLayout.forDisplay(display, 4, (text, width) ->
                Language.getInstance().getVisualOrder(splitter.splitLines(text, width, Style.EMPTY)));
    }

    private static String text(FormattedCharSequence line) {
        var text = new StringBuilder();
        line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        return text.toString();
    }

    private static void assertLastAirTooltipReachable(MachineRecipeDisplay display, MachineRecipeLayout layout) {
        var widget = new MachineRecipeMetadataWidget(CombinedAirManaRecipeLayoutTest.category(), display, layout, List.of());
        var lines = new ArrayList<Component>();
        var tooltip = CombinedAirManaRecipeLayoutTest.tooltip(lines);
        var lastAir = layout.details().stream().filter(row -> row.kind() == MachineRecipeLayout.DetailKind.AIR)
                .reduce((first, second) -> second).orElseThrow();
        int capacity = layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT - 2;
        for (var page : layout.metadataPages()) {
            int contentHeight = page.endY() - page.startY();
            int scrollY = Math.max(0, contentHeight - capacity);
            for (int offset = 0; offset < scrollY; offset += MachineRecipeLayout.TEXT_LINE_SPACING) widget.mouseScrolled(20, 5, 0, -1);
            if (lastAir.y() >= page.startY() && lastAir.y() + lastAir.height() <= page.endY()) {
                double y = lastAir.y() + lastAir.height() - page.startY() - scrollY;
                widget.getTooltip(tooltip, lastAir.x(), y);
            }
            widget.getTooltip(tooltip, 20, layout.metadataViewportHeight() - 1);
            widget.mouseScrolled(20, 5, 0, -1);
        }
        assertThat(lines).containsExactlyElementsOf(display.airInputs().getLast().tooltip(true));
    }

    private static MachineRecipeDisplay display(String id, int ioCount, int airRows) {
        var requirements = new ArrayList<MachineRequirement>();
        for (int index = 0; index < ioCount; index++) {
            requirements.add(new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
            requirements.add(new ItemRequirement(IOType.OUTPUT, null, 1, new ItemStack(Items.GOLD_INGOT)));
        }
        for (int index = 0; index < airRows; index++) requirements.add(AirRequirement.input(100 + index, 4F, List.of()));
        return MachineRecipeDisplay.from(new MachineRecipe(MMCR.id(id), MMCR.id("test_cube"), 20,
                requirements, List.of(), List.of(), 0, 1, false, false, false, Set.of()));
    }
}
