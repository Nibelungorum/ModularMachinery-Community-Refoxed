package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
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

    private static void registerModels(BlockModelRegistration blockRegistration,
                                       ItemModelRegistration itemRegistration) {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            if (shouldGenerateBlockModels(name)) {
                blockRegistration.register(blockHolder::get, name);
            }
        });
        itemRegistration.register(ModItems.WRENCH::get, "wrench");
        itemRegistration.register(ModItems.MULTIBLOCK_DETECTOR::get, "multiblock_detector");
    }

    private static boolean isIoPort(String blockName) {
        return PortKinds.all().stream().anyMatch(kind -> kind.id().equals(blockName));
    }

    /** 用 block 注册名生成纹理 Material:modid:block/<name>。每个 block 自带独立贴图。 */
    private static Material textureFor(String blockName) {
        return new Material(MMCR.id("block/" + blockName));
    }

    private static boolean shouldGenerateBlockModels(String name) {
        return !isIoPort(name) && MachineDefinitions.allRegistrations().stream()
                .map(registration -> MachineControllerSpec.defaultsFor(registration.id()).id().getPath())
                .noneMatch(name::equals);
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
