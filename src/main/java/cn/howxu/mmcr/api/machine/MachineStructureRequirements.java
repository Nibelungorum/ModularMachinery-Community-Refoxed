package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable character-level structure modifier and level declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureRequirements(
        Map<Character, List<SingleBlockModifierReplacement>> modifierReplacements,
        Map<Character, Identifier> levelSlots) {

    public static final MachineStructureRequirements EMPTY = new MachineStructureRequirements(Map.of(), Map.of());

    public MachineStructureRequirements {
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

    public static MachineStructureRequirements merge(
            MachineStructureRequirements previous, MachineStructureRequirements extension, int stageNumber) {
        Builder builder = builder();
        previous.modifierReplacements().forEach((symbol, replacements) ->
                replacements.forEach(replacement -> builder.modifier(symbol, replacement)));
        extension.modifierReplacements().forEach((symbol, replacements) ->
                replacements.forEach(replacement -> builder.modifier(symbol, replacement)));
        previous.levelSlots().forEach(builder::levelSlot);
        extension.levelSlots().forEach((symbol, typeId) -> {
            Identifier existing = builder.levelSlots.get(symbol);
            if (existing != null && !existing.equals(typeId)) {
                throw new IllegalArgumentException("stage " + stageNumber
                        + " has conflicting level slot for symbol " + symbol);
            }
            builder.levelSlot(symbol, typeId);
        });
        return builder.build();
    }

    public MachineStructureRequirements validate(BlockArray pattern) {
        Objects.requireNonNull(pattern, "pattern");
        for (Character symbol : modifierReplacements.keySet()) validatePresent(symbol, pattern);
        for (Character symbol : levelSlots.keySet()) validatePresent(symbol, pattern);
        return this;
    }

    private static void validatePresent(Character symbol, BlockArray pattern) {
        if (!pattern.symbolsByPosition().containsValue(symbol)) {
            throw new IllegalArgumentException("Requirement symbol " + symbol + " is absent from structure pattern");
        }
    }

    private static Map<Character, List<SingleBlockModifierReplacement>> copyModifierMap(
            Map<Character, List<SingleBlockModifierReplacement>> source) {
        Map<Character, List<SingleBlockModifierReplacement>> copy = new LinkedHashMap<>();
        source.forEach((symbol, replacements) -> {
            Objects.requireNonNull(symbol, "modifier symbol");
            List<SingleBlockModifierReplacement> values = List.copyOf(replacements);
            values.forEach(replacement -> Objects.requireNonNull(replacement, "replacement"));
            copy.put(symbol, values);
        });
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Builder for character-level structure requirements.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private final Map<Character, List<SingleBlockModifierReplacement>> modifiers = new LinkedHashMap<>();
        private final Map<Character, Identifier> levelSlots = new LinkedHashMap<>();

        public Builder modifier(char symbol, SingleBlockModifierReplacement replacement) {
            Objects.requireNonNull(replacement, "replacement");
            modifiers.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(replacement);
            return this;
        }

        public Builder modifier(char symbol, Identifier modifierId, BlockPredicate replacement) {
            return modifier(symbol, new SingleBlockModifierReplacement(modifierId, replacement));
        }

        public Builder levelSlot(char symbol, Identifier typeId) {
            Objects.requireNonNull(typeId, "typeId");
            Identifier existing = levelSlots.putIfAbsent(symbol, typeId);
            if (existing != null && !existing.equals(typeId)) {
                throw new IllegalArgumentException("conflicting level slot for symbol " + symbol);
            }
            return this;
        }

        public MachineStructureRequirements build() {
            return new MachineStructureRequirements(modifiers, levelSlots);
        }

        public MachineStructureRequirements build(BlockArray pattern) {
            return build().validate(pattern);
        }
    }
}
