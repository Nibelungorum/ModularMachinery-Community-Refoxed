package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.MMCRBlocks;
import cn.howxu.mmcr.registry.MMCRItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.stream.Stream;

public final class MMCRModelProvider extends ModelProvider {

    public MMCRModelProvider(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        MMCRBlocks.BLOCKS.forEach((name, blockHolder) -> {
            Block block = blockHolder.get();
            Material texture = textureFor(name);

            if ("controller".equals(name)) {
                blockModels.createHorizontallyRotatedBlock(block, TexturedModel.CUBE.updateTexture(
                        m -> m.put(net.minecraft.client.data.models.model.TextureSlot.ALL, texture)));
                blockModels.registerSimpleItemModel(block.asItem(),
                        net.minecraft.client.data.models.model.ModelLocationUtils.getModelLocation(block));
            } else {
                blockModels.createTrivialBlock(block, TexturedModel.CUBE.updateTexture(
                        m -> m.put(net.minecraft.client.data.models.model.TextureSlot.ALL, texture)));
            }
        });
    }

    /** 用 block 注册名生成纹理 Material:modid:block/<name>。每个 block 自带独立贴图。 */
    private static Material textureFor(String blockName) {
        return new Material(MMCR.id("block/" + blockName));
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return MMCRBlocks.BLOCKS.values().stream()
                .map(h -> h.get().builtInRegistryHolder());
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return MMCRItems.ITEMS.values().stream()
                .map(h -> h.get().builtInRegistryHolder());
    }
}
