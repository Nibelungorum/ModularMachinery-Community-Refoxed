package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Built-in machine definitions registered through the {@link MachineDefinitions}
 * SPI instead of being hardcoded in the registry class.
 */
public final class BuiltinMachines {

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
    private static final Identifier MONSTER_FARM_ID = MMCR.id("monster_farm");

    private BuiltinMachines() {
    }

    /**
     * Register the built-in machine definitions with {@link MachineDefinitions}.
     * Call before {@code MMCR} touches {@code ModBlocks}.
     */
    public static void register() {
        MachineDefinitions.addBuiltinSupplier(() -> {
            MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(BLAST_FURNACE_ID);
            MachineControllerSpec controller = new MachineControllerSpec(
                    defaults.id(),
                    defaults.frontTexture(),
                    defaults.sideTexture(),
                    defaults.topTexture(),
                    defaults.bottomTexture(),
                    false,
                    false,
                    false);
            return MachineRegistration.builder(BLAST_FURNACE_ID)
                    .displayNameKey("machine.mmcr.blast_furnace")
                    .controllerSpec(controller)
                    .recipeFamilyId(BLAST_FURNACE_ID)
                    .allowModifiers(false)
                    .allowMultithreading(true)
                    .allowParallelism(true)
                    .maxParallelAmount(Integer.MAX_VALUE)
                    .build();
        });
        MachineDefinitions.addBuiltinSupplier(() -> {
            MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(ALLOY_FURNACE_ID);
            MachineControllerSpec controller = new MachineControllerSpec(
                    defaults.id(),
                    defaults.frontTexture(),
                    defaults.sideTexture(),
                    defaults.topTexture(),
                    defaults.bottomTexture(),
                    false,
                    false,
                    false);
            return MachineRegistration.builder(ALLOY_FURNACE_ID)
                    .displayNameKey("machine.mmcr.alloy_furnace")
                    .controllerSpec(controller)
                    .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("bricks")))
                    .recipeFamilyId(ALLOY_FURNACE_ID)
                    .allowModifiers(true)
                    .expandableStructure()
                    .build();
        });
        MachineDefinitions.addBuiltinSupplier(() -> {
            MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(CRACKER_ID);
            MachineControllerSpec controller = new MachineControllerSpec(
                    defaults.id(),
                    defaults.frontTexture(),
                    defaults.sideTexture(),
                    defaults.topTexture(),
                    defaults.bottomTexture(),
                    true,
                    true,
                    false);
            return MachineRegistration.builder(CRACKER_ID)
                    .displayNameKey("machine.mmcr.cracker")
                    .controllerSpec(controller)
                    .recipeFamilyId(CRACKER_ID)
                    .allowModifiers(false)
                    .build();
        });
        MachineDefinitions.addBuiltinSupplier(() -> {
            MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(REACTOR_ID);
            MachineControllerSpec controller = new MachineControllerSpec(
                    defaults.id(),
                    defaults.frontTexture(),
                    defaults.sideTexture(),
                    defaults.topTexture(),
                    defaults.bottomTexture(),
                    false,
                    false,
                    false);
            return MachineRegistration.builder(REACTOR_ID)
                    .displayNameKey("machine.mmcr.reactor")
                    .controllerSpec(controller)
                    .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("blue_ice")))
                    .recipeFamilyId(REACTOR_ID)
                    .allowModifiers(false)
                    .build();
        });
        MachineDefinitions.addBuiltinSupplier(() -> {
            return MachineRegistration.builder(THERMAL_SMELTING_FURNACE_ID)
                    .displayNameKey("machine.mmcr.thermal_smelting_furnace")
                    .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("smooth_basalt")))
                    .recipeFamilyId(THERMAL_SMELTING_FURNACE_ID)
                    .allowModifiers(false)
                    .allowMultithreading(true)
                    .allowParallelism(true)
                    .maxParallelAmount(Integer.MAX_VALUE)
                    .build();
        });
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(PURPUR_FURNACE_ID)
                .displayNameKey("machine.mmcr.purpur_furnace")
                .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("end_stone_bricks")))
                .recipeFamilyId(PURPUR_FURNACE_ID)
                .allowModifiers(false)
                .allowMultithreading(true)
                .allowParallelism(true)
                .maxParallelAmount(4)
                .runningSound(Identifier.withDefaultNamespace("block.furnace.fire_crackle"))
                .finishSound(Identifier.withDefaultNamespace("entity.ender_dragon.growl"))
                .smartInterfaceType(new SmartInterfaceType("Mode", 1F, 3F, 0, SmartInterfaceType.ValueType.INTEGER))
                .smartInterfaceType(new SmartInterfaceType("Temperature", 400F, 6800F, 1, SmartInterfaceType.ValueType.INTEGER))
                .smartInterfaceType(new SmartInterfaceType("ConversionRate", 0.5F, 0.0F, 1.0F, 2, SmartInterfaceType.ValueType.FLOAT))
                .build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(DISTILLATION_TOWER_ID)
                .displayNameKey("machine.mmcr.distillation_tower")
                .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("polished_blackstone")))
                .recipeFamilyId(DISTILLATION_TOWER_ID)
                .allowModifiers(false)
                .allowMultithreading(true)
                .allowParallelism(true)
                .maxParallelAmount(4)
                .expandableStructure()
                .build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(ECO_MATRIX_ID)
                .displayNameKey("machine.mmcr.eco_matrix")
                .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("sea_lantern")))
                .recipeFamilyId(ECO_MATRIX_ID)
                .allowModifiers(false)
                .expandableStructure()
                .build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(SPACE_ELEVATOR_ID)
                .displayNameKey("machine.mmcr.space_elevator")
                .appearance(new MachineAppearanceSpec(Identifier.withDefaultNamespace("smooth_quartz"),
                        Identifier.withDefaultNamespace("block/quartz_block_bottom"),
                        Identifier.withDefaultNamespace("block/quartz_block_bottom")))
                .recipeFamilyId(SPACE_ELEVATOR_ID)
                .allowModifiers(false)
                .host(SPACE_REASSEMBLER_ID)
                .pattern(couplerPattern(3))
                .build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(SPACE_REASSEMBLER_ID)
                .displayNameKey("machine.mmcr.space_reassembler")
                .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("quartz_pillar")))
                .recipeFamilyId(SPACE_REASSEMBLER_ID)
                .allowModifiers(false)
                .module()
                .pattern(couplerPattern(1))
                .build());
        MachineDefinitions.addBuiltinSupplier(() -> {
            MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(MONSTER_FARM_ID);
            MachineControllerSpec controller = new MachineControllerSpec(
                    defaults.id(),
                    defaults.frontTexture(),
                    defaults.sideTexture(),
                    defaults.topTexture(),
                    defaults.bottomTexture(),
                    true,
                    false,
                    true);
            return MachineRegistration.builder(MONSTER_FARM_ID)
                    .controllerSpec(controller)
                    .build();
        });
    }

    public static Map<Identifier, MachineDefinition> publicDefinitions() {
        MachineDefinition blast = MachineBuilder.machine(BLAST_FURNACE_ID)
                .displayNameKey("machine.mmcr.blast_furnace")
                .pattern(pattern -> pattern.layer("XXX", "XCX", "XXX")
                        .where('X', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.IRON_BLOCK))
                        .where('C', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.BLAST_FURNACE))
                        .controller('C'))
                .portTiers(tiers -> tiers.minEnergyInput(PortTiers.EnergyTier.LUDICROUS)
                        .minItemInput(PortTiers.ItemTier.NORMAL).anyItemOutput())
                .maxParallelism(Integer.MAX_VALUE)
                .parallelizable(true)
                .factory(factory -> factory.hasFactory(true).threadLimit(4))
                .build();
        RecipeModifier modifier = new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 0.5F,
                RecipeModifier.Operation.MULTIPLY, false);
        MachineDefinition alloy = MachineBuilder.machine(ALLOY_FURNACE_ID)
                .displayNameKey("machine.mmcr.alloy_furnace")
                .appearance(appearance -> appearance.machineBasicBlock(Identifier.withDefaultNamespace("bricks")))
                .pattern(pattern -> pattern.layer("XXX", "XMX", "XCX")
                        .where('X', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.BRICKS))
                        .where('M', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.BLAST_FURNACE))
                        .where('C', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.FURNACE))
                        .controller('C'))
                .requirements(requirements -> requirements.modifier('M', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.DIAMOND_BLOCK),
                        List.of(modifier), new ItemStack(Blocks.DIAMOND_BLOCK)))
                .stage(stage -> stage.extension().pattern(pattern -> pattern.layer("XXX", "XMX", "XCX")
                        .where('X', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.BRICKS))
                        .where('M', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.BLAST_FURNACE))
                        .where('C', cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block(Blocks.FURNACE))
                        .controller('C')))
                .build();
        return Map.of(blast.id(), blast, alloy.id(), alloy);
    }

    private static BlockArray couplerPattern(int count) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (int x = 0; x < count; x++) pattern.put(new BlockPos(x, 0, 0), BlockPredicate.machineCoupler());
        return new BlockArray(pattern);
    }

}
