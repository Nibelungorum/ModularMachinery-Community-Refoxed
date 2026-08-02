package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.stream.Stream;

public final class MMCRBlockStateProvider extends ModelProvider {
    public MMCRBlockStateProvider(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        TexturedModel.Provider casingModel = block -> TexturedModel.createAllSame(
                TextureMapping.getBlockTexture(MMCRRegistries.CASING_BLOCK.get()));
        blockModels.createTrivialBlock(MMCRRegistries.CASING_BLOCK.get(), casingModel);
        blockModels.createTrivialBlock(MMCRRegistries.ITEM_BUS_BLOCK.get(), casingModel);
        blockModels.createTrivialBlock(MMCRRegistries.FLUID_HATCH_BLOCK.get(), casingModel);
        blockModels.createTrivialBlock(MMCRRegistries.ENERGY_HATCH_BLOCK.get(), casingModel);
        blockModels.createHorizontallyRotatedBlock(MMCRRegistries.CONTROLLER_BLOCK.get(), casingModel);
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return Stream.of(
                MMCRRegistries.CONTROLLER_BLOCK.get().builtInRegistryHolder(),
                MMCRRegistries.CASING_BLOCK.get().builtInRegistryHolder(),
                MMCRRegistries.ITEM_BUS_BLOCK.get().builtInRegistryHolder(),
                MMCRRegistries.FLUID_HATCH_BLOCK.get().builtInRegistryHolder(),
                MMCRRegistries.ENERGY_HATCH_BLOCK.get().builtInRegistryHolder());
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return Stream.empty();
    }
}
