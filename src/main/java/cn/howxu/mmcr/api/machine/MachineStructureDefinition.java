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
            MachineStructureRequirements requirements) {
        this(machineId, List.of(new Declaration(Declaration.Kind.FULL, pattern,
                portRequirements == null ? PortRequirementSpec.none() : portRequirements,
                portTierRequirements == null ? PortTierRequirementSpec.none() : portTierRequirements,
                dynamicPatterns, requirements)));
    }

    public MachineStructureDefinition(
            Identifier machineId,
            BlockArray pattern,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements) {
        this(machineId, pattern, portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, requirements);
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
        return new MachineStructureRequirementCompiler.Compiled(
                Collections.unmodifiableMap(new LinkedHashMap<>(compiled.modifierReplacements())),
                Collections.unmodifiableMap(new LinkedHashMap<>(compiled.levelSlots())));
    }

    /**
     * One full structure or controller-relative extension declaration.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Declaration {
        private final Kind kind;
        private final BlockArray pattern;
        private final PortRequirementSpec portRequirements;
        private final PortTierRequirementSpec portTierRequirements;
        private final List<DynamicPatternSpec> dynamicPatterns;
        private final MachineStructureRequirements requirements;
        private final boolean stateSensitive;

        public Declaration(Kind kind, BlockArray pattern, PortRequirementSpec portRequirements,
                PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
                MachineStructureRequirements requirements) {
            this(kind, pattern, portRequirements, portTierRequirements, dynamicPatterns, requirements, false);
        }

        public Declaration(Kind kind, BlockArray pattern, PortRequirementSpec portRequirements,
                PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
                MachineStructureRequirements requirements, boolean stateSensitive) {
            this.kind = Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(pattern, "pattern");
            this.pattern = new BlockArray(pattern.pattern(), copyStringMap(pattern.tagsByPosition()), pattern.symbolsByPosition());
            this.portRequirements = portRequirements;
            this.portTierRequirements = portTierRequirements;
            this.dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
            this.requirements = (requirements == null ? MachineStructureRequirements.EMPTY : requirements).validate(this.pattern);
            this.stateSensitive = stateSensitive;
        }

        public Kind kind() {
            return kind;
        }

        public BlockArray pattern() {
            return pattern;
        }

        public PortRequirementSpec portRequirements() {
            return portRequirements;
        }

        public PortTierRequirementSpec portTierRequirements() {
            return portTierRequirements;
        }

        public List<DynamicPatternSpec> dynamicPatterns() {
            return dynamicPatterns;
        }

        public MachineStructureRequirements requirements() {
            return requirements;
        }

        public boolean stateSensitive() {
            return stateSensitive;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Declaration other)) return false;
            return kind == other.kind
                    && pattern.equals(other.pattern)
                    && Objects.equals(portRequirements, other.portRequirements)
                    && Objects.equals(portTierRequirements, other.portTierRequirements)
                    && dynamicPatterns.equals(other.dynamicPatterns)
                    && requirements.equals(other.requirements)
                    && stateSensitive == other.stateSensitive;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, pattern, portRequirements, portTierRequirements, dynamicPatterns, requirements,
                    stateSensitive);
        }

        public static Declaration full(BlockArray pattern) {
            return new Declaration(Kind.FULL, pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                    List.of(), MachineStructureRequirements.EMPTY);
        }

        public static Declaration extension(BlockArray pattern) {
            return new Declaration(Kind.EXTENSION, pattern, null, null, List.of(), MachineStructureRequirements.EMPTY);
        }

        private static Map<BlockPos, List<String>> copyStringMap(Map<BlockPos, List<String>> source) {
            Map<BlockPos, List<String>> copy = new LinkedHashMap<>();
            source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
            return Collections.unmodifiableMap(copy);
        }

        public enum Kind {
            FULL,
            EXTENSION
        }
    }
}
