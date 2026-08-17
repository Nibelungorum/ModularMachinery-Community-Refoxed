package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> REGISTER =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, MMCR.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZER_REGISTER =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, MMCR.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> MACHINE_RECIPE_TYPE =
            REGISTER.register("machine_recipe", () -> RecipeType.simple(MMCR.id("machine_recipe")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> MACHINE_RECIPE_SERIALIZER =
            SERIALIZER_REGISTER.register("machine_recipe", () -> MachineRecipeSerializer.INSTANCE);

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
        SERIALIZER_REGISTER.register(bus);
    }

    private ModRecipeTypes() {}
}
