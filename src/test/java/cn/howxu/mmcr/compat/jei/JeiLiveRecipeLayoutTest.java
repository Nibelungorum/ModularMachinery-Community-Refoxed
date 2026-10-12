package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import cn.howxu.mmcr.internal.client.RecipeViewerRegistration;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the installed JEI drawable, widget and slots alive across real information notifications.
 * @author howxu <dev@howxu.cn>
 */
class JeiLiveRecipeLayoutTest {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final StringSplitter splitter = new StringSplitter((codePoint, style) -> 3F);
    private List<RecipeInformation> previousInformation;
    private Language previousLanguage;
    private int splits;

    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() throws Exception {
        var field = RecipeInformationRegistry.class.getDeclaredField("kubeJSEntries");
        field.setAccessible(true);
        previousInformation = (List<RecipeInformation>) field.get(null);
        previousLanguage = Language.getInstance();
        RecipeInformationRegistry.replaceKubeJS(List.of());
        JeiRuntimeReloader.clearRuntimeForTesting();
        IRecipeManager manager = proxy(IRecipeManager.class, (object, method, args) -> null);
        JeiRuntimeReloader.setRuntime(proxy(IJeiRuntime.class, (object, method, args) -> {
            assertThat(method.getName()).isEqualTo("getRecipeManager");
            return manager;
        }));
        JeiRuntimeReloader.setClientExecutorForTesting(tasks::addLast);
    }

    @AfterEach void restore() {
        JeiRuntimeReloader.clearRuntimeForTesting();
        RecipeInformationRegistry.replaceKubeJS(previousInformation);
        Language.inject(previousLanguage);
    }

    @Test void existingUnpagedDrawableReachesNewTailReplacesPagesAndReturnsToUnpaged() throws Exception {
        var page = page(0, 0);
        assertThat(page.live().current().metadataPages()).isEmpty();
        assertThat(page.widgets()).containsExactly(page.widget()); // Exists before the first long description.
        var position = page.widget().getPosition();
        assertThat(page.remainingSlots()).isEmpty();

        notify(page, "old-".repeat(550) + "OLD_TAIL");
        assertThat(page.live().current().metadataPages()).hasSizeGreaterThan(1);
        assertTailReachable(page, "OLD_TAIL");

        notify(page, "old-".repeat(550) + "OLD_TAIL", "delete-".repeat(200) + "DELETE_ME");
        assertThat(information(page)).hasSize(2);
        assertTailReachable(page, "DELETE_ME");

        notify(page, "new-".repeat(650) + "NEW_TAIL");
        assertThat(information(page)).singleElement().satisfies(row ->
                assertThat(wrappedText(row)).doesNotContain("OLD_TAIL", "DELETE_ME").endsWith("NEW_TAIL"));
        assertTailReachable(page, "NEW_TAIL");

        notify(page); // Deletion crosses the pagination threshold on the very same page.
        assertThat(page.live().current().metadataPages()).isEmpty();
        assertThat(information(page)).isEmpty();
        assertThat(page.widget().mouseScrolled(20, 35, 0, -1)).isFalse();
        assertThat(page.widget().getPosition()).isEqualTo(position);
        assertThat(page.widgets()).containsExactly(page.widget());
        // The category must not emit a second copy after pages disappear (null graphics is intentional).
        page.category().draw(page.display(), null, null, 20, 35);
        assertThat(page.live().current().details()).filteredOn(row ->
                row.kind() == MachineRecipeLayout.DetailKind.DURATION).hasSize(1);
    }

    @Test void bodySlotsKeepOneOwnerAndNoGhostHoverAcrossPaginationAndDeletion() throws Exception {
        var page = page(16, 17); // 20px remain for details: adding information pages the whole body.
        assertThat(page.live().current().metadataPages()).isEmpty();
        var slot = page.slots().getFirst();
        var original = slot.getRect();
        assertThat(page.drawable().getRecipeSlotUnderMouse(original.getX() + 1, original.getY() + 1)).contains(slot);
        assertThat(page.remainingSlots()).isEmpty(); // Native category drawing no longer owns them.

        notify(page, "x".repeat(1000) + "BODY_TAIL");
        assertThat(page.live().current().pagesIncludeIngredients()).isTrue();
        assertTailReachable(page, "BODY_TAIL");
        assertThat(page.drawable().getRecipeSlotUnderMouse(original.getX() + 1, original.getY() + 1)).isEmpty();
        assertThat(slot.getRect().getY()).isNegative();

        notify(page);
        assertThat(page.live().current().metadataPages()).isEmpty();
        assertThat(page.drawable().getRecipeSlotUnderMouse(original.getX() + 1, original.getY() + 1)).contains(slot);
        assertThat(slot.getRect().getY()).isEqualTo(original.getY());
        assertThat(page.remainingSlots()).isEmpty();
        assertThat(page.widgets()).containsExactly(page.widget());
        assertThat(page.widget().getSlotUnderMouse(original.getX() + 1, original.getY() + 1)).isPresent().get()
                .extracting(result -> result.slot()).isSameAs(slot);
    }

