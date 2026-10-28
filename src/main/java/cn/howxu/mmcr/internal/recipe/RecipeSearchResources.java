package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SimpleFluidIngredient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Capture-local resource index. Native objects and predicates never enter the worker snapshot.
 * @author howxu <dev@howxu.cn>
 */
final class RecipeSearchResources {
    private final Map<Identifier, Set<AsyncResourceValue>> universe = new HashMap<>();
    private final Map<Identifier, Map<Identifier, Set<AsyncResourceValue>>> byId = new HashMap<>();
    private final Map<Object, List<Identifier>> memberships = new IdentityHashMap<>();
    // Ingredient contracts prohibit mutation of their arguments.
    private final Map<AsyncResourceValue, ItemStack> itemStacks = new HashMap<>();
    private final Map<AsyncResourceValue, FluidStack> fluidStacks = new HashMap<>();

    void add(Identifier type, AsyncResourceValue value) {
        if (!universe.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(value)) return;
        byId.computeIfAbsent(type, ignored -> new HashMap<>())
                .computeIfAbsent(value.resourceId(), ignored -> new LinkedHashSet<>()).add(value);
    }

    Set<AsyncResourceValue> all(Identifier type) {
        return universe.getOrDefault(type, Set.of());
    }

    Set<AsyncResourceValue> matching(ItemRequirement requirement) {
        Ingredient ingredient = requirement.item();
        if (ingredient == null) return Set.of();
        boolean indexed = ingredient.getClass() == Ingredient.class && !ingredient.isCustom()
                && enumerable(ingredient.getValues());
        Set<AsyncResourceValue> candidates = indexed ? selected(requirement.type().id(),
                memberships.computeIfAbsent(ingredient, ignored -> ingredient.getValues().stream().map(Holder::value)
                        .filter(item -> ingredient.getValues().contains(item.builtInRegistryHolder()))
                        .map(BuiltInRegistries.ITEM::getKey).toList())) : all(requirement.type().id());
        if (indexed && requirement.components().isEmpty()) return Set.copyOf(candidates);
        Set<AsyncResourceValue> accepted = new HashSet<>();
        for (AsyncResourceValue value : candidates) {
            ItemStack stack = itemStacks.computeIfAbsent(value, key -> NativeAsyncResourceValues.item(key).toStack(1));
            if ((indexed || ingredient.test(stack)) && requirement.components().matches(stack)) accepted.add(value);
        }
        return Set.copyOf(accepted);
    }

    Set<AsyncResourceValue> matching(FluidRequirement requirement) {
        var ingredient = requirement.fluid();
        if (ingredient == null) return Set.of();
        if (ingredient.getClass() == SimpleFluidIngredient.class
                && enumerable(((SimpleFluidIngredient) ingredient).fluidSet())) {
            return Set.copyOf(selected(requirement.type().id(), memberships.computeIfAbsent(ingredient,
                    ignored -> ((SimpleFluidIngredient) ingredient).fluidSet().stream().map(Holder::value)
                            .filter(fluid -> ((SimpleFluidIngredient) ingredient).fluidSet().contains(fluid.builtInRegistryHolder()))
                            .map(BuiltInRegistries.FLUID::getKey).toList())));
        }
        Set<AsyncResourceValue> accepted = new HashSet<>();
        for (AsyncResourceValue value : all(requirement.type().id())) {
            if (ingredient.test(fluidStacks.computeIfAbsent(value,
                    key -> NativeAsyncResourceValues.fluid(key).toStack(1)))) accepted.add(value);
        }
        return Set.copyOf(accepted);
    }

    private Set<AsyncResourceValue> selected(Identifier type, List<Identifier> ids) {
        Map<Identifier, Set<AsyncResourceValue>> buckets = byId.getOrDefault(type, Map.of());
        Set<AsyncResourceValue> result = new LinkedHashSet<>();
        for (Identifier id : ids) result.addAll(buckets.getOrDefault(id, Set.of()));
        return result;
    }

    private static boolean enumerable(HolderSet<?> values) {
        return values.isBound() && (values instanceof HolderSet.Direct<?>
                || values.getClass() == HolderSet.Named.class);
    }
}
