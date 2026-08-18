package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles character-level structure requirements to concrete runtime positions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureRequirementCompiler {

    private MachineStructureRequirementCompiler() {
    }

    public static Compiled compile(BlockArray pattern, MachineStructureRequirements requirements) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(requirements, "requirements").validate(pattern);
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifiers = new LinkedHashMap<>();
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
        for (var entry : pattern.symbolsByPosition().entrySet()) {
            BlockPos pos = entry.getKey();
            Character symbol = entry.getValue();
            List<SingleBlockModifierReplacement> replacements = requirements.modifierReplacements().get(symbol);
            if (replacements != null) modifiers.put(pos, new ArrayList<>(replacements));
            Identifier typeId = requirements.levelSlots().get(symbol);
            if (typeId != null) levelSlots.put(pos, typeId);
        }
        return new Compiled(copyNestedMap(modifiers), Map.copyOf(levelSlots));
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> copyNestedMap(
            Map<BlockPos, List<SingleBlockModifierReplacement>> source) {
        Map<BlockPos, List<SingleBlockModifierReplacement>> copy = new LinkedHashMap<>();
        source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
        return Map.copyOf(copy);
    }

    /**
     * Runtime coordinate maps consumed by existing matcher and preview code.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record Compiled(
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            Map<BlockPos, Identifier> levelSlots) {
    }
}
