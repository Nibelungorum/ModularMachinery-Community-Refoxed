package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.DataStorageBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.NetworkInterfaceBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
import cn.howxu.mmcr.compat.create.loaded.StressInterfaceAppearance;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModelGen extends BlockStateProvider {

    public ModelGen(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, MMCR.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        registerModels((block, name) -> {
                    if (isCreateStressPort(name)) {
                        StressInterfaceAppearance.generateModels(this, block.get(), name);
                    } else {
                        var model = models().cubeAll(name, textureFor(name));
                        simpleBlockWithItem(block.get(), model);
                    }
                }, (item, name) -> itemModels().basicItem(item.get()));
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
        names.add("terminal");
        names.add("key_card");
        names.add("blueprint");
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
        itemRegistration.register(ModItems.TERMINAL::get, "terminal");
        itemRegistration.register(ModItems.KEY_CARD::get, "key_card");
        itemRegistration.register(ModItems.BLUEPRINT::get,"blueprint");
        itemRegistration.register(ModItems.MODULARIUM::get, "modularium");
    }

    private static boolean isIoPort(String blockName) {
        return PortKinds.all().stream().anyMatch(kind -> kind.id().equals(blockName));
    }

    /** Uses the block registry name as its mod-local block texture. */
    private static ResourceLocation textureFor(String blockName) {
        String textureName = "smart_interface".equals(blockName) ? "overlay_smartinterface_number" : blockName;
        return MMCR.id("block/" + textureName);
    }

    private static boolean shouldGenerateBlockModels(String name, Supplier<? extends Block> block) {
        if (isCreateStressPort(name)) return true;
        return !isIoPort(name) && !isParallelController(name) && !"factory_controller".equals(name)
                && !"smart_interface".equals(name) && !"module_bridge".equals(name)
                && !(block.get() instanceof MachineControllerBlock)
                && !(block.get() instanceof DataStorageBlock)
                && !(block.get() instanceof UpgradeBusBlock)
                && !(block.get() instanceof NetworkInterfaceBlock);
    }

    private static boolean isCreateStressPort(String name) {
        return "create_stress_input_interface".equals(name) || "create_stress_output_interface".equals(name);
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

}
