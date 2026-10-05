package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.botania.client.ManaJeiIngredient;
import cn.howxu.mmcr.compat.botania.client.ManaJeiIngredientRenderer;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.runtime.IIngredientManager;
import cn.howxu.mmcr.compat.botania.client.ManaJeiIngredientHelper;
import net.minecraft.client.gui.GuiGraphics;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

import static cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT;
import static cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises independent mana rows, native Source coexistence and the actual JEI slot contract.
 * @author howxu <dev@howxu.cn>
 */
class ManaRecipeLayoutTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void manaOnlyRowsKeepRolesExactAmountsAndFullBarHoverOutsideTheGridAtEveryScale() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            for (var declarations : List.of(List.<MachineRequirement>of(ManaRequirement.input(3_000_000_000L)),
                    List.<MachineRequirement>of(ManaRequirement.output(Long.MAX_VALUE)),
                    List.<MachineRequirement>of(ManaRequirement.output(Long.MAX_VALUE), ManaRequirement.input(3_000_000_000L)))) {
                var display = display(declarations, Set.of());
                for (int scale = 1; scale <= 4; scale++) {
                    var layout = MachineRecipeLayout.forDisplay(display, scale);
                    assertThat(layout.inputs().slots()).isEmpty();
                    assertThat(layout.outputs().slots()).isEmpty();
                    assertThat(layout.sourceTextLines()).isEmpty();
                    assertThat(layout.manaRows()).hasSize(declarations.size());
                    assertThat(layout.manaRows()).extracting(row -> row.entry().role())
                            .containsExactlyElementsOf(declarations.stream().map(requirement -> requirement.io() == INPUT
                                    ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT)
                                    .sorted().toList());
                    assertRowsFit(layout, display, scale);
                    for (var row : layout.manaRows()) {
                        assertThat(row.contains(row.x(), row.y())).isTrue();
                        assertThat(row.contains(row.x() + 121.99, row.y() + 15.99)).isTrue();
                        assertThat(row.contains(row.x() + 122, row.y() + 5)).isFalse();
                        assertThat(row.contains(row.x() + 20, row.y() + 16)).isFalse();
                        assertThat(row.contains(row.x() - 0.01, row.y())).isFalse();
                        var mana = (ManaJeiIngredient) row.entry().ingredient();
                        assertThat(mana.amount()).isEqualTo(mana.input() ? 3_000_000_000L : Long.MAX_VALUE);
                        assertThat(row.entry().transferable()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void mixedSourceAndOverflowGridsReserveManaSpaceWithoutCountingManaAsGridOrHiddenEntries() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            ArsNouveauRecipeTypes.register();
            for (int itemCount : List.of(4, 50)) {
                List<MachineRequirement> declarations = new ArrayList<>();
                declarations.add(SourceRequirement.input(10_000L));
                declarations.add(SourceRequirement.output(20_000L));
                declarations.add(ManaRequirement.output(3_000_000_000L));
                declarations.add(ManaRequirement.input(10_000L));
                for (int index = 0; index < itemCount; index++) {
                    declarations.add(new ItemRequirement(INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
                }
                var display = display(declarations, Set.of());
                for (int scale = 1; scale <= 4; scale++) {
                    var layout = MachineRecipeLayout.forDisplay(display, scale);
                    assertThat(layout.manaRows()).hasSize(2);
                    assertThat(layout.sourceTextLines()).isEmpty();
                    assertThat(layout.inputs().slots().getFirst().entry().displayEntry().typeId()).isEqualTo(ArsSourceIds.SOURCE);
                    assertThat(layout.outputs().slots()).singleElement().satisfies(slot ->
                            assertThat(slot.entry().displayEntry().typeId()).isEqualTo(ArsSourceIds.SOURCE));
                    assertThat(layout.inputs().slots().size() + layout.inputs().hiddenEntries().size()).isEqualTo(itemCount + 1);
                    for (var region : List.of(layout.inputs(), layout.outputs())) {
                        assertThat(region.slots()).allSatisfy(slot ->
                                assertThat(slot.entry().displayEntry().typeId()).isNotEqualTo(BotaniaManaIds.MANA));
                        assertThat(region.hiddenEntries()).allSatisfy(entry ->
                                assertThat(entry.displayEntry().typeId()).isNotEqualTo(BotaniaManaIds.MANA));
                    }
                    assertRowsFit(layout, display, scale);
                }
            }
        }
    }

    @Test
    void allFollowingMetadataMovesWithManaRows() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            TestBootstrap.beginRegistration();
            var type = MMCR.id("mana_layout_level_type");
            var level = MMCR.id("mana_layout_level");
            TestBootstrap.registerType(new LevelType(type, Component.literal("Mana layout level")));
            TestBootstrap.registerLevel(new MachineLevel(level, type, 0,
                    new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                    new ItemStack(Items.IRON_BLOCK), ModifierDefinition.EMPTY));
            List<MachineRequirement> baseline = List.of(
                    LevelRequirement.input(type, level),
                    StageRequirement.input(2), SmartInterfaceRequirement.input("mode", 1F));
            var before = display(baseline, Set.of(MMCR.id("mana_layout_host")));
            List<MachineRequirement> withMana = new ArrayList<>(baseline);
            withMana.add(ManaRequirement.input(10_000L));
            withMana.add(ManaRequirement.output(3_000_000_000L));
            var after = display(withMana, before.requiredHostIds());
            for (int scale = 1; scale <= 4; scale++) {
                var old = MachineRecipeLayout.forDisplay(before, scale);
                var layout = MachineRecipeLayout.forDisplay(after, scale);
                assertThat(layout.durationTextY()).isEqualTo(old.durationTextY() + 36);
                assertThat(layout.hostRequirementTextY()).isEqualTo(old.hostRequirementTextY() + 36);
                assertThat(layout.levelRequirementSlotY(after, 0)).isEqualTo(old.levelRequirementSlotY(before, 0) + 36);
                assertThat(layout.stageRequirementTextY(after)).isEqualTo(old.stageRequirementTextY(before) + 36);
                assertThat(layout.smartInterfaceTextY(after)).isEqualTo(old.smartInterfaceTextY(before) + 36);
                assertThat(layout.informationTextY(after)).isEqualTo(old.informationTextY(before) + 36);
                assertRowsFit(layout, after, scale);
            }
        }
    }

    @Test
    void manaTransferStopsBeforeResolvingOrMovingTheDecorativePoolItem() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            List<Component> errors = new ArrayList<>();
            var error = (IRecipeTransferError) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IRecipeTransferError.class}, (proxy, method, arguments) -> {
                        throw new AssertionError("Transfer need not inspect its error");
                    });
            var helper = (IRecipeTransferHandlerHelper) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IRecipeTransferHandlerHelper.class}, (proxy, method, arguments) -> {
                        assertThat(method.getName()).isEqualTo("createUserErrorWithTooltip");
                        errors.add((Component) arguments[0]);
                        return error;
                    });
            var handler = new MachineRecipeTransferHandler(helper, null, null);
            var result = handler.transferRecipe(null,
                    display(List.of(ManaRequirement.input(3_000_000_000L)), Set.of()), null, null, false, true);
            assertThat(result).isSameAs(error);
            assertThat(errors).containsExactly(Component.translatable("jei.mmcr.transfer.unsupported", BotaniaManaIds.MANA.toString()));
        }
    }

    @Test
    void realSlotRegistrationUsesRoleAndFullRendererWithoutBackgroundOverlayOrItemTransferIcon() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            var layout = MachineRecipeLayout.forDisplay(display(List.of(ManaRequirement.input(10_000),
                    ManaRequirement.output(3_000_000_000L)), Set.of()), 4);
            List<Object[]> slots = new ArrayList<>();
            List<Object[]> ingredients = new ArrayList<>();
            List<ManaJeiIngredientRenderer> renderers = new ArrayList<>();
            IRecipeSlotBuilder slot = (IRecipeSlotBuilder) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IRecipeSlotBuilder.class}, (proxy, method, arguments) -> {
                        switch (method.getName()) {
                            case "setCustomRenderer" -> {
                                assertThat(arguments[0]).isSameAs(ManaJeiIngredient.TYPE);
                                renderers.add((ManaJeiIngredientRenderer) arguments[1]);
                            }
                            case "addIngredient" -> ingredients.add(arguments);
                            case "setSlotName" -> { }
                            default -> throw new AssertionError("Unexpected mana slot customization: " + method.getName());
                        }
                        return proxy;
                    });
            IRecipeLayoutBuilder builder = (IRecipeLayoutBuilder) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IRecipeLayoutBuilder.class}, (proxy, method, arguments) -> {
                        assertThat(method.getName()).isEqualTo("addSlot");
                        slots.add(arguments);
                        return slot;
                    });
            MachineRecipeCategory.addManaRows(builder, layout);
            assertThat(slots).hasSize(2);
            assertThat(ingredients).hasSize(2);
            for (int index = 0; index < slots.size(); index++) {
                var row = layout.manaRows().get(index);
                assertThat(slots.get(index)).containsExactly(row.entry().role(), row.x(), row.y());
                assertThat(ingredients.get(index)).containsExactly(ManaJeiIngredient.TYPE, row.entry().ingredient());
                assertThat(renderers.get(index).getWidth()).isEqualTo(row.width());
                assertThat(renderers.get(index).getHeight()).isEqualTo(row.height());
            }
        }
    }

    @Test
    void crowdedGridAndFullMetadataFitTogetherWithoutDroppingEntries() throws Exception {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            List<MachineRequirement> declarations = fullMetadata();
            for (int index = 0; index < 12; index++) {
                declarations.add(new ItemRequirement(INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
            }
            declarations.add(ManaRequirement.input(3_000_000_000L));
            declarations.add(ManaRequirement.output(Long.MAX_VALUE));
            var display = display(declarations, Set.of(MMCR.id("mana_layout_host")));
            for (int scale = 1; scale <= 4; scale++) {
                var layout = MachineRecipeLayout.forDisplay(display, scale);
                assertThat(layout.metadataPages()).isEmpty();
                assertThat(layout.inputs().slots().size() + layout.inputs().hiddenEntries().size()).isEqualTo(12);
                assertRowsFit(layout, display, scale);
                assertThat(layout.informationTextY(display)).isLessThanOrEqualTo(MachineRecipeLayout.categoryHeight(scale));
                assertThat(layout.stageRequirementTextY(display)).isGreaterThanOrEqualTo(layout.levelRequirementSlotY(display, 0) + 18);
                assertThat(layout.smartInterfaceTextY(display)).isGreaterThanOrEqualTo(layout.stageRequirementTextY(display) + 10);
                assertActualSlotsFit(actualDetailSlots(layout, display), layout, display, scale);
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    void manyTaggedManaAndMetadataUseWholeRowPagesWithRealJeiSlotBoundsAndHover() throws Exception {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            ArsNouveauRecipeTypes.register();
            for (int count : List.of(7, 40)) {
                List<MachineRequirement> declarations = fullMetadata();
                declarations.add(SourceRequirement.input(10_000));
                declarations.add(SourceRequirement.output(20_000));
                for (int index = 0; index < 12; index++) {
                    declarations.add(new ItemRequirement(INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
                }
                for (int index = 0; index < count; index++) {
                    declarations.add(new ManaRequirement(index % 2 == 0 ? INPUT : OUTPUT,
                            3_000_000_000L + index, List.of("pool_" + index)));
                }
                var display = display(declarations, Set.of(MMCR.id("mana_layout_host")));
                for (int scale = 1; scale <= 4; scale++) {
                    var layout = MachineRecipeLayout.forDisplay(display, scale);
                    assertThat(layout.manaRows()).hasSize(count);
                    assertThat(layout.inputs().slots().getFirst().entry().displayEntry().typeId()).isEqualTo(ArsSourceIds.SOURCE);
                    assertThat(layout.inputs().slots().size() + layout.inputs().hiddenEntries().size()).isEqualTo(13);
                    assertThat(layout.outputs().slots()).singleElement().satisfies(slot ->
                            assertThat(slot.entry().displayEntry().typeId()).isEqualTo(ArsSourceIds.SOURCE));
                    var slots = actualDetailSlots(layout, display);
                    assertThat(slots).hasSize(count + 1);
                    if (layout.metadataPages().isEmpty()) {
                        assertRowsFit(layout, display, scale);
                        assertActualSlotsFit(slots, layout, display, scale);
                        continue;
                    }
                    var widget = new MachineRecipeMetadataWidget(null, display, layout, slots);
                    Set<IRecipeSlotDrawable> visited = new HashSet<>();
                    int previousEnd = layout.metadataViewportY();
                    for (var page : layout.metadataPages()) {
                        assertThat(page.startY()).isEqualTo(previousEnd);
                        previousEnd = page.endY();
                        assertThat(page.endY() - page.startY() + 2).isLessThanOrEqualTo(
                                layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT);
                        for (var slot : slots) {
                            var rect = slot.getRect();
                            if (rect.getY() < 0) continue;
                            visited.add(slot);
                            assertThat(rect.getY()).isGreaterThanOrEqualTo(1);
                            assertThat(widget.getPosition().y() + rect.getY() + rect.getHeight())
                                    .isLessThanOrEqualTo(MachineRecipeLayout.categoryHeight(scale));
                            assertThat(rect.getX() + rect.getWidth()).isLessThanOrEqualTo(MachineRecipeLayout.CATEGORY_WIDTH);
                            assertThat(widget.getSlotUnderMouse(rect.getX() + rect.getWidth() - 0.01,
                                    rect.getY() + rect.getHeight() - 0.01)).isPresent().get()
                                    .extracting(result -> result.slot()).isSameAs(slot);
                            assertThat(slot.isMouseOver(rect.getX() + rect.getWidth(), rect.getY())).isFalse();
                            assertThat(slot.isMouseOver(rect.getX(), rect.getY() + rect.getHeight())).isFalse();
                            if (slot.getRole() != RecipeIngredientRole.RENDER_ONLY) {
                                var ingredient = slot.getAllIngredients().findFirst().orElseThrow()
                                        .getIngredient(ManaJeiIngredient.TYPE).orElseThrow();
                                assertThat(ingredient.amount()).isBetween(3_000_000_000L, 3_000_000_000L + count - 1);
                                assertThat(slot.getRole()).isEqualTo(ingredient.input()
                                        ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT);
                                assertThat(new ManaJeiIngredientRenderer().getTooltip(ingredient, TooltipFlag.NORMAL))
                                        .containsExactly(ingredient.tooltip());
                            }
                        }
                        assertThat(widget.getSlotUnderMouse(20, layout.metadataViewportHeight() - 1)).isEmpty();
                        widget.mouseScrolled(20, 20, 0, -1);
                    }
                    assertThat(previousEnd).isEqualTo(layout.informationTextY(display));
                    assertThat(visited).containsExactlyInAnyOrderElementsOf(slots);
                    widget.mouseClicked(10, layout.metadataViewportHeight() - 1, 0);
                    widget.mouseScrolled(20, 20, 0, 1);
                    assertThat(widget.getSlotUnderMouse(20, -0.01)).isEmpty();
                }
            }
        }
    }

    private static List<MachineRequirement> fullMetadata() {
        TestBootstrap.beginRegistration();
        var type = MMCR.id("crowded_mana_level_type");
        var level = MMCR.id("crowded_mana_level");
        TestBootstrap.registerType(new LevelType(type, Component.literal("Coils")));
        TestBootstrap.registerLevel(new MachineLevel(level, type, 0,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                new ItemStack(Items.IRON_BLOCK), ModifierDefinition.EMPTY));
        return new ArrayList<>(List.of(LevelRequirement.input(type, level), StageRequirement.input(2),
                SmartInterfaceRequirement.input("mode", 1F)));
    }

    /** Builds the installed JEI 19.57 implementation, not a proxy slot rectangle. */
    private static List<IRecipeSlotDrawable> actualDetailSlots(MachineRecipeLayout layout, MachineRecipeDisplay display) throws Exception {
        var manager = (IIngredientManager) Proxy.newProxyInstance(ManaRecipeLayoutTest.class.getClassLoader(),
                new Class<?>[]{IIngredientManager.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("getIngredientHelper")) throw new AssertionError(method.getName());
                    if (arguments[0] == ManaJeiIngredient.TYPE) return new ManaJeiIngredientHelper();
                    return Proxy.newProxyInstance(ManaRecipeLayoutTest.class.getClassLoader(), new Class<?>[]{IIngredientHelper.class},
                            (helper, helperMethod, values) -> {
                                if (helperMethod.getName().equals("isValidIngredient")) return true;
                                throw new AssertionError(helperMethod.getName());
                            });
                });
        Class<?> builderClass = Class.forName("mezz.jei.library.gui.recipes.layout.builder.RecipeSlotBuilder");
        Class<?> cyclerClass = Class.forName("mezz.jei.library.gui.ingredients.ICycler");
        Object cycler = Class.forName("mezz.jei.library.gui.ingredients.CycleTicker")
                .getMethod("createWithRandomOffset").invoke(null);
        List<Object> builders = new ArrayList<>();
        var builder = (IRecipeLayoutBuilder) Proxy.newProxyInstance(ManaRecipeLayoutTest.class.getClassLoader(),
                new Class<?>[]{IRecipeLayoutBuilder.class}, (proxy, method, arguments) -> {
                    assertThat(method.getName()).isEqualTo("addSlot");
                    var actual = (IRecipeSlotBuilder) builderClass.getConstructor(IIngredientManager.class, int.class, RecipeIngredientRole.class)
                            .newInstance(manager, builders.size(), arguments[0]);
                    actual.setPosition((int) arguments[1], (int) arguments[2]);
                    builders.add(actual);
                    return Proxy.newProxyInstance(ManaRecipeLayoutTest.class.getClassLoader(), new Class<?>[]{IRecipeSlotBuilder.class},
                            (slot, slotMethod, values) -> {
                                if (slotMethod.getName().equals("setStandardSlotBackground")) {
                                    return actual.setBackground(new IDrawable() {
                                        public int getWidth() { return 18; }
                                        public int getHeight() { return 18; }
                                        public void draw(GuiGraphics graphics, int x, int y) { }
                                    }, -1, -1);
                                }
                                return slotMethod.invoke(actual, values);
                            });
                });
        MachineRecipeCategory.addManaRows(builder, layout);
        MachineRecipeCategory.addLevelRequirementSlots(builder, layout, display, label -> 20);
        List<IRecipeSlotDrawable> slots = new ArrayList<>();
        for (var slot : builders) {
            Object pair = builderClass.getMethod("build", Set.class, cyclerClass).invoke(slot, Set.of(), cycler);
            slots.add((IRecipeSlotDrawable) pair.getClass().getMethod("second").invoke(pair));
        }
        return slots;
    }

    @SuppressWarnings("removal")
    private static void assertActualSlotsFit(List<IRecipeSlotDrawable> slots, MachineRecipeLayout layout,
                                           MachineRecipeDisplay display, int scale) {
        for (var slot : slots) {
            var rect = slot.getRect();
            assertThat(rect.getY() + rect.getHeight()).isLessThanOrEqualTo(MachineRecipeLayout.categoryHeight(scale));
            assertThat(slot.isMouseOver(rect.getX() + rect.getWidth() - 0.01, rect.getY() + rect.getHeight() - 0.01)).isTrue();
            assertThat(slot.isMouseOver(rect.getX() + rect.getWidth(), rect.getY())).isFalse();
        }
        assertThat(layout.lastMetadataTextY(display) + 10).isLessThanOrEqualTo(MachineRecipeLayout.categoryHeight(scale));
    }

    private static void assertRowsFit(MachineRecipeLayout layout, MachineRecipeDisplay display, int scale) {
        int previousBottom = Stream.concat(layout.inputs().slots().stream(), layout.outputs().slots().stream())
                .mapToInt(slot -> slot.y() + 18).max().orElse(26);
        for (var region : List.of(layout.inputs(), layout.outputs())) {
            if (region.overflowSlot() != null) previousBottom = Math.max(previousBottom, region.overflowSlot().y() + 18);
        }
        for (var source : layout.sourceTextLines()) {
            assertThat(source.y()).isGreaterThanOrEqualTo(previousBottom);
            previousBottom = source.y() + 10;
        }
        for (var row : layout.manaRows()) {
            assertThat(row.y()).isGreaterThanOrEqualTo(previousBottom);
            assertThat(row.x() + row.width()).isLessThanOrEqualTo(MachineRecipeLayout.CATEGORY_WIDTH);
            previousBottom = row.y() + row.height();
        }
        assertThat(layout.durationTextY()).isGreaterThanOrEqualTo(previousBottom);
        int categoryHeight = switch (scale) { case 1 -> 300; case 2 -> 280; case 3 -> 220; default -> 150; };
        assertThat(layout.durationTextY() + 10).isLessThanOrEqualTo(categoryHeight);
        assertThat(layout.informationTextY(display)).isGreaterThan(layout.durationTextY());
        assertThat(layout.lastMetadataTextY(display) + 10).isLessThanOrEqualTo(categoryHeight);
        for (int index = 0; index < display.recipe().levelRequirements().size(); index++) {
            assertThat(layout.levelRequirementSlotY(display, index) + 18).isLessThanOrEqualTo(categoryHeight);
        }
    }

    private static MachineRecipeDisplay display(List<MachineRequirement> requirements, Set<ResourceLocation> hosts) {
        return MachineRecipeDisplay.from(new MachineRecipe(MMCR.id("mana_layout"), MMCR.id("test_cube"), 20,
                requirements, List.of(), List.of(), 0, 1, false, false, false, hosts));
    }
}
