package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.stream.Stream;

public final class ModelGen extends ModelProvider {

    private static final TextureSlot BG_ALL = TextureSlot.create("bg_all");
    private static final TextureSlot OV_TOP = TextureSlot.create("ov_top");
    private static final TextureSlot OV_SIDE = TextureSlot.create("ov_side");
    private static final TextureSlot OV_FRONT = TextureSlot.create("ov_front");
    private static final TextureSlot OV_ALL = TextureSlot.create("ov_all");

    private static final ModelTemplate CONTROLLER_OVERLAY = new ModelTemplate(
            Optional.of(MMCR.id("block/machine_controller_overlay")),
            Optional.empty(),
            BG_ALL, OV_TOP, OV_SIDE, OV_FRONT);

    private static final ModelTemplate BUS_HATCH_OVERLAY = new ModelTemplate(
            Optional.of(MMCR.id("block/bus_hatch_overlay")),
            Optional.empty(),
            BG_ALL, OV_ALL);

    public ModelGen(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            Block block = blockHolder.get();
            if (block instanceof MachineControllerBlock) {
                Identifier modelId = MMCR.id("block/machine_controller_overlay");
                blockModels.blockStateOutput.accept(MultiVariantGenerator
                        .dispatch(block, BlockModelGenerators.plainVariant(modelId))
                        .with(MachineControllerVariants.full()));
                blockModels.registerSimpleItemModel(block.asItem(),
                        modelId);
            } else if (isIoPort(name)) {
                TextureMapping mapping = new TextureMapping()
                        .put(BG_ALL, new Material(MMCR.id("block/basic_casing")))
                        .put(OV_ALL, new Material(MMCR.id("block/" + overlayTextureFor(name))));
                Identifier modelId = BUS_HATCH_OVERLAY.create(block, mapping, blockModels.modelOutput);
                blockModels.blockStateOutput.accept(MultiVariantGenerator
                        .dispatch(block, BlockModelGenerators.plainVariant(modelId)));
                blockModels.registerSimpleItemModel(block.asItem(),
                        ModelLocationUtils.getModelLocation(block));
            } else if (isParallelController(name)) {
                blockModels.createTrivialBlock(block, TexturedModel.CUBE.updateTexture(
                        m -> m.put(TextureSlot.ALL, new Material(MMCR.id("block/basic_casing")))));
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

    private static boolean isParallelController(String blockName) {
        for (ParallelTier tier : ParallelTier.values()) {
            if (tier.idSuffix().equals(blockName)) return true;
        }
        return false;
    }

    private static String overlayTextureFor(String blockName) {
        return switch (blockName) {
            case "item_input_bus" -> "overlay_inputbus_normal";
            case "item_output_bus" -> "overlay_outputbus_normal";
            case "fluid_input_hatch" -> "overlay_fluidinputhatch_normal";
            case "fluid_output_hatch" -> "overlay_fluidoutputhatch_normal";
            case "energy_input_hatch" -> "overlay_energyinputhatch_normal";
            case "energy_output_hatch" -> "overlay_energyoutputhatch_normal";
            default -> throw new IllegalArgumentException("No overlay texture for I/O port: " + blockName);
        };
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
