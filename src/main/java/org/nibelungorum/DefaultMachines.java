package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
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
    private static final Identifier CRACKER_ID = MMCR.id("cracker");
    private static final Identifier REACTOR_ID = MMCR.id("reactor");

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
        if (MachineRegistry.getMachine(CRACKER_ID) == null) {
            Block itemInput = ModBlocks.BLOCKS.get("item_input_bus").get();
            Block itemOutput = ModBlocks.BLOCKS.get("item_output_bus").get();
            Block fluidOutput = ModBlocks.BLOCKS.get("fluid_output_hatch").get();
            Block energyInput = ModBlocks.BLOCKS.get("energy_input_hatch").get();
            MachineRegistry.register(cracker(itemInput, itemOutput, fluidOutput, energyInput));
        }
        if (MachineRegistry.getMachine(REACTOR_ID) == null) {
            Block itemInput = ModBlocks.BLOCKS.get("item_input_bus").get();
            Block itemOutput = ModBlocks.BLOCKS.get("item_output_bus").get();
            Block fluidInput = ModBlocks.BLOCKS.get("fluid_input_hatch").get();
            Block fluidOutput = ModBlocks.BLOCKS.get("fluid_output_hatch").get();
            Block energyOutput = ModBlocks.BLOCKS.get("energy_output_hatch").get();
            MachineRegistry.register(reactor(itemInput, itemOutput, fluidInput, fluidOutput, energyOutput));
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

        PortRequirementSpec portRequirements = PortRequirementSpec.builder()
                .min(PortKinds.ITEM_INPUT.id(), 1)
                .min(PortKinds.ITEM_OUTPUT.id(), 1)
                .min(PortKinds.ENERGY_INPUT.id(), 1)
                .build();
        Machine definition = MachineDefinitions.get(BLAST_FURNACE_ID);
        return definition == null
                ? new DynamicMachine(BLAST_FURNACE_ID, "高炉", pattern, MachineControllerSpec.defaultsFor(BLAST_FURNACE_ID), portRequirements)
                : new DynamicMachine(BLAST_FURNACE_ID, "高炉", pattern, MachineControllerSpec.defaultsFor(BLAST_FURNACE_ID), portRequirements);
    }

    public static Machine cracker(Block itemInput, Block itemOutput, Block fluidOutput, Block energyInput) {
        Block controller = ModBlocks.controllerFor(CRACKER_ID).get();
        BlockPredicate port = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(itemInput),
                new BlockPredicate.OfBlock(itemOutput),
                new BlockPredicate.OfBlock(fluidOutput),
                new BlockPredicate.OfBlock(energyInput),
                new BlockPredicate.OfBlock(Blocks.WEATHERED_COPPER)));

        BlockArray pattern = BlockArray.builder()
                .pattern("AAA", "XBX", "XBX", "XDX")
                .pattern("AAA", "B B", "B B", "DED")
                .pattern("AAA", "XBX", "XBX", "XDX")
                .set('X', new BlockPredicate.OfBlock(Blocks.POLISHED_DIORITE))
                .set('A', new BlockPredicate.OfBlock(Blocks.POLISHED_ANDESITE))
                .set('B', port)
                .set('D', new BlockPredicate.OfBlock(Blocks.BLUE_ICE))
                .set('E', new BlockPredicate.OfBlock(controller))
                .controller('E')
                .build();

        PortRequirementSpec portRequirements = PortRequirementSpec.builder()
                .min(PortKinds.ITEM_INPUT.id(), 1)
                .min(PortKinds.FLUID_OUTPUT.id(), 1)
                .min(PortKinds.ENERGY_INPUT.id(), 1)
                .min(PortKinds.ITEM_OUTPUT.id(), 1)
                .build();
        MachineControllerSpec controllerSpec = new MachineControllerSpec(
                MachineControllerSpec.defaultsFor(CRACKER_ID).id(),
                MachineControllerSpec.defaultsFor(CRACKER_ID).frontTexture(),
                MachineControllerSpec.defaultsFor(CRACKER_ID).sideTexture(),
                MachineControllerSpec.defaultsFor(CRACKER_ID).topTexture(),
                MachineControllerSpec.defaultsFor(CRACKER_ID).bottomTexture(),
                true,
                true,
                true);
        Machine definition = MachineDefinitions.get(CRACKER_ID);
        return definition == null
                ? new DynamicMachine(CRACKER_ID, "裂化器", pattern, controllerSpec, portRequirements)
                : new DynamicMachine(CRACKER_ID, "裂化器", pattern, controllerSpec, portRequirements);
    }

    public static Machine reactor(Block itemInput, Block itemOutput, Block fluidInput, Block fluidOutput, Block energyOutput) {
        Block controller = ModBlocks.controllerFor(REACTOR_ID).get();
        BlockPredicate optionalSlot = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.BLUE_ICE),
                new BlockPredicate.OfBlock(itemInput),
                new BlockPredicate.OfBlock(itemOutput),
                new BlockPredicate.OfBlock(fluidInput),
                new BlockPredicate.OfBlock(fluidOutput),
                new BlockPredicate.OfBlock(energyOutput)));

        BlockArray pattern = BlockArray.builder()
                .pattern("  AAAAA  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                .pattern(" AAXXXAA ", "   DDD   ", "         ", "         ", "         ", "         ", "         ", "         ")
                .pattern("AAXXXXXAA", "  EFFFE  ", "  EFFFE  ", "  EFFFE  ", "  JJJJJ  ", "         ", "         ", "         ")
                .pattern("AXXXXXXXA", " DFGHGFD ", "  FGHGF  ", "  FGHGF  ", "  JXXXJ  ", "   KKK   ", "         ", "         ")
                .pattern("AXXXXXXXA", " DFHXHFD ", "  FHXHF  ", "  FHXHF  ", "  JXXXJ  ", "   KLK   ", "    L    ", "    M    ")
                .pattern("AXXXXXXXA", " DFGHGFD ", "  FGHGF  ", "  FGHGF  ", "  JXXXJ  ", "   KKK   ", "         ", "         ")
                .pattern("AAXXXXXAA", "  EFFFE  ", "  EFFFE  ", "  EFFFE  ", "  JJJJJ  ", "         ", "         ", "         ")
                .pattern(" AAXXXAA ", "   DID   ", "         ", "         ", "         ", "         ", "         ", "         ")
                .pattern("  AAAAA  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                .set('X', new BlockPredicate.OfBlock(Blocks.REINFORCED_DEEPSLATE))
                .set('A', new BlockPredicate.OfBlock(Blocks.DEEPSLATE_BRICK_STAIRS))
                .set('D', optionalSlot)
                .set('E', new BlockPredicate.OfBlock(Blocks.POLISHED_DEEPSLATE))
                .set('F', new BlockPredicate.OfBlock(Blocks.BLACK_STAINED_GLASS))
                .set('G', block("oritech:uranium"))
                .set('H', block("oritech:energite"))
                .set('I', new BlockPredicate.OfBlock(controller))
                .set('J', new BlockPredicate.OfBlock(Blocks.POLISHED_DEEPSLATE_STAIRS))
                .set('K', new BlockPredicate.OfBlock(Blocks.DEEPSLATE_BRICK_SLAB))
                .set('L', new BlockPredicate.OfBlock(Blocks.DEEPSLATE_TILES))
                .set('M', block("minecraft:oxidized_lightning_rod"))
                .controller('I')
                .build();

        PortRequirementSpec portRequirements = PortRequirementSpec.builder()
                .min(PortKinds.FLUID_INPUT.id(), 1)
                .min(PortKinds.FLUID_OUTPUT.id(), 1)
                .min(PortKinds.ENERGY_OUTPUT.id(), 1)
                .min(PortKinds.ITEM_OUTPUT.id(), 1)
                .min(PortKinds.ITEM_INPUT.id(), 1)
                .build();
        Machine definition = MachineDefinitions.get(REACTOR_ID);
        return definition == null
                ? new DynamicMachine(REACTOR_ID, "反应堆", pattern, MachineControllerSpec.defaultsFor(REACTOR_ID), portRequirements)
                : new DynamicMachine(REACTOR_ID, "反应堆", pattern, MachineControllerSpec.defaultsFor(REACTOR_ID), portRequirements);
    }

    private static BlockPredicate block(String id) {
        return new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)));
    }
}
