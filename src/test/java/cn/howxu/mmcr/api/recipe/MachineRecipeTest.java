package cn.howxu.mmcr.api.recipe;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        Class<?> fmlLoaderCls = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Class<?> distCls = Class.forName("net.neoforged.api.distmarker.Dist");
        Class<?> loadingModListCls = Class.forName("net.neoforged.fml.loading.LoadingModList");
        Constructor<?> fmlCtor = fmlLoaderCls.getDeclaredConstructor(
                ClassLoader.class, String[].class, distCls, boolean.class, Path.class);
        fmlCtor.setAccessible(true);
        Object fmlLoader = fmlCtor.newInstance(Thread.currentThread().getContextClassLoader(),
                new String[0], distCls.getField("CLIENT").get(null), false, Path.of("."));
        Constructor<?> lmlCtor = loadingModListCls.getDeclaredConstructor(
                List.class, List.class, List.class, List.class, java.util.Map.class);
        lmlCtor.setAccessible(true);
        Object emptyLoadingModList = lmlCtor.newInstance(
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        var loadingModListField = fmlLoaderCls.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(fmlLoader, emptyLoadingModList);
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void recipe_codec_roundtrip() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "iron_compressor"),
                Identifier.fromNamespaceAndPath("mmcr", "iron_compressor_machine"),
                40,
                List.of(
                        new MachineIngredient.EnergyIngredient(80)
                ),
                List.of()
        );

        var json = MachineRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        var back = MachineRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertThat(back).isEqualTo(recipe);
    }

    @Test
    void registry_filters_recipes_by_machine_and_rejects_null_id() {
        var machineId = Identifier.fromNamespaceAndPath("mmcr", "compressor");
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "iron"), machineId, 20, List.of(), List.of());
        var other = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "gold"),
                Identifier.fromNamespaceAndPath("mmcr", "other"), 20, List.of(), List.of());

        var machine = new DynamicMachine(machineId, "Compressor", new BlockArray(java.util.Map.of()));
        RecipeRegistry.register(recipe);
        RecipeRegistry.register(other);

        assertThat(RecipeRegistry.byMachine(machine)).containsExactly(recipe);
        assertThatThrownBy(() -> RecipeRegistry.register(
                new MachineRecipe(null, machineId, 1, List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
