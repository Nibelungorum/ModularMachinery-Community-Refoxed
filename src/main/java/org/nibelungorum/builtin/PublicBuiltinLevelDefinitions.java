package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.DisplayStack;
import cn.howxu.mmcr.api.publicapi.machine.LevelModifier;
import cn.howxu.mmcr.api.publicapi.machine.LevelType;
import cn.howxu.mmcr.api.publicapi.machine.MachineLevel;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;

/** Public built-in machine level definitions.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = "mmcr")
public final class PublicBuiltinLevelDefinitions {
    public static final Identifier THERMAL_SMELTING_COIL_TYPE = id("thermal_smelting_coil");
    public static final Identifier COPPER_COIL = id("thermal_smelting_coil_copper");
    public static final Identifier IRON_COIL = id("thermal_smelting_coil_iron");
    public static final Identifier GOLD_COIL = id("thermal_smelting_coil_gold");
    public static final Identifier DIAMOND_COIL = id("thermal_smelting_coil_diamond");

    private PublicBuiltinLevelDefinitions() {
    }

    private static Identifier id(String path) {
        return Identifier.parse("mmcr:" + path);
    }

    @SubscribeEvent
    public static void register(MMCRMachineStructuresEvent event) {
        if (FMLLoader.getCurrent().isProduction()
                || event.levelTypes().containsKey(THERMAL_SMELTING_COIL_TYPE)) return;
        event.registerLevelType(new LevelType(THERMAL_SMELTING_COIL_TYPE, Component.literal("热能冶炼线圈")));
        register(event, COPPER_COIL, 0, Blocks.COPPER_BLOCK, 0.9D);
        register(event, IRON_COIL, 1, Blocks.IRON_BLOCK, 0.8D);
        register(event, GOLD_COIL, 2, Blocks.GOLD_BLOCK, 0.7D);
        register(event, DIAMOND_COIL, 3, Blocks.DIAMOND_BLOCK, 0.6D);
    }

    private static void register(MMCRMachineStructuresEvent event, Identifier id, int priority, Block block, double durationMultiplier) {
        if (event.levels().containsKey(id)) return;
        event.registerLevel(new MachineLevel(id, THERMAL_SMELTING_COIL_TYPE, priority,
                BlockPredicate.blockState(block.defaultBlockState()),
                DisplayStack.of(new ItemStack(Holder.direct(block.asItem(), DataComponentMap.EMPTY))),
                new LevelModifier(durationMultiplier, 1D, 1D, 0, 0)));
    }
}
