package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import net.minecraft.resources.Identifier;

/**
 * Built-in machine definitions registered through the {@link MachineDefinitions}
 * SPI instead of being hardcoded in the registry class.
 */
public final class BuiltinMachines {

    private static final Identifier BLAST_FURNACE_ID = MMCR.id("blast_furnace");

    private BuiltinMachines() {
    }

    /**
     * Register the built-in machine definitions with {@link MachineDefinitions}.
     * Call before {@code MMCR} touches {@code ModBlocks}.
     */
    public static void register() {
        MachineDefinitions.addBuiltinSupplier(() ->
                new DynamicMachine(BLAST_FURNACE_ID, "高炉", new BlockArray(java.util.Map.of())));
    }
}