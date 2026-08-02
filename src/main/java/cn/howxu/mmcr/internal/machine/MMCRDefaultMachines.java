package cn.howxu.mmcr.internal.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class MMCRDefaultMachines {

    private static final net.minecraft.resources.Identifier IRON_COMPRESSOR_ID = MMCR.id("iron_compressor");

    private MMCRDefaultMachines() {
    }

    public static void ensureRegistered() {
        if (MachineRegistry.getMachine(IRON_COMPRESSOR_ID) == null) {
            MachineRegistry.register(ironCompressor());
        }
    }

    public static Machine ironCompressor() {
        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    pattern.put(new BlockPos(x, 0, z), new BlockPredicate.OfBlock(MMCRRegistries.CASING_BLOCK.get()));
                }
            }
        }
        return new DynamicMachine(IRON_COMPRESSOR_ID, "Iron Compressor", new BlockArray(Map.copyOf(pattern)));
    }
}
