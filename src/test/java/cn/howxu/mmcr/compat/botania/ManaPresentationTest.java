package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.botania.client.ManaJeiAdapter;
import cn.howxu.mmcr.compat.botania.client.ManaJeiIngredient;
import cn.howxu.mmcr.compat.botania.client.ManaJeiIngredientHelper;
import cn.howxu.mmcr.compat.botania.client.ManaJeiIngredientRenderer;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapterRegistry;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.ReadableNumber;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Codec;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
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
    void adapterAndHelperRetainExactLongIdentityDirectionAndNonTransferableSemantics() {
        var adapter = new ManaJeiAdapter();
        var helper = new ManaJeiIngredientHelper();
        var renderer = new ManaJeiIngredientRenderer();
        assertThat(adapter.transferHandler()).isEmpty();
        for (long amount : List.of(1L, 3_000_000_000L, Long.MAX_VALUE)) {
            for (boolean input : List.of(true, false)) {
                var requirement = input ? ManaRequirement.input(amount) : ManaRequirement.output(amount);
                var role = input ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
                var entry = adapter.display(new RecipeIoEntry(role, BotaniaManaIds.MANA, requirement, amount, 1F)).orElseThrow();
                var ingredient = (ManaJeiIngredient) entry.ingredient();
                assertThat(ingredient).isEqualTo(new ManaJeiIngredient(amount, input));
                assertThat(entry.ingredientType()).isSameAs(ManaJeiIngredient.TYPE);
                assertThat(entry.role()).isEqualTo(role);
                assertThat(entry.transferable()).isFalse();
                assertThat(entry.count()).isEqualTo((int) Math.min(amount, Integer.MAX_VALUE));
                assertThat(helper.getAmount(ingredient)).isEqualTo(amount);
                assertThat(helper.getUid(ingredient, UidContext.Ingredient)).isEqualTo(BotaniaManaIds.MANA);
                assertThat(helper.getResourceLocation(ingredient)).isEqualTo(BotaniaManaIds.MANA);
                assertThat(helper.copyIngredient(ingredient)).isSameAs(ingredient);
                assertThat(helper.copyWithAmount(ingredient, 7)).isEqualTo(new ManaJeiIngredient(7, input));
                assertThat(helper.normalizeIngredient(ingredient)).isEqualTo(new ManaJeiIngredient(1, input));
                assertThat(ingredient.tooltip()).isEqualTo(Component.translatable(input
                        ? "jei.mmcr.machine_recipe.mana_input" : "jei.mmcr.machine_recipe.mana_output",
                        ReadableNumber.formatExact(amount)));
                assertThat(renderer.getTooltip(ingredient, TooltipFlag.NORMAL)).containsExactly(ingredient.tooltip());
                var encoded = ManaJeiIngredient.CODEC.encodeStart(JsonOps.INSTANCE, ingredient).getOrThrow();
                assertThat(encoded.getAsJsonObject().get("amount").getAsLong()).isEqualTo(amount);
                assertThat(ManaJeiIngredient.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()).isEqualTo(ingredient);
            }
        }
    }

    @Test
    void rejectsNonpositiveAmountsInBothDirectAndSerializedDeclarations() {
        for (long invalid : List.of(0L, -1L, Long.MIN_VALUE)) {
            assertThatThrownBy(() -> new ManaJeiIngredient(invalid, true)).isInstanceOf(IllegalArgumentException.class);
            assertThat(ManaJeiIngredient.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"amount\":" + invalid + "}")).error()).isPresent();
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
                    new ManaJeiIngredient(3_000_000_000L, true), new ManaJeiIngredient(Long.MAX_VALUE, false));
        }
    }

    @Test
    void bothLanguagesGiveDirectionAwareTooltipsWithUnclampedExactTotals() throws Exception {
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
                    var ingredient = new ManaJeiIngredient(3_000_000_000L, input);
                    String key = input ? "jei.mmcr.machine_recipe.mana_input" : "jei.mmcr.machine_recipe.mana_output";
                    assertThat(ingredient.tooltip().getString()).contains("3,000,000,000")
                            .isEqualTo(translations.get(key).replace("%s", "3,000,000,000"));
                }
            }
        } finally {
            Language.inject(previous);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void ingredientHelperAndRendererInitializeWithNativeBotaniaActivelyForbidden() throws Exception {
        var loader = new NeutralIngredientLoader(getClass().getClassLoader());
        Class<?> ingredientClass = loader.loadClass(ManaJeiIngredient.class.getName());
        Object ingredient = ingredientClass.getConstructor(long.class, boolean.class).newInstance(3_000_000_000L, true);
        assertThat(ingredientClass.getField("TYPE").get(null)).isNotNull();
        Codec codec = (Codec) ingredientClass.getField("CODEC").get(null);
        Object encoded = codec.encodeStart(JsonOps.INSTANCE, ingredient).getOrThrow();
        assertThat(ingredientClass.getMethod("amount").invoke(codec.parse(JsonOps.INSTANCE, encoded).getOrThrow()))
                .isEqualTo(3_000_000_000L);
        Class<?> helperClass = loader.loadClass(ManaJeiIngredientHelper.class.getName());
        Object helper = helperClass.getConstructor().newInstance();
        assertThat(helperClass.getMethod("getAmount", ingredientClass).invoke(helper, ingredient)).isEqualTo(3_000_000_000L);
        assertThat(helperClass.getMethod("normalizeIngredient", ingredientClass).invoke(helper, ingredient)).isNotNull();
        Class<?> rendererClass = loader.loadClass(ManaJeiIngredientRenderer.class.getName());
        Object renderer = rendererClass.getConstructor().newInstance();
        assertThat(rendererClass.getMethod("getWidth").invoke(renderer)).isEqualTo(122);
        assertThat(rendererClass.getMethod("getHeight").invoke(renderer)).isEqualTo(16);
        assertThat(rendererClass.getMethod("getTooltip", ingredientClass, TooltipFlag.class)
                .invoke(renderer, ingredient, TooltipFlag.NORMAL)).isInstanceOf(List.class);
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
