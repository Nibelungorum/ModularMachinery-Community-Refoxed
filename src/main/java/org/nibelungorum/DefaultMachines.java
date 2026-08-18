package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
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
    private static final Identifier PURPUR_FURNACE_ID = MMCR.id("purpur_furnace");
    private static final Identifier DISTILLATION_TOWER_ID = MMCR.id("distillation_tower");
    private static final Identifier ECO_MATRIX_ID = MMCR.id("eco_matrix");
    private static final Identifier SPACE_ELEVATOR_ID = MMCR.id("space_elevator");
    private static final Identifier SPACE_REASSEMBLER_ID = MMCR.id("space_reassembler");

    private DefaultMachines() {
    }

    public static void ensureRegistered() {
        MachineStructureRegistry.replaceDynamic(structures());
    }

    public static void registerStructures(DynamicContentReloadService.Candidate candidate) {
        structures().values().forEach(candidate::registerStructure);
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
        structures.put(THERMAL_SMELTING_FURNACE_ID, thermalSmeltingFurnaceStructure());
        structures.put(PURPUR_FURNACE_ID, structureOf(purpurFurnace()));
        structures.put(DISTILLATION_TOWER_ID, distillationTowerStructure());
        structures.put(ECO_MATRIX_ID, ecoMatrixStructure());
        structures.put(SPACE_ELEVATOR_ID, spaceElevatorStructure());
        structures.put(SPACE_REASSEMBLER_ID, spaceReassemblerStructure());
        return Map.copyOf(structures);
    }

    private static MachineStructureDefinition structureOf(Machine machine) {
        return new MachineStructureDefinition(machine.registryName(), machine.structureStages().stream()
                .map(stage -> new Declaration(Declaration.Kind.FULL, stage.pattern(), stage.portRequirements(),
                        stage.portTierRequirements(), stage.dynamicPatterns(), stage.requirements(),
                        stage.modifierReplacements(), stage.levelSlots()))
                .toList());
    }

    private static MachineStructureDefinition thermalSmeltingFurnaceStructure() {
        Machine machine = thermalSmeltingFurnace();
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
        for (var entry : machine.pattern().pattern().entrySet()) {
            if (isThermalSmeltingCoil(entry.getValue())) {
                levelSlots.put(entry.getKey(), DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE);
            }
        }
        return new MachineStructureDefinition(
                machine.registryName(),
                machine.pattern(),
                machine.portRequirements(),
                machine.portTierRequirements(),
                machine.dynamicPatterns(),
                Map.of(),
                levelSlots);
    }

    private static boolean isThermalSmeltingCoil(BlockPredicate predicate) {
        return predicate.matches(Blocks.COPPER_BLOCK.defaultBlockState())
                && predicate.matches(Blocks.IRON_BLOCK.defaultBlockState())
                && predicate.matches(Blocks.GOLD_BLOCK.defaultBlockState())
                && predicate.matches(Blocks.DIAMOND_BLOCK.defaultBlockState());
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
        BlockPredicate casingOrIoPort = new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlock(casing), ioPort));
        List<BlockPredicate> parallelSlotBlocks = new ArrayList<>();
        parallelSlotBlocks.add(new BlockPredicate.OfBlock(casing));
        parallelSlotBlocks.add(ioPort);
        for (ParallelTier tier : ParallelTier.values()) {
            parallelSlotBlocks.add(new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(tier.idSuffix()).get()));
        }
        BlockPredicate casingOrParallelController = new BlockPredicate.AnyOf(parallelSlotBlocks);
        BlockPredicate casingOrFactoryController = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(casing),
                ioPort,
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));

        BlockArray pattern = BlockArray.builder()
                .pattern("AXA", "XIX", "XXX")
                .pattern("XXX", "I I", "XBX")
                .pattern("AXA", "XCX", "XXX")
                .set('X', casingOrIoPort)
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
                "machine.mmcr.blast_furnace",
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
                4,
                List.of());
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
                "machine.mmcr.alloy_furnace",
                pattern,
                MachineControllerSpec.defaultsFor(ALLOY_FURNACE_ID),
                MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("bricks")),
                portRequirements,
                tierRequirements,
                List.of(),
                Map.of(),
                1,
                false,
                false,
                1,
                List.of(),
                List.of(new MachineStructureStage(1, pattern, portRequirements, tierRequirements,
                        List.of(), alloyFurnaceRequirements(), Map.of(), Map.of())));
    }

    /**
     * 合金炉 M 位置允许两种 modifier 方块：
     * <ul>
     *     <li>diamond block -> recipe duration x 0.5</li>
     *     <li>gold block -> output item count x 2</li>
     * </ul>
     * 原方块（高炉）仍为默认可选方块，不挂载 modifier。
     */
    private static MachineStructureRequirements alloyFurnaceRequirements() {
        return MachineStructureRequirements.builder()
                .modifier('M', new SingleBlockModifierReplacement(
                    "alloy_furnace_diamond_speedup",
                    new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK),
                    List.of(new RecipeModifier(
                            "duration",
                            RecipeModifier.IOType.INPUT,
                            0.5F,
                            RecipeModifier.Operation.MULTIPLY,
                            false)),
                    new ItemStack(Holder.direct(Items.DIAMOND_BLOCK, DataComponentMap.EMPTY))))
                .modifier('M', new SingleBlockModifierReplacement(
                    "alloy_furnace_gold_doubling",
                    new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                    List.of(new RecipeModifier(
                            "item",
                            RecipeModifier.IOType.OUTPUT,
                            2.0F,
                            RecipeModifier.Operation.MULTIPLY,
                            false)),
                    new ItemStack(Holder.direct(Items.GOLD_BLOCK, DataComponentMap.EMPTY))))
                .build();
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
                .pattern("AAA", "AAA", "AAA")
                .pattern("XBX", "B B", "XBX")
                .pattern("XDX", "D D", "XDX")
                .pattern("XEX", "ECE", "XEX")
                .set('X', new BlockPredicate.OfBlock(Blocks.POLISHED_DIORITE))
                .set('A', new BlockPredicate.OfBlock(Blocks.POLISHED_ANDESITE))
                .set('B', port)
                .set('D', new BlockPredicate.OfBlock(Blocks.BLUE_ICE))
                .set('E', new BlockPredicate.OfBlock(Blocks.WEATHERED_COPPER))
                .set('C', new BlockPredicate.OfBlock(controller))
                .controller('C')
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
                false);
        return new DynamicMachine(CRACKER_ID, "machine.mmcr.cracker", pattern, controllerSpec, portRequirements, tierRequirements, List.of(), Map.of());
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
                "machine.mmcr.reactor",
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
                .set('X', new BlockPredicate.AnyOf(List.of(
                        new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK),
                        new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                        new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                        new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK))))
                .set('A', new BlockPredicate.AnyOf(basaltSlotBlocks))
                .set('B', new BlockPredicate.OfBlock(controller))
                .set('D', new BlockPredicate.OfBlock(Blocks.REINFORCED_DEEPSLATE))
                .controller('B')
                .build();

        return new DynamicMachine(
                THERMAL_SMELTING_FURNACE_ID,
                "machine.mmcr.thermal_smelting_furnace",
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
                4,
                List.of());
    }

    private static Machine purpurFurnace() {
        Block controller = ModBlocks.controllerFor(PURPUR_FURNACE_ID).get();
        List<BlockPredicate> interfaceSlots = new ArrayList<>(List.of(
                new BlockPredicate.OfBlock(Blocks.PURPUR_PILLAR),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()),
                new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get())));
        for (ParallelTier tier : ParallelTier.values()) {
            interfaceSlots.add(new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(tier.idSuffix()).get()));
        }

        BlockArray pattern = BlockArray.builder()
                .pattern(" AAAAA ", "       ", "       ", "       ", "  GGG  ", "       ", "       ", "       ")
                .pattern("AAXXXAA", "  BBB  ", "  EEE  ", "  FFF  ", " GBBBG ", " HHHHH ", "       ", "       ")
                .pattern("AXXXXXA", " B   B ", " E   E ", " F   F ", "GB   BG", " HXXXH ", "  GBG  ", "   I   ")
                .pattern("AXXXXXA", " B   B ", " E   E ", " F   F ", "GB   BG", " HX XH ", "  B B  ", "  I I  ")
                .pattern("AXXXXXA", " B   B ", " E   E ", " F   F ", "GB   BG", " HXXXH ", "  GBG  ", "   I   ")
                .pattern("AAXXXAA", "  BDB  ", "  EEE  ", "  FFF  ", " GBBBG ", " HHHHH ", "       ", "       ")
                .pattern(" AAAAA ", "       ", "       ", "       ", "  GGG  ", "       ", "       ", "       ")
                .set('X', new BlockPredicate.OfBlock(Blocks.END_STONE_BRICKS))
                .set('A', new BlockPredicate.OfBlock(Blocks.END_STONE_BRICK_STAIRS))
                .set('B', new BlockPredicate.AnyOf(interfaceSlots))
                .set('D', new BlockPredicate.OfBlock(controller))
                .set('E', new BlockPredicate.OfBlock(Blocks.PURPLE_TERRACOTTA))
                .set('F', new BlockPredicate.OfBlock(Blocks.PURPUR_BLOCK))
                .set('G', new BlockPredicate.OfBlock(Blocks.END_STONE_BRICK_SLAB))
                .set('H', new BlockPredicate.OfBlock(Blocks.PURPUR_STAIRS))
                .set('I', new BlockPredicate.OfBlock(Blocks.PURPUR_SLAB))
                .controller('D')
                .build();

        return new DynamicMachine(PURPUR_FURNACE_ID, "machine.mmcr.purpur_furnace", pattern,
                MachineControllerSpec.defaultsFor(PURPUR_FURNACE_ID),
                MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("end_stone_bricks")),
                PortRequirementSpec.none(), PortTierRequirementSpec.builder()
                        .anyItemInput()
                        .anyItemOutput()
                        .anyEnergyInput()
                        .build(),
                List.of(), Map.of(), 4, true, true, 1, List.of());
    }

    private static MachineStructureDefinition distillationTowerStructure() {
        Block controller = ModBlocks.controllerFor(DISTILLATION_TOWER_ID).get();
        BlockPredicate port = new BlockPredicate.AnyOf(PortKinds.all().stream()
                .filter(kind -> !(kind.ioType() == IOType.OUTPUT && kind.itemBusSize().orElse(null) == ItemBusSize.TINY))
                .<BlockPredicate>map(kind -> new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(kind.id()).get()))
                .toList());
        BlockPredicate a = new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlock(Blocks.DEEPSLATE_BRICKS), port));
        BlockPredicate c = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.DEEPSLATE_BRICKS),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus_tiny").get())));
        PortTierRequirementSpec requirements = PortTierRequirementSpec.builder()
                .anyItemInput().anyItemOutput().anyEnergyInput().build();
        return new MachineStructureDefinition(DISTILLATION_TOWER_ID, List.of(
                declaration(distillationTowerLevelOne(a, c, controller), requirements),
                declaration(distillationTowerLevelTwo(a, c, controller), requirements),
                declaration(distillationTowerLevelThree(a, c, controller), requirements)));
    }

    private static BlockArray distillationTowerLevelOne(BlockPredicate a, BlockPredicate c, Block controller) {
        return BlockArray.builder()
                .pattern("  XXX  ", "  AAA  ", "       ", "       ")
                .pattern(" XXXXX ", " B   B ", "  ACA  ", "       ")
                .pattern("XXXXXXX", "A     A", " B   B ", "  DDD  ")
                .pattern("XXXXXXX", "A     A", " B   B ", "  DDD  ")
                .pattern("XXXXXXX", "A     A", " B   B ", "  DDD  ")
                .pattern(" XXXXX ", " B   B ", "  BBB  ", "       ")
                .pattern("  XXX  ", "  BEB  ", "       ", "       ")
                .set('X', new BlockPredicate.OfBlock(Blocks.POLISHED_BLACKSTONE))
                .set('A', a)
                .set('B', new BlockPredicate.OfBlock(Blocks.POLISHED_BLACKSTONE_BRICKS))
                .set('C', c)
                .set('D', new BlockPredicate.OfBlock(Blocks.GILDED_BLACKSTONE))
                .set('E', new BlockPredicate.OfBlock(controller))
                .controller('E')
                .build();
    }

    private static BlockArray distillationTowerLevelTwo(BlockPredicate a, BlockPredicate c, Block controller) {
        return BlockArray.builder()
                .pattern("  XXX  ", "  AAA  ", "       ", "       ", "       ")
                .pattern(" XXXXX ", " B   B ", "  ACA  ", "  ACA  ", "       ")
                .pattern("XXXXXXX", "A     A", " B   B ", " B   B ", "  DDD  ")
                .pattern("XXXXXXX", "A     A", " B   B ", " B   B ", "  DDD  ")
                .pattern("XXXXXXX", "A     A", " B   B ", " B   B ", "  DDD  ")
                .pattern(" XXXXX ", " B   B ", "  BBB  ", "  BBB  ", "       ")
                .pattern("  XXX  ", "  BEB  ", "       ", "       ", "       ")
                .set('X', new BlockPredicate.OfBlock(Blocks.POLISHED_BLACKSTONE))
                .set('A', a)
                .set('B', new BlockPredicate.OfBlock(Blocks.POLISHED_BLACKSTONE_BRICKS))
                .set('C', c)
                .set('D', new BlockPredicate.OfBlock(Blocks.GILDED_BLACKSTONE))
                .set('E', new BlockPredicate.OfBlock(controller))
                .controller('E')
                .build();
    }

    private static BlockArray distillationTowerLevelThree(BlockPredicate a, BlockPredicate c, Block controller) {
        return BlockArray.builder()
                .pattern("  XXX  ", "  AAA  ", "       ", "       ", "       ", "       ")
                .pattern(" XXXXX ", " B   B ", "  ACA  ", "  ACA  ", "  ACA  ", "       ")
                .pattern("XXXXXXX", "A     A", " B   B ", " B   B ", " B   B ", "  DDD  ")
                .pattern("XXXXXXX", "A     A", " B   B ", " B   B ", " B   B ", "  DDD  ")
                .pattern("XXXXXXX", "A     A", " B   B ", " B   B ", " B   B ", "  DDD  ")
                .pattern(" XXXXX ", " B   B ", "  BBB  ", "  BBB  ", "  BBB  ", "       ")
                .pattern("  XXX  ", "  BEB  ", "       ", "       ", "       ", "       ")
                .set('X', new BlockPredicate.OfBlock(Blocks.POLISHED_BLACKSTONE))
                .set('A', a)
                .set('B', new BlockPredicate.OfBlock(Blocks.POLISHED_BLACKSTONE_BRICKS))
                .set('C', c)
                .set('D', new BlockPredicate.OfBlock(Blocks.GILDED_BLACKSTONE))
                .set('E', new BlockPredicate.OfBlock(controller))
                .controller('E')
                .build();
    }

    private static MachineStructureDefinition ecoMatrixStructure() {
        Block controller = ModBlocks.controllerFor(ECO_MATRIX_ID).get();
        BlockPredicate a = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.RESIN_BRICKS),
                new BlockPredicate.AnyOf(PortKinds.all().stream()
                        .<BlockPredicate>map(kind -> new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(kind.id()).get()))
                        .toList())));
        PortTierRequirementSpec requirements = PortTierRequirementSpec.builder().anyEnergyInput().build();
        return new MachineStructureDefinition(ECO_MATRIX_ID, List.of(
                declaration(ecoMatrixPattern(3, a, controller), requirements),
                declaration(ecoMatrixPattern(4, a, controller), requirements),
                declaration(ecoMatrixPattern(5, a, controller), requirements)));
    }

    private static MachineStructureDefinition spaceElevatorStructure() {
        Block controller = ModBlocks.controllerFor(SPACE_ELEVATOR_ID).get();
        BlockPredicate interfaceSlot = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.SMOOTH_QUARTZ),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY)));
        BlockArray pattern = BlockArray.builder()
                .pattern("        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("      XXXXX      ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("     XXAAAXX     ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("    XXXAAAXXX    ", "        B        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("   XXXXAAAXXXX   ", "                 ", "                 ", "                 ", "                 ", "        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("  XXXXXXXXXXXXX  ", "                 ", "                 ", "                 ", "        X        ", "       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern(" XXAAAXXXXXAAAXX ", "       XXX       ", "       DDD       ", "       XXX       ", "       XXX       ", "      XXXXX      ", "       XXX       ", "       X X       ", "                 ", "                 ", "                 ", "                 ")
                .pattern("XXXAAAXXXXXAAAXXX", "    B  X X  B    ", "       D D       ", "       X X       ", "      XX XX      ", "     XXX XXX     ", "       XXX       ", "        X        ", "        X        ", "        X        ", "        X        ", "        X        ")
                .pattern(" XXAAAXXXXXAAAXX ", "       XXX       ", "       DED       ", "       XXX       ", "       XXX       ", "      XXXXX      ", "       XXX       ", "       X X       ", "                 ", "                 ", "                 ", "                 ")
                .pattern("  XXXXXXXXXXXXX  ", "                 ", "                 ", "                 ", "        X        ", "       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("   XXXXXXXXXXX   ", "                 ", "                 ", "                 ", "                 ", "        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("    XXXXXXXXX    ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("     XXXXXXX     ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("      XXXXX      ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .pattern("        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                .set('X', new BlockPredicate.OfBlock(Blocks.SMOOTH_QUARTZ))
                .set('A', new BlockPredicate.OfBlock(Blocks.AMETHYST_BLOCK))
                .set('B', BlockPredicate.machineCoupler())
                .set('D', interfaceSlot)
                .set('E', new BlockPredicate.OfBlock(controller))
                .controller('E')
                .build();
        return new MachineStructureDefinition(SPACE_ELEVATOR_ID, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.builder().anyItemInput().anyEnergyInput().build(), List.of(), Map.of());
    }

    private static MachineStructureDefinition spaceReassemblerStructure() {
        Block controller = ModBlocks.controllerFor(SPACE_REASSEMBLER_ID).get();
        BlockPredicate interfaceSlot = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY)));
        BlockArray pattern = BlockArray.builder()
                .pattern("AAA", "XBX", "XBX", "XDX")
                .pattern("AAA", "BEB", "B B", "DDD")
                .pattern("AAA", "XFX", "XBX", "XDX")
                .set('X', new BlockPredicate.OfBlock(Blocks.QUARTZ_PILLAR))
                .set('A', new BlockPredicate.OfBlock(Blocks.AMETHYST_BLOCK))
                .set('B', interfaceSlot)
                .set('D', new BlockPredicate.OfBlock(Blocks.GLASS))
                .set('E', BlockPredicate.machineCoupler())
                .set('F', new BlockPredicate.OfBlock(controller))
                .controller('F')
                .build();
        return new MachineStructureDefinition(SPACE_REASSEMBLER_ID, pattern, PortRequirementSpec.none(),
                PortTierRequirementSpec.builder().anyItemInput().anyItemOutput().anyEnergyInput().build(), List.of(), Map.of());
    }

    private static BlockArray ecoMatrixPattern(int width, BlockPredicate a, Block controller) {
        String x = "X".repeat(width);
        String aRow = "A".repeat(width);
        String middle = "A" + " ".repeat(width - 2) + "A";
        String controllerRow = "A" + "B" + "A".repeat(width - 2);
        return BlockArray.builder()
                .pattern(x, aRow, x)
                .pattern(x, middle, x)
                .pattern(x, controllerRow, x)
                .set('X', new BlockPredicate.OfBlock(Blocks.SEA_LANTERN))
                .set('A', a)
                .set('B', new BlockPredicate.OfBlock(controller))
                .controller('B')
                .build();
    }

    private static Declaration declaration(BlockArray pattern, PortTierRequirementSpec requirements) {
        return new Declaration(Declaration.Kind.FULL, pattern, PortRequirementSpec.none(), requirements,
                List.of(), Map.of(), Map.of());
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
