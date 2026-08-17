package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

public final class ModRecipeProvider extends RecipeProvider {
    private final HolderGetter<Item> items;

    public ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
        items = registries.lookupOrThrow(Registries.ITEM);
    }

    @Override
    protected void buildRecipes() {
        shaped(ModItems.MODULARIUM.get(), 5)
                .pattern("XAX")
                .pattern("ABA")
                .pattern("BCB")
                .define('X', Tags.Items.INGOTS_COPPER)
                .define('A', Tags.Items.INGOTS_IRON)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', Tags.Items.DUSTS_GLOWSTONE)
                .save(output);

        shaped(ModBlocks.BASIC_CASING.get(), 2)
                .pattern(" X ")
                .pattern("XAX")
                .pattern(" X ")
                .define('X', ModItems.MODULARIUM.get())
                .define('A', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                .save(output);

        ItemLike previous = ModItems.ITEMS.get("item_input_bus_tiny").get();
        shaped(previous, 1)
                .pattern(" A ")
                .pattern(" B ")
                .pattern(" C ")
                .define('A', Items.HOPPER)
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', Tags.Items.CHESTS)
                .save(output);

        for (int index = 1; index < ItemBusSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get(itemInputBusId(ItemBusSize.values()[index])).get();
            shaped(result, 1)
                    .pattern(" A ")
                    .pattern("BCB")
                    .pattern("DBD")
                    .define('A', Items.HOPPER)
                    .define('B', ModItems.MODULARIUM.get())
                    .define('C', previous)
                    .define('D', Tags.Items.CHESTS)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get("item_output_bus_tiny").get();
        shaped(previous, 1)
                .pattern(" A ")
                .pattern(" B ")
                .pattern(" C ")
                .define('A', Tags.Items.CHESTS)
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', Items.HOPPER)
                .save(output);

        for (int index = 1; index < ItemBusSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get(itemOutputBusId(ItemBusSize.values()[index])).get();
            shaped(result, 1)
                    .pattern("ABA")
                    .pattern("BCB")
                    .pattern(" D ")
                    .define('A', Tags.Items.CHESTS)
                    .define('B', ModItems.MODULARIUM.get())
                    .define('C', previous)
                    .define('D', Items.HOPPER)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get("fluid_input_hatch_tiny").get();
        shaped(previous, 1)
                .pattern(" A ")
                .pattern(" B ")
                .pattern(" C ")
                .define('A', Items.HOPPER)
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', Items.BUCKET)
                .save(output);

        for (int index = 1; index < FluidHatchSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get(fluidInputHatchId(FluidHatchSize.values()[index])).get();
            shaped(result, 1)
                    .pattern(" A ")
                    .pattern("BCB")
                    .pattern("DBD")
                    .define('A', Items.HOPPER)
                    .define('B', ModItems.MODULARIUM.get())
                    .define('C', previous)
                    .define('D', Items.BUCKET)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get("fluid_output_hatch_tiny").get();
        shaped(previous, 1)
                .pattern(" A ")
                .pattern(" B ")
                .pattern(" C ")
                .define('A', Items.BUCKET)
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', Items.HOPPER)
                .save(output);

        for (int index = 1; index < FluidHatchSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get(fluidOutputHatchId(FluidHatchSize.values()[index])).get();
            shaped(result, 1)
                    .pattern("DBD")
                    .pattern("BCB")
                    .pattern(" A ")
                    .define('A', Items.HOPPER)
                    .define('B', ModItems.MODULARIUM.get())
                    .define('C', previous)
                    .define('D', Items.BUCKET)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get("energy_input_hatch_tiny").get();
        shaped(previous, 1)
                .pattern(" A ")
                .pattern("ABA")
                .pattern("CDC")
                .define('A', Tags.Items.DUSTS_REDSTONE)
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', Items.REPEATER)
                .define('D', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                .save(output);

        for (int index = 1; index < EnergyHatchSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get(energyInputHatchId(EnergyHatchSize.values()[index])).get();
            shaped(result, 1)
                    .pattern("ABA")
                    .pattern("CDC")
                    .pattern("ACA")
                    .define('A', Tags.Items.DUSTS_REDSTONE)
                    .define('B', Items.REPEATER)
                    .define('C', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                    .define('D', previous)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get("energy_output_hatch_tiny").get();
        shaped(previous, 1)
                .pattern("CDC")
                .pattern("ABA")
                .pattern(" A ")
                .define('A', Tags.Items.DUSTS_REDSTONE)
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', Items.REPEATER)
                .define('D', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                .save(output);

        for (int index = 1; index < EnergyHatchSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get(energyOutputHatchId(EnergyHatchSize.values()[index])).get();
            shaped(result, 1)
                    .pattern("ACA")
                    .pattern("CDC")
                    .pattern("ABA")
                    .define('A', Tags.Items.DUSTS_REDSTONE)
                    .define('B', Items.REPEATER)
                    .define('C', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                    .define('D', previous)
                    .save(output);
            previous = result;
        }

        shaped(ModBlocks.SMART_INTERFACE.get(), 1)
                .pattern("DBD")
                .pattern("FAC")
                .pattern("EBE")
                .define('A', ModBlocks.BASIC_CASING.get())
                .define('B', Tags.Items.GEMS_DIAMOND)
                .define('C', ItemTags.create(Identifier.withDefaultNamespace("bookshelf_books")))
                .define('D', ModItems.MODULARIUM.get())
                .define('E', Items.COMPARATOR)
                .define('F', Blocks.OBSERVER)
                .save(output);

        shaped(ModItems.ITEMS.get("factory_controller").get(), 1)
                .pattern("ABA")
                .pattern("CDC")
                .pattern("EFE")
                .define('A', Blocks.OBSERVER)
                .define('B', Items.LEVER)
                .define('C', Tags.Items.GEMS_DIAMOND)
                .define('D', ModBlocks.BASIC_CASING.get())
                .define('E', Tags.Items.GEMS_AMETHYST)
                .define('F', Tags.Items.INGOTS_GOLD)
                .save(output);

        shaped(ModBlocks.MODULE_BRIDGE.get(), 1)
                .pattern("ABA")
                .pattern("EFE")
                .pattern("CBC")
                .define('A', Tags.Items.GEMS_AMETHYST)
                .define('B', Items.REPEATER)
                .define('C', Items.DIAMOND)
                .define('E', Items.TRIPWIRE_HOOK)
                .define('F', ModBlocks.BASIC_CASING.get())
                .save(output);

        shaped(ModItems.TERMINAL.get(), 1)
                .pattern("ABA")
                .pattern("ECE")
                .pattern("ACA")
                .define('A', ModItems.MODULARIUM.get())
                .define('B', Tags.Items.GEMS_LAPIS)
                .define('C', Tags.Items.DUSTS_REDSTONE)
                .define('E', Tags.Items.GLASS_PANES)
                .save(output);

        shaped(ModItems.THREAD_DISPERSER.get(), 1)
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ADA")
                .define('A', Tags.Items.INGOTS_GOLD)
                .define('B', Tags.Items.GEMS_AMETHYST)
                .define('C', Tags.Items.INGOTS_NETHERITE)
                .define('D', Items.DIAMOND)
                .save(output);

        previous = ModItems.ITEMS.get(ParallelTier.NORMAL.idSuffix()).get();
        shaped(previous, 1)
                .pattern("ABA")
                .pattern("CDC")
                .pattern("ABA")
                .define('A', Tags.Items.GEMS_DIAMOND)
                .define('B', ModItems.MODULARIUM.get())
                .define('C', Tags.Items.INGOTS_NETHERITE)
                .define('D', ModBlocks.BASIC_CASING.get())
                .save(output);

        for (int index = 1; index < ParallelTier.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get(ParallelTier.values()[index].idSuffix()).get();
            shaped(result, 1)
                    .pattern("ABA")
                    .pattern("BDB")
                    .pattern("ACA")
                    .define('A', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                    .define('B', previous)
                    .define('C', Tags.Items.INGOTS_NETHERITE)
                    .define('D', ModBlocks.BASIC_CASING.get())
                    .save(output);
            previous = result;
        }
    }

    private static String itemInputBusId(ItemBusSize size) {
        return size == ItemBusSize.NORMAL ? "item_input_bus" : "item_input_bus_" + size.id();
    }

    private static String itemOutputBusId(ItemBusSize size) {
        return size == ItemBusSize.NORMAL ? "item_output_bus" : "item_output_bus_" + size.id();
    }

    private static String fluidInputHatchId(FluidHatchSize size) {
        return size == FluidHatchSize.NORMAL ? "fluid_input_hatch" : "fluid_input_hatch_" + size.id();
    }

    private static String fluidOutputHatchId(FluidHatchSize size) {
        return size == FluidHatchSize.NORMAL ? "fluid_output_hatch" : "fluid_output_hatch_" + size.id();
    }

    private static String energyInputHatchId(EnergyHatchSize size) {
        return size == EnergyHatchSize.NORMAL ? "energy_input_hatch" : "energy_input_hatch_" + size.id();
    }

    private static String energyOutputHatchId(EnergyHatchSize size) {
        return size == EnergyHatchSize.NORMAL ? "energy_output_hatch" : "energy_output_hatch_" + size.id();
    }

    private static TagKey<Item> itemTag(String path) {
        return ItemTags.create(Identifier.fromNamespaceAndPath("c", path));
    }

    protected ShapedRecipeBuilder shaped(ItemLike result, int count) {
        return ShapedRecipeBuilder.shaped(items, RecipeCategory.MISC, result, count)
                .unlockedBy(getHasName(result), has(result));
    }

    protected ShapelessRecipeBuilder shapeless(ItemLike result, int count) {
        return ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, result, count)
                .unlockedBy(getHasName(result), has(result));
    }
}
