package cn.howxu.mmcr.api.registration;

import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.modifier.ModifierStacking;
import cn.howxu.mmcr.api.recipe.modifier.ModifierItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Canonical collector for machine declarations.
 * @author howxu <dev@howxu.cn>
 */
public class MachineDefinitionRegistration {
    private final Map<Identifier, MachineDefinition> definitions = new LinkedHashMap<>();
    private final Map<Identifier, ModifierDefinition> modifiers = new LinkedHashMap<>();
    private final Map<Identifier, List<ItemStack>> modifierItems = new LinkedHashMap<>();
    private final Map<Identifier, ModifierStacking> stacking = new LinkedHashMap<>();
    private final Map<Identifier, Integer> modifierItemLimits = new LinkedHashMap<>();
    private final Map<ModifierItemKey, Identifier> modifierItemKeys = new LinkedHashMap<>();
    private final Map<Identifier, Identifier> modifierItemIds = new LinkedHashMap<>();
    private boolean frozen;

    public void registerMachine(Identifier id, UnaryOperator<MachineBuilder> consumer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(consumer, "consumer");
        requireAvailable(id);
        String phase = "configuration";
        try {
            MachineBuilder builder = Objects.requireNonNull(consumer.apply(MachineBuilder.machine(id)),
                    "consumer result");
            phase = "build";
            registerMachine(builder.build());
        } catch (RuntimeException exception) {
            throw new ApiRegistrationException("Invalid machine " + id + " during " + phase + ": "
                    + exception.getMessage(), exception);
        }
    }

    public void registerMachine(MachineDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        requireAvailable(definition.id());
        definitions.put(definition.id(), definition);
    }

    private void requireAvailable(Identifier id) {
        if (frozen) throw new ApiRegistrationException("Machine definitions are frozen during registration: " + id);
        if (definitions.containsKey(id)) throw new ApiRegistrationException("Duplicate machine during registration: " + id);
    }

    public Map<Identifier, MachineDefinition> definitions() {
        return immutable(definitions);
    }

    public void freeze() {
        frozen = true;
    }

    public void registerModifier(Identifier id, ModifierDefinition definition) {
        registerModifier(id, definition, ModifierStacking.LEGACY);
    }

    public void registerModifier(Identifier id, ModifierDefinition definition, ModifierStacking policy) {
        requireOpen();
        require(policy, "modifier stacking");
        require(id, "modifier id");
        require(definition, "modifier definition");
        if (modifiers.putIfAbsent(id, definition) != null) {
            throw new ApiRegistrationException("Duplicate machine modifier: " + id);
        }
        if (policy != ModifierStacking.LEGACY) stacking.put(id, policy);
    }

    public void registerModifierItem(ItemStack stack, Identifier modifierId) {
        requireOpen();
        require(stack, "modifier item");
        require(modifierId, "modifier id");
        ItemStack normalized = stack.copyWithCount(1);
        ModifierItemKey key = ModifierItemKey.of(normalized);
        if (modifierItemKeys.putIfAbsent(key, modifierId) != null) {
            throw new ApiRegistrationException("Duplicate machine modifier item binding");
        }
        modifierItems.computeIfAbsent(modifierId, ignored -> new ArrayList<>()).add(normalized);
    }

    /** Records an item ID without constructing a stack before default components are bound. */
    public void registerModifierItem(Identifier itemId, Identifier modifierId) {
        requireOpen();
        require(itemId, "modifier item id");
        require(modifierId, "modifier id");
        if (modifierItemIds.putIfAbsent(itemId, modifierId) != null) {
            throw new ApiRegistrationException("Duplicate machine modifier item binding: " + itemId);
        }
    }

    public void limitModifierItems(Identifier modifierId, int maxCount) {
        requireOpen();
        require(modifierId, "modifier id");
        if (maxCount < 1) throw new ApiRegistrationException(
                "Modifier item limit for " + modifierId + " must be positive: " + maxCount);
        if (modifierItemLimits.putIfAbsent(modifierId, maxCount) != null) {
            throw new ApiRegistrationException("Duplicate modifier item limit: " + modifierId);
        }
    }

