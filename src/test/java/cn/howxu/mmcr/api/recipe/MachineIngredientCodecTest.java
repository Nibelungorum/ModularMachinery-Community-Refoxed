package cn.howxu.mmcr.api.recipe;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class MachineIngredientCodecTest {

    @BeforeAll static void bootstrapMinecraft() throws Exception {
        Class<?> fmlLoaderCls = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Class<?> distCls = Class.forName("net.neoforged.api.distmarker.Dist");
        Class<?> loadingModListCls = Class.forName("net.neoforged.fml.loading.LoadingModList");
        Constructor<?> fmlCtor = fmlLoaderCls.getDeclaredConstructor(
                ClassLoader.class, String[].class, distCls, boolean.class, Path.class);
        fmlCtor.setAccessible(true);
        Object client = distCls.getField("CLIENT").get(null);
        Object fmlLoader = fmlCtor.newInstance(
                Thread.currentThread().getContextClassLoader(),
                new String[0],
                client,
                false,
                Path.of("."));
        Constructor<?> lmlCtor = loadingModListCls.getDeclaredConstructor(
                List.class, List.class, List.class, List.class, java.util.Map.class);
        lmlCtor.setAccessible(true);
        Object emptyLoadingModList = lmlCtor.newInstance(
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        java.lang.reflect.Field loadingModListField = fmlLoaderCls.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(fmlLoader, emptyLoadingModList);
        Class<?> sharedConstantsCls = Class.forName("net.minecraft.SharedConstants");
        sharedConstantsCls.getMethod("tryDetectVersion").invoke(null);
        Bootstrap.bootStrap();
    }

    @Test void itemIngredient_roundtrip() {
        var ing = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2);
        var json = MachineIngredient.CODEC.encodeStart(jsonOps(), ing).getOrThrow();
        var back = MachineIngredient.CODEC.parse(jsonOps(), json).getOrThrow();
        assertThat(back).isEqualTo(ing);
    }

    @Test void energyIngredient_roundtrip() {
        var ing = new MachineIngredient.EnergyIngredient(80);
        var json = MachineIngredient.CODEC.encodeStart(jsonOps(), ing).getOrThrow();
        var back = MachineIngredient.CODEC.parse(jsonOps(), json).getOrThrow();
        assertThat(back).isEqualTo(ing);
    }

    @Test void fluidIngredient_roundtrip_water() {
        var ing = new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 1000);
        var json = MachineIngredient.CODEC.encodeStart(jsonOps(), ing).getOrThrow();
        var back = MachineIngredient.CODEC.parse(jsonOps(), json).getOrThrow();
        assertThat(back).isEqualTo(ing);
    }

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
