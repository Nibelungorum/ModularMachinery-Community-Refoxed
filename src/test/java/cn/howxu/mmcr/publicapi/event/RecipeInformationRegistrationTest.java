package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.RecipeInformation.Target;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import cn.howxu.mmcr.api.recipe.RecipeInformationRegistration;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import net.neoforged.bus.api.Event;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class RecipeInformationRegistrationTest {

    @Test
    void event_preserves_append_order_duplicates_and_translation_arguments() {
        var event = new RecipeInformationRegistration();
        var unknownPool = MMCR.id("unknown_information_pool");
        Object[] arguments = {"temperature", 3200};

        event.registerRecipePool(unknownPool, "jei.example.pool", arguments);
        event.registerRecipePool(unknownPool, "jei.example.pool", arguments);
        event.registerRecipe(MMCR.id("unknown_information_recipe"), "jei.example.recipe");

        assertThat(event).isNotInstanceOf(Event.class);
        assertThat(event.entries()).hasSize(3);
        assertThat(event.entries().get(0).target()).isEqualTo(Target.RECIPE_POOL);
        assertThat(event.entries().get(0).arguments()).containsExactly(arguments);
        assertThat(event.entries().get(1)).isEqualTo(event.entries().get(0));
        assertThat(event.entries().get(2).target()).isEqualTo(Target.RECIPE);
    }

    @Test
    void event_rejects_invalid_values_and_writes_after_freeze() {
        var event = new RecipeInformationRegistration();

        assertThatThrownBy(() -> event.registerRecipePool(null, "jei.example.pool"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("targetId");
        assertThatThrownBy(() -> event.registerRecipe(MMCR.id("recipe"), " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("translationKey");
        assertThatThrownBy(() -> event.registerRecipe(MMCR.id("recipe"), "jei.example.recipe", (Object[]) null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("arguments");

        event.freeze();
        assertThatThrownBy(() -> event.registerRecipe(MMCR.id("later"), "jei.example.later"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("frozen");
    }

    @Test
    void neutral_api_signatures_do_not_expose_optional_integration_types() {
        assertThat(Stream.of(RecipeInformation.class, RecipeInformationRegistration.class,
                        RecipeInformationRegistry.class)
                .flatMap(RecipeInformationRegistrationTest::signatureTypes)
                .map(Class::getName))
                .noneMatch(name -> name.startsWith("mezz.jei.") || name.startsWith("dev.latvian.mods.kubejs."));
    }

    private static Stream<Class<?>> signatureTypes(Class<?> type) {
        Stream<Class<?>> fields = Arrays.stream(type.getDeclaredFields()).map(field -> field.getType());
        Stream<Class<?>> methods = Arrays.stream(type.getDeclaredMethods())
                .flatMap(method -> Stream.concat(Stream.of(method.getReturnType()), parameterTypes(method)));
        Stream<Class<?>> constructors = Arrays.stream(type.getDeclaredConstructors())
                .flatMap(RecipeInformationRegistrationTest::parameterTypes);
        return Stream.of(Stream.of(type), fields, methods, constructors).flatMap(stream -> stream);
    }

    private static Stream<Class<?>> parameterTypes(Executable executable) {
        return Arrays.stream(executable.getParameterTypes());
    }
}
