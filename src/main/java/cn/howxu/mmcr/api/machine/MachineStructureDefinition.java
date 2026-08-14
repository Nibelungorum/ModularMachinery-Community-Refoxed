package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/**
 * Server-reloadable structure data for an already registered startup machine.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureDefinition(Identifier machineId, List<Declaration> declarations) {

    public MachineStructureDefinition {
        if (machineId == null) throw new IllegalArgumentException("machineId null");
        if (declarations == null || declarations.isEmpty()) throw new IllegalArgumentException("declarations empty");
        declarations = List.copyOf(declarations);
    }

    public MachineStructureDefinition(
            Identifier machineId,
            BlockArray pattern,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            Map<BlockPos, Identifier> levelSlots) {
        this(machineId, List.of(new Declaration(Declaration.Kind.FULL, pattern,
                portRequirements == null ? PortRequirementSpec.none() : portRequirements,
                portTierRequirements == null ? PortTierRequirementSpec.none() : portTierRequirements,
                dynamicPatterns, modifierReplacements, levelSlots)));
    }

    public MachineStructureDefinition(
            Identifier machineId,
            BlockArray pattern,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(machineId, pattern, portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, modifierReplacements, Map.of());
    }

    public MachineStructureDefinition(
            Identifier machineId,
            BlockArray pattern,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(machineId, pattern, portRequirements, portTierRequirements, dynamicPatterns, modifierReplacements, Map.of());
    }

    public BlockArray pattern() {
        return declarations.getFirst().pattern();
    }

    public PortRequirementSpec portRequirements() {
        return declarations.getFirst().portRequirements();
    }

    public PortTierRequirementSpec portTierRequirements() {
        return declarations.getFirst().portTierRequirements();
    }

    public List<DynamicPatternSpec> dynamicPatterns() {
        return declarations.getFirst().dynamicPatterns();
    }

    public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements() {
        return declarations.getFirst().modifierReplacements();
    }

    public Map<BlockPos, Identifier> levelSlots() {
        return declarations.getFirst().levelSlots();
    }

    /**
     * One full structure or controller-relative extension declaration.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record Declaration(
            Kind kind,
            BlockArray pattern,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            Map<BlockPos, Identifier> levelSlots) {

        public Declaration {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(pattern, "pattern");
            pattern = new BlockArray(pattern.pattern(), copyStringMap(pattern.tagsByPosition()));
            dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
            modifierReplacements = copyNestedMap(modifierReplacements == null ? Map.of() : modifierReplacements);
            levelSlots = copyMap(levelSlots == null ? Map.of() : levelSlots);
        }

        public static Declaration full(BlockArray pattern) {
            return new Declaration(Kind.FULL, pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                    List.of(), Map.of(), Map.of());
        }

        public static Declaration extension(BlockArray pattern) {
            return new Declaration(Kind.EXTENSION, pattern, null, null, List.of(), Map.of(), Map.of());
        }

        private static Map<BlockPos, List<SingleBlockModifierReplacement>> copyNestedMap(
                Map<BlockPos, List<SingleBlockModifierReplacement>> source) {
            Map<BlockPos, List<SingleBlockModifierReplacement>> copy = new LinkedHashMap<>();
            source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
            return Collections.unmodifiableMap(copy);
        }

        private static Map<BlockPos, List<String>> copyStringMap(Map<BlockPos, List<String>> source) {
            Map<BlockPos, List<String>> copy = new LinkedHashMap<>();
            source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
            return Collections.unmodifiableMap(copy);
        }

        private static <T> Map<BlockPos, T> copyMap(Map<BlockPos, T> source) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }

        public enum Kind {
            FULL,
            EXTENSION
        }
    }
}
