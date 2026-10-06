package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.botania.client.ManaJeiAdapter;
import cn.howxu.mmcr.compat.botania.client.ManaJeiDisplay;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapterRegistry;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.ReadableNumber;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Neutral JEI contracts work without a Botania world or native client initialization.
 * @author howxu <dev@howxu.cn>
 */
class ManaPresentationTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void adapterRetainsExactLongMetadataAndDirectionWithoutJeiIngredientsOrTransfer() {
        var adapter = new ManaJeiAdapter();
        assertThat(adapter.transferHandler()).isEmpty();
        assertThat(adapter.ingredientType()).isNull();
        for (long amount : List.of(1L, 3_000_000_000L, Long.MAX_VALUE)) {
            for (boolean input : List.of(true, false)) {
                var requirement = input ? ManaRequirement.input(amount) : ManaRequirement.output(amount);
                var role = input ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
                var entry = adapter.display(new RecipeIoEntry(role, BotaniaManaIds.MANA, requirement, amount, 1F)).orElseThrow();
                var mana = (ManaJeiDisplay) entry.ingredient();
                assertThat(mana).isEqualTo(new ManaJeiDisplay(amount, input));
                assertThat(entry.ingredientType()).isNull();
                assertThat(entry.isTextOnly()).isTrue();
                assertThat(entry.renderer()).isNull();
                assertThat(entry.role()).isEqualTo(role);
                assertThat(entry.transferable()).isFalse();
                assertThat(entry.count()).isEqualTo((int) Math.min(amount, Integer.MAX_VALUE));
                assertThat(mana.label()).isEqualTo(Component.translatable(input
                        ? "jei.mmcr.machine_recipe.mana_input" : "jei.mmcr.machine_recipe.mana_output",
                        ReadableNumber.format(amount)));
            }
        }
    }

    @Test
    void rejectsNonpositiveMetadataAmounts() {
        for (long invalid : List.of(0L, -1L, Long.MIN_VALUE)) {
            assertThatThrownBy(() -> new ManaJeiDisplay(invalid, true)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void machineDisplayUsesRealRequirementAndOutputTotalsThroughRegisteredAdapter() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            BotaniaRecipeTypes.register();
            var recipe = new MachineRecipe(MMCR.id("mana_presentation"), MMCR.id("test_cube"), 20,
                    List.of(ManaRequirement.input(3_000_000_000L)), List.of(new ManaOutput(Long.MAX_VALUE)),
                    List.of(), 0, 1, false, false, false, Set.of());
            assertThat(JeiIngredientAdapterRegistry.get(BotaniaManaIds.MANA).orElseThrow()).isInstanceOf(ManaJeiAdapter.class);
            var entries = MachineRecipeDisplay.from(recipe).entries();
            assertThat(entries).extracting(entry -> entry.ingredient()).containsExactly(
                    new ManaJeiDisplay(3_000_000_000L, true), new ManaJeiDisplay(Long.MAX_VALUE, false));
        }
    }

    @Test
    void bothLanguagesGiveDirectionAwareLabelsWithoutPoolCapacityClamping() throws Exception {
        Language previous = Language.getInstance();
        try {
            for (String language : List.of("en_us", "zh_cn")) {
                Map<String, String> translations = new HashMap<>();
                try (var stream = getClass().getResourceAsStream("/assets/mmcr/lang/" + language + ".json")) {
                    Language.loadFromJson(stream, translations::put);
                }
                var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
                constructor.setAccessible(true);
                Language.inject(constructor.newInstance(translations, false));
                Component poolTitle = Component.translatable(ResourceLocation.parse("mmcr_example:mana_machine")
                        .toLanguageKey("recipe_pool"));
                assertThat(poolTitle.getString()).isEqualTo(language.equals("en_us")
                        ? "Mana Recycling Machine" : "魔力回收机");
                assertThat(Component.translatable("jei.mmcr.machine_recipe.details_page", 1, 2).getString())
                        .isEqualTo(language.equals("en_us") ? "< Details 1/2 >" : "< 详情 1/2 >");
                for (boolean input : List.of(true, false)) {
                    var ingredient = new ManaJeiDisplay(3_000_000_000L, input);
                    String key = input ? "jei.mmcr.machine_recipe.mana_input" : "jei.mmcr.machine_recipe.mana_output";
                    assertThat(ingredient.label().getString())
                            .isEqualTo(translations.get(key).replace("%s", ReadableNumber.format(ingredient.amount())))
                            .doesNotContain("3,000,000,000");
                }
            }
        } finally {
            Language.inject(previous);
        }
    }

    @Test
    void metadataAndAdapterInitializeWithNativeBotaniaActivelyForbidden() throws Exception {
        var loader = new NeutralIngredientLoader(getClass().getClassLoader());
        Class<?> ingredientClass = loader.loadClass(ManaJeiDisplay.class.getName());
        Object ingredient = ingredientClass.getConstructor(long.class, boolean.class).newInstance(3_000_000_000L, true);
        assertThat(ingredientClass.getMethod("amount").invoke(ingredient))
                .isEqualTo(3_000_000_000L);
        assertThat(ingredientClass.getMethod("label").invoke(ingredient)).isInstanceOf(Component.class);
        Class<?> adapterClass = loader.loadClass(ManaJeiAdapter.class.getName());
        Object adapter = adapterClass.getConstructor().newInstance();
        assertThat(adapterClass.getMethod("ingredientType").invoke(adapter)).isNull();
        assertThat(loader.forbiddenLoads).isEmpty();
    }

    /** Child-first ingredient loading detects accidental native references in outer client declarations.
     * @author howxu <dev@howxu.cn>
     */
    private static final class NeutralIngredientLoader extends ClassLoader {
        private final List<String> forbiddenLoads = new ArrayList<>();

        private NeutralIngredientLoader(ClassLoader parent) { super(parent); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("vazkii.botania.")) {
                    forbiddenLoads.add(name);
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith("cn.howxu.mmcr.compat.botania.client.ManaJei")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream source = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (source == null) throw new ClassNotFoundException(name);
                        byte[] bytes = source.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
