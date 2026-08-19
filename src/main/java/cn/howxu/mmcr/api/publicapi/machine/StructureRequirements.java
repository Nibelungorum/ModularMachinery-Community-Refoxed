package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Level slot and modifier declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record StructureRequirements(
        Map<Character, List<ModifierReplacement>> modifierReplacements,
        Map<Character, Identifier> levelSlots) {

    public static final StructureRequirements EMPTY = new StructureRequirements(Map.of(), Map.of());

    public StructureRequirements {
        modifierReplacements = copyModifierMap(modifierReplacements == null ? Map.of() : modifierReplacements);
        levelSlots = Collections.unmodifiableMap(new LinkedHashMap<>(levelSlots == null ? Map.of() : levelSlots));
        levelSlots.forEach((symbol, typeId) -> {
            Objects.requireNonNull(symbol, "level symbol");
            Objects.requireNonNull(typeId, "level typeId");
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    public record ModifierReplacement(
            String modifierName,
            BlockPredicate replacement,
            List<RecipeModifier> modifiers,
            ItemStack descriptiveStack) {

        public ModifierReplacement {
            Objects.requireNonNull(replacement, "replacement");
            modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
            descriptiveStack = descriptiveStack == null ? ItemStack.EMPTY : descriptiveStack.copy();
        }

        @Override
        public ItemStack descriptiveStack() {
            return descriptiveStack.copy();
        }
    }

    /**
     * Builder for level slot and modifier declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private final Map<Character, List<ModifierReplacement>> modifiers = new LinkedHashMap<>();
        private final Map<Character, Identifier> levelSlots = new LinkedHashMap<>();

        public Builder modifier(char symbol, BlockPredicate replacement, List<RecipeModifier> modifiers, ItemStack descriptiveStack) {
            return modifier(symbol, null, replacement, modifiers, descriptiveStack);
        }

        public Builder modifier(char symbol, String modifierName, BlockPredicate replacement,
                List<RecipeModifier> modifiers, ItemStack descriptiveStack) {
            this.modifiers.computeIfAbsent(symbol, ignored -> new ArrayList<>())
                    .add(new ModifierReplacement(modifierName, replacement, modifiers, descriptiveStack));
            return this;
        }

        public Builder levelSlot(char symbol, Identifier typeId) {
            Objects.requireNonNull(typeId, "typeId");
            Identifier existing = levelSlots.putIfAbsent(symbol, typeId);
            if (existing != null && !existing.equals(typeId)) {
                throw new IllegalArgumentException("conflicting level slot for symbol " + symbol);
            }
            return this;
        }

        public StructureRequirements build() {
            return new StructureRequirements(modifiers, levelSlots);
        }
    }

    private static Map<Character, List<ModifierReplacement>> copyModifierMap(Map<Character, List<ModifierReplacement>> source) {
        Map<Character, List<ModifierReplacement>> copy = new LinkedHashMap<>();
        source.forEach((symbol, replacements) -> {
            Objects.requireNonNull(symbol, "modifier symbol");
            copy.put(symbol, List.copyOf(replacements));
        });
        return Collections.unmodifiableMap(copy);
    }
}
