package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Immutable conservative lookup index for machine recipe input items.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeCandidateIndex {

    private static final RecipeCandidateIndex EMPTY = new RecipeCandidateIndex(Map.of(), Set.of(), List.of());
    private static final AtomicInteger BUILD_COUNT_FOR_TESTING = new AtomicInteger();

    private final Map<Item, Set<MachineRecipe>> exactItemCandidates;
    private final Set<MachineRecipe> fallbackCandidates;
    private final List<MachineRecipe> allCandidates;

    private RecipeCandidateIndex(Map<Item, Set<MachineRecipe>> exactItemCandidates,
                                 Set<MachineRecipe> fallbackCandidates,
                                 List<MachineRecipe> allCandidates) {
        this.exactItemCandidates = exactItemCandidates;
        this.fallbackCandidates = fallbackCandidates;
        this.allCandidates = allCandidates;
    }

    public static RecipeCandidateIndex empty() {
        return EMPTY;
    }

    public static RecipeCandidateIndex build(List<MachineRecipe> recipes) {
        BUILD_COUNT_FOR_TESTING.incrementAndGet();
        if (recipes == null || recipes.isEmpty()) return EMPTY;

        Map<Item, Set<MachineRecipe>> exactItemCandidates = new LinkedHashMap<>();
        Set<MachineRecipe> fallbackCandidates = new LinkedHashSet<>();
        List<MachineRecipe> allCandidates = List.copyOf(recipes);
        for (MachineRecipe recipe : allCandidates) {
            Set<Item> requiredItems;
            try {
                requiredItems = exactInputItems(recipe);
            } catch (RuntimeException exception) {
                throw new MachineRecipeJson.RecipeJsonException(recipe.id(), "requirements",
                        "Candidate index could not inspect recipe requirements", exception);
            }
            if (requiredItems == null) {
                fallbackCandidates.add(recipe);
                continue;
            }
            for (Item item : requiredItems) {
                exactItemCandidates.computeIfAbsent(item, ignored -> new LinkedHashSet<>()).add(recipe);
            }
        }

        Map<Item, Set<MachineRecipe>> immutableCandidates = new LinkedHashMap<>();
        exactItemCandidates.forEach((item, candidates) -> immutableCandidates.put(item, orderedSet(candidates)));
        return new RecipeCandidateIndex(Map.copyOf(immutableCandidates), orderedSet(fallbackCandidates), allCandidates);
    }

    public List<MachineRecipe> candidates(Collection<Item> items) {
        if (allCandidates.isEmpty()) return allCandidates;
        if (items == null) return allCandidates;
        Set<MachineRecipe> matching = new LinkedHashSet<>(fallbackCandidates);
        for (Item item : items) {
            matching.addAll(exactItemCandidates.getOrDefault(item, Set.of()));
        }
        if (matching.size() == allCandidates.size()) return allCandidates;
        return allCandidates.stream().filter(matching::contains).toList();
    }

    public List<MachineRecipe> allCandidates() {
        return allCandidates;
    }

    public static void resetBuildCountForTesting() {
        BUILD_COUNT_FOR_TESTING.set(0);
    }

    public static int buildCountForTesting() {
        return BUILD_COUNT_FOR_TESTING.get();
    }

    private static Set<Item> exactInputItems(MachineRecipe recipe) {
        Set<Item> requiredItems = new LinkedHashSet<>();
        boolean hasItemInput = false;
        for (MachineRequirement requirement : recipe.requirements()) {
            if (!requirement.tags().isEmpty()) return null;
            if (requirement.io() != RecipeModifier.IOType.INPUT) {
                continue;
            }
            if (!(requirement instanceof ItemRequirement item)
                    || !item.components().isEmpty()
                    || item.item() == null) return null;
            hasItemInput = true;
            Ingredient ingredient = item.item();
            if (ingredient.isCustom()) return null;
            HolderSet<Item> matchingItems;
            try {
                matchingItems = ingredient.getValues();
            } catch (IllegalStateException | UnsupportedOperationException ignored) {
                return null;
            }
            if (!matchingItems.isBound() || matchingItems.unwrapKey().isPresent() || matchingItems.size() != 1) return null;
            requiredItems.add(matchingItems.get(0).value());
        }
        return hasItemInput ? requiredItems : null;
    }

    private static <T> Set<T> orderedSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
