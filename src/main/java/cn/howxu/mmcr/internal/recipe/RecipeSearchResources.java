package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;

import java.util.Arrays;
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
    private final Map<ResourceLocation, Set<AsyncResourceValue>> universe = new HashMap<>();
    private final Map<ResourceLocation, Map<ResourceLocation, Set<AsyncResourceValue>>> byId = new HashMap<>();
    private final Map<Object, List<ResourceLocation>> memberships = new IdentityHashMap<>();
    // Ingredient contracts prohibit mutation of their arguments.
    private final Map<AsyncResourceValue, ItemStack> itemStacks = new HashMap<>();
    private final Map<AsyncResourceValue, FluidStack> fluidStacks = new HashMap<>();

    void add(ResourceLocation type, AsyncResourceValue value) {
        if (!universe.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(value)) return;
        byId.computeIfAbsent(type, ignored -> new HashMap<>())
                .computeIfAbsent(value.resourceId(), ignored -> new LinkedHashSet<>()).add(value);
    }

    Set<AsyncResourceValue> all(ResourceLocation type) {
        return universe.getOrDefault(type, Set.of());
    }

    Set<AsyncResourceValue> matching(ItemRequirement requirement) {
        Ingredient ingredient = requirement.item();
        if (ingredient == null) return Set.of();
        boolean indexed = ingredient.getClass() == Ingredient.class && !ingredient.isCustom();
        // Native 1.21.1 test() itself uses getItems(), including its tag invalidation and empty-tag behavior.
        Set<AsyncResourceValue> candidates = indexed ? selected(requirement.type().id(),
                memberships.computeIfAbsent(ingredient, ignored -> Arrays.stream(ingredient.getItems())
                        .map(ItemStack::getItem).map(BuiltInRegistries.ITEM::getKey).toList())) : all(requirement.type().id());
        if (indexed && requirement.components().isEmpty()) return Set.copyOf(candidates);
        Set<AsyncResourceValue> accepted = new HashSet<>();
        for (AsyncResourceValue value : candidates) {
            ItemStack stack = itemStacks.computeIfAbsent(value, NativeAsyncResourceValues::item);
            if ((indexed || ingredient.test(stack)) && requirement.components().matches(stack)) accepted.add(value);
        }
        return Set.copyOf(accepted);
    }

    Set<AsyncResourceValue> matching(FluidRequirement requirement) {
        var ingredient = requirement.fluid();
        if (ingredient == null) return Set.of();
        if (ingredient.getClass() == SingleFluidIngredient.class) {
            return Set.copyOf(selected(requirement.type().id(), memberships.computeIfAbsent(ingredient, ignored -> {
                var fluid = ((SingleFluidIngredient) ingredient).fluid().value();
                return ingredient.test(new FluidStack(fluid, 1)) ? List.of(BuiltInRegistries.FLUID.getKey(fluid)) : List.of();
            })));
        }
        if (ingredient.getClass() == TagFluidIngredient.class) {
            return Set.copyOf(selected(requirement.type().id(), memberships.computeIfAbsent(ingredient, ignored -> {
                var tag = ((TagFluidIngredient) ingredient).tag();
                return BuiltInRegistries.FLUID.getTag(tag).stream().flatMap(HolderSet::stream).map(Holder::value)
                        .filter(fluid -> fluid.builtInRegistryHolder().is(tag)).map(BuiltInRegistries.FLUID::getKey).toList();
            })));
        }
        Set<AsyncResourceValue> accepted = new HashSet<>();
        for (AsyncResourceValue value : all(requirement.type().id())) {
            if (ingredient.test(fluidStacks.computeIfAbsent(value, NativeAsyncResourceValues::fluid))) accepted.add(value);
        }
        return Set.copyOf(accepted);
    }

    private Set<AsyncResourceValue> selected(ResourceLocation type, List<ResourceLocation> ids) {
        Map<ResourceLocation, Set<AsyncResourceValue>> buckets = byId.getOrDefault(type, Map.of());
        Set<AsyncResourceValue> result = new LinkedHashSet<>();
        for (ResourceLocation id : ids) result.addAll(buckets.getOrDefault(id, Set.of()));
        return result;
    }
}
