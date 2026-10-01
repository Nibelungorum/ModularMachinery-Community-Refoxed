package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import cn.howxu.mmcr.api.presentation.ReadableNumber;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.port.PortDefinitionRegistry;
import cn.howxu.mmcr.api.machine.definition.MachineStructureBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.DisplayStack;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.machine.definition.MachineLevel;
import cn.howxu.mmcr.api.machine.definition.PatternBuilder;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.PublicMachineDefinitionProviders;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
        ModifierRegistry.installSnapshot(Map.of());
    }

    @AfterEach
    void cleanup() throws Exception {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
        ModifierRegistry.installSnapshot(Map.of());
    }

    @Test
    void registration_before_begin_is_rejected() {
        MachineDefinition definition = machine("before");
        MachineDefinitionRegistration event = new MachineDefinitionRegistration();
        event.registerMachine(definition);
        event.freeze();
        assertThatThrownBy(() -> ContentRegistrationCoordinator.collectMachines(event))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("Startup content collection")
                 .hasMessageContaining("BEFORE_BEGIN");
    }

    @Test
    void machine_installation_precedes_recipe_installation_and_is_idempotent() {
        PublicApiBootstrap.begin();
        MachineDefinition machine = machine("press");
        MachineRecipeDefinition recipe = recipe("press_recipe", machine.id());

        registerMachine(machine);
        registerRecipe(recipe);
        installMachines(machine);
        assertThat(MachineDefinitions.getRegistration(machine.id())).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipe.id())).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipe.id()).recipePoolId()).isEqualTo(machine.id());
        assertThat(Machines.isRegistrationOpen()).isFalse();
        assertThat(Recipes.isRegistrationOpen()).isFalse();
    }

    @Test
    void empty_startup_commit_is_allowed() {
        PublicApiBootstrap.begin();

        assertThatCode(ContentRegistrationCoordinator::commitStartup).doesNotThrowAnyException();
    }

    @Test
    void service_loaded_providers_register_before_finalization() {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();

        RegisterMachineDefinitionsEvent event = new RegisterMachineDefinitionsEvent();
        PublicMachineDefinitionProviders.registerAll(event);
        RegistrationAdapters.freeze(event);
        ContentRegistrationCoordinator.collectMachines(RegistrationAdapters.core(event));
        ContentRegistrationCoordinator.commitStartup();

        assertThat(MachineDefinitions.getRegistration(id("service_loaded_machine"))).isNotNull();
        assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();
    }

    @Test
    void duplicate_machine_and_recipe_ids_are_rejected() {
        PublicApiBootstrap.begin();
        MachineDefinition machine = machine("duplicate_machine");
        MachineDefinitionRegistration definitions = new MachineDefinitionRegistration();
        definitions.registerMachine(machine.id(), builder -> builder);
        assertThatThrownBy(() -> definitions.registerMachine(machine.id(), builder -> builder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(machine.id().toString());

        MachineRecipeDefinition recipe = recipe("duplicate_recipe", machine.id());
        MachineRecipeRegistration recipes = new MachineRecipeRegistration();
        recipes.registerRecipe(recipe);
        assertThatThrownBy(() -> recipes.registerRecipe(recipe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(recipe.id().toString());
    }

    @Test
    void unknown_recipe_machine_and_after_freeze_registration_are_rejected() {
        PublicApiBootstrap.begin();
        ResourceLocation unknown = id("unknown_machine");
        registerRecipe(recipe("unknown_recipe", unknown));
        collectStructures();
        assertThatCode(ContentRegistrationCoordinator::commitStartup).doesNotThrowAnyException();
        assertThat(RecipeRegistry.getRecipe(id("unknown_recipe"))).isNull();

        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.begin();
        installMachines();
        MachineDefinitionRegistration lateDefinitions = new MachineDefinitionRegistration();
        lateDefinitions.freeze();
        assertThatThrownBy(() -> lateDefinitions.registerMachine(id("after"), builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        MachineRecipeRegistration lateRecipes = new MachineRecipeRegistration();
        lateRecipes.freeze();
        assertThatThrownBy(() -> lateRecipes.registerRecipe(recipe("after_recipe", unknown)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lifecycle_events_are_ordered_and_each_phase_freezes_before_the_next() {
        List<String> observedEvents = new ArrayList<>();
        ResourceLocation machineId = id("ordered_machine");
        var definitions = new AtomicReference<MachineDefinitionRegistration>();
        var structures = new AtomicReference<StructureRegistration>();
        var recipes = new AtomicReference<MachineRecipeRegistration>();
        StartupContentRegistration.registerForTesting(
                event -> {
                    observedEvents.add("definitions");
                    event.registerMachine(machineId, builder -> builder.displayNameKey("machine.mmcr.ordered_machine"));
                    definitions.set(event);
                },
                event -> {
                    observedEvents.add("structures");
                    event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
                    structures.set(event);
                },
                event -> {
                    observedEvents.add("recipes");
                    recipes.set(event);
                });

        assertThatThrownBy(() -> definitions.get().registerMachine(id("late_definition"), builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> structures.get().registerStructure(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> recipes.get().registerRecipe(recipe("late_recipe", machineId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(observedEvents).containsExactly(
                "definitions",
                "structures",
                "recipes");
    }

    @Test
    void structure_event_freeze_validates_modifier_and_level_references_and_returns_snapshot() {
        ResourceLocation machineId = id("snapshot_machine");
        ResourceLocation typeId = id("snapshot_type");
        ResourceLocation levelId = id("snapshot_level");
        ResourceLocation modifierId = id("snapshot_modifier");
        StructureRegistration event = new StructureRegistration(List.of(machineId));
        event.registerLevelType(new cn.howxu.mmcr.api.machine.level.LevelType(typeId,
                Component.literal("Snapshot")));
        event.registerLevel(new cn.howxu.mmcr.api.machine.level.MachineLevel(levelId, typeId, 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock(Blocks.FURNACE),
                ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
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
        ResourceLocation machineId = id("invalid_snapshot_machine");
        StructureRegistration event = new StructureRegistration(List.of(machineId));
        event.registerModifier(id("known_modifier"), new ModifierDefinition(List.of()));
        event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.modifier('F', id("unknown_modifier")))));

        assertThatThrownBy(event::freeze)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("unknown_modifier");
    }

    @Test
    void coordinator_commit_installs_modifier_item_bindings_after_collection() {
        PublicApiBootstrap.begin();
        ResourceLocation modifierId = id("lifecycle_modifier");
        ItemStack stack = new ItemStack(Items.EMERALD, 1);
        stack.set(DataComponents.MAX_STACK_SIZE, 32);
        StructureRegistration event = new StructureRegistration(List.of());
        event.registerModifier(modifierId, new ModifierDefinition(List.of()));
        event.registerModifierItem(stack, modifierId);
        event.freeze();

        ContentRegistrationCoordinator.collectStructures(event);
        ContentRegistrationCoordinator.commitStartup();

        assertThat(ModifierRegistry.get(modifierId)).isNotNull();
        assertThat(ModifierRegistry.modifierFor(stack.copyWithCount(32))).isEqualTo(modifierId);
    }

    @Test
    void public_level_declarations_convert_to_canonical_runtime_levels() {
        ResourceLocation typeId = id("public_type");
        ResourceLocation levelId = id("public_level");
        StructureRegistration event = new StructureRegistration(List.of());
        LevelType type = new LevelType(typeId, Component.literal("Public"));
        event.registerLevelType(type);
        event.registerLevel(new MachineLevel(levelId, typeId, 2,
                BlockPredicate.block(Blocks.FURNACE),
                DisplayStack.of(new ItemStack(Blocks.FURNACE)),
                new ModifierDefinition(List.of(
                        MachineModifier.numeric("duration", "input", 0.5D, "multiply", false),
                        MachineModifier.numeric("parallelism", "machine", 1D, "add", false),
                        MachineModifier.numeric("factory_threads", "machine", 2D, "add", false)))));

        var snapshot = event.freeze();

        assertThat(snapshot.levelTypes().get(typeId)).isSameAs(type);
        assertThat(snapshot.levels().get(levelId).priority()).isEqualTo(2);
        assertThat(snapshot.levels().get(levelId).modifier().modifiers()).contains(
                MachineModifier.numeric("parallelism", "machine", 1D, "add", false));
    }

    @Test
    void public_api_classes_do_not_embed_internal_bootstrap_dependency() throws IOException {
        for (Class<?> apiClass : new Class<?>[]{Machines.class, Recipes.class, ReadableNumber.class}) {
            String bytecode = new String(apiClass.getResourceAsStream(apiClass.getSimpleName() + ".class").readAllBytes());
            assertThat(bytecode).doesNotContain("cn/howxu/mmcr/internal/api/PublicApiBootstrap");
        }
    }

    @Test
    void public_readable_number_exposes_compact_and_exact_formats() {
        assertThat(ReadableNumber.formatCompact(1_000)).isEqualTo("1k");
        assertThat(ReadableNumber.formatExact(1_000_000L)).isEqualTo("1,000,000");
    }

    @Test
    void built_in_capability_recipe_output_and_port_paths_are_registry_owned() {
        PublicApiBootstrap.begin();

        for (CapabilityDefinition definition : CapabilityRegistry.values()) {
            assertThat(CapabilityRegistry.get(definition.type())).isSameAs(definition);
            assertThat(definition.facets()).isNotEmpty();
        }

        RequirementHandlerRegistry.registerBuiltIns();
        List<RequirementType<?>> requirements = List.of(ItemRequirement.TYPE, FluidRequirement.TYPE,
                EnergyRequirement.TYPE, SmartInterfaceRequirement.TYPE);
        for (RequirementType<?> type : requirements) {
            assertThat(RequirementHandlerRegistry.typeFor(type.id())).isSameAs(type);
            assertThat(RequirementHandlerRegistry.handlerFor(type)).isNotNull();
            assertThat(type.syncCodec().maxPayloadSize())
                    .isBetween(1, RecipeSyncCodec.DEFAULT_MAX_PAYLOAD_SIZE);
        }

        OutputRegistry.registerBuiltIns();
        List<OutputType<?>> outputs = List.of(OutputRegistry.typeFor(MMCR.id("item")),
                OutputRegistry.typeFor(MMCR.id("fluid")));
        for (OutputType<?> type : outputs) {
            assertThat(OutputRegistry.canonicalType(type)).isSameAs(type);
            assertThat(type.syncCodec().maxPayloadSize())
                    .isBetween(1, RecipeSyncCodec.DEFAULT_MAX_PAYLOAD_SIZE);
        }

        assertThat(PortDefinitionRegistry.values()).isNotEmpty().allSatisfy(definition ->
                definition.bindings().forEach(binding ->
                        assertThat(CapabilityRegistry.get(binding.type())).isNotNull()));

        List<MachineCapability> capabilities = List.of(
                RuntimeTestFixtures.itemInput(new BlockPos(14, 0, 0))
                        .capabilitySnapshot().capabilities().getFirst(),
                RuntimeTestFixtures.fluidInput(new BlockPos(15, 0, 0))
                        .capabilitySnapshot().capabilities().getFirst(),
                RuntimeTestFixtures.energyInput(new BlockPos(16, 0, 0))
                        .capabilitySnapshot().capabilities().getFirst());
        for (MachineCapability capability : capabilities) {
            CapabilityDefinition definition = CapabilityRegistry.get(capability.type());
            assertThat(definition).isNotNull();
            assertThat(definition.facets()).containsAll(capability.view().facets());
        }
    }

    @Test
    void mutable_built_in_capability_facets_have_persistent_port_state() {
        PublicApiBootstrap.begin();
        List<CapabilitySnapshot> snapshots = List.of(
                RuntimeTestFixtures.itemInput(new BlockPos(10, 0, 0)).capabilitySnapshot(),
                RuntimeTestFixtures.fluidInput(new BlockPos(11, 0, 0)).capabilitySnapshot(),
                RuntimeTestFixtures.energyInput(new BlockPos(12, 0, 0)).capabilitySnapshot(),
                new SmartInterfaceBlockEntity(new BlockPos(13, 0, 0),
                        ModBlocks.SMART_INTERFACE.get().defaultBlockState()).capabilitySnapshot());

        for (CapabilitySnapshot snapshot : snapshots) {
            for (MachineCapability capability : snapshot.capabilities()) {
                boolean nativeStorage = capability.facet(ItemHandlerFacet.class).isPresent()
                        || capability.facet(FluidHandlerFacet.class).isPresent()
                        || capability.facet(EnergyStorageFacet.class).isPresent();
                ValueFacet<?> value = capability.facet(ValueFacet.class).orElse(null);
                if (!nativeStorage && (value == null || value.isStateless())) continue;
                assertThat(snapshot.facets(PersistenceFacet.class))
                        .as(capability.type().id().toString())
                        .isNotEmpty();
            }
        }
    }

    @Test
    void capability_registry_rejects_registration_after_runtime_snapshot_creation() {
        StartupContentRegistration.registerForTesting();

        CapabilitySnapshot snapshot = RuntimeTestFixtures.itemInput(new BlockPos(14, 0, 0))
                .capabilitySnapshot();

        assertThatThrownBy(() -> CapabilityRegistry.register(new CapabilityDefinition(
                new CapabilityType(id("late_capability")), Set.of(), ignored -> null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");

        assertThat(snapshot.capabilities().getFirst().facet(ItemHandlerFacet.class)).isPresent();
    }

    private static MachineDefinition machine(String path) {
        return MachineBuilder.machine(id(path)).build();
    }

    private static void registerMachine(MachineDefinition definition) {
        MachineDefinitionRegistration event = new MachineDefinitionRegistration();
        event.registerMachine(definition);
        event.freeze();
         ContentRegistrationCoordinator.collectMachines(event);
    }

    private static void registerRecipe(MachineRecipeDefinition definition) {
        MachineRecipeRegistration event = new MachineRecipeRegistration();
        event.registerRecipe(definition);
        event.freeze();
         ContentRegistrationCoordinator.collectRecipes(event);
    }

    private static void installMachines(MachineDefinition... definitions) {
        collectStructures(definitions);
        ContentRegistrationCoordinator.commitStartup();
    }

    private static void collectStructures(MachineDefinition... definitions) {
        StructureRegistration structures = new StructureRegistration(
                Arrays.stream(definitions).map(MachineDefinition::id).toList());
        for (MachineDefinition definition : definitions) {
            structures.registerStructure(definition.id(), PublicApiLifecycleTest::patternStructure);
        }
        structures.freeze();
        ContentRegistrationCoordinator.collectStructures(structures);
    }

    private static MachineStructureBuilder patternStructure(
            MachineStructureBuilder builder) {
        return builder.fullStructure(stage -> stage.pattern(PublicApiLifecycleTest::pattern));
    }

    private static PatternBuilder pattern(
            PatternBuilder builder) {
        return builder.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F');
    }

    private static MachineRecipeDefinition recipe(String path, ResourceLocation machineId) {
        return MachineRecipeBuilder.recipe(id(path)).recipePool(machineId).duration(1).build();
    }

    private static ResourceLocation id(String path) {
        return MMCR.id(path);
    }
}
