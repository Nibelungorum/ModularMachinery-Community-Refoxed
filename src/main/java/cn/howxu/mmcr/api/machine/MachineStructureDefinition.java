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

    public MachineStructureRequirements requirements() {
        return declarations.getFirst().requirements();
    }

    public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements() {
        Declaration declaration = declarations.getFirst();
        return compiled(declaration).modifierReplacements();
    }

    public Map<BlockPos, Identifier> levelSlots() {
        Declaration declaration = declarations.getFirst();
        return compiled(declaration).levelSlots();
    }

    private static MachineStructureRequirementCompiler.Compiled compiled(Declaration declaration) {
        MachineStructureRequirementCompiler.Compiled compiled =
                MachineStructureRequirementCompiler.compile(declaration.pattern(), declaration.requirements());
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements =
                new LinkedHashMap<>(declaration.modifierReplacements());
        compiled.modifierReplacements().forEach((position, replacements) ->
                modifierReplacements.merge(position, replacements, (left, right) -> {
                    java.util.ArrayList<SingleBlockModifierReplacement> merged = new java.util.ArrayList<>(left);
                    merged.addAll(right);
                    return List.copyOf(merged);
                }));
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>(declaration.levelSlots());
        compiled.levelSlots().forEach((position, typeId) -> {
            Identifier existing = levelSlots.putIfAbsent(position, typeId);
            if (existing != null && !existing.equals(typeId)) {
                throw new IllegalArgumentException("conflicting compiled requirement at " + position);
            }
        });
        return new MachineStructureRequirementCompiler.Compiled(
                Collections.unmodifiableMap(modifierReplacements), Collections.unmodifiableMap(levelSlots));
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
                MachineStructureRequirements requirements,
                Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
                Map<BlockPos, Identifier> levelSlots) {

            public Declaration(Kind kind, BlockArray pattern, PortRequirementSpec portRequirements,
                    PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
                    MachineStructureRequirements requirements) {
                this(kind, pattern, portRequirements, portTierRequirements, dynamicPatterns,
                        requirements, Map.of(), Map.of());
            }

            public Declaration(Kind kind, BlockArray pattern, PortRequirementSpec portRequirements,
                    PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
                    Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
                    Map<BlockPos, Identifier> levelSlots) {
                this(kind, pattern, portRequirements, portTierRequirements, dynamicPatterns,
                        MachineStructureRequirements.EMPTY, modifierReplacements, levelSlots);
            }

        public Declaration {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(pattern, "pattern");
            pattern = new BlockArray(pattern.pattern(), copyStringMap(pattern.tagsByPosition()), pattern.symbolsByPosition());
            dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
            requirements = (requirements == null ? MachineStructureRequirements.EMPTY : requirements).validate(pattern);
            modifierReplacements = copyNestedMap(modifierReplacements == null ? Map.of() : modifierReplacements);
            levelSlots = copyMap(levelSlots == null ? Map.of() : levelSlots);
        }

        public static Declaration full(BlockArray pattern) {
            return new Declaration(Kind.FULL, pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                    List.of(), MachineStructureRequirements.EMPTY);
        }

        public static Declaration extension(BlockArray pattern) {
            return new Declaration(Kind.EXTENSION, pattern, null, null, List.of(), MachineStructureRequirements.EMPTY);
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
