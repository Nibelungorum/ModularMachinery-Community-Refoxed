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
 * Immutable full snapshot of one publicly visible structure stage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureStage {
    private final int number;
    private final BlockArray pattern;
    private final PortRequirementSpec portRequirements;
    private final PortTierRequirementSpec portTierRequirements;
    private final List<DynamicPatternSpec> dynamicPatterns;
    private final MachineStructureRequirements requirements;
    private final Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements;
    private final Map<BlockPos, Identifier> levelSlots;

    public MachineStructureStage(int number, BlockArray pattern, PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements) {
        this(number, pattern, portRequirements, portTierRequirements, dynamicPatterns,
                requirements, Map.of(), Map.of());
    }

    static MachineStructureStage withCompiledRequirements(int number, BlockArray pattern, PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements, Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            Map<BlockPos, Identifier> levelSlots) {
        return new MachineStructureStage(number, pattern, portRequirements, portTierRequirements, dynamicPatterns,
                requirements, modifierReplacements, levelSlots);
    }

    private MachineStructureStage(int number, BlockArray pattern, PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements, Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            Map<BlockPos, Identifier> levelSlots) {
        if (number < 1) throw new IllegalArgumentException("stage number must be positive");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(portRequirements, "portRequirements");
        Objects.requireNonNull(portTierRequirements, "portTierRequirements");
        this.number = number;
        this.pattern = new BlockArray(pattern.pattern(), copyNestedMap(pattern.tagsByPosition()), pattern.symbolsByPosition());
        this.portRequirements = portRequirements;
        this.portTierRequirements = portTierRequirements;
        this.dynamicPatterns = List.copyOf(dynamicPatterns);
        this.requirements = (requirements == null ? MachineStructureRequirements.EMPTY : requirements).validate(this.pattern);
        MachineStructureRequirementCompiler.Compiled compiled =
                MachineStructureRequirementCompiler.compile(this.pattern, this.requirements);
        this.modifierReplacements = mergeNestedMaps(modifierReplacements, compiled.modifierReplacements());
        this.levelSlots = Collections.unmodifiableMap(mergeMaps(levelSlots, compiled.levelSlots()));
    }

    public int number() {
        return number;
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

    public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements() {
        return modifierReplacements;
    }

    public Map<BlockPos, Identifier> levelSlots() {
        return levelSlots;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MachineStructureStage other)) return false;
        return number == other.number
                && pattern.equals(other.pattern)
                && portRequirements.equals(other.portRequirements)
                && portTierRequirements.equals(other.portTierRequirements)
                && dynamicPatterns.equals(other.dynamicPatterns)
                && requirements.equals(other.requirements)
                && modifierReplacements.equals(other.modifierReplacements)
                && levelSlots.equals(other.levelSlots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, pattern, portRequirements, portTierRequirements, dynamicPatterns,
                requirements, modifierReplacements, levelSlots);
    }

    private static <T> Map<BlockPos, List<T>> copyNestedMap(Map<BlockPos, List<T>> source) {
        Map<BlockPos, List<T>> copy = new LinkedHashMap<>();
        source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> mergeNestedMaps(
            Map<BlockPos, List<SingleBlockModifierReplacement>> explicit,
            Map<BlockPos, List<SingleBlockModifierReplacement>> compiled) {
        Map<BlockPos, List<SingleBlockModifierReplacement>> merged = new LinkedHashMap<>();
        if (explicit != null) explicit.forEach((position, values) -> merged.put(position, List.copyOf(values)));
        compiled.forEach((position, values) -> merged.merge(position, List.copyOf(values), (left, right) -> {
            java.util.ArrayList<SingleBlockModifierReplacement> combined = new java.util.ArrayList<>(left);
            combined.addAll(right);
            return List.copyOf(combined);
        }));
        return Collections.unmodifiableMap(merged);
    }

    private static <T> Map<BlockPos, T> mergeMaps(Map<BlockPos, T> explicit, Map<BlockPos, T> compiled) {
        Map<BlockPos, T> merged = new LinkedHashMap<>();
        if (explicit != null) merged.putAll(explicit);
        compiled.forEach((position, value) -> {
            T existing = merged.putIfAbsent(position, value);
            if (existing != null && !existing.equals(value)) {
                throw new IllegalArgumentException("conflicting compiled requirement at " + position);
            }
        });
        return merged;
    }
}
