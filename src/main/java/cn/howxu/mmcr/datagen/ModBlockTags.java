package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineCasingBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.concurrent.CompletableFuture;

/**
 * Generates block mining-tool tags for MMCR blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ModBlockTags extends BlockTagsProvider {
    public ModBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, MMCR.MODID, null);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider provider) {
        // 新增了扳手功能 因此这个易于挖掘的标签可以去掉了
        // tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.BLOCKS.values().stream()
        //         .map(holder -> holder.get())
        //         .filter(block -> !(block instanceof MachineControllerBlock))
        //         .toArray(Block[]::new));
        tag(blockTag("machine_casings")).add(ModBlocks.BASIC_CASING.get());
        tag(blockTag("machines")).add(ModBlocks.BLOCKS.values().stream()
                .map(DeferredHolder::get)
                .filter(ModBlockTags::isFixedMachineComponent)
                .toArray(Block[]::new));
        tag(blockTag("ports")).add(ModBlocks.SMART_INTERFACE.get(), ModBlocks.NETWORK_INTERFACE.get());
        PortKinds.all().forEach(this::addPortTags);
    }

    private void addPortTags(IOPortKind kind) {
        PortTagSet tags = PortTagSet.forKind(kind);
        for (ResourceLocation tagId : tags.tags()) {
            if (tags.optionalEntries()) {
                tag(TagKey.create(Registries.BLOCK, tagId)).add(TagEntry.optionalElement(MMCR.id(kind.id())));
            } else {
                tag(TagKey.create(Registries.BLOCK, tagId)).add(ModBlocks.BLOCKS.get(kind.id()).get());
            }
        }
    }

    private static boolean isFixedMachineComponent(Block block) {
        return !(block instanceof MachineCasingBlock
                || block instanceof MachineControllerBlock
                || block instanceof ParallelControllerBlock
                || block instanceof FactorySchedulerBlock
                || block instanceof IOPortBlock);
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, MMCR.id(path));
    }
}
