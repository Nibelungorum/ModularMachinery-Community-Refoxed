package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import cn.howxu.mmcr.api.recipe.RecipeInformation.Target;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import cn.howxu.mmcr.internal.client.RecipeViewerRegistration;
import cn.howxu.mmcr.internal.sync.RecipeViewerBackend;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class RecipeInformationEventJSTest {

    @AfterEach
    void clearKubeJSSnapshot() {
        RecipeInformationRegistry.replaceKubeJS(List.of());
    }

    @Test
    void available_event_collects_pool_recipe_and_translation_arguments() {
        var event = new RecipeInformationEventJS(true);
        Object[] arguments = {Component.literal("temperature"), 3200};

        event.addRecipePoolInfo("mmcr:test_pool", "jei.example.pool", arguments);
        event.addRecipeInfo("mmcr:test_recipe", "jei.example.recipe", arguments);

        assertThat(event.entries()).hasSize(2);
        assertThat(event.entries().get(0).target()).isEqualTo(Target.RECIPE_POOL);
        assertThat(event.entries().get(1).target()).isEqualTo(Target.RECIPE);
        assertThat(event.entries()).allSatisfy(entry -> assertThat(entry.arguments()).containsExactly(arguments));
    }

    @Test
    void available_event_rejects_invalid_identifiers_keys_and_arguments() {
        var event = new RecipeInformationEventJS(true);

        assertThatThrownBy(() -> event.addRecipePoolInfo(" ", "jei.example.pool"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("poolId");
        assertThatThrownBy(() -> event.addRecipeInfo("invalid id", "jei.example.recipe"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recipeId");
        assertThatThrownBy(() -> event.addRecipeInfo("mmcr:test_recipe", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("translationKey");
        assertThatThrownBy(() -> event.addRecipeInfo("mmcr:test_recipe", "jei.example.recipe", (Object[]) null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("arguments");
    }

    @Test
    void missing_viewers_warn_once_and_ignore_all_registrations() {
        List<String> warnings = new ArrayList<>();
        var event = new RecipeInformationEventJS(false, warnings::add);

        event.addRecipePoolInfo("not even parsed", " ");
        event.addRecipeInfo(null, null, (Object[]) null);

        assertThat(event.entries()).isEmpty();
        assertThat(warnings).singleElement().asString().contains("Neither JEI nor EMI is loaded");
    }

    @Test
    void successful_client_reload_replaces_previous_snapshot() {
        var manager = new Object();
        long previousVersion = RecipeViewerRegistration.informationVersion();
        Plugin.beginClientReload(manager, 0);

        Plugin.completeClientReloadForTesting(manager, true,
                event -> event.addRecipeInfo("mmcr:new_recipe", "jei.example.new"), () -> 0);

        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("new_recipe")))
                .containsExactly(Component.translatable("jei.example.new"));
        assertThat(RecipeViewerRegistration.informationVersion()).isEqualTo(previousVersion + 1);
    }

    @Test
    void successful_empty_reload_clears_previous_snapshot() {
        RecipeInformationRegistry.replaceKubeJS(List.of(
                RecipeInformation.recipe(MMCR.id("old_recipe"), "jei.example.old")));
        var manager = new Object();
        Plugin.beginClientReload(manager, 0);

        Plugin.completeClientReloadForTesting(manager, true, ignored -> { }, () -> 0);

        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("old_recipe"))).isEmpty();
    }

    @Test
    void failed_client_reload_retains_previous_snapshot() {
        var manager = new Object();
        Plugin.beginClientReload(manager, 0);
        Plugin.completeClientReloadForTesting(manager, true,
                event -> event.addRecipeInfo("mmcr:old_recipe", "jei.example.old"), () -> 0);
        long previousVersion = RecipeViewerRegistration.informationVersion();
        AtomicInteger errors = new AtomicInteger();
        Plugin.beginClientReload(manager, errors.get());

        Plugin.completeClientReloadForTesting(manager, true, event -> {
            event.addRecipeInfo("mmcr:new_recipe", "jei.example.new");
            errors.incrementAndGet();
        }, errors::get);

        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("old_recipe")))
                .containsExactly(Component.translatable("jei.example.old"));
        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("new_recipe"))).isEmpty();
        assertThat(RecipeViewerRegistration.informationVersion()).isEqualTo(previousVersion);
    }

    @Test
    void emi_only_backend_enters_available_client_reload_branch() {
        var manager = new Object();
        Plugin.beginClientReload(manager, 0);
        Plugin.completeClientReloadForTesting(manager,
                RecipeViewerBackend.select(false, true) != RecipeViewerBackend.NONE,
                event -> event.addRecipeInfo("mmcr:emi_recipe", "jei.example.emi"), () -> 0);

        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("emi_recipe")))
                .containsExactly(Component.translatable("jei.example.emi"));
    }

    @Test
    void no_viewer_preserves_previous_information_and_version() {
        var manager = new Object();
        RecipeInformationRegistry.replaceKubeJS(List.of(RecipeInformation.recipe(MMCR.id("old_recipe"), "jei.example.old")));
        long previousVersion = RecipeViewerRegistration.informationVersion();
        Plugin.beginClientReload(manager, 0);
        Plugin.completeClientReloadForTesting(manager,
                RecipeViewerBackend.select(false, false) != RecipeViewerBackend.NONE,
                event -> event.addRecipeInfo("mmcr:new_recipe", "jei.example.new"), () -> 0);

        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("old_recipe")))
                .containsExactly(Component.translatable("jei.example.old"));
        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("new_recipe"))).isEmpty();
        assertThat(RecipeViewerRegistration.informationVersion()).isEqualTo(previousVersion);
    }

    @Test
    void throwing_publisher_preserves_previous_information_and_version() {
        var manager = new Object();
        RecipeInformationRegistry.replaceKubeJS(List.of(RecipeInformation.recipe(MMCR.id("old_recipe"), "jei.example.old")));
        long previousVersion = RecipeViewerRegistration.informationVersion();
        Plugin.beginClientReload(manager, 0);

        assertThatThrownBy(() -> Plugin.completeClientReloadForTesting(manager, true, event -> {
            event.addRecipeInfo("mmcr:new_recipe", "jei.example.new");
            throw new IllegalStateException("publisher failure");
        }, () -> 0)).isInstanceOf(IllegalStateException.class);
        assertThat(RecipeInformationRegistry.componentsFor(MMCR.id("pool"), MMCR.id("old_recipe")))
                .containsExactly(Component.translatable("jei.example.old"));
        assertThat(RecipeViewerRegistration.informationVersion()).isEqualTo(previousVersion);
    }
}
