package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves full and extension declarations into ordered full structure snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureFamily(List<MachineStructureStage> stages) {

    public MachineStructureFamily {
        stages = List.copyOf(stages);
    }

    public static MachineStructureFamily of(MachineStructureDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        List<Declaration> declarations = definition.declarations();
        if (declarations.getFirst().kind() != Declaration.Kind.FULL) {
            throw new IllegalArgumentException("stage 1 must be a full structure");
        }

        List<MachineStructureStage> stages = new ArrayList<>(declarations.size());
        Map<BlockPos, BlockPredicate> pattern = Map.of();
        Map<BlockPos, List<String>> tags = Map.of();
        PortRequirementSpec portRequirements = PortRequirementSpec.none();
        PortTierRequirementSpec portTierRequirements = PortTierRequirementSpec.none();
        List<DynamicPatternSpec> dynamicPatterns = List.of();
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements = Map.of();
        Map<BlockPos, Identifier> levelSlots = Map.of();

        for (int index = 0; index < declarations.size(); index++) {
            int stageNumber = index + 1;
            Declaration declaration = declarations.get(index);
            if (declaration.kind() == Declaration.Kind.FULL) {
                pattern = new LinkedHashMap<>(declaration.pattern().pattern());
                tags = copyNestedMap(declaration.pattern().tagsByPosition());
                portRequirements = defaultPorts(declaration.portRequirements());
                portTierRequirements = defaultTiers(declaration.portTierRequirements());
                dynamicPatterns = new ArrayList<>(declaration.dynamicPatterns());
                modifierReplacements = copyNestedMap(declaration.modifierReplacements());
                levelSlots = new LinkedHashMap<>(declaration.levelSlots());
            } else {
                pattern = mergeMap(pattern, declaration.pattern().pattern(), stageNumber, "predicate");
                tags = mergeMap(tags, declaration.pattern().tagsByPosition(), stageNumber, "tags");
                rejectSecondController(pattern, declaration.pattern().pattern(), stageNumber);
                if (declaration.portRequirements() != null) portRequirements = declaration.portRequirements();
                if (declaration.portTierRequirements() != null) portTierRequirements = declaration.portTierRequirements();
                dynamicPatterns = mergeList(dynamicPatterns, declaration.dynamicPatterns(), stageNumber, "dynamic pattern");
                modifierReplacements = mergeMap(
                        modifierReplacements, declaration.modifierReplacements(), stageNumber, "modifier replacements");
                levelSlots = mergeMap(levelSlots, declaration.levelSlots(), stageNumber, "level slot");
            }

            stages.add(new MachineStructureStage(stageNumber, new BlockArray(pattern, tags),
                    portRequirements, portTierRequirements, dynamicPatterns, modifierReplacements, levelSlots));
        }
        return new MachineStructureFamily(stages);
    }

    private static PortRequirementSpec defaultPorts(PortRequirementSpec requirements) {
        return requirements == null ? PortRequirementSpec.none() : requirements;
    }

    private static PortTierRequirementSpec defaultTiers(PortTierRequirementSpec requirements) {
        return requirements == null ? PortTierRequirementSpec.none() : requirements;
    }

    private static void rejectSecondController(Map<BlockPos, BlockPredicate> merged,
                                               Map<BlockPos, BlockPredicate> extension, int stageNumber) {
        long controllerCount = merged.values().stream().filter(MachineStructureFamily::isController).count();
        if (controllerCount > 1 || extension.entrySet().stream()
                .anyMatch(entry -> isController(entry.getValue()) && !merged.containsKey(entry.getKey()))) {
            throw new IllegalArgumentException("stage " + stageNumber + " introduces a second controller");
        }
    }

    private static boolean isController(BlockPredicate predicate) {
        return predicate instanceof BlockPredicate.OfBlock ofBlock
                && ofBlock.block() instanceof MachineControllerBlock;
    }

    private static <T> Map<BlockPos, T> mergeMap(Map<BlockPos, T> previous, Map<BlockPos, T> extension,
                                                  int stageNumber, String valueName) {
        Map<BlockPos, T> merged = new LinkedHashMap<>(previous);
        extension.forEach((position, value) -> {
            T existing = merged.putIfAbsent(position, value);
            if (existing != null && !existing.equals(value)) {
                throw conflict(stageNumber, position, valueName);
            }
        });
        return merged;
    }

    private static <T> List<T> mergeList(List<T> previous, List<T> extension,
                                         int stageNumber, String valueName) {
        List<T> merged = new ArrayList<>(previous);
        for (T value : extension) {
            if (merged.contains(value)) continue;
            if (value instanceof DynamicPatternSpec dynamic && merged.stream()
                    .filter(DynamicPatternSpec.class::isInstance)
                    .map(DynamicPatternSpec.class::cast)
                    .anyMatch(existing -> existing.name().equals(dynamic.name()))) {
                throw new IllegalArgumentException("stage " + stageNumber + " has conflicting " + valueName
                        + " " + dynamic.name());
            }
            merged.add(value);
        }
        return merged;
    }

    private static IllegalArgumentException conflict(int stageNumber, BlockPos position, String valueName) {
        return new IllegalArgumentException("stage " + stageNumber + " has conflicting " + valueName + " at "
                + position.getX() + ", " + position.getY() + ", " + position.getZ());
    }

    private static <T> Map<BlockPos, List<T>> copyNestedMap(Map<BlockPos, List<T>> source) {
        Map<BlockPos, List<T>> copy = new LinkedHashMap<>();
        source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
        return copy;
    }
}