    public Map<Identifier, ModifierDefinition> modifiers() {
        return immutable(modifiers);
    }

    public Map<Identifier, List<ItemStack>> modifierItems() {
        return immutableItemBindings(modifierItems);
    }

    public Map<Identifier, Identifier> modifierItemIds() {
        return immutable(modifierItemIds);
    }

    /** Merge the Java window without resolving references to declarations in later startup scripts. */
    public void collectFrom(MachineDefinitionRegistration source) {
        requireOpen();
        source.definitions.values().forEach(this::registerMachine);
        source.modifiers.forEach((id, definition) ->
                registerModifier(id, definition, source.stacking.getOrDefault(id, ModifierStacking.LEGACY)));
        source.modifierItems.forEach((id, stacks) -> stacks.forEach(stack -> registerModifierItem(stack, id)));
        source.modifierItemIds.forEach(this::registerModifierItem);
        source.modifierItemLimits.forEach(this::limitModifierItems);
    }

    /** Link forward references only after Java and KubeJS definition windows have both finished. */
    public ModifierSnapshot modifierSnapshot() {
        modifierItems.keySet().forEach(modifierId -> {
            if (!modifiers.containsKey(modifierId)) throw new ApiRegistrationException(
                    "Modifier item binding refers to unknown machine modifier " + modifierId);
        });
        modifierItemIds.forEach((itemId, modifierId) -> {
            if (!modifiers.containsKey(modifierId)) throw new ApiRegistrationException(
                    "Modifier item " + itemId + " refers to unknown machine modifier " + modifierId);
        });
        modifierItemLimits.keySet().forEach(modifierId -> {
            if (!modifiers.containsKey(modifierId)) throw new ApiRegistrationException(
                    "Modifier item limit refers to unknown machine modifier " + modifierId);
        });
        return new ModifierSnapshot(modifiers, modifierItems, modifierItemIds, stacking, modifierItemLimits);
    }

    private void requireOpen() {
        if (frozen) throw new ApiRegistrationException("Machine definitions are frozen");
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new ApiRegistrationException(name + " must not be null");
        return value;
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<Identifier, List<ItemStack>> immutableItemBindings(
            Map<Identifier, List<ItemStack>> source) {
        Map<Identifier, List<ItemStack>> copy = new LinkedHashMap<>();
        source.forEach((modifierId, stacks) -> copy.put(modifierId,
                List.copyOf(stacks.stream().map(ItemStack::copy).toList())));
        return Collections.unmodifiableMap(copy);
    }

    /** Frozen modifier declarations; item IDs remain unresolved until components are bound.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    public record ModifierSnapshot(Map<Identifier, ModifierDefinition> modifiers,
            Map<Identifier, List<ItemStack>> modifierItems, Map<Identifier, Identifier> modifierItemIds,
            Map<Identifier, ModifierStacking> stacking, Map<Identifier, Integer> modifierItemLimits) {
        public ModifierSnapshot {
            modifiers = immutable(modifiers);
            modifierItems = immutableItemBindings(modifierItems);
            modifierItemIds = immutable(modifierItemIds);
            stacking = immutable(stacking);
            modifierItemLimits = immutable(modifierItemLimits);
        }

        @Override
        public Map<Identifier, List<ItemStack>> modifierItems() {
            return immutableItemBindings(modifierItems);
        }

        /** Must only be called once item default components have been bound. */
        public Map<Identifier, List<ItemStack>> resolveModifierItems() {
            Map<Identifier, List<ItemStack>> resolved = new LinkedHashMap<>();
            modifierItems().forEach((id, stacks) -> resolved.put(id, new ArrayList<>(stacks)));
            modifierItemIds.forEach((itemId, modifierId) -> {
                var item = BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow(() ->
                        new ApiRegistrationException("Machine modifier " + modifierId
                                + " refers to unknown item " + itemId));
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) throw new ApiRegistrationException(
                        "Machine modifier " + modifierId + " cannot bind empty item " + itemId);
                resolved.computeIfAbsent(modifierId, ignored -> new ArrayList<>()).add(stack);
            });
            return immutableItemBindings(resolved);
        }
    }
}
