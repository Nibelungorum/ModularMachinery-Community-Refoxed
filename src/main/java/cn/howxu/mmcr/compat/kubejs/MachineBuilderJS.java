package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public class MachineBuilderJS extends BuilderBase<DynamicMachine> {
    public transient String localizedName = "Unknown Machine";
    public transient BlockArray pattern = new BlockArray(Map.of());

    public MachineBuilderJS(Identifier id) {
        super(id);
    }

    public MachineBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineBuilderJS localizedName(String name) {
        this.localizedName = name;
        return this;
    }

    public MachineBuilderJS pattern(String grid, Map<String, Object> keys) {
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

    @Override
    public DynamicMachine createObject() {
        return new DynamicMachine(id, localizedName, pattern);
    }

    public void registerObject() {
        MachineRegistry.register(createObject());
    }

    public MachineBuilderJS register() {
        registerObject();
        return this;
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