    @Test void steadyDrawingTooltipAndInputChecksReuseMeasurementUntilContextChanges() throws Exception {
        var page = page(0, 0);
        var initial = page.live().current();
        int initialSplits = splits;
        for (int i = 0; i < 20; i++) {
            page.widget().getArea();
            page.widget().getSlotUnderMouse(20, 35);
            page.widget().mouseScrolled(20, 35, 0, -1);
            page.category().draw(page.display(), null, null, 20, 35);
            var tips = new ArrayList<Component>();
            page.category().getTooltip(CombinedAirManaRecipeLayoutTest.tooltip(tips), page.display(), null, 20, 35);
            page.widget().getTooltip(CombinedAirManaRecipeLayoutTest.tooltip(tips), 20, 35);
        }
        assertThat(splits).isEqualTo(initialSplits);
        assertThat(page.live().current()).isSameAs(initial);
        var before = page.context().get();
        page.context().set(new MachineRecipeLayout.Context(2, before.font(), before.language(), before.splitter(), before.textWidth()));
        page.widget().getArea();
        assertThat(page.live().current()).isNotSameAs(initial);
        assertThat(page.widget().getArea().height()).isEqualTo(MachineRecipeLayout.categoryHeight(2));
        var resized = page.live().current();
        before = page.context().get();
        page.context().set(new MachineRecipeLayout.Context(2, new Object(), before.language(), before.splitter(), before.textWidth()));
        page.widget().getArea();
        assertThat(page.live().current()).isNotSameAs(resized);

        notify(page, "test.live.translated");
        assertThat(wrappedText(information(page).getFirst())).isEqualTo("test.live.translated");
        var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
        constructor.setAccessible(true);
        Language.inject(constructor.newInstance(Map.of("test.live.translated", "language-".repeat(400) + "LANGUAGE_TAIL"), false));
        before = page.context().get();
        page.context().set(new MachineRecipeLayout.Context(2, before.font(), Language.getInstance(), before.splitter(), before.textWidth()));
        page.widget().tick();
        assertThat(wrappedText(information(page).getFirst())).endsWith("LANGUAGE_TAIL");
        assertTailReachable(page, "LANGUAGE_TAIL");
    }

    private void notify(Page page, String... descriptions) {
        RecipeInformationRegistry.replaceKubeJS(List.of(descriptions).stream()
                .map(text -> RecipeInformation.recipe(page.display().recipeId(), text)).toList());
        RecipeViewerRegistration.informationChanged();
        JeiRuntimeReloader.informationChanged();
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        page.widget().getArea(); // Refresh the already cached widget, not a replacement forDisplay call.
    }

    private static List<MachineRecipeLayout.DetailRow> information(Page page) {
        return page.live().current().details().stream()
                .filter(row -> row.kind() == MachineRecipeLayout.DetailKind.INFORMATION).toList();
    }

    private static void assertTailReachable(Page page, String tail) throws Exception {
        var layout = page.live().current();
        for (int i = 0; i < layout.metadataPages().size(); i++) {
            assertThat(page.widget().mouseClicked(90, layout.metadataViewportY() + layout.metadataViewportHeight() - 1, 0)).isTrue();
        }
        var lastPage = layout.metadataPages().getLast();
        int capacity = layout.metadataViewportHeight() - MachineRecipeLayout.PAGE_FOOTER_HEIGHT - 2;
        int maxScroll = Math.max(0, lastPage.endY() - lastPage.startY() - capacity);
        for (int offset = 0; offset < maxScroll; offset += MachineRecipeLayout.TEXT_LINE_SPACING) {
            assertThat(page.widget().mouseScrolled(20, layout.metadataViewportY() + 5, 0, -1)).isTrue();
        }
        assertThat(integerField(page.widget(), "pageIndex")).isEqualTo(layout.metadataPages().size() - 1);
        assertThat(integerField(page.widget(), "scrollY")).isEqualTo(maxScroll);
        var row = information(page).getLast();
        assertThat(wrappedText(row)).endsWith(tail);
        int bottom = layout.metadataViewportY() + 1 + row.y() + row.height() - lastPage.startY() - maxScroll;
        assertThat(bottom).isLessThanOrEqualTo(layout.metadataViewportY() + 1 + capacity);
        assertThat(bottom).isGreaterThan(layout.metadataViewportY() + 1);
    }

    private static String wrappedText(MachineRecipeLayout.DetailRow row) {
        var emitted = new StringBuilder();
        row.wrapped().drawLines((line, y) -> line.accept((index, style, codePoint) -> {
            emitted.appendCodePoint(codePoint);
            return true;
        }));
        return emitted.toString();
    }

