package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final Identifier ALLOY_FURNACE_ID = MMCR.id("alloy_furnace");
    private static final Identifier CRACKER_ID = MMCR.id("cracker");
    private static final Identifier REACTOR_ID = MMCR.id("reactor");
    private static final Identifier THERMAL_SMELTING_FURNACE_ID = MMCR.id("thermal_smelting_furnace");

    private DefaultMachines() {
    }

    public static void ensureRegistered() {
        MachineStructureRegistry.replaceDynamic(structures());
    }

    public static Map<Identifier, MachineStructureDefinition> structures() {
        Block casing = ModBlocks.CASING.get();
        Block itemInput = ModBlocks.BLOCKS.get("item_input_bus").get();
        Block itemOutput = ModBlocks.BLOCKS.get("item_output_bus").get();
        Block fluidInput = ModBlocks.BLOCKS.get("fluid_input_hatch").get();
        Block fluidOutput = ModBlocks.BLOCKS.get("fluid_output_hatch").get();
        Block energyInput = ModBlocks.BLOCKS.get("energy_input_hatch").get();
        Block energyOutput = ModBlocks.BLOCKS.get("energy_output_hatch").get();

        Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
        structures.put(BLAST_FURNACE_ID, structureOf(blastFurnace(casing, itemInput, itemOutput, fluidInput, fluidOutput, energyInput, energyOutput)));
        structures.put(ALLOY_FURNACE_ID, structureOf(alloyFurnace(itemInput, itemOutput, energyInput)));
        structures.put(CRACKER_ID, structureOf(cracker(itemInput, itemOutput, fluidOutput, energyInput)));
        structures.put(REACTOR_ID, structureOf(reactor(itemInput, itemOutput, fluidInput, fluidOutput, energyOutput)));
        structures.put(THERMAL_SMELTING_FURNACE_ID, structureOf(thermalSmeltingFurnace()));
        return Map.copyOf(structures);
    }

    private static MachineStructureDefinition structureOf(Machine machine) {
        return new MachineStructureDefinition(
                machine.registryName(),
                machine.pattern(),
                machine.portRequirements(),
                machine.portTierRequirements(),
                machine.dynamicPatterns(),
                machine instanceof DynamicMachine dynamic ? dynamic.modifierReplacements() : Map.of());
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
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.FLUID),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.FLUID),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ENERGY)));
        List<BlockPredicate> parallelSlotBlocks = new ArrayList<>();
        parallelSlotBlocks.add(new BlockPredicate.OfBlock(casing));
        for (ParallelTier tier : ParallelTier.values()) {
            parallelSlotBlocks.add(new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(tier.idSuffix()).get()));
        }
        BlockPredicate casingOrParallelController = new BlockPredicate.AnyOf(parallelSlotBlocks);
        BlockPredicate casingOrFactoryController = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(casing),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));

        BlockArray pattern = BlockArray.builder()
                .pattern("AXA", "XIX", "XXX")
                .pattern("XXX", "I I", "XBX")
                .pattern("AXA", "XCX", "XXX")
                .set('X', new BlockPredicate.OfBlock(casing))
                .set('A', casingOrParallelController)
                .set('B', casingOrFactoryController)
                .set('C', new BlockPredicate.OfBlock(controller))
                .set('I', ioPort)
                .build();

        PortRequirementSpec portRequirements = PortRequirementSpec.none();
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.builder()
                .minEnergyInput(EnergyHatchSize.LUDICROUS)
                .minItemInput(ItemBusSize.NORMAL)
                .anyItemOutput()
                .build();
        return new DynamicMachine(
                BLAST_FURNACE_ID,
                "高炉",
                pattern,
                MachineControllerSpec.defaultsFor(BLAST_FURNACE_ID),
                MachineAppearanceSpec.defaults(),
                portRequirements,
                tierRequirements,
                List.of(),
                Map.of(),
                Integer.MAX_VALUE,
                true,
                true,
                4);
    }

    public static Machine alloyFurnace(Block itemInput, Block itemOutput, Block energyInput) {
        Block controller = ModBlocks.controllerFor(ALLOY_FURNACE_ID).get();
        BlockPredicate ioPort = new BlockPredicate.AnyOf(List.of(
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY)));

        BlockArray pattern = BlockArray.builder()
                .pattern("XXX", "XIX", "XXX")
                .pattern("XMX", "I I", "XMX")
                .pattern("XXX", "XCX", "XXX")
                .set('X', new BlockPredicate.OfBlock(Blocks.BRICKS))
                .set('C', new BlockPredicate.OfBlock(controller))
                .set('I', ioPort)
                .set('M', new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE))
                .build();

        PortRequirementSpec portRequirements = PortRequirementSpec.none();
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.none();
        return new DynamicMachine(
                ALLOY_FURNACE_ID,
                "合金炉",
                pattern,
                MachineControllerSpec.defaultsFor(ALLOY_FURNACE_ID),
                MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("bricks")),
                portRequirements,
                tierRequirements,
                List.of(),
                alloyFurnaceModifiers());
    }

    /**
     * 合金炉 M 位置允许两种 modifier 方块：
     * <ul>
     *     <li>钻石块 → 配方时间 × 0.5</li>
     *     <li>金块 → 产物数量 × 2</li>
     * </ul>
     * 原方块（高炉）仍为默认可选方块，不挂载 modifier。
     */
    private static Map<BlockPos, List<SingleBlockModifierReplacement>> alloyFurnaceModifiers() {
        BlockPos[] mPositions = {
                new BlockPos(0, -1, -1),
                new BlockPos(0, 1, -1),
        };
        Map<BlockPos, List<SingleBlockModifierReplacement>> map = new LinkedHashMap<>();
        for (BlockPos pos : mPositions) {
            List<SingleBlockModifierReplacement> replacements = new ArrayList<>(2);
            replacements.add(new SingleBlockModifierReplacement(
                    "alloy_furnace_diamond_speedup",
                    pos,
                    new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK),
                    List.of(new RecipeModifier(
                            "duration",
                            RecipeModifier.IOType.INPUT,
                            0.5F,
                            RecipeModifier.Operation.MULTIPLY,
                            false)),
                    "钻石块：配方时间折半",
                    new ItemStack(Holder.direct(Items.DIAMOND_BLOCK, DataComponentMap.EMPTY))));
            replacements.add(new SingleBlockModifierReplacement(
                    "alloy_furnace_gold_doubling",
                    pos,
                    new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                    List.of(new RecipeModifier(
                            "item",
                            RecipeModifier.IOType.OUTPUT,
                            2.0F,
                            RecipeModifier.Operation.MULTIPLY,
                            false)),
                    "金块：产物数量翻倍",
                    new ItemStack(Holder.direct(Items.GOLD_BLOCK, DataComponentMap.EMPTY))));
            map.put(pos, replacements);
        }
        return map;
    }

    public static Machine cracker(Block itemInput, Block itemOutput, Block fluidOutput, Block energyInput) {
        Block controller = ModBlocks.controllerFor(CRACKER_ID).get();
        BlockPredicate port = new BlockPredicate.AnyOf(List.of(
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.FLUID),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY),
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

        PortRequirementSpec portRequirements = PortRequirementSpec.none();
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.builder()
                .minFluidOutput(FluidHatchSize.HUGE)
                .minEnergyInput(EnergyHatchSize.REINFORCED)
                .minItemInput(ItemBusSize.NORMAL)
                .anyItemOutput()
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
        return new DynamicMachine(CRACKER_ID, "裂化器", pattern, controllerSpec, portRequirements, tierRequirements, List.of(), Map.of());
    }

    public static Machine reactor(Block itemInput, Block itemOutput, Block fluidInput, Block fluidOutput, Block energyOutput) {
        Block controller = ModBlocks.controllerFor(REACTOR_ID).get();
        BlockPredicate optionalSlot = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.BLUE_ICE),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.FLUID),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.FLUID),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ENERGY)));

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
                .set('X', new BlockPredicate.OfBlock(Blocks.BLUE_ICE))
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

        PortRequirementSpec portRequirements = PortRequirementSpec.none();
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.none();
        return new DynamicMachine(
                REACTOR_ID,
                "反应堆",
                pattern,
                MachineControllerSpec.defaultsFor(REACTOR_ID),
                MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("blue_ice")),
                portRequirements,
                tierRequirements,
                List.of(),
                Map.of());
    }

    public static Machine thermalSmeltingFurnace() {
        Block controller = ModBlocks.controllerFor(THERMAL_SMELTING_FURNACE_ID).get();
        List<BlockPredicate> basaltSlotBlocks = new ArrayList<>(List.of(
                new BlockPredicate.OfBlock(Blocks.SMOOTH_BASALT),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        for (ParallelTier tier : ParallelTier.values()) {
            basaltSlotBlocks.add(new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(tier.idSuffix()).get()));
        }

        BlockArray pattern = BlockArray.builder()
                .pattern("AAA", "XXX", "XXX", "AAA")
                .pattern("AAA", "X X", "X X", "ADA")
                .pattern("ABA", "XXX", "XXX", "AAA")
                .set('X', new BlockPredicate.OfBlock(Blocks.EMERALD_BLOCK))
                .set('A', new BlockPredicate.AnyOf(basaltSlotBlocks))
                .set('B', new BlockPredicate.OfBlock(controller))
                .set('D', new BlockPredicate.OfBlock(Blocks.REINFORCED_DEEPSLATE))
                .controller('B')
                .build();

        return new DynamicMachine(
                THERMAL_SMELTING_FURNACE_ID,
                "热能冶炼炉",
                pattern,
                MachineControllerSpec.defaultsFor(THERMAL_SMELTING_FURNACE_ID),
                MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("smooth_basalt")),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.builder()
                        .anyItemInput()
                        .anyItemOutput()
                        .anyEnergyInput()
                        .build(),
                List.of(),
                Map.of(),
                Integer.MAX_VALUE,
                true,
                true,
                4);
    }

    private static BlockPredicate portFamily(IOType ioType, PortTierRequirementSpec.PortCategory category) {
        return new BlockPredicate.AnyOf(PortKinds.all().stream()
                .filter(kind -> kind.ioType() == ioType)
                .filter(kind -> matchesCategory(kind, category))
                .<BlockPredicate>map(kind -> new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(kind.id()).get()))
                .toList());
    }

    private static boolean matchesCategory(IOPortKind kind, PortTierRequirementSpec.PortCategory category) {
        return switch (category) {
            case ITEM -> kind.itemBusSize().isPresent();
            case FLUID -> kind.fluidHatchSize().isPresent();
            case ENERGY -> kind.energyHatchSize().isPresent();
        };
    }

    private static BlockPredicate block(String id) {
        return new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)));
    }
}
