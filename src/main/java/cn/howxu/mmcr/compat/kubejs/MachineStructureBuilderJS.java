package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;

/**
 * Server-script builder for reloadable machine structures.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineStructureBuilderJS extends BuilderBase<MachineStructureDefinition> {
    public transient BlockArray pattern = new BlockArray(Map.of());
    public transient PortRequirementSpec portRequirements = PortRequirementSpec.none();
    public transient Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements = new LinkedHashMap<>();
    public transient Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
    private final List<Declaration> declarations = new ArrayList<>();

    public MachineStructureBuilderJS(Identifier id) {
        super(id);
    }

    public MachineStructureBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineStructureBuilderJS pattern(String grid, Map<String, Object> keys) {
        var blocks = new HashMap<BlockPos, BlockPredicate>();
        var rows = grid.trim().split("\\s+");

        for (int y = 0; y < rows.length; y++) {
            var row = rows[y];

            for (int x = 0; x < row.length(); x++) {
                var key = row.charAt(x);

                if (key == '_' || key == '.') {
                    continue;
                }

                var value = keys.get(String.valueOf(key));

                if (value == null) {
                    continue;
                }

                BlockPos pos = new BlockPos(x, y, 0);
                blocks.put(pos, toPredicate(value));
                if (value instanceof LevelSlot levelSlot) {
                    levelSlots.put(pos, levelSlot.typeId());
                }
            }
        }

        pattern = new BlockArray(Map.copyOf(blocks));
        declarations.clear();
        declarations.add(new Declaration(Declaration.Kind.FULL, pattern, portRequirements,
                PortTierRequirementSpec.none(), List.of(), modifierReplacements, levelSlots));
        return this;
    }

    public MachineStructureBuilderJS fullStructure(BlockArray pattern) {
        declarations.add(Declaration.full(Objects.requireNonNull(pattern)));
        return this;
    }

    public MachineStructureBuilderJS extension(BlockArray pattern) {
        if (declarations.isEmpty()) throw new IllegalStateException("extension requires a full structure first");
        declarations.add(Declaration.extension(Objects.requireNonNull(pattern)));
        return this;
    }

    public MachineStructureBuilderJS addModifier(SingleBlockModifierReplacement replacement) {
        Objects.requireNonNull(replacement, "replacement");
        BlockPos pos = Objects.requireNonNull(replacement.getPos(), "replacement.pos");
        modifierReplacements.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(replacement);
        return this;
    }

    @Override
    public MachineStructureDefinition createObject() {
        if (declarations.isEmpty()) {
            return new MachineStructureDefinition(id, pattern, portRequirements, PortTierRequirementSpec.none(),
                    List.of(), modifierReplacements, levelSlots);
        }
        List<Declaration> result = new ArrayList<>(declarations);
        Declaration first = result.getFirst();
        result.set(0, new Declaration(first.kind(), first.pattern(), portRequirements,
                PortTierRequirementSpec.none(), List.of(), modifierReplacements, levelSlots));
        return new MachineStructureDefinition(id, result);
    }

    private static BlockPredicate toPredicate(Object value) {
        return switch (value) {
            case String blockId -> new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)));
            case Block block -> new BlockPredicate.OfBlock(block);
            case BlockState state -> new BlockPredicate.OfBlockState(state);
            case BlockPredicate predicate -> predicate;
            case LevelSlot levelSlot -> levelPredicate(levelSlot);
            default -> throw new IllegalArgumentException("Unknown pattern key value: " + value);
        };
    }

    private static BlockPredicate levelPredicate(LevelSlot slot) {
        if (MachineLevelRegistry.getType(slot.typeId()) == null) {
            throw new IllegalArgumentException("Unknown machine level type: " + slot.typeId());
        }
        var levels = MachineLevelRegistry.levelsForType(slot.typeId());
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("Machine level type has no registered levels: " + slot.typeId());
        }
        return new BlockPredicate.AnyOf(levels.stream().map(level -> level.statePredicate()).toList());
    }
}
