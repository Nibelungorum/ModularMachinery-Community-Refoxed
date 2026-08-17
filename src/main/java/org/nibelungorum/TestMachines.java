package org.nibelungorum;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Machine structures used by the GameTest-only default machine registrations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestMachines {
    private TestMachines() {
    }

    public static BlockArray casingCubePattern() {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    pattern.put(new BlockPos(x, 0, z), new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
                }
            }
        }
        return new BlockArray(pattern);
    }

    public static BlockArray ironCompressorPattern() {
        return BlockArray.builder()
                .pattern("   ", "XXX", " I ")
                .pattern("   ", "X X", "  E")
                .pattern("   ", "XXX", " O ")
                .set('X', new BlockPredicate.OfBlock(ModBlocks.CASING.get()))
                .set('I', new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))
                .set('O', new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()))
                .set('E', new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()))
                .build();
    }

    public static List<MachineStructureDefinition.Declaration> expandableStageDeclarations() {
        return List.of(
                MachineStructureDefinition.Declaration.full(new BlockArray(Map.of(
                        new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.CASING.get())))),
                MachineStructureDefinition.Declaration.extension(new BlockArray(Map.of(
                        new BlockPos(1, 1, 0), new BlockPredicate.OfBlock(ModBlocks.CASING.get())))),
                MachineStructureDefinition.Declaration.extension(new BlockArray(Map.of(
                        new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(ModBlocks.CASING.get())))));
    }

    public static List<MachineStructureDefinition.Declaration> distillationTowerDeclarations() {
        return List.of(
                MachineStructureDefinition.Declaration.full(blockArray(
                        new BlockPos(0, 0, -1), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                        new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()),
                        new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("fluid_output_hatch").get()))),
                MachineStructureDefinition.Declaration.extension(blockArray(
                        new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("fluid_output_hatch").get()))),
                MachineStructureDefinition.Declaration.extension(blockArray(
                        new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("fluid_output_hatch").get()))));
    }

    private static BlockArray blockArray(Object... entries) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            pattern.put((BlockPos) entries[index], (BlockPredicate) entries[index + 1]);
        }
        return new BlockArray(pattern);
    }
}
