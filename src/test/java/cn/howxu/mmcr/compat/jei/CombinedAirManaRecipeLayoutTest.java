package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT;
import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for the combined PneumaticCraft and Botania JEI details flow.
 * @author howxu <dev@howxu.cn>
 */
class CombinedAirManaRecipeLayoutTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void mixed_air_and_mana_reserve_space_without_duplicate_grid_entries_or_lost_metadata() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            PneumaticRecipeTypes.register();
            List<MachineRequirement> declarations = new ArrayList<>(List.of(
                    ManaRequirement.output(Long.MAX_VALUE), ManaRequirement.input(3_000_000_000L),
                    AirRequirement.input(0, 4F), AirRequirement.input(10_000_000_000L, 4.5F, List.of("drive")),
                    AirRequirement.output(Long.MAX_VALUE, List.of("generator")),
                    StageRequirement.input(2), SmartInterfaceRequirement.input("mode", 1F)));
            for (int index = 0; index < 50; index++) {
                declarations.add(new ItemRequirement(INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
            }
            var display = display(declarations);
            var category = category();
            for (int scale = 1; scale <= 5; scale++) {
                var layout = MachineRecipeLayout.forDisplay(display, scale);
                assertThat(layout.metadataPages()).isEmpty();
                assertThat(layout.manaRows()).hasSize(2);
                assertThat(layout.inputs().slots()).hasSize(50);
                assertThat(layout.outputs().slots()).isEmpty();
                assertThat(layout.inputs().slots()).allSatisfy(slot ->
                        assertThat(slot.entry().displayEntry().typeId()).isNotIn(BotaniaManaIds.MANA, PneumaticIds.AIR));
                assertThat(layout.manaRows().getFirst().y()).isGreaterThan(layout.inputs().slots().getLast().y() + 18);
                assertThat(layout.manaRows().getFirst().y()).isEqualTo(layout.airTextY(display) + 30);
                assertThat(layout.manaRows()).allSatisfy(row ->
                        assertThat(row.y()).isGreaterThan(layout.durationTextY()));
                assertThat(layout.hostRequirementTextY()).isEqualTo(
                        layout.manaRows().getLast().y() + layout.manaRows().getLast().height());
                assertThat(layout.stageRequirementTextY(display)).isEqualTo(layout.hostRequirementTextY() + 10);
                assertThat(layout.smartInterfaceTextY(display)).isEqualTo(layout.stageRequirementTextY(display) + 10);
                assertThat(layout.informationTextY(display)).isGreaterThan(layout.smartInterfaceTextY(display));
                var lines = new ArrayList<Component>();
                var tooltip = tooltip(lines);
                int y = layout.airTextY(display);
                for (int index = 0; index < display.airInputs().size() + display.airOutputs().size(); index++) {
                    category.metadataTooltip(tooltip, display, layout, layout.durationTextX(), y);
                    y += MachineRecipeLayout.TEXT_LINE_SPACING;
                }
                assertThat(lines).containsExactlyElementsOf(airTooltips(display));
            }
        }
    }

    @Test
    void paginated_mixed_details_visit_each_air_tooltip_once_with_whole_row_bounds() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            PneumaticRecipeTypes.register();
            List<MachineRequirement> declarations = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                declarations.add(ManaRequirement.input(3_000_000_000L + index));
                declarations.add(AirRequirement.input(10_000_000_000L + index, 4.5F, List.of("drive_" + index)));
                declarations.add(AirRequirement.output(Long.MAX_VALUE - index, List.of("generator_" + index)));
            }
            var display = display(declarations);
            var category = category();
            for (int scale = 1; scale <= 5; scale++) {
                var layout = MachineRecipeLayout.forDisplay(display, scale);
                assertThat(layout.metadataPages()).hasSizeGreaterThan(1);
                assertThat(layout.manaRows()).allSatisfy(row ->
                        assertThat(row.y()).isGreaterThan(layout.durationTextY()));
                assertThat(layout.manaRows()).allSatisfy(row -> assertThat(layout.metadataPages().stream()
                        .filter(page -> row.y() >= page.startY() && row.y() + row.height() <= page.endY()).count())
                        .isEqualTo(1L));
                var widget = new MachineRecipeMetadataWidget(category, display, layout, List.of());
                var lines = new ArrayList<Component>();
                var tooltip = tooltip(lines);
                int previousEnd = layout.metadataViewportY();
                for (var page : layout.metadataPages()) {
                    assertThat(page.startY()).isEqualTo(previousEnd);
                    assertThat(page.endY() - page.startY() + 2).isLessThanOrEqualTo(
                            layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT);
                    previousEnd = page.endY();
                    for (int index = 0; index < 40; index++) {
                        int y = layout.airTextY(display) + index * MachineRecipeLayout.TEXT_LINE_SPACING;
                        if (y >= page.startY() && y < page.endY()) {
                            assertThat(y + MachineRecipeLayout.TEXT_LINE_SPACING).isLessThanOrEqualTo(page.endY());
                            widget.getTooltip(tooltip, layout.durationTextX(), y - page.startY() + 1);
                        }
                    }
                    int beforeOutsideHover = lines.size();
                    widget.getTooltip(tooltip, layout.durationTextX(), 0);
                    widget.getTooltip(tooltip, layout.durationTextX(), layout.metadataViewportHeight() - 1);
                    assertThat(lines).hasSize(beforeOutsideHover);
                    widget.mouseScrolled(20, 20, 0, -1);
                }
                assertThat(previousEnd).isEqualTo(layout.informationTextY(display));
                assertThat(lines).containsExactlyElementsOf(airTooltips(display));
            }
        }
    }

    private static MachineRecipeDisplay display(List<MachineRequirement> declarations) {
        return MachineRecipeDisplay.from(new MachineRecipe(MMCR.id("combined_air_mana_layout"), MMCR.id("test_cube"), 20,
                declarations, List.of(), List.of(), 0, 1, false, false, false, Set.of(MMCR.id("layout_host"))));
    }

    private static List<Component> airTooltips(MachineRecipeDisplay display) {
        List<Component> lines = new ArrayList<>();
        display.airInputs().forEach(air -> lines.addAll(air.tooltip(true)));
        display.airOutputs().forEach(air -> lines.addAll(air.tooltip(false)));
        return lines;
    }

    static ITooltipBuilder tooltip(List<Component> lines) {
        return (ITooltipBuilder) Proxy.newProxyInstance(CombinedAirManaRecipeLayoutTest.class.getClassLoader(),
                new Class<?>[]{ITooltipBuilder.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("addAll")) {
                        for (Object line : (List<?>) arguments[0]) lines.add((Component) line);
                    } else if (method.getName().equals("add")) {
                        lines.add((Component) arguments[0]);
                    }
                    return method.getReturnType().isInstance(proxy) ? proxy : null;
                });
    }

    static MachineRecipeCategory category() {
        IDrawableStatic drawable = (IDrawableStatic) Proxy.newProxyInstance(CombinedAirManaRecipeLayoutTest.class.getClassLoader(),
                new Class<?>[]{IDrawableStatic.class}, (proxy, method, arguments) ->
                        method.getReturnType() == int.class ? 16 : null);
        var helper = (IGuiHelper) Proxy.newProxyInstance(CombinedAirManaRecipeLayoutTest.class.getClassLoader(),
                new Class<?>[]{IGuiHelper.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("createDrawableItemLike") || method.getName().equals("getSlotDrawable")) {
                        return drawable;
                    }
                    if (method.getName().equals("drawableBuilder")) {
                        return Proxy.newProxyInstance(CombinedAirManaRecipeLayoutTest.class.getClassLoader(),
                                new Class<?>[]{method.getReturnType()}, (builder, builderMethod, builderArguments) ->
                                        builderMethod.getName().equals("build") ? drawable : builder);
                    }
                    return null;
                });
        return new MachineRecipeCategory(helper, MMCR.id("combined_air_mana_layout"), MMCR.id("test_cube"));
    }
}
