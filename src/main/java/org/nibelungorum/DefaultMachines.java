package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 默认内建机器。当前仅高炉(blast_furnace):沿 Z 轴三块 3×3 层并排。
 * <p>三个 Builder 符号:
 * <ul>
 *     <li>{@code X} —— 外壳</li>
 *     <li>{@code C} —— 控制器</li>
 *     <li>{@code I} —— IO 端口(物品或流体均可)</li>
 * </ul>
 */
public final class DefaultMachines {

    private static final Identifier BLAST_FURNACE_ID = MMCR.id("blast_furnace");

    private DefaultMachines() {
    }

    public static void ensureRegistered() {
        if (MachineRegistry.getMachine(BLAST_FURNACE_ID) == null) {
            Block casing = ModBlocks.CASING.get();
            Block itemInput = ModBlocks.BLOCKS.get("item_input_bus").get();
            Block itemOutput = ModBlocks.BLOCKS.get("item_output_bus").get();
            Block fluidInput = ModBlocks.BLOCKS.get("fluid_input_hatch").get();
            Block fluidOutput = ModBlocks.BLOCKS.get("fluid_output_hatch").get();
            Block energyInput = ModBlocks.BLOCKS.get("energy_input_hatch").get();
            Block energyOutput = ModBlocks.BLOCKS.get("energy_output_hatch").get();
            MachineRegistry.register(blastFurnace(casing, itemInput, itemOutput, fluidInput, fluidOutput, energyInput, energyOutput));
        }
    }

    /**
     * 构筑高炉 pattern。Builder 接收具体 Block 实例(不再依赖静态 init 时的 holder 解析),
     * 让 DefaultMachines 类可在 TestBootstrap 反射 bind 任意 block 之后被调用。
     */
    public static Machine blastFurnace(
            Block casing,
            Block itemInput,
            Block itemOutput,
            Block fluidInput,
            Block fluidOutput,
            Block energyInput,
            Block energyOutput) {
        Block controller = ModBlocks.controllerFor(BLAST_FURNACE_ID).get();
        BlockPredicate ioPort = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(itemInput),
                new BlockPredicate.OfBlock(itemOutput),
                new BlockPredicate.OfBlock(fluidInput),
                new BlockPredicate.OfBlock(fluidOutput),
                new BlockPredicate.OfBlock(energyInput),
                new BlockPredicate.OfBlock(energyOutput)));

        BlockArray pattern = BlockArray.builder()
                .pattern("XXX", "XIX", "XXX")
                .pattern("XXX", "I I", "XXX")
                .pattern("XXX", "XCX", "XXX")
                .set('X', new BlockPredicate.OfBlock(casing))
                .set('C', new BlockPredicate.OfBlock(controller))
                .set('I', ioPort)
                .build();

        Machine definition = MachineDefinitions.get(BLAST_FURNACE_ID);
        return definition == null
                ? new DynamicMachine(BLAST_FURNACE_ID, "高炉", pattern)
                : new DynamicMachine(BLAST_FURNACE_ID, "高炉", pattern, definition.controller());
    }
}
