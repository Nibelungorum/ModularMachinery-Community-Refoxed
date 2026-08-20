package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.world.item.ItemStack;

/**
 * Built-in machine levels used to exercise the Java level API.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DefaultMachineLevels {
    public static final Identifier THERMAL_SMELTING_COIL_TYPE = MMCR.id("thermal_smelting_coil");
    public static final Identifier COPPER_COIL = MMCR.id("thermal_smelting_coil_copper");
    public static final Identifier IRON_COIL = MMCR.id("thermal_smelting_coil_iron");
    public static final Identifier GOLD_COIL = MMCR.id("thermal_smelting_coil_gold");
    public static final Identifier DIAMOND_COIL = MMCR.id("thermal_smelting_coil_diamond");

    private DefaultMachineLevels() {
    }

    public static void register(MMCRMachineStructuresEvent event) {
        event.registerLevelType(new LevelType(THERMAL_SMELTING_COIL_TYPE, Component.literal("热能冶炼线圈")));
        register(event, COPPER_COIL, 0, Blocks.COPPER_BLOCK, 0.9D);
        register(event, IRON_COIL, 1, Blocks.IRON_BLOCK, 0.8D);
        register(event, GOLD_COIL, 2, Blocks.GOLD_BLOCK, 0.7D);
        register(event, DIAMOND_COIL, 3, Blocks.DIAMOND_BLOCK, 0.6D);
    }

    private static void register(MMCRMachineStructuresEvent event, Identifier id, int priority, Block block, double durationMultiplier) {
        event.registerLevel(new MachineLevel(id, THERMAL_SMELTING_COIL_TYPE, priority,
                new BlockPredicate.OfBlockState(block.defaultBlockState()),
                new ItemStack(Holder.direct(block.asItem(), DataComponentMap.EMPTY)),
                new LevelModifier(durationMultiplier, 1D, 1D, 0, 0)));
    }
}
