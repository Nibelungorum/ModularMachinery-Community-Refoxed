package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies bounded source-view reuse without memoizing extension callbacks.
 *
 * @author howxu <dev@howxu.cn>
 */
class FailureRequirementCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void reuses_the_source_only_view_with_recipe_modifiers_and_an_immutable_list() {
        MachineRecipe recipe = recipe("failure_view", List.of(input(1)), List.of(),
                List.of(new RecipeModifier("", IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false)));
        FailureRequirementCache cache = new FailureRequirementCache();
        cache.prepare(List.of(recipe), 1L);

        List<MachineRequirement> first = cache.resolve(recipe);

        assertThat(cache.resolve(recipe)).isSameAs(first);
        assertThat(((ItemRequirement) first.getFirst()).count()).isEqualTo(2);
        assertThat(((ItemRequirement) recipe.requirements().getFirst()).count()).isEqualTo(1);
        assertThatThrownBy(() -> first.add(input(3))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void same_id_replacement_does_not_reuse_the_previous_definition() {
        MachineRecipe original = recipe("failure_replace", List.of(input(1)), List.of(), List.of());
        MachineRecipe replacement = recipe("failure_replace", List.of(input(2)), List.of(), List.of());
        FailureRequirementCache cache = new FailureRequirementCache();
        cache.prepare(List.of(original), 1L);
        List<MachineRequirement> first = cache.resolve(original);

        cache.prepare(List.of(replacement), 1L);
        List<MachineRequirement> second = cache.resolve(replacement);

        assertThat(second).isNotSameAs(first);
        assertThat(((ItemRequirement) second.getFirst()).count()).isEqualTo(2);
    }

    @Test
    void equal_definitions_with_different_source_identities_have_separate_views() {
        MachineRecipe original = recipe("failure_equal_source", List.of(input(1)), List.of(), List.of());
        MachineRecipe equal = recipe("failure_equal_source", original.requirements(), List.of(), List.of());
        assertThat(equal).isEqualTo(original).isNotSameAs(original);
        FailureRequirementCache cache = new FailureRequirementCache();
        cache.prepare(List.of(original, equal), 1L);

        List<MachineRequirement> first = cache.resolve(original);
        List<MachineRequirement> second = cache.resolve(equal);

        assertThat(second).isNotSameAs(first);
        assertThat(cache.resolve(original)).isSameAs(first);
        assertThat(cache.resolve(equal)).isSameAs(second);
    }

    @Test
    void native_outputs_reuse_the_modified_view_without_mutating_the_source_or_duplicating_outputs() {
        MachineRecipe recipe = recipe("failure_native_output", List.of(input(1)),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 2), 1F)),
                List.of(new RecipeModifier("", IOType.OUTPUT, 2F, RecipeModifier.Operation.MULTIPLY, false)));
        FailureRequirementCache cache = new FailureRequirementCache();
        cache.prepare(List.of(recipe), 1L);

        List<MachineRequirement> first = cache.resolve(recipe);

        assertThat(cache.resolve(recipe)).isSameAs(first);
        assertThat(first).filteredOn(requirement -> requirement.io() == IOType.OUTPUT).singleElement().satisfies(requirement -> {
            ItemRequirement output = (ItemRequirement) requirement;
            assertThat(output.stack().is(Items.GOLD_INGOT)).isTrue();
            assertThat(output.stack().getCount()).isEqualTo(4);
        });
        assertThat(((MachineOutput.ItemOutput) recipe.machineOutputs().getFirst()).stack().getCount()).isEqualTo(2);
    }

    @Test
    void catalog_changes_candidate_removal_and_explicit_clear_release_views() {
        MachineRecipe recipe = recipe("failure_lifecycle", List.of(input(1)), List.of(), List.of());
        FailureRequirementCache cache = new FailureRequirementCache();
        cache.prepare(List.of(recipe), 1L);
        List<MachineRequirement> first = cache.resolve(recipe);
        cache.prepare(List.of(recipe), 2L);
        List<MachineRequirement> reloaded = cache.resolve(recipe);
        assertThat(reloaded).isNotSameAs(first);

        cache.prepare(List.of(), 2L);
        cache.prepare(List.of(recipe), 2L);
        List<MachineRequirement> restored = cache.resolve(recipe);
        assertThat(restored).isNotSameAs(reloaded);

        cache.clear();
        cache.prepare(List.of(recipe), 2L);
        assertThat(cache.resolve(recipe)).isNotSameAs(restored);
    }

    @Test
    void compatibility_requirements_keep_fresh_views_and_their_native_semantics() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            for (IOType io : IOType.values()) {
                List<String> tags = List.of("compat_wakeup");
                List<MachineRequirement> requirements = List.of(
                        new SourceRequirement(io, 12L, 0.5F, 0.25F, tags),
                        new ManaRequirement(io, 12L, 0.5F, 0.25F, tags),
                        new AirRequirement(io, 12L, io == IOType.INPUT ? 2F : 0F, 0.5F, 0.25F, tags),
                        io == IOType.INPUT ? StressRequirement.input(12D, 16D, tags)
                                : StressRequirement.output(12D, 16D, tags),
                        new LightningRequirement(LightningTier.HIGH_VOLTAGE, io, 12L,
                                io == IOType.INPUT ? 1F : 0.5F, tags),
                        new LightningRequirement(LightningTier.EXTREME_HIGH_VOLTAGE, io, 12L,
                                io == IOType.INPUT ? 1F : 0.5F, tags));
                for (MachineRequirement requirement : requirements) {
                    if (RequirementHandlerRegistry.typeFor(requirement.type().id()) == null) {
                        RequirementHandlerRegistry.register(requirement.type());
                    }
                    MachineRecipe recipe = recipe("failure_view_compat", List.of(requirement), List.of(), List.of());
                    FailureRequirementCache cache = new FailureRequirementCache();
                    cache.prepare(List.of(recipe), 1L);

                    List<MachineRequirement> first = cache.resolve(recipe);
                    List<MachineRequirement> second = cache.resolve(recipe);

                    assertThat(second).isNotSameAs(first).containsExactlyElementsOf(first);
                    assertThat(first).containsExactlyElementsOf(recipe.runtimeRequirements());
                    assertThat(recipe.requirements()).containsExactly(requirement);
                }
            }
        }
    }

    @Test
    void custom_requirement_modifiers_are_not_cached_between_failures() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            AtomicInteger modifiers = new AtomicInteger();
            AtomicReference<RequirementType<TestRequirement>> registered = new AtomicReference<>();
            RequirementType<TestRequirement> type = new RequirementType.Definition<>(MMCR.id("failure_view_custom"),
                    MapCodec.unit(() -> new TestRequirement(registered.get())), new RequirementHandler<>() {
                @Override
                public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                            PlanningContext context) {
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }

                @Override
                public TestRequirement applyModifiers(TestRequirement requirement, List<RecipeModifier> values) {
                    modifiers.incrementAndGet();
                    return requirement;
                }
            }, requirement -> requirement);
            registered.set(type);
            RequirementHandlerRegistry.register(type);
            MachineRecipe recipe = recipe("failure_view_custom", List.of(new TestRequirement(type)), List.of(), List.of());
            FailureRequirementCache cache = new FailureRequirementCache();
            cache.prepare(List.of(recipe), 1L);

            List<MachineRequirement> first = cache.resolve(recipe);
            assertThat(cache.resolve(recipe)).isNotSameAs(first);
            assertThat(modifiers).hasValue(2);
        }
    }

    @Test
    void custom_outputs_returning_native_requirements_are_derived_afresh() {
        try (var ignored = OutputRegistry.openTestScope()) {
            AtomicInteger conversions = new AtomicInteger();
            AtomicReference<OutputType<TestOutput>> registered = new AtomicReference<>();
            var id = MMCR.id("failure_view_custom_output");
            OutputType<TestOutput> type = new OutputType.Definition<>(id,
                    MapCodec.unit(() -> new TestOutput(registered.get(), 1F)),
                    (output, chance) -> new TestOutput(output.outputType(), chance),
                    (output, modifiers) -> output,
                    output -> new TestOutput(output.outputType(), output.chance()),
                    OutputType.Presentation.defaults(id), id.toString(),
                    (output, tags) -> new ItemRequirement(IOType.OUTPUT, null, 0,
                            new ItemStack(Items.IRON_INGOT, conversions.incrementAndGet())),
                    requirement -> false);
            registered.set(type);
            OutputRegistry.register(type);
            MachineRecipe recipe = recipe("failure_view_custom_output", List.of(),
                    List.of(new TestOutput(type, 1F)), List.of());
            FailureRequirementCache cache = new FailureRequirementCache();
            cache.prepare(List.of(recipe), 1L);
            conversions.set(0);

            List<MachineRequirement> first = cache.resolve(recipe);
            List<MachineRequirement> second = cache.resolve(recipe);

            assertThat(second).isNotSameAs(first);
            assertThat(conversions).hasValue(2);
            assertThat(((ItemRequirement) first.getFirst()).stack().getCount()).isEqualTo(1);
            assertThat(((ItemRequirement) second.getFirst()).stack().getCount()).isEqualTo(2);
        }
    }

    @Test
    void empty_declared_outputs_do_not_enumerate_custom_output_matchers() {
        try (var ignored = OutputRegistry.openTestScope()) {
            AtomicInteger matches = new AtomicInteger();
            AtomicReference<OutputType<TestOutput>> registered = new AtomicReference<>();
            var id = MMCR.id("failure_empty_outputs");
            OutputType<TestOutput> type = new OutputType.Definition<>(id,
                    MapCodec.unit(() -> new TestOutput(registered.get(), 1F)),
                    (output, chance) -> new TestOutput(output.outputType(), chance), (output, modifiers) -> output,
                    output -> output, OutputType.Presentation.defaults(id), id.toString(),
                    (output, tags) -> new ItemRequirement(IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_INGOT)),
                    requirement -> { matches.incrementAndGet(); return false; });
            registered.set(type);
            OutputRegistry.register(type);
            MachineRecipe recipe = recipe("failure_empty_outputs", List.of(input(1)), List.of(), List.of());
            matches.set(0);
            assertThat(recipe.runtimeRequirements(List.of())).hasSize(1);
            assertThat(recipe.runtimeRequirements(List.of())).hasSize(1);
            assertThat(matches).hasValue(0);
        }
    }

    @Test
    void native_chemical_and_heat_views_are_reused_but_replaced_delegates_stay_live() {
        boolean chemicalNative = LoadedChemicalRequirement.supportsSearchSnapshot();
        boolean heatNative = LoadedHeatRequirement.supportsSearchSnapshot();
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            if (RequirementHandlerRegistry.typeFor(LoadedChemicalRequirement.TYPE.id()) == null)
                RequirementHandlerRegistry.register(LoadedChemicalRequirement.TYPE);
            if (RequirementHandlerRegistry.typeFor(LoadedHeatRequirement.TYPE.id()) == null)
                RequirementHandlerRegistry.register(LoadedHeatRequirement.TYPE);
            LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
            LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
            MachineRecipe recipe = recipe("failure_native_compat", List.of(
                    LoadedChemicalRequirement.input(ChemicalIngredient.chemical(MMCR.id("test_chemical"), 10L)),
                    LoadedHeatRequirement.minimumTemperature(400D)), List.of(), List.of());
            FailureRequirementCache cache = new FailureRequirementCache();
            cache.prepare(List.of(recipe), 1L);
            List<MachineRequirement> first = cache.resolve(recipe);
            assertThat(cache.resolve(recipe)).isSameAs(first);
            AtomicInteger modifiers = new AtomicInteger();
            LoadedHeatRequirement.installHandler(new RequirementHandler<>() {
                @Override public RequirementPlan plan(LoadedHeatRequirement requirement,
                        List<MachineCapability> capabilities, PlanningContext context) {
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }
                @Override public LoadedHeatRequirement applyModifiers(LoadedHeatRequirement requirement, List<RecipeModifier> values) {
                    return LoadedHeatRequirement.minimumTemperature(500D + modifiers.incrementAndGet());
                }
            });
            var changed = cache.resolve(recipe);
            var changedAgain = cache.resolve(recipe);
            assertThat(changed).isNotSameAs(first).isNotSameAs(changedAgain);
            assertThat(((LoadedHeatRequirement) changed.get(1)).heat().value()).isEqualTo(501D);
            assertThat(((LoadedHeatRequirement) changedAgain.get(1)).heat().value()).isEqualTo(502D);
        } finally {
            if (chemicalNative) LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
            else LoadedChemicalRequirement.installUnavailableHandler();
            if (heatNative) LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
            else LoadedHeatRequirement.installUnavailableHandler();
        }
    }

    private static ItemRequirement input(int count) {
        return new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), count, ItemStack.EMPTY);
    }

    private static MachineRecipe recipe(String id, List<MachineRequirement> requirements, List<MachineOutput> outputs,
                                        List<RecipeModifier> modifiers) {
        return MachineRecipe.fromCanonical(MMCR.id(id), MMCR.id("test_cube"), 20, requirements, outputs, modifiers,
                0, 1, false, false, false, Set.of());
    }

    /** @author howxu <dev@howxu.cn> */
    private record TestRequirement(RequirementType<TestRequirement> type) implements MachineRequirement {
        @Override
        public IOType io() { return IOType.INPUT; }
    }

    /** @author howxu <dev@howxu.cn> */
    private record TestOutput(OutputType<TestOutput> outputType, float chance) implements MachineOutput { }
}
