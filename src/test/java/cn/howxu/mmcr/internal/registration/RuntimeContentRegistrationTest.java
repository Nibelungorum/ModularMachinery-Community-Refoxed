package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies runtime registration ordering and its data-component recipe hook.
 * @author howxu <dev@howxu.cn>
 */
class RuntimeContentRegistrationTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void restoreStartup() {
        TestBootstrap.restoreMachineDefinitions();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void startup_then_builtin_reload_then_cache_rebuild() {
        RuntimeContentRegistration.registerBuiltins();

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(MachineRegistry.effectiveSnapshot()).isNotEmpty();
        assertThat(MachineRegistry.getAllCompiled()).isNotEmpty();
    }

    @Test
    void register_recipes_runs_startup_hook_when_needed() {
        ContentRegistrationCoordinator.resetForTesting();
        AtomicBoolean definitions = new AtomicBoolean();
        AtomicBoolean structures = new AtomicBoolean();
        AtomicBoolean recipes = new AtomicBoolean();

        RuntimeContentRegistration.registerPublicApiLifecycleForTesting(
                event -> definitions.set(true), event -> structures.set(true), event -> recipes.set(true));
        RuntimeContentRegistration.registerRecipes();

        assertThat(definitions).isTrue();
        assertThat(structures).isTrue();
        assertThat(recipes).isTrue();
        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
    }
}
