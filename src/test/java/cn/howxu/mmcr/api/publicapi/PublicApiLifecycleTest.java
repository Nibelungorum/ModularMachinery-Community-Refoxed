package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PatternBuilder;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
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
        MachineDefinition definition = machine("before");
        MMCRMachineDefinationsEvent event = new MMCRMachineDefinationsEvent();
        event.registerMachine(definition);
        event.freeze();
        assertThatThrownBy(() -> PublicApiBootstrap.registerDefinitions(event))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("before")
                 .hasMessageContaining("before begin");
    }

    @Test
    void machine_installation_precedes_recipe_installation_and_is_idempotent() {
        PublicApiBootstrap.begin();
        MachineDefinition machine = machine("press");
        MachineRecipeDefinition recipe = recipe("press_recipe", machine.id());

        registerMachine(machine);
        registerRecipe(recipe);
        installMachines(machine);
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
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(machine.id(), builder -> builder);
        assertThatThrownBy(() -> definitions.registerMachine(machine.id(), builder -> builder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(machine.id().toString());

        MachineRecipeDefinition recipe = recipe("duplicate_recipe", machine.id());
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
        recipes.registerRecipe(recipe);
        assertThatThrownBy(() -> recipes.registerRecipe(recipe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(recipe.id().toString());
    }

    @Test
    void unknown_recipe_machine_and_after_freeze_registration_are_rejected() {
        PublicApiBootstrap.begin();
        Identifier unknown = id("unknown_machine");
        registerRecipe(recipe("unknown_recipe", unknown));
        installMachines();
        assertThatThrownBy(PublicApiBootstrap::installRecipes)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(unknown.toString());

        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.begin();
        installMachines();
        PublicApiBootstrap.installRecipes();
        MMCRMachineDefinationsEvent lateDefinitions = new MMCRMachineDefinationsEvent();
        lateDefinitions.freeze();
        assertThatThrownBy(() -> lateDefinitions.registerMachine(id("after"), builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        MMCRMachineRecipesEvent lateRecipes = new MMCRMachineRecipesEvent();
        lateRecipes.freeze();
        assertThatThrownBy(() -> lateRecipes.registerRecipe(recipe("after_recipe", unknown)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lifecycle_events_are_ordered_and_each_phase_freezes_before_the_next() {
        List<String> observedEvents = new ArrayList<>();
        Identifier machineId = id("ordered_machine");
        boolean[] active = {true};
        var definitions = new java.util.concurrent.atomic.AtomicReference<MMCRMachineDefinationsEvent>();
        var structures = new java.util.concurrent.atomic.AtomicReference<MMCRMachineStructuresEvent>();
        var recipes = new java.util.concurrent.atomic.AtomicReference<MMCRMachineRecipesEvent>();
        NeoForge.EVENT_BUS.addListener(MMCRMachineDefinationsEvent.class,
                event -> {
                    if (!active[0]) return;
                    observedEvents.add("MMCRMachineDefinationsEvent");
                    event.registerMachine(machineId, builder -> builder.displayNameKey("machine.mmcr.ordered_machine"));
                    definitions.set(event);
                });
        NeoForge.EVENT_BUS.addListener(MMCRMachineStructuresEvent.class,
                event -> {
                    if (!active[0]) return;
                    observedEvents.add("MMCRMachineStructuresEvent");
                    event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
                    structures.set(event);
                });
        NeoForge.EVENT_BUS.addListener(MMCRMachineRecipesEvent.class,
                event -> {
                    if (!active[0]) return;
                    observedEvents.add("MMCRMachineRecipesEvent");
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
                "MMCRMachineDefinationsEvent",
                "MMCRMachineStructuresEvent",
                "MMCRMachineRecipesEvent");
    }

    @Test
    void structure_event_freeze_validates_modifier_and_level_references_and_returns_snapshot() {
        Identifier machineId = id("snapshot_machine");
        Identifier typeId = id("snapshot_type");
        Identifier levelId = id("snapshot_level");
        Identifier modifierId = id("snapshot_modifier");
        MMCRMachineStructuresEvent event = new MMCRMachineStructuresEvent(List.of(machineId));
        event.registerLevelType(new cn.howxu.mmcr.api.machine.level.LevelType(typeId,
                net.minecraft.network.chat.Component.literal("Snapshot")));
        event.registerLevel(new cn.howxu.mmcr.api.machine.level.MachineLevel(levelId, typeId, 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock(Blocks.FURNACE),
                net.minecraft.world.item.ItemStack.EMPTY,
                cn.howxu.mmcr.api.machine.level.LevelModifier.IDENTITY));
        event.registerModifier(modifierId, new ModifierDefinition(List.of()));
        event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.levelSlot('F', typeId).modifier('F', modifierId))));

        var snapshot = event.freeze();

        assertThat(snapshot.levelTypes()).containsKey(typeId);
        assertThat(snapshot.levels()).containsKey(levelId);
        assertThat(snapshot.modifiers()).containsKey(modifierId);
        assertThatThrownBy(() -> snapshot.structures().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> event.registerModifier(id("late"), new ModifierDefinition(List.of())))
                .isInstanceOf(ApiRegistrationException.class);
    }

    @Test
    void structure_event_rejects_unknown_references_at_freeze() {
        Identifier machineId = id("invalid_snapshot_machine");
        MMCRMachineStructuresEvent event = new MMCRMachineStructuresEvent(List.of(machineId));
        event.registerModifier(id("known_modifier"), new ModifierDefinition(List.of()));
        event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.modifier('F', id("unknown_modifier")))));

        assertThatThrownBy(event::freeze)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("unknown_modifier");
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

    private static void registerMachine(MachineDefinition definition) {
        MMCRMachineDefinationsEvent event = new MMCRMachineDefinationsEvent();
        event.registerMachine(definition);
        event.freeze();
        PublicApiBootstrap.registerDefinitions(event);
    }

    private static void registerRecipe(MachineRecipeDefinition definition) {
        MMCRMachineRecipesEvent event = new MMCRMachineRecipesEvent();
        event.registerRecipe(definition);
        event.freeze();
        PublicApiBootstrap.registerRecipes(event);
    }

    private static void installMachines(MachineDefinition... definitions) {
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(
                java.util.Arrays.stream(definitions).map(MachineDefinition::id).toList());
        for (MachineDefinition definition : definitions) {
            structures.registerStructure(definition.id(), PublicApiLifecycleTest::patternStructure);
        }
        structures.freeze();
        PublicApiBootstrap.composeMachineRegistrations(new MMCRMachineDefinationsEvent(), structures);
        MachineDefinitions.freezeRegistryPhase();
    }

    private static cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder patternStructure(
            cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder builder) {
        return builder.fullStructure(stage -> stage.pattern(PublicApiLifecycleTest::pattern));
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
