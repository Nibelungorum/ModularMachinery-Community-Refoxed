package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable conservative lookup index for machine recipe input items.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeCandidateIndex {

    private static final RecipeCandidateIndex EMPTY = new RecipeCandidateIndex(Map.of(), Set.of(), List.of());

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
        if (recipes == null || recipes.isEmpty()) return EMPTY;

        Map<Item, Set<MachineRecipe>> exactItemCandidates = new LinkedHashMap<>();
        Set<MachineRecipe> fallbackCandidates = new LinkedHashSet<>();
        List<MachineRecipe> allCandidates = List.copyOf(recipes);
        for (MachineRecipe recipe : allCandidates) {
            Set<Item> requiredItems = exactInputItems(recipe);
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
        Set<MachineRecipe> matching = new LinkedHashSet<>(fallbackCandidates);
        if (items != null) {
            for (Item item : items) {
                matching.addAll(exactItemCandidates.getOrDefault(item, Set.of()));
            }
        }
        if (matching.size() == allCandidates.size()) return allCandidates;
        return allCandidates.stream().filter(matching::contains).toList();
    }

    public List<MachineRecipe> allCandidates() {
        return allCandidates;
    }

    private static Set<Item> exactInputItems(MachineRecipe recipe) {
        Set<Item> requiredItems = new LinkedHashSet<>();
        boolean hasItemInput = false;
        for (MachineRequirement requirement : recipe.requirements()) {
            if (!(requirement instanceof ItemRequirement item)
                    || item.io() != RecipeModifier.IOType.INPUT
                    || item.item() == null) {
                continue;
            }
            hasItemInput = true;
            List<net.minecraft.core.Holder<Item>> matchingItems = item.item().items().toList();
            if (matchingItems.size() != 1) return null;
            requiredItems.add(matchingItems.getFirst().value());
        }
        return hasItemInput ? requiredItems : null;
    }

    private static <T> Set<T> orderedSet(Set<T> values) {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
