package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModifierRegistry {

    private static final Map<String, AbstractModifierReplacement> REPLACEMENTS = new HashMap<>();
    private static Map<ResourceLocation, ModifierDefinition> DEFINITIONS = Map.of();
    private static Map<ModifierItemKey, ResourceLocation> ITEM_BINDINGS = Map.of();
    private static Map<ResourceLocation, List<ItemStack>> ITEM_BINDING_SNAPSHOT = Map.of();

    private ModifierRegistry() {
    }

    static void register(AbstractModifierReplacement replacement) {
        if (replacement == null) return;
        REPLACEMENTS.put(replacement.getModifierName(), replacement);
    }

    public static AbstractModifierReplacement get(String name) {
        return REPLACEMENTS.get(name);
    }

    public static List<AbstractModifierReplacement> all() {
        return List.copyOf(REPLACEMENTS.values());
    }

    static void clear() {
        REPLACEMENTS.clear();
        DEFINITIONS = Map.of();
        ITEM_BINDINGS = Map.of();
        ITEM_BINDING_SNAPSHOT = Map.of();
    }

    public static void installSnapshot(Map<ResourceLocation, ModifierDefinition> definitions) {
        installSnapshot(definitions, Map.of());
    }

    public static void installSnapshot(Map<ResourceLocation, ModifierDefinition> definitions,
            Map<ResourceLocation, List<ItemStack>> itemBindings) {
        Map<ResourceLocation, ModifierDefinition> nextDefinitions = Map.copyOf(
                definitions == null ? Map.of() : definitions);
        Map<ModifierItemKey, ResourceLocation> nextBindings = new LinkedHashMap<>();
        Map<ResourceLocation, List<ItemStack>> nextSnapshot = new LinkedHashMap<>();
        if (itemBindings != null) {
            itemBindings.forEach((modifierId, stacks) -> {
                if (!nextDefinitions.containsKey(modifierId)) {
                    throw new ApiRegistrationException("Modifier item binding refers to unknown machine modifier "
                            + modifierId);
                }
                Objects.requireNonNull(stacks, "item bindings");
                List<ItemStack> copies = stacks.stream().map(stack -> {
                    ItemStack copy = Objects.requireNonNull(stack, "item binding").copy();
                    ModifierItemKey key = ModifierItemKey.of(copy);
                    if (nextBindings.putIfAbsent(key, modifierId) != null) {
                        throw new ApiRegistrationException("Duplicate machine modifier item binding");
                    }
                    return copy;
                }).toList();
                nextSnapshot.put(modifierId, List.copyOf(copies));
            });
        }
        DEFINITIONS = nextDefinitions;
        ITEM_BINDINGS = Collections.unmodifiableMap(new LinkedHashMap<>(nextBindings));
        ITEM_BINDING_SNAPSHOT = immutableItemBindings(nextSnapshot);
    }

    public static ModifierDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Map<ResourceLocation, ModifierDefinition> definitions() {
        return DEFINITIONS;
    }

    public static @Nullable ResourceLocation modifierFor(ItemStack stack) {
        if (stack == null) return null;
        return ITEM_BINDINGS.get(ModifierItemKey.of(stack));
    }

    public static Map<ResourceLocation, List<ItemStack>> modifierItems() {
        return immutableItemBindings(ITEM_BINDING_SNAPSHOT);
    }

    private static Map<ResourceLocation, List<ItemStack>> immutableItemBindings(
            Map<ResourceLocation, List<ItemStack>> source) {
        Map<ResourceLocation, List<ItemStack>> copy = new LinkedHashMap<>();
        source.forEach((modifierId, stacks) -> copy.put(modifierId,
                List.copyOf(stacks.stream().map(ItemStack::copy).toList())));
        return Collections.unmodifiableMap(copy);
    }
}
