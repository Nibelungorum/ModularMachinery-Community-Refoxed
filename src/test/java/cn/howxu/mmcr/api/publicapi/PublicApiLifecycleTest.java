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
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    void machine_installation_precedes_recipe_installation_and_is_idempotent() {
        PublicApiBootstrap.begin();
        MachineDefinition machine = machine("press");
        MachineRecipeDefinition recipe = recipe("press_recipe", machine.id());

        MachineApi.registerMachine(machine);
        RecipeApi.registerRecipe(recipe);
        PublicApiBootstrap.freezeAndInstallMachines();
        PublicApiBootstrap.freezeAndInstallMachines();

        assertThat(MachineDefinitions.getRegistration(machine.id())).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipe.id())).isNull();

        PublicApiBootstrap.installRecipes();
        PublicApiBootstrap.installRecipes();

        assertThat(RecipeRegistry.getRecipe(recipe.id())).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipe.id()).machineId()).isEqualTo(machine.id());
        assertThat(MachineApi.isRegistrationOpen()).isFalse();
        assertThat(RecipeApi.isRegistrationOpen()).isFalse();
    }

    @Test
    void recipe_installation_requires_machine_installation() {
        PublicApiBootstrap.begin();

        assertThatThrownBy(PublicApiBootstrap::installRecipes)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("machines must be installed first");
    }

    @Test
    void service_loaded_providers_register_before_finalization() {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();

        PublicMachineDefinitionProviders.registerAll();
        PublicApiBootstrap.freezeAndInstallMachines();

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
        PublicApiBootstrap.freezeAndInstallMachines();
        assertThatThrownBy(PublicApiBootstrap::installRecipes)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(unknown.toString());

        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.begin();
        PublicApiBootstrap.freezeAndInstallMachines();
        PublicApiBootstrap.installRecipes();
        assertThatThrownBy(() -> MachineApi.registerMachine(machine("after")))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("after");
        assertThatThrownBy(() -> RecipeApi.registerRecipe(recipe("after_recipe", unknown)))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("after_recipe");
    }

    @Test
    void lifecycle_events_are_ordered_and_each_phase_freezes_before_the_next() {
        List<String> observedEvents = new ArrayList<>();
        Identifier machineId = id("ordered_machine");
        boolean[] active = {true};
        var definitions = new java.util.concurrent.atomic.AtomicReference<cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent>();
        var structures = new java.util.concurrent.atomic.AtomicReference<cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent>();
        var recipes = new java.util.concurrent.atomic.AtomicReference<cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent>();
        NeoForge.EVENT_BUS.addListener(cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent.class,
                event -> {
                    if (!active[0]) return;
                    observedEvents.add("RegisterMachineDefinationsEvent");
                    event.registerMachine(machineId, builder -> builder.displayNameKey("machine.mmcr.ordered_machine"));
                    definitions.set(event);
                });
        NeoForge.EVENT_BUS.addListener(cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent.class,
                event -> {
                    if (!active[0]) return;
                    observedEvents.add("RegisterMachineStructuresEvent");
                    event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
                    structures.set(event);
                });
        NeoForge.EVENT_BUS.addListener(cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent.class,
                event -> {
                    if (!active[0]) return;
                    observedEvents.add("MMCRRegisterRecipesEvent");
                    recipes.set(event);
                });

        MMCR.registerPublicApiLifecycleForTesting();
        active[0] = false;

        assertThatThrownBy(() -> definitions.get().registerMachine(id("late_definition"), builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> structures.get().registerStructure(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> recipes.get().registerRecipe(recipe("late_recipe", machineId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(observedEvents).containsExactly(
                "RegisterMachineDefinationsEvent",
                "RegisterMachineStructuresEvent",
                "MMCRRegisterRecipesEvent");
    }

    @Test
    void public_api_classes_do_not_embed_internal_bootstrap_dependency() throws IOException {
        for (Class<?> apiClass : new Class<?>[]{MachineApi.class, RecipeApi.class}) {
            String bytecode = new String(apiClass.getResourceAsStream(apiClass.getSimpleName() + ".class").readAllBytes());
            assertThat(bytecode).doesNotContain("cn/howxu/mmcr/internal/api/PublicApiBootstrap");
        }
    }

    private static MachineDefinition machine(String path) {
        return MachineBuilder.machine(id(path)).build();
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
