package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

import java.util.concurrent.CompletableFuture;

public final class ModItemTags extends ItemTagsProvider {
    public ModItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, MMCR.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ItemTags.create(Identifier.fromNamespaceAndPath("c", "ingots/modularium"))).add(ModItems.MODULARIUM.get());
    }
}