    @SuppressWarnings("unchecked")
    private Page page(int inputs, int outputs) throws Exception {
        var requirements = new ArrayList<MachineRequirement>();
        for (int i = 0; i < inputs; i++) requirements.add(new ItemRequirement(IOType.INPUT,
                Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
        for (int i = 0; i < outputs; i++) requirements.add(new ItemRequirement(IOType.OUTPUT,
                null, 1, new ItemStack(Items.GOLD_INGOT)));
        var display = MachineRecipeDisplay.from(new MachineRecipe(MMCR.id("live_details"), MMCR.id("test_cube"), 20,
                requirements, List.of(), List.of(), 0, 1, false, false, false, Set.of()));
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(display.recipePoolId()));
        var context = new AtomicReference<>(new MachineRecipeLayout.Context(4, splitter, Language.getInstance(),
                (text, width) -> {
                    splits++;
                    return Language.getInstance().getVisualOrder(splitter.splitLines(text, width, Style.EMPTY));
                }, text -> (int) splitter.stringWidth(text.getString())));
        var live = new MachineRecipeLayout.Live(display, context::get, JeiRuntimeReloader::layoutRevision);
        var category = CombinedAirManaRecipeLayoutTest.category();
        // Only replace the host's Minecraft-dependent context access; register the production widget in real JEI.
        var hostCategory = proxy(IRecipeCategory.class, (object, method, args) -> switch (method.getName()) {
            case "getWidth" -> MachineRecipeLayout.CATEGORY_WIDTH;
            case "getHeight" -> live.current().height();
            case "createRecipeExtras" -> {
                category.addLiveWidget((IRecipeExtrasBuilder) args[0], display, live);
                yield null;
            }
            case "onDisplayedIngredientsUpdate" -> null;
            default -> method.invoke(category, args);
        });
        var slots = new ArrayList<IRecipeSlotDrawable>();
        for (var region : List.of(live.current().inputs(), live.current().outputs())) {
            for (var slot : region.slots()) slots.add(actualSlot(slot.x(), slot.y(), slots.size()));
        }
        var layoutClass = Class.forName("mezz.jei.library.gui.recipes.RecipeLayout");
        var pointClass = Class.forName("mezz.jei.common.util.ImmutablePoint2i");
        Object cycler = Class.forName("mezz.jei.library.gui.ingredients.CycleTicker")
                .getMethod("createWithRandomOffset").invoke(null);
        var drawable = (IRecipeLayoutDrawable<MachineRecipeDisplay>) layoutClass.getConstructors()[0].newInstance(
                hostCategory, List.of(), display, null, 0, null, pointClass.getConstructor(int.class, int.class).newInstance(0, 0),
                slots, slots, cycler, null);
        ((IRecipeExtrasBuilder) drawable).getRecipeSlots(); // Create extras once, as an opened JEI page does.
        var widgetsField = layoutClass.getDeclaredField("allWidgets");
        widgetsField.setAccessible(true);
        var widgets = (List<MachineRecipeMetadataWidget>) widgetsField.get(drawable);
        return new Page(display, category, live, context, drawable, widgets.getFirst(), widgets, slots);
    }

    private static IRecipeSlotDrawable actualSlot(int x, int y, int index) throws Exception {
        var manager = proxy(IIngredientManager.class, (object, method, args) -> { throw new AssertionError(method.getName()); });
        var builderClass = Class.forName("mezz.jei.library.gui.recipes.layout.builder.RecipeSlotBuilder");
        var builder = (IRecipeSlotBuilder) builderClass.getConstructor(IIngredientManager.class, int.class, RecipeIngredientRole.class)
                .newInstance(manager, index, RecipeIngredientRole.RENDER_ONLY);
        builder.setPosition(x, y);
        builder.setSlotName("mmcr_body_" + x + "_" + y);
        Object cycler = Class.forName("mezz.jei.library.gui.ingredients.CycleTicker").getMethod("createWithRandomOffset").invoke(null);
        Object pair = builderClass.getMethod("build", Set.class, Class.forName("mezz.jei.library.gui.ingredients.ICycler"))
                .invoke(builder, Set.of(), cycler);
        return (IRecipeSlotDrawable) pair.getClass().getMethod("second").invoke(pair);
    }

    private static int integerField(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    /** @author howxu <dev@howxu.cn> */
    private record Page(MachineRecipeDisplay display, MachineRecipeCategory category, MachineRecipeLayout.Live live,
                        AtomicReference<MachineRecipeLayout.Context> context, IRecipeLayoutDrawable<MachineRecipeDisplay> drawable,
                        MachineRecipeMetadataWidget widget, List<MachineRecipeMetadataWidget> widgets, List<IRecipeSlotDrawable> slots) {
        List<IRecipeSlotDrawable> remainingSlots() { return ((IRecipeExtrasBuilder) drawable).getRecipeSlots().getSlots(); }
    }
}
