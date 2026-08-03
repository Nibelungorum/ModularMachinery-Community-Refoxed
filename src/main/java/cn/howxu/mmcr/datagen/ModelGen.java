package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class ModelGen extends ModelProvider {

    private static final TextureSlot BG_ALL = TextureSlot.create("bg_all");
    private static final TextureSlot OV_TOP = TextureSlot.create("ov_top");
    private static final TextureSlot OV_SIDE = TextureSlot.create("ov_side");
    private static final TextureSlot OV_FRONT = TextureSlot.create("ov_front");

    private static final ModelTemplate CONTROLLER_OVERLAY = new ModelTemplate(
            Optional.of(MMCR.id("block/machine_controller_overlay")),
            Optional.empty(),
            BG_ALL, OV_TOP, OV_SIDE, OV_FRONT);

    public ModelGen(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Map<String, MachineControllerSpec> controllers = new HashMap<>();
        MachineDefinitions.all().forEach(machine ->
                controllers.put(machine.controller().id().getPath(), machine.controller()));

        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            Block block = blockHolder.get();
            MachineControllerSpec controller = controllers.get(name);

            if (controller != null || block instanceof MachineControllerBlock) {
                final MachineControllerSpec spec = controller != null
                        ? controller
                        : MachineControllerSpec.defaultsFor(((MachineControllerBlock) block).machineId());
                TextureMapping mapping = new TextureMapping()
                        .put(BG_ALL, new Material(spec.sideTexture()))
                        .put(OV_TOP, new Material(spec.topTexture()))
                        .put(OV_SIDE, new Material(spec.sideTexture()))
                        .put(OV_FRONT, new Material(spec.frontTexture()));
                Identifier modelId = CONTROLLER_OVERLAY.create(block, mapping, blockModels.modelOutput);
                blockModels.blockStateOutput.accept(MultiVariantGenerator
                        .dispatch(block, BlockModelGenerators.plainVariant(modelId))
                        .with(MachineControllerVariants.full()));
                blockModels.registerSimpleItemModel(block.asItem(),
                        ModelLocationUtils.getModelLocation(block));
            } else {
                blockModels.createTrivialBlock(block, TexturedModel.CUBE.updateTexture(
                        m -> m.put(TextureSlot.ALL, textureFor(name))));
            }
        });
    }

    /** 用 block 注册名生成纹理 Material:modid:block/<name>。每个 block 自带独立贴图。 */
    private static Material textureFor(String blockName) {
        return new Material(MMCR.id("block/" + blockName));
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
