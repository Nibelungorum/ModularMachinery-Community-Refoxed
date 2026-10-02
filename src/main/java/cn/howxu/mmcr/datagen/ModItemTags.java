package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.MachineCasingBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;

import java.util.concurrent.CompletableFuture;

public final class ModItemTags extends ItemTagsProvider {
    public ModItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                       CompletableFuture<TagsProvider.TagLookup<Block>> blockTags) {
        super(output, lookupProvider, blockTags, MMCR.MODID, null);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(Tags.Items.INGOTS).add(ModItems.MODULARIUM.get());
        tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "ingots/modularium"))).add(ModItems.MODULARIUM.get());
        tag(itemTag("machine_casings")).add(ModBlocks.BASIC_CASING.get().asItem());
        tag(itemTag("machines")).add(ModBlocks.BLOCKS.values().stream()
                .map(holder -> holder.get())
                .filter(ModItemTags::isFixedMachineComponent)
                .map(Block::asItem)
                .toArray(Item[]::new));
        tag(itemTag("ports")).add(ModBlocks.SMART_INTERFACE.get().asItem(), ModBlocks.NETWORK_INTERFACE.get().asItem());
        PortKinds.all().forEach(this::addPortTags);
        tag(itemTag("terminals")).add(ModItems.TERMINAL.get());
        tag(itemTag("tools")).add(ModItems.MULTIBLOCK_DETECTOR.get(), ModItems.THREAD_DISPERSER.get());
        tag(itemTag("configuration_cards")).add(ModItems.KEY_CARD.get());
        tag(itemTag("blueprints")).add(ModItems.BLUEPRINT.get());
    }

    private void addPortTags(IOPortKind kind) {
        PortTagSet tags = PortTagSet.forKind(kind);
        for (ResourceLocation tagId : tags.tags()) {
            if (tags.optionalEntries()) {
                tag(TagKey.create(Registries.ITEM, tagId)).add(TagEntry.optionalElement(MMCR.id(kind.id())));
            } else {
                tag(TagKey.create(Registries.ITEM, tagId)).add(ModBlocks.BLOCKS.get(kind.id()).get().asItem());
            }
        }
    }

    private static boolean isFixedMachineComponent(Block block) {
        return !(block instanceof MachineCasingBlock
                || block instanceof MachineControllerBlock
                || block instanceof ParallelControllerBlock
                || block instanceof FactorySchedulerBlock
                || block instanceof MachinePort);
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, MMCR.id(path));
    }
}
