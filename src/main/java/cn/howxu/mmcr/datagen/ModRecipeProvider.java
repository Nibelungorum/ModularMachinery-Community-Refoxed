package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksBridge;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.compat.mekanism.loaded.MekanismPortSizes;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedCombinedPortSize;
import cn.howxu.mmcr.internal.port.ExtendedEnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import cn.howxu.mmcr.internal.port.CombinedPortSize;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import mekanism.common.Mekanism;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.Tags;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ModRecipeProvider extends RecipeProvider {
    private RecipeOutput output;

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output, HolderLookup.Provider registries) {
        this.output = output;
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

        generatedPortRecipes();
        ae2InterfaceRecipes();
        extendedAePlusInterfaceRecipes();
        appliedFluxInterfaceRecipes();
        fluxNetworksInterfaceRecipes();
        createStressInterfaceRecipes();
        arsSourceInterfaceRecipes();
        mekanismPortsRecipes();

        shaped(ModBlocks.SMART_INTERFACE.get(), 1)
                .pattern("DBD")
                .pattern("FAC")
                .pattern("EBE")
                .define('A', ModBlocks.BASIC_CASING.get())
                .define('B', Tags.Items.GEMS_DIAMOND)
                .define('C', ItemTags.create(ResourceLocation.withDefaultNamespace("bookshelf_books")))
                .define('D', ModItems.MODULARIUM.get())
                .define('E', Items.COMPARATOR)
                .define('F', Blocks.OBSERVER)
                .save(output);

        shaped(ModBlocks.DATA_STORAGE.get(), 1)
                .pattern("ADA")
                .pattern("BCB")
                .pattern("ADA")
                .define('A', Tags.Items.DUSTS_REDSTONE)
                .define('B', Tags.Items.CHESTS)
                .define('C', ModBlocks.BASIC_CASING.get())
                .define('D', Tags.Items.GEMS_DIAMOND)
                .save(output);

        shaped(ModBlocks.NETWORK_INTERFACE.get(), 1)
                .pattern("AEA")
                .pattern("BCB")
                .pattern("AEA")
                .define('A', Tags.Items.DUSTS_REDSTONE)
                .define('B', Tags.Items.GEMS_DIAMOND)
                .define('C', ModBlocks.BASIC_CASING.get())
                .define('E', Items.ENDER_PEARL)
                .save(output);

        shaped(ModBlocks.FACTORY_CONTROLLER.get(), 1)
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

        shaped(ModItems.KEY_CARD.get(), 1)
                .pattern("IRI")
                .pattern("RPR")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('P', Items.PAPER)
                .save(output);

        shapeless(ModItems.BLUEPRINT.get(), 1)
                .requires(Items.PAPER)
                .requires(ModItems.MODULARIUM.get())
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

        previous = ModBlocks.BASIC_CASING.get();
        for (UpgradeBusSize size : UpgradeBusSize.values()) {
            ItemLike result = ModItems.ITEMS.get("upgrade_bus_" + size.id()).get();
            upgradeRecipe(result, previous);
            previous = result;
        }
    }

    private void generatedPortRecipes() {
        ItemLike previous = ModItems.ITEMS.get(itemInputBusId(ItemBusSize.LUDICROUS)).get();
        for (ExtendedItemBusSize size : ExtendedItemBusSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_item_input_bus_" + size.id()).get();
            // upgradeRecipe(result, previous);
            shaped(result, 1)
                    .pattern(" A ")
                    .pattern("BCB")
                    .pattern("DBD")
                    .define('A', Items.ANVIL)
                    .define('B', ModBlocks.BASIC_CASING.get())
                    .define('C', previous)
                    .define('D', Tags.Items.CHESTS)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get(itemOutputBusId(ItemBusSize.LUDICROUS)).get();
        for (ExtendedItemBusSize size : ExtendedItemBusSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_item_output_bus_" + size.id()).get();
            // upgradeRecipe(result, previous);
            shaped(result, 1)
                    .pattern("ABA")
                    .pattern("BCB")
                    .pattern(" D ")
                    .define('A', Tags.Items.CHESTS)
                    .define('B', ModBlocks.BASIC_CASING.get())
                    .define('C', previous)
                    .define('D', Items.ANVIL)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get(fluidInputHatchId(FluidHatchSize.VACUUM)).get();
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_fluid_input_hatch_" + size.id()).get();
            // upgradeRecipe(result, previous);
            shaped(result, 1)
                    .pattern(" A ")
                    .pattern("BCB")
                    .pattern("DBD")
                    .define('A', Items.CAULDRON)
                    .define('B', ModBlocks.BASIC_CASING.get())
                    .define('C', previous)
                    .define('D', Items.BUCKET)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get(fluidOutputHatchId(FluidHatchSize.VACUUM)).get();
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_fluid_output_hatch_" + size.id()).get();
            // upgradeRecipe(result, previous);
            shaped(result, 1)
                    .pattern("DBD")
                    .pattern("BCB")
                    .pattern(" A ")
                    .define('A', Items.CAULDRON)
                    .define('B', ModBlocks.BASIC_CASING.get())
                    .define('C', previous)
                    .define('D', Items.BUCKET)
                    .save(output);
            previous = result;
        }

        previous = ModItems.ITEMS.get(energyInputHatchId(EnergyHatchSize.ULTIMATE)).get();
        for (ExtendedEnergyHatchSize size : ExtendedEnergyHatchSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_energy_input_hatch_" + size.id()).get();
            // upgradeRecipe(result, previous);
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

        previous = ModItems.ITEMS.get(energyOutputHatchId(EnergyHatchSize.ULTIMATE)).get();
        for (ExtendedEnergyHatchSize size : ExtendedEnergyHatchSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_energy_output_hatch_" + size.id()).get();
            // upgradeRecipe(result, previous);
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

        previous = combinedRecipe("combined_input_basic", "item_input_bus_huge", "fluid_input_hatch_ludicrous");
        for (int index = 1; index < CombinedPortSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get("combined_input_" + CombinedPortSize.values()[index].id()).get();
            upgradeRecipe(result, previous);
            previous = result;
        }

        previous = combinedRecipe("combined_output_basic", "item_output_bus_huge", "fluid_output_hatch_ludicrous");
        for (int index = 1; index < CombinedPortSize.values().length; index++) {
            ItemLike result = ModItems.ITEMS.get("combined_output_" + CombinedPortSize.values()[index].id()).get();
            upgradeRecipe(result, previous);
            previous = result;
        }

        previous = ModItems.ITEMS.get("combined_input_ultimate").get();
        for (ExtendedCombinedPortSize size : ExtendedCombinedPortSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_combined_input_" + size.id()).get();
            upgradeRecipe(result, previous);
            previous = result;
        }

        previous = ModItems.ITEMS.get("combined_output_ultimate").get();
        for (ExtendedCombinedPortSize size : ExtendedCombinedPortSize.values()) {
            ItemLike result = ModItems.ITEMS.get("extended_combined_output_" + size.id()).get();
            upgradeRecipe(result, previous);
            previous = result;
        }
    }

    private void extendedAePlusInterfaceRecipes() {
        String id = "eaep_me_mirror_pattern_interface";
        if (!ModItems.ITEMS.containsKey(id)) return;
        shapeless(ModItems.ITEMS.get(id).get(), 1)
                .requires(externalItem("extendedae_plus", "mirror_pattern_provider"))
                .requires(ModBlocks.BASIC_CASING.get())
                .save(whenLoaded("ae2", "extendedae", "extendedae_plus"));
    }

    private void ae2InterfaceRecipes() {
        if (!ModItems.ITEMS.containsKey("ae2_me_input_interface")) return;

        ItemLike ae2Interface = externalItem("ae2", "interface");
        ItemLike input = ModItems.ITEMS.get("ae2_me_input_interface").get();
        ItemLike stockingInput = ModItems.ITEMS.get("ae2_me_stocking_input_interface").get();
        ItemLike output = ModItems.ITEMS.get("ae2_me_output_interface").get();
        ItemLike asyncOutput = ModItems.ITEMS.get("ae2_me_async_output_interface").get();
        ItemLike pattern = ModItems.ITEMS.get("ae2_me_pattern_interface").get();

        shaped(input, 1)
                .pattern("A")
                .pattern("B")
                .define('A', ae2Interface)
                .define('B', ModBlocks.BASIC_CASING.get())
                .save(whenLoaded("ae2"));
        shaped(output, 1)
                .pattern("A")
                .pattern("B")
                .define('A', ModBlocks.BASIC_CASING.get())
                .define('B', ae2Interface)
                .save(whenLoaded("ae2"));
        shapeless(stockingInput, 1)
                .requires(input)
                .requires(externalItem("ae2", "storage_bus"))
                .save(whenLoaded("ae2"));
        shapeless(asyncOutput, 1)
                .requires(output)
                .requires(ModItems.THREAD_DISPERSER.get())
                .save(whenLoaded("ae2"));
        shapeless(pattern, 1)
                .requires(externalItem("ae2", "pattern_provider"))
                .requires(ModBlocks.BASIC_CASING.get())
                .save(whenLoaded("ae2"));

        if (!ModItems.ITEMS.containsKey("eae_me_extended_input_interface")) return;

        ItemLike extendedInput = ModItems.ITEMS.get("eae_me_extended_input_interface").get();
        ItemLike extendedStockingInput = ModItems.ITEMS.get("eae_me_extended_stocking_input_interface").get();
        ItemLike extendedOutput = ModItems.ITEMS.get("eae_me_extended_output_interface").get();
        ItemLike oversizeInput = ModItems.ITEMS.get("eae_me_oversize_input_interface").get();
        ItemLike oversizeOutput = ModItems.ITEMS.get("eae_me_oversize_output_interface").get();
        ItemLike extendedPattern = ModItems.ITEMS.get("eae_me_extended_pattern_interface").get();
        ItemLike extendedAeInterface = externalItem("extendedae", "ex_interface");
        ItemLike oversizeAeInterface = externalItem("extendedae", "oversize_interface");

        shapeless(extendedInput, 1).requires(input).requires(extendedAeInterface).save(whenLoaded("ae2", "extendedae"));
        shapeless(extendedStockingInput, 1).requires(stockingInput).requires(extendedAeInterface).save(whenLoaded("ae2", "extendedae"));
        shapeless(extendedOutput, 1).requires(output).requires(extendedAeInterface).save(whenLoaded("ae2", "extendedae"));
        shapeless(oversizeInput, 1).requires(extendedInput).requires(oversizeAeInterface).save(whenLoaded("ae2", "extendedae"));
        shapeless(oversizeOutput, 1).requires(extendedOutput).requires(oversizeAeInterface).save(whenLoaded("ae2", "extendedae"));
        shapeless(extendedPattern, 1)
                .requires(pattern)
                .requires(externalItem("extendedae", "ex_pattern_provider"))
                .save(whenLoaded("ae2", "extendedae"));
    }

    private void mekanismPortsRecipes() {
        if (externalItem("mekanism", MekanismPortSizes.ChemicalTier.BASIC.id() + "_chemical_tank") == Items.AIR) {
            return;
        }
        for (MekanismPortSizes.ChemicalTier size : MekanismPortSizes.ChemicalTier.values()) {
            ItemLike chemical_tank = BuiltInRegistries.ITEM.get(Mekanism.rl(size.id() + "_chemical_tank"));
            ItemLike result = ModBlocks.BLOCKS.get("chemical_input_hatch_" + size.id()).get();
            shaped(result, 1)
                    .pattern(" A ")
                    .pattern("BCB")
                    .pattern("ABA")
                    .define('A', itemTag("ingots/osmium"))
                    .define('B', ModBlocks.BASIC_CASING.get())
                    .define('C', chemical_tank)
                    .save(whenLoaded("mekanism"));
        }

        for (MekanismPortSizes.ChemicalTier size : MekanismPortSizes.ChemicalTier.values()) {
            ItemLike chemical_tank = BuiltInRegistries.ITEM.get(Mekanism.rl(size.id() + "_chemical_tank"));
            ItemLike result = ModBlocks.BLOCKS.get("chemical_output_hatch_" + size.id()).get();
            shaped(result, 1)
                    .pattern("ABA")
                    .pattern("BCB")
                    .pattern(" A ")
                    .define('A', itemTag("ingots/osmium"))
                    .define('B', ModBlocks.BASIC_CASING.get())
                    .define('C', chemical_tank)
                    .save(whenLoaded("mekanism"));
        }

        ItemLike result = ModBlocks.BLOCKS.get("radioactive_chemical_input_hatch").get();
        ItemLike input = BuiltInRegistries.ITEM.get(Mekanism.rl("radioactive_waste_barrel"));

        shaped(result, 1)
                .pattern(" A ")
                .pattern("BCB")
                .pattern("ABA")
                .define('A', itemTag("ingots/lead"))
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', input)
                .save(whenLoaded("mekanism"));

        result = ModBlocks.BLOCKS.get("radioactive_chemical_output_hatch").get();

        shaped(result, 1)
                .pattern("ABA")
                .pattern("BCB")
                .pattern(" A ")
                .define('A', itemTag("ingots/lead"))
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', input)
                .save(whenLoaded("mekanism"));

        result = ModBlocks.BLOCKS.get("heat_input_hatch").get();
        input = BuiltInRegistries.ITEM.get(Mekanism.rl("superheating_element"));

        shaped(result, 1)
                .pattern(" A ")
                .pattern("BCB")
                .pattern("ABA")
                .define('A', itemTag("ingots/copper"))
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', input)
                .save(whenLoaded("mekanism"));

        result = ModBlocks.BLOCKS.get("heat_output_hatch").get();

        shaped(result, 1)
                .pattern("ABA")
                .pattern("BCB")
                .pattern(" A ")
                .define('A', itemTag("ingots/copper"))
                .define('B', ModBlocks.BASIC_CASING.get())
                .define('C', input)
                .save(whenLoaded("mekanism"));
    }

    private void appliedFluxInterfaceRecipes() {
        if (!ModItems.ITEMS.containsKey("appflux_me_flux_input_interface")) return;
        if (!ModItems.ITEMS.containsKey("appflux_me_flux_output_interface")) return;
        ItemLike accessor = externalItem("appflux", "flux_accessor");
        if (accessor == Items.AIR) return;
        ItemLike input = ModItems.ITEMS.get("appflux_me_flux_input_interface").get();
        ItemLike output = ModItems.ITEMS.get("appflux_me_flux_output_interface").get();

        shaped(input, 1)
                .pattern("A")
                .pattern("B")
                .define('A', accessor)
                .define('B', ModBlocks.BASIC_CASING.get())
                .save(whenLoaded("ae2", "appflux"));
        shaped(output, 1)
                .pattern("A")
                .pattern("B")
                .define('A', ModBlocks.BASIC_CASING.get())
                .define('B', accessor)
                .save(whenLoaded("ae2", "appflux"));
    }

    private void fluxNetworksInterfaceRecipes() {
        if (!FluxNetworksBridge.get().available()) return;
        RecipeOutput conditioned = output.withConditions(new ModLoadedCondition(FluxNetworksIds.MOD_ID));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.BLOCKS.get(FluxNetworksIds.INPUT).get())
                .requires(externalItem(FluxNetworksIds.MOD_ID, "flux_point"))
                .requires(ModBlocks.BASIC_CASING.get()).requires(ModItems.MODULARIUM.get())
                .unlockedBy("has_modularium", has(ModItems.MODULARIUM.get()))
                .save(conditioned, MMCR.id(FluxNetworksIds.INPUT));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.BLOCKS.get(FluxNetworksIds.OUTPUT).get())
                .requires(externalItem(FluxNetworksIds.MOD_ID, "flux_plug"))
                .requires(ModBlocks.BASIC_CASING.get()).requires(ModItems.MODULARIUM.get())
                .unlockedBy("has_modularium", has(ModItems.MODULARIUM.get()))
                .save(conditioned, MMCR.id(FluxNetworksIds.OUTPUT));
        for (String id : List.of(FluxNetworksIds.INPUT, FluxNetworksIds.OUTPUT)) {
            Block block = ModBlocks.BLOCKS.get(id).get();
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, block)
                    .requires(block)
                    .unlockedBy("has_flux_interface", has(block))
                    .save(conditioned, MMCR.id("wipe_" + id));
        }
    }

    private void createStressInterfaceRecipes() {
        if (!ModBlocks.BLOCKS.containsKey("create_stress_input_interface")) return;
        createStressRecipe(output, ModBlocks.BLOCKS.get("create_stress_input_interface").get(),
                externalItem("create", "encased_chain_drive"), ModBlocks.BASIC_CASING.get(),
                ModItems.MODULARIUM.get(), false);
        createStressRecipe(output, ModBlocks.BLOCKS.get("create_stress_output_interface").get(),
                externalItem("create", "encased_chain_drive"), ModBlocks.BASIC_CASING.get(),
                ModItems.MODULARIUM.get(), true);
    }

    static void createStressRecipe(RecipeOutput output, ItemLike result, ItemLike chainDrive,
                                  ItemLike casing, ItemLike modularium, boolean reverse) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, 1)
                .pattern(reverse ? "C" : "A")
                .pattern("B")
                .pattern(reverse ? "A" : "C")
                .define('A', chainDrive)
                .define('B', casing)
                .define('C', modularium)
                .unlockedBy(getHasName(casing), has(casing))
                .save(output.withConditions(new ModLoadedCondition("create")));
    }

    private void arsSourceInterfaceRecipes() {
        if (!ModItems.ITEMS.containsKey(ArsSourceIds.INPUT)) return;
        ItemLike gem = externalItem("ars_nouveau", "source_gem");
        ItemLike jar = externalItem("ars_nouveau", "source_jar");
        shaped(ModItems.ITEMS.get(ArsSourceIds.INPUT).get(), 1)
                .pattern("G").pattern("J").pattern("C")
                .define('G', gem).define('J', jar).define('C', ModBlocks.BASIC_CASING.get())
                .save(whenLoaded("ars_nouveau"));
        shaped(ModItems.ITEMS.get(ArsSourceIds.OUTPUT).get(), 1)
                .pattern("G").pattern("C").pattern("J")
                .define('G', gem).define('J', jar).define('C', ModBlocks.BASIC_CASING.get())
                .save(whenLoaded("ars_nouveau"));
    }

    private ItemLike combinedRecipe(String resultId, String itemId, String fluidId) {
        ItemLike result = ModItems.ITEMS.get(resultId).get();
        shapeless(result, 1)
                .requires(ModItems.ITEMS.get(itemId).get())
                .requires(ModItems.ITEMS.get(fluidId).get())
                .requires(ModBlocks.BASIC_CASING.get())
                .save(output);
        return result;
    }

    private void upgradeRecipe(ItemLike result, ItemLike previous) {
        shapeless(result, 1)
                .requires(previous)
                .requires(ModItems.MODULARIUM.get())
                .requires(Tags.Items.NETHER_STARS)
                .save(output);
    }

    private RecipeOutput whenLoaded(String... modIds) {
        ICondition[] conditions = Arrays.stream(modIds)
                .map(ModLoadedCondition::new)
                .toArray(ICondition[]::new);
        return output.withConditions(conditions);
    }

    private ItemLike externalItem(String namespace, String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
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
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private ShapedRecipeBuilder shaped(ItemLike result, int count) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, count)
                .unlockedBy(getHasName(result), has(result));
    }

    private ShapelessRecipeBuilder shapeless(ItemLike result, int count) {
        return ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result, count)
                .unlockedBy(getHasName(result), has(result));
    }
}
