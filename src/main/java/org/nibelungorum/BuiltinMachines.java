package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

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
                .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("smooth_quartz")))
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
    }

    private static BlockArray couplerPattern(int count) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (int x = 0; x < count; x++) pattern.put(new BlockPos(x, 0, 0), BlockPredicate.machineCoupler());
        return new BlockArray(pattern);
    }
}
