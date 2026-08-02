package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeSerializer;
import cn.howxu.mmcr.internal.block.EnergyHatchBlock;
import cn.howxu.mmcr.internal.block.FluidHatchBlock;
import cn.howxu.mmcr.internal.block.ItemBusBlock;
import cn.howxu.mmcr.internal.block.MachineCasingBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MMCRRegistries {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MMCR.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MMCR.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, MMCR.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, MMCR.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MMCR.MODID);

    public static final java.util.function.Supplier<RecipeType<MachineRecipe>> MACHINE_RECIPE_TYPE =
            RECIPE_TYPES.register("machine_recipe", () -> RecipeType.simple(MMCR.id("machine_recipe")));
    public static final java.util.function.Supplier<RecipeSerializer<MachineRecipe>> MACHINE_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("machine_recipe", () -> MachineRecipeSerializer.INSTANCE);

    public static final java.util.function.Supplier<ItemBusBlock> ITEM_BUS_BLOCK =
            BLOCKS.register("item_bus", () -> new ItemBusBlock(Block.Properties.of()));

    public static final java.util.function.Supplier<Item> ITEM_BUS_BLOCK_ITEM =
            ITEMS.register("item_bus", () -> new BlockItem(
                    MMCRRegistries.ITEM_BUS_BLOCK.get(),
                    new Item.Properties()));

    public static final java.util.function.Supplier<BlockEntityType<ItemBusBlockEntity>> ITEM_BUS_BE =
            BLOCK_ENTITIES.register("item_bus", () -> new BlockEntityType<>(
                    ItemBusBlockEntity::new,
                    MMCRRegistries.ITEM_BUS_BLOCK.get()));

    public static final java.util.function.Supplier<FluidHatchBlock> FLUID_HATCH_BLOCK =
            BLOCKS.register("fluid_hatch", () -> new FluidHatchBlock(Block.Properties.of()));

    public static final java.util.function.Supplier<Item> FLUID_HATCH_BLOCK_ITEM =
            ITEMS.register("fluid_hatch", () -> new BlockItem(
                    MMCRRegistries.FLUID_HATCH_BLOCK.get(),
                    new Item.Properties()));

    public static final java.util.function.Supplier<BlockEntityType<FluidHatchBlockEntity>> FLUID_HATCH_BE =
            BLOCK_ENTITIES.register("fluid_hatch", () -> new BlockEntityType<>(
                    FluidHatchBlockEntity::new,
                    MMCRRegistries.FLUID_HATCH_BLOCK.get()));

    public static final java.util.function.Supplier<EnergyHatchBlock> ENERGY_HATCH_BLOCK =
            BLOCKS.register("energy_hatch", () -> new EnergyHatchBlock(Block.Properties.of()));

    public static final java.util.function.Supplier<Item> ENERGY_HATCH_BLOCK_ITEM =
            ITEMS.register("energy_hatch", () -> new BlockItem(
                    MMCRRegistries.ENERGY_HATCH_BLOCK.get(),
                    new Item.Properties()));

    public static final java.util.function.Supplier<BlockEntityType<EnergyHatchBlockEntity>> ENERGY_HATCH_BE =
            BLOCK_ENTITIES.register("energy_hatch", () -> new BlockEntityType<>(
                    EnergyHatchBlockEntity::new,
                    MMCRRegistries.ENERGY_HATCH_BLOCK.get()));

    public static final java.util.function.Supplier<MachineCasingBlock> CASING_BLOCK =
            BLOCKS.register("casing", () -> new MachineCasingBlock(Block.Properties.of()));

    public static final java.util.function.Supplier<Item> CASING_BLOCK_ITEM =
            ITEMS.register("casing", () -> new BlockItem(
                    MMCRRegistries.CASING_BLOCK.get(),
                    new Item.Properties()));

    public static final java.util.function.Supplier<MachineControllerBlock> CONTROLLER_BLOCK =
            BLOCKS.register("controller", () -> new MachineControllerBlock(Block.Properties.of()));

    public static final java.util.function.Supplier<Item> CONTROLLER_BLOCK_ITEM =
            ITEMS.register("controller", () -> new BlockItem(
                    MMCRRegistries.CONTROLLER_BLOCK.get(),
                    new Item.Properties()));

    public static final java.util.function.Supplier<BlockEntityType<MachineControllerBlockEntity>> CONTROLLER_BE =
            BLOCK_ENTITIES.register("controller", () -> new BlockEntityType<>(
                    MachineControllerBlockEntity::new,
                    MMCRRegistries.CONTROLLER_BLOCK.get()));

    private MMCRRegistries() {
    }
}
