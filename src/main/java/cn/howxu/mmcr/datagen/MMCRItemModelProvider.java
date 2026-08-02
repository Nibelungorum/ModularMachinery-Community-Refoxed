package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.stream.Stream;

public final class MMCRItemModelProvider extends ModelProvider {
    public MMCRItemModelProvider(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.itemModelOutput.accept(
                MMCRRegistries.CONTROLLER_BLOCK_ITEM.get(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(MMCRRegistries.CONTROLLER_BLOCK.get())));
        itemModels.itemModelOutput.accept(
                MMCRRegistries.CASING_BLOCK_ITEM.get(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(MMCRRegistries.CASING_BLOCK.get())));
        itemModels.itemModelOutput.accept(
                MMCRRegistries.ITEM_BUS_BLOCK_ITEM.get(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(MMCRRegistries.ITEM_BUS_BLOCK.get())));
        itemModels.itemModelOutput.accept(
                MMCRRegistries.FLUID_HATCH_BLOCK_ITEM.get(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(MMCRRegistries.FLUID_HATCH_BLOCK.get())));
        itemModels.itemModelOutput.accept(
                MMCRRegistries.ENERGY_HATCH_BLOCK_ITEM.get(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(MMCRRegistries.ENERGY_HATCH_BLOCK.get())));
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return Stream.empty();
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return Stream.of(
                MMCRRegistries.CONTROLLER_BLOCK_ITEM.get().builtInRegistryHolder(),
                MMCRRegistries.CASING_BLOCK_ITEM.get().builtInRegistryHolder(),
                MMCRRegistries.ITEM_BUS_BLOCK_ITEM.get().builtInRegistryHolder(),
                MMCRRegistries.FLUID_HATCH_BLOCK_ITEM.get().builtInRegistryHolder(),
                MMCRRegistries.ENERGY_HATCH_BLOCK_ITEM.get().builtInRegistryHolder());
    }
}
