package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PatternBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.PublicMachineDefinitionProviders;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the shared public startup registration lifecycle.
 * @author howxu <dev@howxu.cn>
 */
class PublicApiLifecycleTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void reset() {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @AfterEach
    void cleanup() throws Exception {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void registration_before_begin_is_rejected() {
        assertThatThrownBy(() -> MachineApi.registerMachine(machine("before")))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("before")
                .hasMessageContaining("before begin");
    }

    @Test
    void open_phase_installs_machine_before_recipe_and_freeze_is_idempotent() {
        PublicApiBootstrap.begin();
        MachineDefinition machine = machine("press");
        MachineRecipeDefinition recipe = recipe("press_recipe", machine.id());

        MachineApi.registerMachine(machine);
        RecipeApi.registerRecipe(recipe);
        PublicApiBootstrap.freezeAndInstall();
        PublicApiBootstrap.freezeAndInstall();

        assertThat(MachineDefinitions.getRegistration(machine.id())).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipe.id())).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipe.id()).machineId()).isEqualTo(machine.id());
        assertThat(MachineApi.isRegistrationOpen()).isFalse();
        assertThat(RecipeApi.isRegistrationOpen()).isFalse();
    }

    @Test
    void service_loaded_providers_register_before_finalization() {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();

        PublicMachineDefinitionProviders.registerAll();
        PublicApiBootstrap.freezeAndInstall();

        assertThat(MachineDefinitions.getRegistration(id("service_loaded_machine"))).isNotNull();
        assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();
    }

    @Test
    void duplicate_machine_and_recipe_ids_are_rejected() {
        PublicApiBootstrap.begin();
        MachineDefinition machine = machine("duplicate_machine");
        MachineApi.registerMachine(machine);
        assertThatThrownBy(() -> MachineApi.registerMachine(machine))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(machine.id().toString());

        MachineRecipeDefinition recipe = recipe("duplicate_recipe", machine.id());
        RecipeApi.registerRecipe(recipe);
        assertThatThrownBy(() -> RecipeApi.registerRecipe(recipe))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(recipe.id().toString());
    }

    @Test
    void unknown_recipe_machine_and_after_freeze_registration_are_rejected() {
        PublicApiBootstrap.begin();
        Identifier unknown = id("unknown_machine");
        RecipeApi.registerRecipe(recipe("unknown_recipe", unknown));
        assertThatThrownBy(PublicApiBootstrap::freezeAndInstall)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(unknown.toString());
        assertThat(MachineApi.isRegistrationOpen()).isTrue();
        MachineApi.registerMachine(machine("after_validation_failure"));

        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.begin();
        PublicApiBootstrap.freezeAndInstall();
        assertThatThrownBy(() -> MachineApi.registerMachine(machine("after")))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("after");
        assertThatThrownBy(() -> RecipeApi.registerRecipe(recipe("after_recipe", unknown)))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("after_recipe");
    }

    @Test
    void public_api_classes_do_not_embed_internal_bootstrap_dependency() throws IOException {
        for (Class<?> apiClass : new Class<?>[]{MachineApi.class, RecipeApi.class}) {
            String bytecode = new String(apiClass.getResourceAsStream(apiClass.getSimpleName() + ".class").readAllBytes());
            assertThat(bytecode).doesNotContain("cn/howxu/mmcr/internal/api/PublicApiBootstrap");
        }
    }

    private static MachineDefinition machine(String path) {
        return MachineBuilder.machine(id(path)).pattern(PublicApiLifecycleTest::pattern).build();
    }

    private static cn.howxu.mmcr.api.publicapi.machine.PatternBuilder pattern(
            cn.howxu.mmcr.api.publicapi.machine.PatternBuilder builder) {
        return builder.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F');
    }

    private static MachineRecipeDefinition recipe(String path, Identifier machineId) {
        return MachineRecipeBuilder.recipe(id(path), machineId).duration(1).build();
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }
}
