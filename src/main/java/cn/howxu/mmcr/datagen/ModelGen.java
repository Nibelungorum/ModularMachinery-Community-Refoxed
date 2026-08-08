package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.stream.Stream;

public final class ModelGen extends ModelProvider {

    private static final Identifier DYNAMIC_CONTROLLER_MODEL = MMCR.id("block/dynamic_machine_controller");
    private static final Identifier DYNAMIC_IO_PORT_MODEL = MMCR.id("block/dynamic_io_port");

    public ModelGen(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            Block block = blockHolder.get();
            if (block instanceof MachineControllerBlock) {
                blockModels.blockStateOutput.accept(MultiVariantGenerator
                        .dispatch(block, BlockModelGenerators.plainVariant(DYNAMIC_CONTROLLER_MODEL))
                        .with(MachineControllerVariants.full()));
                blockModels.registerSimpleItemModel(block.asItem(),
                        DYNAMIC_CONTROLLER_MODEL);
            } else if (isIoPort(name)) {
                blockModels.blockStateOutput.accept(MultiVariantGenerator
                        .dispatch(block, BlockModelGenerators.plainVariant(DYNAMIC_IO_PORT_MODEL)));
                blockModels.registerSimpleItemModel(block.asItem(),
                        DYNAMIC_IO_PORT_MODEL);
            } else {
                blockModels.createTrivialBlock(block, TexturedModel.CUBE.updateTexture(
                        m -> m.put(TextureSlot.ALL, textureFor(name))));
            }
        });
        itemModels.generateFlatItem(ModItems.WRENCH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MULTIBLOCK_DETECTOR.get(), ModelTemplates.FLAT_ITEM);
    }

    /** 用 block 注册名生成纹理 Material:modid:block/<name>。每个 block 自带独立贴图。 */
    private static Material textureFor(String blockName) {
        return new Material(MMCR.id("block/" + blockName));
    }

    private static boolean isIoPort(String blockName) {
        return PortKinds.all().stream().anyMatch(kind -> kind.id().equals(blockName));
    }

    static String overlayTextureFor(String blockName) {
        if (blockName.startsWith("item_input_bus")) return overlayTexture(blockName, "item_input_bus", "overlay_inputbus");
        if (blockName.startsWith("item_output_bus")) return overlayTexture(blockName, "item_output_bus", "overlay_outputbus");
        if (blockName.startsWith("fluid_input_hatch")) return overlayTexture(blockName, "fluid_input_hatch", "overlay_fluidinputhatch");
        if (blockName.startsWith("fluid_output_hatch")) return overlayTexture(blockName, "fluid_output_hatch", "overlay_fluidoutputhatch");
        if (blockName.startsWith("energy_input_hatch")) return overlayTexture(blockName, "energy_input_hatch", "overlay_energyinputhatch");
        if (blockName.startsWith("energy_output_hatch")) return overlayTexture(blockName, "energy_output_hatch", "overlay_energyoutputhatch");
        throw new IllegalArgumentException("No overlay texture for I/O port: " + blockName);
    }

    static Identifier dynamicControllerModel() {
        return DYNAMIC_CONTROLLER_MODEL;
    }

    static Identifier dynamicIoPortModel() {
        return DYNAMIC_IO_PORT_MODEL;
    }

    private static String overlayTexture(String blockName, String baseName, String textureBase) {
        String tier = blockName.equals(baseName) ? "normal" : blockName.substring(baseName.length() + 1);
        return textureBase + "_" + tier;
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
