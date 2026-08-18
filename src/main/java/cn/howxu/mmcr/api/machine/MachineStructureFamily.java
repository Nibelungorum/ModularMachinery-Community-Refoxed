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
        Map<BlockPos, Character> symbols = Map.of();
        PortRequirementSpec portRequirements = PortRequirementSpec.none();
        PortTierRequirementSpec portTierRequirements = PortTierRequirementSpec.none();
        List<DynamicPatternSpec> dynamicPatterns = List.of();
        MachineStructureRequirements requirements = MachineStructureRequirements.EMPTY;
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements = Map.of();
        Map<BlockPos, Identifier> levelSlots = Map.of();

        for (int index = 0; index < declarations.size(); index++) {
            int stageNumber = index + 1;
            Declaration declaration = declarations.get(index);
            if (declaration.kind() == Declaration.Kind.FULL) {
                pattern = new LinkedHashMap<>(declaration.pattern().pattern());
                tags = copyNestedMap(declaration.pattern().tagsByPosition());
                symbols = new LinkedHashMap<>(declaration.pattern().symbolsByPosition());
                portRequirements = defaultPorts(declaration.portRequirements());
                portTierRequirements = defaultTiers(declaration.portTierRequirements());
                dynamicPatterns = new ArrayList<>(declaration.dynamicPatterns());
                requirements = declaration.requirements();
                modifierReplacements = Map.of();
                levelSlots = Map.of();
            } else {
                pattern = mergeMap(pattern, declaration.pattern().pattern(), stageNumber, "predicate");
                tags = mergeMap(tags, declaration.pattern().tagsByPosition(), stageNumber, "tags");
                symbols = mergeMap(symbols, declaration.pattern().symbolsByPosition(), stageNumber, "symbol");
                if (declaration.portRequirements() != null) portRequirements = declaration.portRequirements();
                if (declaration.portTierRequirements() != null) portTierRequirements = declaration.portTierRequirements();
                dynamicPatterns = mergeList(dynamicPatterns, declaration.dynamicPatterns(), stageNumber, "dynamic pattern");
                requirements = MachineStructureRequirements.merge(requirements, declaration.requirements(), stageNumber)
                        .validate(new BlockArray(pattern, tags, symbols));
                modifierReplacements = Map.of();
                levelSlots = Map.of();
            }

            rejectMultipleControllers(pattern, stageNumber);
            stages.add(MachineStructureStage.withCompiledRequirements(stageNumber, declaration.kind(),
                    new BlockArray(pattern, tags, symbols), portRequirements, portTierRequirements, dynamicPatterns,
                    requirements, modifierReplacements, levelSlots));
        }
        return new MachineStructureFamily(stages);
    }

    private static PortRequirementSpec defaultPorts(PortRequirementSpec requirements) {
        return requirements == null ? PortRequirementSpec.none() : requirements;
    }

    private static PortTierRequirementSpec defaultTiers(PortTierRequirementSpec requirements) {
        return requirements == null ? PortTierRequirementSpec.none() : requirements;
    }

    private static void rejectMultipleControllers(Map<BlockPos, BlockPredicate> pattern, int stageNumber) {
        List<BlockPos> controllers = pattern.entrySet().stream()
                .filter(entry -> isController(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (controllers.size() > 1) {
            throw new IllegalArgumentException("stage " + stageNumber + " contains multiple controllers at "
                    + controllers.stream().map(MachineStructureFamily::format).toList());
        }
    }

    private static boolean isController(BlockPredicate predicate) {
        if (predicate instanceof BlockPredicate.OfBlock ofBlock) {
            return ofBlock.block() instanceof MachineControllerBlock;
        }
        if (predicate instanceof BlockPredicate.OfBlockState ofBlockState) {
            return ofBlockState.state().getBlock() instanceof MachineControllerBlock;
        }
        if (predicate instanceof BlockPredicate.AnyOf anyOf) {
            return anyOf.children().stream().anyMatch(MachineStructureFamily::isController);
        }
        return false;
    }

    private static String format(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
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
