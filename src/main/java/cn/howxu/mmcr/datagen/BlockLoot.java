package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Set;

/**
 * Generates self-drop tables for blocks whose registrations are known at data-generation time.
 * Machine controllers can be registered dynamically by startup scripts and supply their own drops.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BlockLoot extends BlockLootSubProvider {
    public BlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.values().stream()
                .map(holder -> holder.get())
                .filter(block -> !(block instanceof MachineControllerBlock))
                .toList();
    }

    @Override
    public void generate() {
        getKnownBlocks().forEach(block -> dropSelf(block));
    }
}
