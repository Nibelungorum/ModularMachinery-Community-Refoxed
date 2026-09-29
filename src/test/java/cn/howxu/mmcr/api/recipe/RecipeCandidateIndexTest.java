package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.FailureReport;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.util.IOType;
import com.mojang.serialization.Lifecycle;
import java.util.Set;
import net.minecraft.core.Holder;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeCandidateIndexTest {

    private static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath("test", "machine");
    private static final ResourceLocation LEVEL_TYPE = ResourceLocation.fromNamespaceAndPath("test", "recipe_search_level_type");
    private static final ResourceLocation LEVEL = ResourceLocation.fromNamespaceAndPath("test", "recipe_search_level");
    private RequirementHandlerRegistry.TestScope requirementScope;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindComponents(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.RAW_COPPER, Items.RAW_IRON);
        TestBootstrap.registerType(new LevelType(LEVEL_TYPE, Component.literal("Recipe Search Level")));
        TestBootstrap.registerLevel(new MachineLevel(LEVEL, LEVEL_TYPE, 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
    }

    @BeforeEach
    void registerLoadedHeatRequirement() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
        RequirementHandlerRegistry.register(LoadedHeatRequirement.TEMPERATURE_TYPE);
    }

    @AfterEach
    void clearLoadedHeatRequirement() {
        LoadedHeatRequirement.installUnavailableHandler();
        requirementScope.close();
    }

    @Test
    void candidatesIncludeMatchingExactItemsAndFallbackRecipesInOriginalOrder() {
        MachineRecipe iron = itemRecipe("iron", Ingredient.of(Items.IRON_INGOT));
        MachineRecipe gold = itemRecipe("gold", Ingredient.of(Items.GOLD_INGOT));
        MachineRecipe noItemInput = RecipeTestSupport.create(id("no_item"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1);

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(iron, gold, noItemInput));

        assertThat(index.candidates(List.of(Items.IRON_INGOT))).containsExactly(iron, noItemInput);
        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(noItemInput);
    }

    @Test
    void multiItemIngredientFallsBackRatherThanExcludingAValidRecipe() {
        MachineRecipe alternatives = itemRecipe("alternatives", Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT));

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(alternatives));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(alternatives);
    }

    @Test
    void tagIngredientFallsBackWithoutDereferencingDuringIndexBuild() {
        MachineRecipe tagged = itemRecipe("tagged", Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.SWORDS)));

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(tagged));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(tagged);
    }

    @Test
    void single_member_named_tag_falls_back_instead_of_being_indexed_as_exact() {
        MachineRecipe tagged = itemRecipe("single_member_tag", singleMemberTagIngredient());

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(tagged));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(tagged);
    }

    @Test
    void unbound_named_tag_falls_back_without_throwing() {
        MachineRecipe tagged = itemRecipe("unbound_tag", unboundTagIngredient());

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(tagged));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(tagged);
    }

    @Test
    void custom_ingredient_falls_back_even_when_it_reports_one_item() {
        MachineRecipe custom = itemRecipe("custom_single_item", new Ingredient(new ICustomIngredient() {
            @Override
            public boolean test(ItemStack stack) {
                return stack.is(Items.IRON_INGOT);
            }

            @Override
            public Stream<Holder<Item>> items() {
                return Stream.of(Items.IRON_INGOT.builtInRegistryHolder());
            }

            @Override
            public boolean isSimple() {
                return false;
            }

            @Override
            public IngredientType<?> getType() {
                return null;
            }
        }));

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(custom));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(custom);
    }

    @Test
    void unknown_ingredient_falls_back_when_recipe_also_has_an_exact_item_input() {
        MachineRecipe unknown = RecipeTestSupport.create(id("unknown_with_exact_item"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.INPUT, null, 1, ItemStack.EMPTY)), false);

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(unknown));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(unknown);
    }

    @Test
    void non_item_inputs_fall_back_when_recipe_also_has_an_exact_item_input() {
        MachineRecipe mixed = RecipeTestSupport.create(id("mixed_exact_item_and_energy"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY),
                new EnergyRequirement(RecipeModifier.IOType.INPUT, 1)), false);

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(mixed));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(mixed);
    }

    @Test
    void level_requirements_do_not_count_or_demote_exact_item_candidates() {
        MachineRecipe itemAndLevel = RecipeTestSupport.create(id("exact_item_and_level"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY),
                LevelRequirement.input(LEVEL_TYPE, LEVEL)), false, List.of(), false, Set.of());

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(itemAndLevel));

        assertThat(itemAndLevel.inputRequirementCount()).isEqualTo(1);
        assertThat(index.candidates(List.of(Items.IRON_INGOT))).containsExactly(itemAndLevel);
        assertThat(index.candidates(List.of(Items.DIAMOND))).isEmpty();
    }

    @Test
    void stage_requirements_do_not_count_or_demote_exact_item_candidates() {
        MachineRecipe itemAndStage = RecipeTestSupport.create(id("exact_item_and_stage"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY),
                StageRequirement.input(2)), false, List.of(), false, Set.of());

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(itemAndStage));

        assertThat(itemAndStage.inputRequirementCount()).isEqualTo(1);
        assertThat(index.candidates(List.of(Items.IRON_INGOT))).containsExactly(itemAndStage);
        assertThat(index.candidates(List.of(Items.DIAMOND))).isEmpty();
    }

    @Test
    void capability_tagged_inputs_fall_back_when_recipe_also_has_an_exact_item_input() {
        MachineRecipe tagged = RecipeTestSupport.create(id("tagged_exact_item"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, List.of("north_buses"))), false);

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(tagged));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(tagged);
    }

    @Test
    void tagged_non_item_requirements_also_fall_back_when_recipe_has_an_exact_item_input() {
        MachineRecipe tagged = RecipeTestSupport.create(id("tagged_energy_with_exact_item"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY),
                new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 1, List.of("energy_hatches"))), false);

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(tagged));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(tagged);
    }

    @Test
    void unknown_input_types_do_not_filter_exact_item_candidates() {
        MachineRecipe exact = itemRecipe("unknown_input_types", Ingredient.of(Items.IRON_INGOT));

        RecipeCandidateIndex index = RecipeCandidateIndex.build(MACHINE, List.of(exact));

        assertThat(index.candidates(null)).containsExactly(exact);
    }

    @Test
    void mixed_pool_recipes_cannot_share_a_candidate_index() {
        MachineRecipe firstPoolRecipe = itemRecipe("first_pool", Ingredient.of(Items.IRON_INGOT));
        MachineRecipe secondPoolRecipe = RecipeTestSupport.create(id("second_pool"),
                ResourceLocation.fromNamespaceAndPath("test", "other_pool"), 20,
                List.of(), List.of(), List.of(), 0, 1);

        assertThatThrownBy(() -> RecipeCandidateIndex.build(MACHINE, List.of(firstPoolRecipe, secondPoolRecipe)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipe pool");
    }

    @Test
    void preordered_candidates_preserve_supplied_order() {
        MachineRecipe highPriority = RecipeTestSupport.create(id("high_priority"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1);
        MachineRecipe moreInputs = RecipeTestSupport.create(id("more_inputs"), MACHINE, 20,
                List.of(), List.of(), List.of(), 1, 1);
        MachineRecipe idTieBreaker = RecipeTestSupport.create(id("id_tie_breaker"), MACHINE, 20,
                List.of(), List.of(), List.of(), 2, 1);
        List<MachineRecipe> supplied = List.of(idTieBreaker, highPriority, moreInputs);
        RecipeSearchResult existing = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1,
                supplied, List.of()).compute();

        RecipeSearchResult result = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1,
                supplied, List.of(), List.of()).compute();

        assertThat(existing.recipe()).isEqualTo(highPriority);
        assertThat(result.recipe()).isEqualTo(idTieBreaker);
    }

    @Test
    void worker_level_failure_snapshot_preserves_sync_pending_input_conflict() {
        MachineRecipe specific = RecipeTestSupport.create(id("worker_level_blocked_specific"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.GOLD_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET, 1))));
        MachineRecipe fallback = RecipeTestSupport.create(id("worker_level_blocked_fallback"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));

        RecipeSearchResult result = RecipeSearchTask.forPlanningValues(emptySnapshot(), MACHINE, 0L, 1L,
                List.of(specific, fallback), List.of(
                RecipeSearchTask.PlanningValue.failure(specific.id(), BuiltinFailureReasons.LEVEL_INSUFFICIENT, 0, true),
                RecipeSearchTask.PlanningValue.success(fallback.id()))).compute();

        assertThat(result.recipe()).isEqualTo(fallback);
        assertThat(result.hasMoreSpecificPendingInputCandidate()).isTrue();
    }

    @Test
    void search_failure_report_prioritizes_core_failure_reasons() {
        assertThat(BuiltinFailureReasons.MISSING_INPUT.priority())
                .isGreaterThan(BuiltinFailureReasons.MISSING_ENERGY.priority());
        assertThat(BuiltinFailureReasons.MISSING_ENERGY.priority())
                .isGreaterThan(BuiltinFailureReasons.LEVEL_INSUFFICIENT.priority());
        assertThat(BuiltinFailureReasons.LEVEL_INSUFFICIENT.priority())
                .isGreaterThan(BuiltinFailureReasons.MISSING_OUTPUT.priority());
    }

    @Test
    void search_task_does_not_select_a_candidate_from_another_recipe_pool() {
        MachineRecipe foreign = RecipeTestSupport.create(id("foreign_pool_search"),
                ResourceLocation.fromNamespaceAndPath("test", "other_pool"), 20,
                List.of(), List.of(), List.of(), 0, 1);

        RecipeSearchResult result = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1L,
                List.of(foreign), List.of(), List.of()).compute();

        assertThat(result.success()).isFalse();
        assertThat(result.recipe()).isNull();
    }

    @Test
    void search_prefers_missing_input_over_energy_and_level_requirements() {
        MachineRecipe levelLimited = RecipeTestSupport.create(id("level_limited"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(LevelRequirement.input(LEVEL_TYPE, LEVEL)), false, List.of(), false, Set.of());
        MachineRecipe energyLimited = RecipeTestSupport.create(id("energy_limited"), MACHINE, 20,
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 1)), List.of(), List.of(), 0, 1);
        MachineRecipe inputLimited = itemRecipe("input_limited", Ingredient.of(Items.IRON_INGOT));

        RecipeSearchResult result = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1L,
                List.of(levelLimited, energyLimited, inputLimited), List.of(), List.of()).compute();

        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(result.primaryFailure()).isSameAs(result.failure().failure());
        assertThat(result.failure().details()).containsEntry("required", "1");
        assertThat(result.failure().details()).containsEntry("available", "0");
        assertThat(result.planningResult()).isNull();
    }

    @Test
    void search_prefers_missing_output_after_a_feasible_input_over_unrelated_missing_inputs() {
        BulkItemStorage storage = new BulkItemStorage(1, null);
        ItemStack copper = new ItemStack(Items.RAW_COPPER);
        assertThat(storage.forceInsert(copper, 1L, false)).isEqualTo(1L);
        assertThat(ItemStack.isSameItemSameComponents(storage.resource(0), copper)).isTrue();
        assertThat(storage.amount(0)).isEqualTo(1L);
        MachineRecipe copperRecipe = recipeWithRequirements("copper_missing_output", List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.RAW_COPPER), 1,
                        ItemStack.EMPTY),
                MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND))));
        MachineRecipe ironRecipe = itemRecipe("iron_missing_input", Ingredient.of(Items.RAW_IRON));
        ItemBusCapability capability = new ItemBusCapability(storage, IOType.INPUT);

        assertThat(new CraftingContext(new CapabilitySnapshot(List.of(capability))).planStartResult(copperRecipe, 1)
                .failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_OUTPUT);

        RecipeSearchResult result = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1L,
                List.of(copperRecipe, ironRecipe),
                List.of(capability), List.of()).compute();

        assertThat(result.failure()).isNotNull();
        assertThat(result.failureReport().candidates()).extracting(FailureReport.Candidate::validity)
                .containsExactlyInAnyOrder(0.5F, 1.5F);
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_OUTPUT);
    }

    @Test
    void search_prefers_missing_energy_after_a_feasible_input_over_unrelated_missing_inputs() {
        BulkItemStorage storage = new BulkItemStorage(1, null);
        ItemStack copper = new ItemStack(Items.RAW_COPPER);
        assertThat(storage.forceInsert(copper, 1L, false)).isEqualTo(1L);
        MachineRecipe copperRecipe = recipeWithRequirements("copper_missing_energy", List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.RAW_COPPER), 1,
                        ItemStack.EMPTY),
                new EnergyRequirement(RecipeModifier.IOType.INPUT, 1)));
        MachineRecipe ironRecipe = itemRecipe("iron_missing_input_for_energy", Ingredient.of(Items.RAW_IRON));

        RecipeSearchResult result = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1L,
                List.of(copperRecipe, ironRecipe),
                List.of(new ItemBusCapability(storage, IOType.INPUT)), List.of()).compute();

        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_ENERGY);
    }

    @Test
    void search_selects_the_highest_priority_failure_and_keeps_search_and_source_trace_frames() {
        MachineRecipe missingInput = recipeWithRequirements("search_missing_input",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY)));
        MachineRecipe missingEnergy = recipeWithRequirements("search_missing_energy",
                List.of(new EnergyRequirement(1)));
        MachineRecipe lowTemperature = recipeWithRequirements("search_low_temperature",
                List.of(LoadedHeatRequirement.minimumTemperature(450D)));
        MachineRecipe insufficientLevel = recipeWithRequirements("search_insufficient_level",
                List.of(LevelRequirement.input(LEVEL_TYPE, LEVEL)));
        MachineRecipe missingOutput = recipeWithRequirements("search_missing_output",
                List.of(MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND))));
        List<MachineRecipe> candidates = List.of(missingInput, missingEnergy, lowTemperature,
                insufficientLevel, missingOutput);

        List<MachineRecipe> indexedCandidates = RecipeCandidateIndex.build(MACHINE, candidates).candidates(null);
        RecipeSearchResult result = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1L,
                indexedCandidates, List.of(), List.of()).compute();

        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT);
        assertThat(result.failureReport().candidates()).extracting(FailureReport.Candidate::status)
                .extracting(ExecutionStatus::reason)
                .containsExactlyInAnyOrder(BuiltinFailureReasons.MISSING_INPUT,
                        BuiltinFailureReasons.MISSING_ENERGY,
                        MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT,
                        BuiltinFailureReasons.LEVEL_INSUFFICIENT,
                        BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(result.primaryFailure().trace().frames())
                .anySatisfy(frame -> assertThat(frame.phase()).isEqualTo(FailurePhase.RECIPE_SEARCH))
                .anySatisfy(frame -> assertThat(frame.source()).isEqualTo(LoadedHeatRequirement.TYPE.id()));
    }

    @Test
    void search_level_failure_is_typed_with_recipe_trace_and_level_details() {
        MachineRecipe levelLimited = RecipeTestSupport.create(id("level_only"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(LevelRequirement.input(LEVEL_TYPE, LEVEL)), false, List.of(), false, Set.of());

        RecipeSearchResult result = new RecipeSearchTask(emptySnapshot(), MACHINE, 0L, 1L,
                List.of(levelLimited), List.of(), List.of()).compute();

        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.LEVEL_INSUFFICIENT);
        assertThat(result.failure().details()).containsEntry("level_type", LEVEL_TYPE.toString());
        assertThat(result.failure().details()).containsEntry("required_level", LEVEL.toString());
        assertThat(result.failure().details()).doesNotContainKey("actual_level");
        assertThat(result.primaryFailure()).isSameAs(result.failure().failure());
        assertThat(result.primaryFailure().trace().frames()).singleElement().satisfies(frame -> {
            assertThat(frame.source()).isEqualTo(MMCR.id("crafting_runtime"));
            assertThat(frame.phase()).isEqualTo(FailurePhase.LEVEL_CHECK);
            assertThat(frame.recipeId()).isEqualTo(levelLimited.id());
            assertThat(frame.requirementIndex()).isNull();
        });
    }

    @Test
    void search_failure_report_keeps_first_equal_priority_and_validity_candidate() {
        ResourceLocation requiredHost = id("required_host");
        MachineRecipe first = RecipeTestSupport.create(id("first_module_failure"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false,
                List.of(), false, Set.of(requiredHost));
        MachineRecipe second = RecipeTestSupport.create(id("second_module_failure"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false,
                List.of(), false, Set.of(requiredHost));

        RecipeSearchResult result = new RecipeSearchTask(snapshot(ModuleConnectionStatus.disconnected()), MACHINE,
                0L, 1L, List.of(first, second), List.of(), List.of()).compute();

        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MODULE_CONNECTION);
        assertThat(result.primaryFailure().trace().frames()).singleElement()
                .satisfies(frame -> assertThat(frame.recipeId()).isEqualTo(first.id()));
    }

    private static ControllerRuntimeSnapshot emptySnapshot() {
        return snapshot(ModuleConnectionStatus.notRequired());
    }

    private static ControllerRuntimeSnapshot snapshot(ModuleConnectionStatus moduleConnectionStatus) {
        return new ControllerRuntimeSnapshot(StructureSnapshot.empty(), 0L, 0L, 0L,
                Map.of(), Map.of(), Set.of(),
                moduleConnectionStatus, 0,
                CraftingStateSnapshot.empty(0L, 0L, 0L),
                FactorySnapshot.empty(), List.of(), List.of(), List.of(),
                "", "", 0, false, false, 0, 0, 1, Map.of());
    }

    private static MachineRecipe itemRecipe(String path, Ingredient ingredient) {
        return RecipeTestSupport.create(id(path), MACHINE, 20, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ingredient, 1, ItemStack.EMPTY)), false);
    }

    private static MachineRecipe recipeWithRequirements(String path, List<MachineRequirement> requirements) {
        return RecipeTestSupport.create(id(path), MACHINE, 20, List.of(), List.of(), List.of(), 0, 1,
                false, List.of(), requirements, false, List.of(), false, Set.of());
    }

    private static Ingredient singleMemberTagIngredient() {
        MappedRegistry<Item> registry = new MappedRegistry<>(
                ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("mmcr_test", "tag_items")),
                Lifecycle.stable(), true);
        Holder.Reference<Item> holder = registry.createIntrusiveHolder(Items.IRON_INGOT);
        registry.register(ResourceKey.create(registry.key(), id("tagged_item")), Items.IRON_INGOT, RegistrationInfo.BUILT_IN);
        TagKey<Item> tag = TagKey.create(registry.key(), id("single_member"));
        registry.bindTags(Map.of(tag, List.of(holder)));
        registry.freeze();
        return Ingredient.of(registry.get(tag).orElseThrow());
    }

    private static Ingredient unboundTagIngredient() {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id("unbound"));
        return Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, tag));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static void bindComponents(Item... items) {
        for (Item item : items) item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }
}
