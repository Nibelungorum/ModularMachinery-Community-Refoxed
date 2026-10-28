package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.recipe.EffectiveRecipeSet;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveRecipeResolverTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void resolves_bounded_duration_threads_and_parallelized_rule() {
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("effective_recipe"), MMCR.id("pool"), 20,
                List.of(), List.of(), List.of(), 0, 8, false, true, false, Set.of());
        ControllerRuntimeSnapshot snapshot = new ControllerRuntimeSnapshot(
                StructureSnapshot.empty(), 2L, 3L, 4L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.disconnected(), 0, CraftingStateSnapshot.empty(0L, 2L, 3L),
                FactorySnapshot.empty(), List.of(), List.of(), List.of(), "", "", 0,
                false, false, 0, 0, 16L);

        EffectiveRecipe effective = new EffectiveRecipeResolver().resolve(recipe, snapshot, List.of(
                MachineModifier.numeric("duration", "input", 0D, "multiply", false),
                MachineModifier.numeric("parallelism", "machine", 4D, "multiply", false),
                MachineModifier.numeric("factory_threads", "machine", 2D, "add", false),
                MachineModifier.numeric("recipe_threads", "recipe", 2D, "add", false),
                MachineModifier.parallelized(false)));

        assertThat(effective.duration()).isEqualTo(1);
        assertThat(effective.recipeThreadLimit()).isEqualTo(10);
        assertThat(effective.parallelized()).isFalse();
        assertThat(effective.parallelismLimit()).isEqualTo(1L);
        assertThat(effective.modifierVersion()).isEqualTo(3L);
        assertThat(effective.factoryThreadLimit()).isEqualTo(1);
    }

    @Test
    void zero_recipe_thread_limit_remains_unbounded_before_modifiers_are_applied() {
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("unbounded_recipe_threads"), MMCR.id("pool"), 20,
                List.of(), List.of(), List.of(), 0, 0, false, true, false, Set.of());

        EffectiveRecipe effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(3L), List.of());

        assertThat(effective.recipeThreadLimit()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void controller_wide_parallelism_is_not_modified_twice_per_recipe() {
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("single_parallel_modifier_application"),
                MMCR.id("pool"), 20, List.of(), List.of(), List.of(), 0, 1, false, true, false, Set.of());

        EffectiveRecipe effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(3L), List.of(
                MachineModifier.numeric("parallelism", "machine", 4D, "multiply", false)));

        assertThat(effective.parallelismLimit()).isEqualTo(16L);
    }

    @Test
    void controller_cache_reuses_one_effective_set_until_a_version_changes() {
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("cached_effective_recipe"), MMCR.id("pool"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, true, false, Set.of());
        ControllerRuntimeSnapshot firstSnapshot = snapshot(3L);
        EffectiveRecipeSet.Cache cache = new EffectiveRecipeSet.Cache();

        EffectiveRecipeSet first = cache.resolve(firstSnapshot, 7L, List.of(recipe), List.of());
        EffectiveRecipeSet reused = cache.resolve(firstSnapshot, 7L, List.of(recipe), List.of());
        EffectiveRecipeSet changed = cache.resolve(snapshot(4L), 7L, List.of(recipe), List.of());

        assertThat(reused).isSameAs(first);
        assertThat(changed).isNotSameAs(first);
    }

    @Test
    void controller_cache_reuses_catalog_recipes_across_candidate_subsets_and_direct_limit_reads() {
        var pool = MMCR.id("controller_tick");
        MachineRecipe firstRecipe = MachineRecipe.fromCanonical(MMCR.id("effective_subset_a"), pool, 20,
                List.of(), List.of(), List.of(), 0, 1, false, true, false, Set.of());
        MachineRecipe secondRecipe = firstRecipe.withId(MMCR.id("effective_subset_b"));
        RecipeRegistry.replaceDynamic(Map.of(firstRecipe.id(), firstRecipe, secondRecipe.id(), secondRecipe));
        try {
            long catalog = RecipeRegistry.catalogForPool(pool).version();
            EffectiveRecipeSet.Cache cache = new EffectiveRecipeSet.Cache();
            ControllerRuntimeSnapshot snapshot = snapshot(3L);
            EffectiveRecipeSet first = cache.resolve(snapshot, catalog, List.of(firstRecipe, secondRecipe), List.of());
            EffectiveRecipeSet subset = cache.resolve(snapshot, catalog, List.of(firstRecipe), List.of());
            EffectiveRecipeSet last = cache.resolve(snapshot, catalog, List.of(firstRecipe, secondRecipe), List.of());
            assertThat(first.recipes().get(0)).isSameAs(subset.recipes().getFirst());
            assertThat(first.recipes().get(0)).isSameAs(last.recipes().get(0));
            assertThat(first.recipes().get(1)).isSameAs(last.recipes().get(1));
            assertThat(cache.resolveOne(snapshot, catalog, firstRecipe, List.of())).isSameAs(first.recipes().get(0));
            ControllerRuntimeSnapshot larger = snapshot.withRuntimeState(snapshot.crafting(), snapshot.factory(),
                    snapshot.capabilityPresentations(), 32L, snapshot.recipePresentation());
            assertThat(cache.resolveOne(larger, catalog, firstRecipe, List.of()))
                    .isNotSameAs(first.recipes().get(0))
                    .extracting(EffectiveRecipe::parallelismLimit).isEqualTo(32L);
            assertThat(cache.resolveOne(larger, catalog, firstRecipe.withId(firstRecipe.id()), List.of()))
                    .isNotSameAs(cache.resolveOne(larger, catalog, firstRecipe, List.of()));
        } finally {
            RecipeRegistry.replaceDynamic(Map.of());
        }
    }

    private static ControllerRuntimeSnapshot snapshot(long modifierVersion) {
        return new ControllerRuntimeSnapshot(StructureSnapshot.empty(), 2L, modifierVersion, 4L,
                Map.of(), Map.of(), Set.of(), ModuleConnectionStatus.disconnected(), 0,
                CraftingStateSnapshot.empty(0L, 2L, modifierVersion), FactorySnapshot.empty(), List.of(), List.of(),
                List.of(), "", "", 0, false, false, 0, 0, 16L);
    }
}
