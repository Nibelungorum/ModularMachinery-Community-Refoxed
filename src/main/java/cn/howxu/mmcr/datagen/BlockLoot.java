package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

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
        for (Block block : getKnownBlocks()) {
            LootTable.Builder builder = createSingleItemTable(block);
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (MMCR.MODID.equals(id.getNamespace())
                    && ("ae2_me_pattern_interface".equals(id.getPath())
                    || "eae_me_extended_pattern_interface".equals(id.getPath())
                    || "eaep_me_mirror_pattern_interface".equals(id.getPath()))) {
                builder.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                        // One compressed owner for both legacy escrow and every native return slot.
                        .include(DataComponents.BLOCK_ENTITY_DATA));
            }
            for (ICondition condition : conditionsFor(block)) {
                builder.withCondition(condition);
            }
            add(block, builder);
        }
    }

    private static List<ModLoadedCondition> conditionsFor(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (!MMCR.MODID.equals(id.getNamespace())) return List.of();
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id.getPath()))
                .findFirst()
                .map(kind -> kind.modDependencies().stream()
                        .map(ModLoadedCondition::new)
                        .toList())
                .orElse(List.of());
    }
}
