package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
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

/**
 * Server-script builder for reloadable machine structures.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineStructureBuilderJS extends BuilderBase<MachineStructureDefinition> {
    public transient BlockArray pattern = new BlockArray(Map.of());
    public transient PortRequirementSpec portRequirements = PortRequirementSpec.none();
    public transient Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements = new LinkedHashMap<>();

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

                blocks.put(new BlockPos(x, y, 0), toPredicate(value));
            }
        }

        pattern = new BlockArray(Map.copyOf(blocks));
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
        return new MachineStructureDefinition(id, pattern, portRequirements, List.of(), modifierReplacements);
    }

    private static BlockPredicate toPredicate(Object value) {
        return switch (value) {
            case String blockId -> new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)));
            case Block block -> new BlockPredicate.OfBlock(block);
            case BlockState state -> new BlockPredicate.OfBlockState(state);
            case BlockPredicate predicate -> predicate;
            default -> throw new IllegalArgumentException("Unknown pattern key value: " + value);
        };
    }
}
