package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import net.minecraft.resources.Identifier;

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
                    .localizedName("高炉")
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
                    .localizedName("合金炉")
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
                    true);
            return MachineRegistration.builder(CRACKER_ID)
                    .localizedName("裂化器")
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
                    .localizedName("反应堆")
                    .controllerSpec(controller)
                    .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("blue_ice")))
                    .recipeFamilyId(REACTOR_ID)
                    .allowModifiers(false)
                    .build();
        });
        MachineDefinitions.addBuiltinSupplier(() -> {
            return MachineRegistration.builder(THERMAL_SMELTING_FURNACE_ID)
                    .localizedName("热能冶炼炉")
                    .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("smooth_basalt")))
                    .recipeFamilyId(THERMAL_SMELTING_FURNACE_ID)
                    .allowModifiers(false)
                    .allowMultithreading(true)
                    .allowParallelism(true)
                    .maxParallelAmount(Integer.MAX_VALUE)
                    .build();
        });
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(PURPUR_FURNACE_ID)
                .localizedName("紫珀炉")
                .appearance(MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("end_stone_bricks")))
                .recipeFamilyId(PURPUR_FURNACE_ID)
                .allowModifiers(false)
                .allowMultithreading(true)
                .allowParallelism(true)
                .maxParallelAmount(4)
                .smartInterfaceType(new SmartInterfaceType("Mode", 1F, 0,
                        "模式", "Mode: %.0f", "1: 钻石, 2: 金锭, 3: 铁锭", "模式不匹配", "模式: %.0f", 1))
                .build());
    }
}
