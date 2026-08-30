package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

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
        Map<Character, List<ModifierUse>> modifierReplacements,
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

    /**
     * Builder for level slot and modifier declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private final Map<Character, List<ModifierUse>> modifiers = new LinkedHashMap<>();
        private final Map<Character, Identifier> levelSlots = new LinkedHashMap<>();

        public Builder modifier(char symbol, Identifier modifierId) {
            return modifier(symbol, ModifierUse.of(modifierId, BlockPredicate.block(Blocks.AIR)));
        }

        public Builder modifier(char symbol, ModifierUse use) {
            this.modifiers.computeIfAbsent(symbol, ignored -> new ArrayList<>())
                    .add(Objects.requireNonNull(use, "modifier use"));
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

    private static Map<Character, List<ModifierUse>> copyModifierMap(Map<Character, List<ModifierUse>> source) {
        Map<Character, List<ModifierUse>> copy = new LinkedHashMap<>();
        source.forEach((symbol, replacements) -> {
            Objects.requireNonNull(symbol, "modifier symbol");
            copy.put(symbol, List.copyOf(replacements));
        });
        return Collections.unmodifiableMap(copy);
    }
}
