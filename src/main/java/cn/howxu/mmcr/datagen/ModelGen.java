package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.DataStorageBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.NetworkInterfaceBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.function.Supplier;

public final class ModelGen extends ModelProvider {

    public ModelGen(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        registerModels((BlockModelRegistration) (block, name) -> blockModels.createTrivialBlock(block.get(), TexturedModel.CUBE.updateTexture(
                        m -> m.put(TextureSlot.ALL, textureFor(name)))),
                (ItemModelRegistration) (item, name) -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
    }

    static List<GeneratedModel> collectRegisteredModels() {
        List<GeneratedModel> models = new ArrayList<>();
        registerModels((block, name) -> {
                    models.add(new GeneratedModel(GeneratedModel.Kind.BLOCKSTATE, name));
                    models.add(new GeneratedModel(GeneratedModel.Kind.ITEM, name));
                }, (item, name) -> models.add(new GeneratedModel(GeneratedModel.Kind.ITEM, name)));
        return models;
    }

    static List<String> collectKnownBlockNames() {
        return ModBlocks.BLOCKS.keySet().stream()
                .filter(name -> shouldGenerateBlockModels(name, ModBlocks.BLOCKS.get(name)))
                .toList();
    }

    static List<String> collectKnownItemNames() {
        List<String> names = new ArrayList<>(collectKnownBlockNames());
        names.add("multiblock_detector");
        names.add("thread_disperser");
        names.add("terminal");
        names.add("key_card");
        names.add("modularium");
        return List.copyOf(names);
    }

    private static void registerModels(BlockModelRegistration blockRegistration,
                                       ItemModelRegistration itemRegistration) {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            if (shouldGenerateBlockModels(name, blockHolder)) {
                blockRegistration.register(blockHolder::get, name);
            }
        });
        itemRegistration.register(ModItems.MULTIBLOCK_DETECTOR::get, "multiblock_detector");
        itemRegistration.register(ModItems.THREAD_DISPERSER::get, "thread_disperser");
        itemRegistration.register(ModItems.TERMINAL::get, "terminal");
        itemRegistration.register(ModItems.KEY_CARD::get, "key_card");
        itemRegistration.register(ModItems.MODULARIUM::get, "modularium");
    }

    private static boolean isIoPort(String blockName) {
        return PortKinds.all().stream().anyMatch(kind -> kind.id().equals(blockName));
    }

    /** 用 block 注册名生成纹理 Material:modid:block/<name>。每个 block 自带独立贴图。 */
    private static Material textureFor(String blockName) {
        String textureName = "smart_interface".equals(blockName) ? "overlay_smartinterface_number" : blockName;
        return new Material(MMCR.id("block/" + textureName));
    }

    private static boolean shouldGenerateBlockModels(String name, Supplier<? extends Block> block) {
        return !isIoPort(name) && !isParallelController(name) && !"factory_controller".equals(name)
                && !"smart_interface".equals(name) && !"module_bridge".equals(name)
                && !(block.get() instanceof MachineControllerBlock)
                && !(block.get() instanceof DataStorageBlock)
                && !(block.get() instanceof UpgradeBusBlock)
                && !(block.get() instanceof NetworkInterfaceBlock);
    }

    @FunctionalInterface
    private interface BlockModelRegistration {
        void register(Supplier<Block> block, String name);
    }

    @FunctionalInterface
    private interface ItemModelRegistration {
        void register(Supplier<Item> item, String name);
    }

    record GeneratedModel(Kind kind, String name) {
        enum Kind {
            BLOCKSTATE,
            ITEM
        }
    }

    private static boolean isParallelController(String blockName) {
        for (ParallelTier tier : ParallelTier.values()) {
            if (tier.idSuffix().equals(blockName)) return true;
        }
        return false;
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return collectKnownBlockNames().stream()
                .map(ModBlocks.BLOCKS::get)
                .map(h -> h.get().builtInRegistryHolder());
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return collectKnownItemNames().stream()
                .map(ModItems.ITEMS::get)
                .map(h -> h.get().builtInRegistryHolder());
    }
}
