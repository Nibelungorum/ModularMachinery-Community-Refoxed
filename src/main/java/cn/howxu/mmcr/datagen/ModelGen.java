package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.stream.Stream;
import java.util.List;

public final class ModelGen extends ModelProvider {

    public ModelGen(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            Block block = blockHolder.get();
            if (!(block instanceof MachineControllerBlock) && !isIoPort(name)) {
                blockModels.createTrivialBlock(block, TexturedModel.CUBE.updateTexture(
                        m -> m.put(TextureSlot.ALL, textureFor(name))));
            }
        });
        itemModels.generateFlatItem(ModItems.WRENCH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MULTIBLOCK_DETECTOR.get(), ModelTemplates.FLAT_ITEM);
    }

    private static boolean isIoPort(String blockName) {
        return PortKinds.all().stream().anyMatch(kind -> kind.id().equals(blockName));
    }

    /** 用 block 注册名生成纹理 Material:modid:block/<name>。每个 block 自带独立贴图。 */
    private static Material textureFor(String blockName) {
        return new Material(MMCR.id("block/" + blockName));
    }

    static List<String> generatedDynamicBlocks() {
        return List.of();
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return ModBlocks.BLOCKS.values().stream()
                .map(h -> h.get().builtInRegistryHolder());
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return ModItems.ITEMS.values().stream()
                .map(h -> h.get().builtInRegistryHolder());
    }
}
